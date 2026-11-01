package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.NumericField
import com.mooscles.designsystem.components.NumericKind
import com.mooscles.designsystem.components.PrBadge
import com.mooscles.designsystem.components.SetCheck
import com.mooscles.designsystem.components.Stepper
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_stepper_decrease
import com.mooscles.designsystem.resources.ds_stepper_increase
import com.mooscles.designsystem.resources.gallery_sample_load_label
import com.mooscles.designsystem.resources.gallery_sample_load_name
import com.mooscles.designsystem.resources.gallery_sample_reps_error
import com.mooscles.designsystem.resources.gallery_sample_reps_label
import com.mooscles.designsystem.resources.gallery_sample_step
import com.mooscles.designsystem.resources.gallery_section_workout_inputs
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

private const val STEP = 2.5

/** The numeric field (empty with a suggestion, filled, error, disabled), the stepper, the set check and the PR badge. */
@Composable
fun WorkoutInputsPage() {
    val locale = currentNumberLocale()
    val loadLabel = stringResource(Res.string.gallery_sample_load_label)
    val repsLabel = stringResource(Res.string.gallery_sample_reps_label)
    val name = stringResource(Res.string.gallery_sample_load_name)
    val step = stringResource(Res.string.gallery_sample_step)
    // A reps field rejects 0: its error says the reps rule, not the text field's "Enter a name".
    val error = stringResource(Res.string.gallery_sample_reps_error)
    var load by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("8") }
    var invalid by remember { mutableStateOf("0") }
    var stepped by remember { mutableStateOf(20.0) }
    var done by remember { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_workout_inputs))
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.Top) {
            NumericField(load, { load = it }, NumericKind.Load, loadLabel, suggested = MoosclesNumbers.localize("82.5", locale))
            NumericField(reps, { reps = it }, NumericKind.Reps, repsLabel)
            NumericField(invalid, { invalid = it }, NumericKind.Reps, repsLabel, error = error)
            NumericField("", {}, NumericKind.Load, loadLabel, enabled = false)
        }
        Stepper(
            value = stepped,
            onValueChange = { stepped = it },
            step = STEP,
            min = 0.0,
            decreaseLabel = stringResource(Res.string.ds_stepper_decrease, name, step),
            increaseLabel = stringResource(Res.string.ds_stepper_increase, name, step),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
            SetCheck(done, { done = it }, setNumber = 1)
            SetCheck(true, {}, setNumber = 2, enabled = false)
            PrBadge()
            PrBadge(soft = true)
        }
    }
}
