package com.example.gastracker.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gastracker.data.FillUp
import com.example.gastracker.data.FillUpWithEfficiency
import com.example.gastracker.data.formatPrice
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private const val UNDO_TIMEOUT_MS = 2_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FillUpListScreen(
    fillUps: List<FillUpWithEfficiency>,
    lastDeleted: StateFlow<FillUp?>,
    onAddClick: () -> Unit,
    onRowClick: (FillUp) -> Unit,
    onDelete: (FillUp) -> Unit,
    onUndoDelete: () -> Unit,
    onUndoExpired: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleted by lastDeleted.collectAsState()

    LaunchedEffect(deleted) {
        if (deleted == null) return@LaunchedEffect
        val result = withTimeoutOrNull(UNDO_TIMEOUT_MS) {
            snackbarHostState.showSnackbar(
                message = "Entry deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Indefinite,
            )
        }
        if (result == SnackbarResult.ActionPerformed) {
            onUndoDelete()
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
            onUndoExpired()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gas Tracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "Add fill-up")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (fillUps.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No fill-ups yet.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(fillUps, key = { it.fillUp.id }) { item ->
                    SwipeableRow(
                        fillUp = item.fillUp,
                        efficiency = item.lPer100km,
                        onClick = { onRowClick(item.fillUp) },
                        onDismiss = { onDelete(item.fillUp) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(
    fillUp: FillUp,
    efficiency: Double?,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onDismiss()
        },
        backgroundContent = {
            val swiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val progress = if (swiping) dismissState.progress.coerceIn(0f, 1f) else 0f
            val bg = lerp(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.error,
                progress,
            )
            val iconColor = lerp(
                MaterialTheme.colorScheme.onSurfaceVariant,
                MaterialTheme.colorScheme.onError,
                progress,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = null,
                    tint = iconColor,
                )
            }
        },
    ) {
        FillUpRow(fillUp = fillUp, efficiency = efficiency, onClick = onClick)
    }
}

@Composable
private fun FillUpRow(fillUp: FillUp, efficiency: Double?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fillUp.date.format(dateFormatter),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val subtitle = buildString {
                append(fillUp.pricePerLiter.formatPrice())
                append(" · ")
                append("%.2f".format(fillUp.litres))
                append(" L")
                if (efficiency != null) {
                    append(" · ")
                    append("%.1f".format(efficiency))
                    append(" L/100km")
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = fillUp.totalCost.format(),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
