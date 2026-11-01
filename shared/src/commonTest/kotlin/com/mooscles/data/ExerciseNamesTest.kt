package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseNamesTest {
    private val stableIds = ExerciseCatalog.exercises.map { it.id.removePrefix("catalog_") }

    @Test
    fun everyCatalogEntryHasAPortugueseName() {
        val missing = stableIds.filterNot { it in ExerciseNames.portuguese }
        assertTrue(missing.isEmpty(), "Catalog exercises without a Portuguese name: $missing")
    }

    @Test
    fun namesAreNotBlankAndAtMostSixtyCharacters() {
        ExerciseNames.portuguese.forEach { (id, name) ->
            assertTrue(name.isNotBlank(), "$id has a blank name")
            assertEquals(name.trim(), name, "$id has surrounding spaces")
            assertTrue(name.length <= 60, "$id is longer than 60 characters: $name")
        }
    }

    @Test
    fun namesAreUniqueAfterFolding() {
        val repeated = ExerciseNames.portuguese.entries
            .groupBy { SearchFolding.fold(it.value) }
            .filterValues { it.size > 1 }
            .mapValues { (_, entries) -> entries.map { it.key } }
        assertTrue(repeated.isEmpty(), "Names that fold to the same text: $repeated")
    }

    @Test
    fun noOrphanKeys() {
        val orphans = ExerciseNames.portuguese.keys - stableIds.toSet()
        assertTrue(orphans.isEmpty(), "Portuguese names for ids outside the catalog: $orphans")
        assertTrue(ExerciseNames.portuguese.keys.none { it.startsWith("catalog_") }, "Keys are stable ids without the prefix")
        // A repeated key would silently replace an earlier entry in the map.
        assertEquals(stableIds, portugueseNameEntries.map { it.first })
    }

    @Test
    fun specExamples() {
        val examples = mapOf(
            "barbell_bench_flat" to "Supino reto com barra",
            "barbell_back_squat_high_bar" to "Agachamento livre (barra alta)",
            "barbell_deadlift_conventional" to "Levantamento terra convencional",
            "cable_lat_pulldown_wide" to "Puxada aberta na polia",
            "cable_row_close_neutral" to "Remada baixa no triângulo",
            "cable_triceps_pushdown_rope" to "Tríceps na polia com corda",
            "selectorized_pec_fly" to "Voador",
            "selectorized_leg_extension" to "Cadeira extensora",
            "selectorized_leg_curl_lying" to "Mesa flexora",
            "selectorized_leg_curl_seated" to "Cadeira flexora",
            "plate_leg_press_45" to "Leg press 45°",
            "dumbbell_raise_lateral" to "Elevação lateral com halteres",
            "barbell_curl" to "Rosca direta com barra",
            "barbell_hip_thrust" to "Elevação pélvica com barra",
            "bodyweight_pull_up" to "Barra fixa pronada",
            "bodyweight_push_up" to "Flexão de braço",
            "ez_bar_triceps_lying" to "Tríceps testa com barra EZ",
        )
        assertEquals(17, examples.size)
        examples.forEach { (id, name) -> assertEquals(name, ExerciseNames.portuguese[id], id) }
    }

    @Test
    fun displayUsesTheCatalogNameInTheScreenLanguage() {
        // An older build stored this entry under a name the catalog has since replaced.
        val stored = "Selectorized shoulder press"
        assertEquals(
            "Seated Overhead Press (Machine)",
            ExerciseNames.display("catalog_selectorized_shoulder_press", stored, custom = false, portuguese = false)
        )
        assertEquals(
            ExerciseNames.portuguese.getValue("selectorized_shoulder_press"),
            ExerciseNames.display("catalog_selectorized_shoulder_press", stored, custom = false, portuguese = true)
        )
        assertEquals("Mesa flexora", ExerciseNames.display("catalog_selectorized_leg_curl_lying", "x", custom = false, portuguese = true))
    }

    @Test
    fun displayKeepsTheTypedNameOfACustomExercise() {
        assertEquals("Meu supino", ExerciseNames.display("custom_1", "Meu supino", custom = true, portuguese = true))
        assertEquals("Meu supino", ExerciseNames.display("custom_1", "Meu supino", custom = true, portuguese = false))
        // Even a custom row whose id looks like a catalog id keeps what the person typed.
        assertEquals("Mine", ExerciseNames.display("catalog_barbell_bench_flat", "Mine", custom = true, portuguese = true))
    }

    @Test
    fun displayKeepsTheStoredNameOfAnUnknownCatalogId() {
        // A catalog entry removed in a later build, or an id without the prefix.
        assertEquals("Old move", ExerciseNames.display("catalog_removed_move", "Old move", custom = false, portuguese = true))
        assertEquals("Old move", ExerciseNames.display("catalog_removed_move", "Old move", custom = false, portuguese = false))
        assertEquals("Bench", ExerciseNames.display("barbell_bench_flat", "Bench", custom = false, portuguese = true))
    }
}
