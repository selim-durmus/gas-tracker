package com.example.gastracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

private const val CSV_HEADER = "date,price_per_liter_usd,total_cost_usd,litres,odometer_km"

fun List<FillUp>.toCsv(): String = buildString {
    appendLine(CSV_HEADER)
    sortedBy { it.dateEpochDay }.forEach { entry ->
        append(entry.date).append(',')
        append("%.3f".format(entry.pricePerLiterCents / 100.0)).append(',')
        append("%.2f".format(entry.totalCostCents / 100.0)).append(',')
        append("%.2f".format(entry.litres)).append(',')
        append(entry.odometerKm?.toString() ?: "")
        append('\n')
    }
}

fun shareCsvExport(context: Context, entries: List<FillUp>) {
    val csv = entries.toCsv()
    val today = LocalDate.now()
    val cacheFile = File(context.cacheDir, "gas-tracker-$today.csv")
    cacheFile.writeText(csv)

    val uri: Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        cacheFile,
    )

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Gas Tracker export ($today)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, "Export gas tracker data").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
}
