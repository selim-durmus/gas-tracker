package com.tuttoposto.gastracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FillUp::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao

    companion object {
        const val NAME = "gas-tracker.db"

        /** 2.1: adds the "missed a fill-up before this one" flag; existing rows default to false. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fill_ups ADD COLUMN missedPrevious INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2)

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .build()
    }
}
