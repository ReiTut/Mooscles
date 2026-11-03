package com.mooscles.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.withTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import kotlin.test.Test
import kotlin.test.assertTrue

class MProgressBarTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun determinateExposesRange() = runComposeUiTest {
        withTheme { MProgressBar(0.5f, Modifier.testTag("p")) }
        waitForIdle()
        onNodeWithTag("p").assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun indeterminateHasADescription() = runComposeUiTest {
        withTheme(reducedMotion = true) { MProgressBar(null, Modifier.testTag("p")) }
        waitForIdle()
        val description = onNodeWithTag("p").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertTrue(!description.isNullOrEmpty())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun indeterminateExposesIndeterminate() {
        for (reduced in listOf(false, true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                withTheme(reducedMotion = reduced) { MProgressBar(null, Modifier.testTag("p")) }
                waitForIdle()
                onNodeWithTag("p").assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
            }
        }
    }
}
