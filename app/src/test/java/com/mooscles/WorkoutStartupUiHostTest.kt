package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalTestApi::class)
class WorkoutStartupUiHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val exercise = ExerciseUi("exercise", "Test bench", "Chest", "Barbell", "Total load")
    private val initial = WorkoutUi("session", "Startup test", 1000,
        exercises = List(3) { entry -> WorkoutExerciseUi("entry-$entry", exercise,
            sets = List(2) { row -> SetUi("set-$entry-$row", 8, 20.0) }) })
    private val state = mutableStateOf(UiState(loading = false, active = initial))
    private val calls = mutableListOf<String>()
    private val actions = actions { name, args ->
        calls += name
        if (name == "updateSet") {
            val updated = SetUi(args[0] as String, args[1] as Int, args[2] as Double,
                args[3] as Boolean, args[4] as Boolean, args[5] as Boolean)
            state.value = state.value.copy(active = state.value.active!!.copy(exercises =
                state.value.active!!.exercises.map { entry -> entry.copy(sets = entry.sets.map {
                    if (it.id == updated.id) updated else it
                }) }))
        }
    }

    @Test fun startingMessageReplacesEmptyPromptAndEndsAsSoonAsEssentialsExist() {
        state.value = state.value.copy(active = null, startingWorkout = true)
        show()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        compose.onNodeWithText("Starting workout…").assertExists()
        compose.onNodeWithText("Loading your routine in the background.").assertExists()
        compose.onNodeWithText("A fresh start").assertDoesNotExist()
        compose.onNodeWithTag("active-workout-list").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(active = initial, secondaryLoading = true) }
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
        list().performScrollToNode(hasTestTag("workout-set-set-0-0"))
        field("set-0-0", "set-load-0").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle { assertTrue(calls.isEmpty()) }
    }

    @Test fun disabledAnimationsKeepTheSameAccessibleMessageWithStaticProgress() {
        compose.setContent { MaterialTheme { StartingWorkoutState(animationsEnabled = false) } }
        compose.onNodeWithTag("starting-workout-static-progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("starting-workout-progress", useUnmergedTree = true).assertDoesNotExist()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .assertTextContains("Starting workout…")
            .assertTextContains("Loading your routine in the background.")
    }

    @Test fun newAndInvalidatedRecordsBelowCurrentInputsPreserveDraftFocusSelectionAndGeometry() {
        show()
        list().performScrollToNode(hasTestTag("workout-set-set-0-0"))
        val load = field("set-0-0", "set-load-0")
        load.performTextReplacement("31.1250")
        field("set-0-0", "set-reps-0").performTextReplacement("6")
        field("set-0-0", "set-complete-0").performClick()
        load.performClick().performTextInputSelection(TextRange(2, 5))
        val before = load.getUnclippedBoundsInRoot()
        val values = state.value.active!!.exercises.flatMap { it.sets }
        enrich(0)
        load.assertIsDisplayed().assertIsFocused().assert(editable("31.1250"))
        assertEquals(TextRange(2, 5), load.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertEquals(before, load.getUnclippedBoundsInRoot())
        compose.runOnIdle {
            assertEquals(values, state.value.active!!.exercises.flatMap { it.sets })
            assertEquals(31.125, state.value.active!!.exercises.first().sets.first().weightKg, 0.0)
            val workout = state.value.active!!
            state.value = state.value.copy(active = workout.copy(exercises = workout.exercises.map {
                it.copy(previous = null, records = emptyList())
            }))
        }
        load.assertIsFocused().assert(editable("31.1250"))
        assertEquals(before, load.getUnclippedBoundsInRoot())
        compose.runOnIdle { assertFalse(calls.any { it.contains("Timer") || it == "startWorkout" }) }
    }

    @Test fun earlierExerciseEnrichmentKeepsALaterFocusedInputAtTheSamePosition() {
        show()
        // The preceding exercise's metadata is deliberately the first visible
        // item; expanding it must not move the later focused input out of view.
        list().performScrollToIndex(7)
        val load = field("set-1-0", "set-load-0")
        load.assertIsDisplayed().performClick().performTextReplacement("31.1250")
        load.performTextInputSelection(TextRange(2, 5))
        val before = load.getUnclippedBoundsInRoot()
        enrich(0)
        load.assertIsDisplayed().assertIsFocused().assert(editable("31.1250"))
        assertEquals(TextRange(2, 5), load.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        assertEquals("Earlier secondary details must not move the later focused row", before, load.getUnclippedBoundsInRoot())
    }

    @Test fun completePreviousDescriptionAndRecordBadgesRemainAvailableAfterTheSets() {
        show()
        enrich(0)
        list().performScrollToNode(hasTestTag("exercise-metadata-entry-0"))
        compose.onNodeWithText("Previous: $longHistory", substring = false).assertExists()
        compose.onNodeWithText("Weight PR", substring = true).assertExists()
        compose.onNodeWithText("Reps PR", substring = true).assertExists()
        compose.runOnIdle { assertEquals(initial.exercises.flatMap { it.sets }, state.value.active!!.exercises.flatMap { it.sets }) }
    }

    @Test fun repeatedStartNavigationLeavesOnlyOneWorkoutDestination() {
        val routine = RoutineUi("routine", "Routine")
        state.value = state.value.copy(active = null, routines = listOf(routine))
        val startActions = actions { name, _ ->
            if (name == "startWorkout") state.value = state.value.copy(startingWorkout = true)
        }
        compose.setContent { MoosclesApp(state.value, startActions) }
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag("routine-routine-start"))
        val click = compose.onNodeWithTag("routine-routine-start").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { click(); click() }
        compose.onNodeWithTag("tab-workout").assertIsSelected()
        compose.onNodeWithTag("starting-workout").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("tab-routines").assertIsSelected()
        compose.onNodeWithTag("routines-list").assertIsDisplayed()
        compose.onNodeWithTag("starting-workout").assertDoesNotExist()
    }

    private val longHistory = List(12) { "24 kg × ${8 + it % 3}" }.joinToString(" · ")
    private fun enrich(index: Int) = compose.runOnIdle {
        val workout = state.value.active!!
        state.value = state.value.copy(active = workout.copy(exercises = workout.exercises.mapIndexed { current, entry ->
            if (current != index) entry else entry.copy(previous = longHistory, records = listOf(
                RecordUi(entry.exercise, 31.125, 6, previousKg = 24.0, workoutId = workout.id),
                RecordUi(entry.exercise, 24.0, 12, workoutId = workout.id, kind = RecordKind.REPS, previousReps = 10)))
        }))
    }
    private fun show() {
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 480.dp)) {
            ActiveWorkoutScreen(state.value, actions, {}, {}, {})
        } } }
    }
    private fun list() = compose.onNodeWithTag("active-workout-list")
    private fun field(set: String, tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("workout-set-$set")))
    private fun editable(value: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
    private fun actions(callback: (String, Array<out Any?>) -> Unit): MoosclesActions =
        Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Startup UI actions"
                else -> { callback(method.name, args ?: emptyArray()); null }
            }
        } as MoosclesActions
}
