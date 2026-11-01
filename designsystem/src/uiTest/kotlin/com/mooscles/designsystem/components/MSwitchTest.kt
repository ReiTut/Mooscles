package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MSwitchTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standaloneSwitchIsALabelledSwitchWithToggleHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(false)
        withTheme(haptics = haptics) {
            MSwitch(checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("s"), contentDescription = "Auto rest")
        }
        waitForIdle()
        onNodeWithTag("s").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        onNodeWithTag("s").assertContentDescriptionEquals("Auto rest")
        onNodeWithTag("s").assertIsOff()
        onNodeWithTag("s").performClick()
        waitForIdle()
        onNodeWithTag("s").assertIsOn()
        onNodeWithTag("s").performClick()
        waitForIdle()
        onNodeWithTag("s").assertIsOff()
        assertEquals(listOf(HapticEvent.ToggleOn, HapticEvent.ToggleOff), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standaloneSwitchHasAFortyEightTarget() = runComposeUiTest {
        withTheme { MSwitch(false, onCheckedChange = {}, modifier = Modifier.testTag("s"), contentDescription = "Auto rest") }
        waitForIdle()
        val touch = onNodeWithTag("s").fetchSemanticsNode().touchBoundsInRoot
        val minPx = with(density) { MoosclesTargets().min.toPx() }
        assertTrue(touch.height >= minPx - 0.5f && touch.width >= minPx - 0.5f, "touch ${touch.width} x ${touch.height} < $minPx")
    }
}

class MRadioTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun standaloneRadioIsASelectableRadioButtonWithAFortyEightTarget() = runComposeUiTest {
        var selected by mutableStateOf(false)
        withTheme { MRadio(selected, onClick = { selected = true }, modifier = Modifier.testTag("r"), contentDescription = "Kilograms") }
        waitForIdle()
        onNodeWithTag("r").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithTag("r").assertContentDescriptionEquals("Kilograms")
        onNodeWithTag("r").assertIsNotSelected()
        val touch = onNodeWithTag("r").fetchSemanticsNode().touchBoundsInRoot
        val minPx = with(density) { MoosclesTargets().min.toPx() }
        assertTrue(touch.height >= minPx - 0.5f && touch.width >= minPx - 0.5f, "touch ${touch.width} x ${touch.height} < $minPx")
        onNodeWithTag("r").performClick()
        waitForIdle()
        onNodeWithTag("r").assertIsSelected()
    }
}
