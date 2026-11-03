package com.mooscles.data

import com.mooscles.data.MuscleRegion.CHEST
import com.mooscles.data.MuscleRegion.GLUTES
import com.mooscles.data.MuscleRegion.HAMSTRINGS
import com.mooscles.data.MuscleRegion.LATS
import com.mooscles.data.MuscleRegion.QUADRICEPS
import com.mooscles.data.MuscleRegion.TRAPS
import com.mooscles.data.MuscleRegion.TRICEPS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExerciseAnatomyTest {
    private val catalog = ExerciseCatalog.exercises
    private val stableIds = catalog.map { it.id.removePrefix("catalog_") }

    @Test
    fun everyCatalogExerciseHasAnatomy() {
        val missing = stableIds.filterNot { it in ExerciseAnatomy.catalog }
        assertTrue(missing.isEmpty(), "Catalog exercises without anatomy: $missing")
    }

    @Test
    fun noOrphanKeys() {
        val orphans = ExerciseAnatomy.catalog.keys - stableIds.toSet()
        assertTrue(orphans.isEmpty(), "Anatomy keys that are not catalog stable ids: $orphans")
        assertTrue(ExerciseAnatomy.catalog.keys.none { it.startsWith("catalog_") }, "Keys are stable ids without the prefix")
    }

    @Test
    fun tableFollowsTheCatalogOrderWithoutDuplicates() {
        // A repeated key would silently replace an earlier entry in the map.
        assertEquals(stableIds, anatomyEntries.map { it.first })
    }

    @Test
    fun primaryIsNeverEmpty() {
        val empty = ExerciseAnatomy.catalog.filterValues { it.primary.isEmpty() }.keys
        assertTrue(empty.isEmpty(), "Entries without a primary region: $empty")
    }

    @Test
    fun noRegionTwiceInAnEntry() {
        ExerciseAnatomy.catalog.forEach { (id, anatomy) ->
            assertEquals(anatomy.primary.size, anatomy.primary.toSet().size, "$id repeats a primary region")
            assertEquals(anatomy.secondary.size, anatomy.secondary.toSet().size, "$id repeats a secondary region")
            val both = anatomy.primary.toSet() intersect anatomy.secondary.toSet()
            assertTrue(both.isEmpty(), "$id lists $both as both primary and secondary")
        }
    }

    @Test
    fun firstPrimaryFitsTheMuscleGroup() {
        catalog.forEach { exercise ->
            val anatomy = ExerciseAnatomy.catalog.getValue(exercise.id.removePrefix("catalog_"))
            val allowed = ExerciseAnatomy.allowedPrimary.getValue(exercise.muscle)
            assertTrue(
                anatomy.primary.first() in allowed,
                "${exercise.id} (${exercise.muscle}) starts with ${anatomy.primary.first()}, outside $allowed"
            )
        }
    }

    @Test
    fun everyMuscleGroupHasAnAllowedSet() {
        MuscleGroup.entries.forEach { group ->
            val allowed = ExerciseAnatomy.allowedPrimary[group]
            assertNotNull(allowed, "$group has no allowed primary set")
            assertTrue(allowed.isNotEmpty(), "$group allows no region")
        }
        assertEquals(MuscleRegion.entries.toSet(), ExerciseAnatomy.allowedPrimary.getValue(MuscleGroup.FULL_BODY))
        MuscleGroup.entries.filter { it != MuscleGroup.FULL_BODY }.forEach { group ->
            val region = ExerciseAnatomy.forMuscleGroup(group)?.primary?.single()
            assertNotNull(region, "$group has no custom region")
            assertTrue(region in ExerciseAnatomy.allowedPrimary.getValue(group), "$group custom region $region is not allowed")
        }
    }

    @Test
    fun customUsesTheMuscleGroupRegion() {
        assertEquals(Anatomy(listOf(CHEST), emptyList()), ExerciseAnatomy.forExercise("x", "Chest"))
        assertEquals(Anatomy(listOf(LATS), emptyList()), ExerciseAnatomy.forExercise("custom_1", "Back"))
        // The whole mapping is pinned, so swapping one group's region (say Quadriceps to adductors) fails.
        val expected = mapOf(
            MuscleGroup.CHEST to CHEST,
            MuscleGroup.BACK to LATS,
            MuscleGroup.SHOULDERS to MuscleRegion.SHOULDERS,
            MuscleGroup.BICEPS to MuscleRegion.BICEPS,
            MuscleGroup.TRICEPS to TRICEPS,
            MuscleGroup.QUADRICEPS to QUADRICEPS,
            MuscleGroup.HAMSTRINGS to HAMSTRINGS,
            MuscleGroup.GLUTES to GLUTES,
            MuscleGroup.CALVES to MuscleRegion.CALVES,
            MuscleGroup.CORE to MuscleRegion.ABDOMINALS,
            MuscleGroup.FOREARMS to MuscleRegion.FOREARMS,
            MuscleGroup.FULL_BODY to null
        ).mapValues { (_, region) -> region?.let { Anatomy(listOf(it), emptyList()) } }
        assertEquals(expected, MuscleGroup.entries.associateWith { ExerciseAnatomy.forMuscleGroup(it) })
        assertEquals(expected, MuscleGroup.entries.associateWith { ExerciseAnatomy.forExercise("custom_1", it.label) })
    }

    /** Final review, item 9: the group is read as the rest of the app reads it, by label or enum name. */
    @Test
    fun customAcceptsTheEnumNameToo() {
        assertEquals(Anatomy(listOf(CHEST), emptyList()), ExerciseAnatomy.forExercise("custom_1", "CHEST"))
        assertEquals(Anatomy(listOf(MuscleRegion.ABDOMINALS), emptyList()), ExerciseAnatomy.forExercise("custom_1", "CORE"))
        assertNull(ExerciseAnatomy.forExercise("custom_1", "FULL_BODY"))
    }

    /** The one label-or-name parser of the muscle groups and the equipment (final review, item 9). */
    @Test
    fun groupsAndEquipmentParseByLabelOrName() {
        for (group in MuscleGroup.entries) {
            assertEquals(group, MuscleGroup.fromLabelOrName(group.label))
            assertEquals(group, MuscleGroup.fromLabelOrName(group.name))
        }
        for (equipment in Equipment.entries) {
            assertEquals(equipment, Equipment.fromLabelOrName(equipment.label))
            assertEquals(equipment, Equipment.fromLabelOrName(equipment.name))
        }
        for (convention in LoadConvention.entries) {
            assertEquals(convention, LoadConvention.fromLabelOrName(convention.label))
            assertEquals(convention, LoadConvention.fromLabelOrName(convention.name))
        }
        assertNull(MuscleGroup.fromLabelOrName("chest"))
        assertNull(Equipment.fromLabelOrName(""))
        assertNull(LoadConvention.fromLabelOrName("total load"))
    }

    @Test
    fun prefixedIdOutsideTheCatalogFallsBackToTheGroup() {
        assertEquals(Anatomy(listOf(TRICEPS), emptyList()), ExerciseAnatomy.forExercise("catalog_not_a_real_exercise", "Triceps"))
    }

    @Test
    fun fullBodyCustomHasNoRegion() {
        assertNull(ExerciseAnatomy.forMuscleGroup(MuscleGroup.FULL_BODY))
        assertNull(ExerciseAnatomy.forExercise("x", "Full body"))
    }

    @Test
    fun unknownLabelHasNoAnatomy() {
        assertNull(ExerciseAnatomy.forExercise("x", "Elbows"))
        assertNull(ExerciseAnatomy.forExercise("x", ""))
    }

    @Test
    fun highBarSquatIsQuadricepsWithGlutes() {
        val anatomy = assertNotNull(ExerciseAnatomy.forExercise("catalog_barbell_back_squat_high_bar", "Quadriceps"))
        assertEquals(QUADRICEPS, anatomy.primary.first())
        assertTrue(GLUTES in anatomy.secondary)
    }

    @Test
    fun lyingLegCurlIsHamstrings() {
        val anatomy = assertNotNull(ExerciseAnatomy.forExercise("catalog_selectorized_leg_curl_lying", "Hamstrings"))
        assertEquals(HAMSTRINGS, anatomy.primary.first())
    }

    @Test
    fun shrugIsTraps() {
        // The label is deliberately wrong: a catalog id wins over the group.
        val anatomy = assertNotNull(ExerciseAnatomy.forExercise("catalog_barbell_shrug", "Chest"))
        assertEquals(TRAPS, anatomy.primary.first())
    }

    @Test
    fun benchPressIsChestWithTriceps() {
        val anatomy = assertNotNull(ExerciseAnatomy.forExercise("catalog_barbell_bench_flat", "Chest"))
        assertEquals(CHEST, anatomy.primary.first())
        assertTrue(TRICEPS in anatomy.secondary)
    }
}
