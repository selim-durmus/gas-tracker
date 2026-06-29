package com.example.gastracker.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.example.gastracker.data.FillUp
import com.example.gastracker.data.FillUpWithEfficiency
import com.example.gastracker.data.formatPrice
import com.example.gastracker.data.shareCsvExport
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// Pattern-only ofPattern uses the default FORMAT locale at creation.
private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy")

private const val UNDO_TIMEOUT_MS = 2_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillUpListScreen(
    fillUps: List<FillUpWithEfficiency>,
    lastDeleted: StateFlow<FillUp?>,
    onAddClick: () -> Unit,
    onRowClick: (FillUp) -> Unit,
    onDelete: (FillUp) -> Unit,
    onUndoDelete: () -> Unit,
    onUndoExpired: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted by lastDeleted.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_TIMEOUT_MS) {
            snackbarHostState.showSnackbar(
                message = "Entry deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete()
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
            onUndoExpired()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gas Tracker", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (fillUps.isNotEmpty()) {
                        IconButton(onClick = {
                            shareCsvExport(context, fillUps.map { it.fillUp })
                        }) {
                            Icon(
                                imageVector = Icons.Filled.IosShare,
                                contentDescription = "Export CSV",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add fill-up")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (fillUps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No fill-ups yet.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
            var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }

            val years = remember(fillUps) {
                fillUps.map { it.fillUp.date.year }.distinct().sortedDescending()
            }
            // Months available within the selected year (descending). Empty when no year picked.
            val months = remember(fillUps, selectedYear) {
                selectedYear?.let { yr ->
                    fillUps.filter { it.fillUp.date.year == yr }
                        .map { it.fillUp.date.monthValue }
                        .distinct()
                        .sortedDescending()
                } ?: emptyList()
            }
            // Keep the month selection valid when the year changes.
            LaunchedEffect(selectedYear, months) {
                if (selectedMonth != null && selectedMonth !in months) selectedMonth = null
            }

            val filtered = remember(fillUps, selectedYear, selectedMonth) {
                fillUps.filter { item ->
                    val d = item.fillUp.date
                    (selectedYear == null || d.year == selectedYear) &&
                        (selectedMonth == null || d.monthValue == selectedMonth)
                }
            }

            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                FilterBar(
                    years = years,
                    months = months,
                    selectedYear = selectedYear,
                    selectedMonth = selectedMonth,
                    onYearSelected = { selectedYear = it },
                    onMonthSelected = { selectedMonth = it },
                )
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No fill-ups for this period.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.fillUp.id }) { item ->
                            SwipeableRow(
                                item = item,
                                onClick = { onRowClick(item.fillUp) },
                                onDismiss = { onDelete(item.fillUp) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    years: List<Int>,
    months: List<Int>,
    selectedYear: Int?,
    selectedMonth: Int?,
    onYearSelected: (Int?) -> Unit,
    onMonthSelected: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdown(
            label = selectedYear?.toString() ?: "All years",
            active = selectedYear != null,
            options = buildList {
                add(null to "All years")
                years.forEach { add(it to it.toString()) }
            },
            onSelect = onYearSelected,
        )
        FilterDropdown(
            label = selectedMonth?.let { monthName(it) } ?: "All months",
            active = selectedMonth != null,
            enabled = selectedYear != null && months.isNotEmpty(),
            options = buildList {
                add(null to "All months")
                months.forEach { add(it to monthName(it)) }
            },
            onSelect = onMonthSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    active: Boolean,
    options: List<Pair<Int?, String>>,
    onSelect: (Int?) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = active,
            enabled = enabled,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun monthName(month: Int): String =
    java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    item: FillUpWithEfficiency,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
        },
        backgroundContent = {
            val swiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val progress = if (swiping) dismissState.progress.coerceIn(0f, 1f) else 0f
            val bg = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.error,
                progress,
            )
            val iconColor = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.onError,
                progress,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = iconColor,
                )
            }
        },
    ) {
        FillUpRow(item = item, onClick = onClick)
    }
}

@Composable
private fun FillUpRow(item: FillUpWithEfficiency, onClick: () -> Unit) {
    val fillUp = item.fillUp
    val efficiency = item.lPer100km
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fillUp.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = buildString {
                append(fillUp.pricePerLiter.formatPrice())
                append(" · ")
                append("%.2f".format(fillUp.litres))
                append(" L")
                if (efficiency != null) {
                    append(" · ")
                    // A tilde flags an averaged reading that spans a partial fill.
                    if (item.isCombined) append("~")
                    append("%.1f".format(efficiency))
                    append(" L/100km")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.isCombined) {
                Text(
                    text = "combined over ${item.fillsCovered} fills since last odometer reading",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = fillUp.totalCost.format(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
