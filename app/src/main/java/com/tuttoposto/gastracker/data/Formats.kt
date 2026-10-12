package com.tuttoposto.gastracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Display formatting is pinned to Locale.US (the app tracks dollars and litres), and the
 * machine formats (CSV, edit-field round trips) to Locale.ROOT. v1 used the device's default
 * locale for String.format, so on a comma-decimal locale "1.799" became "1,799" — which
 * corrupted the comma-separated export and broke re-parsing of the edit fields.
 */

private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}
private val currencyWhole: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 0
}
private val grouped: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

fun formatMoney(cents: Long): String = currency.format(cents / 100.0)

fun formatMoneyWhole(cents: Long): String = currencyWhole.format(cents / 100.0)

/** "$1.799" — three decimals, like the pump sign. */
fun formatPrice(mills: Long): String = "$" + plainDecimal(mills, 3)

fun formatLitres(litres: Double, decimals: Int = 2): String =
    String.format(Locale.US, "%,.${decimals}f L", litres)

fun formatKm(km: Long): String = grouped.format(km) + " km"

fun formatEfficiency(lPer100km: Double): String = String.format(Locale.US, "%.1f", lPer100km)

fun formatPercent(fraction: Double): String = String.format(Locale.US, "%.0f%%", fraction * 100)

val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)
val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
val monthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)
val isoDate: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun LocalDate.formatLong(): String = format(longDate)

/** An integer amount of 1/10^[scale] units, written as a plain decimal: (1799, 3) -> "1.799". */
fun plainDecimal(units: Long, scale: Int): String =
    BigDecimal.valueOf(units, scale).toPlainString()

/**
 * Parses user or file input like "45.5", "45,50", "$1.799" into integer units of
 * 1/10^[scale]. Rounds half-up past [scale] decimals. Returns null for blank, malformed or
 * negative input.
 */
fun parseScaled(input: String, scale: Int): Long? {
    val cleaned = input.trim().removePrefix("$").trim().replace(',', '.')
    if (cleaned.isEmpty() || cleaned == ".") return null
    if (cleaned.count { it == '.' } > 1) return null
    if (!cleaned.all { it.isDigit() || it == '.' }) return null
    return try {
        BigDecimal(cleaned).setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).longValueExact()
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }
}

fun parseCents(input: String): Long? = parseScaled(input, 2)

fun parseMills(input: String): Long? = parseScaled(input, 3)

/**
 * Keeps a decimal text field well-formed while the user types.
 *
 * - accepts ',' as the decimal separator (some keyboards only offer a comma),
 * - keeps at most one separator, [maxIntDigits] integer digits and [maxDecimals] decimals,
 * - with [autoDecimalAfter], inserts the separator automatically once that many integer
 *   digits are typed, so "1799" becomes "1.799" for a price per litre.
 */
fun sanitizeDecimalInput(
    raw: String,
    maxDecimals: Int,
    maxIntDigits: Int = 6,
    autoDecimalAfter: Int? = null,
): String {
    val filtered = raw.map { if (it == ',') '.' else it }.filter { it.isDigit() || it == '.' }
        .joinToString("")
    val dot = filtered.indexOf('.')
    if (dot < 0) {
        if (autoDecimalAfter != null && filtered.length > autoDecimalAfter) {
            return filtered.take(autoDecimalAfter) + "." +
                filtered.drop(autoDecimalAfter).take(maxDecimals)
        }
        return filtered.take(maxIntDigits)
    }
    val intPart = filtered.substring(0, dot).take(maxIntDigits)
    val decimals = filtered.substring(dot + 1).replace(".", "").take(maxDecimals)
    return "$intPart.$decimals"
}
