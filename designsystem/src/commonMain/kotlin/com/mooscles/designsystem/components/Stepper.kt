package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.input.pointer.positionChangedIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.withTimeoutOrNull

const val StepperDecreaseTag = "stepper-decrease"
const val StepperIncreaseTag = "stepper-increase"
const val StepperValueTag = "stepper-value"

/**
 * A value changed in steps of [step] with a button on each side (64 x 56 dp each, always) and the
 * value between, in `num.lg`, or `num.md` or `num.sm` when that is all the room left fits (large text
 * in a narrow place). When not even `num.sm` fits between them, for [display] or for [widest] (the
 * widest value the caller expects to step to, so the arrangement does not change while stepping),
 * the value goes on its own line above the two buttons, whole, in the largest of the three that
 * fits the width; once there it stays there at that width. The buttons keep their identity either
 * way, so a hold in progress goes on. `BoxWithConstraints`
 * decides that, and it has no intrinsic measurement: keep the stepper out of `IntrinsicSize`
 * parents. A short tap is one step on release; holding is one step after `timing.stepperRepeatDelay`
 * and then one every `timing.stepperRepeatInterval`, and releasing adds nothing. Moving the finger past
 * touch slop, off the button or into a scroll cancels the press (no step, no more repeats), so a scroll
 * that starts on a button never changes the value. Every step gives the
 * `StepperStep` haptic. The value stays within [min] and [max], where the decrease or the increase
 * button is disabled.
 *
 * [onValueChangeFinished] is called once when a run of steps ends: after the step of a tap, a key
 * press or a screen reader's activation, and when the finger lifts (or the press is cancelled)
 * after a hold, however many steps it made. A press that made no step does not call it. A caller
 * that saves on every step can instead save only here.
 *
 * [contentDescription] names the whole stepper ("Editing load of set 4"); with [focusRequester]
 * the stepper itself is focusable and draws the focus ring when focused, so the caller can move
 * focus to it (its buttons stay focusable on their own, each with its ring). [display] is the text of [value]; by default the domain's plain decimal
 * ([MoosclesNumbers.plain], `20`, `82.5`) with the separators of [numberLocale] (`82,5` in pt-BR),
 * never `20.0`.
 */
@Composable
fun Stepper(
    value: Double,
    onValueChange: (Double) -> Unit,
    step: Double,
    min: Double,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    numberLocale: NumberLocale = currentNumberLocale(),
    display: String = MoosclesNumbers.localize(MoosclesNumbers.plain(value), numberLocale),
    max: Double = Double.POSITIVE_INFINITY,
    onValueChangeFinished: () -> Unit = {},
    focusRequester: FocusRequester? = null,
    contentDescription: String? = null,
    widest: String = display,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val haptics = MTheme.haptics
    // A step; true when it changed the value. The value the caller gets is always within min..max.
    val change: (Int) -> Boolean = { direction ->
        val next = (currentValue + direction * step).coerceAtMost(max).coerceAtLeast(min)
        if (next != currentValue) {
            haptics.perform(HapticEvent.StepperStep)
            currentOnChange(next)
            true
        } else {
            false
        }
    }
    val finished = { currentOnFinished() }
    val groupSource = remember { MutableInteractionSource() }
    var group: Modifier = Modifier
    if (contentDescription != null) group = group.semantics { this.contentDescription = contentDescription }
    // A focusable group draws the focus ring like every other focusable (WCAG 2.4.7).
    if (focusRequester != null) {
        group = group
            .focusRequester(focusRequester)
            .focusable(interactionSource = groupSource)
            .mFocusRing(RoundedCornerShape(MTheme.radius.md), groupSource)
    }
    val type = MTheme.type
    val measurer = rememberTextMeasurer()
    val gap = MTheme.targets.gap
    val buttons = with(LocalDensity.current) { (MTheme.sizes.stepperButtonWidth * 2 + gap * 2).roundToPx() }
    val minus: @Composable () -> Unit = {
        StepperButton(MIcons.Minus, decreaseLabel, enabled = value > min, tag = StepperDecreaseTag, step = { change(-1) }, finished = finished)
    }
    val plus: @Composable () -> Unit = {
        StepperButton(MIcons.Plus, increaseLabel, enabled = value < max, tag = StepperIncreaseTag, step = { change(1) }, finished = finished)
    }
    BoxWithConstraints(modifier.then(group), contentAlignment = Alignment.Center) {
        val candidates = listOf(type.numLg, type.numMd, type.numSm)
        val width = constraints.maxWidth
        fun fitting(text: String, room: Int) =
            candidates.firstOrNull { measurer.measure(text, it, softWrap = false, maxLines = 1).size.width <= room }
        val room = width - buttons
        // Above once, above for good at this width: the layout never flips back while stepping.
        val wentAbove = remember(width) { booleanArrayOf(false) }
        val between = !constraints.hasBoundedWidth ||
            (!wentAbove[0] && fitting(widest, room) != null && fitting(display, room) != null)
        if (!between) wentAbove[0] = true
        val style = when {
            !constraints.hasBoundedWidth -> type.numLg
            between -> fitting(display, room) ?: type.numSm
            else -> fitting(display, width) ?: type.numSm
        }
        val gapPx = with(LocalDensity.current) { gap.roundToPx() }
        // One layout for both arrangements, so the buttons keep their identity (and a press in
        // progress) even if the arrangement changes.
        Layout(content = {
            minus()
            StepperValue(display, style)
            plus()
        }) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val left = measurables[0].measure(loose)
            val right = measurables[2].measure(loose)
            val text = measurables[1].measure(loose)
            if (between) {
                val w = left.width + gapPx + text.width + gapPx + right.width
                val h = maxOf(left.height, text.height, right.height)
                layout(w, h) {
                    left.placeRelative(0, (h - left.height) / 2)
                    text.placeRelative(left.width + gapPx, (h - text.height) / 2)
                    right.placeRelative(w - right.width, (h - right.height) / 2)
                }
            } else {
                // The value on its own line, the two buttons side by side under it.
                val pair = left.width + gapPx + right.width
                val w = maxOf(pair, text.width)
                val row = maxOf(left.height, right.height)
                val h = text.height + gapPx + row
                layout(w, h) {
                    text.placeRelative((w - text.width) / 2, 0)
                    val start = (w - pair) / 2
                    left.placeRelative(start, text.height + gapPx)
                    right.placeRelative(start + left.width + gapPx, text.height + gapPx)
                }
            }
        }
    }
}

/** The value, on one line in [style] (chosen by [Stepper] to fit). */
@Composable
private fun StepperValue(display: String, style: TextStyle) {
    MText(display, style, modifier = Modifier.testTag(StepperValueTag), softWrap = false, maxLines = 1)
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    step: () -> Boolean,
    finished: () -> Unit,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val currentStep by rememberUpdatedState(step)
    val currentFinished by rememberUpdatedState(finished)
    val repeatDelay = MTheme.timing.stepperRepeatDelay
    val repeatInterval = MTheme.timing.stepperRepeatInterval
    val shape = RoundedCornerShape(MTheme.radius.md)
    Box(
        Modifier
            .testTag(tag)
            .sizeIn(minWidth = MTheme.sizes.stepperButtonWidth, minHeight = MTheme.sizes.stepperButtonHeight)
            .mPressScale(source, enabled)
            .semantics {
                contentDescription = label
                role = Role.Button
                if (enabled) onClick { if (currentStep()) currentFinished(); true } else disabled()
            }
            .onKeyEvent {
                // One step per press, on key up; Enter, NumPad Enter and Space.
                val activates = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
                if (enabled && activates && it.type == KeyEventType.KeyUp) {
                    if (currentStep()) currentFinished()
                    true
                } else {
                    false
                }
            }
            .focusable(enabled, source)
            .pointerInput(enabled, repeatDelay, repeatInterval) {
                if (!enabled) return@pointerInput
                // The timing starts at the finger going down and ends synchronously when it lifts,
                // not at a recomposition. The press ends without a step (and stops repeating) when
                // the finger moves past touch slop, leaves the button, or a scrolling parent
                // consumes the movement: a scroll that starts on a button never changes the value.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val press = PressInteraction.Press(down.position)
                    source.tryEmit(press)
                    var released = false
                    // Whether this press changed the value: then it ends with onValueChangeFinished, once.
                    var stepped = false
                    try {
                        val first = withTimeoutOrNull(repeatDelay) { awaitReleaseOrCancel(down) }
                        if (first != null) {
                            if (first) {
                                released = true
                                stepped = currentStep()
                            }
                        } else {
                            stepped = currentStep()
                            while (true) {
                                val end = withTimeoutOrNull(repeatInterval) { awaitReleaseOrCancel(down) }
                                if (end == null) {
                                    stepped = currentStep() || stepped
                                } else {
                                    released = end
                                    break
                                }
                            }
                        }
                    } finally {
                        source.tryEmit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        if (stepped) currentFinished()
                    }
                }
            }
            .mFocusRing(shape, source)
            .background(if (enabled) colors.bgSurface else colors.bgInset, shape)
            .border(MTheme.sizes.border, if (enabled) colors.borderStrong else colors.borderSubtle, shape),
        contentAlignment = Alignment.Center,
    ) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.icon24, tint = if (enabled) colors.textPrimary else colors.textDisabled)
    }
}

/**
 * Suspends until the press ends: `true` when every pointer lifted on the button, `false` when it is
 * cancelled because a pointer moved past touch slop from [down], left the button, or had its
 * movement consumed by another node (a scrolling parent consumes on the Main pass, after this
 * child, so that is checked on the Final pass). A consumed down (a clickable parent) does not cancel.
 */
private suspend fun AwaitPointerEventScope.awaitReleaseOrCancel(down: PointerInputChange): Boolean {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.fastAll { !it.pressed }) {
            return event.changes.fastAll { !it.isOutOfBounds(size, extendedTouchPadding) }
        }
        val cancelled = event.changes.fastAny {
            it.isOutOfBounds(size, extendedTouchPadding) ||
                (it.id == down.id && (it.position - down.position).getDistance() > slop) ||
                (it.isConsumed && it.positionChangedIgnoreConsumed())
        }
        if (cancelled) return false
        val final = awaitPointerEvent(PointerEventPass.Final)
        if (final.changes.fastAny { it.isConsumed && it.positionChangedIgnoreConsumed() }) return false
    }
}
