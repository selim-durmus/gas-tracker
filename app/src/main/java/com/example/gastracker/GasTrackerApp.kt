package com.example.gastracker

import android.app.Application
import com.example.gastracker.data.AppDatabase
import com.example.gastracker.data.FillUpRepository

class GasTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: FillUpRepository by lazy { FillUpRepository(database.fillUpDao()) }
}
