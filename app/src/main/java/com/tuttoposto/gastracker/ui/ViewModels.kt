package com.tuttoposto.gastracker.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tuttoposto.gastracker.GasTrackerApp
import com.tuttoposto.gastracker.data.FillUpRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/** Builds a ViewModel with the app's repository and the nav entry's SavedStateHandle. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (FillUpRepository, SavedStateHandle) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as GasTrackerApp
            create(app.repository, createSavedStateHandle())
        }
    },
)

/** Today's date, re-emitted when the day rolls over so "this month" never goes stale. */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(60_000)
    }
}.distinctUntilChanged()
