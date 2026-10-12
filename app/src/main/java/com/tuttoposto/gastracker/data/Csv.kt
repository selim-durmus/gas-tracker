package com.tuttoposto.gastracker.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * CSV import/export.
 *
 * Export writes the v2 format, oldest first:
 *   date,price_per_litre,total_cost,litres,odometer_km,full_tank,note
 *
 * Import is header-driven and also reads the v1 export
 * (date,price_per_liter_usd,total_cost_usd,litres,odometer_km), so data from the old app
 * comes across unchanged. Litres are always re-derived from total / price; a file that has
 * litres but no price gets its price derived instead.
 */
object Csv {
    const val HEADER = "date,price_per_litre,total_cost,litres,odometer_km,full_tank,note"
    const val MIME = "text/csv"
    private val BOM = Char(0xFEFF).toString()

    fun format(entries: List<FillUp>): String = buildString {
        append(HEADER).append('\n')
        for (e in entries.chronological()) {
            append(e.date.format(DateTimeFormatter.ISO_LOCAL_DATE)).append(',')
            append(plainDecimal(e.pricePerLitreMills, 3)).append(',')
            append(plainDecimal(e.totalCostCents, 2)).append(',')
            append(String.format(Locale.ROOT, "%.3f", e.litres)).append(',')
            append(e.odometerKm?.toString() ?: "").append(',')
            append(if (e.fullTank) "yes" else "no").append(',')
            append(quote(e.note)).append('\n')
        }
    }

    data class RowError(val line: Int, val message: String)

    data class ParseResult(
        val rows: List<FillUp>,
        val errors: List<RowError>,
        val isLegacyFormat: Boolean,
    )

    class FormatException(message: String) : Exception(message)

    private val dateColumns = setOf("date", "day")
    private val priceColumns = setOf("price_per_litre", "price_per_liter", "price_per_liter_usd", "price_per_litre_usd", "price")
    private val totalColumns = setOf("total_cost", "total_cost_usd", "total", "cost", "amount")
    private val litresColumns = setOf("litres", "liters", "volume", "litres_l")
    private val odoColumns = setOf("odometer_km", "odometer", "odo", "km")
    private val fullColumns = setOf("full_tank", "full", "fulltank")
    private val noteColumns = setOf("note", "notes", "comment")

    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT),
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ROOT),
    )

    /** @throws FormatException when the file has no usable header. */
    fun parse(text: String): ParseResult {
        val records = readRecords(text.removePrefix(BOM))
        val headerIndex = records.indexOfFirst { (_, fields) -> fields.any { it.isNotBlank() } }
        if (headerIndex < 0) throw FormatException("The file is empty.")
        val header = records[headerIndex].second.map { it.trim().lowercase(Locale.ROOT).replace(' ', '_') }

        fun col(names: Set<String>) = header.indexOfFirst { it in names }.takeIf { it >= 0 }
        val dateCol = col(dateColumns)
        val priceCol = col(priceColumns)
        val totalCol = col(totalColumns)
        val litresCol = col(litresColumns)
        val odoCol = col(odoColumns)
        val fullCol = col(fullColumns)
        val noteCol = col(noteColumns)
        if (dateCol == null || totalCol == null || (priceCol == null && litresCol == null)) {
            throw FormatException(
                "This doesn't look like a Gas Tracker export. Expected columns: date, " +
                    "price per litre (or litres), total cost.",
            )
        }

        val rows = ArrayList<FillUp>()
        val errors = ArrayList<RowError>()
        for ((line, fields) in records.drop(headerIndex + 1)) {
            if (fields.all { it.isBlank() }) continue
            fun field(index: Int?) = index?.let { fields.getOrNull(it)?.trim() }.orEmpty()

            val date = parseDate(field(dateCol))
            if (date == null) {
                errors += RowError(line, "unreadable date \"${field(dateCol)}\"")
                continue
            }
            val total = parseCents(field(totalCol))
            if (total == null || total <= 0) {
                errors += RowError(line, "missing or invalid total cost")
                continue
            }
            var price = parseMills(field(priceCol))?.takeIf { it > 0 }
            if (price == null) {
                val litres = field(litresCol).replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
                price = litres?.let { Math.round(total * 10.0 / it) }
            }
            if (price == null || price <= 0) {
                errors += RowError(line, "missing or invalid price per litre")
                continue
            }
            val odoText = field(odoCol).replace(",", "").replace(" ", "")
            val odo = if (odoText.isEmpty()) null else odoText.toLongOrNull()
            if (odoText.isNotEmpty() && (odo == null || odo < 0)) {
                errors += RowError(line, "invalid odometer \"${field(odoCol)}\"")
                continue
            }
            val full = when (field(fullCol).lowercase(Locale.ROOT)) {
                "no", "n", "false", "0", "partial" -> false
                else -> true
            }
            rows += FillUp(
                dateEpochDay = date.toEpochDay(),
                pricePerLitreMills = price,
                totalCostCents = total,
                odometerKm = odo,
                fullTank = full,
                note = field(noteCol),
            )
        }
        val legacy = "price_per_liter_usd" in header || "total_cost_usd" in header
        return ParseResult(rows, errors, legacy)
    }

    private fun parseDate(text: String): LocalDate? {
        for (formatter in dateFormats) {
            try {
                return LocalDate.parse(text, formatter)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** RFC 4180 reader: quoted fields may hold commas, quotes ("") and newlines. Pairs are (line, fields). */
    private fun readRecords(text: String): List<Pair<Int, List<String>>> {
        val records = ArrayList<Pair<Int, List<String>>>()
        var fields = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var line = 1
        var recordLine = 1
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> { if (c == '\n') line++; field.append(c) }
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> { fields.add(field.toString()); field.clear() }
                    '\r' -> {}
                    '\n' -> {
                        fields.add(field.toString()); field.clear()
                        records += recordLine to fields
                        fields = ArrayList()
                        line++
                        recordLine = line
                    }
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || fields.isNotEmpty()) {
            fields.add(field.toString())
            records += recordLine to fields
        }
        return records
    }
}

/** Identity used to spot rows that are already in the log when merging an import. */
data class FillUpKey(val day: Long, val priceMills: Long, val totalCents: Long, val odometerKm: Long?)

val FillUp.key: FillUpKey get() = FillUpKey(dateEpochDay, pricePerLitreMills, totalCostCents, odometerKm)
