package com.tuttoposto.gastracker.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.gastracker.data.Dashboard
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.data.dashboard
import com.tuttoposto.gastracker.ui.todayFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class StatsState(
    val loaded: Boolean = false,
    val entries: List<FillUp> = emptyList(),
    val dashboard: Dashboard? = null,
)

class StatsViewModel(repository: FillUpRepository) : ViewModel() {
    val state: StateFlow<StatsState> = combine(repository.observeAll(), todayFlow()) { all, today ->
        StatsState(loaded = true, entries = all, dashboard = if (all.isEmpty()) null else all.dashboard(today))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())
}
