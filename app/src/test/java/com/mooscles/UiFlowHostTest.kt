package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room + DataStore + Activity flow. The generated routine name isolates repeated runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class UiFlowHostTest {
    @get:Rule(order = 0) val environment = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.deleteDatabase("mooscles.db")
            // Other persistence tests intentionally change preferences; establish this flow's baseline.
            runBlocking { app.preferences.autoRest(true); app.preferences.pounds(false); app.preferences.defaultRest(90) }
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun after() {
            val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
            app.appScope.cancel()
            app.database.close()
        }
    }
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
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onAllNodesWithText("Add exercise").onFirst().performClick()
        compose.onNodeWithText("Find a movement").performTextInput("Barbell bench press")
        compose.onNode(hasText("Barbell bench press", substring = false) and !hasSetTextAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1. Barbell bench press").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Reps") and hasSetTextAction())
        compose.onNode(hasText("Reps") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNode(hasText("Reps") and hasSetTextAction()).performTextReplacement("8")
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Rest duration") and hasSetTextAction())
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).performTextReplacement("2:00")
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
        assertTrue(started.exerciseSets(workoutExercise.id).all { it.reps == 0 })
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Rest duration") and hasSetTextAction())
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("2:00")))
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).performTextReplacement("1:45")
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("set-load-0"))
        compose.onNodeWithTag("set-reps-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("set-load-0").performTextReplacement("20")
        compose.onNodeWithTag("set-reps-0").performTextReplacement("10")
        compose.onNodeWithTag("set-complete-0").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Undo set completion").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) {
            // Querying Compose also drains the paused Robolectric main looper,
            // including the DataStore continuation that publishes timer state.
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
