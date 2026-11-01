package com.mooscles.screens.workout

import androidx.compose.ui.graphics.vector.ImageVector
import com.mooscles.data.Equipment
import com.mooscles.designsystem.icons.MIcons

/** The design system icon of each catalog equipment, found by its English label (ExerciseUi.equipment). */
object EquipmentIcons {
    /** The icon for [label]; the plate for "Other" and for a label no equipment has. */
    fun forEquipment(label: String): ImageVector = when (Equipment.fromLabelOrName(label)) {
        Equipment.BARBELL -> MIcons.Barbell
        Equipment.DUMBBELL -> MIcons.Dumbbell
        Equipment.KETTLEBELL -> MIcons.Kettlebell
        Equipment.CABLE -> MIcons.Cable
        Equipment.BODYWEIGHT -> MIcons.Body
        Equipment.SMITH_MACHINE, Equipment.SELECTORIZED_MACHINE, Equipment.PLATE_LOADED_MACHINE -> MIcons.Machine
        Equipment.OTHER, null -> MIcons.Plate
    }
}
