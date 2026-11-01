package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WorkoutInputHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restCanReturnToOriginalValueBeforePersistenceEmits() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(90, { saved += it }) } }
        compose.onNodeWithTag("rest-duration").performTextReplacement("2:00")
        compose.onNodeWithTag("rest-duration").performTextReplacement("1:30")
        compose.onNodeWithTag("rest-duration").performTextReplacement("1:")
        compose.runOnIdle { assertEquals(listOf(120, 90), saved) }
        compose.onNodeWithTag("rest-duration").assertTextContains("1:30")
        compose.onNodeWithText("Use 0:00 to 5:00, with seconds from 00 to 59.").assertExists()
    }

    @Test fun numericTimerTypingKeepsColonAndAdvancesSeconds() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(90, { saved += it }) } }
        val field = compose.onNodeWithTag("rest-duration")
        field.performTextReplacement("2")
        field.assertTextContains("2:00")
        field.performTextInput("0")
        field.performTextInput("3")
        field.assertTextContains("2:03")
        field.performTextReplacement("5:00")
        field.performTextReplacement("5:01")
        field.assertTextContains("5:00")
        compose.runOnIdle { assertEquals(listOf(120, 123, 300), saved) }
    }

    @Test fun invalidInputDoesNotModifyLegacyHistoricalDuration() {
        val saved = mutableListOf<Int>()
        compose.setContent { MaterialTheme { RestDurationField(600, { saved += it }) } }
        compose.onNodeWithTag("rest-duration").performTextReplacement("6:00")
        compose.onNodeWithTag("rest-duration").assertTextContains("5:00")
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
    }
}
