package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room + DataStore + Activity flow, through the new workout and summary (Task 24). The
 * generated routine name isolates repeated runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UiFlowHostTest {
    private var activityApplication: MoosclesApplication? = null
    @get:Rule(order = 0) val environment = object : ExternalResource() {
        override fun before() {
            // AndroidX caches the first Application in a process-wide factory. Robolectric
            // creates a new Application per sandbox, so another test's factory must not leak.
            resetApplicationFactory()
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.deleteDatabase("mooscles.db")
            // Other persistence tests intentionally change preferences; establish this flow's baseline.
            runBlocking { app.preferences.autoRest(true); app.preferences.pounds(false); app.preferences.defaultRest(90) }
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun after() {
            val app = activityApplication ?: ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.appScope.cancel()
            app.database.close()
            resetApplicationFactory()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    private fun resetApplicationFactory() {
        ViewModelProvider.AndroidViewModelFactory::class.java.getDeclaredField("_instance")
            .apply { isAccessible = true }.set(null, null)
    }

    @Test
    fun routineToWorkoutRestSummaryAndHistory() {
        // The Activity owns the ViewModel under test. Robolectric's global provider can refer to
        // a different sandbox application after other Compose test classes have run.
        val app = (compose.activity.application as MoosclesApplication).also { activityApplication = it }
        val vm = ViewModelProvider(compose.activity)[AppViewModel::class.java]
        assertSame("Observe the same Application used by the Activity ViewModel", app, vm.getApplication<MoosclesApplication>())
        // The Activity has already subscribed to AppPreferences.flow. Its initial DataStore
        // migration can resume on Android's paused main looper; blocking that looper here can
        // deadlock the store's serialized edit queue. Keep driving Compose while setup completes.
        val configuredPreferences = app.appScope.async(Dispatchers.IO) {
            app.preferences.autoRest(true)
            app.preferences.pounds(false)
            app.preferences.defaultRest(90)
        }
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes()
            configuredPreferences.isCompleted
        }
        runBlocking { configuredPreferences.await() } // Already completed; propagates setup errors.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val name = "Flow test ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        // The new Train tab (plan 4B): the empty folder offers "New routine"; the name sheet creates it and the
        // editor opens on its echo.
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes()
            !vm.ui.value.loading
        }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
        compose.onAllNodesWithText("New routine").onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(name)))
        compose.onNodeWithText("Create").assertIsEnabled().performClick()
        var createdRoutineId: String? = null
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(name).fetchSemanticsNodes()
                createdRoutineId = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
                    .routines.singleOrNull { it.name == name }?.id
                createdRoutineId != null && vm.ui.value.routines.any { it.id == createdRoutineId }
            }
        } catch (failure: Throwable) {
            val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            val path = app.getDatabasePath("mooscles.db").absolutePath
            throw AssertionError("Routine creation failed: expected=$name; loading=${vm.ui.value.loading}; " +
                "error=${vm.ui.value.error}; databasePathLength=${path.length}; database=$path; " +
                "savedRoutines=${saved.routines}; ui=${compose.onRoot().printToString()}", failure)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-editor-add"))
        compose.onNodeWithTag("routine-editor-add").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Barbell bench press")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("exercise-picker-all-catalog_barbell_bench_flat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("exercise-picker-all-catalog_barbell_bench_flat").performClick()
        compose.onNodeWithTag("exercise-picker-add").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes()
            vm.ui.value.routines.singleOrNull { it.id == createdRoutineId }?.exercises?.singleOrNull()?.setTargets?.size == 2
        }
        val entry = vm.ui.value.routines.single { it.id == createdRoutineId }.exercises.single()
        // The routine's rest, from the editor's rest chip (Task 13), with a value that is not the default: 2:00 + 0:15.
        // Planned reps first (Task 14): the new set starts empty; the first tap shows the stepper, the second opens the keyboard.
        val firstSetId = entry.setTargets.first().id
        val repsTag = "routine-set-$firstSetId-reps"
        val repsField = hasSetTextAction() and hasAnyAncestor(hasTestTag(repsTag))
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(repsTag))
        compose.onNode(repsField, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNode(repsField, useUnmergedTree = true).performClick()
        compose.onNode(repsField, useUnmergedTree = true).performClick()
        compose.onNode(repsField, useUnmergedTree = true).performTextReplacement("8")
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes()
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.plannedSets(entry.id).firstOrNull()?.targetReps == 8
        }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-exercise-${entry.id}-rest"))
        compose.onNodeWithTag("routine-exercise-${entry.id}-rest").performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        compose.onNodeWithTag(StepperIncreaseTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:15")
        closeSheet()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes()
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.routineItems(createdRoutineId!!).singleOrNull()?.restSeconds == 135
        }
        compose.onNodeWithTag("routine-editor-start").performClick()
        // The workout opens at once (the shell's full-screen route), then shows the started workout.
        compose.onNodeWithTag("workout-route").assertExists()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-bar").assertDoesNotExist()
        val started = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val createdRoutine = started.routines.firstOrNull { it.id == createdRoutineId }
        assertNotNull("Created routine $createdRoutineId must remain saved; actual routines: ${started.routines}", createdRoutine)
        assertEquals("Starting a routine must preserve its name", name, createdRoutine!!.name)
        val planned = started.routineItems(createdRoutine.id).single()
        val workoutExercise = started.workoutItems(started.activeWorkout!!.id).single()
        assertEquals(8, planned.targetReps)
        assertEquals(135, planned.restSeconds)
        assertEquals(135, workoutExercise.restSeconds)
        assertEquals(createdRoutine.id, started.activeWorkout!!.sourceRoutineId)
        assertEquals(listOf(8, 0), started.plannedSets(planned.id).map { it.targetReps })
        val sets = started.exerciseSets(workoutExercise.id)
        assertEquals(listOf(8, 0), sets.map { it.reps })
        assertTrue(sets.none { it.completed })
        // This workout's next rests, from the exercise's rest sheet: 2:15 - 0:15.
        openRestSheet(workoutExercise.id)
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:15")
        compose.onNodeWithTag(StepperDecreaseTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        closeSheet()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes()
            vm.ui.value.active?.exercises?.single()?.restSeconds == 120
        }
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("workout-set-${sets[0].id}"))
        field(sets[0].id, SetRowRepsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("8")))
        field(sets[0].id, SetRowLoadTag).performTextReplacement("20")
        field(sets[0].id, SetRowRepsTag).performTextReplacement("10")
        check(sets[0].id).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes()
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.exerciseSets(workoutExercise.id)
                .first().let { it.completed && it.reps == 10 && it.weightKg == 20.0 }
        }
        compose.waitUntil(10_000) {
            // Querying Compose also drains the paused Robolectric main looper,
            // including the DataStore continuation that publishes timer state.
            compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes()
            app.timer.state.value.let { it.running && it.durationMillis == 120_000L }
        }
        assertEquals(120_000L, app.timer.state.value.durationMillis)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-timer-layer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("workout-timer-layer").assertIsDisplayed()
        val inProgressTimer = app.timer.state.value
        // 2:00 + 3 × 0:15 = 2:45 for the next rests; the live interval keeps its own.
        openRestSheet(workoutExercise.id)
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        repeat(3) { compose.onNodeWithTag(StepperIncreaseTag).performClick() }
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:45")
        closeSheet()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes()
            vm.ui.value.active?.exercises?.single()?.restSeconds == 165
        }
        assertEquals("Changing the next rest must preserve the live interval", inProgressTimer, app.timer.state.value)
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("workout-set-${sets[1].id}"))
        field(sets[1].id, SetRowRepsTag).performTextReplacement("8")
        check(sets[1].id).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes()
            app.timer.state.value.let { it.running && it.durationMillis == 165_000L && it.token != inProgressTimer.token }
        }
        compose.onNodeWithTag("workout-timer-layer").assertIsDisplayed()
        compose.onNodeWithTag("workout-header-finish").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("summary-stats").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout complete").assertIsDisplayed()
        compose.onNodeWithTag("workout-route").assertDoesNotExist()
        compose.onNodeWithTag("shell-workout-bar").assertDoesNotExist()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("summary-stats").fetchSemanticsNodes()
            app.timer.state.value.let { !it.running && !it.paused && !it.finished && it.workoutId == null }
        }
        compose.onNodeWithTag("tab-history").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("SESSION BREAKDOWN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SESSION BREAKDOWN").assertIsDisplayed()
        val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        assertEquals(165, saved.workoutExercises.single { it.id == workoutExercise.id }.restSeconds)
        assertEquals(135, saved.routineExercises.single { it.id == planned.id }.restSeconds)

    }

    private fun openRestSheet(entryId: String) {
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("exercise-rest-$entryId"))
        compose.onNodeWithTag("exercise-rest-$entryId").performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertExists()
    }
    private fun closeSheet() {
        compose.onNodeWithTag("sheet-scrim").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sheet-scrim").fetchSemanticsNodes().isEmpty() }
    }
    /** The editable field in the [cell] of the set row of [setId]. */
    private fun field(setId: String, cell: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and
        hasAnyAncestor(hasTestTag("workout-set-$setId")), useUnmergedTree = true)
    private fun check(setId: String) = compose.onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag("workout-set-$setId")))
}
