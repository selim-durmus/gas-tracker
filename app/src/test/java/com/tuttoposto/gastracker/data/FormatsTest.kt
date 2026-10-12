package com.tuttoposto.gastracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatsTest {

    @Test
    fun `prices keep their third decimal`() {
        // v1 rounded this to 180 cents.
        assertEquals(1799L, parseMills("1.799"))
        assertEquals("$1.799", formatPrice(1799))
        assertEquals("1.799", plainDecimal(1799, 3))
    }

    @Test
    fun `comma decimals are understood instead of being multiplied by 100`() {
        // v1 stripped the comma: "45,50" became $4550.
        assertEquals(4550L, parseCents("45,50"))
        assertEquals(1799L, parseMills("1,799"))
    }

    @Test
    fun `parsing handles currency signs, rounding and junk`() {
        assertEquals(4550L, parseCents("$45.5"))
        assertEquals(4550L, parseCents(" 45.50 "))
        assertEquals(4600L, parseCents("45.995"))
        assertEquals(500L, parseCents("5."))
        assertEquals(50L, parseCents(".5"))
        assertNull(parseCents(""))
        assertNull(parseCents("."))
        assertNull(parseCents("1.2.3"))
        assertNull(parseCents("-4"))
        assertNull(parseCents("abc"))
    }

    @Test
    fun `price field auto-inserts the decimal after the first digit`() {
        assertEquals("1", sanitizeDecimalInput("1", maxDecimals = 3, autoDecimalAfter = 1))
        assertEquals("1.7", sanitizeDecimalInput("17", maxDecimals = 3, autoDecimalAfter = 1))
        assertEquals("1.799", sanitizeDecimalInput("1799", maxDecimals = 3, autoDecimalAfter = 1))
        assertEquals("1.799", sanitizeDecimalInput("17999", maxDecimals = 3, autoDecimalAfter = 1))
        assertEquals("1.", sanitizeDecimalInput("1.", maxDecimals = 3, autoDecimalAfter = 1))
        assertEquals("1.79", sanitizeDecimalInput("1,79", maxDecimals = 3, autoDecimalAfter = 1))
    }

    @Test
    fun `total field limits decimals and keeps a single separator`() {
        assertEquals("45.50", sanitizeDecimalInput("45.509", maxDecimals = 2))
        assertEquals("45.5", sanitizeDecimalInput("45.5.", maxDecimals = 2))
        assertEquals("45.50", sanitizeDecimalInput("$45,50", maxDecimals = 2))
        assertEquals("123456", sanitizeDecimalInput("1234567", maxDecimals = 2))
    }

    @Test
    fun `display formats`() {
        assertEquals("$1,234.56", formatMoney(123456))
        assertEquals("$1,235", formatMoneyWhole(123456))
        assertEquals("214,970 km", formatKm(214970))
        assertEquals("43.49 L", formatLitres(43.4926))
        assertEquals("8.5", formatEfficiency(8.46))
    }
}
