package com.tuttoposto.gastracker.widget

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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tuttoposto.gastracker.GasTrackerApp
import com.tuttoposto.gastracker.MainActivity
import com.tuttoposto.gastracker.R
import com.tuttoposto.gastracker.data.formatEfficiency
import com.tuttoposto.gastracker.data.formatLitres
import com.tuttoposto.gastracker.data.formatMoneyWhole
import com.tuttoposto.gastracker.data.lifetime
import com.tuttoposto.gastracker.data.summary
import com.tuttoposto.gastracker.ui.theme.Gold
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private data class Snapshot(
    val monthName: String,
    val monthCents: Long,
    val monthLitres: Double,
    val monthCount: Int,
    val lPer100km: Double?,
)

private suspend fun snapshot(context: Context): Snapshot {
    val all = (context.applicationContext as GasTrackerApp).repository.getAll()
    val today = LocalDate.now()
    val month = YearMonth.from(today)
    val inMonth = all.filter { YearMonth.from(it.date) == month }.summary()
    return Snapshot(
        monthName = today.month.getDisplayName(JavaTextStyle.SHORT, Locale.US),
        monthCents = inMonth.spentCents,
        monthLitres = inMonth.litres,
        monthCount = inMonth.count,
        lPer100km = all.lifetime().lPer100km,
    )
}

private val Background = ColorProvider(Color(0xFF000000))
private val Accent = ColorProvider(Gold)
private val Label = ColorProvider(Color(0xFFB9B3A8))

@Composable
private fun Frame(content: @Composable () -> Unit) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Background)
            .cornerRadius(20.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun Drop(size: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_widget_drop),
        contentDescription = null,
        colorFilter = ColorFilter.tint(Accent),
        modifier = GlanceModifier.size(size.dp),
    )
}

@Composable
private fun WideContent(s: Snapshot, subtitle: String) {
    Frame {
        Drop(34)
        Spacer(GlanceModifier.width(12.dp))
        Column(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.monthCount == 0) "$0" else formatMoneyWhole(s.monthCents),
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Accent),
                maxLines = 1,
            )
            Text(subtitle, style = TextStyle(fontSize = 12.sp, color = Label), maxLines = 1)
        }
    }
}

/** 1×1: the month's spend. */
class CompactWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshot(context)
        provideContent {
            Frame {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Drop(18)
                    Spacer(GlanceModifier.height(2.dp))
                    Text(
                        formatMoneyWhole(s.monthCents),
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Accent),
                        maxLines = 1,
                    )
                    Text(s.monthName, style = TextStyle(fontSize = 10.sp, color = Label), maxLines = 1)
                }
            }
        }
    }
}

/** 2×1: the month's spend, litres and fill count. */
class WideWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshot(context)
        provideContent {
            WideContent(
                s,
                if (s.monthCount == 0) "${s.monthName} · no fill-ups"
                else "${s.monthName} · ${formatLitres(s.monthLitres, 1)} · ${s.monthCount}×",
            )
        }
    }
}

/** 2×1: the month's spend with lifetime fuel economy. */
class EconomyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val s = snapshot(context)
        provideContent {
            WideContent(
                s,
                s.lPer100km?.let { "${s.monthName} · ${formatEfficiency(it)} L/100km" } ?: "${s.monthName} · economy —",
            )
        }
    }
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactWidget()
}

class WideWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WideWidget()
}

class EconomyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EconomyWidget()
}

object Widgets {
    suspend fun refreshAll(context: Context) {
        CompactWidget().updateAll(context)
        WideWidget().updateAll(context)
        EconomyWidget().updateAll(context)
    }
}
