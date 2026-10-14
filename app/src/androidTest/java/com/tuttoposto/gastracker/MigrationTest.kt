package com.tuttoposto.gastracker

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tuttoposto.gastracker.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun version1RowsSurviveTheMissedFillMigration() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO fill_ups (id, dateEpochDay, pricePerLitreMills, totalCostCents, odometerKm, fullTank, note) " +
                    "VALUES (13, 20724, 1690, 3000, 214841, 0, ''), (14, 20728, 1360, 5915, 214970, 1, 'Costco')",
            )
        }
        helper.runMigrationsAndValidate(dbName, 2, true, *AppDatabase.MIGRATIONS).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
        try {
            val rows = runBlocking { db.fillUpDao().getAll() }.sortedBy { it.id }
            assertEquals(listOf(13L, 14L), rows.map { it.id })
            assertEquals(listOf(false, true), rows.map { it.fullTank })
            assertEquals("Costco", rows[1].note)
            assertEquals(5915L, rows[1].totalCostCents)
            rows.forEach { assertFalse(it.missedPrevious) }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
