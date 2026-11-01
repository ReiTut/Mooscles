package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.MToastResult
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_set_removed
import com.mooscles.screens.resources.workout_undo
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetUi
import org.jetbrains.compose.resources.stringResource

/**
 * A set just removed, with what "Undo" puts back: [set] as it stood ([SetRowEditor.snapshot]),
 * its 0-based [position] in the exercise [entryId] and the [number] the toast names ("Set 4
 * removed").
 */
data class RemovedSet(val entryId: String, val position: Int, val set: SetUi, val number: Int)

/** The texts of the undo toast, read in composition: "Set 4 removed" and "Undo". */
data class UndoTexts(val message: String, val undo: String)

/** The texts of the undo toast of the set [number]. */
@Composable
internal fun undoTexts(number: Int): UndoTexts =
    UndoTexts(stringResource(Res.string.workout_set_removed, number), stringResource(Res.string.workout_undo))

/**
 * Shows the undo toast of [removed] on [host] and, when "Undo" is used, puts the set back with
 * `restoreSet(entryId, position, set)`. It suspends while the toast is up: `timing.toastAction`
 * (6 s) or the longer time the system's accessibility settings ask for, both in `MToastHost`.
 * Cancelling the call closes the toast, which is how another removal, finishing or discarding
 * close it (F36). Nothing vibrates (spec, haptics).
 */
internal suspend fun showUndo(host: MToastHostState, removed: RemovedSet, actions: MoosclesActions, texts: UndoTexts) {
    if (host.show(texts.message, actionLabel = texts.undo) == MToastResult.ActionPerformed) {
        actions.restoreSet(removed.entryId, removed.position, removed.set)
    }
}

/**
 * The undo toast of [ActiveWorkoutScreenState.lastRemoved], shown on [host] (the screen's
 * `MToastHost`): put it on the screen once. The toast's show runs in an effect keyed on the
 * removal, so that effect's job is the one to cancel: a new removal replaces the key and
 * [ActiveWorkoutScreenState.closeUndo] takes it away, and either cancels the show and closes the
 * toast. Since the removal is saved, the toast comes back after a recreation (Review Focus 1),
 * with its time from the start. When it closes on its own or by "Undo", the removal is forgotten.
 */
@Composable
internal fun UndoRemovalToast(screen: ActiveWorkoutScreenState, host: MToastHostState, actions: MoosclesActions) {
    val removed = screen.lastRemoved ?: return
    val texts = undoTexts(removed.number)
    LaunchedEffect(removed) {
        showUndo(host, removed, actions, texts)
        if (screen.lastRemoved == removed) screen.lastRemoved = null
    }
}
