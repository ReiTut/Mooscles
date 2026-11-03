package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.catalog_convention_added_weight
import com.mooscles.screens.resources.catalog_convention_assistance
import com.mooscles.screens.resources.catalog_convention_caption_added_weight
import com.mooscles.screens.resources.catalog_convention_caption_assistance
import com.mooscles.screens.resources.catalog_convention_caption_one_side
import com.mooscles.screens.resources.catalog_convention_caption_per_hand
import com.mooscles.screens.resources.catalog_convention_explanation_added_weight
import com.mooscles.screens.resources.catalog_convention_explanation_assistance
import com.mooscles.screens.resources.catalog_convention_explanation_one_side
import com.mooscles.screens.resources.catalog_convention_explanation_per_hand
import com.mooscles.screens.resources.catalog_convention_explanation_total_load
import com.mooscles.screens.resources.catalog_convention_one_side
import com.mooscles.screens.resources.catalog_convention_per_hand
import com.mooscles.screens.resources.catalog_convention_total_load
import com.mooscles.screens.resources.catalog_equipment_barbell
import com.mooscles.screens.resources.catalog_equipment_bodyweight
import com.mooscles.screens.resources.catalog_equipment_cable
import com.mooscles.screens.resources.catalog_equipment_dumbbell
import com.mooscles.screens.resources.catalog_equipment_kettlebell
import com.mooscles.screens.resources.catalog_equipment_other
import com.mooscles.screens.resources.catalog_equipment_plate_loaded_machine
import com.mooscles.screens.resources.catalog_equipment_selectorized_machine
import com.mooscles.screens.resources.catalog_equipment_smith_machine
import com.mooscles.screens.resources.catalog_muscle_back
import com.mooscles.screens.resources.catalog_muscle_biceps
import com.mooscles.screens.resources.catalog_muscle_calves
import com.mooscles.screens.resources.catalog_muscle_chest
import com.mooscles.screens.resources.catalog_muscle_core
import com.mooscles.screens.resources.catalog_muscle_forearms
import com.mooscles.screens.resources.catalog_muscle_full_body
import com.mooscles.screens.resources.catalog_muscle_glutes
import com.mooscles.screens.resources.catalog_muscle_hamstrings
import com.mooscles.screens.resources.catalog_muscle_quadriceps
import com.mooscles.screens.resources.catalog_muscle_shoulders
import com.mooscles.screens.resources.catalog_muscle_triceps
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// The UI contract carries the catalog's fixed lists as their English labels (ExerciseUi.muscle,
// .equipment, .convention), the values stored in the database. These functions find the enum by
// that label and read the localized text. A label no enum has (an old or unknown value) is shown
// as it is, so nothing is ever blank.

/** The localized name of the muscle group whose English label (or enum name) is [label]. */
@Composable
fun muscleLabel(label: String): String =
    MuscleGroup.fromLabelOrName(label)?.let { stringResource(it.text()) } ?: label

/** The localized name of the equipment whose English label (or enum name) is [label]. */
@Composable
fun equipmentLabel(label: String): String =
    Equipment.fromLabelOrName(label)?.let { stringResource(it.text()) } ?: label

/** The localized name of the load convention whose English label (or enum name) is [label]. */
@Composable
fun conventionLabel(label: String): String =
    convention(label)?.let { stringResource(it.text()) } ?: label

/**
 * The one-line caption shown under an exercise whose load is not logged as the total load, such
 * as "Per hand: log one dumbbell's load."; null for the total load and for an unknown label.
 */
@Composable
fun conventionCaption(label: String): String? =
    convention(label)?.caption()?.let { stringResource(it) }

/** The full explanation of the load convention whose English label is [label]. */
@Composable
fun conventionExplanation(label: String): String =
    convention(label)?.let { stringResource(it.explanation()) } ?: label

private fun convention(label: String): LoadConvention? = LoadConvention.fromLabelOrName(label)

private fun MuscleGroup.text(): StringResource = when (this) {
    MuscleGroup.CHEST -> Res.string.catalog_muscle_chest
    MuscleGroup.BACK -> Res.string.catalog_muscle_back
    MuscleGroup.SHOULDERS -> Res.string.catalog_muscle_shoulders
    MuscleGroup.BICEPS -> Res.string.catalog_muscle_biceps
    MuscleGroup.TRICEPS -> Res.string.catalog_muscle_triceps
    MuscleGroup.QUADRICEPS -> Res.string.catalog_muscle_quadriceps
    MuscleGroup.HAMSTRINGS -> Res.string.catalog_muscle_hamstrings
    MuscleGroup.GLUTES -> Res.string.catalog_muscle_glutes
    MuscleGroup.CALVES -> Res.string.catalog_muscle_calves
    MuscleGroup.CORE -> Res.string.catalog_muscle_core
    MuscleGroup.FOREARMS -> Res.string.catalog_muscle_forearms
    MuscleGroup.FULL_BODY -> Res.string.catalog_muscle_full_body
}

private fun Equipment.text(): StringResource = when (this) {
    Equipment.BARBELL -> Res.string.catalog_equipment_barbell
    Equipment.DUMBBELL -> Res.string.catalog_equipment_dumbbell
    Equipment.KETTLEBELL -> Res.string.catalog_equipment_kettlebell
    Equipment.CABLE -> Res.string.catalog_equipment_cable
    Equipment.BODYWEIGHT -> Res.string.catalog_equipment_bodyweight
    Equipment.SMITH_MACHINE -> Res.string.catalog_equipment_smith_machine
    Equipment.SELECTORIZED_MACHINE -> Res.string.catalog_equipment_selectorized_machine
    Equipment.PLATE_LOADED_MACHINE -> Res.string.catalog_equipment_plate_loaded_machine
    Equipment.OTHER -> Res.string.catalog_equipment_other
}

private fun LoadConvention.text(): StringResource = when (this) {
    LoadConvention.TOTAL_LOAD -> Res.string.catalog_convention_total_load
    LoadConvention.PER_HAND -> Res.string.catalog_convention_per_hand
    LoadConvention.ONE_SIDE -> Res.string.catalog_convention_one_side
    LoadConvention.BODYWEIGHT -> Res.string.catalog_convention_added_weight
    LoadConvention.ASSISTANCE -> Res.string.catalog_convention_assistance
}

private fun LoadConvention.caption(): StringResource? = when (this) {
    LoadConvention.TOTAL_LOAD -> null
    LoadConvention.PER_HAND -> Res.string.catalog_convention_caption_per_hand
    LoadConvention.ONE_SIDE -> Res.string.catalog_convention_caption_one_side
    LoadConvention.BODYWEIGHT -> Res.string.catalog_convention_caption_added_weight
    LoadConvention.ASSISTANCE -> Res.string.catalog_convention_caption_assistance
}

private fun LoadConvention.explanation(): StringResource = when (this) {
    LoadConvention.TOTAL_LOAD -> Res.string.catalog_convention_explanation_total_load
    LoadConvention.PER_HAND -> Res.string.catalog_convention_explanation_per_hand
    LoadConvention.ONE_SIDE -> Res.string.catalog_convention_explanation_one_side
    LoadConvention.BODYWEIGHT -> Res.string.catalog_convention_explanation_added_weight
    LoadConvention.ASSISTANCE -> Res.string.catalog_convention_explanation_assistance
}
