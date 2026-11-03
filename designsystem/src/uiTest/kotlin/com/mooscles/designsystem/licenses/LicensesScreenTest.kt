package com.mooscles.designsystem.licenses

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LicensesScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    /**
     * Phase 4A (spec D9, plan decision 17): the MuscleMap entry (MIT, with the source commit) and
     * the entry of the own media (CC0), whose name is translated; the others keep proper names.
     */
    @Test
    fun listsTheMediaAndMuscleMapEntries() {
        val media = listOf(
            null to "Mooscles illustrations, exercise tips and names",
            "pt-BR" to "Ilustrações, dicas de execução e nomes do Mooscles",
        )
        for ((tag, name) in media) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(tag) { LicensesScreen(onBack = {}) } }
                // The texts are read from the bundled files after the first frame.
                waitUntil(timeoutMillis = 5_000) {
                    onAllNodesWithText("Statement of Purpose", substring = true).fetchSemanticsNodes().isNotEmpty() &&
                        onAllNodesWithText("Jsplice / MuscleMap", substring = true).fetchSemanticsNodes().isNotEmpty()
                }
                val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
                for (proper in listOf("Inter", "Barlow Condensed", "Lucide", "MuscleMap")) {
                    onNodeWithText(proper).assert(heading)
                }
                onNodeWithText(name).assert(heading).performScrollTo().assertIsDisplayed()
                assertTrue(onAllNodesWithText("CC0 1.0 Universal").fetchSemanticsNodes().isNotEmpty(), "lang=$tag: the CC0 label")

                // Opening the MuscleMap entry shows its license and the commit the map comes from.
                onNodeWithText("MuscleMap").performScrollTo().assertIsDisplayed()
                // The entry's license line comes first; the file itself starts with "MIT License" too.
                onAllNodesWithText("MIT License").onFirst().performScrollTo().assertIsDisplayed()
                onNode(hasText("170cfacda79376dd59ac0cf2f7dfab8ff9f4443a", substring = true)).performScrollTo().assertIsDisplayed()
                onNode(hasText("Copyright (c) 2026 Jsplice / MuscleMap contributors", substring = true)).assertExists()
            }
        }
    }
}
