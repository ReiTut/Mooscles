package com.mooscles.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test
    fun catalogHasUsefulOfflineBreadthAndCollisionFreeIdentities() {
        val catalog = ExerciseCatalog.exercises
        assertTrue("The offline library must contain at least 150 variations", catalog.size >= 150)
        assertEquals("Variation IDs must never collide", catalog.size, catalog.map { it.id }.toSet().size)
        assertEquals("Accidental duplicate names hide variations in search", catalog.size, catalog.map { it.name }.toSet().size)
        assertTrue(catalog.all { it.id.matches(Regex("catalog_[a-z0-9_]+")) })
        assertTrue(catalog.all { it.name.isNotBlank() && !it.custom })
        val requiredEquipment = setOf(
            Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL, Equipment.CABLE,
            Equipment.BODYWEIGHT, Equipment.SMITH_MACHINE,
            Equipment.SELECTORIZED_MACHINE, Equipment.PLATE_LOADED_MACHINE
        )
        requiredEquipment.forEach { equipment ->
            assertTrue("Missing meaningful coverage for $equipment", catalog.count { it.equipment == equipment } >= 10)
        }
        MuscleGroup.entries.forEach { muscle ->
            assertTrue("Missing primary muscle group $muscle", catalog.any { it.muscle == muscle })
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
}
