package com.mooscles

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.mooscles.data.ExerciseCatalog
import com.mooscles.screens.exercise.ExerciseInfoScreenTag
import com.mooscles.screens.progress.ExerciseProgressTag
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.records.RecordsScreenTag
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/**
 * The Records tab and the exercise progress hosted by the app (plan 5A, Task 13): the routes `records` and
 * `exercise/{id}?metric=` in the real navigation graph, how they move to each other, to the exercise detail, to the
 * workout's detail and to Settings, the global snackbar that stays off them, and the application's progress index
 * (one per process, prewarmed). The application's cache is provided as in `MainActivity`; fake actions change the
 * state as the view model would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ProgressNavigationHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private lateinit var state: MutableState<UiState>
    private lateinit var nav: NavHostController
    private var clearErrorClears = true
    private val app get() = ApplicationProvider.getApplicationContext<MoosclesApplication>()

    private val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    private val incline = catalog.single { it.id == "catalog_barbell_bench_incline" }
    /** Added weight: its first metric is Reps, so a weight record opening Load shows the route's metric. */
    private val pushUp = catalog.single { it.id == "catalog_bodyweight_push_up" }
    /** A custom exercise that was deleted: only its history's snapshot is left. */
    private val gone = ExerciseUi("custom-gone", "Old cable curl", "Biceps", "Cable", incline.convention, custom = true)
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    /** [SESSIONS] workouts of the incline press, each 2.5 kg heavier (a beaten weight record), the last also a reps record. */
    private val history: List<WorkoutUi> = (0 until SESSIONS).map { i ->
        val id = "w$i"
        val started = now - (SESSIONS - i) * 2 * day
        val kg = 60.0 + 2.5 * i
        val weight = RecordUi(incline, kg, 5, previousKg = if (i == 0) null else kg - 2.5, workoutId = id, date = started)
        val reps = if (i == SESSIONS - 1) listOf(RecordUi(incline, 60.0, 12, workoutId = id, date = started, kind = RecordKind.REPS, previousReps = 10)) else emptyList()
        val goneEntry = if (i == 3) listOf(WorkoutExerciseUi("$id-gone", gone, listOf(SetUi("$id-gone-set", 10, 20.0, completed = true)))) else emptyList()
        // Push-ups with 10 kg added, then 12.5 kg: a first record, then a beaten one.
        val pushUpKg = mapOf(4 to 10.0, 6 to 12.5)[i]
        val pushUpEntry = pushUpKg?.let { listOf(WorkoutExerciseUi("$id-push", pushUp, listOf(SetUi("$id-push-set", 8, it, completed = true)))) }.orEmpty()
        val pushUpRecord = pushUpKg?.let { listOf(RecordUi(pushUp, it, 8, previousKg = if (i == 4) null else 10.0, workoutId = id, date = started)) }.orEmpty()
        WorkoutUi(id, "Session $i", started, endedAt = started + 3_600_000L,
            exercises = listOf(WorkoutExerciseUi("$id-incline", incline, listOf(
                SetUi("$id-a", 5, kg, completed = true),
                SetUi("$id-b", if (reps.isEmpty()) 8 else 12, 60.0, completed = true),
            ))) + pushUpEntry + goneEntry,
            records = listOf(weight) + reps + pushUpRecord)
    }.reversed()
    private val last = "w${SESSIONS - 1}"
    /** The records that still hold, as `UiState.records` has them (no previous value). */
    private val current = listOf(
        RecordUi(incline, 60.0 + 2.5 * (SESSIONS - 1), 5, workoutId = last, date = history.first().startedAt),
        RecordUi(incline, 60.0, 12, workoutId = last, date = history.first().startedAt, kind = RecordKind.REPS),
        RecordUi(pushUp, 12.5, 8, workoutId = "w6", date = history.single { it.id == "w6" }.startedAt),
    )

    private fun rowTag(workoutId: String, kind: RecordKind, kg: Double, exercise: ExerciseUi = incline) =
        "record-$workoutId|${exercise.id}|${exercise.convention}|$kind|${kg.toBits()}|false"
    private val lastWeightRow = rowTag(last, RecordKind.WEIGHT, 60.0 + 2.5 * (SESSIONS - 1))
    private val lastRepsRow = rowTag(last, RecordKind.REPS, 60.0)
    private val pushUpWeightRow = rowTag("w6", RecordKind.WEIGHT, 12.5, pushUp)

    @Test fun recordsTabShowsTheTimeline() {
        show()
        openRecords()
        compose.onNodeWithTag(RecordsScreenTag).assertIsDisplayed()
        compose.onNodeWithTag(lastWeightRow).assertIsDisplayed()
        compose.onNodeWithTag(lastRepsRow).assertIsDisplayed()
        tab("records").assertIsSelected()
        // The new screen brings its own top bar: neither the old one nor the old screen's texts.
        compose.onNodeWithText("mooscles").assertDoesNotExist()
        compose.onNodeWithText("Personal records").assertDoesNotExist()
        compose.onNodeWithText("Find a movement’s record").assertDoesNotExist()
    }

    @Test fun recordRowOpensProgressWithItsMetric() {
        show()
        openRecords()
        compose.onNodeWithTag(lastWeightRow).performClick()
        assertRouteMetric("load")
        assertMetricSelected("load")
        tab("records").assertIsSelected()
        back()
        compose.onNodeWithTag(lastRepsRow).performClick()
        assertRouteMetric("reps")
        // Total load has no reps chart: the progress opens the convention's first metric (spec D2).
        assertMetricSelected("load")
        back()
        compose.onNodeWithTag(RecordsScreenTag).performScrollToNode(hasTestTag(pushUpWeightRow))
        compose.onNodeWithTag(pushUpWeightRow).performClick()
        assertRouteMetric("load")
        // Added weight opens on Reps by default: Load is the record's.
        assertMetricSelected("load")
        compose.onNodeWithTag("progress-metric-reps").assertIsNotSelected()
        compose.onNodeWithTag("progress-title").assertTextEquals(pushUp.name)
    }

    @Test fun recordsToProgressAndBackKeepsSearchChipsAndScroll() {
        show()
        openRecords()
        // "First records" adds the first session's record to the 29 beaten ones.
        compose.onNodeWithTag("records-first").performClick()
        compose.waitForIdle()
        val (top, topBounds) = scrollAndOpenAndReturn()
        compose.onNodeWithTag("records-first").assertIsOn()
        assertEquals(top, topVisibleRow())
        assertEquals(topBounds, compose.onNodeWithTag(top).fetchSemanticsNode().boundsInRoot)

        // With a search typed in, the text and the chips come back too, and so does the place in the list. The field
        // still has focus when the row is tapped; leaving then brings the row under the month's sticky header fully
        // into view before the scroll is saved, so the list may come back up to one row higher (task 13 report).
        compose.onNode(hasSetTextAction()).performTextReplacement(incline.name)
        compose.waitForIdle()
        val (searchedTop, _) = scrollAndOpenAndReturn()
        assertEquals(incline.name, compose.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithTag("records-first").assertIsOn()
        compose.onNodeWithTag("records-current-only").assertIsOff()
        compose.onNodeWithTag(lastWeightRow).assertDoesNotExist()
        // Newer sessions are higher in the list.
        val shift = sessionOf(topVisibleRow()) - sessionOf(searchedTop)
        assertTrue("The list came back $shift rows away from $searchedTop", shift in 0..1)
    }

    /**
     * Scrolls the timeline into its middle, opens the progress from a row a person can tap (whole, under the month's
     * sticky header) and comes back; returns the top row and its bounds from before.
     */
    private fun scrollAndOpenAndReturn(): Pair<String, androidx.compose.ui.geometry.Rect> {
        compose.waitUntil(10_000) { rows().isNotEmpty() }
        compose.onNodeWithTag(RecordsScreenTag).performScrollToIndex(12)
        compose.waitForIdle()
        val top = topVisibleRow()
        val topBounds = compose.onNodeWithTag(top).fetchSemanticsNode().boundsInRoot
        val header = compose.onAllNodes(monthMatcher).fetchSemanticsNodes().minBy { it.boundsInRoot.top }.boundsInRoot.bottom
        val tapped = rows().filter { it.boundsInRoot.top >= header }.minBy { it.boundsInRoot.top }.config[SemanticsProperties.TestTag]
        compose.onNodeWithTag(tapped).performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.onNodeWithTag(RecordsScreenTag).assertDoesNotExist()
        back()
        compose.waitUntil(10_000) { rows().isNotEmpty() }
        compose.waitForIdle()
        return top to topBounds
    }

    /** The session number of a row's workout (`record-w12|…` is 12). */
    private fun sessionOf(rowTag: String) = rowTag.removePrefix("record-w").substringBefore('|').toInt()

    @Test fun openWorkoutActionOpensTheDetail() {
        show()
        openRecords()
        val open = compose.onNodeWithTag(lastWeightRow).fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .single { it.label == "Open workout" }
        compose.runOnIdle { open.action() }
        compose.waitForIdle()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        compose.onNodeWithText("Session ${SESSIONS - 1}").assertExists()
        compose.onNodeWithTag(ExerciseProgressTag).assertDoesNotExist()
        back()
        compose.onNodeWithTag(RecordsScreenTag).assertIsDisplayed()
    }

    @Test fun sessionOpensTheWorkoutDetail() {
        show()
        openRecords()
        compose.onNodeWithTag(lastWeightRow).performClick()
        assertMetricSelected("load")
        val session = "progress-session-$last"
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag(ExerciseProgressTag).performScrollToNode(hasTestTag(session)) }.isSuccess
        }
        compose.onNodeWithTag(session).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("workout-details").assertIsDisplayed()
        compose.onNodeWithText("Session ${SESSIONS - 1}").assertExists()
        back()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
    }

    /** Opened from Records, the progress has no detail under it: "About the exercise" opens one over it. */
    @Test fun aboutOpensTheDetailWhenRecordsOpenedTheProgress() {
        show()
        openRecords()
        compose.onNodeWithTag(lastWeightRow).performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.onNodeWithTag("progress-menu").performClick()
        compose.onNodeWithTag("progress-menu-about").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("exercise-info-title").assertTextEquals(incline.name)
        back()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.onNodeWithTag(ExerciseInfoScreenTag).assertDoesNotExist()
        back()
        compose.onNodeWithTag(RecordsScreenTag).assertIsDisplayed()
    }

    @Test fun deletedExerciseWithHistoryOpensWithTheSnapshotName() {
        show()
        openRoute("exercise/${gone.id}")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress-title").assertTextEquals(gone.name)
        compose.onNodeWithTag("progress-missing").assertDoesNotExist()
        compose.onNodeWithText("Exercise unavailable").assertDoesNotExist()
    }

    @Test fun missingExerciseWithoutHistoryShowsNotFound() {
        show()
        openRoute("exercise/never-was")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-missing").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress-missing").assertIsDisplayed()
        // The old screen's placeholder is gone with it.
        compose.onNodeWithText("Exercise unavailable").assertDoesNotExist()
        compose.onNodeWithTag("progress-title").assertDoesNotExist()
    }

    @Test fun globalSnackbarNeverShowsOnRecordsOrProgress() {
        // The error stays in the state after clearError here, so a snackbar would stay on screen too.
        clearErrorClears = false
        show()
        openRecords()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw records failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw records failure").assertDoesNotExist()
        // The tab shows its own words for the code and hands the error back.
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle { assertEquals(1, calls.count { it == "clearError:" }) }

        compose.runOnIdle { state.value = state.value.copy(error = null, errorCode = null) }
        compose.onNodeWithTag(lastWeightRow).performClick()
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(error = "Raw progress failure", errorCode = UiError.SAVE_FAILED) }
        compose.waitForIdle()
        compose.onNodeWithText("Raw progress failure").assertDoesNotExist()
        compose.onNodeWithText("Couldn't save the last change", substring = true).assertExists()
        compose.runOnIdle { assertEquals(2, calls.count { it == "clearError:" }) }
    }

    @Test fun settingsShortcutOpensSettings() {
        show()
        openRecords()
        compose.onNodeWithTag("records-settings").performClick()
        compose.onNodeWithTag("settings-content").assertExists()
        compose.onNodeWithTag(RecordsScreenTag).assertDoesNotExist()
        tab("records").assertIsSelected()
        back()
        compose.onNodeWithTag(RecordsScreenTag).assertIsDisplayed()
        compose.onNodeWithTag("settings-content").assertDoesNotExist()
    }

    /**
     * Plan 5A decision 9: the screens get the application's one cache, and a new history is built in it, after the
     * prewarm's delay, without any screen asking (Train is in view).
     */
    @Test fun progressIndexIsTheApplicationsAndIsPrewarmed() {
        var provided: ProgressIndexCache? = null
        state = mutableStateOf(UiState(loading = false, exercises = catalog, history = history, records = current))
        compose.setContent {
            ProvideProgressIndex(app) {
                provided = LocalProgressIndexCache.current
                MoosclesApp(state.value, actions)
            }
        }
        compose.waitForIdle()
        assertSame(app.progressIndex, provided)

        // A finished workout: a new history and new records, as the presentation makes them.
        val next = listOf(history.first().copy(id = "w-next", name = "Next session", startedAt = now)) + history
        val nextRecords = current.toList()
        compose.runOnIdle { state.value = state.value.copy(history = next, records = nextRecords) }
        compose.waitForIdle()
        assertNull("Nothing builds before the prewarm's delay", app.progressIndex.peek(next, nextRecords))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PREWARM_DELAY_MILLIS))
        compose.waitUntil(10_000) { app.progressIndex.peek(next, nextRecords) != null }
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
    }

    private fun show() {
        state = mutableStateOf(UiState(loading = false, exercises = catalog, history = history, records = current))
        compose.setContent {
            ProvideProgressIndex(app) {
                MoosclesApp(state.value, actions, navController = rememberNavController().also { nav = it })
            }
        }
        compose.waitForIdle()
    }

    private fun tab(route: String) = compose.onNodeWithTag("tab-$route")

    /** The Records tab, once its timeline is built (off the main thread). */
    private fun openRecords() {
        tab("records").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(lastWeightRow).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openRoute(route: String) {
        compose.runOnIdle { nav.navigate(route) }
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
    }

    private fun assertRouteMetric(route: String) {
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.runOnIdle { assertEquals(route, nav.currentBackStackEntry?.arguments?.getString("metric")) }
    }

    /** The metric chips come with the chart, built off the main thread. */
    private fun assertMetricSelected(route: String) {
        compose.onNodeWithTag(ExerciseProgressTag).assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("progress-metric-$route").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("progress-metric-$route").assertIsSelected()
    }

    private fun back() {
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private val rowMatcher = SemanticsMatcher("is a record row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("record-") == true
    }

    private val monthMatcher = SemanticsMatcher("is a month header") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("records-month-") == true
    }

    private fun rows() = compose.onAllNodes(rowMatcher).fetchSemanticsNodes()

    private fun topVisibleRow(): String =
        rows().filter { it.boundsInRoot.height > 0f }.minBy { it.boundsInRoot.top }.config[SemanticsProperties.TestTag]

    /** Records each call; clearing an error changes the state as the view model does. */
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Progress navigation actions"
            else -> {
                calls += method.name + ":" + (args ?: emptyArray()).joinToString(",")
                if (method.name == "clearError" && clearErrorClears) state.value = state.value.copy(error = null, errorCode = null)
                null
            }
        }
    } as MoosclesActions

    private companion object {
        const val SESSIONS = 30
        /** `ProgressIndexCache`'s default prewarm delay, which the application's cache keeps. */
        const val PREWARM_DELAY_MILLIS = 500L
    }
}
