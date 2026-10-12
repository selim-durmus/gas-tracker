package com.tuttoposto.gastracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {

    private fun fill(
        id: Long,
        date: LocalDate,
        priceMills: Long,
        costCents: Long,
        odometerKm: Long? = null,
        fullTank: Boolean = true,
    ) = FillUp(id, date.toEpochDay(), priceMills, costCents, odometerKm, fullTank)

    // Real on-device entries (v1 stored whole cents, hence the trailing zeros).
    private val device = listOf(
        fill(7, LocalDate.of(2026, 5, 18), 1800, 8500, 209033),
        fill(8, LocalDate.of(2026, 5, 23), 1650, 3066, 209249),
        fill(12, LocalDate.of(2026, 6, 11), 1430, 3523, 209954),
        fill(13, LocalDate.of(2026, 6, 25), 1490, 6666, 210415),
    )

    private fun effById(entries: List<FillUp>) =
        entries.efficiencySegments().associate { it.endFillUpId to it.lPer100km }

    @Test
    fun `litres are total over price with three-decimal prices`() {
        val f = fill(1, LocalDate.of(2026, 1, 1), priceMills = 1799, costCents = 5000)
        assertEquals(27.793, f.litres, 0.001)
    }

    @Test
    fun `empty log has no economy`() {
        val l = emptyList<FillUp>().lifetime()
        assertEquals(0, l.total.count)
        assertNull(l.kmTracked)
        assertNull(l.lPer100km)
        assertNull(l.costPer100kmCents)
        assertTrue(emptyList<FillUp>().efficiencySegments().isEmpty())
    }

    @Test
    fun `first fill never has a reading`() {
        assertNull(effById(device)[7])
    }

    @Test
    fun `each consecutive full fill pair is litres over distance`() {
        val eff = effById(device)
        assertEquals(8.603, eff[8]!!, 0.01)
        assertEquals(3.495, eff[12]!!, 0.01)
        assertEquals(9.705, eff[13]!!, 0.01)
    }

    @Test
    fun `a fill without odometer folds its fuel into the next reading`() {
        val entries = device.map { if (it.id == 12L) it.copy(odometerKm = null) else it }
        val eff = effById(entries)
        assertNull(eff[12])
        assertEquals(8.603, eff[8]!!, 0.01)
        assertEquals(5.950, eff[13]!!, 0.01) // (24.636 + 44.738) L over 1166 km
    }

    @Test
    fun `a partial fill with odometer also folds forward and does not reset the baseline`() {
        val entries = device.map { if (it.id == 12L) it.copy(fullTank = false) else it }
        val segs = entries.efficiencySegments().associateBy { it.endFillUpId }
        assertNull(segs[12])
        assertEquals(5.950, segs[13]!!.lPer100km, 0.01)
        assertEquals(2, segs[13]!!.fillsCovered)
        assertTrue(segs[13]!!.isCombined)
        assertFalse(segs[8]!!.isCombined)
    }

    @Test
    fun `the Sep 28 partial fill no longer produces impossible readings once flagged`() {
        // From the phone: a $30 top-up on Sep 28 then a full fill on Oct 2.
        val sep20 = fill(1, LocalDate.of(2026, 9, 20), 1770, 7861, 214251)
        val sep28 = fill(2, LocalDate.of(2026, 9, 28), 1690, 3000, 214841)
        val oct2 = fill(3, LocalDate.of(2026, 10, 2), 1360, 5915, 214970)
        val asFull = effById(listOf(sep20, sep28, oct2))
        assertTrue(asFull[2]!! < 4.0)   // what v1 showed: ~3.0
        assertTrue(asFull[3]!! > 30.0)  // and ~33.7
        val flagged = effById(listOf(sep20, sep28.copy(fullTank = false), oct2))
        assertNull(flagged[2])
        assertEquals((17.751 + 43.493) / 719 * 100, flagged[3]!!, 0.05) // ~8.5
    }

    /** The complete migrated log as of 2026-10-11 (ids in date order). */
    private val fullLog = listOf(
        "2026-05-18,1800,8500,209033", "2026-05-23,1650,3066,209249", "2026-06-11,1430,3523,",
        "2026-06-25,1490,6666,210415", "2026-07-06,1530,6871,210898", "2026-07-15,1570,7100,211406",
        "2026-07-27,1730,7690,211889", "2026-08-06,1540,6220,212382", "2026-08-17,1590,7200,212911",
        "2026-08-29,1420,5336,213331", "2026-09-12,1650,6000,213709", "2026-09-20,1770,7861,214251",
        "2026-09-28,1690,3000,214841", "2026-10-02,1360,5915,214970",
    ).mapIndexed { i, line ->
        val (d, p, t, o) = line.split(",")
        fill(i + 1L, LocalDate.parse(d), p.toLong(), t.toLong(), o.toLongOrNull())
    }

    @Test
    fun `the Sep 28 top-up is the only suspected partial fill in the real log`() {
        assertEquals(mapOf(13L to PartialHint.LowReading), fullLog.partialFillSuspects())
    }

    @Test
    fun `marking it partial clears every hint and gives a sane economy`() {
        val fixed = fullLog.map { if (it.id == 13L) it.copy(fullTank = false) else it }
        assertTrue(fixed.partialFillSuspects().isEmpty())
        val readings = fixed.efficiencySegments().map { it.lPer100km }
        assertTrue("all readings between 5 and 11: $readings", readings.all { it in 5.0..11.0 })
        val oct2 = fixed.efficiencySegments().last()
        assertEquals(12L, oct2.startFillUpId)
        assertEquals(719L, oct2.distanceKm)
        assertEquals(2, oct2.fillsCovered)
    }

    @Test
    fun `a high reading points at the fill it started from`() {
        // Six normal ~8 L/100km tanks, then one starting from a fill that wasn't really full.
        val normal = (0 until 6).map { i ->
            fill(i + 1L, LocalDate.of(2026, 1, 1 + i * 3), 1000, 4000, 1000L + i * 500)
        }
        val afterShortFill = fill(7, LocalDate.of(2026, 1, 25), 1000, 8000, 3600) // 80 L over 600 km
        val hints = (normal + afterShortFill).partialFillSuspects()
        assertEquals(mapOf(6L to PartialHint.NextReadingHigh), hints)
    }

    @Test
    fun `no hints without enough readings to know what normal is`() {
        assertTrue(device.take(3).partialFillSuspects().isEmpty())
    }

    @Test
    fun `fuel before the first full reading is not attributed to any distance`() {
        val entries = listOf(
            fill(1, LocalDate.of(2026, 1, 1), 1000, 2000),           // no odometer yet
            fill(2, LocalDate.of(2026, 1, 5), 1000, 4000, 1000),
            fill(3, LocalDate.of(2026, 1, 9), 1000, 3000, 1500),
        )
        val segs = entries.efficiencySegments()
        assertEquals(1, segs.size)
        assertEquals(6.0, segs.single().lPer100km, 0.001) // 30 L / 500 km
    }

    @Test
    fun `ordering is deterministic for same-day entries`() {
        val day = LocalDate.of(2026, 6, 1)
        val a = fill(1, day, 1000, 4000, 1000)
        val b = fill(2, day, 1000, 5000, 1100)
        val eff = effById(listOf(b, a))
        assertNull(eff[1])
        assertEquals(50.0, eff[2]!!, 0.01)
    }

    @Test
    fun `a non-increasing odometer restarts the baseline`() {
        val entries = listOf(
            fill(1, LocalDate.of(2026, 1, 1), 1000, 1000, 1000),
            fill(2, LocalDate.of(2026, 1, 5), 1000, 1000, 900),
            fill(3, LocalDate.of(2026, 1, 9), 1000, 2000, 1100),
        )
        val eff = effById(entries)
        assertNull(eff[2])
        assertEquals(10.0, eff[3]!!, 0.01)
    }

    @Test
    fun `lifetime economy and cost per distance`() {
        val l = device.lifetime()
        assertEquals(4, l.total.count)
        assertEquals(8500L + 3066 + 3523 + 6666, l.total.spentCents)
        assertEquals(135.18, l.total.litres, 0.01)
        assertEquals(1382L, l.kmTracked)
        assertEquals(6.364, l.lPer100km!!, 0.01)
        // (3066 + 3523 + 6666) cents over 1382 km -> 959 cents per 100 km
        assertEquals(959L, l.costPer100kmCents)
        assertEquals(LocalDate.of(2026, 5, 18), l.firstDate)
    }

    @Test
    fun `average price is weighted by volume`() {
        val s = listOf(
            fill(1, LocalDate.of(2026, 1, 1), 1000, 4000),  // 40 L at $1.000
            fill(2, LocalDate.of(2026, 1, 2), 2000, 4000),  // 20 L at $2.000
        ).summary()
        assertEquals(1333L, s.avgPriceMills) // $80 / 60 L
    }

    @Test
    fun `history groups years and months newest first`() {
        val entries = listOf(
            fill(1, LocalDate.of(2025, 12, 10), 1000, 5000),
            fill(2, LocalDate.of(2026, 1, 15), 1000, 4000),
            fill(3, LocalDate.of(2026, 1, 20), 2000, 4000),
            fill(4, LocalDate.of(2026, 3, 5), 1000, 3000),
        )
        val h = entries.history()
        assertEquals(listOf(2026, 2025), h.map { it.year })
        assertEquals(11000L, h[0].summary.spentCents)
        assertEquals(listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 1)), h[0].months.map { it.month })
        assertEquals(60.0, h[0].months[1].summary.litres, 0.001)
    }

    @Test
    fun `monthly spend includes empty months oldest first`() {
        val entries = listOf(
            fill(1, LocalDate.of(2026, 8, 3), 1000, 5000),
            fill(2, LocalDate.of(2026, 10, 1), 1000, 2500),
        )
        val m = entries.monthlySpend(YearMonth.of(2026, 10), 3)
        assertEquals(
            listOf(YearMonth.of(2026, 8) to 5000L, YearMonth.of(2026, 9) to 0L, YearMonth.of(2026, 10) to 2500L),
            m,
        )
    }

    @Test
    fun `dashboard splits this month, last month and this year`() {
        val entries = listOf(
            fill(1, LocalDate.of(2025, 12, 30), 1000, 1000),
            fill(2, LocalDate.of(2026, 9, 30), 1000, 2000),
            fill(3, LocalDate.of(2026, 10, 1), 1000, 3000),
        )
        val d = entries.dashboard(LocalDate.of(2026, 10, 11))
        assertEquals(3000L, d.thisMonth.spentCents)
        assertEquals(2000L, d.lastMonth.spentCents)
        assertEquals(5000L, d.thisYear.spentCents)
        assertEquals(6000L, d.lifetime.total.spentCents)
    }
}
