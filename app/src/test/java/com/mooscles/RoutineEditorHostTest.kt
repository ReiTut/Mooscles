package com.mooscles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
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
    private val saves = mutableListOf<SavedTarget>()
    private val calls = mutableListOf<Pair<String, List<Any?>>>()
    private val routine = mutableStateOf(fixtures())
    private val actions = actions { name, args ->
        calls += name to args.toList()
        if (name == "updateRoutineSet") saves += SavedTarget(args[0] as String, args[1] as Int, args[2] as Double?)
    }

    @Test fun individualRowsShowReferenceValuesAndSaveOnlyTheEditedRow() {
        show()
        assertField("bench-1", "load", "17")
        assertField("bench-1", "reps", "8")
        assertField("bench-2", "reps", "8")
        assertField("bench-3", "reps", "7")
        field("bench-1", "load").performTextReplacement("17.5")
        field("bench-1", "reps").performTextReplacement("9")
        compose.runOnIdle { assertEquals(listOf(SavedTarget("bench-1", 8, 17.5), SavedTarget("bench-1", 9, 17.5)), saves) }
        assertField("bench-2", "load", "17")
        assertField("bench-2", "reps", "8")
        assertField("fly-1", "load", "70")
        assertField("fly-1", "reps", "6")
        assertField("fly-2", "load", "65")
        assertField("fly-2", "reps", "10")
        assertField("fly-3", "load", "65")
        assertField("fly-3", "reps", "8")
        compose.onNodeWithText("Save targets").assertDoesNotExist()
    }

    @Test fun blankRepetitionsAndInvalidWeightRemainEditableWithoutBlockingSiblingSaves() {
        show()
        field("bench-1", "load").performTextReplacement("-")
        field("bench-1", "reps").performTextReplacement("")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 0, 17.0), saves.single()) }
        field("bench-1", "reps").performTextReplacement("0")
        field("bench-1", "reps").performTextReplacement("-1")
        field("bench-1", "reps").performTextReplacement("1.5")
        compose.runOnIdle { assertEquals(1, saves.size) }
        field("bench-1", "reps").performTextReplacement("12")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 12, 17.0), saves.last()) }
        assertField("bench-1", "load", "-")
        field("bench-1", "load").performTextReplacement("18,25")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 12, 18.25), saves.last()) }
    }

    @Test fun olderAutosaveAcknowledgementsCannotReplaceNewerDrafts() {
        show()
        field("bench-1", "reps").performTextReplacement("9")
        field("bench-1", "reps").performTextReplacement("10")
        compose.runOnIdle { changeTarget("bench-1") { it.copy(reps = 9, inherited = false) } }
        assertField("bench-1", "reps", "10")
        compose.runOnIdle { changeTarget("bench-1") { it.copy(reps = 10, inherited = false) } }
        assertField("bench-1", "reps", "10")
        compose.runOnIdle { assertEquals(2, saves.size) }
    }

    @Test fun repositoryPrRefreshUpdatesBothValuesWithoutSavingDuringComposition() {
        show()
        compose.runOnIdle { changeTarget("bench-1") { it.copy(reps = 6, weightKg = 22.0) } }
        assertField("bench-1", "load", "22")
        assertField("bench-1", "reps", "6")
        assertField("bench-2", "load", "17")
        assertField("bench-2", "reps", "8")
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
        field("bench-1", "reps").performTextReplacement("7")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 7, 22.0), saves.single()) }
    }

    @Test fun editingRepetitionsRetainsExactKilogramsBehindRoundedPoundsAndInvalidDraft() {
        val exactKg = 22.123456789
        changeTarget("bench-1") { it.copy(weightKg = exactKg) }
        show(pounds = true)
        assertField("bench-1", "load", number(kgToDisplay(exactKg, true)))
        field("bench-1", "load").performTextReplacement("-")
        field("bench-1", "reps").performTextReplacement("9")
        compose.runOnIdle { assertEquals(exactKg, saves.single().weightKg!!, 0.0) }
        field("bench-1", "load").performTextReplacement(number(kgToDisplay(exactKg, true)))
        compose.runOnIdle { assertEquals(exactKg, saves.last().weightKg!!, 0.0) }
        field("bench-1", "load").performTextReplacement("50.5")
        compose.runOnIdle { assertEquals(displayToKg(50.5, true), saves.last().weightKg!!, 0.0) }
    }

    @Test fun explicitlyClearedWeightSavesBodyweightAndPrInheritanceIsAnExplicitSeparateAction() {
        show()
        field("bench-1", "load").performTextReplacement("")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 8, null), saves.single()) }
        scroll(hasTestTag("routine-set-bench-1-menu"))
        compose.onNodeWithTag("routine-set-bench-1-menu").performClick()
        compose.onNodeWithText("Custom target").assertExists()
        compose.onNodeWithText("Use PR defaults").performClick()
        compose.runOnIdle { assertEquals("inheritRoutineSet" to listOf("bench-1"), calls.last()) }
    }

    @Test fun replacingAnExerciseResetsDraftBuffersAndNeverAutosavesTheOldMovement() {
        show()
        assertField("bench-1", "load", "17")
        compose.runOnIdle {
            routine.value = routine.value.copy(exercises = routine.value.exercises.map { plan ->
                if (plan.id != "bench") plan else plan.copy(exercise = ExerciseUi("pullup", "Pull-up", "BACK", "BODYWEIGHT"),
                    setTargets = plan.setTargets.map { it.copy(weightKg = null, reps = 12) })
            })
        }
        assertField("bench-1", "load", "")
        assertField("bench-1", "reps", "12")
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
        field("bench-1", "reps").performTextReplacement("13")
        compose.runOnIdle { assertEquals(SavedTarget("bench-1", 13, null), saves.single()) }
    }

    @Test fun explicitPrDefaultAcknowledgementClearsAnInvalidDraftEvenWhenThePairIsUnchanged() {
        show()
        field("bench-2", "load").performTextReplacement("-")
        scroll(hasTestTag("routine-set-bench-2-menu"))
        compose.onNodeWithTag("routine-set-bench-2-menu").performClick()
        compose.onNodeWithText("Use PR defaults").performClick()
        compose.runOnIdle { changeTarget("bench-2") { it.copy(inherited = true) } }
        assertField("bench-2", "load", "17")
        assertField("bench-2", "reps", "8")
        compose.runOnIdle { assertTrue(saves.isEmpty()) }
    }

    @Test fun delayedPrDefaultAcknowledgementCannotOverwriteAMoreRecentManualEdit() {
        show()
        scroll(hasTestTag("routine-set-bench-2-menu"))
        compose.onNodeWithTag("routine-set-bench-2-menu").performClick()
        compose.onNodeWithText("Use PR defaults").performClick()
        field("bench-2", "reps").performTextReplacement("12")
        compose.runOnIdle { changeTarget("bench-2") { it.copy(inherited = true, reps = 6, weightKg = 22.0) } }
        assertField("bench-2", "reps", "12")
        assertField("bench-2", "load", "17")
        compose.runOnIdle { changeTarget("bench-2") { it.copy(inherited = false, reps = 12, weightKg = 17.0) } }
        assertField("bench-2", "reps", "12")
        compose.runOnIdle { assertEquals(SavedTarget("bench-2", 12, 17.0), saves.single()) }
    }

    @Test fun restMenuOpensExistingFixedColonEditorAndSavesOnlyThatEntryWithinFiveMinutes() {
        show()
        scroll(hasTestTag("routine-bench-menu"))
        compose.onNodeWithTag("routine-bench-rest-label").assertTextEquals("2:15 min rest")
        compose.onNodeWithTag("routine-bench-menu").performClick()
        compose.onNodeWithText("Replace Exercise").assertExists()
        compose.onNodeWithText("Remove Exercise").assertExists()
        compose.onNodeWithText("Edit Rest Timer").performClick()
        compose.onNodeWithTag("routine-bench-rest").assert(editable("2:15"))
        compose.onNodeWithTag("routine-bench-rest").performClick().performTextReplacement("5:00")
        compose.onNodeWithTag("routine-bench-rest-plus").assertIsNotEnabled()
        compose.onNodeWithTag("routine-bench-rest").performTextReplacement("6:00")
        compose.onNodeWithTag("routine-bench-rest").assert(editable("5:00"))
        compose.onNodeWithTag("routine-bench-rest-minus").performClick()
        compose.onNodeWithTag("routine-bench-rest").assert(editable("4:45"))
        compose.runOnIdle {
            assertEquals(listOf("bench", 285), calls.last().second)
            assertTrue(calls.filter { it.first == "updateRoutineExerciseRest" }.all { it.second[0] == "bench" && (it.second[1] as Int) <= 300 })
        }
        compose.onNodeWithText("Done").performClick()
        scroll(hasTestTag("routine-fly-rest-label"))
        compose.onNodeWithTag("routine-fly-rest-label").assertTextEquals("2:00 min rest")
    }

    @Test fun addAndRemoveSetArePerExerciseAndPerStableSetIdentifier() {
        show()
        scroll(hasTestTag("routine-bench-add-set"))
        compose.onNodeWithTag("routine-bench-add-set").performClick()
        scroll(hasTestTag("routine-set-bench-2-menu"))
        compose.onNodeWithTag("routine-set-bench-2-menu").performClick()
        compose.onNodeWithText("Remove Set").performClick()
        compose.runOnIdle {
            assertTrue(calls.contains("addRoutineSet" to listOf("bench")))
            assertEquals("removeRoutineSet" to listOf("bench-2"), calls.last())
        }
    }

    @Test fun finalSetCannotBeRemovedAndFiftiethSetDisablesAdd() {
        routine.value = routine.value.copy(exercises = listOf(routine.value.exercises.first().copy(setTargets = listOf(RoutineSetUi("only")))))
        show()
        scroll(hasTestTag("routine-set-only-menu"))
        compose.onNodeWithTag("routine-set-only-menu").performClick()
        compose.onNodeWithText("Remove Set").assertIsNotEnabled()
        compose.onNodeWithText("Use PR defaults").performClick()
        compose.runOnIdle {
            routine.value = routine.value.copy(exercises = routine.value.exercises.map { it.copy(setTargets = List(50) { index -> RoutineSetUi("row-$index") }) })
        }
        scroll(hasTestTag("routine-bench-add-set"))
        compose.onNodeWithTag("routine-bench-add-set").assertIsNotEnabled()
    }

    @Test fun longNamesAndLaterSetControlsRemainReachableInAKeyboardSizedViewport() {
        val height = mutableStateOf(900.dp)
        routine.value = routine.value.copy(exercises = routine.value.exercises + PlannedExerciseUi("long", ExerciseUi("machine", "Seated Overhead Press (Machine) with a deliberately long exercise name", "SHOULDERS", "MACHINE"),
            setTargets = listOf(RoutineSetUi("long-1"), RoutineSetUi("long-2"))))
        compose.setContent { MaterialTheme { Box(Modifier.height(height.value)) { RoutineEditor(routine.value, UiState(loading = false), actions, {}) } } }
        scroll(hasTestTag("routine-set-long-2-reps"))
        compose.onNodeWithTag("routine-set-long-2-reps").performClick().assertIsFocused()
        // Host tests cannot display a real IME. Reduce the available viewport to
        // the same constrained height, then verify focused entry and Add Set.
        compose.runOnIdle { height.value = 340.dp }
        scroll(hasTestTag("routine-set-long-2-reps"))
        compose.onNodeWithTag("routine-set-long-2-reps").assertIsDisplayed().performTextReplacement("12")
        scroll(hasTestTag("routine-long-add-set"))
        compose.onNodeWithTag("routine-long-add-set").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(calls.contains("addRoutineSet" to listOf("long"))) }
    }

    private fun show(pounds: Boolean = false) {
        compose.setContent { MaterialTheme { RoutineEditor(routine.value, UiState(loading = false, preferences = PreferencesUi(pounds = pounds)), actions, {}) } }
    }
    private fun changeTarget(id: String, update: (RoutineSetUi) -> RoutineSetUi) {
        routine.value = routine.value.copy(exercises = routine.value.exercises.map { plan -> plan.copy(setTargets = plan.setTargets.map { if (it.id == id) update(it) else it }) })
    }
    private fun scroll(matcher: SemanticsMatcher) = compose.onNodeWithTag("routine-editor-list").performScrollToNode(matcher)
    private fun field(id: String, field: String): SemanticsNodeInteraction {
        val tag = "routine-set-$id-$field"
        scroll(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun assertField(id: String, field: String, text: String) { field(id, field).assert(editable(text)) }
    private fun editable(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text))
    private data class SavedTarget(val id: String, val reps: Int, val weightKg: Double?)

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

    companion object {
        internal fun fixtures() = RoutineUi("routine", "Push day", listOf(
            PlannedExerciseUi("bench", ExerciseUi("incline-bench", "Incline Bench Press (Barbell)", "CHEST", "BARBELL"), sets = 3, restSeconds = 135,
                setTargets = listOf(RoutineSetUi("bench-1", 8, 17.0), RoutineSetUi("bench-2", 8, 17.0, false), RoutineSetUi("bench-3", 7, 17.0, false))),
            PlannedExerciseUi("fly", ExerciseUi("pec-deck", "Chest Fly (Pec Deck)", "CHEST", "MACHINE"), sets = 3,
                setTargets = listOf(RoutineSetUi("fly-1", 6, 70.0, false), RoutineSetUi("fly-2", 10, 65.0, false), RoutineSetUi("fly-3", 8, 65.0, false))),
        ))
    }
}
