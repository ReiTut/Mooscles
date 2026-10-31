package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.designsystem.licenses.LicensesContentTag
import com.mooscles.screens.library.LibraryScreenTag
import com.mooscles.screens.progress.ExerciseProgressTag
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.unit.dp
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The app shell around the new screens (Task 24): which routes have the tabs and the workout bar,
 * how the tabs, the bar, a start, minimizing, Back and the summary move between routes, and what
 * the global snackbar and a workout request do. The real navigation graph, with fake actions that
 * change the state as the view model would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ShellNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell")
    private val row = ExerciseUi("row", "Barbell row", "Back", "Barbell")
    private val squat = ExerciseUi("squat", "Back squat", "Legs", "Barbell")
    private val push = RoutineUi("push", "Push day", listOf(planned("p1", bench), planned("p2", row), planned("p3", squat)))
    private val pull = RoutineUi("pull", "Pull day", listOf(planned("q1", row), planned("q2", squat)))
    private val past = WorkoutUi("past", "Last week", 1_000_000L, endedAt = 4_000_000L,
        exercises = listOf(WorkoutExerciseUi("past-entry", bench, listOf(SetUi("past-set", 8, 80.0, completed = true)))))
    private lateinit var state: MutableState<UiState>
    private var clearErrorClears = true
    private val GALLERY_SCREEN = "gallery-screen"

    @Before fun lock() = DeveloperGallery.resetForTest()
    @After fun relock() = DeveloperGallery.resetForTest()

    @Test fun workoutRouteHasNoTabsOrBar() {
        show(activeState())
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed().performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag("app-footer").assertDoesNotExist()
        compose.onNodeWithTag(MTabBarTag).assertDoesNotExist()
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        // Nor the old top bar: the workout has its own header.
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithTag("workout-minimize").assertIsDisplayed()
    }

    @Test fun tabRoutesShowTheBarWhileActive() {
        show(activeState())
        listOf("library", "history", "records", "routines").forEach { route ->
            tab(route).performClick()
            tab(route).assertIsSelected()
            compose.onAllNodesWithTag("shell-workout-bar").assertCountEquals(1)
            compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
            assertAboveTheTabs("shell-workout-bar")
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        tab("routines").assertIsSelected()
        compose.runOnIdle { state.value = state.value.copy(active = null) }
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
    }

    @Test fun reselectingTheCurrentTabReturnsToItsRoot() {
        show(UiState(loading = false, history = listOf(past)))
        tab("history").performClick()
        compose.onNodeWithText("Last week").performClick()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        tab("history").assertIsSelected().performClick()
        compose.onNodeWithTag("workout-details").assertDoesNotExist()
        compose.onNodeWithTag("history-list").assertIsDisplayed()
        tab("history").assertIsSelected()
        // At the root, reselecting stays there.
        tab("history").performClick()
        compose.onNodeWithTag("history-list").assertIsDisplayed()
    }

    @Test fun reselectFromGalleryAndLicensesReturnsToTheRoot() {
        show(UiState(loading = false))
        tab("library").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("open-licenses").performScrollTo().performClick()
        compose.onNodeWithTag(LicensesContentTag).assertExists()
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
        tab("library").assertIsSelected().performClick()
        assertLibraryRoot()
        var now = 0L
        repeat(7) { now += 100; DeveloperGallery.registerVersionTap(now) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("open-gallery").performScrollTo().performClick()
        compose.onNodeWithTag(GALLERY_SCREEN).assertExists()
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
        tab("library").assertIsSelected().performClick()
        assertLibraryRoot()
    }

    /**
     * A recreation (a change of language, theme or text size) on a screen above a tab's root keeps
     * that tab selected: the back stack is not there yet in the first frame after it, and that frame
     * must not count as having reached Train.
     */
    @Test fun aRecreationAboveATabsRootKeepsThatTabSelected() {
        state = mutableStateOf(UiState(loading = false, history = listOf(past)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions) }
        tab("library").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("settings-content").assertExists()
        tab("library").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings-content").assertExists()
        tab("library").assertIsSelected()
        tab("routines").assertIsNotSelected()
        // The same from the history's workout detail, a screen of another tab.
        tab("history").performClick()
        compose.onNodeWithText("Last week").performClick()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        tab("history").assertIsSelected()
        // Reselecting it still goes back to its own root.
        tab("history").performClick()
        compose.onNodeWithTag("history-list").assertIsDisplayed()
    }

    @Test fun startNavigatesAtOnceAndShowsOneSkeletonPerRoutineExercise() {
        show(UiState(loading = false, routines = listOf(push, pull), exercises = listOf(bench, row, squat)))
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-push-start"))
        compose.onNodeWithTag("routine-push-start").performClick()
        // The view model has not answered: the workout opens on placeholders, one per exercise.
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onAllNodesWithTag("workout-skeleton-card", useUnmergedTree = true).assertCountEquals(3)
        compose.runOnIdle { assertEquals(listOf("startWorkout:push"), calls) }
        // From the routine editor, with another routine.
        compose.runOnIdle { state.value = state.value.copy(startingWorkout = false) }
        // "No workout in progress": its "Choose routine" goes to Train.
        compose.onNodeWithText("Choose routine").performClick()
        tab("routines").assertIsSelected()
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-pull-card"))
        compose.onNodeWithTag("routine-pull-card").performClick()
        compose.onNodeWithTag("routine-editor-start").performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onAllNodesWithTag("workout-skeleton-card", useUnmergedTree = true).assertCountEquals(2)
        // An empty workout has one placeholder.
        compose.runOnIdle { state.value = state.value.copy(startingWorkout = false) }
        compose.onNodeWithTag("workout-none").assertIsDisplayed()
        compose.onNodeWithText("Empty workout").performClick()
        compose.onAllNodesWithTag("workout-skeleton-card", useUnmergedTree = true).assertCountEquals(1)
        compose.runOnIdle { assertEquals(listOf("startWorkout:push", "startWorkout:pull", "startWorkout:null"), calls) }
    }

    @Test fun minimizeReturnsWhereTheWorkoutWasOpened() {
        show(activeState())
        tab("library").performClick()
        openFromTheBar()
        compose.onNodeWithTag("workout-minimize").performClick()
        assertLibraryRoot()
        tab("history").performClick()
        openFromTheBar()
        compose.onNodeWithTag("workout-minimize").performClick()
        tab("history").assertIsSelected()
        compose.onNodeWithTag("history-list").assertIsDisplayed()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
    }

    /** Pins the behavior, not the mechanism: the graph never has the workout at its root, so the
     *  NavHost's own Back would pop the same entry as the route's BackHandler. */
    @Test fun systemBackFromTheWorkoutMinimizes() {
        show(activeState())
        tab("records").performClick()
        openFromTheBar()
        back()
        tab("records").assertIsSelected()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        // From Train, the root, Back from the workout still lands on Train, not out of the app.
        tab("routines").performClick()
        openFromTheBar()
        back()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        compose.runOnIdle { assertFalse(compose.activity.isFinishing) }
    }

    @Test fun summaryTrueOpensTheNewSummaryAndFalseTheOldDetail() {
        show(activeState(completed = true))
        tab("library").performClick()
        openFromTheBar()
        compose.onNodeWithTag("workout-header-finish").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Workout complete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("summary-stats").assertExists()
        compose.onNodeWithTag("workout-details").assertDoesNotExist()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag(MTabBarTag).assertIsDisplayed()
        // The summary is a new screen: its own header, no old top bar, and the global snackbar waits.
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithText("View details").performScrollTo().performClick()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        compose.onNodeWithText("Session breakdown", substring = true, ignoreCase = true).assertExists()
        compose.onNodeWithText("Strong work.").assertDoesNotExist()
        back()
        compose.onNodeWithTag("summary-stats").assertExists()
        // The workout left the stack when the summary opened: Back returns to where it was opened.
        back()
        assertLibraryRoot()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("finishWorkout:"), calls.filter { it.startsWith("finish") }) }
    }

    @Test fun summaryDoneGoesToTrainAndTheSummaryStaysAboveTheShell() {
        show(activeState(completed = true))
        openFromTheBar()
        finish()
        val tabs = compose.onNodeWithTag(MTabBarTag).fetchSemanticsNode().boundsInRoot
        val done = compose.onNodeWithText("Done").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertTrue("The summary's last action must be above the tab bar: $done vs $tabs", done.bottom <= tabs.top)
        compose.onNodeWithText("Done").performClick()
        tab("routines").assertIsSelected()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }

    @Test fun summaryShareOpensThePreview() {
        show(activeState(completed = true))
        openFromTheBar()
        finish()
        compose.onNodeWithText("Share").performScrollTo().performClick()
        compose.onNodeWithTag("share-preview-close").assertExists().performClick()
        compose.onNodeWithTag("share-preview-close").assertDoesNotExist()
        compose.onNodeWithTag("summary-stats").assertExists()
    }

    @Test fun exerciseHistoryFromTheWorkoutReturnsToIt() {
        show(activeState())
        openFromTheBar()
        compose.onNodeWithTag(ExerciseMenuTag).performClick()
        compose.onNodeWithTag("exercise-menu-history").performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        back()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag(MTabBarTag).assertDoesNotExist()
    }

    /** Spec D2: the base route `exercise/{id}` of before opens the convention's first metric. */
    @Test fun progressRouteWithoutMetricOpensTheDefault() {
        showWithNav(activeState())
        openRoute("exercise/bench")
        assertMetricSelected("load")
        compose.onNodeWithTag("progress-metric-e1rm").assertIsNotSelected()
    }

    @Test fun progressRouteWithMetricOpensIt() {
        showWithNav(activeState())
        openRoute("exercise/bench?metric=e1rm")
        assertMetricSelected("e1rm")
        compose.onNodeWithTag("progress-metric-load").assertIsNotSelected()
    }

    /** Only the four lower-case routes are metrics: anything else opens the default, never a blank chart. */
    @Test fun unknownMetricFallsBackToTheDefault() {
        showWithNav(activeState())
        openRoute("exercise/bench?metric=LOAD")
        assertMetricSelected("load")
        back()
        openRoute("exercise/bench?metric=calories")
        assertMetricSelected("load")
    }

    @Test fun theBarFromAScreenOpenedInTheWorkoutReturnsToThatWorkout() {
        show(activeState(completed = true))
        tab("library").performClick()
        openFromTheBar()
        openExerciseHistory()
        // The bar goes back to the workout already in the stack: one entry, not a second one.
        openFromTheBar()
        compose.onNodeWithTag("workout-minimize").performClick()
        assertLibraryRoot()
        openFromTheBar()
        finish()
        back()
        assertLibraryRoot()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag("workout-none").assertDoesNotExist()
    }

    @Test fun aSavedTabNeverKeepsTheWorkoutAfterItEnds() {
        show(activeState(completed = true))
        tab("library").performClick()
        openFromTheBar()
        openExerciseHistory()
        // Leaving for another tab from a screen opened in the workout, then finishing there.
        tab("history").performClick()
        openFromTheBar()
        finish()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        tab("library").performClick()
        compose.onNodeWithTag("workout-none").assertDoesNotExist()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        assertLibraryRoot()
        back()
        compose.onNodeWithTag("workout-none").assertDoesNotExist()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }

    @Test fun theSummaryShowsAndClearsItsErrorWithoutTheSnackbar() {
        show(activeState(completed = true))
        openFromTheBar()
        finish()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw summary failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw summary failure").assertDoesNotExist()
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle {
            assertEquals(1, calls.count { it == "clearError:" })
            assertNull(state.value.error)
        }
    }

    @Test fun theShellPadsTheSystemBarsAndRidesTheKeyboard() {
        val ime = mutableIntStateOf(0)
        val density = compose.activity.resources.displayMetrics.density
        state = mutableStateOf(activeState().copy(timer = TimerUi("Bench press", 90, 120, running = true)))
        val timer = mutableStateOf(state.value.timer)
        compose.setContent {
            MoosclesTheme("light") {
                Column(Modifier.fillMaxSize().testTag("window")) {
                    Box(Modifier.weight(1f).fillMaxWidth())
                    ShellBottom(state.value, timer, "routines", {}, {}, {}, actions,
                        systemBars = WindowInsets(left = 40.dp, right = 24.dp, bottom = 32.dp),
                        ime = WindowInsets(bottom = ime.intValue))
                }
            }
        }
        val window = bounds("window")
        val tabs = bounds(MTabBarTag)
        val bar = bounds("shell-workout-bar")
        val room = bounds(SHELL_SYSTEM_BAR_TAG)
        assertEquals("The tabs clear the left bar", 40 * density, tabs.left, 1f)
        assertEquals("The tabs clear the right bar", window.right - 24 * density, tabs.right, 1f)
        assertEquals(tabs.left, bar.left, 1f)
        assertEquals(tabs.right, bar.right, 1f)
        assertEquals("The navigation bar's room is under the tabs", tabs.bottom, room.top, 1f)
        assertEquals(window.bottom, room.bottom, 1f)
        assertEquals(32 * density, room.height, 1f)
        // A keyboard 300 dp tall (navigation bar included): the bar on it, the tabs where they were.
        compose.runOnIdle { ime.intValue = (300 * density).toInt() }
        assertEquals(tabs, bounds(MTabBarTag))
        assertEquals(window.bottom - 300 * density, bounds("shell-workout-bar").bottom, 1f)
        assertEquals(bar.height, bounds("shell-workout-bar").height, 1f)
    }

    @Test fun globalSnackbarNeverShowsOnTheWorkoutRoute() {
        // The error stays in the state after clearError here, so a snackbar would stay on screen too.
        clearErrorClears = false
        show(activeState())
        openFromTheBar()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw save failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw save failure").assertDoesNotExist()
        // The workout shows its own words for the code and hands the error back.
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle { assertEquals(1, calls.count { it == "clearError:" }) }
        // On a tab route, the old screens still get the snackbar (Train is a new screen since plan 4B).
        compose.runOnIdle { state.value = state.value.copy(error = null, errorCode = null) }
        compose.onNodeWithTag("workout-minimize").performClick()
        tab("history").performClick()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw routine failure", errorCode = UiError.GENERIC) }
        compose.onNodeWithText("Raw routine failure").assertExists()
    }

    @Test fun workoutRequestOpensTheWorkoutOnce() {
        state = mutableStateOf(activeState())
        val request = mutableLongStateOf(0L)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions, workoutRequest = request.longValue) }
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        compose.runOnIdle { request.longValue = 1L }
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag("workout-minimize").performClick()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        // Neither a recomposition nor a recreation with the same request opens it again.
        compose.runOnIdle { state.value = state.value.copy(active = state.value.active!!.copy(name = "Renamed")) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.runOnIdle { request.longValue = 2L }
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
    }

    /** The spec keeps the workout screen's own state for the session: minimizing and Back pop its entry, not it. */
    @Test fun theWorkoutScreenStateOutlivesMinimizeBackAndRecreation() {
        state = mutableStateOf(activeState())
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions) }
        openFromTheBar()
        compose.onNodeWithTag("workout-set-set-1").assertExists()
        // Closed by hand: the advance never opens it again (D9), nor does leaving the workout.
        toggleCard("entry")
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
        compose.onNodeWithTag("workout-minimize").performClick()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        openFromTheBar()
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
        back()
        openFromTheBar()
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
        // A recreation while the state reloads (a process death) keeps it too.
        compose.runOnIdle { state.value = state.value.copy(loading = true, active = null) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { state.value = activeState() }
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag("workout-set-set-1").assertDoesNotExist()
    }

    @Test fun theWorkoutScreenStateStartsAfreshForTheNextWorkout() {
        show(activeState(completed = true))
        openFromTheBar()
        toggleCard("entry")
        compose.onNodeWithTag("workout-set-set-2").assertDoesNotExist()
        finish()
        // The next workout happens to reuse the entry's id: nothing of the last one carries over.
        compose.runOnIdle { state.value = activeState().let { it.copy(active = it.active!!.copy(id = "next")) } }
        compose.onNodeWithTag("tab-routines").performClick()
        openFromTheBar()
        compose.onNodeWithTag("workout-set-set-2").assertExists()
    }

    private fun planned(id: String, exercise: ExerciseUi) = PlannedExerciseUi(id, exercise, sets = 2, reps = 8)

    private fun activeState(completed: Boolean = false) = UiState(loading = false, exercises = listOf(bench, row),
        history = listOf(past), active = WorkoutUi("active", "Push day", System.currentTimeMillis() - 60_000L,
            exercises = listOf(WorkoutExerciseUi("entry", bench, listOf(SetUi("set-1", 8, 80.0, completed = completed),
                SetUi("set-2", 8, 80.0))))))

    private fun show(initial: UiState) {
        state = mutableStateOf(initial)
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
    }

    private lateinit var nav: NavHostController

    /** [show] with the graph's controller kept in [nav], to open a route directly. */
    private fun showWithNav(initial: UiState) {
        state = mutableStateOf(initial)
        compose.setContent { MoosclesApp(state.value, actions, navController = rememberNavController().also { nav = it }) }
        compose.waitForIdle()
    }

    private fun openRoute(route: String) {
        compose.runOnIdle { nav.navigate(route) }
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
    }

    /** The metric chips come with the chart, built off the main thread. */
    private fun assertMetricSelected(route: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-metric-$route").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress-metric-$route").assertIsSelected()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")
    private fun openFromTheBar() {
        compose.onNodeWithTag("shell-workout-bar").performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
    }
    private fun finish() {
        compose.onNodeWithTag("workout-header-finish").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("summary-stats").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun toggleCard(entryId: String) {
        compose.onNode(hasTestTag("exercise-header-toggle") and hasAnyAncestor(hasTestTag("exercise-header-$entryId")), useUnmergedTree = true)
            .performClick()
        compose.waitForIdle()
    }
    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }
    private fun openExerciseHistory() {
        compose.onNodeWithTag(ExerciseMenuTag).performClick()
        compose.onNodeWithTag("exercise-menu-history").performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
    }
    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun assertLibraryRoot() {
        tab("library").assertIsSelected()
        compose.onNodeWithTag(LibraryScreenTag).assertExists()
        compose.onNodeWithTag("settings-content").assertDoesNotExist()
        compose.onNodeWithTag(LicensesContentTag).assertDoesNotExist()
        compose.onNodeWithTag(GALLERY_SCREEN).assertDoesNotExist()
    }
    private fun assertAboveTheTabs(tag: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val tabs = compose.onNodeWithTag(MTabBarTag).fetchSemanticsNode().boundsInRoot
        assertEquals(tabs.top, node.bottom, 1f)
    }

    /** Records each call; starting, finishing, discarding and clearing change the state as the view model does. */
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Shell navigation actions"
            else -> {
                calls += method.name + ":" + (args ?: emptyArray()).joinToString(",")
                val current = state.value
                when (method.name) {
                    "startWorkout" -> state.value = current.copy(startingWorkout = true)
                    "clearError" -> if (clearErrorClears) state.value = current.copy(error = null, errorCode = null)
                    "finishWorkout" -> current.active?.let { active ->
                        state.value = current.copy(active = null, history = listOf(active.copy(endedAt = System.currentTimeMillis())) + current.history)
                    }
                }
                null
            }
        }
    } as MoosclesActions
}
