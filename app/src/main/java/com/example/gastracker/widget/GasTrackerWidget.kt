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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.gastracker.GasTrackerApp
import com.example.gastracker.MainActivity
import com.example.gastracker.R
import com.example.gastracker.data.toLifetimeSummary
import com.example.gastracker.ui.theme.Gold
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

private data class WidgetSnapshot(
    val totalCents: Long,
    val totalLitres: Double,
    val count: Int,
    val lifetimeLper100km: Double?,
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
        lifetimeLper100km = allEntries.toLifetimeSummary().avgLper100km,
    )
}

private fun formatDollarsRounded(cents: Long): String = "$${Math.round(cents / 100.0)}"

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_pump),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ColorProvider(AccentColor)),
                modifier = GlanceModifier.size(22.dp),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = if (snap.count == 0) "—" else formatDollarsRounded(snap.totalCents),
                style = TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(AccentColor),
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
        WideLayout(
            snap = snap,
            subtitle = if (snap.count == 0) null
                else "${"%.1f".format(snap.totalLitres)} L · ${snap.count}",
        )
    }
}

class GasTrackerEfficiencyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = fetchThisMonth(context)
        provideContent {
            EfficiencyContent(snap)
        }
    }

    @Composable
    private fun EfficiencyContent(snap: WidgetSnapshot) {
        val subtitle = when {
            snap.count == 0 -> null
            snap.lifetimeLper100km != null ->
                "${"%.1f".format(snap.lifetimeLper100km)} L/100km"
            else -> "${"%.1f".format(snap.totalLitres)} L"
        }
        WideLayout(snap = snap, subtitle = subtitle)
    }
}

@Composable
private fun WideLayout(snap: WidgetSnapshot, subtitle: String?) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_pump),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(AccentColor)),
            modifier = GlanceModifier.size(40.dp),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (snap.count == 0) {
                Text(
                    text = "No fill-ups",
                    style = TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(ValueColor),
                        textAlign = TextAlign.Center,
                    ),
                )
            } else {
                Text(
                    text = formatDollarsRounded(snap.totalCents),
                    style = TextStyle(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(AccentColor),
                        textAlign = TextAlign.Center,
                    ),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = ColorProvider(LabelColor),
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
            }
        }
    }
}
