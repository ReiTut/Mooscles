package com.mooscles.screens.exercise

import com.mooscles.data.ExerciseCatalog
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reads the cue files of the repository (spec D8), as [com.mooscles.screens.StringsParityTest]
 * reads the string XML: the same exercises and counts in both languages, short cues, no orphan,
 * no exclamation, emoji or health promise, and cues for every catalog exercise (spec D21).
 */
class ExerciseCuesFilesTest {
    private val dir = File("src/commonMain/composeResources/files/exercise_cues")

    private fun text(name: String) = File(dir, name).readText()

    private val en by lazy { ExerciseCues.parse(text("en.json")) }
    private val pt by lazy { ExerciseCues.parse(text("pt-BR.json")) }
    private val both get() = listOf("en" to en, "pt-BR" to pt)

    private val stableIds = ExerciseCatalog.exercises.map { it.id.removePrefix("catalog_") }

    @Test
    fun sameKeysAndCountsInBothLanguages() {
        assertEquals(en.keys, pt.keys)
        for ((id, cues) in en) assertEquals(cues.size, pt.getValue(id).size, "cue count of $id")
    }

    @Test
    fun atMostFourCuesOfAtMost110Characters() {
        for ((language, map) in both) for ((id, cues) in map) {
            assertTrue(cues.size <= 4, "$language $id has ${cues.size} cues")
            for (cue in cues) assertTrue(cue.length <= 110, "$language $id: \"$cue\" has ${cue.length} characters")
        }
    }

    @Test
    fun noEmptyCueAndNoOrphanKey() {
        val known = stableIds.toSet()
        for ((language, map) in both) {
            for ((id, cues) in map) {
                assertTrue(id in known, "$language $id is not a catalog exercise")
                assertTrue(cues.isNotEmpty(), "$language $id has an empty list")
                for (cue in cues) assertTrue(cue.isNotBlank() && cue == cue.trim(), "$language $id has a blank or untrimmed cue")
            }
        }
        // One key per line in the files, so a key written twice (which the parser would merge) shows here.
        for (name in listOf("en.json", "pt-BR.json")) {
            val keys = Regex("^\\s*\"([^\"]+)\"\\s*:", RegexOption.MULTILINE).findAll(text(name)).map { it.groupValues[1] }.toList()
            assertEquals(keys.size, keys.toSet().size, "$name repeats a key: ${keys.groupBy { it }.filter { it.value.size > 1 }.keys}")
        }
    }

    @Test
    fun noExclamationEmojiOrHealthClaims() {
        for ((language, map) in both) for ((id, cues) in map) for (cue in cues) {
            assertFalse('!' in cue, "$language $id has an exclamation mark: \"$cue\"")
            assertFalse(hasEmoji(cue), "$language $id has an emoji: \"$cue\"")
            assertFalse(HealthClaims.matches(cue), "$language $id makes a health claim: \"$cue\"")
        }
    }

    /** The filter itself (pre-flight F17): whole words only, so common gym words pass. */
    @Test
    fun healthClaimFilterMatchesWholeWordsOnly() {
        for (claim in listOf(
            "Cura a dor nas costas.", "Ajuda a curar o joelho.", "Bom tratamento para o ombro.", "Evita lesão.",
            "Previne lesões.", "Indicado pelo médico.", "Recomendação médica.", "Cures back pain.", "Treats knee pain.",
            "Prevents injury.", "Avoids injuries.", "A medical classic.",
            // Inflections of the same roots (task 11 review, minor 1).
            "Ombro tratado.", "Trate o joelho.", "Tratando a lombar.", "Joelho curado.", "Curando o ombro.",
            "Pode lesionar o ombro.", "Ombro lesionado.", "Efeito curativo.", "Treated knees.", "A cured back.",
            "Avoids injuring the knee.", "An injured shoulder.", "Medically sound.",
        )) assertTrue(HealthClaims.matches(claim), "should flag \"$claim\"")
        for (fine in listOf(
            "Curl the bar up.", "Barbell curl.", "Secure the bar.", "Occurs at the top.", "Percurso curto e curva suave.",
            "Rosca curta.", "Lower the bar to the curve of your chest.", "Retreat the shoulders.",
            "Curling the bar.", "Secured feet.", "Costas curvadas.", "Curvas suaves.", "Retreating hips.",
        )) assertFalse(HealthClaims.matches(fine), "should pass \"$fine\"")
    }

    @Test
    fun everyCatalogExerciseHasCues() {
        for ((language, map) in both) {
            val missing = stableIds.filter { it !in map }
            assertTrue(missing.isEmpty(), "$language has no cues for ${missing.size} exercises: $missing")
        }
    }

    private fun hasEmoji(text: String): Boolean {
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp in 0x1F000..0x1FAFF || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x2600..0x27BF ||
                cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D
            ) return true
            i += Character.charCount(cp)
        }
        return false
    }

    /**
     * Medical or healing words, by whole word (spec D8: no health promise, no medical language).
     * Each root lists its inflections instead of matching as a prefix, so "curl", "curva", "curto",
     * "secure" and "retreat" pass.
     */
    private object HealthClaims {
        private val portuguese = Regex(
            "(?U)\\b(cur(a|am|ar|as|e|em|ad[oa]s?|ando|ativ\\w*)|trat(a|am|ar|as|e|em|ad[oa]s?|ando|ament\\w*)" +
                "|les(ão|ões|ion\\w*)|médic\\w*)\\b",
            RegexOption.IGNORE_CASE,
        )
        private val english = Regex(
            "(?U)\\b(cur(e|es|ed|ing|ative\\w*)|treat(s|ed|ing|ment|ments)?|injur\\w*|medical\\w*)\\b",
            RegexOption.IGNORE_CASE,
        )

        fun matches(text: String) = portuguese.containsMatchIn(text) || english.containsMatchIn(text)
    }
}
