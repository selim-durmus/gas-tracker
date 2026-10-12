package com.tuttoposto.gastracker

import android.app.Application
import com.tuttoposto.gastracker.data.AppDatabase
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.widget.Widgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.io.File

class GasTrackerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: FillUpRepository by lazy {
        FillUpRepository(database.fillUpDao(), File(filesDir, "backups"))
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Widgets read the database themselves; just poke them whenever the log changes.
        appScope.launch {
            repository.observeAll()
                .distinctUntilChanged()
                .drop(1)
                .collect { Widgets.refreshAll(this@GasTrackerApp) }
        }
    }
}
