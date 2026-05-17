package com.example.gastracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gastracker.GasTrackerApp
import com.example.gastracker.MainActivity
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

class GasTrackerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as GasTrackerApp).repository
        val allEntries = repository.observeAll().first()
        val ym = YearMonth.from(LocalDate.now())
        val monthEntries = allEntries.filter { YearMonth.from(it.date) == ym }
        val totalCents = monthEntries.sumOf { it.totalCostCents }
        val totalLitres = monthEntries.sumOf { it.litres }
        val count = monthEntries.size

        provideContent {
            WidgetContent(totalCents = totalCents, totalLitres = totalLitres, count = count)
        }
    }

    @Composable
    private fun WidgetContent(totalCents: Long, totalLitres: Double, count: Int) {
        GlanceTheme {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surface)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "This month",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(4.dp))
                if (count == 0) {
                    Text(
                        text = "No fill-ups yet",
                        style = TextStyle(
                            fontSize = 16.sp,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )
                } else {
                    Text(
                        text = "$%.2f".format(totalCents / 100.0),
                        style = TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    Text(
                        text = "${"%.1f".format(totalLitres)} L · " +
                            "$count fill-up${if (count == 1) "" else "s"}",
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}
