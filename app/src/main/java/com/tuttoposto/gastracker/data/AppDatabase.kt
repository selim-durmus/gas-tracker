package com.tuttoposto.gastracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FillUp::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fillUpDao(): FillUpDao

    companion object {
        const val NAME = "gas-tracker.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, AppDatabase::class.java)
                .build()
    }
}
