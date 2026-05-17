package com.example.gastracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.gastracker.GasTrackerApp
import com.example.gastracker.MainActivity
import com.example.gastracker.R
import com.example.gastracker.ui.theme.Gold
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

private data class WidgetSnapshot(
    val totalCents: Long,
    val totalLitres: Double,
    val count: Int,
)

private suspend fun fetchThisMonth(context: Context): WidgetSnapshot {
    val repository = (context.applicationContext as GasTrackerApp).repository
    val allEntries = repository.observeAll().first()
    val ym = YearMonth.from(LocalDate.now())
    val monthEntries = allEntries.filter { YearMonth.from(it.date) == ym }
    return WidgetSnapshot(
        totalCents = monthEntries.sumOf { it.totalCostCents },
        totalLitres = monthEntries.sumOf { it.litres },
        count = monthEntries.size,
    )
}

private fun formatDollars(cents: Long): String = "$%.2f".format(cents / 100.0)

private val WidgetBackground: Color = Color.Black
private val LabelColor: Color = Color(0xFFB0B0B0)
private val ValueColor: Color = Color.White
private val AccentColor: Color = Gold

class GasTrackerCompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = fetchThisMonth(context)
        provideContent {
            CompactContent(snap)
        }
    }

    @Composable
    private fun CompactContent(snap: WidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetBackground)
                .padding(horizontal = 6.dp, vertical = 8.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_pump),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(AccentColor)),
                modifier = GlanceModifier.size(20.dp),
            )
            Spacer(modifier = GlanceModifier.height(3.dp))
            Text(
                text = if (snap.count == 0) "—" else formatDollars(snap.totalCents),
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(AccentColor),
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = "this month",
                style = TextStyle(
                    fontSize = 9.sp,
                    color = ColorProvider(LabelColor),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

class GasTrackerWideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = fetchThisMonth(context)
        provideContent {
            WideContent(snap)
        }
    }

    @Composable
    private fun WideContent(snap: WidgetSnapshot) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetBackground)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "This month",
                style = TextStyle(
                    fontSize = 13.sp,
                    color = ColorProvider(LabelColor),
                ),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            if (snap.count == 0) {
                Text(
                    text = "No fill-ups",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(ValueColor),
                    ),
                )
            } else {
                Text(
                    text = formatDollars(snap.totalCents),
                    style = TextStyle(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(AccentColor),
                    ),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = "${"%.1f".format(snap.totalLitres)} L · " +
                        "${snap.count} fill-up${if (snap.count == 1) "" else "s"}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = ColorProvider(LabelColor),
                    ),
                )
            }
        }
    }
}
