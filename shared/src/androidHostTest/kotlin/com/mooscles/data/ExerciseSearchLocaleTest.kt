package com.mooscles.data

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertTrue

/** Changes the JVM's default Locale, which only the JVM has, so this stays off iOS. */
class ExerciseSearchLocaleTest {
    private val catalog = ExerciseCatalog.exercises.associateBy { it.id }

    private fun matches(id: String, query: String) = ExerciseSearch.matches(catalog.getValue("catalog_$id"), query)

    @Test
    fun searchIsIndependentOfTheDevicesCaseMappingLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertTrue(matches("barbell_bench_incline", "INCLINE BENCH PRESS BARBELL"))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
