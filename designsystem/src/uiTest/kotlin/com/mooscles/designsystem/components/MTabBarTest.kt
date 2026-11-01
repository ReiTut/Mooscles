package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MTabBarTest {
    private fun tabs(train: String, library: String, history: String, records: String) = listOf(
        MTab("routines", train, MIcons.Dumbbell, MIcons.DumbbellFilled),
        MTab("library", library, MIcons.LayoutGrid, MIcons.LayoutGridFilled),
        MTab("history", history, MIcons.History, MIcons.HistoryFilled),
        MTab("records", records, MIcons.PlateRecord, MIcons.PlateRecordFilled),
    )

    private val english = tabs("Train", "Library", "History", "Records")
    private val portuguese = tabs("Treinar", "Biblioteca", "Histórico", "Recordes")

    private fun ComposeUiTest.bar(
        tabs: List<MTab> = english,
        selected: String? = "routines",
        width: Dp = 412.dp,
        fontScale: Float = 1f,
        density: Float? = null,
        dark: Boolean = false,
        onSelect: (String) -> Unit = {},
        onReselect: (String) -> Unit = {},
    ) {
        withTheme(dark = dark, fontScale = fontScale, density = density) {
            Box(Modifier.width(width)) { MTabBar(tabs, selected, onSelect, onReselect) }
        }
        waitForIdle()
    }

    /** The labels drawn, read from the unmerged tree (each tab's own name replaces them for screen readers). */
    private fun ComposeUiTest.shownLabels(tabs: List<MTab>): List<String> =
        tabs.map { it.label }.filter { onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun isSixtyFourTallWithFortyEightTargets() = runComposeUiTest {
        bar()
        val height = onNodeWithTag(MTabBarTag).getUnclippedBoundsInRoot().height
        assertTrue(abs(height.value - MoosclesSizes().barMin.value) < 0.5f, "the bar is $height tall")
        val minPx = with(density) { MoosclesTargets().min.toPx() } - 0.5f
        for (tab in english) {
            val touch = onNodeWithTag(tabTag(tab.key)).fetchSemanticsNode().touchBoundsInRoot
            assertTrue(touch.width >= minPx && touch.height >= minPx, "${tab.key}: ${touch.width} x ${touch.height} px")
        }
        // At 100% on a phone every label is drawn.
        assertEquals(english.map { it.label }, shownLabels(english))
    }

    @Test
    fun tabsAreSelectableTabsWithPosition() = runComposeUiTest {
        bar(selected = "history")
        onAllNodes(isSelected()).assertCountEquals(1)
        english.forEachIndexed { index, tab ->
            val config = onNodeWithTag(tabTag(tab.key)).fetchSemanticsNode().config
            assertEquals(Role.Tab, config[SemanticsProperties.Role], tab.key)
            assertEquals(tab.key == "history", config[SemanticsProperties.Selected], tab.key)
            val item = config[SemanticsProperties.CollectionItemInfo]
            assertEquals(listOf(0, 1, index, 1), listOf(item.rowIndex, item.rowSpan, item.columnIndex, item.columnSpan), tab.key)
            assertEquals(listOf(tab.label), config[SemanticsProperties.ContentDescription], tab.key)
        }
        val group = onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNode().config
        assertEquals(1, group[SemanticsProperties.CollectionInfo].rowCount)
        assertEquals(4, group[SemanticsProperties.CollectionInfo].columnCount)
        assertTrue(SemanticsProperties.SelectableGroup in group, "the row is a selectable group")
    }

    @Test
    fun selectedHasFilledIconAndPillNotOnlyColor() = runComposeUiTest {
        bar(selected = "library")
        onAllNodesWithTag(TabPillTag, useUnmergedTree = true).assertCountEquals(1)
        onNode(hasTestTag(TabPillTag) and hasAnyAncestor(hasTestTag(tabTag("library"))), useUnmergedTree = true).assertExists()
        // The selected tab draws its filled variant; the others their outline icon.
        for (tab in english) {
            val (drawn, other) = if (tab.key == "library") tab.selectedIcon to tab.icon else tab.icon to tab.selectedIcon
            onNode(hasTestTag(tabIconTag(drawn.name)) and hasAnyAncestor(hasTestTag(tabTag(tab.key))), useUnmergedTree = true).assertExists(tab.key)
            onAllNodesWithTag(tabIconTag(other.name), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun reselectCallsOnReselect() = runComposeUiTest {
        val selects = mutableListOf<String>()
        val reselects = mutableListOf<String>()
        bar(selected = "routines", onSelect = { selects += it }, onReselect = { reselects += it })
        onNodeWithTag(tabTag("routines")).performClick()
        waitForIdle()
        assertEquals(listOf("routines"), reselects)
        assertEquals(emptyList(), selects)
        onNodeWithTag(tabTag("records")).performClick()
        waitForIdle()
        assertEquals(listOf("records"), selects)
        assertEquals(listOf("routines"), reselects)
    }

    @Test
    fun onlySelectedLabelAboveOneAndAHalf() = runComposeUiTest {
        bar(selected = "history", fontScale = 1.6f)
        assertEquals(listOf("History"), shownLabels(english))
        // Every tab keeps its name.
        for (tab in english) onNodeWithTag(tabTag(tab.key)).assertContentDescriptionEquals(tab.label)
    }

    @Test
    fun onlySelectedLabelWhenALabelDoesNotFit() = runComposeUiTest {
        val long = tabs("Train", "Exercise library", "Workout history", "Personal records")
        bar(tabs = long, selected = "routines", width = 320.dp, fontScale = 1.3f)
        assertEquals(listOf("Train"), shownLabels(long))
        for (tab in long) onNodeWithTag(tabTag(tab.key)).assertContentDescriptionEquals(tab.label)
    }

    @Test
    fun selectedLabelIsWholeAtTwoHundred() {
        // 2.0 on the emulator's density, and 2.01 at density 1, the linear scaling iOS gets.
        for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
            for (tabs in listOf(english, portuguese)) {
                for (selected in tabs) {
                    runComposeUiTest {
                        bar(tabs = tabs, selected = selected.key, width = 320.dp, fontScale = scale, density = density)
                        val where = "${selected.label} at $scale"
                        assertEquals(listOf(selected.label), shownLabels(tabs), where)
                        val results = mutableListOf<TextLayoutResult>()
                        val node = onNodeWithText(selected.label, useUnmergedTree = true).fetchSemanticsNode()
                        val action = node.config[SemanticsActions.GetTextLayoutResult].action
                        assertTrue(action != null && action(results), "no text layout result")
                        assertShownWhole(results.first(), where)
                        // The others keep a 48 dp target (in the pixels of the density the bar was laid out at).
                        val minPx = MoosclesTargets().min.value * (density ?: this.density.density) - 0.5f
                        for (tab in tabs) {
                            val touch = onNodeWithTag(tabTag(tab.key)).fetchSemanticsNode().touchBoundsInRoot
                            assertTrue(touch.width >= minPx, "$where: ${tab.key} is ${touch.width} px wide")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun followsTheLightTheme() {
        for ((dark, expected) in listOf(false to MoosclesColors.Light.bgRaised, true to MoosclesColors.Dark.bgRaised)) {
            runComposeUiTest {
                bar(dark = dark)
                val pixels = onNodeWithTag(MTabBarTag).captureToImage().toPixelMap()
                // The left edge of the first tab, half way down: clear of the pill, the label and the dark hairline on top.
                val color = pixels[1, pixels.height / 2]
                assertClose(expected, color, "dark=$dark")
            }
        }
    }

    private fun assertClose(expected: Color, actual: Color, where: String) {
        val tolerance = 2f / 255f
        assertTrue(
            abs(expected.red - actual.red) <= tolerance && abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance,
            "$where: expected $expected, drew $actual",
        )
    }
}
