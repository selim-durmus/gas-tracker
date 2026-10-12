package com.tuttoposto.gastracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tuttoposto.gastracker.ui.theme.GasTheme
import kotlin.math.abs
import kotlin.math.max

data class Bar(val label: String, val value: Double)

/** Vertical bars; tap a bar to select it. The selected bar is drawn in the accent color. */
@Composable
fun BarChart(
    bars: List<Bar>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 168.dp,
) {
    val accent = GasTheme.extra.chartPrimary
    val muted = accent.copy(alpha = 0.32f)
    val grid = GasTheme.extra.chartGrid
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(bars) {
                detectTapGestures { pos ->
                    if (bars.isEmpty()) return@detectTapGestures
                    val slot = size.width / bars.size
                    onSelect((pos.x / slot).toInt().coerceIn(0, bars.lastIndex))
                }
            },
    ) {
        if (bars.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val maxValue = bars.maxOf { it.value }.takeIf { it > 0 } ?: 1.0
        val slot = size.width / bars.size
        val barWidth = slot * 0.62f

        // Baseline and a mid guide.
        drawLine(grid, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.dp.toPx())
        drawLine(
            grid, Offset(0f, chartHeight / 2), Offset(size.width, chartHeight / 2),
            strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        val labelEvery = if (slot < 28.dp.toPx()) 2 else 1
        bars.forEachIndexed { i, bar ->
            val left = i * slot + (slot - barWidth) / 2
            val h = (bar.value / maxValue * (chartHeight - 4.dp.toPx())).toFloat()
            val selected = i == selectedIndex
            if (h > 0f) {
                drawRoundRect(
                    color = if (selected) accent else muted,
                    topLeft = Offset(left, chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
            if (i % labelEvery == (bars.size - 1) % labelEvery || selected) {
                val style = if (selected) labelStyle.copy(color = accent) else labelStyle
                drawCentered(measurer, bar.label, style, i * slot + slot / 2, chartHeight + 3.dp.toPx())
            }
        }
    }
}

data class LinePoint(val x: Double, val y: Double, val hollow: Boolean = false)

/**
 * A single-series line chart with min/max guides. Tap anywhere to select the nearest point;
 * the caller renders the selected value (keeps the chart itself uncluttered).
 */
@Composable
fun LineChart(
    points: List<LinePoint>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    yLabel: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier,
    color: Color = GasTheme.extra.chartPrimary,
    height: Dp = 180.dp,
    referenceY: Double? = null,
) {
    val grid = GasTheme.extra.chartGrid
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { pos ->
                    if (points.isEmpty()) return@detectTapGestures
                    val geo = LineGeometry(points, size.width.toFloat(), size.height.toFloat(), 0f, 10.dp.toPx())
                    val nearest = points.indices.minBy { abs(geo.px(points[it].x) - pos.x) }
                    onSelect(nearest)
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val geo = LineGeometry(points, size.width, size.height, 0f, 10.dp.toPx())

        // Guides at the data's min and max, labelled on the left edge.
        listOf(geo.maxY, geo.minY).distinct().forEach { v ->
            val y = geo.py(v)
            drawLine(
                grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
            val layout = measurer.measure(yLabel(v), labelStyle)
            val top = (y - layout.size.height - 2.dp.toPx()).coerceAtLeast(0f)
            drawText(layout, topLeft = Offset(0f, top))
        }
        referenceY?.let { ref ->
            val y = geo.py(ref)
            drawLine(color.copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f)))
        }

        val path = Path()
        points.forEachIndexed { i, p ->
            val o = Offset(geo.px(p.x), geo.py(p.y))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        if (points.size > 1) {
            val fill = Path().apply {
                addPath(path)
                lineTo(geo.px(points.last().x), size.height)
                lineTo(geo.px(points.first().x), size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
            drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        points.forEachIndexed { i, p ->
            val o = Offset(geo.px(p.x), geo.py(p.y))
            val selected = i == selectedIndex
            if (selected) {
                drawLine(color.copy(alpha = 0.5f), Offset(o.x, 0f), Offset(o.x, size.height), strokeWidth = 1.dp.toPx())
            }
            drawPoint(o, color, surface, hollow = p.hollow, radius = if (selected) 6.dp.toPx() else 3.5.dp.toPx())
        }
    }
}

private fun DrawScope.drawPoint(center: Offset, color: Color, surface: Color, hollow: Boolean, radius: Float) {
    if (hollow) {
        drawCircle(surface, radius, center)
        drawCircle(color, radius, center, style = Stroke(width = 2.dp.toPx()))
    } else {
        drawCircle(surface, radius + 2.dp.toPx(), center)
        drawCircle(color, radius, center)
    }
}

private class LineGeometry(
    points: List<LinePoint>,
    private val width: Float,
    private val height: Float,
    private val left: Float,
    private val inset: Float = 0f,
) {
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    private val pad = if (maxY > minY) (maxY - minY) * 0.12 else max(abs(maxY) * 0.1, 1.0)
    private val paddedMin = minY - pad
    private val paddedMax = maxY + pad

    fun px(x: Double): Float {
        val usable = width - left - 2 * inset
        return if (maxX == minX) left + inset + usable / 2
        else left + inset + ((x - minX) / (maxX - minX) * usable).toFloat()
    }

    fun py(y: Double): Float {
        val top = 16f
        val usable = height - top - 8f
        return top + ((paddedMax - y) / (paddedMax - paddedMin) * usable).toFloat()
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, cx: Float, top: Float) {
    val layout = measurer.measure(text, style)
    drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, top))
}
