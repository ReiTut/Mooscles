package com.mooscles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.screens.workoutdetail.WorkoutDetailRoute
import com.mooscles.screens.workoutdetail.WorkoutDetailScreenState
import com.mooscles.screens.workoutdetail.WorkoutDetailTags
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** The legacy detail-rest coverage now exercises the shared detail's precise, cancellable editor. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class InlineRestEditorHostTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreLocale() { LocalAppLocale.restore() }
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val bench = WorkoutExerciseUi("bench", ExerciseUi("bench-exercise", "Bench press", "Chest", "Barbell"),
        sets = listOf(SetUi("bench-set", 8, 20.0)), restSeconds = 165)
    private val state = mutableStateOf(UiState(loading = false))
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Saved rest test actions"
            else -> {
                calls += method.name to args.orEmpty().toList()
                if (method.name == "updateWorkoutExerciseRest") {
                    val id = args!![0] as String
                    val seconds = args[1] as Int
                    state.value = state.value.copy(history = state.value.history.map { workout ->
                        workout.copy(exercises = workout.exercises.map { if (it.id == id) it.copy(restSeconds = seconds) else it })
                    })
                }
                null
            }
        }
    } as MoosclesActions

    @Test fun idleInlineLabelEditsOnlyItsExerciseAndCancelLeavesTheSavedValue() {
        val second = bench.copy(id = "other", restSeconds = 75, sets = listOf(SetUi("other-set", 8, 15.0)))
        show(listOf(bench, second))
        openRest("bench")
        restInput().assertTextContains("2:45").performTextReplacement("3")
        restInput().assertTextContains("3:00").performTextInput("0")
        restInput().performTextInput("5")
        restInput().assertTextContains("3:05")
        compose.runOnIdle { assertTrue("Editing is a draft until Save", calls.isEmpty()) }
        compose.onNodeWithTag("detail-rest-cancel").performClick()
        compose.onNodeWithTag(WorkoutDetailTags.rest("bench")).assertTextEquals("Rest 2:45 min")
        compose.runOnIdle { assertTrue(calls.isEmpty()) }

        openRest("bench")
        restInput().assertTextContains("2:45").performTextReplacement("3:05")
        compose.onNodeWithTag("detail-rest-save").performClick()
        compose.onNodeWithTag(WorkoutDetailTags.rest("bench")).assertTextEquals("Rest 3:05 min")
        compose.onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.rest("other")))
        compose.onNodeWithTag(WorkoutDetailTags.rest("other")).assertTextEquals("Rest 1:15 min")
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 185)), calls) }
    }

    @Test fun inlineDurationEditorEnforcesTheFiveMinuteMaximum() {
        show(listOf(bench))
        openRest("bench")
        restInput().performTextReplacement("5:00")
        restInput().performTextReplacement("5:01")
        restInput().assertTextContains("5:00")
        restInput().performTextReplacement("6:00")
        restInput().assertTextContains("5:00")
        compose.onNodeWithTag("detail-rest-save").performClick()
        compose.onNodeWithTag(WorkoutDetailTags.rest("bench")).assertTextEquals("Rest 5:00 min")
        compose.runOnIdle { assertEquals(listOf("updateWorkoutExerciseRest" to listOf("bench", 300)), calls) }
    }

    private fun show(entries: List<WorkoutExerciseUi>) {
        state.value = UiState(loading = false, history = listOf(WorkoutUi("saved", "Upper", 1_000, 61_000, entries)))
        compose.setContent { MoosclesDesignTheme(dark = false, reducedMotion = true) {
            ProvideAppLocale("en") { Box(Modifier.size(430.dp, 900.dp)) {
                WorkoutDetailRoute("saved", state.value, actions, {}, {}, {}, {}, screen = WorkoutDetailScreenState(editing = true))
            } }
        } }
    }

    private fun openRest(id: String) {
        compose.onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.rest(id)))
        compose.onNodeWithTag(WorkoutDetailTags.rest(id)).performClick()
    }

    private fun restInput() = compose.onNode(hasSetTextAction() and
        (hasTestTag("detail-rest-input") or hasAnyAncestor(hasTestTag("detail-rest-input"))), useUnmergedTree = true)
}
