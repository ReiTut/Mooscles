package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.ui.*
import org.junit.Assert.*
import org.junit.After
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
    @After fun restoreLocale() { LocalAppLocale.restore() }

    @Test fun restCanReturnToOriginalValueBeforePersistenceEmits() {
        val saved = mutableListOf<Int>()
        show { SettingsRestDurationField(90, { saved += it }) }
        field().performTextReplacement("2:00")
        field().performTextReplacement("1:30")
        field().performTextReplacement("1:")
        compose.runOnIdle { assertEquals(listOf(120, 90), saved) }
        field().assertTextContains("1:30")
        compose.onNodeWithText("Use 0:00 to 5:00, with seconds from 00 to 59.").assertExists()
    }

    @Test fun numericTimerTypingKeepsColonAndAdvancesSeconds() {
        val saved = mutableListOf<Int>()
        show { SettingsRestDurationField(90, { saved += it }) }
        val field = field()
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
        show { SettingsRestDurationField(600, { saved += it }) }
        field().performTextReplacement("6:00")
        field().assertTextContains("5:00")
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
    }

    private fun field() = compose.onNode(hasSetTextAction() and
        (hasTestTag("rest-duration") or hasAnyAncestor(hasTestTag("rest-duration"))), useUnmergedTree = true)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { MaterialTheme { MoosclesDesignTheme(dark = false, reducedMotion = true) {
            ProvideAppLocale("en") { content() }
        } } }
    }
}
