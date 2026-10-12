package com.tuttoposto.gastracker.ui.log

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.PeriodSummary
import com.tuttoposto.gastracker.data.formatEfficiency
import com.tuttoposto.gastracker.data.formatKm
import com.tuttoposto.gastracker.data.formatLitres
import com.tuttoposto.gastracker.data.formatMoney
import com.tuttoposto.gastracker.data.formatPrice
import com.tuttoposto.gastracker.data.monthYear
import com.tuttoposto.gastracker.ui.components.EmptyState
import com.tuttoposto.gastracker.ui.components.Pill
import com.tuttoposto.gastracker.ui.theme.GasTheme
import kotlinx.coroutines.launch
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LogScreen(
    viewModel: LogViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onImport: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    LaunchedEffect(viewModel) {
        viewModel.deleted.collect { removed ->
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar(
                    message = "Fill-up from ${removed.date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US))} deleted",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(removed)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Gas Tracker") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        floatingActionButton = {
            if (!state.isEmpty) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    expanded = fabExpanded,
                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                    text = { Text("Add fill-up") },
                    modifier = Modifier.testTag("add_fill_up"),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding))
            state.isEmpty -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.LocalGasStation,
                    title = "No fill-ups yet",
                    body = "Log your first trip to the pump, or bring over your history from a CSV export.",
                    action = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FilledTonalButton(onClick = onAdd, modifier = Modifier.testTag("add_first")) {
                                Icon(Icons.Outlined.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add fill-up")
                            }
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.TextButton(onClick = onImport) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import CSV")
                            }
                        }
                    },
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding).testTag("log_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "hero") {
                    MonthHero(thisMonth = state.thisMonth, lastMonth = state.lastMonth, monthName = state.today.month.getDisplayName(TextStyle.FULL, Locale.US))
                }
                state.groups.forEach { group ->
                    stickyHeader(key = "h-${group.month}") {
                        MonthHeader(group)
                    }
                    items(group.rows, key = { it.fillUp.id }) { row ->
                        SwipeToDelete(
                            onDelete = { viewModel.delete(row.fillUp) },
                            modifier = Modifier.animateItem(),
                        ) {
                            FillUpCard(row = row, onClick = { onOpen(row.fillUp.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHero(thisMonth: PeriodSummary, lastMonth: PeriodSummary, monthName: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Spent in $monthName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                formatMoney(thisMonth.spentCents),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("month_total"),
            )
            Spacer(Modifier.height(6.dp))
            val detail = buildString {
                append(formatLitres(thisMonth.litres, 1))
                append(" · ")
                append(thisMonth.count).append(if (thisMonth.count == 1) " fill-up" else " fill-ups")
                if (lastMonth.count > 0) append(" · last month ").append(formatMoney(lastMonth.spentCents))
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun MonthHeader(group: MonthGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            group.month.format(monthYear),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${formatMoney(group.summary.spentCents)} · ${formatLitres(group.summary.litres, 1)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            val active = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(
                if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "swipe-bg",
            )
            Box(
                Modifier.fillMaxSize().background(bg, RoundedCornerShape(18.dp)).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
        content = { content() },
    )
}

@Composable
private fun FillUpCard(row: LogRow, onClick: () -> Unit) {
    val f: FillUp = row.fillUp
    val seg = row.efficiency
    val description = buildString {
        append(f.date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)))
        append(", ").append(formatMoney(f.totalCostCents))
        append(", ").append(formatLitres(f.litres)).append(" at ").append(formatPrice(f.pricePerLitreMills)).append(" per litre")
        if (seg != null) append(", ").append(formatEfficiency(seg.lPer100km)).append(" litres per 100 kilometres")
        if (!f.fullTank) append(", partial fill")
        if (row.partialHint != null) append(", may have been a partial fill")
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DateBadge(f)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${formatPrice(f.pricePerLitreMills)}/L · ${formatLitres(f.litres)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (seg != null) {
                        Pill(
                            (if (seg.isCombined) "~" else "") + formatEfficiency(seg.lPer100km) + " L/100km",
                            container = MaterialTheme.colorScheme.secondaryContainer,
                        )
                    }
                    if (row.partialHint != null) {
                        Pill(
                            "Partial?",
                            container = MaterialTheme.colorScheme.errorContainer,
                            content = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (!f.fullTank) {
                        Pill("Partial", container = MaterialTheme.colorScheme.surfaceContainerHighest, content = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val odo = f.odometerKm
                    if (odo != null) {
                        Text(formatKm(odo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (f.note.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(f.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(formatMoney(f.totalCostCents), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun DateBadge(f: FillUp) {
    Column(
        modifier = Modifier
            .size(width = 48.dp, height = 52.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            f.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = GasTheme.extra.chartPrimary,
        )
        Text(
            f.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
