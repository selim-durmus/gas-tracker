package com.example.gastracker

import android.app.Application
import com.example.gastracker.data.AppDatabase
import com.example.gastracker.data.FillUpRepository
import androidx.glance.appwidget.updateAll
import com.example.gastracker.widget.GasTrackerCompactWidget
import com.example.gastracker.widget.GasTrackerEfficiencyWidget
import com.example.gastracker.widget.GasTrackerWideWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class GasTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: FillUpRepository by lazy { FillUpRepository(database.fillUpDao()) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.observeAll()
                .distinctUntilChanged()
                .collect {
                    GasTrackerCompactWidget().updateAll(this@GasTrackerApp)
                    GasTrackerWideWidget().updateAll(this@GasTrackerApp)
                    GasTrackerEfficiencyWidget().updateAll(this@GasTrackerApp)
                }
        }
    }
}
