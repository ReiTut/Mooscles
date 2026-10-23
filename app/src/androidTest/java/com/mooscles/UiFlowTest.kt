package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + DataStore + Activity flow. The generated routine name isolates repeated runs. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule(order = 0) val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun routineToWorkoutRestSummaryAndHistory() {
        val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val name = "Flow test ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
        compose.onNodeWithText("New routine").performClick()
        compose.onNodeWithText("Routine name").performTextInput(name)
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performClick()
        compose.onAllNodesWithText("Add exercise").onFirst().performClick()
        compose.onNodeWithText("Find a movement").performTextInput("Barbell bench press")
        compose.onNode(hasText("Barbell bench press", substring = false) and !hasSetTextAction()).performClick()
        var entryId: String? = null
        var firstSetId: String? = null
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Barbell bench press").fetchSemanticsNodes()
            val data = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            val routineId = data.routines.singleOrNull { it.name == name }?.id
            val entry = routineId?.let { data.routineItems(it).singleOrNull() }
            val rows = entry?.let { data.plannedSets(it.id) }.orEmpty()
            entryId = entry?.id
            firstSetId = rows.firstOrNull()?.id
            rows.size == 2
        }
        val repsTag = "routine-set-$firstSetId-reps"
        // Room can finish before the saved routine reaches Compose. Wait for the
        // actual lazy-list item, scrolling on each attempt so this also works on small screens.
        compose.waitUntil(30_000) {
            try {
                compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(repsTag))
                compose.onNodeWithTag(repsTag).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        compose.onNodeWithTag(repsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag(repsTag).performTextReplacement("8")
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-$entryId-menu"))
        compose.onNodeWithTag("routine-$entryId-menu").performClick()
        compose.onNodeWithText("Edit Rest Timer").performClick()
        compose.onNodeWithTag("routine-$entryId-rest").performTextReplacement("2:00")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Save targets").assertDoesNotExist()
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Start this routine"))
        compose.onNodeWithText("Start this routine").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("active-workout-list").fetchSemanticsNodes().isNotEmpty() }
        val started = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val planned = started.routineItems(started.routines.single { it.name == name }.id).single()
        val workoutExercise = started.workoutItems(started.activeWorkout!!.id).single()
        assertEquals(8, planned.targetReps)
        assertEquals(120, planned.restSeconds)
        assertEquals(120, workoutExercise.restSeconds)
        assertEquals(planned.routineId, started.activeWorkout!!.sourceRoutineId)
        assertEquals(listOf(8, 0), started.plannedSets(planned.id).map { it.targetReps })
        assertEquals(listOf(8, 0), started.exerciseSets(workoutExercise.id).map { it.reps })
        assertTrue(started.exerciseSets(workoutExercise.id).none { it.completed })
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-${workoutExercise.id}"))
        compose.onNodeWithTag("workout-rest-edit-${workoutExercise.id}").performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("2:00")))
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).performTextReplacement("1:45")
        compose.onNodeWithTag("workout-rest-save-${workoutExercise.id}").performClick()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("set-load-0"))
        compose.onNodeWithTag("set-reps-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("8")))
        compose.onNodeWithTag("set-load-0").performTextReplacement("20")
        compose.onNodeWithTag("set-reps-0").performTextReplacement("10")
        compose.onNodeWithTag("set-complete-0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Undo set completion").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("active-workout-list").fetchSemanticsNodes()
            app.timer.state.value.let { it.running && it.durationMillis == 105_000L }
        }
        assertEquals(105_000L, app.timer.state.value.durationMillis)
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Pause rest timer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("active-rest-timer").assertIsDisplayed()
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("finish-workout"))
        compose.onNodeWithTag("finish-workout").performClick()
        compose.onNodeWithText("Finish & save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Strong work.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Strong work.").assertIsDisplayed()
        compose.onNodeWithText("History", substring = false).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("history-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("history-list").performScrollToNode(hasText(name))
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("SESSION BREAKDOWN").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("SESSION BREAKDOWN").assertIsDisplayed()
        val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        assertEquals(105, saved.workoutExercises.single { it.id == workoutExercise.id }.restSeconds)
        assertEquals(120, saved.routineExercises.single { it.id == planned.id }.restSeconds)

    }
}
