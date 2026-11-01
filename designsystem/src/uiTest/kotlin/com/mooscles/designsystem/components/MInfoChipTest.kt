package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MInfoChipTest {
    /** Plan decision 12: a read-only chip reads as its text, with no role, action or focus. */
    @Test
    fun isTextOnlyAndNotFocusable() = runComposeUiTest {
        withTheme { MInfoChip("Barbell", Modifier.testTag("info"), icon = MIcons.Barbell) }
        waitForIdle()
        val chip = onNodeWithTag("info").assertTextEquals("Barbell")
        val config = chip.fetchSemanticsNode().config
        assertFalse(SemanticsProperties.Role in config, "the info chip has a role")
        assertFalse(SemanticsActions.OnClick in config, "the info chip is clickable")
        assertFalse(SemanticsProperties.ContentDescription in config, "the icon is read: ${config}")
        onNode(hasClickAction()).assertDoesNotExist()
        onNode(isFocusable()).assertDoesNotExist()
        val drawn = chip.fetchSemanticsNode().boundsInRoot
        assertEquals(with(density) { MoosclesSizes().infoChipHeight.toPx() }, drawn.height, 1f, "the info chip is 32 dp tall")
    }

    private fun ComposeUiTest.layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action = onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "no text layout for $text")
        return results.first()
    }

    /** At 200%, and at the iOS linear 2x (ruling F23), the label is whole and the chip grows around it. */
    @Test
    fun textStaysWholeAtTwoX() {
        for (label in listOf("Total load", "Carga adicional")) {
            for ((scale, density) in listOf(2f to null, LINEAR_TWO_X_FONT_SCALE to 1f)) {
                runComposeUiTest {
                    withTheme(fontScale = scale, density = density) {
                        Box(Modifier.width(360.dp)) { MInfoChip(label, Modifier.testTag("info"), icon = MIcons.Info) }
                    }
                    waitForIdle()
                    val where = "\"$label\" scale=$scale density=$density"
                    assertShownWhole(layoutOf(label), where)
                    val chip = onNodeWithTag("info").fetchSemanticsNode().boundsInRoot
                    val text = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    assertTrue(text.top >= chip.top - 0.5f && text.bottom <= chip.bottom + 0.5f && text.right <= chip.right + 0.5f, "$where: text $text outside chip $chip")
                    // In px at the density the chip was drawn at (1 for the iOS case).
                    val minPx = MoosclesSizes().infoChipHeight.value * (density ?: this.density.density)
                    assertTrue(chip.height > minPx, "$where: the chip did not grow with the text (${chip.height} px, min $minPx)")
                }
            }
        }
    }
}
