package com.example.gastracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {

    private fun fill(
        id: Long,
        date: LocalDate,
        priceCents: Long,
        costCents: Long,
        odometerKm: Long? = null,
    ) = FillUp(
        id = id,
        dateEpochDay = date.toEpochDay(),
        pricePerLiterCents = priceCents,
        totalCostCents = costCents,
        odometerKm = odometerKm,
    )

    // The real on-device data set that produced the suspicious 3.5 L/100km reading.
    private val deviceEntries = listOf(
        fill(7, LocalDate.of(2026, 5, 18), priceCents = 180, costCents = 8500, odometerKm = 209033),
        fill(8, LocalDate.of(2026, 5, 23), priceCents = 165, costCents = 3066, odometerKm = 209249),
        fill(12, LocalDate.of(2026, 6, 11), priceCents = 143, costCents = 3523, odometerKm = 209954),
        fill(13, LocalDate.of(2026, 6, 25), priceCents = 149, costCents = 6666, odometerKm = 210415),
    )

    private fun effById(entries: List<FillUp>): Map<Long, Double?> =
        entries.withEfficiency().associate { it.fillUp.id to it.lPer100km }

    // --- withEfficiency -----------------------------------------------------

    @Test
    fun `empty list has no efficiency and empty lifetime`() {
        val lifetime = emptyList<FillUp>().toLifetimeSummary()
        assertEquals(0, lifetime.fillUpCount)
        assertEquals(0L, lifetime.totalSpentCents)
        assertNull(lifetime.kmDriven)
        assertNull(lifetime.avgLper100km)
        assertTrue(emptyList<FillUp>().withEfficiency().isEmpty())
    }

    @Test
    fun `first fill-up never gets an efficiency value`() {
        val eff = effById(deviceEntries)
        assertNull(eff[7])
    }

    @Test
    fun `efficiency is litres over distance times 100 for each consecutive odometer pair`() {
        val eff = effById(deviceEntries)
        // 18.5818 L over 216 km -> 8.60
        assertEquals(8.603, eff[8]!!, 0.01)
        // The bug scenario, reproduced exactly: 24.636 L over 705 km -> 3.49 (correct given the data)
        assertEquals(3.495, eff[12]!!, 0.01)
        // 44.738 L over 461 km -> 9.70
        assertEquals(9.705, eff[13]!!, 0.01)
    }

    @Test
    fun `skipping odometer on a fill-up skips that entry and folds its fuel into the next reading`() {
        // User omits the odometer on the noisy Jun 11 entry (partial fill).
        val entries = deviceEntries.map {
            if (it.id == 12L) it.copy(odometerKm = null) else it
        }
        val eff = effById(entries)

        // Jun 11 itself no longer shows a (misleading) efficiency.
        assertNull(eff[12])
        // May 23 unaffected.
        assertEquals(8.603, eff[8]!!, 0.01)
        // Jun 25 now measures over the combined span May 23 -> Jun 25 (1166 km),
        // with Jun 11's litres rolled in: (24.636 + 44.738) / 1166 * 100 = 5.95.
        assertEquals(5.950, eff[13]!!, 0.01)
    }

    @Test
    fun `skipping odometer does not break the following entry the way adjacent-only pairing would`() {
        val entries = deviceEntries.map {
            if (it.id == 12L) it.copy(odometerKm = null) else it
        }
        // The key regression guard: the next entry still has a value.
        assertTrue(effById(entries)[13] != null)
    }

    @Test
    fun `a reading that folds in a partial fill is flagged as combined with a fill count`() {
        val entries = deviceEntries.map {
            if (it.id == 12L) it.copy(odometerKm = null) else it
        }
        val byId = entries.withEfficiency().associateBy { it.fillUp.id }

        // Jun 25 spans the partial Jun 11 fill -> combined over 2 fills.
        assertTrue(byId[13]!!.isCombined)
        assertEquals(2, byId[13]!!.fillsCovered)

        // May 23 is a clean tank-to-tank reading -> not combined.
        assertEquals(false, byId[8]!!.isCombined)
        assertEquals(1, byId[8]!!.fillsCovered)
    }

    @Test
    fun `clean readings are never flagged as combined`() {
        deviceEntries.withEfficiency().forEach {
            assertEquals(false, it.isCombined)
            assertEquals(1, it.fillsCovered)
        }
    }

    @Test
    fun `ordering is deterministic for same-day entries regardless of input order`() {
        val day = LocalDate.of(2026, 6, 1)
        val a = fill(1, day, priceCents = 100, costCents = 4000, odometerKm = 1000)
        val b = fill(2, day, priceCents = 100, costCents = 5000, odometerKm = 1100)
        // Feed them in reverse id order; sort must still pair by (date, id).
        val eff = effById(listOf(b, a))
        assertNull(eff[1])              // lowest id is the baseline
        assertEquals(50.0, eff[2]!!, 0.01) // 50 L over 100 km
    }

    @Test
    fun `a non-increasing odometer is treated as a reset and produces no efficiency`() {
        val entries = listOf(
            fill(1, LocalDate.of(2026, 1, 1), priceCents = 100, costCents = 1000, odometerKm = 1000),
            fill(2, LocalDate.of(2026, 1, 5), priceCents = 100, costCents = 1000, odometerKm = 900),
            fill(3, LocalDate.of(2026, 1, 9), priceCents = 100, costCents = 2000, odometerKm = 1100),
        )
        val eff = effById(entries)
        assertNull(eff[1])
        assertNull(eff[2]) // odometer went backwards -> skipped, becomes new baseline
        assertEquals(10.0, eff[3]!!, 0.01) // 20 L over 200 km (900 -> 1100)
    }

    // --- toLifetimeSummary --------------------------------------------------

    @Test
    fun `lifetime totals sum cost and litres across all entries`() {
        val lifetime = deviceEntries.toLifetimeSummary()
        assertEquals(4, lifetime.fillUpCount)
        assertEquals(8500L + 3066 + 3523 + 6666, lifetime.totalSpentCents)
        assertEquals(135.18, lifetime.totalLitres, 0.01)
    }

    @Test
    fun `lifetime efficiency averages consumed litres over driven distance`() {
        val lifetime = deviceEntries.toLifetimeSummary()
        // km = 216 + 705 + 461 = 1382
        assertEquals(1382L, lifetime.kmDriven)
        // consumed = 18.582 + 24.636 + 44.738 = 87.956 over 1382 km -> 6.36
        assertEquals(6.364, lifetime.avgLper100km!!, 0.01)
    }

    @Test
    fun `lifetime efficiency is null when fewer than two odometer readings exist`() {
        val entries = listOf(
            fill(1, LocalDate.of(2026, 1, 1), priceCents = 100, costCents = 5000, odometerKm = 1000),
            fill(2, LocalDate.of(2026, 1, 8), priceCents = 100, costCents = 5000), // no odometer
        )
        val lifetime = entries.toLifetimeSummary()
        assertNull(lifetime.kmDriven)
        assertNull(lifetime.avgLper100km)
    }

    @Test
    fun `lifetime efficiency accumulates skipped fills consistently with per-entry values`() {
        val entries = deviceEntries.map {
            if (it.id == 12L) it.copy(odometerKm = null) else it
        }
        val lifetime = entries.toLifetimeSummary()
        // Segments: May 23 (216 km, 18.582 L) and Jun 25 (1166 km, 24.636 + 44.738 L)
        assertEquals(216L + 1166L, lifetime.kmDriven)
        val consumed = 18.58182 + (24.63636 + 44.73826)
        assertEquals(consumed / (216 + 1166) * 100.0, lifetime.avgLper100km!!, 0.01)
    }

    // --- toHistorySections --------------------------------------------------

    @Test
    fun `history groups by year and month, newest first, with correct sums`() {
        val entries = listOf(
            fill(1, LocalDate.of(2025, 12, 10), priceCents = 100, costCents = 5000),
            fill(2, LocalDate.of(2026, 1, 15), priceCents = 100, costCents = 4000),
            fill(3, LocalDate.of(2026, 1, 20), priceCents = 200, costCents = 4000),
            fill(4, LocalDate.of(2026, 3, 5), priceCents = 100, costCents = 3000),
        )
        val sections = entries.toHistorySections()

        // Years ordered descending.
        assertEquals(listOf(2026, 2025), sections.map { it.year.year })

        val y2026 = sections.first { it.year.year == 2026 }
        assertEquals(11000L, y2026.year.totalSpentCents)
        assertEquals(3, y2026.year.fillUpCount)
        // Months within the year ordered descending: March then January.
        assertEquals(
            listOf(YearMonth.of(2026, 3), YearMonth.of(2026, 1)),
            y2026.months.map { it.yearMonth },
        )

        val jan = y2026.months.first { it.yearMonth == YearMonth.of(2026, 1) }
        assertEquals(8000L, jan.totalSpentCents)
        assertEquals(2, jan.fillUpCount)
        // 40 L + 20 L = 60 L; avg price = 8000 / 60 = 133 cents/L (rounded).
        assertEquals(60.0, jan.totalLitres, 0.01)
        assertEquals(133L, jan.avgPricePerLiterCents)
    }
}
