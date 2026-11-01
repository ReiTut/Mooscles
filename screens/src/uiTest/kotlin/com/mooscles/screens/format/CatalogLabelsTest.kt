package com.mooscles.screens.format

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class CatalogLabelsTest {
    /** Puts the system language back, so a failed test cannot leak a language into the next one. */
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun portugueseLabels() = runComposeUiTest {
        val texts = mutableMapOf<String, String>()
        withScreen(locale = "pt-BR") {
            texts["muscle"] = muscleLabel("Chest")
            texts["equipment"] = equipmentLabel("Barbell")
            texts["convention"] = conventionLabel("Per hand")
            texts["bodyweight"] = conventionLabel("Added weight")
        }
        waitForIdle()
        assertEquals("Peito", texts["muscle"])
        assertEquals("Barra", texts["equipment"])
        assertEquals("Por mão", texts["convention"])
        assertEquals("Peso adicional", texts["bodyweight"])
    }

    @Test
    fun captionOnlyForNonTotalLoad() = runComposeUiTest {
        var total: String? = "not read"
        var perHand: String? = null
        withScreen(locale = "pt-BR") {
            total = conventionCaption("Total load")
            perHand = conventionCaption("Per hand")
        }
        waitForIdle()
        assertNull(total)
        assertEquals("Por mão: registre a carga de um halter.", perHand)
    }

    @Test
    fun unknownLabelFallsBackToItself() = runComposeUiTest {
        val texts = mutableMapOf<String, String?>()
        withScreen(locale = "pt-BR") {
            texts["muscle"] = muscleLabel("Neck")
            texts["equipment"] = equipmentLabel("Sled")
            texts["convention"] = conventionLabel("Per finger")
            texts["caption"] = conventionCaption("Per finger")
        }
        waitForIdle()
        assertEquals("Neck", texts["muscle"])
        assertEquals("Sled", texts["equipment"])
        assertEquals("Per finger", texts["convention"])
        assertNull(texts["caption"])
    }

    /** The full texts in both languages, apostrophes included (Compose resources keep them as written). */
    @Test
    fun explanationsInBothLanguages() {
        for ((locale, expected) in listOf(
            "en" to "Log one dumbbell's load and reps. Volume is per hand; it is never doubled.",
            "pt-BR" to "Registre a carga e as repetições de um halter. O volume é por mão; nunca é dobrado.",
        )) {
            runComposeUiTest {
                var text = ""
                withScreen(locale = locale) { text = conventionExplanation("Per hand") }
                waitForIdle()
                assertEquals(expected, text, locale)
            }
        }
    }
}
