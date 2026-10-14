package com.tuttoposto.gastracker.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class CsvTest {

    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    /** Exactly what the v1 app's "Export CSV" writes (taken from a real export). */
    private val legacyExport = """
        date,price_per_liter_usd,total_cost_usd,litres,odometer_km
        2026-05-18,1.800,85.00,47.22,209033
        2026-05-23,1.650,30.66,18.58,209249
        2026-06-11,1.430,35.23,24.64,
        2026-06-25,1.490,66.66,44.74,210415
    """.trimIndent() + "\n"

    @Test
    fun `reads the v1 export losslessly`() {
        val r = Csv.parse(legacyExport)
        assertTrue(r.isLegacyFormat)
        assertTrue(r.errors.isEmpty())
        assertEquals(4, r.rows.size)
        val first = r.rows[0]
        assertEquals(LocalDate.of(2026, 5, 18).toEpochDay(), first.dateEpochDay)
        assertEquals(1800L, first.pricePerLitreMills)
        assertEquals(8500L, first.totalCostCents)
        assertEquals(209033L, first.odometerKm)
        assertTrue(first.fullTank)
        assertNull(r.rows[2].odometerKm)
        assertEquals(3523L, r.rows[2].totalCostCents)
    }

    @Test
    fun `round trips every field including awkward notes`() {
        val entries = listOf(
            FillUp(1, LocalDate.of(2026, 9, 28).toEpochDay(), 1699, 3000, 214841, fullTank = false, note = "Costco, \"quick\" top-up"),
            FillUp(2, LocalDate.of(2026, 10, 2).toEpochDay(), 1359, 5915, 214970, note = "line one\nline two"),
            FillUp(3, LocalDate.of(2026, 10, 2).toEpochDay(), 1001, 1, null),
            FillUp(4, LocalDate.of(2026, 10, 9).toEpochDay(), 1500, 6000, 216000, missedPrevious = true),
        )
        val parsed = Csv.parse(Csv.format(entries))
        assertTrue(parsed.errors.isEmpty())
        assertFalse(parsed.isLegacyFormat)
        assertEquals(entries.map { it.copy(id = 0) }, parsed.rows)
    }

    @Test
    fun `export is oldest first with same-day ties in id order`() {
        val day = LocalDate.of(2026, 1, 1).toEpochDay()
        val csv = Csv.format(listOf(FillUp(9, day, 1000, 200), FillUp(3, day, 1000, 100), FillUp(5, day - 1, 1000, 50)))
        val totals = Csv.parse(csv).rows.map { it.totalCostCents }
        assertEquals(listOf(50L, 100L, 200L), totals)
    }

    @Test
    fun `export uses dot decimals even on a comma-decimal locale`() {
        Locale.setDefault(Locale.ITALY)
        val csv = Csv.format(listOf(FillUp(1, LocalDate.of(2026, 1, 1).toEpochDay(), 1799, 4550)))
        assertEquals("2026-01-01,1.799,45.50,25.292,,yes,no,", csv.lines()[1])
        assertEquals(1, Csv.parse(csv).rows.size)
    }

    @Test
    fun `tolerates BOM, CRLF, blank lines, quoted headers and column order`() {
        val text = Char(0xFEFF) + "\"Total Cost\",Date,Price\r\n\r\n45.00,2026-02-03,1.5\r\n"
        val r = Csv.parse(text)
        assertEquals(1, r.rows.size)
        assertEquals(4500L, r.rows[0].totalCostCents)
        assertEquals(1500L, r.rows[0].pricePerLitreMills)
    }

    @Test
    fun `derives price from litres when the file has no price column`() {
        val r = Csv.parse("date,total_cost,litres\n2026-02-03,50.00,25.00\n")
        assertEquals(2000L, r.rows.single().pricePerLitreMills)
    }

    @Test
    fun `reports bad rows with line numbers and keeps the good ones`() {
        val text = """
            date,price_per_litre,total_cost,litres,odometer_km
            2026-01-01,1.5,30.00,,1000
            not-a-date,1.5,30.00,,
            2026-01-03,,30.00,,
            2026-01-04,1.5,-3,,
            2026-01-05,1.5,30.00,,12x
        """.trimIndent()
        val r = Csv.parse(text)
        assertEquals(1, r.rows.size)
        assertEquals(listOf(3, 4, 5, 6), r.errors.map { it.line })
    }

    @Test
    fun `accepts US style dates and partial flags`() {
        val r = Csv.parse("date,price,total,full_tank\n10/2/2026,1.36,59.15,no\n")
        assertEquals(LocalDate.of(2026, 10, 2).toEpochDay(), r.rows.single().dateEpochDay)
        assertFalse(r.rows.single().fullTank)
    }

    @Test
    fun `exports from before the missed-fill column still import`() {
        val r = Csv.parse(
            "date,price_per_litre,total_cost,litres,odometer_km,full_tank,note\n" +
                "2026-10-02,1.360,59.15,43.493,214970,yes,\n",
        )
        assertFalse(r.rows.single().missedPrevious)
        assertTrue(r.rows.single().fullTank)
    }

    @Test
    fun `rejects files that are not fill-up exports`() {
        try {
            Csv.parse("name,email\nbob,bob@example.com\n")
            fail("expected FormatException")
        } catch (_: Csv.FormatException) {
        }
        try {
            Csv.parse("")
            fail("expected FormatException")
        } catch (_: Csv.FormatException) {
        }
    }
}
