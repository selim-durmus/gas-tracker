package com.tuttoposto.gastracker.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ImportMode { Merge, Replace }

data class ImportPlan(
    val parsed: Csv.ParseResult,
    /** Rows not already in the log — what a merge would add. */
    val newRows: List<FillUp>,
    val duplicateCount: Int,
    val existingCount: Int,
) {
    val rows: List<FillUp> get() = parsed.rows
}

data class ImportResult(val imported: Int, val mode: ImportMode, val safetyBackup: File?)

class FillUpRepository(
    private val dao: FillUpDao,
    /** Where a CSV snapshot of the log is written before every import. */
    private val backupDir: File,
) {
    fun observeAll(): Flow<List<FillUp>> = dao.observeAll()
    suspend fun getAll(): List<FillUp> = dao.getAll()
    suspend fun getById(id: Long): FillUp? = dao.getById(id)
    suspend fun upsert(fillUp: FillUp): Long = dao.upsert(fillUp)
    suspend fun delete(fillUp: FillUp) = dao.delete(fillUp)

    suspend fun planImport(parsed: Csv.ParseResult): ImportPlan {
        val existing = dao.getAll()
        // Multiset match, so a file with two identical same-day fills keeps both.
        val remaining = existing.groupingBy { it.key }.eachCount().toMutableMap()
        val newRows = parsed.rows.filter { row ->
            val left = remaining[row.key] ?: 0
            if (left > 0) {
                remaining[row.key] = left - 1
                false
            } else {
                true
            }
        }
        return ImportPlan(parsed, newRows, parsed.rows.size - newRows.size, existing.size)
    }

    suspend fun applyImport(plan: ImportPlan, mode: ImportMode): ImportResult {
        val backup = writeSafetyBackup()
        val rows = when (mode) {
            ImportMode.Merge -> plan.newRows
            ImportMode.Replace -> plan.rows
        }.map { it.copy(id = 0) }
        when (mode) {
            ImportMode.Merge -> dao.insertAll(rows)
            ImportMode.Replace -> dao.replaceAll(rows)
        }
        return ImportResult(rows.size, mode, backup)
    }

    /** Snapshot of the current log; null when the log is empty. Keeps the newest 10. */
    private suspend fun writeSafetyBackup(): File? {
        val current = dao.getAll()
        if (current.isEmpty()) return null
        return withContext(Dispatchers.IO) {
            backupDir.mkdirs()
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT))
            val file = File(backupDir, "before-import-$stamp.csv")
            file.writeText(Csv.format(current))
            backupDir.listFiles { f -> f.name.startsWith("before-import-") }
                ?.sortedByDescending { it.name }
                ?.drop(10)
                ?.forEach { it.delete() }
            file
        }
    }
}
