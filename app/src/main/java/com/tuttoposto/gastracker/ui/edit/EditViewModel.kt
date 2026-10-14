package com.tuttoposto.gastracker.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuttoposto.gastracker.data.FillUp
import com.tuttoposto.gastracker.data.FillUpRepository
import com.tuttoposto.gastracker.data.PartialHint
import com.tuttoposto.gastracker.data.chronological
import com.tuttoposto.gastracker.data.efficiencySegments
import com.tuttoposto.gastracker.data.partialFillSuspects
import com.tuttoposto.gastracker.data.parseCents
import com.tuttoposto.gastracker.data.parseMills
import com.tuttoposto.gastracker.data.plainDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class Draft(
    val date: LocalDate = LocalDate.now(),
    val price: String = "",
    val total: String = "",
    val odometer: String = "",
    val fullTank: Boolean = true,
    val note: String = "",
    val missedPrevious: Boolean = false,
)

data class EditState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val draft: Draft = Draft(),
    val initial: Draft = Draft(),
    /** The nearest earlier fill-up with an odometer reading, for context and sanity checks. */
    val previousWithOdometer: FillUp? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    /** Set once the entry is saved or deleted; the screen closes on it. */
    val finished: Boolean = false,
    val missing: Boolean = false,
    /** Set while the draft is marked full but its economy says the tank probably wasn't. */
    val partialHint: PartialHint? = null,
    /**
     * Set to the distance since the last reading when it's far beyond the usual distance per
     * fill-up, which usually means a fill-up was never logged.
     */
    val longGapKm: Long? = null,
) {
    val priceMills: Long? get() = parseMills(draft.price)?.takeIf { it > 0 }
    val totalCents: Long? get() = parseCents(draft.total)?.takeIf { it > 0 }
    val odometerKm: Long? get() = draft.odometer.toLongOrNull()
    val odometerInvalid: Boolean get() = draft.odometer.isNotBlank() && odometerKm == null

    val litres: Double?
        get() {
            val p = priceMills ?: return null
            val t = totalCents ?: return null
            return t * 10.0 / p
        }

    val distanceSincePrevious: Long?
        get() {
            val odo = odometerKm ?: return null
            val prev = previousWithOdometer?.odometerKm ?: return null
            return odo - prev
        }

    /** A reading at or below the previous one: likely a typo, and it restarts the economy baseline. */
    val odometerNotIncreasing: Boolean get() = (distanceSincePrevious ?: 1) <= 0

    val canSave: Boolean get() = loaded && !saving && priceMills != null && totalCents != null && !odometerInvalid
    val isDirty: Boolean get() = draft != initial
}

class EditViewModel(
    private val repository: FillUpRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val id: Long = savedStateHandle.get<Long>(ARG_ID)?.takeIf { it > 0 } ?: 0L
    private var existing: FillUp? = null
    private var all: List<FillUp> = emptyList()
    private var typicalDistanceKm: Long? = null

    private val _state = MutableStateFlow(EditState(isNew = id == 0L))
    val state: StateFlow<EditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            all = repository.getAll()
            typicalDistanceKm = medianTankDistance()
            val entry = if (id != 0L) repository.getById(id) else null
            if (id != 0L && entry == null) {
                _state.update { it.copy(loaded = true, missing = true) }
                return@launch
            }
            existing = entry
            val draft = entry?.toDraft() ?: Draft(date = LocalDate.now())
            _state.update {
                it.copy(
                    loaded = true,
                    draft = draft,
                    initial = draft,
                    previousWithOdometer = previousFor(draft.date),
                    partialHint = hintFor(draft),
                ).withGapCheck()
            }
        }
    }

    fun update(transform: (Draft) -> Draft) {
        _state.update { s ->
            val next = transform(s.draft)
            s.copy(
                draft = next,
                previousWithOdometer = if (next.date != s.draft.date) previousFor(next.date) else s.previousWithOdometer,
                partialHint = hintFor(next),
            ).withGapCheck()
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || s.finished) return
        val price = s.priceMills
        val total = s.totalCents
        if (price == null || total == null || s.odometerInvalid) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        _state.update { it.copy(saving = true) }
        val entity = FillUp(
            id = existing?.id ?: 0L,
            dateEpochDay = s.draft.date.toEpochDay(),
            pricePerLitreMills = price,
            totalCostCents = total,
            odometerKm = s.odometerKm,
            fullTank = s.draft.fullTank,
            note = s.draft.note.trim(),
            missedPrevious = s.draft.missedPrevious,
        )
        viewModelScope.launch {
            repository.upsert(entity)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    fun delete() {
        val entry = existing ?: return
        if (_state.value.saving || _state.value.finished) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.delete(entry)
            _state.update { it.copy(saving = false, finished = true) }
        }
    }

    /** Runs the log's partial-fill check with this draft in place of the stored entry. */
    private fun hintFor(draft: Draft): PartialHint? {
        if (!draft.fullTank || draft.missedPrevious) return null
        val price = parseMills(draft.price)?.takeIf { it > 0 } ?: return null
        val total = parseCents(draft.total)?.takeIf { it > 0 } ?: return null
        val me = FillUp(
            id = if (id != 0L) id else Long.MAX_VALUE,
            dateEpochDay = draft.date.toEpochDay(),
            pricePerLitreMills = price,
            totalCostCents = total,
            odometerKm = draft.odometer.toLongOrNull(),
        )
        return (all.filter { it.id != id } + me).partialFillSuspects()[me.id]
    }

    /** Median distance of the log's single-tank economy readings; null until there are three. */
    private fun medianTankDistance(): Long? {
        val d = all.filter { it.id != id }.efficiencySegments()
            .filter { it.fillsCovered == 1 }
            .map { it.distanceKm }
            .sorted()
        return if (d.size < 3) null else d[d.size / 2]
    }

    private fun EditState.withGapCheck(): EditState {
        val distance = distanceSincePrevious
        val typical = typicalDistanceKm
        val prev = previousWithOdometer
        if (draft.missedPrevious || distance == null || typical == null || prev == null) return copy(longGapKm = null)
        // Fills logged without a reading in between legitimately stretch the distance.
        val day = draft.date.toEpochDay()
        val between = all.count { it.id != id && it.id != prev.id && it.dateEpochDay in prev.dateEpochDay..day }
        val limit = typical * GAP_RATIO * (1 + between)
        return copy(longGapKm = distance.takeIf { it > limit })
    }

    /** Nearest entry before [date] (same-day entries count if older) that has an odometer reading. */
    private fun previousFor(date: LocalDate): FillUp? {
        val day = date.toEpochDay()
        return all.chronological()
            .filter { it.id != id && it.odometerKm != null }
            .filter { it.dateEpochDay < day || (it.dateEpochDay == day && (id == 0L || it.id < id)) }
            .lastOrNull()
    }

    companion object {
        const val ARG_ID = "id"
        private const val GAP_RATIO = 1.8
    }
}

private fun FillUp.toDraft() = Draft(
    date = date,
    price = plainDecimal(pricePerLitreMills, 3),
    total = plainDecimal(totalCostCents, 2),
    odometer = odometerKm?.toString().orEmpty(),
    fullTank = fullTank,
    note = note,
    missedPrevious = missedPrevious,
)
