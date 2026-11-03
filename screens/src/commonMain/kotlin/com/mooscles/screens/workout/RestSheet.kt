package com.mooscles.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.Stepper
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.ds_seconds
import com.mooscles.designsystem.resources.ds_stepper_decrease
import com.mooscles.designsystem.resources.ds_stepper_increase
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.RestDuration
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_field_rest
import com.mooscles.screens.resources.workout_rest_note
import com.mooscles.screens.resources.workout_rest_start_now
import com.mooscles.screens.resources.workout_rest_title
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/** The step of the rest stepper, in seconds. */
internal const val REST_STEP_SECONDS = 15

/** The rest shortcuts of the sheet, in seconds: 1:00, 1:30, 2:00 and 3:00 (D14). */
internal val RestShortcuts = listOf(60, 90, 120, 180)

/** The tag of the shortcut chip of [seconds]. */
internal fun restShortcutTag(seconds: Int) = "rest-shortcut-$seconds"

/**
 * The rest the sheet shows, apart from the one the database has until it holds the same: what a
 * step or a shortcut set ([value]), the rest last seen saved ([saved]) and the rests written and
 * not seen back yet, oldest first ([pending]). A late echo of an older write is one of [pending]
 * and does not move [value]; a saved rest nobody here wrote shows only with nothing in flight (a
 * change from elsewhere). The same idea as the set fields' drafts ([ActiveWorkoutScreenState.echo]).
 */
@Stable
internal class RestDraft(value: Int, saved: Int, pending: List<Int> = emptyList()) {
    var value: Int by mutableIntStateOf(value)
        private set
    private var saved: Int = saved
    private var pending: List<Int> = pending

    /** Shows [seconds], as a step of the stepper does; nothing is written until [commit]. */
    fun show(seconds: Int) {
        value = seconds
    }

    /**
     * Writes [value] as the exercise's rest through [write] (`updateWorkoutExerciseRest`), unless
     * it is the rest the database has or will have once every write is done.
     */
    fun commit(write: (seconds: Int) -> Unit) {
        val expected = pending.lastOrNull() ?: saved
        if (value == expected) return
        pending = pending + value
        write(value)
    }

    /**
     * The database now holds [rest]. Writes are applied in order and an emission may skip some
     * (60, 120, 60 seen as one 60), so a rest equal to the last write settles every write: nothing
     * is awaited any more, whatever was skipped. One equal to an earlier write acknowledges it and
     * the ones before it, and the later ones are still awaited. Any other rest, with nothing in
     * flight, is a change from elsewhere and shows.
     */
    fun echo(rest: Int) {
        saved = rest
        if (pending.lastOrNull() == rest) {
            pending = emptyList()
            return
        }
        val at = pending.indexOf(rest)
        if (at >= 0) {
            pending = pending.drop(at + 1)
        } else if (pending.isEmpty()) {
            value = rest
        }
    }

    companion object {
        /**
         * Saves [value] and [saved] only. The writes in flight are left out on purpose: one lost
         * with the process must not come back as awaited, or its rest would stay on screen against
         * the database's and writing it again would be skipped. After a recreation the first echo
         * shows the rest the database holds, and a step or shortcut writes again.
         */
        val Saver: Saver<RestDraft, Any> = listSaver(
            save = { listOf(it.value, it.saved) },
            restore = { RestDraft(it[0], it[1]) },
        )
    }
}

/**
 * The rest sheet of [entry] (spec D14): its name, a `Stepper` from 0:00 to 5:00 in steps of 15 s,
 * the shortcuts 1:00, 1:30, 2:00 and 3:00, the note that it applies to the exercise's next rests
 * and "Start rest now".
 *
 * Every run of steps, once it ends (`onValueChangeFinished`), and every shortcut write
 * `updateWorkoutExerciseRest(entry.id, seconds)`; the rest the database already has is not written
 * again. The value shown is the one stepped or chosen even before the database echoes it. Across
 * recreation the sheet keeps only what the database settled: a rest written and not yet seen back
 * gives way to the saved one ([RestDraft.Saver]). 0:00 turns the exercise's rest off (a set completed then starts no rest),
 * and then there is no rest to start: "Start rest now" is disabled (F19). "Start rest now" starts
 * the rest shown as the automatic rest starts one (final review, item 1): `startTimer` with the
 * entry's snapshot name and its id, so the timer names it by the entry in the screen's language
 * (after a change of language too), and closes the sheet through
 * [onDismiss], which is also the close without a choice (the scrim, Back, a drag down). Nothing
 * else here touches the rest timer: the rest running now keeps its own time.
 *
 * Put it on the screen through [RestSheetHost], which closes it when the exercise leaves the workout. With
 * [showStartNow] false the button is not there (the form for any other exercise with a rest is the overload below).
 */
@Composable
internal fun RestSheet(entry: WorkoutExerciseUi, actions: MoosclesActions, onDismiss: () -> Unit, showStartNow: Boolean = true) {
    RestSheet(
        exerciseName = exerciseName(entry.exercise),
        restSeconds = entry.restSeconds,
        key = entry.id,
        onSave = { actions.updateWorkoutExerciseRest(entry.id, it) },
        onStartNow = if (showStartNow) ({ seconds -> actions.startTimer(entry.exercise.name, seconds, entry.id) }) else null,
        onDismiss = onDismiss,
    )
}

/**
 * The sheet itself (plan decision 15), for any exercise with a rest: [exerciseName] under the title, the [restSeconds]
 * the database has now, [key] to tell its draft from another entry's, [onSave] for every write and [onStartNow] for
 * "Start rest now", which is left out of the sheet when null (the routine editor's, where nothing is running). The
 * routine editor writes `updateRoutineExerciseRest` through [onSave].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RestSheet(
    exerciseName: String,
    restSeconds: Int,
    key: Any,
    onSave: (seconds: Int) -> Unit,
    onStartNow: ((seconds: Int) -> Unit)?,
    onDismiss: () -> Unit,
    note: String = stringResource(Res.string.workout_rest_note),
) {
    val draft = rememberSaveable(key, saver = RestDraft.Saver) { RestDraft(restSeconds, restSeconds) }
    LaunchedEffect(draft, restSeconds) { draft.echo(restSeconds) }
    val seconds = draft.value
    val rest = stringResource(Res.string.workout_field_rest)
    val step = pluralCount(DsRes.plurals.ds_seconds, REST_STEP_SECONDS)
    MBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MTheme.space.s4)
                .padding(bottom = MTheme.space.s6),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }) {
                MText(stringResource(Res.string.workout_rest_title), MTheme.type.titleMd)
                MText(exerciseName, MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
            }
            Stepper(
                value = seconds.toDouble(),
                onValueChange = { draft.show(it.toInt()) },
                step = REST_STEP_SECONDS.toDouble(),
                min = 0.0,
                max = RestDuration.MAX_SECONDS.toDouble(),
                decreaseLabel = stringResource(DsRes.string.ds_stepper_decrease, rest, step),
                increaseLabel = stringResource(DsRes.string.ds_stepper_increase, rest, step),
                display = RestDuration.format(seconds),
                onValueChangeFinished = { draft.commit(onSave) },
            )
            FlowRow(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(MTheme.targets.gap, Alignment.CenterHorizontally),
                // Wrapped rows of chips (large text, a narrow screen) keep the gap between targets too.
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            ) {
                for (shortcut in RestShortcuts) {
                    MChip(
                        label = RestDuration.format(shortcut),
                        selected = seconds == shortcut,
                        onClick = {
                            draft.show(shortcut)
                            draft.commit(onSave)
                        },
                        modifier = Modifier.testTag(restShortcutTag(shortcut)),
                        kind = MChipKind.SetType,
                    )
                }
            }
            MText(
                note,
                MTheme.type.bodyMd,
                color = MTheme.colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onStartNow != null) {
                MButton(
                    stringResource(Res.string.workout_rest_start_now),
                    onClick = {
                        onStartNow(draft.value)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = MButtonVariant.Tonal,
                    size = MButtonSize.L,
                    enabled = seconds > 0,
                )
            }
        }
    }
}

/**
 * The rest sheet of the entry [entryId] over the latest [exercises], or nothing while [entryId] is
 * null. Put it on the screen once, beside the list, not in a card: it stays open while the card
 * that opened it leaves the lazy list (a scroll, an IME resize). The caller keeps [entryId] (in
 * saveable state, so the sheet comes back after recreation) and clears it in [onClose], which runs
 * when the sheet is closed and also, by itself, when the entry is no longer in [exercises] (removed
 * here or elsewhere). Pass the workout's exercises once they are loaded: an empty list closes it.
 */
@Composable
internal fun RestSheetHost(
    exercises: List<WorkoutExerciseUi>,
    entryId: String?,
    actions: MoosclesActions,
    onClose: () -> Unit,
) {
    val id = entryId ?: return
    val close by rememberUpdatedState(onClose)
    val entry = exercises.firstOrNull { it.id == id }
    if (entry == null) {
        LaunchedEffect(id) { close() }
        return
    }
    RestSheet(entry, actions, onDismiss = { close() })
}
