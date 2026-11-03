package com.mooscles.designsystem.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/** How far, as a share of the row's width, a set row must be dragged for the swipe to count. */
const val SetRowSwipeThreshold = 0.35f

/** Where a swiped row can be: at rest, or dragged a whole width toward completing or removing. */
private enum class SwipeAnchor { Center, Complete, Remove }

/**
 * Lets a set row be swiped sideways: released past [SetRowSwipeThreshold] of its width to the
 * right it calls [onComplete], to the left [onRemove]; shorter, it calls nothing. Either way it
 * goes back to the center when the finger lifts (a snappy spring, instant with reduced motion).
 * Crossing the threshold, in either direction, gives the `StepperStep` haptic. Only the release
 * position counts, never the speed, so a quick flick that stays short does nothing. A drag the
 * system cancels (a call screen, an overlay) goes back to the center without calling anything.
 * The drag only starts after a horizontal touch slop, so a vertical scroll of the list that starts
 * on the row stays a scroll.
 *
 * Each side has its own switch. With [completeEnabled] false the row does not move to the right
 * at all (no motion, no haptic): a completed set passes false, so a swipe never unchecks it. With
 * [removeEnabled] false the same holds to the left. Both actions also need a tap path and an
 * accessibility action (the row's check and its "Remove set" action).
 */
fun Modifier.setRowSwipe(
    completeEnabled: Boolean,
    removeEnabled: Boolean,
    onComplete: () -> Unit,
    onRemove: () -> Unit,
): Modifier = composed {
    val haptics = MTheme.haptics
    val reduced = MTheme.reducedMotion
    val snappy = MTheme.motion.springSnappy
    val complete by rememberUpdatedState(onComplete)
    val remove by rememberUpdatedState(onRemove)
    val state = remember { AnchoredDraggableState(SwipeAnchor.Center) }
    var width by remember { mutableIntStateOf(0) }
    val limit = { width * SetRowSwipeThreshold }
    val drags = remember { LastInteractionSource() }
    val back: AnimationSpec<Float> = if (reduced) snap() else spring(snappy.dampingRatio, snappy.stiffness)
    val fling = remember(state, back) {
        SettleToCenter(state, back) { released ->
            // processDragCancel emits Cancel before it stops the drag: a cancelled drag never commits.
            if (drags.last is DragInteraction.Cancel) return@SettleToCenter
            val threshold = limit()
            when {
                threshold <= 0f -> Unit
                released >= threshold -> complete()
                released <= -threshold -> remove()
            }
        }
    }
    LaunchedEffect(state, width, completeEnabled, removeEnabled) {
        // Only the enabled sides get an anchor; the offset is clamped to the anchors, so a
        // disabled side neither moves nor reaches the threshold.
        // New anchors wait for the settle to end: the state restarts a running anchoredDrag when
        // they change, which would run the fling (and its commit) again. A set completed by this
        // swipe turns completeEnabled off while the row is still going back.
        snapshotFlow { fling.settling }.first { !it }
        val full = width.toFloat()
        state.updateAnchors(
            DraggableAnchors {
                SwipeAnchor.Center at 0f
                if (completeEnabled && full > 0f) SwipeAnchor.Complete at full
                if (removeEnabled && full > 0f) SwipeAnchor.Remove at -full
            },
        )
    }
    LaunchedEffect(state) {
        // -1, 0 or 1: which side of the threshold the row is on; a tick each time it gets past it.
        sides(state, limit).collect { side -> if (side != 0) haptics.perform(HapticEvent.StepperStep) }
    }
    this
        .onSizeChanged { width = it.width }
        .anchoredDraggable(
            state,
            Orientation.Horizontal,
            enabled = completeEnabled || removeEnabled,
            interactionSource = drags,
            flingBehavior = fling,
        )
        .offset {
            val offset = state.offset
            IntOffset(if (offset.isNaN()) 0 else offset.roundToInt(), 0)
        }
}

private fun sides(state: AnchoredDraggableState<SwipeAnchor>, limit: () -> Float): Flow<Int> = snapshotFlow {
    val offset = state.offset
    val threshold = limit()
    when {
        offset.isNaN() || threshold <= 0f -> 0
        offset >= threshold -> 1
        offset <= -threshold -> -1
        else -> 0
    }
}.distinctUntilChanged()

/** An interaction source that also remembers, synchronously, the last interaction emitted to it. */
private class LastInteractionSource(
    private val delegate: MutableInteractionSource = MutableInteractionSource(),
) : MutableInteractionSource by delegate {
    var last: Interaction? = null
        private set

    override suspend fun emit(interaction: Interaction) {
        last = interaction
        delegate.emit(interaction)
    }

    override fun tryEmit(interaction: Interaction): Boolean {
        last = interaction
        return delegate.tryEmit(interaction)
    }
}

/**
 * Ends every drag at the center: reports where the finger lifted to [onRelease], then animates the
 * offset back to 0 with [spec], ignoring the fling velocity. `scrollBy` here is in the state's
 * offset space (only the velocity is reversed for RTL), so it moves the offset toward 0 everywhere.
 */
private class SettleToCenter(
    private val state: AnchoredDraggableState<SwipeAnchor>,
    private val spec: AnimationSpec<Float>,
    private val onRelease: (Float) -> Unit,
) : FlingBehavior {
    /** True from the release until the row is back at the center. */
    var settling by mutableStateOf(false)
        private set

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val released = state.offset
        if (released.isNaN()) return 0f
        settling = true
        try {
            onRelease(released)
            var current = released
            animate(released, 0f, animationSpec = spec) { value, _ ->
                scrollBy(value - current)
                current = value
            }
        } finally {
            settling = false
        }
        return 0f
    }
}
