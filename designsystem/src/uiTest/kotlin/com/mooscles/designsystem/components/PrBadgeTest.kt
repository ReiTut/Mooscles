package com.mooscles.designsystem.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class PrBadgeTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isReadAsPersonalRecord() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { PrBadge() } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag).assertContentDescriptionEquals("personal record")
        onNodeWithText("PR", useUnmergedTree = true).assertExists()
    }

    /** Carry from Task 11: [PrBadge]'s own label and spoken text, not a row around it, draw and announce "X". */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun customLabelIsDrawnAndSpoken() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { PrBadge(soft = true, label = "X", spoken = "first record") } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag).assertContentDescriptionEquals("first record")
        onNodeWithText("X", useUnmergedTree = true).assertExists()
        onNodeWithText("PR", useUnmergedTree = true).assertDoesNotExist()
    }

    /** Without [spoken], a custom label keeps the badge read as a personal record, never as nothing. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun customLabelAloneKeepsTheSpokenDefault() = runComposeUiTest {
        withTheme { ProvideAppLocale("en") { PrBadge(label = "X") } }
        waitForIdle()
        onNodeWithTag(PrBadgeTag).assertContentDescriptionEquals("personal record")
        onNodeWithText("X", useUnmergedTree = true).assertExists()
    }
}
