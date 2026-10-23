package com.mooscles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class InlineRestEditorHostTest {
    @get:Rule val compose = createComposeRule()
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Inline rest test actions"
            else -> { calls += method.name to args.orEmpty().toList(); null }
        }
    } as MoosclesActions
    private val bench = WorkoutExerciseUi("bench", ExerciseUi("bench-exercise", "Bench press", "CHEST", "BARBELL"),
        sets = listOf(SetUi("bench-set", 8, 20.0)), restSeconds = 165)

    @Test fun idleInlineLabelEditsOnlyItsExerciseAndCancelLeavesTheSavedValue() {
        val second = bench.copy(id = "other", restSeconds = 75, sets = listOf(SetUi("other-set", 8, 15.0)))
        compose.setContent { MaterialTheme {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                WorkoutExerciseCard(bench, 0, 2, false, actions, true, {}, {})
                WorkoutExerciseCard(second, 1, 2, false, actions, true, {}, {})
            }
        } }
        compose.onNodeWithTag("workout-rest-edit-bench").performScrollTo()
            .assertContentDescriptionEquals("Edit rest duration for Bench press").performClick()
        compose.onNodeWithTag("workout-rest-bench").assertIsFocused().assertTextContains("2:45")
            .performTextReplacement("3")
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("3:00").performTextInput("0")
        compose.onNodeWithTag("workout-rest-bench").performTextInput("5")
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("3:05")
        compose.runOnIdle { assertTrue("Editing is a draft until Save", calls.isEmpty()) }
        compose.onNodeWithTag("workout-rest-cancel-bench").performClick()
        compose.onNodeWithTag("workout-rest-edit-bench").assertTextContains("Rest 2:45 min")
        compose.runOnIdle { assertTrue(calls.isEmpty()) }

        compose.onNodeWithTag("workout-rest-edit-bench").performClick()
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("2:45").performTextReplacement("2:15")
        compose.onNodeWithTag("workout-rest-save-bench").performClick()
        // The label reflects the saved draft immediately, even before the repository's echo.
        compose.onNodeWithTag("workout-rest-edit-bench").assertTextContains("Rest 2:15 min")
        compose.onNodeWithTag("workout-rest-edit-other").performScrollTo().assertTextContains("Rest 1:15 min")
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 135)), calls) }
    }

    @Test fun runningCountdownIsIndependentFromOpeningCancellingAndSavingTheRestEditor() {
        val timer = TimerUi("Pec deck fly", 69, 120, running = true, progress = 0.575f)
        compose.setContent { MaterialTheme {
            Column {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    WorkoutExerciseCard(bench.copy(restSeconds = 120), 0, 1, false, actions, true, {}, {})
                }
                TimerPanel(timer, actions, 120, compact = true)
            }
        } }
        compose.onNodeWithTag("workout-rest-edit-bench").performScrollTo().performClick()
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("2:00")
        compose.onNodeWithTag("workout-rest-cancel-bench").performClick()
        assertProgress(0.575f)
        compose.onNodeWithText("1:09 min").assertIsDisplayed()
        compose.onNodeWithTag("workout-rest-edit-bench").performClick()
        compose.onNodeWithTag("workout-rest-bench").performTextReplacement("1:45")
        compose.onNodeWithTag("workout-rest-save-bench").performClick()
        compose.onNodeWithTag("workout-rest-edit-bench").assertTextContains("Rest 1:45 min")
        assertProgress(0.575f)
        compose.onNodeWithText("1:09 min").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 105)), calls) }

        compose.onNodeWithText("−0:15").performClick()
        compose.onNodeWithContentDescription("Pause rest timer").performClick()
        compose.onNodeWithText("+0:15").performClick()
        compose.onNodeWithContentDescription("Reset rest timer").performClick()
        compose.onNodeWithContentDescription("Skip rest timer").performClick()
        compose.runOnIdle {
            assertEquals(listOf("adjustTimer" to listOf(-15), "pauseTimer" to emptyList(),
                "adjustTimer" to listOf(15), "resetTimer" to emptyList(), "skipTimer" to emptyList()), calls.drop(1))
        }
    }

    @Test fun inlineDurationEditorEnforcesTheFiveMinuteMaximum() {
        compose.setContent { MaterialTheme { WorkoutExerciseCard(bench, 0, 1, false, actions, true, {}, {}) } }
        compose.onNodeWithTag("workout-rest-edit-bench").performClick()
        compose.onNodeWithTag("workout-rest-bench").performTextReplacement("5:00")
        compose.onNodeWithTag("workout-rest-bench").performTextReplacement("5:01")
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("5:00")
        compose.onNodeWithTag("workout-rest-bench").performTextReplacement("6:00")
        compose.onNodeWithTag("workout-rest-bench").assertTextContains("5:00")
        compose.onNodeWithText("Use 0:00 to 5:00, with seconds from 00 to 59.").assertExists()
        compose.onNodeWithTag("workout-rest-save-bench").performClick()
        compose.onNodeWithTag("workout-rest-edit-bench").assertTextContains("Rest 5:00 min")
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 300)), calls) }
    }

    @Test fun activeWorkoutDialogSurvivesLazyCardDisposalAndClosesIfItsExerciseIsRemoved() {
        val exercises = listOf(bench) + List(10) { index -> bench.copy(id = "other-$index",
            exercise = bench.exercise.copy(id = "exercise-$index", name = "Other movement $index"),
            sets = listOf(SetUi("other-set-$index", 8, 15.0)), restSeconds = 75) }
        val state = mutableStateOf(UiState(loading = false,
            active = WorkoutUi("active", "Long session", System.currentTimeMillis(), exercises = exercises),
            timer = TimerUi("Live rest", 69, 120, running = true, progress = 0.575f)))
        val constrained = mutableStateOf(false)
        compose.setContent { MaterialTheme {
            Column(Modifier.height(if (constrained.value) 350.dp else 600.dp)) {
                Box(Modifier.weight(1f)) { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) }
                TimerPanel(state.value.timer, actions, 120, compact = true)
            }
        } }
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-bench"))
        compose.onNodeWithTag("workout-rest-edit-bench").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("workout-rest-bench").assertIsFocused().performTextReplacement("2:30")
        compose.runOnIdle { constrained.value = true }
        // Programmatically move the underlying lazy list to dispose its first
        // card, as can also happen when an IME resize changes its viewport.
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasText("Discard workout"))
        compose.onNodeWithTag("workout-rest-edit-bench").assertDoesNotExist()
        compose.onNodeWithTag("workout-rest-bench").assertIsFocused().assertTextContains("2:30")
        compose.onNodeWithTag("workout-rest-save-bench").performClick()
        assertProgress(0.575f)
        compose.onNodeWithTag("active-workout-list").performScrollToNode(hasTestTag("workout-rest-edit-bench"))
        compose.onNodeWithTag("workout-rest-edit-bench").performScrollTo().assertTextContains("Rest 2:30 min").performClick()
        compose.onNodeWithTag("workout-rest-bench").assertIsFocused().assertTextContains("2:30")
        compose.runOnIdle { state.value = state.value.copy(active = state.value.active!!.copy(exercises = exercises.drop(1))) }
        compose.onNodeWithTag("workout-rest-bench").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 150)), calls) }
    }

    @Test fun progressSemanticsReflectRunningPausedAdjustedResetAndCompletedSnapshots() {
        val timer = mutableStateOf(TimerUi("Bench", 90, 120, running = true, progress = 0.75f))
        compose.setContent { MaterialTheme { TimerPanel(timer.value, actions, 120, compact = true) } }
        assertProgress(0.75f)
        compose.runOnIdle { timer.value = timer.value.copy(running = false, paused = true) }
        assertProgress(0.75f)
        compose.onNodeWithTag("rest-timer-progress").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "75% remaining, paused"))
        compose.runOnIdle { timer.value = timer.value.copy(remainingSeconds = 60, progress = 0.5f) }
        assertProgress(0.5f)
        compose.runOnIdle { timer.value = timer.value.copy(remainingSeconds = 120, progress = 1f) }
        assertProgress(1f)
        compose.runOnIdle { timer.value = timer.value.copy(remainingSeconds = 0, paused = false, finished = true, progress = 0f) }
        assertProgress(0f)
        compose.runOnIdle { timer.value = timer.value.copy(progress = 1.2f) }
        assertProgress(1f)
        compose.runOnIdle { timer.value = timer.value.copy(progress = -0.2f) }
        assertProgress(0f)
        compose.runOnIdle { timer.value = timer.value.copy(progress = Float.NaN) }
        assertProgress(0f)
    }

    private fun assertProgress(value: Float) {
        compose.onNodeWithTag("rest-timer-progress").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(value, 0f..1f)))
    }
}
