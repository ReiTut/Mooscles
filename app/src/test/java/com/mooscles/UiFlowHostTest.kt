package com.mooscles

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.ViewModelProvider
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
        runBlocking { app.preferences.autoRest(true); app.preferences.pounds(false); app.preferences.defaultRest(90) }
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val name = "Flow test ${System.currentTimeMillis()}"
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("routines-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
        compose.onNodeWithTag("routines-list").performScrollToNode(hasText("New routine"))
        compose.onNodeWithText("New routine").performClick()
        compose.onNodeWithText("Routine name").performTextInput(name)
        compose.onNode(hasText("Routine name") and hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(name)))
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        var createdRoutineId: String? = null
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(name).fetchSemanticsNodes()
                createdRoutineId = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
                    .routines.singleOrNull { it.name == name }?.id
                createdRoutineId != null
            }
        } catch (failure: Throwable) {
            val saved = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
            val path = app.getDatabasePath("mooscles.db").absolutePath
            throw AssertionError("Routine creation failed: expected=$name; loading=${vm.ui.value.loading}; " +
                "error=${vm.ui.value.error}; databasePathLength=${path.length}; database=$path; " +
                "savedRoutines=${saved.routines}; ui=${compose.onRoot().printToString()}", failure)
        }
        compose.onNodeWithText(name).performScrollTo().performClick()
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Add exercise"))
        compose.onAllNodesWithText("Add exercise").onFirst().performClick()
        compose.onNodeWithText("Find a movement").performTextInput("Barbell bench press")
        compose.onNode(hasText("Barbell bench press", substring = false) and !hasSetTextAction()).performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Barbell bench press").fetchSemanticsNodes()
            vm.ui.value.routines.singleOrNull { it.id == createdRoutineId }?.exercises?.singleOrNull()?.setTargets?.size == 2
        }
        val entry = vm.ui.value.routines.single { it.id == createdRoutineId }.exercises.single()
        val repsTag = "routine-set-${entry.setTargets.first().id}-reps"
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(repsTag))
        compose.onNodeWithTag(repsTag).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag(repsTag).performTextReplacement("8")
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-${entry.id}-menu"))
        compose.onNodeWithTag("routine-${entry.id}-menu").performClick()
        compose.onNodeWithText("Edit Rest Timer").performClick()
        compose.onNodeWithTag("routine-${entry.id}-rest").performTextReplacement("2:00")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Save targets").assertDoesNotExist()
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Start this routine"))
        compose.onNodeWithText("Start this routine").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("active-workout-list").fetchSemanticsNodes().isNotEmpty() }
        val started = runBlocking(Dispatchers.IO) { app.repository.snapshot() }
        val createdRoutine = started.routines.firstOrNull { it.id == createdRoutineId }
        assertNotNull("Created routine $createdRoutineId must remain saved; actual routines: ${started.routines}", createdRoutine)
        assertEquals("Starting a routine must preserve its name", name, createdRoutine!!.name)
        val planned = started.routineItems(createdRoutine.id).single()
        val workoutExercise = started.workoutItems(started.activeWorkout!!.id).single()
        assertEquals(8, planned.targetReps)
        assertEquals(120, planned.restSeconds)
        assertEquals(120, workoutExercise.restSeconds)
        assertEquals(listOf(8, 0), started.plannedSets(planned.id).map { it.targetReps })
        assertEquals(listOf(8, 0), started.exerciseSets(workoutExercise.id).map { it.reps })
        assertTrue(started.exerciseSets(workoutExercise.id).none { it.completed })
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Rest duration") and hasSetTextAction())
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("2:00")))
        compose.onNode(hasText("Rest duration") and hasSetTextAction()).performTextReplacement("1:45")
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("set-load-0"))
        compose.onNodeWithTag("set-reps-0").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("8")))
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
        compose.onNodeWithTag("active-rest-timer").assertDoesNotExist()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("active-rest-timer").fetchSemanticsNodes()
            app.timer.state.value.let { !it.running && !it.paused && !it.finished && it.workoutId == null }
        }
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
