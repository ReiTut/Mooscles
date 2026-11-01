package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import com.mooscles.designsystem.components.MTabBarTag
import com.mooscles.photos.NoExercisePhotos
import com.mooscles.screens.exercise.ExerciseInfoScreenTag
import com.mooscles.screens.exercise.photo.LocalExercisePhotos
import com.mooscles.screens.routines.RoutineEditorTag
import com.mooscles.screens.routines.TrainScreenTag
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
 * The new Train tab ([com.mooscles.screens.routines.TrainRoute]) and routine editor hosted by the app (plan 4B, Task 15):
 * the routes `routines` and `routine/{id}` in the real navigation graph, how they move to the detail, the workout and
 * Settings, and the global snackbar that stays off them. Fake actions change the state as the view model would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class RoutinesNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private lateinit var state: MutableState<UiState>
    private var clearErrorClears = true

    private val bench = ExerciseUi("bench", "Bench press", "CHEST", "BARBELL")
    private val row = ExerciseUi("row", "Barbell row", "BACK", "BARBELL")
    private val folders = listOf(RoutineFolderUi("default", "My routines"), RoutineFolderUi("legs", "Legs block"))
    private val many = (1..14).map { i ->
        RoutineUi("r$i", "Routine $i", listOf(PlannedExerciseUi("r$i-p1", bench, setTargets = listOf(RoutineSetUi("r$i-s1", 8), RoutineSetUi("r$i-s2", 8)))), folderId = "legs")
    }
    private val push = RoutineUi("push", "Push day", listOf(
        PlannedExerciseUi("p1", bench, setTargets = listOf(RoutineSetUi("p1-s1", 8), RoutineSetUi("p1-s2", 8))),
        PlannedExerciseUi("p2", row, setTargets = listOf(RoutineSetUi("p2-s1", 10))),
    ))
    private val past = WorkoutUi("past", "Last week", 1_000_000L, endedAt = 4_000_000L,
        exercises = listOf(WorkoutExerciseUi("past-entry", bench, listOf(SetUi("past-set", 8, 80.0, completed = true)))))

    @Test fun trainToEditorAndBackKeepsTheSelectedFolderAndScroll() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = many + push, routineFolders = folders,
            preferences = PreferencesUi(selectedRoutineFolderId = "legs")))
        compose.onNodeWithTag(TrainScreenTag).performScrollToIndex(9)
        compose.waitForIdle()
        val top = topVisibleCard()
        compose.onNodeWithTag("$top").performClick()
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
        compose.onNodeWithTag(TrainScreenTag).assertDoesNotExist()
        back()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
        assertEquals(top, topVisibleCard())
        assertEquals("legs", state.value.preferences.selectedRoutineFolderId)
    }

    @Test fun newRoutineOpensTheEditorOnItsEcho() {
        show(UiState(loading = false, exercises = listOf(bench), routines = listOf(push), routineFolders = folders))
        compose.onNodeWithTag("train-new-routine").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Fresh routine")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(RoutineEditorTag).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(calls.toString(), calls.any { it.startsWith("createRoutine:Fresh routine,") })
        compose.onNodeWithText("Fresh routine").assertIsDisplayed()
        compose.onNodeWithTag(TrainScreenTag).assertDoesNotExist()
    }

    @Test fun deletedRoutineInTheEditorReturnsToTrain() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        compose.runOnIdle { state.value = state.value.copy(routines = emptyList()) }
        compose.waitForIdle()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
        compose.onNodeWithTag(RoutineEditorTag).assertDoesNotExist()
        tab("routines").assertIsSelected()
    }

    /**
     * Smoke test of M9 (Task 13): the routine goes away in the same frame as Back and the app ends on Train. It does
     * not tell a bare pop from the real guard (the editor is not recomposed in time to call `onGone` a second time);
     * the proof of idempotence is [leavingTheEditorTwiceStillLandsOnTrain].
     */
    @Test fun backWhileTheRoutineGoesLandsOnTrain() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        compose.runOnIdle {
            state.value = state.value.copy(routines = emptyList())
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.waitForIdle()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
        assertFalse(compose.activity.isFinishing)
    }

    /** The copy's "Open" replaces the editor in view, so Back from the copy lands on Train, not on the original. */
    @Test fun copyOpenReplacesTheEditor() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        compose.onNodeWithTag("routine-editor-menu").performClick()
        compose.onNodeWithTag("routine-editor-menu-duplicate").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Open").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Open").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Push day (copy)").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
        back()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
        compose.onNodeWithTag(RoutineEditorTag).assertDoesNotExist()
    }

    /** F11 at the host: a restore of `routine/{id}` while the state loads stays on the editor, and leaves only once loaded. */
    @Test fun restoringTheEditorWhileLoadingNeitherNavigatesNorLeaves() {
        state = mutableStateOf(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
        openEditor("push")
        compose.runOnIdle { state.value = state.value.copy(loading = true, routines = emptyList()) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
        compose.onNodeWithTag(TrainScreenTag).assertDoesNotExist()
        compose.onAllNodes(androidx.compose.ui.test.hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(loading = false, routines = listOf(push)) }
        compose.waitForIdle()
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(routines = emptyList()) }
        compose.waitForIdle()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
    }

    @Test fun leavingTheEditorTwiceStillLandsOnTrain() {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = ShellTabs.TRAIN) {
                composable(ShellTabs.TRAIN) {}
                composable("routine/{id}") {}
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            nav.navigate("routine/push")
            nav.leaveRoutineEditor()
            nav.leaveRoutineEditor()
            assertEquals(ShellTabs.TRAIN, nav.currentDestination?.route)
            assertEquals(listOf(ShellTabs.TRAIN), nav.currentBackStack.value.mapNotNull { it.destination.route })
        }
    }

    @Test fun editorAboutOpensTheDetailAndBackReturnsToTheEditor() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        compose.onNodeWithContentDescription("Options for Bench press").performClick()
        compose.onNodeWithText("About the exercise").performClick()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        back()
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
    }

    @Test fun startFromTheEditorOpensTheWorkout() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        compose.onNodeWithTag("routine-editor-start").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("workout-route").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(calls.toString(), "startWorkout:push" in calls)
        compose.onNodeWithTag(MTabBarTag).assertDoesNotExist()
    }

    @Test fun globalSnackbarNeverShowsOnTrainOrEditor() {
        clearErrorClears = false
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        compose.runOnIdle { state.value = state.value.copy(error = "Raw failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw failure").assertDoesNotExist()
        openEditor("push")
        compose.onNodeWithText("Raw failure").assertDoesNotExist()
        // An old screen still has it.
        tab("history").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Raw failure").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun reselectingTrainFromTheEditorReturnsToTheList() {
        show(UiState(loading = false, exercises = listOf(bench, row), routines = listOf(push), routineFolders = folders))
        openEditor("push")
        tab("routines").assertIsSelected().performClick()
        compose.onNodeWithTag(RoutineEditorTag).assertDoesNotExist()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
    }

    @Test fun settingsShortcutOpensSettings() {
        show(UiState(loading = false, exercises = listOf(bench), routines = listOf(push), routineFolders = folders))
        compose.onNodeWithTag("train-settings").performClick()
        compose.onNodeWithTag("settings-content").assertExists()
        compose.onNodeWithTag(TrainScreenTag).assertDoesNotExist()
        tab("routines").assertIsSelected()
        back()
        compose.onNodeWithTag(TrainScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("settings-content").assertDoesNotExist()
    }

    @Test fun photosAreProvidedToTheScreens() {
        var provided: com.mooscles.screens.exercise.photo.ExercisePhotos? = null
        val app = compose.activity.application as MoosclesApplication
        compose.setContent {
            ProvideExercisePhotos(app, compose.activity) { provided = LocalExercisePhotos.current }
        }
        compose.waitForIdle()
        val photos = provided
        assertNotNull(photos)
        assertSame(app.photos, photos!!.store)
        assertTrue(photos.capture is com.mooscles.photos.AndroidPhotoCapture)
        assertNotSame(NoExercisePhotos, photos.store)
    }

    private fun show(initial: UiState) {
        state = mutableStateOf(initial)
        compose.setContent { MoosclesApp(state.value, actions) }
        compose.waitForIdle()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")

    private fun openEditor(id: String) {
        compose.onNodeWithTag("routine-$id-card").performClick()
        compose.onNodeWithTag(RoutineEditorTag).assertIsDisplayed()
    }

    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private val cardTag = SemanticsMatcher("is a routine card") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.let { it.startsWith("routine-") && it.endsWith("-card") } == true
    }

    private fun topVisibleCard(): String =
        compose.onAllNodes(cardTag).fetchSemanticsNodes().filter { it.boundsInRoot.height > 0f }
            .minBy { it.boundsInRoot.top }.config[SemanticsProperties.TestTag]

    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Routines navigation actions"
            else -> {
                calls += method.name + ":" + (args ?: emptyArray()).joinToString(",")
                val current = state.value
                when (method.name) {
                    "clearError" -> if (clearErrorClears) state.value = current.copy(error = null, errorCode = null)
                    "createRoutine" -> if (args!!.size == 3) {
                        val (name, folder, id) = args.map { it as String }
                        state.value = current.copy(routines = current.routines + RoutineUi(id, name, folderId = folder))
                    }
                    "duplicateRoutine" -> if (args!!.size == 3) {
                        val (_, copyName, newId) = args.map { it as String }
                        val source = current.routines.first()
                        state.value = current.copy(routines = current.routines + source.copy(id = newId, name = copyName,
                            exercises = source.exercises.map { it.copy(id = "$newId-${it.id}", setTargets = it.setTargets.map { t -> t.copy(id = "$newId-${t.id}") }) }))
                    }
                    "startWorkout" -> state.value = current.copy(active = WorkoutUi("active", "Push day", System.currentTimeMillis(),
                        sourceRoutineId = args!![0] as String?))
                    "selectRoutineFolder" -> state.value = current.copy(preferences = current.preferences.copy(selectedRoutineFolderId = args!![0] as String))
                }
                null
            }
        }
    } as MoosclesActions
}
