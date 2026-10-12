package com.tuttoposto.gastracker.ui.stats

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.gastracker.data.Dashboard
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.YearSection
import com.tuttoposto.gastracker.data.chronological
import com.tuttoposto.gastracker.data.formatEfficiency
import com.tuttoposto.gastracker.data.formatKm
import com.tuttoposto.gastracker.data.formatLitres
import com.tuttoposto.gastracker.data.formatMoney
import com.tuttoposto.gastracker.data.formatMoneyWhole
import com.tuttoposto.gastracker.data.formatPercent
import com.tuttoposto.gastracker.data.formatPrice
import com.tuttoposto.gastracker.data.shortDate
import com.tuttoposto.gastracker.ui.components.Bar
import com.tuttoposto.gastracker.ui.components.BarChart
import com.tuttoposto.gastracker.ui.components.EmptyState
import com.tuttoposto.gastracker.ui.components.LegendDot
import com.tuttoposto.gastracker.ui.components.LinePoint
import com.tuttoposto.gastracker.ui.components.LineChart
import com.tuttoposto.gastracker.ui.components.SectionCard
import com.tuttoposto.gastracker.ui.components.SectionTitle
import com.tuttoposto.gastracker.ui.components.StatTile
import com.tuttoposto.gastracker.ui.theme.GasTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private enum class Window(val label: String, val months: Long?) {
    M3("3M", 3), M6("6M", 6), Y1("1Y", 12), All("All", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: StatsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stats") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
    ) { padding ->
        val dashboard = state.dashboard
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding))
            dashboard == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.Insights, "Nothing to chart yet", "Stats appear once you've logged a fill-up.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { OverviewTiles(dashboard) }
                item { EconomyTiles(dashboard) }
                item { MonthlySpendCard(dashboard) }
                item { PriceCard(state.entries, dashboard.today) }
                item { EconomyCard(dashboard) }
                item { HistoryCard(dashboard.history) }
            }
        }
    }
}

@Composable
private fun OverviewTiles(d: Dashboard) {
    val delta = if (d.lastMonth.spentCents > 0) {
        (d.thisMonth.spentCents - d.lastMonth.spentCents).toDouble() / d.lastMonth.spentCents
    } else null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "This month",
                value = formatMoney(d.thisMonth.spentCents),
                caption = delta?.let {
                    (if (it >= 0) "▲ " else "▼ ") + formatPercent(kotlin.math.abs(it)) + " vs " +
                        d.today.minusMonths(1).month.getDisplayName(TextStyle.FULL, Locale.US)
                }
                    ?: "${d.thisMonth.count} fill-ups",
                captionColor = when {
                    delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    delta > 0 -> GasTheme.extra.bad
                    else -> GasTheme.extra.good
                },
                icon = Icons.Outlined.Payments,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "${d.today.year}",
                value = formatMoneyWhole(d.thisYear.spentCents),
                caption = "${formatLitres(d.thisYear.litres, 0)} · ${d.thisYear.count} fill-ups",
                icon = Icons.Outlined.LocalGasStation,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Avg price",
                value = d.lifetime.total.avgPriceMills?.let { formatPrice(it) + "/L" } ?: "—",
                caption = "All time, by volume",
                icon = Icons.Outlined.WaterDrop,
                modifier = Modifier.weight(1f),
            )
            val total = d.lifetime.total
            if (d.lifetime.firstDate?.year != d.today.year) {
                StatTile(
                    label = "All time",
                    value = formatMoneyWhole(total.spentCents),
                    caption = "${formatLitres(total.litres, 0)} since ${d.lifetime.firstDate?.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)) ?: "—"}",
                    icon = Icons.Outlined.Insights,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // While everything is from this year, "all time" would just repeat the year tile.
                StatTile(
                    label = "Avg fill-up",
                    value = formatMoney(total.spentCents / total.count),
                    caption = "${formatLitres(total.litres / total.count, 1)} per visit",
                    icon = Icons.Outlined.Insights,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun EconomyTiles(d: Dashboard) {
    val l = d.lifetime
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            label = "Economy",
            value = l.lPer100km?.let { formatEfficiency(it) } ?: "—",
            caption = d.latestEfficiency?.let { "L/100km · last ${formatEfficiency(it.lPer100km)}" }
                ?: "Needs two full fills with odometer",
            icon = Icons.Outlined.Speed,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Per 100 km",
            value = l.costPer100kmCents?.let { formatMoney(it) } ?: "—",
            caption = l.kmTracked?.let { "over ${formatKm(it)}" } ?: "Fuel cost per distance",
            icon = Icons.Outlined.Route,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthlySpendCard(d: Dashboard) {
    var selected by rememberSaveable { mutableStateOf<Int?>(null) }
    // Start at the first month with data (but show at least six), not a run of empty bars.
    val months = remember(d.monthlySpend) {
        val first = d.monthlySpend.indexOfFirst { it.second > 0 }.coerceAtLeast(0)
        d.monthlySpend.drop(minOf(first, d.monthlySpend.size - 6))
    }
    val bars = remember(months) {
        months.map { (m, cents) -> Bar(m.month.getDisplayName(TextStyle.SHORT, Locale.US), cents / 100.0) }
    }
    val index = (selected ?: bars.lastIndex).coerceIn(0, bars.lastIndex)
    val (month, cents) = months[index]
    SectionCard {
        SectionTitle("Monthly spend")
        Spacer(Modifier.height(2.dp))
        Text(
            "${formatMoney(cents)} in ${month.month.getDisplayName(TextStyle.FULL, Locale.US)} ${month.year}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        BarChart(
            bars = bars,
            selectedIndex = index,
            onSelect = { selected = it },
            description = "Monthly spend by month",
        )
    }
}

@Composable
private fun WindowChips(selected: Window, onSelect: (Window) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Window.entries.forEach { w ->
            FilterChip(
                selected = w == selected,
                onClick = { onSelect(w) },
                label = { Text(w.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

private fun cutoff(today: LocalDate, window: Window): Long? =
    window.months?.let { today.minusMonths(it).toEpochDay() }

@Composable
private fun PriceCard(entries: List<FillUp>, today: LocalDate) {
    var window by rememberSaveable { mutableStateOf(Window.Y1) }
    var selected by remember(window, entries) { mutableStateOf<Int?>(null) }
    val points = remember(entries, window, today) {
        val from = cutoff(today, window)
        entries.chronological().filter { from == null || it.dateEpochDay >= from }
    }
    SectionCard {
        SectionTitle("Price per litre")
        Spacer(Modifier.height(8.dp))
        WindowChips(window) { window = it }
        Spacer(Modifier.height(8.dp))
        if (points.size < 2) {
            ChartPlaceholder("Need at least two fill-ups in this range.")
        } else {
            val index = selected ?: points.lastIndex
            val p = points[index]
            val avg = points.sumOf { it.totalCostCents } * 10.0 / points.sumOf { it.litres }
            Text(
                "${formatPrice(p.pricePerLitreMills)}/L on ${p.date.format(shortDate)} · range avg ${formatPrice(Math.round(avg))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LineChart(
                points = points.map { LinePoint(it.dateEpochDay.toDouble(), it.pricePerLitreMills / 1000.0) },
                selectedIndex = index,
                onSelect = { selected = it },
                yLabel = { String.format(Locale.US, "$%.3f", it) },
                description = "Price per litre over time",
                referenceY = avg / 1000.0,
            )
        }
    }
}

@Composable
private fun EconomyCard(d: Dashboard) {
    var window by rememberSaveable { mutableStateOf(Window.All) }
    var selected by remember(window, d.segments) { mutableStateOf<Int?>(null) }
    val segments = remember(d.segments, window, d.today) {
        val from = cutoff(d.today, window)
        d.segments.filter { from == null || it.endDateEpochDay >= from }
    }
    SectionCard {
        SectionTitle("Fuel economy")
        Spacer(Modifier.height(8.dp))
        WindowChips(window) { window = it }
        Spacer(Modifier.height(8.dp))
        if (segments.size < 2) {
            ChartPlaceholder("Log odometer readings at full fills to see economy here.")
        } else {
            val index = selected ?: segments.lastIndex
            val s = segments[index]
            Text(
                "${formatEfficiency(s.lPer100km)} L/100km over ${formatKm(s.distanceKm)}" +
                    " ending ${LocalDate.ofEpochDay(s.endDateEpochDay).format(shortDate)}" +
                    if (s.isCombined) " · ${s.fillsCovered} fills" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LineChart(
                points = segments.map { LinePoint(it.endDateEpochDay.toDouble(), it.lPer100km, hollow = it.isCombined) },
                selectedIndex = index,
                onSelect = { selected = it },
                yLabel = { String.format(Locale.US, "%.1f", it) },
                description = "Fuel economy in litres per 100 kilometres over time",
                color = GasTheme.extra.chartSecondary,
                referenceY = d.lifetime.lPer100km,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(GasTheme.extra.chartSecondary, "Tank to tank")
                LegendDot(MaterialTheme.colorScheme.outline, "Hollow: spans a partial fill")
            }
        }
    }
}

@Composable
private fun ChartPlaceholder(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryCard(history: List<YearSection>) {
    SectionCard {
        SectionTitle("History")
        history.forEach { year ->
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(year.year.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(
                    "${formatMoney(year.summary.spentCents)} · ${formatLitres(year.summary.litres, 0)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            year.months.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.month.month.getDisplayName(TextStyle.FULL, Locale.US), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            buildString {
                                append(formatLitres(m.summary.litres, 1))
                                m.summary.avgPriceMills?.let { append(" · avg ").append(formatPrice(it)).append("/L") }
                                append(" · ").append(m.summary.count).append(if (m.summary.count == 1) " fill" else " fills")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(formatMoney(m.summary.spentCents), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
