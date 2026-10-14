package com.tuttoposto.gastracker

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttoposto.gastracker.data.Csv
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Drives the real app (debug package, its own database) on the device. */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as GasTrackerApp
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun clearLog() = runBlocking { app.database.fillUpDao().deleteAll() }

    @After
    fun close() {
        scenario?.close()
        runBlocking { app.database.fillUpDao().deleteAll() }
    }

    private fun count() = runBlocking { app.database.fillUpDao().count() }

    @Test
    fun addEditAndDeleteAFillUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("No fill-ups yet").assertIsDisplayed()
        compose.onNodeWithTag("add_first").performClick()

        compose.onNodeWithTag("save").assertIsNotEnabled()
        // Typed digits only: the price field inserts the decimal itself.
        compose.onNodeWithTag("price").performTextInput("1799")
        compose.onNodeWithTag("price").assertTextContains("1.799")
        // A comma decimal must not turn $45.50 into $4550.
        compose.onNodeWithTag("total").performTextInput("45,50")
        compose.onNodeWithTag("total").assertTextContains("45.50")
        compose.onNodeWithText("≈ 25.29 L pumped").assertIsDisplayed()
        compose.onNodeWithTag("odometer").performTextInput("214970")
        compose.onNodeWithTag("save").assertIsEnabled().performClick()

        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("log_list")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("month_total").assertTextContains("$45.50")
        compose.onNodeWithText("$1.799/L · 25.29 L").assertIsDisplayed()
        assertEquals(1, count())
        val saved = runBlocking { app.repository.getAll().single() }
        assertEquals(1799L, saved.pricePerLitreMills)
        assertEquals(4550L, saved.totalCostCents)

        // Edit: the screen must load this entry (v1 could flash or auto-close on stale state).
        compose.onNodeWithText("$1.799/L · 25.29 L").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Edit fill-up")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("price").assertTextContains("1.799")
        compose.onNodeWithTag("full_tank").performClick()
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Partial")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, count())
        assertEquals(false, runBlocking { app.repository.getAll().single().fullTank })

        // Delete from the edit screen.
        compose.onNodeWithText("$1.799/L · 25.29 L").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("Edit fill-up")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Edit fill-up").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("Delete fill-up")).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("No fill-ups yet")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, count())
    }

    @Test
    fun aSuspiciouslySmallFillIsFlaggedAsPossiblyPartial() {
        runBlocking {
            // Four normal ~9 L/100km tanks ending at 211,889 km.
            listOf(
                Triple("2026-07-06", 6871L, 210898L), Triple("2026-07-15", 7100L, 211406L),
                Triple("2026-07-27", 7690L, 211889L), Triple("2026-06-25", 6666L, 210415L),
            ).forEach { (d, cents, odo) ->
                app.repository.upsert(
                    com.tuttoposto.gastracker.data.FillUp(
                        dateEpochDay = java.time.LocalDate.parse(d).toEpochDay(),
                        pricePerLitreMills = 1550, totalCostCents = cents, odometerKm = odo,
                    ),
                )
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("add_fill_up").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("New fill-up")).fetchSemanticsNodes().isNotEmpty() }
        // $30 (~19 L) after 600 km is ~3 L/100km: not a full tank.
        compose.onNodeWithTag("price").performTextInput("1550")
        compose.onNodeWithTag("total").performTextInput("30")
        compose.onNodeWithTag("odometer").performTextInput("212489")
        compose.onNodeWithTag("full_tank_hint", useUnmergedTree = true).assertTextContains("much less fuel than usual", substring = true)
        compose.onNodeWithTag("full_tank").performClick()
        compose.onNodeWithTag("full_tank_hint", useUnmergedTree = true).assertTextContains("Partial fill", substring = true)
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5_000) { count() == 5 }
        assertEquals(1, runBlocking { app.repository.getAll().count { !it.fullTank } })
    }

    @Test
    fun aLongGapSuggestsAMissedFillUpAndTheSwitchSkipsThatReading() {
        runBlocking {
            // Four tanks of ~490 km each, ending at 211,889 km.
            listOf(
                Triple("2026-06-25", 6666L, 210415L), Triple("2026-07-06", 6871L, 210898L),
                Triple("2026-07-15", 7100L, 211406L), Triple("2026-07-27", 7690L, 211889L),
            ).forEach { (d, cents, odo) ->
                app.repository.upsert(
                    com.tuttoposto.gastracker.data.FillUp(
                        dateEpochDay = java.time.LocalDate.parse(d).toEpochDay(),
                        pricePerLitreMills = 1550, totalCostCents = cents, odometerKm = odo,
                    ),
                )
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("add_fill_up").performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("New fill-up")).fetchSemanticsNodes().isNotEmpty() }
        // One normal tank's worth of fuel after ~1,000 km: a fill-up in between was never logged.
        compose.onNodeWithTag("price").performTextInput("1550")
        compose.onNodeWithTag("total").performTextInput("70")
        compose.onNodeWithTag("odometer").performTextInput("212889")
        compose.onNodeWithTag("missed_previous_hint", useUnmergedTree = true)
            .assertTextContains("much further than you usually drive", substring = true)
        compose.onNodeWithTag("full_tank_hint", useUnmergedTree = true)
            .assertTextContains("forgot to log a fill-up", substring = true)
        compose.onNodeWithTag("missed_previous").performClick()
        compose.onNodeWithTag("missed_previous_hint", useUnmergedTree = true)
            .assertTextContains("measuring starts fresh from here", substring = true)
        compose.onNodeWithTag("save").performClick()
        compose.waitUntil(5_000) { count() == 5 }
        val saved = runBlocking { app.repository.getAll().maxBy { it.dateEpochDay } }
        assertEquals(true, saved.missedPrevious)
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("After gap")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, compose.onAllNodes(hasText("Check fill")).fetchSemanticsNodes().size)
    }

    @Test
    fun aCsvSharedFromTheOldAppIsPreviewedThenImported() {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "gas-tracker-2026-10-11.csv").apply {
            writeText(
                "date,price_per_liter_usd,total_cost_usd,litres,odometer_km\n" +
                    "2026-09-20,1.770,78.61,44.41,214251\n" +
                    "2026-09-28,1.690,30.00,17.75,214841\n" +
                    "2026-10-02,1.360,59.15,43.49,214970\n",
            )
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = Csv.MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        scenario = ActivityScenario.launch(intent)

        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("import_review")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Exported by the old Gas Tracker app.").assertIsDisplayed()
        compose.onNodeWithText("Total $167.76").assertIsDisplayed()
        assertEquals("nothing is written before confirming", 0, count())
        compose.onNodeWithTag("import_confirm").performClick()
        compose.waitUntil(5_000) { count() == 3 }
        compose.onNodeWithTag("entry_count").assertTextContains("3 fill-ups")
        file.delete()
    }
}
