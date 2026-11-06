package com.mooscles.screens.workoutdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MTextField
import com.mooscles.designsystem.components.Stepper
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.resources.ds_seconds
import com.mooscles.designsystem.resources.ds_stepper_decrease
import com.mooscles.designsystem.resources.ds_stepper_increase
import com.mooscles.designsystem.resources.settings_rest_suffix
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.RestDuration
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.detail_cancel
import com.mooscles.screens.resources.detail_rest_error
import com.mooscles.screens.resources.detail_rest_help
import com.mooscles.screens.resources.detail_save
import com.mooscles.screens.resources.workout_field_rest
import com.mooscles.screens.workout.REST_STEP_SECONDS
import com.mooscles.screens.workout.RestShortcuts
import com.mooscles.screens.workout.restShortcutTag
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.WorkoutExerciseUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.resources.Res as DsRes

/**
 * Saved-workout rest parity: arbitrary seconds can be typed with the existing fixed-colon editor, while the
 * active workout's stepper and shortcuts remain available. Unlike the active rest sheet, the value is a local
 * draft until Save; Cancel, Back and the scrim leave storage unchanged. Nothing here touches a running timer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailRestSheet(entry: WorkoutExerciseUi, actions: MoosclesActions, onDismiss: () -> Unit) {
    var value by rememberSaveable(entry.id, stateSaver = TextFieldValue.Saver) {
        val text = RestDuration.format(entry.restSeconds)
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    var rejected by rememberSaveable(entry.id) { mutableStateOf(false) }
    // No wait survives a recreation: Save closes the draft immediately and its write uses the existing command loop.
    var sent by remember(entry.id) { mutableStateOf(false) }
    val seconds = RestDuration.parse(value.text)
    val rest = stringResource(Res.string.workout_field_rest)
    val step = pluralCount(DsRes.plurals.ds_seconds, REST_STEP_SECONDS)
    val setSeconds: (Int) -> Unit = { next ->
        value = TextFieldValue(RestDuration.format(next), TextRange(0, 4))
        rejected = false
    }
    MBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
            MText(exerciseName(entry.exercise), MTheme.type.titleMd, Modifier.semantics { heading() })
            MTextField(value, { proposed ->
                val before = RestDuration.EditorValue(value.text, value.selection.start, value.selection.end)
                val next = RestDuration.edit(before, RestDuration.EditorValue(proposed.text, proposed.selection.start, proposed.selection.end))
                rejected = proposed.text != before.text && next == before
                value = TextFieldValue(next.text, TextRange(next.selectionStart, next.selectionEnd))
            }, rest, Modifier.fillMaxWidth().testTag("detail-rest-input"),
                help = stringResource(Res.string.detail_rest_help),
                error = if (rejected) stringResource(Res.string.detail_rest_error) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true, suffix = stringResource(DsRes.string.settings_rest_suffix))
            Stepper((seconds ?: entry.restSeconds).toDouble(), { setSeconds(it.toInt()) }, REST_STEP_SECONDS.toDouble(),
                min = 0.0, max = RestDuration.MAX_SECONDS.toDouble(),
                decreaseLabel = stringResource(DsRes.string.ds_stepper_decrease, rest, step),
                increaseLabel = stringResource(DsRes.string.ds_stepper_increase, rest, step),
                display = value.text, widest = RestDuration.format(RestDuration.MAX_SECONDS), contentDescription = rest)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                RestShortcuts.forEach { shortcut ->
                    MChip(RestDuration.format(shortcut), seconds == shortcut, { setSeconds(shortcut) },
                        Modifier.testTag(restShortcutTag(shortcut)), MChipKind.SetType)
                }
            }
            MButton(stringResource(Res.string.detail_save), {
                val duration = RestDuration.parse(value.text)
                if (!sent && duration != null) {
                    sent = true
                    if (duration != entry.restSeconds) actions.updateWorkoutExerciseRest(entry.id, duration)
                    onDismiss()
                }
            }, Modifier.fillMaxWidth().testTag("detail-rest-save"), enabled = !sent && seconds != null)
            MButton(stringResource(Res.string.detail_cancel), onDismiss,
                Modifier.fillMaxWidth().testTag("detail-rest-cancel"), MButtonVariant.Text)
        }
    }
}
