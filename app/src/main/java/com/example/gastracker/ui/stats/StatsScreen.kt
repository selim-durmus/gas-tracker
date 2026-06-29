package com.example.gastracker.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gastracker.data.FillUp
import com.example.gastracker.data.FillUpWithEfficiency
import com.example.gastracker.data.HistorySection
import com.example.gastracker.data.LifetimeSummary
import com.example.gastracker.data.MonthSummary
import com.example.gastracker.data.YearSummary
import com.example.gastracker.ui.theme.ChartBlue
import com.example.gastracker.ui.theme.ChartGold
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.Axis
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import kotlin.math.abs
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// Pattern-only ofPattern uses the default FORMAT locale at creation.
private val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM")
private val chartDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d")

private enum class TimeWindow(val label: String, val days: Int?) {
    Days30("30d", 30),
    Days90("90d", 90),
    Year1("1y", 365),
    All("All", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    fillUps: List<FillUp>,
    efficiency: List<FillUpWithEfficiency>,
    history: List<HistorySection>,
    lifetime: LifetimeSummary,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Stats", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
    ) { innerPadding ->
        if (fillUps.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Add a fill-up to see stats.")
            }
            return@Scaffold
        }

        val today = remember { LocalDate.now() }
        val thisMonth = remember(fillUps, today) {
            val ym = YearMonth.from(today)
            buildMonthSummary(ym, fillUps.filter { YearMonth.from(it.date) == ym })
        }
        val thisYear = remember(fillUps, today) {
            buildYearSummary(today.year, fillUps.filter { it.date.year == today.year })
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SummaryCardsRow(thisMonth = thisMonth, thisYear = thisYear) }
            item { LifetimeRow(lifetime = lifetime) }
            item { ChartCard(fillUps = fillUps) }
            item { EfficiencyChartCard(efficiency = efficiency) }

            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            history.forEach { section ->
                item(key = "year-${section.year.year}") {
                    YearHeader(section.year)
                }
                items(section.months, key = { "month-${it.yearMonth}" }) { month ->
                    MonthRow(month)
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun buildMonthSummary(ym: YearMonth, entries: List<FillUp>): MonthSummary =
    MonthSummary(
        yearMonth = ym,
        totalSpentCents = entries.sumOf { it.totalCostCents },
        totalLitres = entries.sumOf { it.litres },
        fillUpCount = entries.size,
    )

private fun buildYearSummary(year: Int, entries: List<FillUp>): YearSummary =
    YearSummary(
        year = year,
        totalSpentCents = entries.sumOf { it.totalCostCents },
        totalLitres = entries.sumOf { it.litres },
        fillUpCount = entries.size,
    )

@Composable
private fun SummaryCardsRow(thisMonth: MonthSummary, thisYear: YearSummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryCard(
            title = "This month",
            primary = thisMonth.totalSpent.format(),
            secondary = "${"%.1f".format(thisMonth.totalLitres)} L · ${thisMonth.fillUpCount} fill-up${if (thisMonth.fillUpCount == 1) "" else "s"}",
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "This year",
            primary = thisYear.totalSpent.format(),
            secondary = "${"%.0f".format(thisYear.totalLitres)} L · ${thisYear.fillUpCount} fill-up${if (thisYear.fillUpCount == 1) "" else "s"}",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = primary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LifetimeRow(lifetime: LifetimeSummary) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lifetime",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = lifetime.totalSpent.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${"%.0f".format(lifetime.totalLitres)} L · avg \$${"%.3f".format(lifetime.avgPricePerLiterCents / 100.0)}/L",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                lifetime.avgLper100km?.let { eff ->
                    Text(
                        text = "${"%.1f".format(eff)} L/100km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartCard(fillUps: List<FillUp>) {
    var window by rememberSaveable { mutableStateOf(TimeWindow.All) }

    val filtered = remember(fillUps, window) {
        val cutoff = window.days?.let { LocalDate.now().minusDays(it.toLong()).toEpochDay() }
        if (cutoff == null) fillUps else fillUps.filter { it.dateEpochDay >= cutoff }
    }
    val sorted = remember(filtered) { filtered.sortedBy { it.date } }
    val baseDay = remember(sorted) { sorted.firstOrNull()?.dateEpochDay ?: 0L }
    val xValues = remember(sorted) {
        sorted.map { (it.dateEpochDay - baseDay).toFloat() }
    }
    val pricePerLiterSeries = remember(sorted) {
        sorted.map { it.pricePerLiterCents / 100.0 }
    }
    val totalCostSeries = remember(sorted) {
        sorted.map { it.totalCostCents / 100.0 }
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(sorted) {
        if (sorted.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries { series(x = xValues, y = pricePerLiterSeries) }
            lineSeries { series(x = xValues, y = totalCostSeries) }
        }
    }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Price/L vs total $",
                    style = MaterialTheme.typography.titleSmall,
                )
                TimeWindowSelector(selected = window, onSelect = { window = it })
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (sorted.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Need at least 2 fill-ups in this window.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val priceLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(ChartBlue)),
                )
                val totalLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(ChartGold)),
                )

                val priceFormatter = CartesianValueFormatter { _, value, _ -> "%.2f".format(value) }
                val totalFormatter = CartesianValueFormatter { _, value, _ -> "\$%.0f".format(value) }
                val dateFormatter = CartesianValueFormatter { _, value, _ ->
                    val date = LocalDate.ofEpochDay(baseDay + value.toLong())
                    chartDateFormatter.format(date)
                }

                val bottomSpacing = (xValues.size / 5).coerceAtLeast(1)
                val tightRange = remember { TightRangeProvider() }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(priceLine),
                            rangeProvider = tightRange,
                            verticalAxisPosition = Axis.Position.Vertical.Start,
                        ),
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(totalLine),
                            rangeProvider = tightRange,
                            verticalAxisPosition = Axis.Position.Vertical.End,
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = priceFormatter,
                        ),
                        endAxis = VerticalAxis.rememberEnd(
                            valueFormatter = totalFormatter,
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = dateFormatter,
                            itemPlacer = remember(bottomSpacing) {
                                HorizontalAxis.ItemPlacer.aligned(spacing = { bottomSpacing })
                            },
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    scrollState = rememberVicoScrollState(),
                )

                Spacer(modifier = Modifier.height(6.dp))
                ChartLegend()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EfficiencyChartCard(efficiency: List<FillUpWithEfficiency>) {
    var window by rememberSaveable { mutableStateOf(TimeWindow.All) }

    // Only fill-ups that produced an efficiency measurement, sorted chronologically.
    val points = remember(efficiency, window) {
        val cutoff = window.days?.let { LocalDate.now().minusDays(it.toLong()).toEpochDay() }
        efficiency
            .filter { it.lPer100km != null }
            .filter { cutoff == null || it.fillUp.dateEpochDay >= cutoff }
            .sortedBy { it.fillUp.dateEpochDay }
    }
    val baseDay = remember(points) { points.firstOrNull()?.fillUp?.dateEpochDay ?: 0L }
    val xValues = remember(points) { points.map { (it.fillUp.dateEpochDay - baseDay).toFloat() } }
    val effSeries = remember(points) { points.map { it.lPer100km!! } }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        if (points.size < 2) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries { series(x = xValues, y = effSeries) }
        }
    }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Efficiency (L/100km)",
                    style = MaterialTheme.typography.titleSmall,
                )
                TimeWindowSelector(selected = window, onSelect = { window = it })
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (points.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Add odometer readings to two or more fill-ups in this window.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val effLine = LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(fill(ChartGold)),
                )
                val effFormatter = CartesianValueFormatter { _, value, _ -> "%.1f".format(value) }
                val dateFormatter = CartesianValueFormatter { _, value, _ ->
                    val date = LocalDate.ofEpochDay(baseDay + value.toLong())
                    chartDateFormatter.format(date)
                }
                val bottomSpacing = (xValues.size / 5).coerceAtLeast(1)
                val tightRange = remember { TightRangeProvider() }

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(effLine),
                            rangeProvider = tightRange,
                            verticalAxisPosition = Axis.Position.Vertical.Start,
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            valueFormatter = effFormatter,
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            valueFormatter = dateFormatter,
                            itemPlacer = remember(bottomSpacing) {
                                HorizontalAxis.ItemPlacer.aligned(spacing = { bottomSpacing })
                            },
                        ),
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    scrollState = rememberVicoScrollState(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeWindowSelector(
    selected: TimeWindow,
    onSelect: (TimeWindow) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        TimeWindow.values().forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = TimeWindow.values().size),
                label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(color = ChartBlue, label = "Price/L (left)")
        LegendDot(color = ChartGold, label = "Total $ (right)")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun YearHeader(year: YearSummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = year.year.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${year.totalSpent.format()} · ${"%.1f".format(year.totalLitres)} L",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthRow(month: MonthSummary) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = month.yearMonth.format(monthFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = month.totalSpent.format(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = "${"%.2f".format(month.totalLitres)} L · " +
                "avg \$${"%.3f".format(month.avgPricePerLiterCents / 100.0)}/L · " +
                "${month.fillUpCount} fill-up${if (month.fillUpCount == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private class TightRangeProvider(
    private val paddingFraction: Double = 0.10,
) : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        val range = maxY - minY
        val pad = if (range > 0) range * paddingFraction
                  else maxOf(abs(minY) * paddingFraction, 1.0)
        return minY - pad
    }

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        val range = maxY - minY
        val pad = if (range > 0) range * paddingFraction
                  else maxOf(abs(maxY) * paddingFraction, 1.0)
        return maxY + pad
    }
}
