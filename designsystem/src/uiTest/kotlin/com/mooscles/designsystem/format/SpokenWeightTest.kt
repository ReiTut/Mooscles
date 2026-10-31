package com.mooscles.designsystem.format

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

/** Carry from Task 8: a weight read aloud is singular only at exactly 1 ("1 quilo"), in both languages and units. */
class SpokenWeightTest {
    @OptIn(ExperimentalTestApi::class)
    private fun expect(locale: String, cases: List<Triple<String, Boolean, String>>) {
        for ((plain, pounds, expected) in cases) {
            runComposeUiTest {
                withTheme { ProvideAppLocale(locale) { BasicText(spokenWeight(plain, pounds)) } }
                waitForIdle()
                onNodeWithText(expected).assertExists()
            }
        }
    }

    @Test
    fun portugueseIsSingularAtOneOnly() = expect(
        "pt-BR",
        listOf(
            Triple("1", false, "1 quilo"), Triple("2", false, "2 quilos"), Triple("1.5", false, "1,5 quilos"),
            Triple("0", false, "0 quilos"), Triple("1", true, "1 libra"), Triple("2", true, "2 libras"),
        ),
    )

    @Test
    fun englishIsSingularAtOneOnly() = expect(
        "en",
        listOf(
            Triple("1", false, "1 kilogram"), Triple("2", false, "2 kilograms"), Triple("1.5", false, "1.5 kilograms"),
            Triple("0", false, "0 kilograms"), Triple("1", true, "1 pound"), Triple("2", true, "2 pounds"),
        ),
    )
}
