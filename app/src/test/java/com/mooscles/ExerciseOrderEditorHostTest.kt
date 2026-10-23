package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(ExperimentalTestApi::class)
class ExerciseOrderEditorHostTest {
    @get:Rule val compose = createComposeRule()
    private val entries = listOf(OrderExerciseUi("first", "Bench press"),
        OrderExerciseUi("repeat", "Bench press"), OrderExerciseUi("third", "Pull-up"))
    private var saved: List<String>? = null
    private var cancels = 0

    @Test fun accessibleMovesKeepRepeatedNamesIndependentAndSaveOnlyPersistentInstanceIds() {
        show()
        position("first", 1, 3)
        move("repeat", "Move up")
        position("repeat", 1, 3)
        position("first", 2, 3)
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("exercise-order-save").performClick()
        compose.runOnIdle { assertEquals(listOf("repeat", "first", "third"), saved); assertEquals(0, cancels) }
    }

    @Test fun dragShowsProvisionalOrderImmediatelyAndSaveConfirmsIt() {
        show()
        val first = compose.onNodeWithTag("exercise-order-drag-first").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithTag("exercise-order-drag-third").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("exercise-order-drag-first").performTouchInput {
            swipe(center, center + Offset(0f, third.center.y - first.center.y), 400)
        }
        position("first", 3, 3)
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithTag("exercise-order-save").performClick()
        compose.runOnIdle { assertEquals(listOf("repeat", "third", "first"), saved) }
    }

    @Test fun cancelDiscardsProvisionalMovesWithoutAnySave() {
        show()
        compose.onNodeWithTag("exercise-order-drag-third").performClick()
        compose.onNodeWithText("Move up", substring = false).performClick()
        position("third", 2, 3)
        compose.onNodeWithTag("exercise-order-cancel").performClick()
        compose.runOnIdle { assertNull(saved); assertEquals(1, cancels) }
    }

    @Suppress("DEPRECATION")
    @Test fun androidBackCancelsWithoutSaving() {
        show()
        move("third", "Move up")
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.runOnIdle { assertNull(saved); assertEquals(1, cancels) }
    }

    @Test fun heldDragNearTheBottomAutomaticallyScrollsToLaterRows() {
        val many = List(30) { OrderExerciseUi("entry-$it", "Exercise $it") }
        show(many)
        val list = compose.onNodeWithTag("exercise-order-list").fetchSemanticsNode().boundsInRoot
        val handle = compose.onNodeWithTag("exercise-order-drag-entry-0").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("exercise-order-drag-entry-0").performTouchInput {
            down(center)
            moveBy(Offset(0f, list.bottom - 12f - handle.center.y), 350)
        }
        // Advance both Compose frames and Android layout while the pointer stays
        // down. One large clock jump does not pump Robolectric's view layout
        // between frames, so it cannot model repeated edge-scroll remeasures.
        repeat(90) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("exercise-order-drag-entry-0").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        val position = compose.onNodeWithTag("exercise-order-row-entry-0")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
            .substringAfter("Position ").substringBefore(' ').toInt()
        assertTrue("A held edge drag must reach rows beyond the original viewport, was $position", position > 12)
        compose.onNodeWithTag("exercise-order-save").performClick()
        compose.runOnIdle { assertEquals(30, saved!!.toSet().size); assertEquals("entry-0", saved!![position - 1]) }
    }

    @Test fun changesToExerciseMembershipDisableSavingAnObsoleteOrder() {
        val current = mutableStateOf(entries)
        compose.setContent { MaterialTheme { ExerciseOrderEditor(current.value, { saved = it }, { cancels++ }) } }
        move("third", "Move up")
        compose.runOnIdle { current.value = entries.drop(1) }
        compose.onNodeWithTag("exercise-order-save").assertIsNotEnabled()
        compose.onNodeWithText("Exercises changed while this view was open. Cancel and reopen to reorder them.").assertExists()
        compose.onNodeWithTag("exercise-order-cancel").performClick()
        compose.runOnIdle { assertNull(saved); assertEquals(1, cancels) }
    }

    @Test fun routineOrderingPreservesInvalidNumericDraftsAcrossCancelAndSave() {
        val fixture = RoutineEditorHostTest.fixtures()
        val routine = mutableStateOf(fixture)
        val calls = mutableListOf<Pair<String, List<Any?>>>()
        val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> "Order actions"
                else -> {
                    calls += method.name to args.orEmpty().toList()
                    if (method.name == "reorderRoutineExercises") {
                        @Suppress("UNCHECKED_CAST") val ids = args!![1] as List<String>
                        val byId = routine.value.exercises.associateBy { it.id }
                        routine.value = routine.value.copy(exercises = ids.map { byId.getValue(it) })
                    }
                    null
                }
            }
        } as MoosclesActions
        compose.setContent { MaterialTheme { RoutineEditor(routine.value, UiState(loading = false), actions, {}) } }
        routineField("routine-set-bench-1-load").performTextReplacement("-")
        routineField("routine-set-bench-1-reps").performTextReplacement("")
        compose.runOnIdle { calls.clear() }
        openRoutineOrder()
        move("fly", "Move up")
        compose.onNodeWithTag("exercise-order-cancel").performClick()
        routineField("routine-set-bench-1-load").assertTextContains("-")
        routineField("routine-set-bench-1-reps").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle { assertTrue(calls.isEmpty()); assertEquals(fixture, routine.value) }
        openRoutineOrder()
        move("fly", "Move up")
        compose.onNodeWithTag("exercise-order-save").performClick()
        routineField("routine-set-bench-1-load").assertTextContains("-")
        routineField("routine-set-bench-1-reps").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.runOnIdle {
            assertEquals(listOf("reorderRoutineExercises" to listOf(fixture.id, listOf("fly", "bench"))), calls)
            assertEquals(listOf(fixture.exercises[1], fixture.exercises[0]), routine.value.exercises)
        }
    }

    private fun show(current: List<OrderExerciseUi> = entries) {
        compose.setContent { MaterialTheme { ExerciseOrderEditor(current, { saved = it }, { cancels++ }) } }
    }
    private fun move(id: String, label: String) {
        compose.onNodeWithTag("exercise-order-row-$id").performCustomAccessibilityActionWithLabel(label)
    }
    private fun position(id: String, position: Int, total: Int) {
        compose.onNodeWithTag("exercise-order-row-$id").assert(SemanticsMatcher.expectValue(
            SemanticsProperties.StateDescription, "Position $position of $total"))
    }
    private fun openRoutineOrder() {
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag("exercise-plan-reorder"))
        compose.onNodeWithTag("exercise-plan-reorder").performClick()
    }
    private fun routineField(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("routine-editor-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
}
