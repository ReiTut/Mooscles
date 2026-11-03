package com.mooscles.screens.workout

import com.mooscles.data.Equipment
import com.mooscles.designsystem.icons.MIcons
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EquipmentIconsTest {
    @Test
    fun matchesTheSpecTable() {
        val table = listOf(
            "Barbell" to MIcons.Barbell,
            "Dumbbell" to MIcons.Dumbbell,
            "Kettlebell" to MIcons.Kettlebell,
            "Cable" to MIcons.Cable,
            "Bodyweight" to MIcons.Body,
            "Smith machine" to MIcons.Machine,
            "Selectorized machine" to MIcons.Machine,
            "Plate-loaded machine" to MIcons.Machine,
            "Other" to MIcons.Plate,
            "Sled" to MIcons.Plate,
        )
        // Every equipment of the catalog is in the table, so a new one cannot fall to the plate unseen.
        assertEquals(Equipment.entries.map { it.label }.toSet(), table.map { it.first }.toSet() - "Sled")
        for ((label, icon) in table) assertSame(icon, EquipmentIcons.forEquipment(label), label)
    }
}
