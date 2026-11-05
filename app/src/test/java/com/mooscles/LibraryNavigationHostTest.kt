package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.components.ExerciseMenuTag
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.screens.exercise.ExerciseInfoScreenTag
import com.mooscles.screens.library.LibraryScreenTag
import com.mooscles.screens.library.LibraryTags
import com.mooscles.screens.progress.ExerciseProgressTag
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The library tab and the exercise detail hosted by the app (plan 4A, Task 19): the routes
 * `library` and `exerciseInfo/{id}` in the real navigation graph, how they move to the old
 * screens and back, the global snackbar and the loading overlay that stay off them (plan
 * decision 14, pre-flight F21). Fake actions change the state as the view model would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private lateinit var state: MutableState<UiState>
    private var clearErrorClears = true

    private val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    // Outside the figure pilot: the detail shows the equipment's icon, not the alternating figure.
    private val incline = catalog.single { it.id == "catalog_barbell_bench_incline" }
    private val push = RoutineUi("push", "Push day", listOf(PlannedExerciseUi("p1", incline, sets = 2, reps = 8)))
    private val past = WorkoutUi("past", "Last week", 1_000_000L, endedAt = 4_000_000L,
        exercises = listOf(WorkoutExerciseUi("past-entry", incline, listOf(SetUi("past-set", 8, 60.0, completed = true)))))

    @Test fun libraryToDetailAndBackKeepsSearchFiltersAndScroll() {
        show()
        openLibrary()
        compose.onNode(hasSetTextAction()).performTextReplacement("press")
        compose.onNodeWithTag(LibraryTags.muscleChip("Chest")).performClick()
        compose.waitForIdle()
        val count = countText()
        compose.onNodeWithTag(LibraryTags.LIST).performScrollToIndex(8)
        compose.waitForIdle()
        val top = topVisibleRow()
        val topBounds = compose.onNodeWithTag(top).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag(LibraryTags.muscleChip("Chest")).assertDoesNotExist()

        compose.onNodeWithTag(top).performClick()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        compose.onNodeWithTag(LibraryScreenTag).assertDoesNotExist()
        back()

        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        assertEquals("press", compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals(top, topVisibleRow())
        assertEquals(topBounds, compose.onNodeWithTag(top).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag(LibraryTags.LIST).performScrollToIndex(0)
        compose.waitForIdle()
        compose.onNodeWithTag(LibraryTags.muscleChip("Chest")).assertIsOn()
        assertEquals(count, countText())
    }

    @Test fun detailProgressOpensTheProgressAndBackReturns() {
        show()
        openDetail(incline)
        compose.onNodeWithTag("exercise-info-history-full").performScrollTo().performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress-title").assertTextEquals(incline.name)
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        tab("library").assertIsSelected()
        back()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("exercise-info-title").assertTextEquals(incline.name)
    }

    /** Spec "Progresso do exercício": "About the exercise" returns to the detail below instead of stacking a second one. */
    @Test fun progressAboutReturnsToTheDetailBelow() {
        show()
        openDetail(incline)
        compose.onNodeWithTag("exercise-info-history-full").performScrollTo().performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.onNodeWithTag("progress-menu").performClick()
        compose.onNodeWithTag("progress-menu-about").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("exercise-info-title").assertTextEquals(incline.name)
        compose.onNodeWithTag(ExerciseProgressTag).assertDoesNotExist()
        // One detail in the stack: Back leaves it for the library, not for a progress under a second detail.
        back()
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        compose.onNodeWithTag(ExerciseProgressTag).assertDoesNotExist()
    }

    @Test fun workoutMenuAboutOpensTheDetailAndBackReturnsToTheWorkout() {
        show(UiState(loading = false, exercises = catalog, history = listOf(past),
            active = WorkoutUi("active", "Push day", System.currentTimeMillis() - 60_000L,
                exercises = listOf(WorkoutExerciseUi("entry", incline, listOf(SetUi("set-1", 8, 60.0)))))))
        compose.onNodeWithTag("shell-workout-bar").performClick()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag(ExerciseMenuTag).performClick()
        compose.onNodeWithTag("exercise-menu-about").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("exercise-info-title").assertTextEquals(incline.name)
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag("shell-workout-bar").assertIsDisplayed()
        back()
        compose.onNodeWithTag("workout-route").assertIsDisplayed()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        compose.onNodeWithTag(MTabBarTag).assertDoesNotExist()
    }

    @Test fun settingsShortcutOpensSettings() {
        show()
        openLibrary()
        compose.onNodeWithTag(LibraryTags.SETTINGS).performClick()
        compose.onNodeWithTag("settings-content").assertExists()
        compose.onNodeWithTag(LibraryScreenTag).assertDoesNotExist()
        tab("library").assertIsSelected()
        back()
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("settings-content").assertDoesNotExist()
    }

    @Test fun addToRoutineOpenOpensTheRoutineEditor() {
        show()
        openDetail(incline)
        compose.onNodeWithTag("exercise-info-add-routine").performScrollTo().performClick()
        compose.onNodeWithTag("add-to-routine-push").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(listOf("addRoutineExercise:push,${incline.id}"), calls.filter { it.startsWith("addRoutine") }) }
        compose.onNodeWithText("Added to Push day").assertExists()
        compose.onNodeWithText("Open").performClick()
        compose.onNodeWithTag("routine-editor-list").assertIsDisplayed()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        back()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
    }

    @Test fun globalSnackbarNeverShowsOnLibraryOrDetail() {
        // The error stays in the state after clearError here, so a snackbar would stay on screen too.
        clearErrorClears = false
        show()
        openLibrary()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw library failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw library failure").assertDoesNotExist()
        // The library shows its own words for the code and hands the error back.
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle { assertEquals(1, calls.count { it == "clearError:" }) }

        compose.runOnIdle { state.value = state.value.copy(error = null, errorCode = null) }
        openRow(incline)
        compose.runOnIdle { state.value = state.value.copy(error = "Raw detail failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw detail failure").assertDoesNotExist()
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle { assertEquals(2, calls.count { it == "clearError:" }) }
    }

    @Test fun reselectingTheLibraryTabFromTheDetailReturnsToTheList() {
        show()
        openDetail(incline)
        tab("library").assertIsSelected().performClick()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        tab("library").assertIsSelected()
    }

    /** Pre-flight F21: a reload on these routes shows their own placeholders, not the app's spinner. */
    @Test fun loadingShowsTheirSkeletonsNotTheOverlay() {
        show()
        // Settings retains the generic overlay; History joins the routes with their own skeleton in Phase 5B.
        compose.onNodeWithTag("train-settings").performClick()
        reload()
        compose.onAllNodes(spinner).assertCountEquals(1)
        back()
        compose.runOnIdle { state.value = state.value.copy(loading = false, exercises = catalog) }
        tab("history").performClick()
        reload()
        compose.onNodeWithTag("history-skeleton").assertIsDisplayed()
        compose.onAllNodes(spinner).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(loading = false, exercises = catalog) }
        openDetail(incline)
        reload()
        compose.onNodeWithTag("exercise-info-loading").assertIsDisplayed()
        compose.onAllNodes(spinner).assertCountEquals(0)
        back()
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        compose.onAllNodesWithTag(LibraryTags.SKELETON_ROW).onFirst().assertIsDisplayed()
        compose.onAllNodes(spinner).assertCountEquals(0)
    }

    /**
     * Final review, item 4: in the first frames after a recreation the back stack is not there yet.
     * Those frames must not count as Train: on the library and on the detail, while the state
     * reloads with an error, neither the app's spinner nor the global snackbar may flash.
     */
    @Test fun aRecreationWhileLoadingNeverFlashesTheOverlayOrTheSnackbar() {
        clearErrorClears = false
        state = mutableStateOf(UiState(loading = false, exercises = catalog, routines = listOf(push), history = listOf(past)))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
        openLibrary()
        recreateWhileLoading(restoration)
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(loading = false, exercises = catalog, error = null, errorCode = null) }
        compose.waitForIdle()
        openRow(incline)
        recreateWhileLoading(restoration)
        compose.onNodeWithTag("exercise-info-loading").assertIsDisplayed()
    }

    /** Reloads with an error and recreates: the route keeps its own placeholder, with no spinner and no snackbar. */
    private fun recreateWhileLoading(restoration: StateRestorationTester) {
        compose.runOnIdle {
            state.value = state.value.copy(loading = true, exercises = emptyList(), error = "Raw reload failure", errorCode = UiError.SAVE_FAILED)
        }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodes(spinner).assertCountEquals(0)
        compose.onNodeWithText("Raw reload failure").assertDoesNotExist()
    }

    /**
     * Final review, item 4: the first frame after a recreation has no back stack entry yet. It
     * keeps the route the last entry had (the library, the detail, the summary), never Train, so
     * neither the app's spinner nor the global snackbar flash on a new screen.
     */
    @Test fun theFrameWithoutAnEntryKeepsTheLastRoute() {
        val entry = mutableStateOf<ShownRoute?>(ShownRoute(ShellTabs.LIBRARY))
        val frames = mutableListOf<ShownRoute>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val current = entry.value
            frames += rememberShownRoute(current?.route, current?.summary ?: false)
        }
        compose.waitForIdle()
        for (shown in listOf(ShownRoute(ShellTabs.LIBRARY), ShownRoute(EXERCISE_INFO_ROUTE),
            ShownRoute(ShellTabs.HISTORY), ShownRoute("workoutDetail/{id}?summary={summary}"),
            ShownRoute("workoutDetail/{id}?summary={summary}", summary = true))) {
            compose.runOnIdle { entry.value = shown }
            compose.waitForIdle()
            // The recreation: the new composition starts without an entry.
            compose.runOnIdle { entry.value = null; frames.clear() }
            restoration.emulateSavedInstanceStateRestore()
            assertEquals(listOf(shown), frames.distinct())
            assertTrue(frames.last().newScreen)
            assertFalse(frames.last().loadingOverlay)
        }
        // Train and the editor are new screens too (plan 4B, Task 15), and Records and the progress (plan 5A,
        // Task 13); History and saved detail now own their placeholders too (phase 5B).
        assertTrue(ShownRoute(ShellTabs.TRAIN).newScreen)
        assertTrue(ShownRoute(ROUTINE_ROUTE).newScreen)
        assertTrue(ShownRoute(ShellTabs.RECORDS).newScreen)
        assertTrue(ShownRoute(PROGRESS_ROUTE).newScreen)
        assertFalse(ShownRoute(PROGRESS_ROUTE).loadingOverlay)
        assertTrue(ShownRoute(ShellTabs.HISTORY).newScreen)
        assertFalse(ShownRoute(ShellTabs.HISTORY).loadingOverlay)
        assertFalse(ShownRoute(SettingsRoutes.SETTINGS).newScreen)
        assertTrue(ShownRoute(SettingsRoutes.SETTINGS).loadingOverlay)
    }

    private fun reload() {
        compose.runOnIdle { state.value = state.value.copy(loading = true, exercises = emptyList()) }
        compose.waitForIdle()
    }

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    private fun show(initial: UiState = UiState(loading = false, exercises = catalog, routines = listOf(push), history = listOf(past))) {
        state = mutableStateOf(initial)
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")

    /** The library tab, once its list is built (off the main thread). */
    private fun openLibrary() {
        tab("library").performClick()
        compose.onNodeWithTag(LibraryScreenTag).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodes(rowTag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openRow(exercise: ExerciseUi) {
        compose.onNode(hasSetTextAction()).performTextReplacement(exercise.name)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(LibraryTags.row(exercise.id)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(LibraryTags.row(exercise.id)).performClick()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
    }

    private fun openDetail(exercise: ExerciseUi) {
        openLibrary()
        openRow(exercise)
    }

    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun countText(): String =
        compose.onNodeWithTag(LibraryTags.COUNT).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private val rowTag = SemanticsMatcher("is a library row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("library-row-") == true
    }

    private fun topVisibleRow(): String =
        compose.onAllNodes(rowTag).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f }
            .minBy { it.boundsInRoot.top }
            .config[SemanticsProperties.TestTag]

    /** Records each call; adding to a routine and clearing an error change the state as the view model does. */
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Library navigation actions"
            else -> {
                calls += method.name + ":" + (args ?: emptyArray()).joinToString(",")
                val current = state.value
                when (method.name) {
                    "clearError" -> if (clearErrorClears) state.value = current.copy(error = null, errorCode = null)
                    "addRoutineExercise" -> {
                        val (routineId, exerciseId) = args!!.map { it as String }
                        val exercise = current.exercises.single { it.id == exerciseId }
                        state.value = current.copy(routines = current.routines.map { routine ->
                            if (routine.id != routineId) routine
                            else routine.copy(exercises = routine.exercises + PlannedExerciseUi("added-${routine.exercises.size}", exercise))
                        })
                    }
                }
                null
            }
        }
    } as MoosclesActions
}
