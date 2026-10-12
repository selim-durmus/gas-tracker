package com.tuttoposto.gastracker

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import com.tuttoposto.gastracker.ui.appViewModel
import com.tuttoposto.gastracker.ui.data.DataScreen
import com.tuttoposto.gastracker.ui.data.DataViewModel
import com.tuttoposto.gastracker.ui.edit.EditScreen
import com.tuttoposto.gastracker.ui.edit.EditViewModel
import com.tuttoposto.gastracker.ui.log.LogScreen
import com.tuttoposto.gastracker.ui.log.LogViewModel
import com.tuttoposto.gastracker.ui.stats.StatsScreen
import com.tuttoposto.gastracker.ui.stats.StatsViewModel
import com.tuttoposto.gastracker.ui.theme.GasTrackerTheme

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Log("log", "Log", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Stats("stats", "Stats", Icons.Outlined.Insights, Icons.Filled.Insights),
    Data("data", "Data", Icons.Outlined.SettingsBackupRestore, Icons.Filled.SettingsBackupRestore),
}

private const val EDIT_ROUTE = "edit?id={id}"
private fun editRoute(id: Long?) = "edit?id=${id ?: 0L}"

class MainActivity : ComponentActivity() {

    /** A CSV handed to us by another app (share sheet or "open with"), waiting to be reviewed. */
    private var incomingCsv by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingCsv = csvFrom(intent)
        setContent {
            GasTrackerTheme {
                App(incomingCsv = incomingCsv, onIncomingHandled = { incomingCsv = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        csvFrom(intent)?.let { incomingCsv = it }
    }

    private fun csvFrom(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }
}

@Composable
private fun App(incomingCsv: Uri?, onIncomingHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }
    val context = LocalContext.current
    val dataViewModel = appViewModel { repo, _ -> DataViewModel(repo, context.applicationContext.contentResolver) }

    LaunchedEffect(incomingCsv) {
        val uri = incomingCsv ?: return@LaunchedEffect
        dataViewModel.startImport(uri)
        onIncomingHandled()
        // On a cold start the NavHost hasn't set its graph yet; wait for it before navigating.
        nav.currentBackStackEntryFlow.first()
        nav.switchTab(Tab.Data)
    }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { tab ->
                        val selected = route == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { if (!selected) nav.switchTab(tab) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("tab_${tab.route}"),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Log.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Tab.Log.route) {
                LogScreen(
                    viewModel = appViewModel { repo, _ -> LogViewModel(repo) },
                    onAdd = dropUnlessResumed { nav.navigate(editRoute(null)) },
                    onOpen = { id -> if (nav.currentBackStackEntry?.destination?.route == Tab.Log.route) nav.navigate(editRoute(id)) },
                    onImport = { nav.switchTab(Tab.Data) },
                )
            }
            composable(Tab.Stats.route) {
                StatsScreen(viewModel = appViewModel { repo, _ -> StatsViewModel(repo) })
            }
            composable(Tab.Data.route) {
                DataScreen(viewModel = dataViewModel)
            }
            composable(
                route = EDIT_ROUTE,
                arguments = listOf(navArgument(EditViewModel.ARG_ID) { type = NavType.LongType; defaultValue = 0L }),
                enterTransition = { slideInVertically(tween(260)) { it / 6 } + fadeIn(tween(200)) },
                popExitTransition = { slideOutVertically(tween(220)) { it / 6 } + fadeOut(tween(160)) },
            ) {
                EditScreen(
                    viewModel = appViewModel { repo, handle -> EditViewModel(repo, handle) },
                    // Only pop while this screen is resumed, so a save and a back press can't pop twice.
                    onClose = dropUnlessResumed { nav.popBackStack() },
                )
            }
        }
    }
}

private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
