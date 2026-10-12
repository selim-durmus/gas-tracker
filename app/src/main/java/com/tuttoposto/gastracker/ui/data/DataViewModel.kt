package com.tuttoposto.gastracker.ui.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.gastracker.data.Csv
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.data.ImportMode
import com.tuttoposto.gastracker.data.ImportPlan
import com.tuttoposto.gastracker.data.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ImportUi {
    data object Idle : ImportUi
    data object Reading : ImportUi
    data class Review(val plan: ImportPlan, val fileName: String) : ImportUi
    data class Failed(val message: String) : ImportUi
    data object Importing : ImportUi
}

class DataViewModel(
    private val repository: FillUpRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    val entries: StateFlow<List<FillUp>?> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _import = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val import: StateFlow<ImportUi> = _import.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun startImport(uri: Uri) {
        if (_import.value is ImportUi.Reading || _import.value is ImportUi.Importing) return
        _import.value = ImportUi.Reading
        viewModelScope.launch {
            _import.value = try {
                val (name, text) = withContext(Dispatchers.IO) { displayName(uri) to readText(uri) }
                val parsed = withContext(Dispatchers.Default) { Csv.parse(text) }
                if (parsed.rows.isEmpty()) {
                    ImportUi.Failed(
                        if (parsed.errors.isEmpty()) "The file has no fill-ups in it."
                        else "None of the rows could be read (${parsed.errors.first().message} on line ${parsed.errors.first().line}).",
                    )
                } else {
                    ImportUi.Review(repository.planImport(parsed), name)
                }
            } catch (e: Csv.FormatException) {
                ImportUi.Failed(e.message ?: "Unrecognised file.")
            } catch (e: Exception) {
                ImportUi.Failed("Couldn't read the file: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun confirmImport(mode: ImportMode) {
        val review = _import.value as? ImportUi.Review ?: return
        _import.value = ImportUi.Importing
        viewModelScope.launch {
            val result: ImportResult = repository.applyImport(review.plan, mode)
            _import.value = ImportUi.Idle
            _messages.emit(
                when {
                    result.imported == 0 -> "Nothing new to import"
                    mode == ImportMode.Replace -> "Log replaced with ${result.imported} fill-ups"
                    else -> "Imported ${result.imported} fill-up${if (result.imported == 1) "" else "s"}"
                },
            )
        }
    }

    fun dismissImport() {
        if (_import.value !is ImportUi.Importing) _import.value = ImportUi.Idle
    }

    suspend fun csvText(): String = Csv.format(repository.getAll())

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            try {
                val text = csvText()
                withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("no output stream")
                }
                _messages.emit("Saved ${entries.value?.size ?: 0} fill-ups to CSV")
            } catch (e: Exception) {
                _messages.emit("Export failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun readText(uri: Uri): String {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val buffer = input.readNBytesCompat(MAX_IMPORT_BYTES + 1)
            if (buffer.size > MAX_IMPORT_BYTES) throw IllegalArgumentException("file is larger than 5 MB")
            buffer
        } ?: throw IllegalArgumentException("file couldn't be opened")
        return bytes.toString(Charsets.UTF_8)
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "CSV file"

    companion object {
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(chunk, 0, minOf(chunk.size, limit - out.size()))
        if (n < 0) break
        out.write(chunk, 0, n)
    }
    return out.toByteArray()
}
