package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RoutineEditorHostTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun editsSaveImmediatelyWhileBlankRepsAndInvalidSiblingRemainEditable() {
        val plan = PlannedExerciseUi("entry", ExerciseUi("bench", "Bench press", "CHEST", "BARBELL"), sets = 3, reps = 0)
        val routine = mutableStateOf(RoutineUi("routine", "Test routine", listOf(plan)))
        val saves = mutableListOf<SavedPlan>()
        val events = mutableListOf<String>()
        val actions = actions { name, args ->
            when (name) {
                "updateRoutineExercise" -> {
                    saves += SavedPlan(args[1] as Int, args[2] as Int, args[3] as Double?, args[4] as Int)
                    events += "save"
                }
                "startWorkout" -> events += "start"
            }
        }
        compose.setContent {
            MaterialTheme { RoutineEditor(routine.value, UiState(loading = false), actions, {}) }
        }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-entry-reps"))
        compose.onNodeWithTag("routine-entry-reps").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("Save targets").assertDoesNotExist()

        // An empty required set count is still editable and cannot leak into saves.
        compose.onNodeWithTag("routine-entry-sets").performTextReplacement("")
        compose.onNodeWithTag("routine-entry-reps").performTextReplacement("12")
        compose.runOnIdle { assertEquals(SavedPlan(3, 12, null, 90), saves.last()) }
        // A persisted emission must not reset the incomplete set-count buffer.
        compose.runOnIdle { routine.value = routine.value.copy(exercises = listOf(plan.copy(reps = 12))) }
        compose.onNodeWithTag("routine-entry-sets").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))

        compose.onNodeWithTag("routine-entry-rest").performTextReplacement("2:00")
        compose.runOnIdle { assertEquals(SavedPlan(3, 12, null, 120), saves.last()) }
        compose.onNodeWithTag("routine-entry-sets").performTextReplacement("4")
        compose.onNodeWithTag("routine-entry-reps").performTextReplacement("")
        compose.runOnIdle { assertEquals(SavedPlan(4, 0, null, 120), saves.last()) }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasText("Start this routine"))
        compose.onNodeWithText("Start this routine").performClick()
        compose.runOnIdle {
            assertEquals(listOf("save", "start"), events.takeLast(2))
            assertEquals(SavedPlan(4, 0, null, 120), saves.last())
        }
    }

    @Test
    fun savingOtherFieldsRetainsTheExactKilogramsBehindRoundedPounds() {
        val originalKg = 22.123456789
        val plan = PlannedExerciseUi("entry", ExerciseUi("bench", "Bench press", "CHEST", "BARBELL"), reps = 0, weightKg = originalKg)
        val saves = mutableListOf<SavedPlan>()
        val actions = actions { name, args ->
            if (name == "updateRoutineExercise") saves += SavedPlan(args[1] as Int, args[2] as Int, args[3] as Double?, args[4] as Int)
        }
        compose.setContent {
            MaterialTheme {
                RoutineEditor(RoutineUi("routine", "Test routine", listOf(plan)), UiState(loading = false, preferences = PreferencesUi(pounds = true)), actions, {})
            }
        }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-entry-load"))
        compose.onNodeWithTag("routine-entry-sets").performTextReplacement("4")
        compose.runOnIdle { assertEquals(originalKg, saves.last().weightKg!!, 0.0) }
        compose.onNodeWithTag("routine-entry-load").performTextReplacement("-")
        compose.onNodeWithTag("routine-entry-reps").performTextReplacement("8")
        compose.runOnIdle { assertEquals(originalKg, saves.last().weightKg!!, 0.0) }
        compose.onNodeWithTag("routine-entry-load").performTextReplacement("50.5")
        compose.runOnIdle { assertEquals(displayToKg(50.5, true), saves.last().weightKg!!, 0.0) }
    }

    @Test
    fun replacingExerciseCannotAutosavePreviousMovementsWeight() {
        val original = PlannedExerciseUi("entry", ExerciseUi("bench", "Bench press", "Chest", "Barbell"), weightKg = 25.0)
        val routine = mutableStateOf(RoutineUi("routine", "Test routine", listOf(original)))
        val saves = mutableListOf<SavedPlan>()
        val actions = actions { name, args ->
            if (name == "updateRoutineExercise") saves += SavedPlan(args[1] as Int, args[2] as Int, args[3] as Double?, args[4] as Int)
        }
        compose.setContent { MaterialTheme { RoutineEditor(routine.value, UiState(loading = false), actions, {}) } }
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("routine-entry-load"))
        compose.onNodeWithTag("routine-entry-load").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("25")))
        compose.runOnIdle {
            routine.value = routine.value.copy(exercises = listOf(original.copy(
                exercise = ExerciseUi("row", "Cable row", "Back", "Cable"), weightKg = null)))
        }
        compose.onNodeWithTag("routine-entry-load").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithTag("routine-entry-rest").performTextReplacement("2:00")
        compose.runOnIdle { assertNull(saves.last().weightKg); assertEquals(120, saves.last().restSeconds) }
    }

    private data class SavedPlan(val sets: Int, val reps: Int, val weightKg: Double?, val restSeconds: Int)

    private fun actions(onCall: (String, Array<out Any?>) -> Unit): MoosclesActions = Proxy.newProxyInstance(
        MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Routine test actions"
            else -> { onCall(method.name, args ?: emptyArray()); null }
        }
    } as MoosclesActions
}
