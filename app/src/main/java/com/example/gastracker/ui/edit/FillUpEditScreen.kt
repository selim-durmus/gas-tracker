package com.example.gastracker.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.gastracker.data.Money
import com.example.gastracker.ui.EditState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillUpEditScreen(
    state: EditState,
    isNew: Boolean,
    onBack: () -> Unit,
    onPriceChange: (String) -> Unit,
    onTotalChange: (String) -> Unit,
    onOdometerChange: (String) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onSave: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    fun attemptExit() {
        if (state.isDirty) showDiscardConfirm = true else onBack()
    }

    BackHandler { attemptExit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New fill-up" else "Edit fill-up") },
                navigationIcon = {
                    IconButton(onClick = { attemptExit() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = state.canSave) {
                        Text("Save")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DecimalTextField(
                value = state.priceInput,
                onValueChange = onPriceChange,
                label = "Price per litre",
                prefix = "$",
                suffix = "/L",
                maxDecimals = 3,
                isError = state.priceError,
                errorText = "Enter a valid amount",
                parsedPreview = Money.fromInput(state.priceInput)
                    ?.let { "= \$${"%.3f".format(it.cents / 100.0)}/L" },
            )

            DecimalTextField(
                value = state.totalInput,
                onValueChange = onTotalChange,
                label = "Total cost",
                prefix = "$",
                suffix = null,
                maxDecimals = 2,
                isError = state.totalError,
                errorText = "Enter a valid amount",
                parsedPreview = Money.fromInput(state.totalInput)?.format()?.let { "= $it" },
            )

            IntegerTextField(
                value = state.odometerInput,
                onValueChange = onOdometerChange,
                label = "Odometer (km) — optional",
                isError = state.odometerError,
                errorText = "Enter whole kilometres",
            )

            Box(modifier = Modifier
                .fillMaxWidth()
                .clickable { showDatePicker = true }
            ) {
                OutlinedTextField(
                    value = state.date.format(dateFormatter),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Odometer is optional but unlocks fuel-efficiency stats. Edit any time from the list.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showDatePicker) {
        val initialMillis = state.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                        onDateChange(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits won't be saved.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) { Text("Keep editing") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DecimalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String?,
    suffix: String?,
    maxDecimals: Int,
    isError: Boolean,
    errorText: String,
    parsedPreview: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(sanitizeDecimal(raw, maxDecimals)) },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = isError,
        supportingText = {
            when {
                isError -> Text(errorText)
                !parsedPreview.isNullOrBlank() -> Text(
                    parsedPreview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(" ")
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    errorText: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }.take(8)) },
        label = { Text(label) },
        isError = isError,
        supportingText = if (isError) {
            { Text(errorText) }
        } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun sanitizeDecimal(raw: String, maxDecimals: Int): String {
    val filtered = raw.filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) return filtered.take(8)
    val intPart = filtered.substring(0, firstDot).take(8)
    val rest = filtered.substring(firstDot + 1).replace(".", "")
    return intPart + "." + rest.take(maxDecimals)
}
