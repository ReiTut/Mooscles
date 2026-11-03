package com.mooscles

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
}
