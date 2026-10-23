package com.mooscles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
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
class WorkoutLazyRowsHostTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var focusManager: FocusManager
    private val exercise = ExerciseUi("exercise", "Repeated movement", "Back", "Bodyweight", "Added weight")

    @Test fun aSingleLargeExerciseOnlyComposesVisibleSetInputs() {
        val sets = List(120) { SetUi("set-$it", reps = 8, weightKg = 20.0) }
        val state = UiState(loading = false, active = workout(listOf(WorkoutExerciseUi("entry", exercise, sets))))
        show(state, actions())
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        compose.onNodeWithTag("workout-set-set-0").assertExists()
        compose.onNodeWithTag("workout-set-set-119").assertDoesNotExist()
        val inputCount = compose.onAllNodes(SemanticsMatcher("set input") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("set-reps-") == true
        }).fetchSemanticsNodes().size
        assertTrue("120 rows must not create 120 off-screen input fields: $inputCount", inputCount in 1..8)
        list().performScrollToNode(hasTestTag("workout-set-set-119"))
        compose.onNodeWithTag("workout-set-set-119").assertExists()
        compose.onNodeWithTag("workout-set-set-0").assertDoesNotExist()
    }

    @Test fun blankAndInvalidDraftsSurviveDisposalBeforePersistenceEchoes() {
        val updates = mutableListOf<List<Any?>>()
        val sets = List(35) { SetUi("set-$it", reps = 8, weightKg = 20.0) }
        val state = UiState(loading = false, active = workout(listOf(WorkoutExerciseUi("entry", exercise, sets))))
        show(state, actions { method, args -> if (method == "updateSet") updates += args.toList() })
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-reps-0").performTextReplacement("")
        field("set-0", "set-load-0").performTextReplacement("-")
        compose.runOnIdle { focusManager.clearFocus(force = true) }
        list().performScrollToNode(hasTestTag("workout-set-set-34"))
        compose.onNodeWithTag("workout-set-set-0").assertDoesNotExist()
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-reps-0").assert(editableText(""))
        field("set-0", "set-load-0").assert(editableText("-"))
        compose.runOnIdle {
            assertEquals(1, updates.size)
            assertEquals("set-0", updates.single()[0])
            assertEquals(0, updates.single()[1])
            assertEquals(20.0, updates.single()[2] as Double, 0.0)
        }
    }

    @Test fun repeatedExerciseInstancesKeepTheirOwnValuesAndCompletionAfterReorderAndScroll() {
        val entries = List(16) { WorkoutExerciseUi("entry-$it", exercise,
            listOf(SetUi("set-$it", reps = 8, weightKg = 20.0 + it))) }
        val state = mutableStateOf(UiState(loading = false, active = workout(entries)))
        val actions = actions { method, args ->
            if (method == "updateSet") {
                val updated = SetUi(args[0] as String, args[1] as Int, args[2] as Double,
                    args[3] as Boolean, args[4] as Boolean, args[5] as Boolean)
                val current = state.value.active!!
                state.value = state.value.copy(active = current.copy(exercises = current.exercises.map { entry ->
                    if (entry.sets.any { it.id == updated.id }) entry.copy(sets = entry.sets.map { if (it.id == updated.id) updated else it }) else entry
                }))
            }
        }
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 600.dp)) { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) } } }
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-load-0").performTextReplacement("")
        field("set-0", "set-reps-0").performTextReplacement("12")
        field("set-0", "set-complete-0").performClick()
        compose.runOnIdle {
            val current = state.value.active!!
            assertEquals(SetUi("set-0", 12, 0.0, completed = true), current.exercises.first().sets.single())
            assertEquals(21.0, current.exercises[1].sets.single().weightKg, 0.0)
            state.value = state.value.copy(active = current.copy(exercises = current.exercises.reversed()))
        }
        list().performScrollToNode(hasTestTag("workout-set-set-15"))
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-load-0").assert(editableText(""))
        field("set-0", "set-reps-0").assert(editableText("12"))
        compose.onNode(hasContentDescription("Undo set completion") and hasAnyAncestor(hasTestTag("workout-set-set-0"))).assertExists()
    }

    @Test fun aDisposedRowRestoresWeightUsingTheCurrentDisplayUnitWithoutRoundingItsStoredKg() {
        val exactKg = 17.123456789
        val state = mutableStateOf(UiState(loading = false, active = workout(listOf(WorkoutExerciseUi("entry", exercise,
            List(30) { SetUi("set-$it", 8, exactKg) })))))
        val updates = mutableListOf<List<Any?>>()
        val actions = actions { method, args -> if (method == "updateSet") updates += args.toList() }
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 600.dp)) { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) } } }
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        list().performScrollToNode(hasTestTag("workout-set-set-29"))
        compose.runOnIdle { state.value = state.value.copy(preferences = state.value.preferences.copy(pounds = true)) }
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-load-0").assert(editableText(number(kgToDisplay(exactKg, true))))
        field("set-0", "set-reps-0").performTextReplacement("9")
        compose.runOnIdle { assertEquals(exactKg, updates.last()[2] as Double, 0.0) }
    }

    @Test fun rapidCrossFieldEditsAndDelayedOlderEchoDoNotRevertCompletionOrOtherValues() {
        val entries = listOf(WorkoutExerciseUi("entry", exercise, List(30) { SetUi("set-$it", 8, 20.0) }))
        val state = mutableStateOf(UiState(loading = false, active = workout(entries)))
        val updates = mutableListOf<List<Any?>>()
        val actions = actions { method, args -> if (method == "updateSet") updates += args.toList() }
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 600.dp)) { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) } } }
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        val editLoad = field("set-0", "set-load-0").fetchSemanticsNode().config[SemanticsActions.SetText].action!!
        val editReps = field("set-0", "set-reps-0").fetchSemanticsNode().config[SemanticsActions.SetText].action!!
        compose.runOnIdle {
            // Both callbacks run before recomposition can replace their lambdas.
            editLoad(AnnotatedString("22"))
            editReps(AnnotatedString("6"))
            assertEquals(22.0, updates.last()[2] as Double, 0.0)
            assertEquals(6, updates.last()[1])
        }
        field("set-0", "set-complete-0").performClick()
        compose.runOnIdle {
            assertEquals(true, updates.last()[3])
            val current = state.value.active!!
            state.value = state.value.copy(active = current.copy(exercises = listOf(current.exercises.single().copy(
                sets = current.exercises.single().sets.map { if (it.id == "set-0") it.copy(weightKg = 22.0) else it }))))
        }
        list().performScrollToNode(hasTestTag("workout-set-set-29"))
        list().performScrollToNode(hasTestTag("workout-set-set-0"))
        field("set-0", "set-load-0").assert(editableText("22"))
        field("set-0", "set-reps-0").assert(editableText("6"))
        compose.onNode(hasContentDescription("Undo set completion") and hasAnyAncestor(hasTestTag("workout-set-set-0"))).assertExists()
        field("set-0", "set-reps-0").performTextReplacement("7")
        compose.runOnIdle {
            assertEquals(22.0, updates.last()[2] as Double, 0.0)
            assertEquals(7, updates.last()[1])
            assertEquals(true, updates.last()[3])
        }
    }

    @Test fun acknowledgedFieldsAcceptExternalChangesWithoutErasingAnInvalidLocalDraft() {
        val state = mutableStateOf(UiState(loading = false, active = workout(listOf(WorkoutExerciseUi("entry", exercise,
            listOf(SetUi("set", 8, 20.0)))))))
        var lastUpdate: SetUi? = null
        val actions = actions { method, args -> if (method == "updateSet") lastUpdate = SetUi(args[0] as String,
            args[1] as Int, args[2] as Double, args[3] as Boolean, args[4] as Boolean, args[5] as Boolean) }
        compose.setContent { MaterialTheme { Box(Modifier.size(360.dp, 600.dp)) { ActiveWorkoutScreen(state.value, actions, {}, {}, {}) } } }
        list().performScrollToNode(hasTestTag("workout-set-set"))
        field("set", "set-load-0").performTextReplacement("22")
        field("set", "set-reps-0").performTextReplacement("6")
        fun emit(set: SetUi) {
            val current = state.value.active!!
            state.value = state.value.copy(active = current.copy(exercises = listOf(current.exercises.single().copy(sets = listOf(set)))))
        }
        compose.runOnIdle { emit(lastUpdate!!) }
        compose.runOnIdle { emit(SetUi("set", 5, 24.0)) }
        field("set", "set-load-0").assert(editableText("24"))
        field("set", "set-reps-0").assert(editableText("5"))
        field("set", "set-load-0").performTextReplacement("-")
        compose.runOnIdle { emit(SetUi("set", 7, 26.0)) }
        field("set", "set-load-0").assert(editableText("-"))
        field("set", "set-reps-0").assert(editableText("7"))
    }

    private fun workout(entries: List<WorkoutExerciseUi>) = WorkoutUi("workout", "Lazy session", System.currentTimeMillis(), exercises = entries)
    private fun list() = compose.onNodeWithTag("active-workout-list")
    private fun field(setId: String, tag: String) = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("workout-set-$setId")))
    private fun editableText(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
    private fun show(state: UiState, actions: MoosclesActions) {
        compose.setContent { focusManager = LocalFocusManager.current; MaterialTheme { Box(Modifier.size(360.dp, 600.dp)) { ActiveWorkoutScreen(state, actions, {}, {}, {}) } } }
    }
    private fun actions(onAction: (String, Array<out Any?>) -> Unit = { _, _ -> }): MoosclesActions =
        Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Lazy workout test actions"
                else -> { onAction(method.name, args ?: emptyArray()); null }
            }
        } as MoosclesActions
}
