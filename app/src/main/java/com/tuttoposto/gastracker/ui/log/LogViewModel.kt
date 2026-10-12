package com.tuttoposto.gastracker.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.gastracker.data.EfficiencySegment
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.data.PartialHint
import com.tuttoposto.gastracker.data.PeriodSummary
import com.tuttoposto.gastracker.data.efficiencySegments
import com.tuttoposto.gastracker.data.partialFillSuspects
import com.tuttoposto.gastracker.data.summary
import com.tuttoposto.gastracker.ui.todayFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class LogRow(
    val fillUp: FillUp,
    val efficiency: EfficiencySegment?,
    /** Set when this "full" fill looks like a partial one. */
    val partialHint: PartialHint? = null,
)

data class MonthGroup(val month: YearMonth, val summary: PeriodSummary, val rows: List<LogRow>)

data class LogState(
    val loaded: Boolean = false,
    val groups: List<MonthGroup> = emptyList(),
    val thisMonth: PeriodSummary = PeriodSummary(),
    val lastMonth: PeriodSummary = PeriodSummary(),
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = loaded && groups.isEmpty()
}

class LogViewModel(private val repository: FillUpRepository) : ViewModel() {

    val state: StateFlow<LogState> = combine(repository.observeAll(), todayFlow()) { all, today ->
        val segments = all.efficiencySegments()
        val byId = segments.associateBy { it.endFillUpId }
        val suspects = all.partialFillSuspects(segments)
        val month = YearMonth.from(today)
        LogState(
            loaded = true,
            groups = all.groupBy { YearMonth.from(it.date) }
                .toSortedMap(compareByDescending { it })
                .map { (m, entries) -> MonthGroup(m, entries.summary(), entries.map { LogRow(it, byId[it.id], suspects[it.id]) }) },
            thisMonth = all.filter { YearMonth.from(it.date) == month }.summary(),
            lastMonth = all.filter { YearMonth.from(it.date) == month.minusMonths(1) }.summary(),
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogState())

    private val _deleted = MutableSharedFlow<FillUp>(extraBufferCapacity = 4)
    /** Emits each deleted entry once, so the screen can offer Undo. */
    val deleted: SharedFlow<FillUp> = _deleted

    fun delete(fillUp: FillUp) {
        viewModelScope.launch {
            repository.delete(fillUp)
            _deleted.emit(fillUp)
        }
    }

    /** Re-inserts with the original id, so ordering and efficiency come back exactly. */
    fun restore(fillUp: FillUp) {
        viewModelScope.launch { repository.upsert(fillUp) }
    }
}
