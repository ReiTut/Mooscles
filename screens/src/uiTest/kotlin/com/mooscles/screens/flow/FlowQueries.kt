package com.mooscles.screens.flow

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.IntSize
import com.mooscles.designsystem.components.ExerciseHeaderToggleTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.screens.workout.ActiveWorkoutTags
import com.mooscles.screens.workout.exerciseColumnsTag
import com.mooscles.screens.workout.exerciseHeaderTag
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.assertTrue

// What the flows over the real stack (ActiveWorkoutFlowTest, LibraryFlowTest, TapBudgetTest) read and find: the view
// model's state, which comes from Room, and the nodes of the workout screen.

/** How long a flow waits, in real time, for the view model and Room. */
internal const val WAIT_MS = 20_000L

// --- The view model's state ---------------------------------------------------------------

internal fun active(vm: FlowViewModel): WorkoutUi = requireNotNull(vm.contentUi.value.active) { "No active workout" }

internal fun entry(vm: FlowViewModel, id: String): WorkoutExerciseUi = active(vm).exercises.first { it.id == id }

internal fun set(vm: FlowViewModel, id: String): SetUi? =
    vm.contentUi.value.active?.exercises?.firstNotNullOfOrNull { e -> e.sets.firstOrNull { it.id == id } }

/** Waits, in real time, for [condition]: the view model and Room work on their own threads. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.await(what: String, condition: () -> Boolean) {
    waitUntil(what, timeoutMillis = WAIT_MS, condition = condition)
}

// --- Finding and acting ---------------------------------------------------------------------

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.exists(matcher: SemanticsMatcher, unmerged: Boolean = false): Boolean =
    onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()

internal fun row(id: String): SemanticsMatcher = hasTestTag(workoutSetRowTag(id))

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.check(id: String): SemanticsNodeInteraction = onNode(hasTestTag(SetRowCheckTag) and hasAnyAncestor(row(id)))

/** The editable text of [cell] (the load or reps tag) in the row of [id]. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.field(id: String, cell: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(cell)) and hasAnyAncestor(row(id)), useUnmergedTree = true)

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollToRow(id: String) {
    onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(row(id))
    waitForIdle()
}

/**
 * Opens the card of [entryId] by its header's toggle when it is collapsed (the advance closes a
 * card once its sets are all done). An open card shows its column titles right under the header.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.openCard(entryId: String) {
    onNodeWithTag(ActiveWorkoutTags.LIST).performScrollToNode(hasTestTag(exerciseHeaderTag(entryId)))
    waitForIdle()
    if (exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true)) return
    onNode(hasTestTag(ExerciseHeaderToggleTag) and hasAnyAncestor(hasTestTag(exerciseHeaderTag(entryId)))).performClick()
    await("the card opens") { exists(hasTestTag(exerciseColumnsTag(entryId)), unmerged = true) }
    waitForIdle()
}

// --- Captures and the window -----------------------------------------------------------------

/**
 * Takes a capture of each root (the screen, and a dialog or sheet over it) and hands it to the
 * platform ([saveFlowCapture]: a PNG on iOS). Where `captureToImage` is not supported, says so once and goes on.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.capture(state: String) {
    val roots = onAllNodes(isRoot())
    val count = roots.fetchSemanticsNodes().size
    for (i in 0 until count) {
        // Only the capture may be unsupported; a failure to encode or write the PNG fails the flow.
        val image = try {
            roots[i].captureToImage()
        } catch (failure: Throwable) {
            println("FLOW capture unsupported ($state): ${failure.message}")
            return
        }
        saveFlowCapture(if (i == 0) state else "$state-$i", image)
    }
}

/**
 * [node] lies inside the window, [window] px (`LocalWindowInfo.containerSize`, read by the host):
 * what the person sees. The iOS simulator's window is smaller than the emulator's, so a toast drawn
 * at the bottom of a frame taller than the window would pass `assertIsDisplayed` and be out of sight.
 */
internal fun assertInsideWindow(node: SemanticsNodeInteraction, window: IntSize, what: String) {
    val bounds = node.fetchSemanticsNode().boundsInWindow
    assertTrue(window.width > 0 && window.height > 0, "$what: the window has no size ($window)")
    assertTrue(
        bounds.left >= -WINDOW_SLACK_PX && bounds.top >= -WINDOW_SLACK_PX &&
            bounds.right <= window.width + WINDOW_SLACK_PX && bounds.bottom <= window.height + WINDOW_SLACK_PX,
        "$what: $bounds is not inside the window $window",
    )
}

private const val WINDOW_SLACK_PX = 1f
