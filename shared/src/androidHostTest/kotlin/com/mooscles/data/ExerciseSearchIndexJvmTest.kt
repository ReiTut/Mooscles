package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The exhaustive form of `ExerciseSearchIndexTest.everyLegacyMatchStillMatches`, on the JVM only:
 * about 130 000 legacy searches, seconds here but minutes on the iOS simulator, where commonTest
 * runs a fixed sample of the same property instead.
 */
class ExerciseSearchIndexJvmTest {
    @Test
    fun everyLegacyMatchStillMatchesForEveryNameAliasLabelAndWord() {
        val catalog = ExerciseCatalog.exercises
        val index = ExerciseSearchIndex.build(catalog.map { SearchEntry(it.id, it.name, it.muscle.label, it.equipment.label) })
        val phrases = catalog.map(Exercise::name) + ExerciseSearch.aliases.values.flatten() +
            MuscleGroup.entries.map(MuscleGroup::label) + Equipment.entries.map(Equipment::label)
        val words = phrases.flatMap { it.split(' ', '-', '(', ')', '/') }.filter(String::isNotBlank)
        // Both searches read a query only through ExerciseSearch.normalize: one query per form.
        val queries = (phrases + words).distinctBy(ExerciseSearch::normalize)
        var legacyMatches = 0
        val lost = mutableListOf<String>()
        for (exercise in catalog) for (query in queries) {
            if (!ExerciseSearch.matches(exercise, query)) continue
            legacyMatches++
            if (!index.matches(exercise.id, query)) lost += "${exercise.id} / \"$query\""
        }
        println("${catalog.size} exercises x ${queries.size} queries, $legacyMatches legacy matches")
        assertTrue(legacyMatches > catalog.size, "only $legacyMatches legacy matches")
        assertTrue(lost.isEmpty(), "Found before, missed by the index: ${lost.take(20)}")
    }
}
