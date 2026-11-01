package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.restoreTouchMode
import com.mooscles.designsystem.withTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MChipTest {
    // A test here asks for keyboard mode, which outlives it on Android: give the next tests touch mode.
    @AfterTest
    fun backToTouchMode() = restoreTouchMode()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun selectedShowsACheckIconNotOnlyColor() {
        for (dark in listOf(false, true)) {
            runComposeUiTest {
                var selected by mutableStateOf(false)
                withTheme(dark = dark) { MChip("Chest", selected = selected, onClick = {}) }
                waitForIdle()
                onNodeWithTag(ChipCheckTag, useUnmergedTree = true).assertDoesNotExist()
                runOnUiThread { selected = true }
                waitForIdle()
                onNodeWithTag(ChipCheckTag, useUnmergedTree = true).assertExists()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun everyKindExposesItsRoleAndState() = runComposeUiTest {
        var selected by mutableStateOf(false)
        withTheme {
          androidx.compose.foundation.layout.Column {
            MChip("Filter", selected, onClick = { selected = !selected }, kind = MChipKind.Filter, modifier = Modifier.testTag("filter"))
            MChip("Set", selected, onClick = {}, kind = MChipKind.SetType, modifier = Modifier.testTag("set"))
            MChip("Period", selected, onClick = {}, kind = MChipKind.Segmented, modifier = Modifier.testTag("seg"))
          }
        }
        waitForIdle()
        fun role(tag: String, role: Role) = onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, role))
        role("filter", Role.Checkbox)
        role("set", Role.RadioButton)
        role("seg", Role.Tab)
        onNodeWithTag("filter").assertIsOff()
        onNodeWithTag("set").assertIsNotSelected()
        onNodeWithTag("seg").assertIsNotSelected()
        onNodeWithTag("filter").performClick()
        waitForIdle()
        onNodeWithTag("filter").assertIsOn()
        onNodeWithTag("set").assertIsSelected()
        onNodeWithTag("seg").assertIsSelected()
    }

    /**
     * Plan decision 12: the menu chip ("Equipment · 2") is a button that opens a sheet: the count
     * is in its label, a chevron says it opens something, and selected (a filter is on) draws the
     * selected look with no check. A caller can focus it through its modifier (ruling F33).
     */
    @OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
    @Test
    fun menuChipIsAButtonAndShowsItsCount() = runComposeUiTest {
        var opened = 0
        var selected by mutableStateOf(false)
        val focus = FocusRequester()
        var inputMode: InputModeManager? = null
        withTheme {
            inputMode = LocalInputModeManager.current
            MChip(
                if (selected) "Equipment · 2" else "Equipment",
                selected = selected,
                onClick = { opened++ },
                kind = MChipKind.Menu,
                modifier = Modifier.testTag("menu").focusRequester(focus),
            )
        }
        waitForIdle()
        val menu = onNodeWithTag("menu")
        menu.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).assertHasClickAction()
        onNodeWithTag(ChipMenuChevronTag, useUnmergedTree = true).assertExists()
        runOnUiThread { selected = true }
        waitForIdle()
        menu.assertTextEquals("Equipment · 2")
        onNodeWithTag(ChipCheckTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ChipMenuChevronTag, useUnmergedTree = true).assertExists()
        // A button, not a toggle or one of a group: no on/off or selected state to read.
        val config = menu.fetchSemanticsNode().config
        assertFalse(SemanticsProperties.ToggleableState in config, "the menu chip reports a toggle state")
        assertFalse(SemanticsProperties.Selected in config, "the menu chip reports a selected state")
        menu.performClick()
        waitForIdle()
        assertEquals(1, opened)
        // Focus is returned for keyboard use: on Android a clickable takes no focus in touch mode.
        runOnUiThread {
            inputMode?.requestInputMode(InputMode.Keyboard)
            focus.requestFocus()
        }
        waitForIdle()
        menu.assertIsFocused()
    }
}
