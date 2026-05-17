package com.example.gastracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.gastracker.data.FillUp
import com.example.gastracker.data.FillUpRepository
import com.example.gastracker.data.FillUpWithEfficiency
import com.example.gastracker.data.HistorySection
import com.example.gastracker.data.LifetimeSummary
import com.example.gastracker.data.Money
import com.example.gastracker.data.toHistorySections
import com.example.gastracker.data.toLifetimeSummary
import com.example.gastracker.data.withEfficiency
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EditState(
    val id: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val priceInput: String = "",
    val totalInput: String = "",
    val odometerInput: String = "",
    val priceError: Boolean = false,
    val totalError: Boolean = false,
    val odometerError: Boolean = false,
    val saved: Boolean = false,
    val initial: InitialSnapshot = InitialSnapshot.empty(),
) {
    val canSave: Boolean
        get() = Money.fromInput(priceInput) != null &&
            Money.fromInput(totalInput) != null &&
            (odometerInput.isBlank() || odometerInput.toLongOrNull()?.let { it >= 0 } == true)

    val isDirty: Boolean
        get() = initial.date != date ||
            initial.priceInput != priceInput ||
            initial.totalInput != totalInput ||
            initial.odometerInput != odometerInput
}

data class InitialSnapshot(
    val date: LocalDate,
    val priceInput: String,
    val totalInput: String,
    val odometerInput: String,
) {
    companion object {
        fun empty() = InitialSnapshot(LocalDate.now(), "", "", "")
    }
}

class FillUpsViewModel(private val repository: FillUpRepository) : ViewModel() {

    val fillUps: StateFlow<List<FillUp>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fillUpsWithEfficiency: StateFlow<List<FillUpWithEfficiency>> = repository.observeAll()
        .map { it.withEfficiency() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<HistorySection>> = repository.observeAll()
        .map { it.toHistorySections() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lifetime: StateFlow<LifetimeSummary> = repository.observeAll()
        .map { it.toLifetimeSummary() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            LifetimeSummary(0, 0.0, 0, null, null),
        )

    private val _editState = MutableStateFlow(EditState())
    val editState: StateFlow<EditState> = _editState.asStateFlow()

    fun startNew() {
        val lastEntry = fillUps.value.firstOrNull()
        val today = LocalDate.now()
        val pricePrefill = lastEntry?.let { "%.3f".format(it.pricePerLiterCents / 100.0) } ?: ""
        val snapshot = InitialSnapshot(today, pricePrefill, "", "")
        _editState.value = EditState(
            date = today,
            priceInput = pricePrefill,
            initial = snapshot,
        )
    }

    fun startEdit(id: Long) {
        viewModelScope.launch {
            val existing = repository.getById(id) ?: return@launch
            val priceStr = "%.3f".format(existing.pricePerLiterCents / 100.0)
            val totalStr = "%.2f".format(existing.totalCostCents / 100.0)
            val odoStr = existing.odometerKm?.toString() ?: ""
            _editState.value = EditState(
                id = existing.id,
                date = existing.date,
                priceInput = priceStr,
                totalInput = totalStr,
                odometerInput = odoStr,
                initial = InitialSnapshot(existing.date, priceStr, totalStr, odoStr),
            )
        }
    }

    fun onPriceChange(value: String) {
        _editState.value = _editState.value.copy(priceInput = value, priceError = false)
    }

    fun onTotalChange(value: String) {
        _editState.value = _editState.value.copy(totalInput = value, totalError = false)
    }

    fun onOdometerChange(value: String) {
        _editState.value = _editState.value.copy(odometerInput = value, odometerError = false)
    }

    fun onDateChange(date: LocalDate) {
        _editState.value = _editState.value.copy(date = date)
    }

    fun save() {
        val state = _editState.value
        val price = Money.fromInput(state.priceInput)
        val total = Money.fromInput(state.totalInput)
        val odo = if (state.odometerInput.isBlank()) null else state.odometerInput.toLongOrNull()
        val odoBad = state.odometerInput.isNotBlank() && (odo == null || odo < 0)
        if (price == null || total == null || odoBad) {
            _editState.value = state.copy(
                priceError = price == null,
                totalError = total == null,
                odometerError = odoBad,
            )
            return
        }
        val entity = FillUp(
            id = state.id ?: 0,
            dateEpochDay = state.date.toEpochDay(),
            pricePerLiterCents = price.cents,
            totalCostCents = total.cents,
            odometerKm = odo,
        )
        viewModelScope.launch {
            repository.upsert(entity)
            _editState.value = state.copy(saved = true)
        }
    }

    private val _lastDeleted = MutableStateFlow<FillUp?>(null)
    val lastDeleted: StateFlow<FillUp?> = _lastDeleted.asStateFlow()

    fun requestDelete(fillUp: FillUp) {
        viewModelScope.launch {
            repository.delete(fillUp)
            _lastDeleted.value = fillUp
        }
    }

    fun undoLastDelete() {
        val deleted = _lastDeleted.value ?: return
        _lastDeleted.value = null
        viewModelScope.launch { repository.upsert(deleted) }
    }

    fun consumeLastDeleted() {
        _lastDeleted.value = null
    }

    companion object {
        fun factory(repository: FillUpRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    FillUpsViewModel(repository) as T
            }
    }
}
