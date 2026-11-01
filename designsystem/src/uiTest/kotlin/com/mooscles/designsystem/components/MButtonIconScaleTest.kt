package com.mooscles.designsystem.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Critique #13: a button's icon grows with the text, so at 200% it is not left at its 100% size. */
class MButtonIconScaleTest {
    @OptIn(ExperimentalTestApi::class)
    private fun iconSize(fontScale: Float): Pair<Dp, Dp> {
        var size = Dp.Unspecified to Dp.Unspecified
        runComposeUiTest {
            withTheme(fontScale = fontScale) { MButton("Check for updates", onClick = {}, icon = MIcons.RefreshCw) }
            waitForIdle()
            val bounds = onNodeWithTag(ButtonIconTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            size = bounds.width to bounds.height
        }
        return size
    }

    @Test
    fun theIconScalesWithTheFontScale() {
        val icon = MoosclesSizes().icon20
        for (scale in listOf(1f, 1.3f, 2f)) {
            val (width, height) = iconSize(scale)
            val expected = icon * scale
            assertTrue(abs((width - expected).value) < 0.5f && abs((height - expected).value) < 0.5f, "at $scale: $width x $height, expected $expected")
        }
    }
}
