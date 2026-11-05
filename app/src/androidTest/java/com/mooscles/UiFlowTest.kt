package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.StepperValueTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room + DataStore + Activity flow, through the new workout and summary (Task 24). The
 * generated routine name isolates repeated runs. The flow expects the rest to start when a set is
 * checked, so it turns automatic rest on first: the suite installs over whatever app data the
 * device holds (5A Task 16 found it off there), and the value it found is put back at the end.
 */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun routineToWorkoutRestSummaryAndHistory() {
        val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val autoRestBefore = runBlocking(Dispatchers.IO) { app.preferences.snapshot().autoRest.also { app.preferences.autoRest(true) } }
        try {
            flow(app)
        } finally {
            runBlocking(Dispatchers.IO) { app.preferences.autoRest(autoRestBefore) }
        }
    }

    private fun flow(app: MoosclesApplication) {
        val name = "Flow test ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes().isNotEmpty() }
        // The new Train tab and routine editor (plan 4B): the name sheet creates the routine and the editor opens on its echo.
        // The list shows placeholders until the state loads; the button is there once it has.
        compose.waitUntil(30_000) {
            try {
                compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
                compose.onAllNodesWithText("New routine").fetchSemanticsNodes().isNotEmpty()
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onAllNodesWithText("New routine").onFirst().performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement(name)
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-editor-add"))
        compose.onNodeWithTag("routine-editor-add").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Barbell bench press")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("exercise-picker-all-catalog_barbell_bench_flat").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("exercise-picker-all-catalog_barbell_bench_flat").performClick()
        compose.onNodeWithTag("exercise-picker-add").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("routine-editor-list").fetchSemanticsNodes()
            val data = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            val routineId = data.routines.singleOrNull { it.name == name }?.id
            val entry = routineId?.let { data.routineItems(it).singleOrNull() }
            entry != null && data.plannedSets(entry.id).size == 2
        }
        // The routine's rest, from the editor's rest chip (Task 13), with a value that is not the default: 2:00 + 0:15.
        val editorData = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val entryId = editorData.routineItems(editorData.routines.single { it.name == name }.id).single().id
        // Planned reps first (Task 14): the new set starts empty; the first tap shows the stepper, the second opens the keyboard.
        val firstSetId = editorData.plannedSets(entryId).first().id
        val repsTag = "routine-set-$firstSetId-reps"
        val repsField = hasSetTextAction() and hasAnyAncestor(hasTestTag(repsTag))
        // The repository snapshot can be ahead of the editor's state by a frame, so wait for the row to be reachable.
        compose.waitUntil(10_000) {
            try {
                compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(repsTag))
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNode(repsField, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNode(repsField, useUnmergedTree = true).performClick()
        compose.onNode(repsField, useUnmergedTree = true).performClick()
        compose.onNode(repsField, useUnmergedTree = true).performTextReplacement("8")
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.plannedSets(entryId).firstOrNull()?.targetReps == 8
        }
        val restTag = "routine-exercise-$entryId-rest"
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(restTag))
        compose.onNodeWithTag(restTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        compose.onNodeWithTag(StepperIncreaseTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:15")
        compose.onNodeWithTag("sheet-scrim").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sheet-scrim").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) {
            val data = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            data.routines.singleOrNull { it.name == name }?.let { data.routineItems(it.id).singleOrNull()?.restSeconds } == 135
        }
        compose.onNodeWithTag("routine-editor-start").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-bar").assertDoesNotExist()
        val started = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val planned = started.routineItems(started.routines.single { it.name == name }.id).single()
        val workoutExercise = started.workoutItems(started.activeWorkout!!.id).single()
        assertEquals(8, planned.targetReps)
        assertEquals(135, planned.restSeconds)
        assertEquals(135, workoutExercise.restSeconds)
        assertEquals(planned.routineId, started.activeWorkout!!.sourceRoutineId)
        assertEquals(listOf(8, 0), started.plannedSets(planned.id).map { it.targetReps })
        val sets = started.exerciseSets(workoutExercise.id)
        assertEquals(listOf(8, 0), sets.map { it.reps })
        assertTrue(sets.none { it.completed })
        // This workout's next rests, from the exercise's rest sheet: 2:15 - 0:15.
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("exercise-rest-${workoutExercise.id}"))
        compose.onNodeWithTag("exercise-rest-${workoutExercise.id}").performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:15")
        compose.onNodeWithTag(StepperDecreaseTag).performClick()
        compose.onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("2:00")
        compose.onNodeWithTag("sheet-scrim").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("sheet-scrim").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) { app.repository.snapshot() }.workoutExercises.single { it.id == workoutExercise.id }.restSeconds == 120
        }
        compose.onNodeWithTag("workout-list").performScrollToNode(hasTestTag("workout-set-${sets[0].id}"))
        field(sets[0].id, SetRowRepsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("8")))
        field(sets[0].id, SetRowLoadTag).performTextReplacement("20")
        field(sets[0].id, SetRowRepsTag).performTextReplacement("10")
        compose.onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(hasTestTag("workout-set-${sets[0].id}"))).performClick()
        compose.waitUntil(10_000) { app.timer.state.value.let { it.running && it.durationMillis == 120_000L } }
        assertEquals(120_000L, app.timer.state.value.durationMillis)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-timer-layer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("workout-timer-layer").assertIsDisplayed()
        compose.onNodeWithTag("workout-header-finish").performClick()
        compose.onNodeWithTag("dialog-confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Workout complete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout complete").assertIsDisplayed()
        compose.onNodeWithTag("tab-history").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("workout-details").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Workout details").assertIsDisplayed()
        val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        assertEquals(120, saved.workoutExercises.single { it.id == workoutExercise.id }.restSeconds)
        assertEquals(135, saved.routineExercises.single { it.id == planned.id }.restSeconds)

    }

    /** The editable field in the [cell] of the set row of [setId]. */
    private fun field(setId: String, cell: String) = compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and
        hasAnyAncestor(hasTestTag("workout-set-$setId")), useUnmergedTree = true)
}
