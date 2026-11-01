package com.mooscles.designsystem.format

import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_sets_count
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class PluralCountTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun zeroStaysPluralInPortuguese() {
        for ((n, expected) in listOf(0 to "0 séries", 1 to "1 série", 2 to "2 séries")) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("pt-BR") { BasicText(pluralCount(Res.plurals.ds_sets_count, n)) } }
                waitForIdle()
                onNodeWithText(expected).assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun englishUsesOneAndOther() {
        for ((n, expected) in listOf(0 to "0 sets", 1 to "1 set", 5 to "5 sets")) {
            runComposeUiTest {
                withTheme { ProvideAppLocale("en") { BasicText(pluralCount(Res.plurals.ds_sets_count, n)) } }
                waitForIdle()
                onNodeWithText(expected).assertExists()
            }
        }
    }
}
