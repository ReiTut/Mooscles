package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseSearchTest {
    private val catalog = ExerciseCatalog.exercises.associateBy { it.id }

    private fun matches(id: String, query: String) = ExerciseSearch.matches(catalog.getValue("catalog_$id"), query)

    @Test
    fun shoulderAndOverheadSynonymsResolveToExistingVariationWithoutNewEntries() {
        listOf("Seated Overhead Press (Machine)", "machine shoulder press", "Selectorized shoulder press")
            .forEach { assertTrue(matches("selectorized_shoulder_press", it), it) }
        assertTrue(matches("dumbbell_press_seated", "seated overhead DB press"))
        assertTrue(matches("barbell_seated_press", "overhead press seated barbell"))
        assertTrue(matches("barbell_overhead_press", "military press"))
        assertTrue(matches("plate_shoulder_press", "overhead press plate loaded"))
        assertFalse(matches("selectorized_shoulder_press", "dumbbell shoulder press"))
        assertFalse(matches("plate_shoulder_press", "selectorized shoulder press"))
    }

    @Test
    fun referenceExerciseNamesAreSearchableRegardlessOfPunctuationOrWordOrder() {
        assertTrue(matches("barbell_bench_incline", "Incline Bench Press (Barbell)"))
        assertTrue(matches("selectorized_pec_fly", "Chest Fly (Pec Deck)"))
        assertTrue(matches("selectorized_pec_fly", "pecdeck chest flyes"))
        assertTrue(matches("selectorized_pec_fly", "butterfly machine"))
        assertTrue(matches("selectorized_reverse_fly", "rear delt fly machine"))
        assertFalse(matches("selectorized_pec_fly", "rear delt"))
    }

    @Test
    fun commonAliasesRetainTheSpecificEquipmentVariation() {
        assertTrue(matches("trap_bar_deadlift", "hex bar deadlift"))
        assertFalse(matches("barbell_deadlift_conventional", "hex bar deadlift"))
        assertTrue(matches("ez_bar_triceps_lying", "EZ bar skullcrushers"))
        assertFalse(matches("barbell_triceps_lying", "EZ bar skullcrusher"))
        assertTrue(matches("dumbbell_bench_one_arm", "single arm dumbbell bench press"))
        assertTrue(matches("bodyweight_pull_up_wide", "wide pullup"))
        assertTrue(matches("cable_lat_pulldown_wide", "wide lat pull down"))
        assertTrue(matches("cable_curl_behind_body", "Bayesian cable curl"))
        assertFalse(matches("dumbbell_curl_standing", "Bayesian cable curl"))
    }

    @Test
    fun metadataAndCustomNamesRemainSearchableUsingTheSameContract() {
        val custom = Exercise("my-custom", "Élite / press", MuscleGroup.SHOULDERS, Equipment.OTHER, custom = true)
        assertTrue(ExerciseSearch.matches(custom, "elite press"))
        assertTrue(ExerciseSearch.matches(custom, "shoulders other"))
        assertTrue(ExerciseSearch.matches(custom, "    "))
        assertFalse(ExerciseSearch.matches(custom, "machine shoulder press"))
        val queries = listOf("seated machine shoulder press", "Chest Fly (Pec Deck)", "incline bench barbell")
        queries.forEach { query ->
            val domainMatches = ExerciseCatalog.exercises.filter { ExerciseSearch.matches(it, query) }.map { it.id }
            val uiMatches = ExerciseCatalog.exercises.filter {
                ExerciseSearch.matches(it.id, it.name, it.muscle.name, it.equipment.name, query)
            }.map { it.id }
            assertEquals(domainMatches, uiMatches, query)
        }
    }

    @Test
    fun renamedCatalogSearchStillWorksWhileAnOlderDisplayNameIsLoaded() {
        assertTrue(ExerciseSearch.matches(
            "catalog_selectorized_shoulder_press", "Selectorized shoulder press", "Shoulders", "Selectorized machine",
            "seated overhead press machine"
        ))
    }
}
