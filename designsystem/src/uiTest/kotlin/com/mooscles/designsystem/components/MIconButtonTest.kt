package com.mooscles.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.withTheme
import kotlin.test.Test

class MIconButtonTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun toggledExposesToggleState() = runComposeUiTest {
        withTheme {
            var on by remember { mutableStateOf(false) }
            MIconButton(MIcons.Sun, "Light", onClick = { on = !on }, toggled = on, modifier = Modifier.testTag("t"))
        }
        waitForIdle()
        onNodeWithTag("t").assertIsOff()
        onNodeWithTag("t").performClick()
        waitForIdle()
        onNodeWithTag("t").assertIsOn()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hasTheGivenLabel() = runComposeUiTest {
        withTheme { MIconButton(MIcons.Plus, "Add set", onClick = {}, modifier = Modifier.testTag("t")) }
        waitForIdle()
        onNodeWithTag("t").assertContentDescriptionEquals("Add set")
    }
}
