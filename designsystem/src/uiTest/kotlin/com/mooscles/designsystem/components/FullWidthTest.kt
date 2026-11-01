package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Critique N4 and #38: a button or a field given a width (or fillMaxWidth) draws that wide,
 * instead of drawing around its text in the middle of a wide touch target.
 */
class FullWidthTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aButtonGivenAWidthDrawsThatWide() = runComposeUiTest {
        withTheme { MButton("Add set", onClick = {}, modifier = Modifier.width(300.dp), variant = MButtonVariant.Secondary) }
        waitForIdle()
        val drawn = onNodeWithTag(ButtonContainerTag, useUnmergedTree = true).getUnclippedBoundsInRoot().width
        assertTrue(abs((drawn - 300.dp).value) < 0.5f, "drawn $drawn wide, expected 300.dp")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aSmallButtonStillDrawsItsHeightInsideTheTarget() = runComposeUiTest {
        withTheme {
            MButton("Skip", onClick = {}, modifier = Modifier.testTag("small"), variant = MButtonVariant.Secondary, size = MButtonSize.S)
        }
        waitForIdle()
        val drawn = onNodeWithTag(ButtonContainerTag, useUnmergedTree = true).getUnclippedBoundsInRoot().height
        val target = onNodeWithTag("small").getUnclippedBoundsInRoot().height
        assertTrue(abs((drawn - MoosclesSizes().buttonS).value) < 0.5f, "S draws $drawn, expected ${MoosclesSizes().buttonS}")
        assertTrue(target >= MoosclesTargets().min - 0.5.dp, "S target $target")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aTextFieldGivenAWidthDrawsThatWide() = runComposeUiTest {
        withTheme { MTextField(TextFieldValue(""), {}, "Exercise name", Modifier.width(300.dp)) }
        waitForIdle()
        val drawn = onNodeWithTag(TextFieldBoxTag, useUnmergedTree = true).getUnclippedBoundsInRoot().width
        assertTrue(abs((drawn - 300.dp).value) < 0.5f, "field drawn $drawn wide, expected 300.dp")
    }
}
