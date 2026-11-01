package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import com.mooscles.designsystem.theme.MTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal const val SheetTag = "sheet"
internal const val SheetScrimTag = "sheet-scrim"

private enum class SheetValue { Hidden, Shown }

/** How many frames [MBottomSheet] waits for its `initialFocus` to be attached. */
private const val INITIAL_FOCUS_FRAMES = 60

/**
 * A bottom sheet with a 32 x 4 handle, `radius.xl` top corners and a height of at most 90% of
 * the screen (`sizes.sheetMaxFraction`). It closes with Back/Escape, a tap on the scrim and by
 * dragging it down (`anchoredDraggable`); [onDismiss] runs once, after it has left the screen.
 * The caller removes the sheet from composition in [onDismiss].
 *
 * Like [MDialog] it is a common `Dialog` window (no Material 3), full screen with our own
 * scrim, so it works unchanged on Android and iOS. It enters with `motion.slow` and
 * `ease.drawer` and leaves with `motion.slowExit`; with reduced motion it fades instead of
 * sliding. The content is laid out in a plain column: a long list should scroll itself
 * (the drag listens on the whole sheet, so keep scrollable content out of the handle area).
 *
 * [initialFocus], a requester on a field of the content, takes the focus once the sheet is open, in place of the
 * sheet itself (which has it until then, so Escape and Back reach the sheet): asked once, so a focus that moves
 * away afterwards is never pulled back.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MTheme.colors
    val motion = MTheme.motion
    val reduced = MTheme.reducedMotion
    val currentDismiss by rememberUpdatedState(onDismiss)
    val state = remember { AnchoredDraggableState(SheetValue.Hidden) }
    val fade = remember { Animatable(if (reduced) 0f else 1f) }
    var closing by remember { mutableStateOf(false) }
    var sheetHeight by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val enterSpec = tween<Float>(motion.slow, easing = motion.easeDrawer)

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            if (reduced) {
                fade.animateTo(0f, tween(motion.fast, easing = motion.easeOut))
            } else {
                state.animateTo(SheetValue.Hidden, tween(motion.slowExit, easing = motion.easeOut))
            }
            currentDismiss()
        }
    }

    LaunchedEffect(sheetHeight) {
        if (sheetHeight > 0) {
            val h = sheetHeight.toFloat()
            state.updateAnchors(
                DraggableAnchors {
                    SheetValue.Hidden at h
                    SheetValue.Shown at 0f
                },
            )
        }
    }
    LaunchedEffect(sheetHeight > 0) {
        if (sheetHeight > 0 && !closing) {
            if (reduced) {
                state.snapTo(SheetValue.Shown)
                fade.animateTo(1f, tween(motion.fast, easing = motion.easeOut))
            } else {
                state.animateTo(SheetValue.Shown, enterSpec)
            }
        }
    }
    // A drag that settles on Hidden dismisses; the first, initial Hidden does not (not shown yet).
    LaunchedEffect(Unit) {
        var shownOnce = false
        snapshotFlow { state.settledValue }.collect { value ->
            if (value == SheetValue.Shown) shownOnce = true
            if (value == SheetValue.Hidden && shownOnce && !closing) {
                closing = true
                currentDismiss()
            }
        }
    }
    MOverlayWindow(onDismissRequest = { close() }) {
        // Inside the Dialog's own composition, so the requester is attached before it is used.
        LaunchedEffect(Unit) {
            focus.requestFocus()
            if (initialFocus != null) {
                // The content is laid out in the box below, which composes it during layout: the field is not
                // attached when this effect starts, so ask once per frame until it is, then never again.
                var tries = 0
                while (tries < INITIAL_FOCUS_FRAMES) {
                    withFrameNanos { }
                    val asked: Any = try {
                        initialFocus.requestFocus()
                    } catch (_: IllegalStateException) {
                        false
                    }
                    if (asked != false) break
                    tries++
                }
            }
        }
        BoxWithConstraints(
            Modifier.fillMaxSize().overlayKeys { close() }.focusRequester(focus).focusable(),
        ) {
            val offset = state.offset
            val hidden = offset.isNaN() || sheetHeight == 0
            val shownFraction = when {
                hidden -> 0f
                sheetHeight > 0 -> (1f - offset / sheetHeight).coerceIn(0f, 1f)
                else -> 0f
            }
            OverlayScrim(SheetScrimTag, if (reduced) fade.value else shownFraction) { close() }
            val shape = RoundedCornerShape(topStart = MTheme.radius.xl, topEnd = MTheme.radius.xl)
            Column(
                modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * MTheme.sizes.sheetMaxFraction)
                    .testTag(SheetTag)
                    .onSizeChanged { sheetHeight = it.height }
                    .offset {
                        // Reduced motion only drops the programmatic slide; a finger drag still follows.
                        val y = if (hidden) (if (reduced) 0 else sheetHeight) else offset.roundToInt()
                        IntOffset(0, y)
                    }
                    .graphicsLayer { alpha = if (reduced) fade.value else if (hidden) 0f else 1f }
                    .overlayElevation(shape)
                    .background(colors.bgOverlay, shape)
                    .anchoredDraggable(state, Orientation.Vertical),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(vertical = MTheme.space.s3)
                        .size(MTheme.sizes.sheetHandleWidth, MTheme.sizes.sheetHandleHeight)
                        .background(colors.borderStrong, RoundedCornerShape(percent = 50)),
                )
                content()
            }
        }
    }
}
