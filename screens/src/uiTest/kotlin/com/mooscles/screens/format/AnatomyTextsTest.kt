package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.data.Anatomy
import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The text equivalent of the body map (spec "Acessibilidade" of the detail, D21). */
@OptIn(ExperimentalTestApi::class)
class AnatomyTextsTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private fun inLanguage(locale: String, read: @Composable () -> String): String {
        var value: String? = null
        runComposeUiTest {
            withScreen(locale = locale) { value = read() }
            waitForIdle()
        }
        LocalAppLocale.restore()
        return checkNotNull(value) { "nothing composed in $locale" }
    }

    /** Every region's name in enum order, joined by ", ". */
    @Composable
    private fun allRegions(): String {
        val names = ArrayList<String>()
        for (region in MuscleRegion.entries) names += regionName(region)
        return names.joinToString(", ")
    }

    private val squat = Anatomy(listOf(MuscleRegion.QUADRICEPS), listOf(MuscleRegion.GLUTES, MuscleRegion.HAMSTRINGS))

    @Test
    fun sentenceSamplesFromTheSpec() {
        assertEquals("Primary: quadriceps. Secondary: glutes, hamstrings.", inLanguage("en") { anatomySentence(squat) })
        assertEquals(
            "Principais: quadríceps. Secundários: glúteos, posteriores da coxa.",
            inLanguage("pt-BR") { anatomySentence(squat) },
        )
        // Every region has its own lowercase name, in both languages.
        val english = inLanguage("en") { allRegions() }
        assertEquals(
            "abdominals, abductors, adductors, biceps, calves, chest, forearms, glutes, hamstrings, lats, " +
                "lower back, middle back, neck, quadriceps, shoulders, traps, triceps",
            english,
        )
        val portuguese = inLanguage("pt-BR") { allRegions() }
        assertEquals(
            "abdominais, abdutores, adutores, bíceps, panturrilhas, peito, antebraços, glúteos, posteriores da coxa, " +
                "dorsais, lombar, meio das costas, pescoço, quadríceps, ombros, trapézio, tríceps",
            portuguese,
        )
    }

    @Test
    fun primaryOnly() {
        val curl = Anatomy(listOf(MuscleRegion.BICEPS, MuscleRegion.FOREARMS), emptyList())
        assertEquals("Primary: biceps, forearms.", inLanguage("en") { anatomySentence(curl) })
        assertEquals("Principais: bíceps, antebraços.", inLanguage("pt-BR") { anatomySentence(curl) })
    }

    @Test
    fun fullBody() {
        assertEquals("Full body", inLanguage("en") { anatomySentence(null) })
        assertEquals("Corpo inteiro", inLanguage("pt-BR") { anatomySentence(null) })
    }
}
