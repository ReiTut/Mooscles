package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mooscles.designsystem.components.MDialog
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.screens.format.durationText
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_discard
import com.mooscles.screens.resources.workout_discard_nothing
import com.mooscles.screens.resources.workout_discard_text
import com.mooscles.screens.resources.workout_discard_title
import com.mooscles.screens.resources.workout_exercises_count
import com.mooscles.screens.resources.workout_finish
import com.mooscles.screens.resources.workout_finish_pending
import com.mooscles.screens.resources.workout_finish_summary
import com.mooscles.screens.resources.workout_finish_title
import com.mooscles.screens.resources.workout_keep_training
import com.mooscles.screens.resources.workout_sets_count
import com.mooscles.screens.shell.elapsedSeconds
import com.mooscles.ui.WorkoutUi
import org.jetbrains.compose.resources.stringResource

/** What finishing or discarding [WorkoutUi] counts, with each set done as drawn. */
internal data class WorkoutCounts(val doneSets: Int, val pendingSets: Int, val doneExercises: Int) {
    companion object {
        fun of(workout: WorkoutUi, screen: ActiveWorkoutScreenState): WorkoutCounts {
            var done = 0
            var pending = 0
            var exercises = 0
            for (entry in workout.exercises) {
                val doneHere = entry.sets.count { screen.completedOf(it) }
                done += doneHere
                pending += entry.sets.size - doneHere
                if (doneHere > 0) exercises++
            }
            return WorkoutCounts(done, pending, exercises)
        }
    }
}

/**
 * "Finish workout?" (spec "Finalizar"): "9 sets across 4 exercises · 42 min." and, with sets not
 * done, "3 incomplete sets will be left out."; "Keep training" and "Finish" (primary, at the
 * end). The time is the elapsed time when the dialog opened ([nowMillis]).
 */
@Composable
internal fun FinishDialog(
    workout: WorkoutUi,
    counts: WorkoutCounts,
    nowMillis: () -> Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val elapsed = remember(workout.startedAt) { elapsedSeconds(workout.startedAt, nowMillis()) }
    val summary = stringResource(
        Res.string.workout_finish_summary,
        pluralCount(Res.plurals.workout_sets_count, counts.doneSets),
        pluralCount(Res.plurals.workout_exercises_count, counts.doneExercises),
        durationText(elapsed),
    )
    // The pending sets on a line of their own: run on after "42 min.", the count read as part of
    // the duration ("12 min. 7 / incomplete sets").
    val text = if (counts.pendingSets > 0) {
        summary + "\n" + pluralCount(Res.plurals.workout_finish_pending, counts.pendingSets)
    } else {
        summary
    }
    MDialog(
        title = stringResource(Res.string.workout_finish_title),
        text = text,
        confirmLabel = stringResource(Res.string.workout_finish),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.workout_keep_training),
    )
}

/**
 * "Discard workout?" (spec "Descartar"), destructive: "The 9 logged sets will be deleted.", or
 * "Nothing has been logged yet." without a done set; "Keep training" and "Discard" (danger).
 */
@Composable
internal fun DiscardDialog(counts: WorkoutCounts, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MDialog(
        title = stringResource(Res.string.workout_discard_title),
        text = if (counts.doneSets > 0) {
            pluralCount(Res.plurals.workout_discard_text, counts.doneSets)
        } else {
            stringResource(Res.string.workout_discard_nothing)
        },
        confirmLabel = stringResource(Res.string.workout_discard),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        dismissLabel = stringResource(Res.string.workout_keep_training),
        destructive = true,
    )
}
