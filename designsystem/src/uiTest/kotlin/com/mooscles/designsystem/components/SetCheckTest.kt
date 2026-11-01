package com.mooscles.designsystem.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTargets
import com.mooscles.designsystem.withTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SetCheckTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun isAToggleWithTheSetNumber() = runComposeUiTest {
        var checked by mutableStateOf(false)
        withTheme { ProvideAppLocale("en") { SetCheck(checked, { checked = it }, setNumber = 4, modifier = Modifier.testTag("c")) } }
        waitForIdle()
        onNodeWithTag("c").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        onNodeWithTag("c").assertContentDescriptionEquals("Complete set 4")
        onNodeWithTag("c").assertIsOff()
        onNodeWithTag("c").performClick()
        waitForIdle()
        onNodeWithTag("c").assertIsOn()
    }

    /**
     * Done, the check says so: its name becomes "Set 4 completed" and its double tap reads as
     * "mark set as not done", so a screen reader never offers "Complete set 4" on a set that is
     * already complete, where the tap would undo it.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun nameAndActionFollowTheState() {
        for ((language, texts) in listOf(
            "en" to listOf("Complete set 4", "Complete set", "Set 4 completed", "Mark set as not done"),
            "pt-BR" to listOf("Concluir série 4", "Concluir série", "Série 4 concluída", "Marcar série como não feita"),
        )) {
            runComposeUiTest {
                var checked by mutableStateOf(false)
                withTheme { ProvideAppLocale(language) { SetCheck(checked, { checked = it }, setNumber = 4, modifier = Modifier.testTag("c")) } }
                waitForIdle()
                fun clickLabel() = onNodeWithTag("c").fetchSemanticsNode().config[SemanticsActions.OnClick].label
                onNodeWithTag("c").assertContentDescriptionEquals(texts[0]).assertIsOff()
                assertEquals(texts[1], clickLabel(), language)
                onNodeWithTag("c").performClick()
                waitForIdle()
                onNodeWithTag("c").assertContentDescriptionEquals(texts[2]).assertIsOn()
                assertEquals(texts[3], clickLabel(), language)
                onNodeWithTag("c").performSemanticsAction(SemanticsActions.OnClick)
                waitForIdle()
                onNodeWithTag("c").assertContentDescriptionEquals(texts[0]).assertIsOff()
            }
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun targetIsFiftySix() = runComposeUiTest {
        withTheme { SetCheck(false, {}, setNumber = 1, modifier = Modifier.testTag("c")) }
        waitForIdle()
        val touch = onNodeWithTag("c").fetchSemanticsNode().touchBoundsInRoot
        val target = with(density) { MoosclesTargets().workout.toPx() }
        assertTrue(touch.height >= target - 0.5f && touch.width >= target - 0.5f, "touch ${touch.width} x ${touch.height} < $target")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun checkAndUncheckFireTheirHaptics() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(false)
        withTheme(haptics = haptics) { SetCheck(checked, { checked = it }, setNumber = 1, modifier = Modifier.testTag("c")) }
        waitForIdle()
        onNodeWithTag("c").performClick()
        waitForIdle()
        onNodeWithTag("c").performClick()
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted, HapticEvent.SetUnchecked), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun entranceIsDrawnFromItsStartOnTheFirstFrame() {
        for (reduced in listOf(false, true)) {
            runComposeUiTest {
                mainClock.autoAdvance = false
                var checked by mutableStateOf(false)
                withTheme(reducedMotion = reduced) { SetCheck(checked, { checked = it }, setNumber = 1, modifier = Modifier.testTag("c")) }
                waitForIdle()
                fun drawn() = onNodeWithTag("c").fetchSemanticsNode().config.let { it[SetCheckScale] to it[SetCheckAlpha] }
                assertEquals(1f to 1f, drawn())
                checked = true
                Snapshot.sendApplyNotifications()
                mainClock.advanceTimeByFrame()
                // The first frame with the check already shows the start of the entrance (at most one
                // animation frame in), never the finished disc that then jumps back.
                val (scale, alpha) = drawn()
                if (reduced) {
                    assertTrue(alpha < 0.5f, "reduced: first frame drawn at alpha $alpha")
                    assertEquals(1f, scale)
                } else {
                    val from = MoosclesMotion().setCheckFrom
                    assertTrue(scale < (from + 1f) / 2f, "spring: first frame drawn at scale $scale, from $from")
                    assertEquals(1f, alpha)
                }
                mainClock.advanceTimeBy(2_000)
                val (restScale, restAlpha) = drawn()
                assertEquals(1f, restScale, 0.001f)
                assertEquals(1f, restAlpha, 0.001f)
            }
        }
    }
}
