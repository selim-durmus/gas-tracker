package com.tuttoposto.gastracker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttoposto.gastracker.data.AppDatabase
import com.tuttoposto.gastracker.data.Csv
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.data.ImportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var backups: File
    private lateinit var repo: FillUpRepository

    private val legacy = """
        date,price_per_liter_usd,total_cost_usd,litres,odometer_km
        2026-05-18,1.800,85.00,47.22,209033
        2026-06-11,1.430,35.23,24.64,
        2026-10-02,1.360,59.15,43.49,214970
    """.trimIndent()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        backups = File(context.cacheDir, "test-backups-${System.nanoTime()}")
        repo = FillUpRepository(db.fillUpDao(), backups)
    }

    @After
    fun tearDown() {
        db.close()
        backups.deleteRecursively()
    }

    private fun fill(day: String, mills: Long, cents: Long, odo: Long? = null) =
        FillUp(dateEpochDay = LocalDate.parse(day).toEpochDay(), pricePerLitreMills = mills, totalCostCents = cents, odometerKm = odo)

    @Test
    fun upsertInsertsThenUpdatesInPlace() = runTest {
        val id = repo.upsert(fill("2026-10-01", 1799, 4550))
        repo.upsert(repo.getById(id)!!.copy(totalCostCents = 5000, note = "edited"))
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals(5000L, all.single().totalCostCents)
        assertEquals("edited", all.single().note)
    }

    @Test
    fun observeAllIsNewestFirst() = runTest {
        repo.upsert(fill("2026-01-01", 1000, 100))
        repo.upsert(fill("2026-03-01", 1000, 300))
        repo.upsert(fill("2026-02-01", 1000, 200))
        assertEquals(listOf(300L, 200L, 100L), repo.observeAll().first().map { it.totalCostCents })
    }

    @Test
    fun importingTheLegacyExportIntoAnEmptyLogKeepsEverything() = runTest {
        val plan = repo.planImport(Csv.parse(legacy))
        assertEquals(3, plan.newRows.size)
        val result = repo.applyImport(plan, ImportMode.Merge)
        assertEquals(3, result.imported)
        assertNull("no snapshot needed for an empty log", result.safetyBackup)
        val all = repo.getAll().sortedBy { it.dateEpochDay }
        assertEquals(listOf(1800L, 1430L, 1360L), all.map { it.pricePerLitreMills })
        assertEquals(listOf(8500L, 3523L, 5915L), all.map { it.totalCostCents })
        assertEquals(listOf(209033L, null, 214970L), all.map { it.odometerKm })
    }

    @Test
    fun mergeSkipsRowsAlreadyInTheLogAndSnapshotsFirst() = runTest {
        repo.upsert(fill("2026-05-18", 1800, 8500, 209033))
        val plan = repo.planImport(Csv.parse(legacy))
        assertEquals(1, plan.duplicateCount)
        assertEquals(2, plan.newRows.size)
        val result = repo.applyImport(plan, ImportMode.Merge)
        assertEquals(3, repo.getAll().size)
        assertNotNull(result.safetyBackup)
        assertEquals(1, Csv.parse(result.safetyBackup!!.readText()).rows.size)
    }

    @Test
    fun mergingTheSameFileTwiceAddsNothingTheSecondTime() = runTest {
        repo.applyImport(repo.planImport(Csv.parse(legacy)), ImportMode.Merge)
        val again = repo.planImport(Csv.parse(legacy))
        assertTrue(again.newRows.isEmpty())
        assertEquals(3, again.duplicateCount)
    }

    @Test
    fun replaceSwapsTheWholeLog() = runTest {
        repo.upsert(fill("2020-01-01", 1000, 100))
        repo.upsert(fill("2020-01-02", 1000, 200))
        val result = repo.applyImport(repo.planImport(Csv.parse(legacy)), ImportMode.Replace)
        assertEquals(3, result.imported)
        assertEquals(3, repo.getAll().size)
        assertTrue(repo.getAll().none { it.dateEpochDay < LocalDate.parse("2026-01-01").toEpochDay() })
        assertEquals(2, Csv.parse(result.safetyBackup!!.readText()).rows.size)
    }

    @Test
    fun exportThenImportIsLossless() = runTest {
        repo.upsert(fill("2026-09-28", 1699, 3000, 214841).copy(fullTank = false, note = "top-up, quick"))
        repo.upsert(fill("2026-10-02", 1359, 5915, 214970))
        val csv = Csv.format(repo.getAll())
        val other = FillUpRepository(db.fillUpDao(), backups)
        other.applyImport(other.planImport(Csv.parse(csv)), ImportMode.Replace)
        assertEquals(
            listOf(1699L to false, 1359L to true),
            other.getAll().sortedBy { it.dateEpochDay }.map { it.pricePerLitreMills to it.fullTank },
        )
        assertEquals("top-up, quick", other.getAll().first { !it.fullTank }.note)
    }
}
