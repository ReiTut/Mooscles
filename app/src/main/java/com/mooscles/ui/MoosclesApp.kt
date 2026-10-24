package com.mooscles.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainHeight
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument

private data class Destination(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(
    Destination("routines", "Train", Icons.Outlined.FitnessCenter),
    Destination("workout", "Workout", Icons.Outlined.PlayCircleOutline),
    Destination("library", "Library", Icons.Outlined.GridView),
    Destination("history", "History", Icons.Outlined.History),
    Destination("records", "Records", Icons.Outlined.EmojiEvents),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoosclesApp(state: UiState, actions: MoosclesActions, timerState: State<TimerUi>? = null) {
    MoosclesTheme(state.preferences.theme) {
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route ?: "routines"
        var handledDiscard by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.discardNavigationRequest, state.loading) {
            if (!state.loading && state.discardNavigationRequest == 0L) {
                // A new process starts a new ViewModel event sequence, while
                // Android may restore this composition's saved navigation state.
                handledDiscard = 0L
            } else if (!state.loading && state.discardNavigationRequest != handledDiscard) {
                // Saved top-level tails can otherwise restore the discarded
                // Workout route when Train is selected or the Activity returns.
                destinations.forEach { nav.clearBackStack(it.route) }
                nav.navigate("routines") {
                    popUpTo(nav.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
                handledDiscard = state.discardNavigationRequest
            }
        }
        LaunchedEffect(state.updateSettingsRequest) {
            if (state.updateSettingsRequest > 0) nav.navigate("settings") { launchSingleTop = true }
        }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(state.error) { state.error?.let { snack.showSnackbar(it); actions.clearError() } }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("mooscles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    navigationIcon = { if (route !in destinations.map { it.route }) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                    actions = { IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Icon(Icons.Outlined.Tune, "Settings") } })
            },
            bottomBar = {
                WorkoutBottomBar(timer = { LiveWorkoutTimerDock(state, actions, timerState) }, footer = {
                    NavigationBar(Modifier.testTag("app-footer")) {
                        destinations.forEach { destination ->
                            NavigationBarItem(selected = route == destination.route, enabled = !state.loading, modifier = Modifier.testTag("tab-${destination.route}"), onClick = {
                                // Train is the root itself, never a restored tail
                                // that happens to have been saved under its ID.
                                if (destination.route == "routines") nav.clearBackStack("routines")
                                nav.navigate(destination.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = destination.route != "routines" }
                                    launchSingleTop = true
                                    restoreState = destination.route != "routines"
                                }
                            }, icon = { if (destination.route == "workout" && state.active != null) BadgedBox(badge = { Badge() }) { Icon(destination.icon, null) } else Icon(destination.icon, null) }, label = { Text(destination.label) })
                        }
                    }
                })
            }, snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                // Keep the graph installed even while loading, including restores.
                NavHost(nav, startDestination = "routines", modifier = Modifier.fillMaxSize()) {
                    composable("routines") { RoutinesScreen(state, actions, onEdit = { nav.navigate("routine/$it") }, onWorkout = { nav.navigate("workout") }) }
                    composable("routine/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        val routine = state.routines.find { it.id == page.arguments?.getString("id") }
                        if (routine != null) RoutineEditor(routine, state, actions, onWorkout = { nav.navigate("workout") })
                        else MissingScreen("Routine unavailable")
                    }
                    composable("workout") { ActiveWorkoutScreen(state.active, state.exercises, state.preferences, state.history, state.error,
                        actions, onRoutines = { nav.navigate("routines") }, onHistory = { id -> nav.navigate("workoutDetail/$id?summary=true") },
                        onExerciseHistory = { nav.navigate("exercise/$it") }, startingWorkout = state.startingWorkout,
                        discardingWorkout = state.discardingWorkout) }
                    composable("library") { LibraryScreen(state, actions, onHistory = { nav.navigate("exercise/$it") }) }
                    composable("history") { HistoryScreen(state, actions, onDetail = { nav.navigate("workoutDetail/$it?summary=false") }) }
                    composable("workoutDetail/{id}?summary={summary}", arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("summary") { type = NavType.BoolType; defaultValue = false })) { page ->
                        val workout = state.history.find { it.id == page.arguments?.getString("id") }
                        if (workout != null) WorkoutDetailScreen(workout, state, actions, page.arguments?.getBoolean("summary") ?: false, onDeleted = { nav.popBackStack() }, onExerciseHistory = { nav.navigate("exercise/$it") })
                        else MissingScreen("Workout unavailable")
                    }
                    composable("exercise/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        val exercise = state.exercises.find { it.id == page.arguments?.getString("id") }
                        if (exercise != null) ExerciseHistoryScreen(exercise, state, actions, onWorkout = { nav.navigate("workoutDetail/$it?summary=false") })
                        else MissingScreen("Exercise unavailable")
                    }
                    composable("records") { RecordsScreen(state, onExercise = { nav.navigate("exercise/$it") }) }
                    composable("settings") { SettingsScreen(state, actions) }
                }
                if (state.loading) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/** Reserve the IME once without moving the existing footer above the keyboard. */
@Composable
internal fun WorkoutBottomBar(
    imeInsets: WindowInsets = WindowInsets.ime,
    timer: @Composable () -> Unit,
    footer: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    Layout(modifier = Modifier.fillMaxWidth(), content = {
        Box { timer() }
        Box { footer() }
    }) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val timerPlaceable = measurables[0].measure(loose)
        val footerPlaceable = measurables[1].measure(loose)
        val bottomReservation = maxOf(footerPlaceable.height, imeInsets.getBottom(density))
        val height = constraints.constrainHeight(timerPlaceable.height + bottomReservation)
        layout(constraints.maxWidth, height) {
            timerPlaceable.placeRelative(0, 0)
            footerPlaceable.placeRelative(0, height - footerPlaceable.height)
        }
    }
}

@Composable
private fun LiveWorkoutTimerDock(state: UiState, actions: MoosclesActions, timerState: State<TimerUi>?) {
    // Read the 250 ms progress state here, outside the navigation and workout-list scopes.
    WorkoutTimerDock(state.copy(timer = timerState?.value ?: state.timer), actions)
}

@Composable
private fun MissingScreen(title: String) = EmptyState(Icons.Outlined.SearchOff, title, "This item may have been deleted. Return to your library to continue.")

@Composable
fun SettingsScreen(state: UiState, actions: MoosclesActions) {
    val prefs = state.preferences
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageHeading("Make it yours", "Settings", "Your training, your preferences. Stored on this device.") }
        item { UpdateSection(state.updates, actions) }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Weight units", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(selected = !prefs.pounds, onClick = { actions.setPounds(false) }, label = { Text("Kilograms · kg") })
                        FilterChip(selected = prefs.pounds, onClick = { actions.setPounds(true) }, label = { Text("Pounds · lb") })
                    }
                    Text("Records keep their original precision when you change units.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Card {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system", "light", "dark").forEach { theme -> FilterChip(selected = prefs.theme == theme, onClick = { actions.setTheme(theme) }, label = { Text(human(theme)) }) }
                    }
                }
            }
        }
        item {
            Card {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("Automatic rest", style = MaterialTheme.typography.titleMedium); Text("Start after completing a set", style = MaterialTheme.typography.bodySmall) }
                        Switch(checked = prefs.autoRest, onCheckedChange = actions::setAutoRest)
                    }
                    Text("Completing another set restarts rest using that exercise’s duration.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    RestDurationField(prefs.defaultRestSeconds, actions::setDefaultRest, Modifier.fillMaxWidth(),
                        label = "Default rest duration", testTag = "default-rest-duration")
                    Text("Each exercise can use its own rest duration.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Text("Just you and your progress", style = MaterialTheme.typography.titleMedium)
                Text("Your routines, workout history, and records stay on this device. Training works offline. App connections check and download updates from GitHub. Sharing creates a workout image on your device and opens the app you choose, where you select the recipient and confirm sending. Workout data is excluded from automatic cloud backups.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
