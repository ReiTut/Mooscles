package com.mooscles.designsystem.components

import com.mooscles.designsystem.format.NumberLocale
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesSizes
import com.mooscles.designsystem.withTheme
import com.mooscles.designsystem.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.designsystem.assertShownWhole
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.interaction.FocusRingVisible
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StepperTest {
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun stepChangesValueAndTicks() = runComposeUiTest {
        val haptics = RecordingHaptics()
        var value by mutableStateOf(20.0)
        withTheme(haptics = haptics) {
            Stepper(value, { value = it }, step = 2.5, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
        }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(22.5, value)
        assertEquals(listOf(HapticEvent.StepperStep), haptics.events.toList())
        onNodeWithTag(StepperDecreaseTag).performClick()
        waitForIdle()
        assertEquals(20.0, value)
        assertEquals(listOf(HapticEvent.StepperStep, HapticEvent.StepperStep), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun minimumDisablesDecrease() = runComposeUiTest {
        withTheme { Stepper(0.0, {}, step = 2.5, min = 0.0, display = "0", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        onNodeWithTag(StepperDecreaseTag).assertIsNotEnabled()
        onNodeWithTag(StepperIncreaseTag).assertIsEnabled()
    }

    /** Timing is read from the test clock at each step, 1 ms at a time, so no assertion sits on a frame boundary. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdRepeatsAfterFourHundredThenEveryEighty() = runComposeUiTest {
        val times = mutableListOf<Long>()
        var value by mutableStateOf(0.0)
        withTheme {
            Stepper(value, { value = it; times += mainClock.currentTime }, step = 1.0, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
        }
        waitForIdle()
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        val start = mainClock.currentTime
        repeat(700) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertTrue(times.size >= 4, "steps at ${times.map { it - start }}")
        val offsets = times.map { it - start }
        assertTrue(offsets[0] in 398..416, "first repeat at ${offsets[0]} ms, expected about 400")
        for (i in 1 until offsets.size) {
            val gap = offsets[i] - offsets[i - 1]
            assertTrue(gap in 64..96, "gap $i is $gap ms, expected about 80")
        }
        val before = times.size
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(before, times.size, "a step happened after the finger lifted")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsAreLabelledWithTheStep() = runComposeUiTest {
        withTheme {
            Stepper(
                20.0, {}, step = 2.5, min = 0.0, display = "20",
                decreaseLabel = "Decrease load by 2.5 kilograms", increaseLabel = "Increase load by 2.5 kilograms",
            )
        }
        waitForIdle()
        onNodeWithContentDescription("Increase load by 2.5 kilograms").assertExists()
        onNodeWithContentDescription("Decrease load by 2.5 kilograms").assertExists()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsMeetTheWorkoutTarget() = runComposeUiTest {
        withTheme { Stepper(20.0, {}, step = 2.5, min = 0.0, display = "20", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        val sizes = MoosclesSizes()
        for (tag in listOf(StepperDecreaseTag, StepperIncreaseTag)) {
            val touch = onNodeWithTag(tag).fetchSemanticsNode().touchBoundsInRoot
            val height = with(density) { sizes.stepperButtonHeight.toPx() }
            val width = with(density) { sizes.stepperButtonWidth.toPx() }
            assertTrue(touch.height >= height - 0.5f, "$tag height ${touch.height} < $height")
            assertTrue(touch.width >= width - 0.5f, "$tag width ${touch.width} < $width")
        }
    }

    /**
     * At 200% in a narrow place (the set edit panel beside its keyboard button), the value used to
     * take the room of the increase button, which collapsed to 0 wide (found by the active workout
     * flow, Task 28). The buttons keep their 64 x 56 dp and the value steps down to the largest of
     * `num.lg`, `num.md` and `num.sm` that it fits in, whole.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun buttonsKeepTheirSizeWhenTheValueIsWide() {
        var narrow = 0f
        runComposeUiTest {
            withTheme(fontScale = LINEAR_TWO_X_FONT_SCALE) {
                Box(Modifier.width(260.dp)) {
                    Stepper(87.5, {}, step = 2.5, min = 0.0, display = "87.5 kg", decreaseLabel = "Decrease", increaseLabel = "Increase")
                }
            }
            waitForIdle()
            val sizes = MoosclesSizes()
            for (tag in listOf(StepperDecreaseTag, StepperIncreaseTag)) {
                val bounds = onNodeWithTag(tag).fetchSemanticsNode().size
                val width = with(density) { sizes.stepperButtonWidth.toPx() }
                val height = with(density) { sizes.stepperButtonHeight.toPx() }
                assertTrue(bounds.width >= width - 0.5f && bounds.height >= height - 0.5f, "$tag is $bounds px, at least $width x $height expected")
            }
            val layout = valueLayout()
            assertShownWhole(layout, "stepper value")
            onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("87.5 kg")
            narrow = layout.layoutInput.style.fontSize.value
        }
        // With room, the value stays num.lg (44 sp); the narrow one above stepped down from it.
        runComposeUiTest {
            withTheme { Stepper(87.5, {}, step = 2.5, min = 0.0, display = "87.5 kg", decreaseLabel = "Decrease", increaseLabel = "Increase") }
            waitForIdle()
            val roomy = valueLayout().layoutInput.style.fontSize.value
            assertEquals(44f, roomy, "num.lg with room")
            assertTrue(narrow < roomy, "the narrow value stepped down: $narrow sp")
        }
    }

    /**
     * Where not even `num.sm` fits between the buttons ("82,5 kg" in the set edit panel of a 320 dp
     * phone, already at 1.0: the accessibility sweep, Task 29), the value goes on its own line above
     * them, whole, and the buttons keep their 64 x 56 dp side by side under it.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun valueGoesAboveTheButtonsWhenItCannotFitBetween() {
        for ((scale, width) in listOf(1f to 170.dp, LINEAR_TWO_X_FONT_SCALE to 230.dp)) {
            runComposeUiTest {
                withTheme(fontScale = scale) {
                    Box(Modifier.width(width)) {
                        Stepper(82.5, {}, step = 2.5, min = 0.0, display = "82,5 kg", decreaseLabel = "Decrease", increaseLabel = "Increase")
                    }
                }
                waitForIdle()
                val where = "at $scale in $width"
                val sizes = MoosclesSizes()
                val minus = onNodeWithTag(StepperDecreaseTag).getBoundsInRoot()
                val plus = onNodeWithTag(StepperIncreaseTag).getBoundsInRoot()
                val value = onNodeWithTag(StepperValueTag, useUnmergedTree = true).getBoundsInRoot()
                for ((tag, bounds) in listOf("decrease" to minus, "increase" to plus)) {
                    assertTrue(bounds.right - bounds.left >= sizes.stepperButtonWidth - 0.5.dp && bounds.bottom - bounds.top >= sizes.stepperButtonHeight - 0.5.dp, "$where $tag is $bounds")
                    assertTrue(bounds.right <= width + 0.5.dp, "$where $tag ends at ${bounds.right}")
                }
                assertTrue(value.bottom <= minus.top + 0.5.dp && value.bottom <= plus.top + 0.5.dp, "$where the value $value is not above the buttons $minus $plus")
                assertTrue(minus.right <= plus.left + 0.5.dp && minus.top == plus.top, "$where the buttons are not side by side: $minus $plus")
                assertShownWhole(valueLayout(), "stepper value $where")
                onNodeWithTag(StepperValueTag, useUnmergedTree = true).assertTextEquals("82,5 kg")
            }
        }
        // With room, the value stays between the buttons.
        runComposeUiTest {
            withTheme { Stepper(82.5, {}, step = 2.5, min = 0.0, display = "82,5 kg", decreaseLabel = "Decrease", increaseLabel = "Increase") }
            waitForIdle()
            val minus = onNodeWithTag(StepperDecreaseTag).getBoundsInRoot()
            val plus = onNodeWithTag(StepperIncreaseTag).getBoundsInRoot()
            val value = onNodeWithTag(StepperValueTag, useUnmergedTree = true).getBoundsInRoot()
            assertTrue(minus.right <= value.left && value.right <= plus.left, "the value is not between the buttons: $minus $value $plus")
        }
    }

    /**
     * A box where "97.5" fits between the buttons in `num.sm` and "102.5" does not (half way between
     * their widths), with a stepper from [start] by 2.5 showing its plain value.
     */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.thresholdStepper(start: Double, widest: String?, onValue: (Double) -> Unit = {}): () -> Double {
        var value by mutableStateOf(start)
        withTheme {
            val sizes = MoosclesSizes()
            val measurer = rememberTextMeasurer()
            val style = MTheme.type.numSm
            val density = LocalDensity.current
            val buttons = with(density) { (sizes.stepperButtonWidth * 2 + MTheme.targets.gap * 2).roundToPx() }
            val narrow = measurer.measure("97.5", style, softWrap = false, maxLines = 1).size.width
            val wide = measurer.measure("102.5", style, softWrap = false, maxLines = 1).size.width
            Box(Modifier.width(with(density) { (buttons + (narrow + wide) / 2).toDp() })) {
                val display = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                Stepper(
                    value, { value = it; onValue(it) }, step = 2.5, min = 0.0, display = display,
                    decreaseLabel = "Decrease", increaseLabel = "Increase", widest = widest ?: display,
                )
            }
        }
        waitForIdle()
        return { value }
    }

    /**
     * Holding + from 92.5 past the width where the value no longer fits between the buttons (102.5)
     * neither moves the buttons nor stops the hold: given the widest value it will show, the
     * stepper keeps one arrangement (review I1 of Task 29).
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun steppingPastTheWidthThresholdKeepsTheButtonsAndTheHold() = runComposeUiTest {
        val value = thresholdStepper(92.5, widest = "102.5")
        val minus = onNodeWithTag(StepperDecreaseTag).getBoundsInRoot()
        val plus = onNodeWithTag(StepperIncreaseTag).getBoundsInRoot()
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        var seen = 0
        repeat(1_000) {
            mainClock.advanceTimeBy(1, ignoreFrameDuration = true)
            if (it % 50 == 0) {
                assertEquals(plus, onNodeWithTag(StepperIncreaseTag).getBoundsInRoot(), "the increase button moved at ${value()}")
                assertEquals(minus, onNodeWithTag(StepperDecreaseTag).getBoundsInRoot(), "the decrease button moved at ${value()}")
                seen++
            }
        }
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(100) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        mainClock.autoAdvance = true
        assertTrue(value() >= 105.0, "the hold stopped at ${value()}")
        assertTrue(seen > 0)
    }

    /**
     * Without a widest value, the arrangement changes at most once: stepping up past the threshold
     * puts the value above the buttons, and stepping back down leaves it there.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun onceAboveTheValueStaysAboveAtThatWidth() = runComposeUiTest {
        val value = thresholdStepper(97.5, widest = null)
        val between = onNodeWithTag(StepperIncreaseTag).getBoundsInRoot()
        repeat(2) {
            onNodeWithTag(StepperIncreaseTag).performClick()
            waitForIdle()
        }
        assertEquals(102.5, value())
        val above = onNodeWithTag(StepperIncreaseTag).getBoundsInRoot()
        assertTrue(above.top > between.top, "102.5 did not go above the buttons: $between, then $above")
        repeat(2) {
            onNodeWithTag(StepperDecreaseTag).performClick()
            waitForIdle()
        }
        assertEquals(97.5, value())
        assertEquals(above, onNodeWithTag(StepperIncreaseTag).getBoundsInRoot(), "the buttons moved back")
    }

    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.valueLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val layout = onNodeWithTag(StepperValueTag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action
        assertTrue(layout?.invoke(results) == true && results.isNotEmpty(), "the value has no text layout")
        return results.first()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun keyboardActivatesTheFocusedButton() = runComposeUiTest {
        var value by mutableStateOf(0.0)
        withTheme { Stepper(value, { value = it }, step = 1.0, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase") }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).requestFocus()
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1.0, value)
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Spacebar) }
        waitForIdle()
        assertEquals(2.0, value)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun defaultDisplayIsTheDomainDecimalNeverDoubleToString() {
        for ((locale, value, expected) in listOf(
            Triple(NumberLocale.EN, 20.0, "20"), Triple(NumberLocale.EN, 82.5, "82.5"),
            Triple(NumberLocale.PT_BR, 20.0, "20"), Triple(NumberLocale.PT_BR, 82.5, "82,5"),
        )) {
            runComposeUiTest {
                withTheme {
                    Stepper(value, {}, step = 2.5, min = 0.0, decreaseLabel = "Decrease", increaseLabel = "Increase", numberLocale = locale)
                }
                waitForIdle()
                onNodeWithTag(StepperValueTag).assertTextEquals(expected)
            }
        }
    }

    /** A Stepper between two spacers in a scrolling column 300 dp tall, so a vertical drag on a button really scrolls. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.scrollingStepper(haptics: RecordingHaptics, onChange: (Double) -> Unit): () -> ScrollState {
        var scroll: ScrollState? = null
        var value by mutableStateOf(20.0)
        withTheme(haptics = haptics) {
            val state = rememberScrollState().also { scroll = it }
            Column(Modifier.height(300.dp).verticalScroll(state)) {
                Spacer(Modifier.height(100.dp))
                Stepper(value, { value = it; onChange(it) }, step = 2.5, min = 0.0, display = "$value", decreaseLabel = "Decrease", increaseLabel = "Increase")
                Spacer(Modifier.height(2000.dp))
            }
        }
        waitForIdle()
        return { scroll!! }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun fastFlickStartingOnAButtonScrollsWithoutAStep() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val changes = mutableListOf<Double>()
        val scroll = scrollingStepper(haptics) { changes += it }
        for (tag in listOf(StepperIncreaseTag, StepperDecreaseTag)) {
            onNodeWithTag(tag).performTouchInput {
                swipeUp(startY = centerY, endY = centerY - 60.dp.toPx(), durationMillis = 100)
            }
            waitForIdle()
        }
        assertTrue(scroll().value > 0, "the drag did not scroll the column")
        assertEquals(emptyList(), changes, "a flick that scrolled changed the value")
        assertEquals(emptyList(), haptics.events.toList())
    }

    /** One second of slow drag, 20 ms per move, so the 400 ms hold delay passes while the finger is still down. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun slowDragStartingOnAButtonScrollsWithoutAStep() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val changes = mutableListOf<Double>()
        val scroll = scrollingStepper(haptics) { changes += it }
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(50) {
            mainClock.advanceTimeBy(20, ignoreFrameDuration = true)
            onNodeWithTag(StepperIncreaseTag).performTouchInput { moveBy(Offset(0f, -4f)) }
        }
        mainClock.advanceTimeBy(20, ignoreFrameDuration = true)
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertTrue(scroll().value > 0, "the drag did not scroll the column")
        assertEquals(emptyList(), changes, "a slow drag that scrolled changed the value")
        assertEquals(emptyList(), haptics.events.toList())
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun movingAwayDuringAHoldStopsTheRepeat() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        scrollingStepper(RecordingHaptics()) { changes += it }
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(450) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf(22.5), changes, "the hold did not step once at 400 ms")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { moveBy(Offset(0f, -60.dp.toPx())) }
        repeat(400) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(100) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf(22.5), changes, "the repeat went on after the finger moved away")
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tapWithAJitterUnderTouchSlopStillSteps() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        scrollingStepper(RecordingHaptics()) { changes += it }
        onNodeWithTag(StepperIncreaseTag).performTouchInput {
            down(center)
            moveBy(Offset(1f, -1f))
            up()
        }
        waitForIdle()
        assertEquals(listOf(22.5), changes)
    }

    /** A stepper that logs each change and each finish, in order. */
    @OptIn(ExperimentalTestApi::class)
    private fun ComposeUiTest.loggingStepper(log: MutableList<String>, start: Double = 20.0, max: Double = Double.POSITIVE_INFINITY) {
        var value by mutableStateOf(start)
        withTheme {
            Stepper(
                value, { value = it; log += "change:$it" }, step = 2.5, min = 0.0, display = "$value",
                decreaseLabel = "Decrease", increaseLabel = "Increase", max = max,
                onValueChangeFinished = { log += "finished" },
            )
        }
        waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun tapFinishesOnceAfterTheStep() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("change:22.5", "finished"), log)
        onNodeWithTag(StepperIncreaseTag).requestFocus()
        onNodeWithTag(StepperIncreaseTag).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("change:22.5", "finished", "change:25.0", "finished"), log)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdFinishesOnceOnRelease() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        // Steps at about 400, 480 and 560 ms; the next would be at 640.
        repeat(600) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf("change:22.5", "change:25.0", "change:27.5"), log, "while held")
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(200) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf("change:22.5", "change:25.0", "change:27.5", "finished"), log)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun semanticActionStepsAndFinishes() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log)
        onNodeWithTag(StepperDecreaseTag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
        assertEquals(listOf("change:17.5", "finished"), log)
    }

    /** A scroll that starts on a button makes no step, so nothing is finished either. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aScrollDoesNotFinish() = runComposeUiTest {
        val changes = mutableListOf<Double>()
        var finished = 0
        var value by mutableStateOf(20.0)
        withTheme {
            Column(Modifier.height(300.dp).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(100.dp))
                Stepper(
                    value, { value = it; changes += it }, step = 2.5, min = 0.0, display = "$value",
                    decreaseLabel = "Decrease", increaseLabel = "Increase", onValueChangeFinished = { finished++ },
                )
                Spacer(Modifier.height(2000.dp))
            }
        }
        waitForIdle()
        onNodeWithTag(StepperIncreaseTag).performTouchInput { swipeUp(startY = centerY, endY = centerY - 60.dp.toPx(), durationMillis = 100) }
        waitForIdle()
        assertEquals(emptyList(), changes)
        assertEquals(0, finished)
    }

    /** Pre-flight F6: the value stops at [max], where the increase button is disabled. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun maxDisablesIncreaseAndClampsTheLastStep() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log, start = 299.0, max = 300.0)
        onNodeWithTag(StepperIncreaseTag).assertIsEnabled()
        onNodeWithTag(StepperIncreaseTag).performClick()
        waitForIdle()
        assertEquals(listOf("change:300.0", "finished"), log)
        onNodeWithTag(StepperIncreaseTag).assertIsNotEnabled()
        onNodeWithTag(StepperDecreaseTag).assertIsEnabled()
    }

    /** Pre-flight F6: the whole stepper has a name and can take the focus ("Editing load of set 4"). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun groupHasItsNameAndTakesFocus() = runComposeUiTest {
        val focus = FocusRequester()
        withTheme {
            Stepper(
                85.0, {}, step = 2.5, min = 0.0, decreaseLabel = "Decrease", increaseLabel = "Increase",
                focusRequester = focus, contentDescription = "Editing load of set 4",
            )
        }
        waitForIdle()
        runOnUiThread { focus.requestFocus() }
        waitForIdle()
        onNodeWithContentDescription("Editing load of set 4").assertIsFocused()
        onNodeWithContentDescription("Increase").assertExists()
        onNodeWithContentDescription("Decrease").assertExists()
    }

    /** Review M4: holding up to [max] ends the run there, with one finish, and lifting adds none. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun holdToTheMaxFinishesOnce() = runComposeUiTest {
        val log = mutableListOf<String>()
        loggingStepper(log, start = 295.0, max = 300.0)
        mainClock.autoAdvance = false
        onNodeWithTag(StepperIncreaseTag).performTouchInput { down(center) }
        repeat(700) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        onNodeWithTag(StepperIncreaseTag).performTouchInput { up() }
        repeat(200) { mainClock.advanceTimeBy(1, ignoreFrameDuration = true) }
        assertEquals(listOf("change:297.5", "change:300.0", "finished"), log)
        onNodeWithTag(StepperIncreaseTag).assertIsNotEnabled()
    }

    /** Review M3: the focusable group and each button draw the focus ring when focused (WCAG 2.4.7). */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun focusedGroupAndButtonsDrawTheRing() = runComposeUiTest {
        val focus = FocusRequester()
        withTheme {
            Stepper(
                85.0, {}, step = 2.5, min = 0.0, decreaseLabel = "Decrease", increaseLabel = "Increase",
                focusRequester = focus, contentDescription = "Editing load of set 4",
            )
        }
        waitForIdle()
        fun ring(node: androidx.compose.ui.test.SemanticsNodeInteraction) = node.fetchSemanticsNode().config.getOrNull(FocusRingVisible)
        val group = onNodeWithContentDescription("Editing load of set 4")
        assertEquals(false, ring(group))
        runOnUiThread { focus.requestFocus() }
        waitForIdle()
        assertEquals(true, ring(group))
        for (tag in listOf(StepperIncreaseTag, StepperDecreaseTag)) {
            onNodeWithTag(tag).requestFocus()
            waitForIdle()
            assertEquals(true, ring(onNodeWithTag(tag)), tag)
            assertEquals(false, ring(group), "the group keeps its ring while $tag has focus")
        }
    }
}
