package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.launch

internal const val DialogTag = "dialog"
internal const val DialogScrimTag = "dialog-scrim"
internal const val DialogDismissTag = "dialog-dismiss"
internal const val DialogConfirmTag = "dialog-confirm"
internal const val DialogConfirmDangerTag = "dialog-confirm-danger"

/**
 * A confirmation dialog: [title], [text] (none when null) and actions at the end, with the confirmation last.
 * [destructive] makes the confirmation the filled `Danger` button.
 *
 * Built on the common `androidx.compose.ui.window.Dialog` (a window on Android, a platform
 * overlay on iOS), which traps focus and gives the dialog its own accessibility pane; no
 * Material 3. Back, Escape and a tap on the scrim call [onDismiss] after the exit animation
 * (`motion.slowExit`); the scrim and the card fade and rise in with `motion.slow`
 * (`ease.drawer`). The close key handling lives here because common code cannot see the
 * platform back button: Android's goes through the Dialog's own request, hardware keys
 * through `onPreviewKeyEvent`; both end in the same guarded [close].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
    destructive: Boolean = false,
) {
    val colors = MTheme.colors
    val motion = MTheme.motion
    val reduced = MTheme.reducedMotion
    val currentConfirm by rememberUpdatedState(onConfirm)
    val currentDismiss by rememberUpdatedState(onDismiss)
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    val rise = MTheme.space.s2
    val enterMs = (MotionPolicy.enter(reduced, motion.slow) as MotionChoice.Fade).durationMs
    val exitMs = if (reduced) motion.fast else motion.slowExit

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(enterMs, easing = if (reduced) motion.easeOut else motion.easeDrawer))
    }
    fun close(then: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(exitMs, easing = motion.easeOut))
            then()
        }
    }

    MOverlayWindow(onDismissRequest = { close { currentDismiss() } }) {
        // Inside the Dialog's own composition, so the requester is attached before it is used.
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(
            Modifier
                .fillMaxSize()
                .overlayKeys { close { currentDismiss() } }
                .focusRequester(focus)
                .focusable(),
        ) {
            OverlayScrim(DialogScrimTag, progress.value) { close { currentDismiss() } }
            Box(Modifier.fillMaxSize().padding(MTheme.space.s6), contentAlignment = Alignment.Center) {
                val shape = RoundedCornerShape(MTheme.radius.xl)
                Column(
                    Modifier
                        .widthIn(max = MTheme.sizes.dialogMaxWidth)
                        .fillMaxWidth()
                        .testTag(DialogTag)
                        .graphicsLayer {
                            alpha = progress.value
                            translationY = if (reduced) 0f else (1f - progress.value) * rise.toPx()
                        }
                        .overlayElevation(shape)
                        .background(colors.bgOverlay, shape)
                        // Scrolls when it is taller than the window (landscape, split screen, 200 % text).
                        .verticalScroll(rememberScrollState())
                        .padding(MTheme.space.s6)
                        .semantics { paneTitle = title },
                    verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
                ) {
                    MText(title, style = MTheme.type.titleLg, modifier = Modifier.semantics { heading() })
                    // A null text is a dialog of a title alone: no block and no gap for it.
                    if (text != null) MText(text, style = MTheme.type.bodyLg, color = colors.textSecondary)
                    // Long labels wrap onto a second line, still at the end, instead of squeezing the confirmation.
                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = MTheme.space.s2),
                        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (dismissLabel != null) {
                            MButton(
                                dismissLabel,
                                onClick = { close { currentDismiss() } },
                                variant = MButtonVariant.Text,
                                modifier = Modifier.testTag(DialogDismissTag),
                            )
                        }
                        MButton(
                            confirmLabel,
                            onClick = { close { currentConfirm() } },
                            variant = if (destructive) MButtonVariant.Danger else MButtonVariant.Primary,
                            modifier = Modifier.testTag(if (destructive) DialogConfirmDangerTag else DialogConfirmTag),
                        )
                    }
                }
            }
        }
    }
}
