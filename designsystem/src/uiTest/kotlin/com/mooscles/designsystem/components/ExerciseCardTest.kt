package com.mooscles.designsystem.components

import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import com.mooscles.designsystem.tokens.MoosclesRadius
import com.mooscles.designsystem.tokens.MoosclesSpace
import kotlin.test.assertIs
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseCardTest {
    @androidx.compose.runtime.Composable
    private fun Card(
        media: ExerciseMedia,
        collapsed: Boolean = false,
        onMenu: () -> Unit = {},
        onAddSet: () -> Unit = {},
    ) = ProvideAppLocale("en") {
        ExerciseCard("Bench press", "Barbell, chest", media, MIcons.Barbell, onMenu, onAddSet, collapsed = collapsed) {
            Box(Modifier.testTag("a-set"))
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun offlineMediaShowsTheIconAndText() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.Offline) }
        waitForIdle()
        onNodeWithTag(ExerciseOfflineIconTag, useUnmergedTree = true).assertExists()
        onNodeWithTag(ExerciseOfflineTextTag, useUnmergedTree = true).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun noMediaShowsOnlyTheEquipmentIcon() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.None) }
        waitForIdle()
        onNodeWithTag(ExerciseEquipmentIconTag, useUnmergedTree = true).assertExists()
        onNodeWithTag(ExerciseOfflineIconTag, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ExerciseOfflineTextTag, useUnmergedTree = true).assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun headerWithoutToggleIsNotAButtonAndNamesItsMenu() = runComposeUiTest {
        withTheme {
            ProvideAppLocale("en") {
                ExerciseCardHeader(
                    "Bench press", "Chest · Barbell", ExerciseMedia.None, MIcons.Barbell, onMenu = {}, collapsed = false,
                    onToggle = null, menuTag = "custom-menu", menuLabel = "Options for Bench press",
                )
            }
        }
        waitForIdle()
        // No toggle: no "Collapse" action for a screen reader and no toggle node, only the menu button.
        onNodeWithTag(ExerciseHeaderToggleTag).assertDoesNotExist()
        onNodeWithTag("custom-menu").assertContentDescriptionEquals("Options for Bench press")
        onNodeWithTag(ExerciseMenuTag).assertDoesNotExist()
        onAllNodes(hasClickAction()).assertCountEquals(1)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun menuIsLabelled() = runComposeUiTest {
        var opened = 0
        withTheme { Card(ExerciseMedia.None, onMenu = { opened++ }) }
        waitForIdle()
        onNodeWithTag(ExerciseMenuTag).assertContentDescriptionEquals("Exercise options")
        onNodeWithTag(ExerciseMenuTag).performClick()
        assertEquals(1, opened)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun addSetMeetsTheWorkoutTarget() = runComposeUiTest {
        var added = 0
        withTheme { Card(ExerciseMedia.None, onAddSet = { added++ }) }
        waitForIdle()
        val touch = onNodeWithTag(ExerciseAddSetTag).fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f, "height ${touch.height} < $target")
        onNodeWithTag(ExerciseAddSetTag).performClick()
        assertEquals(1, added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun collapsedHidesTheSets() = runComposeUiTest {
        withTheme { Card(ExerciseMedia.None, collapsed = true) }
        waitForIdle()
        onNodeWithTag("a-set").assertDoesNotExist()
        onNodeWithTag(ExerciseAddSetTag).assertDoesNotExist()
        onNodeWithTag(ExerciseMenuTag).assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun imageHasTheEquipmentIconUnderneathWhileItLoads() {
        runComposeUiTest {
            // A painter that has not loaded draws nothing; the placeholder must be there to show through.
            withTheme { Card(ExerciseMedia.Image(ColorPainter(Color.Transparent))) }
            waitForIdle()
            onNodeWithTag(ExerciseMediaPlaceholderTag, useUnmergedTree = true).assertExists()
        }
        runComposeUiTest {
            withTheme { Card(ExerciseMedia.None) }
            waitForIdle()
            onNodeWithTag(ExerciseMediaPlaceholderTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @androidx.compose.runtime.Composable
    private fun Segmented(collapsed: Boolean = false, onToggle: () -> Unit = {}, progress: String? = null, done: Boolean = false, onAddSet: () -> Unit = {}) =
        ProvideAppLocale("en") {
            Column(Modifier.width(360.dp)) {
                ExerciseCardHeader(
                    "Bench press", "Barbell, chest", ExerciseMedia.None, MIcons.Barbell, {}, collapsed, onToggle,
                    progress = progress, done = done,
                )
                if (!collapsed) {
                    ExerciseCardSegment { Box(Modifier.testTag("a-set")) }
                    ExerciseCardFooter(onAddSet)
                }
            }
        }

    /** The corner radii of a segment's shape, in px for a 200 x 100 px box at density 1: top start, bottom start. */
    private fun corners(node: androidx.compose.ui.semantics.SemanticsNode): Pair<Float, Float> {
        val shape = node.config[CardSegmentShape]
        return when (val outline = shape.createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f))) {
            is Outline.Rectangle -> 0f to 0f
            is Outline.Rounded -> outline.roundRect.topLeftCornerRadius.x to outline.roundRect.bottomLeftCornerRadius.x
            is Outline.Generic -> error("a generic outline")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun segmentsDrawOneCard() {
        val lg = MoosclesRadius().lg.value
        runComposeUiTest {
            withTheme { Segmented() }
            waitForIdle()
            val header = onNodeWithTag(ExerciseCardHeaderTag).fetchSemanticsNode()
            val segment = onNodeWithTag(ExerciseCardSegmentTag).fetchSemanticsNode()
            val footer = onNodeWithTag(ExerciseCardFooterTag).fetchSemanticsNode()
            assertEquals(lg to 0f, corners(header), "header: rounded on top only")
            assertEquals(0f to 0f, corners(segment), "segment: no corners")
            assertIs<Outline.Rectangle>(segment.config[CardSegmentShape].createOutline(Size(200f, 100f), LayoutDirection.Ltr, Density(1f)))
            assertEquals(0f to lg, corners(footer), "footer: rounded at the bottom only")
            // The three pieces touch and share their sides: one card.
            val h = header.boundsInRoot
            val s = segment.boundsInRoot
            val f = footer.boundsInRoot
            assertEquals(h.bottom, s.top, 0.5f)
            assertEquals(s.bottom, f.top, 0.5f)
            for (each in listOf(s, f)) {
                assertEquals(h.left, each.left, 0.5f)
                assertEquals(h.right, each.right, 0.5f)
            }
        }
        runComposeUiTest {
            withTheme { Segmented(collapsed = true) }
            waitForIdle()
            assertEquals(lg to lg, corners(onNodeWithTag(ExerciseCardHeaderTag).fetchSemanticsNode()), "collapsed: the header is the whole card")
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun collapsedHeaderShowsProgressAndCheck() {
        runComposeUiTest {
            withTheme { Segmented(collapsed = true, progress = "3 of 3 sets", done = true) }
            waitForIdle()
            onNodeWithText("3 of 3 sets", useUnmergedTree = true).assertExists()
            onNodeWithTag(ExerciseCardDoneTag, useUnmergedTree = true).assertExists()
        }
        runComposeUiTest {
            withTheme { Segmented(collapsed = true, progress = "0 of 3 sets", done = false) }
            waitForIdle()
            onNodeWithText("0 of 3 sets", useUnmergedTree = true).assertExists()
            onNodeWithTag(ExerciseCardDoneTag, useUnmergedTree = true).assertDoesNotExist()
        }
        runComposeUiTest {
            // Open, the sets say it: no progress line and no check.
            withTheme { Segmented(collapsed = false, progress = "3 of 3 sets", done = true) }
            waitForIdle()
            onNodeWithText("3 of 3 sets", useUnmergedTree = true).assertDoesNotExist()
            onNodeWithTag(ExerciseCardDoneTag, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun headerTapToggles() = runComposeUiTest {
        var collapsed by mutableStateOf(false)
        var toggles = 0
        var menus = 0
        withTheme {
            ProvideAppLocale("en") {
                ExerciseCardHeader(
                    "Bench press", "Barbell, chest", ExerciseMedia.None, MIcons.Barbell, { menus++ }, collapsed,
                    { toggles++; collapsed = !collapsed },
                )
            }
        }
        waitForIdle()
        onNodeWithTag(ExerciseHeaderToggleTag).performClick()
        waitForIdle()
        assertEquals(1, toggles)
        assertTrue(collapsed)
        onNodeWithTag(ExerciseHeaderToggleTag).performClick()
        waitForIdle()
        assertEquals(2, toggles)
        // The menu is its own button inside the header: it does not toggle.
        onNodeWithTag(ExerciseMenuTag).performClick()
        waitForIdle()
        assertEquals(1, menus)
        assertEquals(2, toggles)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun footerAddSetIsFiftySixAndFullWidth() = runComposeUiTest {
        var added = 0
        withTheme { Segmented(onAddSet = { added++ }) }
        waitForIdle()
        val footer = onNodeWithTag(ExerciseCardFooterTag).fetchSemanticsNode().boundsInRoot
        val button = onNodeWithTag(ExerciseAddSetTag).fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        val inset = with(density) { MoosclesSpace().s3.toPx() }
        assertTrue(button.height >= target - 0.5f, "height ${button.height} < $target")
        assertEquals(footer.width - inset * 2, button.width, 1f, "the button spans the card less its padding")
        onNodeWithTag(ExerciseAddSetTag).performClick()
        assertEquals(1, added)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun setsDoneOfIsPluralOnTheTotal() {
        for ((locale, expected) in listOf("en" to listOf("3 of 3 sets", "0 of 1 set", "0 of 0 sets"), "pt-BR" to listOf("3 de 3 séries", "0 de 1 série", "0 de 0 séries"))) {
            runComposeUiTest {
                withTheme {
                    ProvideAppLocale(locale) {
                        Column {
                            BasicText(setsDoneOf(3, 3))
                            BasicText(setsDoneOf(0, 1))
                            BasicText(setsDoneOf(0, 0))
                        }
                    }
                }
                waitForIdle()
                for (text in expected) onNodeWithText(text).assertExists("$locale: $text")
            }
        }
    }

    /**
     * Phase 4B (plan Task 4): the header takes a drag handle before the thumbnail, outside the
     * toggle (holding it never opens the card), and a styled subline under the meta (the target line).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun headerShowsTheHandleAndTheSubline() {
        for (collapsed in listOf(true, false)) {
            runComposeUiTest {
                var toggles = 0
                withTheme {
                    ProvideAppLocale("en") {
                        ExerciseCardHeader(
                            "Bench press", "Barbell, chest", ExerciseMedia.None, MIcons.Barbell,
                            onMenu = {}, collapsed = collapsed, onToggle = { toggles++ },
                            handle = { Box(Modifier.size(48.dp).testTag("the-handle")) },
                            subline = AnnotatedString("Target 3×8 · 80 kg"),
                        )
                    }
                }
                waitForIdle()
                val handle = onNodeWithTag("the-handle", useUnmergedTree = true).getBoundsInRoot()
                val tile = onNodeWithTag(ExerciseEquipmentIconTag, useUnmergedTree = true).getBoundsInRoot()
                assertTrue(handle.right <= tile.left, "the handle comes first: ${handle.right} / ${tile.left}")
                val meta = onNodeWithText("Barbell, chest", useUnmergedTree = true).getBoundsInRoot()
                val subline = onNodeWithTag(ExerciseCardSublineTag, useUnmergedTree = true)
                subline.assertTextEquals("Target 3×8 · 80 kg")
                assertTrue(subline.getBoundsInRoot().top >= meta.bottom, "the subline is under the meta (collapsed=$collapsed)")
                onNodeWithTag("the-handle", useUnmergedTree = true).performTouchInput { click(center) }
                waitForIdle()
                assertEquals(0, toggles, "a tap on the handle does not toggle the card")
                onNodeWithTag(ExerciseHeaderToggleTag).performClick()
                assertEquals(1, toggles)
            }
        }
    }
}
