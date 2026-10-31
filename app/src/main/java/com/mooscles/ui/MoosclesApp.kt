package com.mooscles.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import com.mooscles.designsystem.licenses.LicensesScreen
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.library.LibraryRoute
import com.mooscles.screens.progress.ExerciseProgressRoute
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressMetric
import com.mooscles.screens.records.RecordsRoute
import com.mooscles.screens.routines.RoutineEditorRoute
import com.mooscles.screens.routines.TrainRoute
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.screens.summary.WorkoutSummaryScreen
import com.mooscles.screens.workout.ActiveWorkoutRoute
import com.mooscles.screens.workout.ActiveWorkoutScreenState
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.rememberActiveWorkoutScreenState

/** The full-screen active workout (spec D1): the only route without the shell. */
internal const val WORKOUT_ROUTE = "workout"
private const val DETAIL_ROUTE = "workoutDetail/{id}?summary={summary}"
private const val SUMMARY_ARG = "summary"

private fun detailRoute(id: String, summary: Boolean) = "workoutDetail/$id?summary=$summary"

/** The exercise detail ([ExerciseInfoRoute]), opened from the library and from the workout's menu. */
internal const val EXERCISE_INFO_ROUTE = "exerciseInfo/{id}"

/** The routine editor ([RoutineEditorRoute]), opened from Train, the exercise detail and a copy's "Open". */
internal const val ROUTINE_ROUTE = "routine/{id}"

private fun routineRoute(id: String) = "routine/$id"

/** Opens an editor, replacing the one in view (a copy's "Open"), never stacking editors. */
private fun NavHostController.openRoutine(id: String) = navigate(routineRoute(id)) {
    if (currentDestination?.route == ROUTINE_ROUTE) popUpTo(ROUTINE_ROUTE) { inclusive = true }
    launchSingleTop = true
}

/**
 * Leaves the editor for Train. Idempotent: it pops to Train instead of popping once, so a second call (the routine going
 * away in the same frame as Back) lands on the same screen and never pops Train itself.
 */
internal fun NavHostController.leaveRoutineEditor() {
    navigate(ShellTabs.TRAIN) {
        popUpTo(ShellTabs.TRAIN) { inclusive = false }
        launchSingleTop = true
    }
}

private fun NavHostController.openExerciseInfo(id: String) = navigate("exerciseInfo/$id") {
    // A second tap before the first one lands opens no second detail over it.
    launchSingleTop = true
}

/**
 * The exercise progress ([ExerciseProgressRoute], spec D2): the base route `exercise/{id}` of before, with an optional
 * `metric` (`load`, `reps`, `e1rm` or `volume`; a missing or unknown one opens the convention's first).
 */
internal const val PROGRESS_ROUTE = "exercise/{id}?metric={metric}"
private const val METRIC_ARG = "metric"

private fun progressRoute(id: String, metric: ProgressMetric? = null) =
    if (metric == null) "exercise/$id" else "exercise/$id?$METRIC_ARG=${metric.route}"

/**
 * The progress's "About the exercise": back to the detail under it when that is the same exercise's (the detail's
 * "View progress"), so details never stack; otherwise the detail opens over the progress.
 */
private fun NavHostController.aboutFromProgress(id: String) {
    val below = previousBackStackEntry
    if (below?.destination?.route == EXERCISE_INFO_ROUTE && below.arguments?.getString("id") == id) popBackStack()
    else openExerciseInfo(id)
}

/**
 * The routes of the new screens that show their own placeholders while the state loads (decision
 * 17, pre-flight F21) and their own errors (decision 19, plan 4A decision 14). The one list both
 * the snackbar and the loading overlay read.
 */
private val OWN_PLACEHOLDER_ROUTES =
    setOf(WORKOUT_ROUTE, ShellTabs.TRAIN, ROUTINE_ROUTE, ShellTabs.LIBRARY, EXERCISE_INFO_ROUTE, ShellTabs.RECORDS, PROGRESS_ROUTE)

/** The route the app's frame is drawn for, and [summary] when it is the end-of-workout summary. */
internal data class ShownRoute(val route: String, val summary: Boolean = false) {
    /** A new screen: its own top bar, background and errors (the summary too). */
    val newScreen: Boolean get() = route in OWN_PLACEHOLDER_ROUTES || summary
    /** Whether the app's spinner covers it while the state loads (not the routes with their own placeholders). */
    val loadingOverlay: Boolean get() = route !in OWN_PLACEHOLDER_ROUTES
}

/**
 * The route of the back stack entry in view, given as its [entryRoute] and [entrySummary] (null
 * without an entry). Only a real entry counts (as the selected tab): the first frame after a
 * recreation has none yet, and it keeps the route the last entry had (saved), so the library,
 * the detail or the summary are not drawn for one frame as Train, with the app's spinner or the
 * global snackbar (final review, item 4). Before any entry, it is Train, the start destination.
 */
@Composable
internal fun rememberShownRoute(entryRoute: String?, entrySummary: Boolean): ShownRoute {
    var savedRoute by rememberSaveable { mutableStateOf(ShellTabs.TRAIN) }
    var savedSummary by rememberSaveable { mutableStateOf(false) }
    SideEffect {
        if (entryRoute != null) {
            savedRoute = entryRoute
            savedSummary = entrySummary
        }
    }
    return if (entryRoute != null) ShownRoute(entryRoute, entrySummary) else ShownRoute(savedRoute, savedSummary)
}

/**
 * The app: the old screens and, from `:screens`, the Train tab ([TrainRoute]), the routine editor
 * ([RoutineEditorRoute], [ROUTINE_ROUTE]), the active workout ([ActiveWorkoutRoute]), the
 * end-of-workout summary ([WorkoutSummaryScreen]), the library tab ([LibraryRoute]), the exercise
 * detail ([ExerciseInfoRoute], [EXERCISE_INFO_ROUTE]), the Records tab ([RecordsRoute]), the exercise
 * progress ([ExerciseProgressRoute], [PROGRESS_ROUTE]) and the shell's bottom ([AppShellBottom]).
 *
 * Every route but [WORKOUT_ROUTE] has the shell: the workout bar while a workout is active and the
 * four tabs. The selected tab is the root of the stack in view, kept across recreation; tapping it
 * again goes back to that root, also from the gallery and the licenses (#20). The workout is
 * opened by a start (Train, a routine), the workout bar, or a new [workoutRequest] (the rest
 * notification), each new value once. Minimizing it, or the system Back, returns to where it was
 * opened. Finishing replaces it with `workoutDetail/{id}?summary=true`.
 *
 * The new screens bring their own top bar, show their own errors in the app's language (the global
 * snackbar waits until they are left) and their own placeholders while the state loads (the
 * spinner overlay is only the old screens'). Train and the library open Settings from their
 * shortcut; Train opens the editor, whose "Open" on a copy replaces it, and whose deleted routine
 * returns to Train ([leaveRoutineEditor]); the detail opens the progress, the editor and the workout. A record opens
 * the progress on its metric; the progress's sessions and a record's "Open workout" open the workout's detail.
 *
 * With the application's progress index provided ([LocalProgressIndexCache]), each new history (and its records) is
 * prewarmed once the state has loaded: built off the main thread after a short delay, with the Records tab's query,
 * so the tab and the progress open on a ready index (plan 5A decision 9).
 *
 * [timerState] is the ticking rest timer, read only by the workout bar and the workout's timer;
 * without it they read [UiState.timer]. [foreground] is whether the app is in the foreground (the
 * Activity's lifecycle, plan decision 15): the detail's figure stops while it is not.
 * [navController] is the graph's controller; tests pass their own to open a route directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoosclesApp(
    state: UiState,
    actions: MoosclesActions,
    timerState: State<TimerUi>? = null,
    workoutRequest: Long = 0L,
    foreground: Boolean = true,
    navController: NavHostController = rememberNavController(),
) {
    MoosclesTheme(state.preferences.theme) {
        val nav = navController
        val entry by nav.currentBackStackEntryAsState()
        val entryRoute = entry?.destination?.route
        val shown = rememberShownRoute(entryRoute, entryRoute == DETAIL_ROUTE && entry?.arguments?.getBoolean(SUMMARY_ARG) == true)
        val route = shown.route
        val fallbackTimer = rememberUpdatedState(state.timer)
        val timer = timerState ?: fallbackTimer

        // The root of the stack in view: set by a tab, and by reaching a tab's route any other way
        // (shown at once, in the same frame; kept for the routes above it). Only a real entry counts:
        // the first frame after a recreation has none yet, and its stand-in Train must not replace
        // the restored tab of the screen above a root (a change of language, theme or text size).
        var savedTab by rememberSaveable { mutableStateOf(ShellTabs.TRAIN) }
        val reached = entry?.destination?.route?.takeIf { it in ShellTabs.keys }
        val selectedTab = reached ?: savedTab
        SideEffect { if (reached != null) savedTab = reached }
        fun selectTab(tab: String) {
            // A saved tab stack never keeps the workout: restored after the workout ended, it
            // would show "No workout in progress". Leaving drops what was opened from it.
            nav.popBackStack(WORKOUT_ROUTE, inclusive = true)
            // Train is the root itself, never a restored tail that happens to have been saved under its ID.
            if (tab == ShellTabs.TRAIN) nav.clearBackStack(ShellTabs.TRAIN)
            nav.navigate(tab) {
                popUpTo(nav.graph.startDestinationId) { saveState = tab != ShellTabs.TRAIN }
                launchSingleTop = true
                restoreState = tab != ShellTabs.TRAIN
            }
            savedTab = tab
        }
        // Back to the tab's own root, dropping what was opened on it (the gallery and licenses too).
        fun reselectTab(tab: String) {
            if (!nav.popBackStack(tab, inclusive = false)) selectTab(tab)
        }
        // Back to the workout already in the stack (a screen opened from it), never a second one.
        fun openWorkout() {
            if (!nav.popBackStack(WORKOUT_ROUTE, inclusive = false)) nav.navigate(WORKOUT_ROUTE) { launchSingleTop = true }
        }

        // Decision 17: the routine of the last start gives the skeleton one card per exercise.
        var startedRoutineId by rememberSaveable { mutableStateOf<String?>(null) }
        val hosted = remember(actions) {
            object : MoosclesActions by actions {
                override fun startWorkout(routineId: String?) {
                    startedRoutineId = routineId
                    actions.startWorkout(routineId)
                }
            }
        }
        val skeletonCards = startedRoutineId?.let { id -> state.routines.find { it.id == id } }
            ?.exercises?.size?.coerceAtLeast(1) ?: 1

        // The workout screen's own state (cleared fields, drafts, cards opened by hand, the pending
        // undo) lives here, above the NavHost: minimizing and Back pop the workout's entry, and the
        // spec keeps that state for the session. A new workout, or none, starts afresh; while the
        // state loads (a process death) the last workout's id stands, so the restored state stays.
        var workoutScreenFor by rememberSaveable { mutableStateOf<String?>(null) }
        val workoutScreenKey = if (state.loading) workoutScreenFor else state.active?.id
        SideEffect { workoutScreenFor = workoutScreenKey }
        val workoutScreen = rememberActiveWorkoutScreenState(workoutScreenKey)

        // Plan 5A decision 9: the next history's index is on its way before a screen asks for it. Never while
        // loading, whose empty lists are not the history. The prewarm never throws (a failure waits for a screen).
        val progressIndex = LocalProgressIndexCache.current
        LaunchedEffect(progressIndex, state.history, state.records, state.loading) {
            if (progressIndex != null && !state.loading) progressIndex.prewarm(state.history, state.records, TimeZone.currentSystemDefault())
        }

        var handledDiscard by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.discardNavigationRequest, state.loading) {
            if (!state.loading && state.discardNavigationRequest == 0L) {
                // A new process starts a new ViewModel event sequence, while
                // Android may restore this composition's saved navigation state.
                handledDiscard = 0L
            } else if (!state.loading && state.discardNavigationRequest != handledDiscard) {
                // A saved tab tail can otherwise restore the discarded workout route.
                (ShellTabs.keys + WORKOUT_ROUTE).forEach { nav.clearBackStack(it) }
                nav.navigate(ShellTabs.TRAIN) {
                    popUpTo(nav.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                    restoreState = false
                }
                savedTab = ShellTabs.TRAIN
                handledDiscard = state.discardNavigationRequest
            }
        }
        var handledUpdateRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(state.updateSettingsRequest) {
            if (state.updateSettingsRequest == 0L) {
                // A new process starts a new ViewModel request sequence (see handledDiscard).
                handledUpdateRequest = 0L
            } else if (state.updateSettingsRequest != handledUpdateRequest) {
                // Lands on the updates section, also when Settings is already open: that entry is
                // replaced, since a single-top navigation would keep its old section argument.
                // A request already handled is not repeated when the composition is recreated.
                // The effect can run before the NavHost sets its graph: wait for the first entry.
                nav.currentBackStackEntryFlow.first()
                nav.navigate(SettingsRoutes.settings(SettingsRoutes.UPDATES)) {
                    popUpTo(SettingsRoutes.SETTINGS) { inclusive = true }
                    launchSingleTop = true
                }
                handledUpdateRequest = state.updateSettingsRequest
            }
        }
        var handledWorkoutRequest by rememberSaveable { mutableLongStateOf(0L) }
        LaunchedEffect(workoutRequest) {
            if (workoutRequest == 0L) {
                handledWorkoutRequest = 0L
            } else if (workoutRequest != handledWorkoutRequest) {
                // Opened once per request, as the update request above: never again on recreation.
                nav.currentBackStackEntryFlow.first()
                openWorkout()
                handledWorkoutRequest = workoutRequest
            }
        }

        // Decision 19 (and plan 4A decision 14): the new screens show their own localized error and
        // clear it; the global snackbar waits until the workout, the summary, the library, the
        // exercise detail, the Records tab or the exercise progress is left.
        val snack = remember { SnackbarHostState() }
        val newScreen = shown.newScreen
        LaunchedEffect(state.error, newScreen) {
            if (!newScreen) state.error?.let { snack.showSnackbar(it); actions.clearError() }
        }
        Scaffold(
            containerColor = if (newScreen) MTheme.colors.bgBase else MaterialTheme.colorScheme.background,
            topBar = {
                // Settings has the design system's top bar; the gallery, the licenses and the new
                // screens bring their own.
                if (route == SettingsRoutes.SETTINGS) SettingsTopBar(onBack = { nav.popBackStack() })
                else if (!newScreen && route != DeveloperGallery.ROUTE && route != SettingsRoutes.LICENSES) TopAppBar(title = { Text("mooscles", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) },
                    navigationIcon = { if (route !in ShellTabs.keys) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                    actions = { IconButton(onClick = { nav.navigate("settings") { launchSingleTop = true } }) { Icon(Icons.Outlined.Tune, "Settings") } })
            },
            bottomBar = {
                if (route != WORKOUT_ROUTE) ShellBottom(state, timer, selectedTab, ::selectTab, ::reselectTab, ::openWorkout, hosted)
            }, snackbarHost = { SnackbarHost(snack) },
        ) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
                // Keep the graph installed even while loading, including restores.
                NavHost(nav, startDestination = ShellTabs.TRAIN, modifier = Modifier.fillMaxSize()) {
                    composable(ShellTabs.TRAIN) {
                        TrainRoute(state, hosted, onOpenRoutine = { nav.openRoutine(it) }, onOpenWorkout = ::openWorkout,
                            onOpenSettings = { nav.navigate(SettingsRoutes.settings()) { launchSingleTop = true } })
                    }
                    composable(ROUTINE_ROUTE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        RoutineEditorRoute(
                            routineId = page.arguments?.getString("id").orEmpty(),
                            state = state,
                            actions = hosted,
                            onBack = { nav.popBackStack() },
                            onGone = { nav.leaveRoutineEditor() },
                            onOpenWorkout = ::openWorkout,
                            onExerciseInfo = { nav.openExerciseInfo(it) },
                            onOpenRoutine = { nav.openRoutine(it) },
                        )
                    }
                    composable(WORKOUT_ROUTE) { WorkoutRoute(state, timer, hosted, nav, skeletonCards, workoutScreen, onChooseRoutine = { selectTab(ShellTabs.TRAIN) }) }
                    composable(ShellTabs.LIBRARY) {
                        LibraryRoute(state, hosted, onOpenExercise = { nav.openExerciseInfo(it) },
                            onOpenSettings = { nav.navigate(SettingsRoutes.settings()) { launchSingleTop = true } })
                    }
                    composable(EXERCISE_INFO_ROUTE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { page ->
                        ExerciseInfoRoute(
                            exerciseId = page.arguments?.getString("id").orEmpty(),
                            state = state,
                            actions = hosted,
                            onBack = { nav.popBackStack() },
                            onOpenHistory = { nav.navigate(progressRoute(it)) },
                            onOpenRoutine = { nav.openRoutine(it) },
                            onOpenWorkout = ::openWorkout,
                            foreground = foreground,
                        )
                    }
                    composable(ShellTabs.HISTORY) { HistoryScreen(state, hosted, onDetail = { nav.navigate(detailRoute(it, summary = false)) }) }
                    composable(DETAIL_ROUTE, arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument(SUMMARY_ARG) { type = NavType.BoolType; defaultValue = false })) { page ->
                        val workout = state.history.find { it.id == page.arguments?.getString("id") }
                        val asSummary = page.arguments?.getBoolean(SUMMARY_ARG) ?: false
                        when {
                            workout == null -> MissingScreen("Workout unavailable")
                            asSummary -> SummaryRoute(workout, state, hosted, onDone = { selectTab(ShellTabs.TRAIN) },
                                onDetails = { nav.navigate(detailRoute(workout.id, summary = false)) })
                            else -> WorkoutDetailScreen(workout, state, hosted, onDeleted = { nav.popBackStack() }, onExerciseHistory = { nav.navigate(progressRoute(it)) })
                        }
                    }
                    composable(PROGRESS_ROUTE, arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument(METRIC_ARG) { type = NavType.StringType; nullable = true; defaultValue = null },
                    )) { page ->
                        val id = page.arguments?.getString("id").orEmpty()
                        ExerciseProgressRoute(
                            exerciseId = id,
                            metric = page.arguments?.getString(METRIC_ARG),
                            state = state,
                            actions = hosted,
                            onBack = { nav.popBackStack() },
                            onAbout = { nav.aboutFromProgress(id) },
                            onOpenWorkout = { nav.navigate(detailRoute(it, summary = false)) },
                        )
                    }
                    composable(ShellTabs.RECORDS) {
                        RecordsRoute(state, hosted,
                            onOpenProgress = { id, metric -> nav.navigate(progressRoute(id, metric)) },
                            onOpenWorkout = { nav.navigate(detailRoute(it, summary = false)) },
                            onOpenSettings = { nav.navigate(SettingsRoutes.settings()) { launchSingleTop = true } })
                    }
                    composable(SettingsRoutes.SETTINGS, arguments = listOf(navArgument(SettingsRoutes.SECTION_ARG) { type = NavType.StringType; defaultValue = "" })) { page ->
                        SettingsScreen(state, hosted, section = page.arguments?.getString(SettingsRoutes.SECTION_ARG).orEmpty(),
                            onOpenLicenses = { nav.navigate(SettingsRoutes.LICENSES) },
                            onOpenGallery = { nav.navigate(DeveloperGallery.ROUTE) })
                    }
                    composable(SettingsRoutes.LICENSES) { LicensesScreen(onBack = { nav.popBackStack() }) }
                    composable(DeveloperGallery.ROUTE) { DeveloperGalleryRoute(nav) }
                }
                // The new screens show their own placeholders while loading (decision 17, pre-flight F21).
                if (state.loading && shown.loadingOverlay) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

/**
 * The shell under every route but the workout. The tab bar does not pad itself for the system
 * bars, so the room they take is added here in the bar's own color: under it for the navigation
 * bar, and at the sides for a navigation bar or a cutout there (landscape). The keyboard is then
 * counted without the bottom room, which keeps the old `WorkoutBottomBar` geometry: the tabs stay
 * put and the workout bar rides on the keyboard. [systemBars] and [ime] are the real insets in the
 * app; tests inject their own.
 */
@Composable
internal fun ShellBottom(
    state: UiState,
    timer: State<TimerUi>,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onReselectTab: (String) -> Unit,
    onOpenWorkout: () -> Unit,
    actions: MoosclesActions,
    systemBars: WindowInsets = WindowInsets.navigationBars.union(WindowInsets.displayCutout),
    ime: WindowInsets = WindowInsets.ime,
) {
    val raised = MTheme.colors.bgRaised
    val keyboard = remember(ime, systemBars) { ime.exclude(systemBars.only(WindowInsetsSides.Bottom)) }
    Column(
        Modifier
            .fillMaxWidth()
            // Only the side strips: behind the bar's rounded top the screen shows through.
            .drawBehind {
                val left = systemBars.getLeft(this, layoutDirection).toFloat()
                val right = systemBars.getRight(this, layoutDirection).toFloat()
                if (left > 0f) drawRect(raised, size = Size(left, size.height))
                if (right > 0f) drawRect(raised, topLeft = Offset(size.width - right, 0f), size = Size(right, size.height))
            }
            .windowInsetsPadding(systemBars.only(WindowInsetsSides.Horizontal)),
    ) {
        AppShellBottom(state, timer, selectedTab, onSelectTab, onReselectTab, onOpenWorkout, actions, imeInsets = keyboard)
        Spacer(Modifier.fillMaxWidth().background(raised).windowInsetsBottomHeight(systemBars).testTag(SHELL_SYSTEM_BAR_TAG))
    }
}

/** The room under the tab bar for the system navigation bar. */
internal const val SHELL_SYSTEM_BAR_TAG = "shell-system-bar"

/**
 * The `workout` route: minimizing, and the system Back, return to where the workout was opened
 * (Train when nothing is under it); the summary replaces the workout in the stack, which the
 * route relies on to open it once. [screen] is the screen's state, kept by [MoosclesApp].
 */
@Composable
private fun WorkoutRoute(
    state: UiState,
    timer: State<TimerUi>,
    actions: MoosclesActions,
    nav: NavHostController,
    skeletonCards: Int,
    screen: ActiveWorkoutScreenState,
    onChooseRoutine: () -> Unit,
) {
    val minimize = { if (!nav.popBackStack()) nav.navigate(ShellTabs.TRAIN) }
    BackHandler(onBack = minimize)
    ActiveWorkoutRoute(
        state = state,
        timer = timer,
        actions = actions,
        onMinimize = minimize,
        onOpenSummary = { id -> nav.navigate(detailRoute(id, summary = true)) { popUpTo(WORKOUT_ROUTE) { inclusive = true } } },
        onExerciseHistory = { nav.navigate(progressRoute(it)) },
        onChooseRoutine = onChooseRoutine,
        onExerciseInfo = { nav.openExerciseInfo(it) },
        skeletonCards = skeletonCards,
        screen = screen,
    )
}

/**
 * The summary of a finished [workout]; Share opens the card preview over it. An error shows in the
 * app's language and is handed back, as in the workout (decision 19).
 */
@Composable
private fun SummaryRoute(workout: WorkoutUi, state: UiState, actions: MoosclesActions, onDone: () -> Unit, onDetails: () -> Unit) {
    var share by rememberSaveable(workout.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        WorkoutSummaryScreen(workout, state.history, state.preferences.pounds, onDone = onDone, onShare = { share = true }, onDetails = onDetails)
        ScreenErrorToast(state.errorCode, actions, Modifier.fillMaxSize())
    }
    if (share) WorkoutSharePreview(workout.id, state.preferences.pounds, onDismiss = { share = false })
}

@Composable
private fun MissingScreen(title: String) = EmptyState(Icons.Outlined.SearchOff, title, "This item may have been deleted. Return to your library to continue.")
