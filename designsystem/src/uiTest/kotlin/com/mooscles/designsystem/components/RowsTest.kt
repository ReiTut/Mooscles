package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_back
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesSpace
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.resources.getString

class RowsTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun switchRowTogglesFromTheWholeRowAndFiresToggleHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(false)
        withTheme(haptics = haptics) {
            SwitchRow("Auto rest", checked = checked, onCheckedChange = { checked = it }, modifier = Modifier.testTag("row"))
        }
        waitForIdle()
        onNodeWithTag("row").performClick()
        waitForIdle()
        assertEquals(true, checked)
        assertEquals(listOf(HapticEvent.ToggleOn), haptics.events.toList())
        onNodeWithTag("row").performClick()
        waitForIdle()
        assertEquals(false, checked)
        assertEquals(listOf(HapticEvent.ToggleOn, HapticEvent.ToggleOff), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun radioGroupIsASelectableGroupOfRadioButtons() = runComposeUiTest {
        withTheme {
            RadioGroup(Modifier.testTag("group")) {
                RadioRow("Kilograms", selected = true, onSelect = {}, modifier = Modifier.testTag("kg"))
                RadioRow("Pounds", selected = false, onSelect = {}, modifier = Modifier.testTag("lb"))
            }
        }
        waitForIdle()
        onNodeWithTag("group").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        for (tag in listOf("kg", "lb")) {
            onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
                .assert(hasParent(hasTestTag("group")))
        }
        onNodeWithTag("kg").assertIsSelected()
        onNodeWithTag("lb").assertIsNotSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun radioRowIsASelectableRadioButton() = runComposeUiTest {
        withTheme { RadioRow("Kilograms", selected = true, onSelect = {}, modifier = Modifier.testTag("row")) }
        waitForIdle()
        onNodeWithTag("row").assertIsSelected()
        onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun rowsAreAtLeastFiftySixTallAtEveryScale() {
        for (scale in listOf(1f, 1.3f, 2f)) {
            runComposeUiTest {
                withTheme(fontScale = scale) {
                    SettingsRow("Settings row", onClick = {}, modifier = Modifier.testTag("settings"))
                    RadioRow("Radio row", selected = false, onSelect = {}, modifier = Modifier.testTag("radio"))
                    SwitchRow("Switch row", checked = false, onCheckedChange = {}, modifier = Modifier.testTag("switch"))
                    TopBar("Top bar", modifier = Modifier.testTag("top"))
                }
                waitForIdle()
                for (tag in listOf("settings", "radio", "switch", "top")) {
                    onNodeWithTag(tag).assertHeightIsAtLeast(MoosclesSizes().rowMin)
                }
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sectionHeaderIsAHeading() = runComposeUiTest {
        withTheme { SectionHeader("Your data", modifier = Modifier.testTag("h")) }
        waitForIdle()
        onNodeWithTag("h").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun topBarBackIsLabelledAndCallsBack() = runComposeUiTest {
        var backs = 0
        withTheme { TopBar("Settings", onBack = { backs++ }) }
        waitForIdle()
        onAllNodesWithText("Settings").assertCountEquals(1)
        val back = onNodeWithContentDescription(getString(Res.string.ds_back))
        back.assertHasClickAction()
        back.performClick()
        waitForIdle()
        assertEquals(1, backs)
    }

    /** Without a title: back at the start, the actions at the end, and no empty heading. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun topBarWithoutTitleKeepsBackAndActions() = runComposeUiTest {
        withTheme {
            TopBar(null, modifier = Modifier.testTag("top"), onBack = {}) {
                MIconButton(MIcons.EllipsisVertical, "More", onClick = {}, modifier = Modifier.testTag("more"))
            }
        }
        waitForIdle()
        onNodeWithTag("top").assertHeightIsAtLeast(MoosclesSizes().rowMin)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertCountEquals(0)
        val back = onNodeWithContentDescription(getString(Res.string.ds_back)).getUnclippedBoundsInRoot()
        val more = onNodeWithTag("more").getUnclippedBoundsInRoot()
        val bar = onNodeWithTag("top").getUnclippedBoundsInRoot()
        assertTrue(back.left < more.left, "back $back, more $more")
        assertTrue(bar.right - more.right <= MoosclesSpace().s2 + 0.5.dp, "the action is not at the end: bar $bar, more $more")
    }
}
