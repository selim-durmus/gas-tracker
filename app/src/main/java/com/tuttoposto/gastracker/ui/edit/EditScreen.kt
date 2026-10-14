package com.tuttoposto.gastracker.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuttoposto.gastracker.data.PartialHint
import com.tuttoposto.gastracker.data.formatKm
import com.tuttoposto.gastracker.data.formatLitres
import com.tuttoposto.gastracker.data.formatLong
import com.tuttoposto.gastracker.data.formatMoney
import com.tuttoposto.gastracker.data.sanitizeDecimalInput
import com.tuttoposto.gastracker.data.shortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(viewModel: EditViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    LaunchedEffect(state.finished, state.missing) {
        if (state.finished || state.missing) onClose()
    }

    fun attemptClose() {
        if (state.isDirty) confirmDiscard = true else onClose()
    }
    BackHandler(enabled = state.isDirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New fill-up" else "Edit fill-up") },
                navigationIcon = {
                    IconButton(onClick = { attemptClose() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }, enabled = !state.saving) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete fill-up")
                        }
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp).testTag("save"),
                    ) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DecimalField(
                value = state.draft.price,
                onValueChange = { v -> viewModel.update { it.copy(price = v) } },
                label = "Price per litre",
                prefix = "$",
                suffix = "/L",
                maxDecimals = 3,
                autoDecimalAfter = 1,
                error = if (state.showErrors && state.priceMills == null) "Enter the pump price, e.g. 1.799" else null,
                supporting = "From the pump display",
                testTag = "price",
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )
            DecimalField(
                value = state.draft.total,
                onValueChange = { v -> viewModel.update { it.copy(total = v) } },
                label = "Total cost",
                prefix = "$",
                suffix = null,
                maxDecimals = 2,
                autoDecimalAfter = null,
                error = if (state.showErrors && state.totalCents == null) "Enter the amount paid" else null,
                supporting = state.litres?.let { "≈ ${formatLitres(it)} pumped" } ?: "From the receipt",
                testTag = "total",
                onNext = { focus.moveFocus(FocusDirection.Down) },
            )

            OdometerField(state = state, onValueChange = { v -> viewModel.update { it.copy(odometer = v) } })

            ToggleRow(
                title = "Filled to the top",
                body = when {
                    state.partialHint == PartialHint.LowReading ->
                        "That's much less fuel than usual for this distance. If you didn't fill to the top, " +
                            "turn this off. If you forgot to log a fill-up before this one, add it, or use " +
                            "\"Missed a fill-up\" below."
                    state.partialHint == PartialHint.NextReadingHigh ->
                        "The next fill used far more fuel than usual, so this one probably wasn't full. If so, turn this off."
                    state.draft.fullTank -> "Economy is measured from one full tank to the next."
                    else -> "Partial fill: its fuel is added to the next full fill's economy reading."
                },
                checked = state.draft.fullTank,
                onChange = { v -> viewModel.update { it.copy(fullTank = v) } },
                warning = state.partialHint != null,
                tag = "full_tank",
            )

            val gap = state.longGapKm
            ToggleRow(
                title = "Missed a fill-up before this one",
                body = when {
                    state.draft.missedPrevious ->
                        "This fill won't get an economy reading; measuring starts fresh from here. " +
                            "Spending totals aren't affected."
                    gap != null ->
                        "${formatKm(gap)} is much further than you usually drive between fill-ups. Forgot to " +
                            "log one? Add it if you know the amount, otherwise turn this on so it doesn't " +
                            "skew your economy."
                    else -> "Forgot to log a fill-up? Add it if you know the amount. If not, turn this on " +
                        "to skip the one economy reading it would throw off."
                },
                checked = state.draft.missedPrevious,
                onChange = { v -> viewModel.update { it.copy(missedPrevious = v) } },
                warning = gap != null,
                tag = "missed_previous",
            )

            DateRow(date = state.draft.date, onClick = { pickDate = true })

            OutlinedTextField(
                value = state.draft.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v.take(120)) } },
                label = { Text("Note (optional)") },
                placeholder = { Text("Station, trip, …") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().testTag("note"),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickDate) {
        val pickerState = rememberDatePickerState(
            // The Material date picker works in UTC midnights; v1 mixed in the local zone and
            // pre-selected the wrong day east of UTC.
            initialSelectedDateMillis = state.draft.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis <= LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        val picked = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.update { it.copy(date = picked) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("What you've entered won't be saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
            title = { Text("Delete this fill-up?") },
            text = { Text("${formatMoney(state.totalCents ?: 0)} on ${state.draft.date.formatLong()}. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    prefix: String?,
    suffix: String?,
    maxDecimals: Int,
    autoDecimalAfter: Int?,
    error: String?,
    supporting: String,
    testTag: String,
    onNext: () -> Unit,
) {
    // Local TextFieldValue so the cursor can be placed after auto-inserted characters.
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            val clean = sanitizeDecimalInput(next.text, maxDecimals = maxDecimals, autoDecimalAfter = autoDecimalAfter)
            val selection = if (clean == next.text) next.selection else TextRange(clean.length)
            field = TextFieldValue(clean, selection)
            if (clean != value) onValueChange(clean)
        },
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        isError = error != null,
        supportingText = { Text(error ?: supporting) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

@Composable
private fun OdometerField(state: EditState, onValueChange: (String) -> Unit) {
    val prev = state.previousWithOdometer
    val distance = state.distanceSincePrevious
    val warn = state.odometerKm != null && state.odometerNotIncreasing
    val supporting = when {
        state.odometerInvalid -> "Whole kilometres only"
        warn && prev != null -> "Not above the previous reading (${formatKm(prev.odometerKm!!)}). Economy restarts from here."
        distance != null -> "+${formatKm(distance)} since ${prev!!.date.format(shortDate)}"
        prev != null -> "Last reading ${formatKm(prev.odometerKm!!)} on ${prev.date.format(shortDate)}"
        else -> "Optional. Unlocks fuel-economy stats."
    }
    OutlinedTextField(
        value = state.draft.odometer,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(7)) },
        label = { Text("Odometer") },
        suffix = { Text("km") },
        isError = state.odometerInvalid,
        supportingText = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (warn) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                }
                Text(supporting, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().testTag("odometer"),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    warning: Boolean,
    tag: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("${tag}_hint"),
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Change date", onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Date", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(date.formatLong(), style = MaterialTheme.typography.bodyLarge)
            }
            if (date == LocalDate.now()) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
