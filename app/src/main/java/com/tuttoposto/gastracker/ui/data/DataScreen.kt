package com.tuttoposto.gastracker.ui.data

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.gastracker.BuildConfig
import com.tuttoposto.gastracker.data.Csv
import com.tuttoposto.gastracker.data.ImportMode
import com.tuttoposto.gastracker.data.formatLong
import com.tuttoposto.gastracker.data.formatMoney
import com.tuttoposto.gastracker.data.isoDate
import com.tuttoposto.gastracker.data.summary
import com.tuttoposto.gastracker.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(viewModel: DataViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val importUi by viewModel.import.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.startImport(uri)
    }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Csv.MIME)) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val exportName = "gas-tracker-${LocalDate.now().format(isoDate)}.csv"
    val hasData = !entries.isNullOrEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & data") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                val list = entries.orEmpty()
                Text("Your log", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${list.size} fill-up${if (list.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag("entry_count"),
                )
                if (list.isNotEmpty()) {
                    val first = list.minOf { it.dateEpochDay }.let(LocalDate::ofEpochDay)
                    val last = list.maxOf { it.dateEpochDay }.let(LocalDate::ofEpochDay)
                    Text(
                        "${first.formatLong()} – ${last.formatLong()} · ${formatMoney(list.summary().spentCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard {
                Text("Export", style = MaterialTheme.typography.titleSmall)
                ActionRow(
                    icon = Icons.Outlined.FileUpload,
                    title = "Save CSV file",
                    body = "Pick a folder, e.g. Downloads or Drive",
                    enabled = hasData,
                    onClick = { createDocument.launch(exportName) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ActionRow(
                    icon = Icons.Outlined.Share,
                    title = "Share CSV",
                    body = "Send to another app",
                    enabled = hasData,
                    onClick = { scope.launch { shareCsv(context, exportName, viewModel.csvText()) } },
                )
            }

            SectionCard {
                Text("Import", style = MaterialTheme.typography.titleSmall)
                ActionRow(
                    icon = Icons.Outlined.FileDownload,
                    title = "Import CSV",
                    body = "Gas Tracker exports from this app or the old one",
                    enabled = true,
                    onClick = { openDocument.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.testTag("import_csv"),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Moving from the old Gas Tracker? Open it, tap its export button and choose this app " +
                            "in the share sheet. You'll see a preview before anything is imported.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "Gas Tracker ${BuildConfig.VERSION_NAME}. Your log stays on this phone and is included in " +
                            "Android's device backup. A copy of the log is kept automatically before every import.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    ImportDialogs(
        ui = importUi,
        onConfirm = viewModel::confirmImport,
        onDismiss = viewModel::dismissImport,
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    body: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}

@Composable
fun ImportDialogs(ui: ImportUi, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    when (ui) {
        ImportUi.Idle -> Unit
        ImportUi.Reading, ImportUi.Importing -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(if (ui == ImportUi.Reading) "Reading file…" else "Importing…") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                }
            },
        )
        is ImportUi.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Can't import this file") },
            text = { Text(ui.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
        is ImportUi.Review -> ReviewDialog(ui, onConfirm, onDismiss)
    }
}

@Composable
private fun ReviewDialog(ui: ImportUi.Review, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    val plan = ui.plan
    var confirmReplace by rememberSaveable { mutableStateOf(false) }
    val rows = plan.rows
    val first = LocalDate.ofEpochDay(rows.minOf { it.dateEpochDay })
    val last = LocalDate.ofEpochDay(rows.maxOf { it.dateEpochDay })

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace your whole log?") },
            text = {
                Text(
                    "Your ${plan.existingCount} current fill-ups will be replaced by the ${rows.size} in this file. " +
                        "A copy of the current log is saved on the phone first.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReplace = false; onConfirm(ImportMode.Replace) }) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
        title = { Text("Import fill-ups") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("import_review")) {
                Text(ui.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${rows.size} fill-ups · ${first.formatLong()} – ${last.formatLong()}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Total ${formatMoney(rows.summary().spentCents)}", style = MaterialTheme.typography.bodyMedium)
                if (plan.parsed.isLegacyFormat) {
                    Text("Exported by the old Gas Tracker app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (plan.existingCount > 0) {
                    Text(
                        "${plan.newRows.size} new · ${plan.duplicateCount} already in your log",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (plan.parsed.errors.isNotEmpty()) {
                    Text(
                        "${plan.parsed.errors.size} row(s) skipped: " +
                            plan.parsed.errors.take(3).joinToString("; ") { "line ${it.line}: ${it.message}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (plan.existingCount == 0) {
                TextButton(onClick = { onConfirm(ImportMode.Merge) }, modifier = Modifier.testTag("import_confirm")) {
                    Text("Import ${rows.size}")
                }
            } else {
                TextButton(
                    onClick = { onConfirm(ImportMode.Merge) },
                    enabled = plan.newRows.isNotEmpty(),
                    modifier = Modifier.testTag("import_confirm"),
                ) { Text("Add ${plan.newRows.size} new") }
            }
        },
        dismissButton = {
            Row {
                if (plan.existingCount > 0) {
                    TextButton(onClick = { confirmReplace = true }) { Text("Replace all") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private suspend fun shareCsv(context: Context, fileName: String, csv: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, fileName).apply { writeText(csv) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = Csv.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "Gas Tracker export")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share Gas Tracker CSV"))
}
