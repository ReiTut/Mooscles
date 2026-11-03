package com.mooscles.screens.workout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.RadioGroup
import com.mooscles.designsystem.components.RadioRow
import com.mooscles.designsystem.components.SetType
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.workout_remove_set
import com.mooscles.screens.resources.workout_set_type
import com.mooscles.screens.resources.workout_type_assisted
import com.mooscles.screens.resources.workout_type_assisted_note
import com.mooscles.screens.resources.workout_type_normal
import com.mooscles.screens.resources.workout_type_warmup
import com.mooscles.screens.resources.workout_type_warmup_note
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource

/**
 * The type a set is shown with. A warm-up is a warm-up first. In the Assistance convention every
 * set is assisted, so none is marked as such (F29): an assisted set there is a normal one.
 */
internal fun setTypeOf(set: SetUi, convention: String): SetType = when {
    set.warmup -> SetType.Warmup
    set.assistance && convention != LoadConvention.ASSISTANCE.label -> SetType.Assisted
    else -> SetType.Normal
}

/**
 * The "Set type" sheet of [set]: Normal; Warm-up ("doesn't count for records"); Assisted
 * ("separate rep records"), only when [convention] is not Assistance; and "Remove set"
 * (destructive, outlined) at the end. The current type is selected.
 *
 * [onChoose] gets the flags the chosen type is written with. In the Assistance convention every
 * set stays assisted, so Normal and Warm-up keep `assistance = true` there (F29). The caller
 * writes the change and closes the sheet; [onDismiss] is a close without a choice (the scrim,
 * Back, a drag down).
 */
@Composable
internal fun SetTypeSheet(
    set: SetUi,
    convention: String,
    onChoose: (warmup: Boolean, assistance: Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val assistanceConvention = convention == LoadConvention.ASSISTANCE.label
    val type = setTypeOf(set, convention)
    MBottomSheet(onDismiss = onDismiss) {
        MText(
            stringResource(Res.string.workout_set_type),
            MTheme.type.titleMd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MTheme.space.s4)
                .padding(bottom = MTheme.space.s2)
                .semantics { heading() },
        )
        RadioGroup {
            RadioRow(
                title = stringResource(Res.string.workout_type_normal),
                selected = type == SetType.Normal,
                onSelect = { onChoose(false, assistanceConvention) },
            )
            RadioRow(
                title = stringResource(Res.string.workout_type_warmup),
                subtitle = stringResource(Res.string.workout_type_warmup_note),
                selected = type == SetType.Warmup,
                onSelect = { onChoose(true, assistanceConvention) },
            )
            if (!assistanceConvention) {
                RadioRow(
                    title = stringResource(Res.string.workout_type_assisted),
                    subtitle = stringResource(Res.string.workout_type_assisted_note),
                    selected = type == SetType.Assisted,
                    onSelect = { onChoose(false, true) },
                )
            }
        }
        MButton(
            stringResource(Res.string.workout_remove_set),
            onClick = onRemove,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MTheme.space.s4)
                .padding(top = MTheme.space.s4, bottom = MTheme.space.s6),
            variant = MButtonVariant.Destructive,
            size = MButtonSize.L,
        )
    }
}

/**
 * The type sheet of [ActiveWorkoutScreenState.typeSheetFor], over the latest [exercises]: put it
 * on the screen once, beside the list. It looks the set up by id at the moment of each choice,
 * so a change from elsewhere while it is open is not undone, and it closes by itself when the set
 * is gone.
 *
 * Choosing a type closes the sheet and writes it with the set's values as of now
 * ([SetRowEditor.changeType]: the drafts as the row would write them), not the values the sheet
 * opened with; the type already shown writes nothing. "Remove set" removes it with its undo
 * ([ActiveWorkoutScreenState.removeSet]).
 */
@Composable
internal fun SetTypeSheetHost(
    exercises: List<WorkoutExerciseUi>,
    pounds: Boolean,
    screen: ActiveWorkoutScreenState,
    actions: MoosclesActions,
) {
    val setId = screen.typeSheetFor ?: return
    val latest by rememberUpdatedState(exercises)
    val unit by rememberUpdatedState(pounds)
    val locale = currentNumberLocale()
    val entry = exercises.firstOrNull { e -> e.sets.any { it.id == setId } }
    if (entry == null) {
        LaunchedEffect(setId) { if (screen.typeSheetFor == setId) screen.typeSheetFor = null }
        return
    }
    val set = entry.sets.first { it.id == setId }

    // The entry, position and set as they are at the moment of the call.
    fun now(): Triple<WorkoutExerciseUi, Int, SetUi>? {
        val current = latest.firstOrNull { e -> e.sets.any { it.id == setId } } ?: return null
        val index = current.sets.indexOfFirst { it.id == setId }
        return Triple(current, index, current.sets[index])
    }
    fun editor(of: SetUi) = SetRowEditor(of, unit, SetSuggestion(null, null), screen, actions, locale)

    SetTypeSheet(
        set = set,
        convention = entry.exercise.convention,
        onChoose = { warmup, assistance ->
            screen.typeSheetFor = null
            val (_, _, current) = now() ?: return@SetTypeSheet
            if (current.warmup != warmup || current.assistance != assistance) editor(current).changeType(warmup, assistance)
        },
        onRemove = {
            screen.typeSheetFor = null
            val (current, index, currentSet) = now() ?: return@SetTypeSheet
            screen.removeSet(RemovedSet(current.id, index, editor(currentSet).snapshot(), index + 1), actions)
        },
        onDismiss = { if (screen.typeSheetFor == setId) screen.typeSheetFor = null },
    )
}
