package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogTest {
    @Test
    fun catalogHasUsefulOfflineBreadthAndCollisionFreeIdentities() {
        val catalog = ExerciseCatalog.exercises
        assertTrue(catalog.size >= 150, "The offline library must contain at least 150 variations")
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size, "Variation IDs must never collide")
        assertEquals(catalog.size, catalog.map { it.name }.toSet().size, "Accidental duplicate names hide variations in search")
        assertTrue(catalog.all { it.id.matches(Regex("catalog_[a-z0-9_]+")) })
        assertTrue(catalog.all { it.name.isNotBlank() && !it.custom })
        val requiredEquipment = setOf(
            Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.CABLE,
            Equipment.BODYWEIGHT, Equipment.SMITH_MACHINE,
            Equipment.SELECTORIZED_MACHINE, Equipment.PLATE_LOADED_MACHINE
        )
        requiredEquipment.forEach { equipment ->
            assertTrue(catalog.count { it.equipment == equipment } >= 10, "Missing meaningful coverage for $equipment")
        }
        MuscleGroup.entries.forEach { muscle ->
            assertTrue(catalog.any { it.muscle == muscle }, "Missing primary muscle group $muscle")
        }
    }

    @Test
    fun variantsAndLoadConventionsRemainExplicit() {
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        assertEquals(LoadConvention.TOTAL_LOAD, catalog.getValue("catalog_barbell_bench_flat").convention)
        assertEquals(LoadConvention.PER_HAND, catalog.getValue("catalog_dumbbell_bench_flat").convention)
        assertEquals(LoadConvention.ONE_SIDE, catalog.getValue("catalog_dumbbell_row_one_arm").convention)
        assertEquals(LoadConvention.BODYWEIGHT, catalog.getValue("catalog_bodyweight_pull_up").convention)
        assertEquals(LoadConvention.ASSISTANCE, catalog.getValue("catalog_selectorized_assisted_pull_up").convention)
        assertEquals(LoadConvention.ASSISTANCE, catalog.getValue("catalog_selectorized_assisted_dip").convention)
        assertFalse(catalog.getValue("catalog_barbell_bench_flat").id == catalog.getValue("catalog_barbell_bench_incline").id)
        assertFalse(catalog.getValue("catalog_bodyweight_pull_up").id == catalog.getValue("catalog_selectorized_assisted_pull_up").id)
        assertTrue(ExerciseCatalog.exercises.filter { it.equipment == Equipment.BODYWEIGHT }
            .all { it.convention == LoadConvention.BODYWEIGHT })
    }

    @Test
    fun seatedMachineOverheadPressUsesTheExistingShoulderPressIdentity() {
        val machinePress = ExerciseCatalog.exercises.single { it.id == "catalog_selectorized_shoulder_press" }
        assertEquals("Seated Overhead Press (Machine)", machinePress.name)
        assertEquals(MuscleGroup.SHOULDERS, machinePress.muscle)
        assertEquals(Equipment.SELECTORIZED_MACHINE, machinePress.equipment)
        assertEquals(LoadConvention.TOTAL_LOAD, machinePress.convention)
        assertEquals(1, ExerciseCatalog.exercises.count { it.name == machinePress.name })
        assertEquals(Equipment.PLATE_LOADED_MACHINE,
            ExerciseCatalog.exercises.single { it.id == "catalog_plate_shoulder_press" }.equipment)
    }

    @Test
    fun genuineNewImplementAndPositionVariationsHaveIndependentIdentities() {
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        val additions = mapOf(
            "catalog_barbell_squat_bulgarian" to Triple(Equipment.BARBELL, MuscleGroup.QUADRICEPS, LoadConvention.ONE_SIDE),
            "catalog_ez_bar_curl" to Triple(Equipment.BARBELL, MuscleGroup.BICEPS, LoadConvention.TOTAL_LOAD),
            "catalog_ez_bar_curl_preacher" to Triple(Equipment.BARBELL, MuscleGroup.BICEPS, LoadConvention.TOTAL_LOAD),
            "catalog_ez_bar_triceps_lying" to Triple(Equipment.BARBELL, MuscleGroup.TRICEPS, LoadConvention.TOTAL_LOAD),
            "catalog_dumbbell_bench_one_arm" to Triple(Equipment.DUMBBELL, MuscleGroup.CHEST, LoadConvention.ONE_SIDE),
            "catalog_cable_chest_fly_flat_bench" to Triple(Equipment.CABLE, MuscleGroup.CHEST, LoadConvention.TOTAL_LOAD),
            "catalog_cable_chest_fly_incline_bench" to Triple(Equipment.CABLE, MuscleGroup.CHEST, LoadConvention.TOTAL_LOAD),
            "catalog_cable_shoulder_press" to Triple(Equipment.CABLE, MuscleGroup.SHOULDERS, LoadConvention.TOTAL_LOAD),
            "catalog_plate_belt_squat" to Triple(Equipment.PLATE_LOADED_MACHINE, MuscleGroup.QUADRICEPS, LoadConvention.TOTAL_LOAD),
            "catalog_trap_bar_deadlift" to Triple(Equipment.OTHER, MuscleGroup.QUADRICEPS, LoadConvention.TOTAL_LOAD)
        )
        additions.forEach { (id, expected) ->
            val exercise = catalog.getValue(id)
            assertEquals(expected, Triple(exercise.equipment, exercise.muscle, exercise.convention), id)
        }
        assertFalse(catalog.getValue("catalog_ez_bar_curl").id == catalog.getValue("catalog_barbell_curl").id)
        assertFalse(catalog.getValue("catalog_trap_bar_deadlift").id == catalog.getValue("catalog_barbell_deadlift_conventional").id)
    }
}
