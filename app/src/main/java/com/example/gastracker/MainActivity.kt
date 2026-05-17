package com.example.gastracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gastracker.data.FillUpRepository
import com.example.gastracker.ui.FillUpsViewModel
import com.example.gastracker.ui.edit.FillUpEditScreen
import com.example.gastracker.ui.list.FillUpListScreen
import com.example.gastracker.ui.stats.StatsScreen
import com.example.gastracker.ui.theme.GasTrackerTheme

private sealed class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit,
) {
    object Log : TopLevelDestination(
        route = "log",
        label = "Log",
        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
    )
    object Stats : TopLevelDestination(
        route = "stats",
        label = "Stats",
        icon = { Icon(Icons.Filled.BarChart, contentDescription = null) },
    )
}

private val topLevelDestinations = listOf(TopLevelDestination.Log, TopLevelDestination.Stats)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as GasTrackerApp).repository
        setContent {
            GasTrackerTheme {
                GasTrackerNav(repository)
            }
        }
    }
}

@Composable
private fun GasTrackerNav(repository: FillUpRepository) {
    val navController = rememberNavController()
    val viewModel: FillUpsViewModel = viewModel(factory = FillUpsViewModel.factory(repository))

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == TopLevelDestination.Log.route ||
        currentRoute == TopLevelDestination.Stats.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(navController = navController, currentRoute = currentRoute)
            }
        },
    ) { scaffoldPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.Log.route,
            modifier = Modifier
                .padding(scaffoldPadding)
                .consumeWindowInsets(scaffoldPadding),
        ) {
            composable(
                route = TopLevelDestination.Log.route,
                enterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() },
                popEnterTransition = { fadeThroughEnter() },
                popExitTransition = { fadeThroughExit() },
            ) {
                val fillUps by viewModel.fillUpsWithEfficiency.collectAsState()
                FillUpListScreen(
                    fillUps = fillUps,
                    onAddClick = {
                        viewModel.startNew()
                        navController.navigate("edit?id=-1")
                    },
                    onRowClick = { entry ->
                        viewModel.startEdit(entry.id)
                        navController.navigate("edit?id=${entry.id}")
                    },
                    onDelete = viewModel::requestDelete,
                    lastDeleted = viewModel.lastDeleted,
                    onUndoDelete = viewModel::undoLastDelete,
                    onUndoExpired = viewModel::consumeLastDeleted,
                )
            }
            composable(
                route = TopLevelDestination.Stats.route,
                enterTransition = { fadeThroughEnter() },
                exitTransition = { fadeThroughExit() },
                popEnterTransition = { fadeThroughEnter() },
                popExitTransition = { fadeThroughExit() },
            ) {
                val fillUps by viewModel.fillUps.collectAsState()
                val history by viewModel.history.collectAsState()
                val lifetime by viewModel.lifetime.collectAsState()
                StatsScreen(
                    fillUps = fillUps,
                    history = history,
                    lifetime = lifetime,
                )
            }
            composable(
                route = "edit?id={id}",
                arguments = listOf(navArgument("id") {
                    type = NavType.LongType
                    defaultValue = -1L
                }),
                enterTransition = {
                    slideInVertically(
                        initialOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 300),
                    ) + fadeIn(animationSpec = tween(durationMillis = 200))
                },
                exitTransition = { fadeOut(animationSpec = tween(durationMillis = 150)) },
                popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 150)) },
                popExitTransition = {
                    slideOutVertically(
                        targetOffsetY = { fullHeight -> fullHeight },
                        animationSpec = tween(durationMillis = 300),
                    ) + fadeOut(animationSpec = tween(durationMillis = 200))
                },
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                val isNew = id <= 0L
                val state by viewModel.editState.collectAsState()

                LaunchedEffect(state.saved) {
                    if (state.saved) navController.popBackStack()
                }

                FillUpEditScreen(
                    state = state,
                    isNew = isNew,
                    onBack = { navController.popBackStack() },
                    onPriceChange = viewModel::onPriceChange,
                    onTotalChange = viewModel::onTotalChange,
                    onOdometerChange = viewModel::onOdometerChange,
                    onDateChange = viewModel::onDateChange,
                    onSave = viewModel::save,
                )
            }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        topLevelDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = dest.icon,
                label = { Text(dest.label) },
            )
        }
    }
}

private fun AnimatedContentTransitionScope<*>.fadeThroughEnter() =
    fadeIn(animationSpec = tween(durationMillis = 220, delayMillis = 90)) +
        scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(durationMillis = 220, delayMillis = 90),
        )

private fun AnimatedContentTransitionScope<*>.fadeThroughExit() =
    fadeOut(animationSpec = tween(durationMillis = 90)) +
        scaleOut(
            targetScale = 0.96f,
            animationSpec = tween(durationMillis = 90),
        )
