package com.mooscles.designsystem.illustration

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BodyMapTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val viewRatio = BodyMapPaths.VIEW_WIDTH / BodyMapPaths.VIEW_HEIGHT

    private fun assertAbout(expected: Dp, actual: Dp, what: String) {
        assertTrue(abs((expected - actual).value) <= 2f, "$what is $actual, expected $expected ± 2 dp")
    }

    /** The front and the back view side by side: twice as wide as one view of the same height. */
    @Test
    fun drawsBothViewsSideBySide() {
        runComposeUiTest {
            withTheme { BodyMap(setOf(BodyRegion.CHEST), emptySet(), Modifier.width(320.dp)) }
            waitForIdle()
            val bounds = onNodeWithTag(BodyMapTag).getUnclippedBoundsInRoot()
            assertAbout(320.dp, bounds.width, "width")
            assertAbout(bounds.height * 2f * viewRatio, bounds.width, "width for its height")
        }
        runComposeUiTest {
            withTheme(density = 1f) { BodyMap(emptySet(), setOf(BodyRegion.GLUTES), Modifier.height(300.dp)) }
            waitForIdle()
            val bounds = onNodeWithTag(BodyMapTag).getUnclippedBoundsInRoot()
            assertAbout(300.dp, bounds.height, "height")
            assertAbout(300.dp * 2f * viewRatio, bounds.width, "width for its height")
        }
    }

    /** One image for both views, with no label: the screen's text below says what it shows. */
    @Test
    fun isOneImageWithoutItsOwnLabel() = runComposeUiTest {
        withTheme {
            BodyMap(setOf(BodyRegion.QUADRICEPS), setOf(BodyRegion.GLUTES, BodyRegion.HAMSTRINGS), Modifier.width(320.dp))
        }
        waitForIdle()
        assertEquals(1, onAllNodesWithTag(BodyMapTag).fetchSemanticsNodes().size)
        for (unmerged in listOf(false, true)) {
            val node = onNodeWithTag(BodyMapTag, useUnmergedTree = unmerged).fetchSemanticsNode()
            assertEquals(Role.Image, node.config.getOrNull(SemanticsProperties.Role), "role, unmerged=$unmerged")
            assertNull(node.config.getOrNull(SemanticsProperties.ContentDescription), "label, unmerged=$unmerged")
            assertNull(node.config.getOrNull(SemanticsProperties.Text), "text, unmerged=$unmerged")
            assertTrue(node.children.isEmpty(), "one node, not one per region (unmerged=$unmerged)")
            assertNull(node.config.getOrNull(SemanticsActions.OnClick), "no action, unmerged=$unmerged")
        }
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = onNodeWithText(text).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results), "no text layout result for $text")
        return results.first()
    }

    /**
     * Each legend item is a swatch beside its text, in English and in Portuguese; at 2x text on a
     * narrow column (with the linear 2x of iOS too) the words stay whole and the items wrap instead.
     */
    @Test
    fun legendHasSwatchAndText() {
        for ((tag, words) in listOf(null to listOf("Primary", "Secondary"), "pt-BR" to listOf("Principais", "Secundários"))) {
            for ((scale, density) in listOf(1f to null, 2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
                runComposeUiTest {
                    withTheme(fontScale = scale, density = density) {
                        ProvideAppLocale(tag) { Column(Modifier.width(280.dp)) { BodyMapLegend() } }
                    }
                    waitForIdle()
                    val where = "lang=$tag scale=$scale density=$density"
                    val swatches = onAllNodesWithTag(BodyMapSwatchTag, useUnmergedTree = true).fetchSemanticsNodes()
                    assertEquals(2, swatches.size, where)
                    for ((index, word) in words.withIndex()) {
                        assertShownWhole(layoutOf(word), where)
                        val text = onNodeWithText(word).fetchSemanticsNode().boundsInRoot
                        val swatch = swatches[index].boundsInRoot
                        assertTrue(swatch.right <= text.left, "$where: the $word swatch is not before its text")
                        assertTrue(swatch.center.y in text.top..text.bottom, "$where: the $word swatch is not beside its text")
                    }
                }
            }
        }
    }

    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) <= 2f / 255f && abs(a.green - b.green) <= 2f / 255f && abs(a.blue - b.blue) <= 2f / 255f

    /** Counts the map's pixels of [color] once the geometry is parsed. */
    private suspend fun ComposeUiTest.pixelsOf(
        color: Color,
        primary: Set<BodyRegion>,
        secondary: Set<BodyRegion>,
        dark: Boolean = false,
    ): Int {
        prepareBodyMap()
        withTheme(dark = dark) { BodyMap(primary, secondary, Modifier.width(320.dp)) }
        waitForIdle()
        val pixels = onNodeWithTag(BodyMapTag).captureToImage().toPixelMap()
        var count = 0
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) if (near(pixels[x, y], color)) count++
        return count
    }

    /** The primary regions in accent.solid and nothing else in it; the neck lights the trapezius (spike decision). */
    @Test
    fun primaryRegionsAreDrawnInTheAccent() {
        for ((dark, colors) in listOf(false to MoosclesColors.Light, true to MoosclesColors.Dark)) {
            var none = -1
            var chest = -1
            var neck = -1
            var traps = -1
            runComposeUiTest { none = pixelsOf(colors.accentSolid, emptySet(), setOf(BodyRegion.GLUTES), dark) }
            runComposeUiTest { chest = pixelsOf(colors.accentSolid, setOf(BodyRegion.CHEST), emptySet(), dark) }
            runComposeUiTest { neck = pixelsOf(colors.accentSolid, setOf(BodyRegion.NECK), emptySet(), dark) }
            runComposeUiTest { traps = pixelsOf(colors.accentSolid, setOf(BodyRegion.TRAPS), emptySet(), dark) }
            assertEquals(0, none, "dark=$dark: accent pixels with no primary region")
            assertTrue(chest > 100, "dark=$dark: the chest drew $chest accent pixels")
            assertTrue(neck > 0, "dark=$dark: the neck drew no accent pixel")
            assertEquals(traps, neck, "dark=$dark: the neck and the trapezius differ")
        }
    }
}
