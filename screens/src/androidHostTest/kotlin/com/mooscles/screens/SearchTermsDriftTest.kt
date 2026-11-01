package com.mooscles.screens

import com.mooscles.data.Equipment
import com.mooscles.data.MuscleGroup
import com.mooscles.data.SearchFolding
import com.mooscles.data.SearchTerms
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Portuguese search words of :shared (`SearchTerms`) repeat the labels that the screens show
 * from `values-pt-rBR/strings_catalog.xml`. Whatever label a screen shows must also find the
 * exercise, so each label, folded, has to be one of the search words of its muscle or equipment.
 */
class SearchTermsDriftTest {
    private val labels: Map<String, String> by lazy {
        val text = File("src/commonMain/composeResources/values-pt-rBR/strings_catalog.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun check(key: String, terms: List<String>) {
        val label = assertNotNull(labels[key], "No $key in values-pt-rBR/strings_catalog.xml")
        val folded = terms.map(SearchFolding::fold)
        assertTrue(SearchFolding.fold(label) in folded, "\"$label\" ($key) is not among the search words $terms")
    }

    @Test
    fun everyPortugueseMuscleLabelIsASearchWord() {
        MuscleGroup.entries.forEach { check("catalog_muscle_${it.name.lowercase()}", SearchTerms.muscle(it)) }
    }

    @Test
    fun everyPortugueseEquipmentLabelIsASearchWord() {
        Equipment.entries.forEach { check("catalog_equipment_${it.name.lowercase()}", SearchTerms.equipment(it)) }
    }
}
