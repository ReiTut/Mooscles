package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseSearchIndexTest {
    private val catalog = ExerciseCatalog.exercises
    private val entries = catalog.map { SearchEntry(it.id, it.name, it.muscle.label, it.equipment.label) }
    private val index = ExerciseSearchIndex.build(entries)

    private fun found(query: String) = index.matching(query).toSet()

    @Test
    fun portugueseQueriesFindTheirExercises() {
        // (query, must contain, must not contain), stable ids without the prefix.
        val table = listOf(
            Triple("supino inclinado halter", "dumbbell_bench_incline", null),
            Triple("puxada", "cable_lat_pulldown_wide", null),
            Triple("lat pulldown", "cable_lat_pulldown_wide", null),
            Triple("mesa flexora", "selectorized_leg_curl_lying", "selectorized_leg_curl_seated"),
            Triple("cadeira flexora", "selectorized_leg_curl_seated", "selectorized_leg_curl_lying"),
            Triple("eleva", "dumbbell_raise_lateral", null),
            Triple("terra", "barbell_deadlift_conventional", null),
            Triple("rdl", "barbell_deadlift_romanian", null),
            Triple("voador", "selectorized_pec_fly", null),
            Triple("peito halter", "dumbbell_bench_flat", null),
            Triple("puxador", "cable_lat_pulldown_wide", null),
            Triple("puxador frente", "cable_lat_pulldown_wide", null),
            Triple("puxada alta", "cable_lat_pulldown_wide", "barbell_upright_row"),
            Triple("puxada frente", "cable_lat_pulldown_wide", null),
            Triple("pulldown", "cable_lat_pulldown_wide", null),
            Triple("puxador supinado", "cable_lat_pulldown_underhand", "cable_lat_pulldown_wide"),
            Triple("elevação de quadril", "barbell_hip_thrust", null),
            Triple("elevacao de quadril", "plate_hip_thrust", null),
            Triple("afundo búlgaro", "dumbbell_squat_bulgarian", null),
            Triple("agachamento sumô", "dumbbell_deadlift_sumo", null),
            Triple("flexora deitada", "selectorized_leg_curl_lying", "selectorized_leg_curl_seated"),
            Triple("flexora sentada", "selectorized_leg_curl_seated", "selectorized_leg_curl_lying"),
            Triple("remada australiana", "bodyweight_row_inverted", null),
            Triple("deltoide posterior", "selectorized_reverse_fly", "selectorized_leg_curl_lying"),
            Triple("deltoide posterior", "dumbbell_fly_reverse", null),
        )
        table.forEach { (query, expected, excluded) ->
            val result = found(query)
            assertTrue("catalog_$expected" in result, "\"$query\" should find $expected")
            if (excluded != null) assertFalse("catalog_$excluded" in result, "\"$query\" should not find $excluded")
        }
    }

    @Test
    fun wordsOfTwoDifferentAliasesDoNotCombine() {
        // "Voador" is the Portuguese name and "Peck deck" an alias: no single candidate has both.
        assertTrue(index.matches("catalog_selectorized_pec_fly", "voador"))
        assertTrue(index.matches("catalog_selectorized_pec_fly", "peck deck"))
        assertFalse(index.matches("catalog_selectorized_pec_fly", "voador peck"))
    }

    @Test
    fun metadataWorksInBothLanguages() {
        assertTrue("catalog_cable_row_close_neutral" in found("costas polia"))
        assertTrue("catalog_cable_row_close_neutral" in found("back cable"))
    }

    @Test
    fun everyLegacyMatchStillMatches() {
        // A fixed sample that keeps the iOS simulator run short; ExerciseSearchIndexJvmTest
        // (androidHostTest) checks every name, alias, label and word.
        val names = catalog.map(Exercise::name)
        val aliases = ExerciseSearch.aliases.values.flatten()
        val words = (names + aliases).flatMap { it.split(' ', '-', '(', ')', '/') }.filter(String::isNotBlank)
            .distinctBy(ExerciseSearch::normalize)
        val sampled = names.filterIndexed { i, _ -> i % 4 == 0 } + aliases.filterIndexed { i, _ -> i % 3 == 0 } +
            words.filterIndexed { i, _ -> i % 4 == 0 }
        val variants = sampled.filterIndexed { i, _ -> i % 5 == 0 }.flatMap { listOf(accented(it), it.uppercase()) }
        val partial = words.filterIndexed { i, _ -> i % 7 == 0 }.filter { it.length > 4 }.map { it.take(4) }
        val fixed = listOf(
            "DB curl", "pull-ups", "skullcrushers", "pecdeck flyes", "single arm row", "wide lat pull down",
            "Chest", "Selectorized machine",
        )
        // Both searches read a query only through ExerciseSearch.normalize: one query per form.
        val queries = (sampled + variants + partial + fixed).distinctBy(ExerciseSearch::normalize)
        assertTrue(queries.size in 150..400, "${queries.size} sampled queries")
        println("${catalog.size} exercises x ${queries.size} sampled queries")
        var legacyMatches = 0
        val lost = mutableListOf<String>()
        for (exercise in catalog) for (query in queries) {
            if (!ExerciseSearch.matches(exercise, query)) continue
            legacyMatches++
            if (!index.matches(exercise.id, query)) lost += "${exercise.id} / \"$query\""
        }
        // The check means something only if the old search matches often.
        assertTrue(legacyMatches > catalog.size, "only $legacyMatches legacy matches")
        assertTrue(lost.isEmpty(), "Found before, missed by the index: ${lost.take(20)}")
    }

    private fun accented(text: String): String = text.map {
        when (it) {
            'a' -> 'á'; 'e' -> 'é'; 'o' -> 'õ'; 'c' -> 'ç'; 'u' -> 'ü'
            else -> it
        }
    }.joinToString("")

    @Test
    fun customNamedLikeACatalogEntryIsFoundAlongsideIt() {
        val custom = SearchEntry("custom_supino", "Supino reto com barra", MuscleGroup.CHEST.label, Equipment.BARBELL.label)
        val withCustom = ExerciseSearchIndex.build(entries + custom)
        val result = withCustom.matching("supino reto")
        assertTrue("custom_supino" in result)
        assertTrue("catalog_barbell_bench_flat" in result)
    }

    @Test
    fun blankQueryMatchesAll() {
        val ids = entries.map(SearchEntry::id)
        assertEquals(ids, index.matching(""))
        assertEquals(ids, index.matching("   "))
        assertTrue(index.matches("catalog_barbell_bench_flat", " "))
    }

    @Test
    fun orderFollowsTheEntries() {
        val reversed = entries.reversed()
        val result = ExerciseSearchIndex.build(reversed).matching("supino")
        assertTrue(result.size > 1)
        assertEquals(reversed.map(SearchEntry::id).filter { it in result }, result)
    }

    @Test
    fun aliasKeysAreCatalogIds() {
        val ids = catalog.map(Exercise::id).toSet()
        val orphans = ExerciseAliases.portuguese.keys - ids
        assertTrue(orphans.isEmpty(), "Portuguese aliases for ids outside the catalog: $orphans")
        ExerciseAliases.portuguese.forEach { (id, aliases) ->
            assertTrue(aliases.isNotEmpty() && aliases.all(String::isNotBlank), "$id has an empty alias")
        }
    }

    @Test
    fun specAliasesAreListed() {
        // The gym words of spec D13, each an alias of (or part of an alias of) some exercise.
        val spec = listOf(
            "supino", "voador", "peck deck", "crucifixo", "puxada", "pulley", "remada baixa", "terra", "stiff",
            "RDL", "cadeira extensora", "mesa flexora", "cadeira flexora", "agachamento", "afundo", "passada",
            "búlgaro", "panturrilha", "rosca direta", "rosca martelo", "rosca scott", "tríceps testa",
            "tríceps corda", "desenvolvimento", "elevação lateral", "encolhimento", "elevação pélvica",
            "barra fixa", "paralelas", "flexão", "graviton", "hack",
        )
        val aliases = ExerciseAliases.portuguese.values.flatten().map(SearchFolding::fold)
        val missing = spec.filter { term -> aliases.none { SearchFolding.fold(term) in it } }
        assertTrue(missing.isEmpty(), "Spec aliases not listed: $missing")
    }
}
