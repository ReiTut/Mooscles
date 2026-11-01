package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_close
import com.mooscles.designsystem.resources.ds_error_prefix
import com.mooscles.designsystem.theme.LocalMContentColor
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.stringResource

enum class MToastKind { Info, Error }

enum class MToastResult { Dismissed, ActionPerformed }

internal const val ToastTag = "toast"
internal const val ToastActionTag = "toast-action"
internal const val ToastCloseTag = "toast-close"
internal const val ToastErrorIconTag = "toast-error-icon"

/** What a toast is drawn with: the surface, the message, the action and the close button. */
internal data class ToastColors(val surface: Color, val content: Color, val action: Color, val icon: Color?)

/**
 * An info toast is `bg.inverse` with its action in `accent.inverseText`. An error toast takes the
 * error surface of [ErrorBanner] instead ([ErrorSurfaceColors]: `danger.subtle`, the alert icon in
 * `danger.text`, message and action in `text.primary`), so it is told apart by its icon and color, not
 * only by its words. No `danger.*` token reaches 3:1 on `bg.inverse` (2.44 light, 1.93 dark).
 */
internal fun toastColors(kind: MToastKind, colors: MoosclesColors): ToastColors = when (kind) {
    MToastKind.Info -> ToastColors(colors.bgInverse, colors.textInverse, colors.accentInverseText, icon = null)
    MToastKind.Error -> ErrorSurfaceColors.of(colors).let { ToastColors(it.surface, it.content, it.content, it.icon) }
}

@Stable
internal class MToastData(
    val message: String,
    val kind: MToastKind,
    val actionLabel: String?,
    private val result: CompletableDeferred<MToastResult>,
) {
    fun finish(value: MToastResult) {
        result.complete(value)
    }
}

/**
 * Queues toasts, one at a time: [show] suspends until the toast is gone and says how.
 * An info toast goes away by itself after `timing.toastInfo` (`timing.toastAction` when it
 * has an action), or the longer time the platform's accessibility settings recommend; an error
 * toast stays until it is closed (or its action is used).
 */
@Stable
class MToastHostState {
    private val mutex = Mutex()
    internal var current by mutableStateOf<MToastData?>(null)
        private set

    suspend fun show(
        message: String,
        kind: MToastKind = MToastKind.Info,
        actionLabel: String? = null,
    ): MToastResult = mutex.withLock {
        val result = CompletableDeferred<MToastResult>()
        current = MToastData(message, kind, actionLabel, result)
        try {
            result.await()
        } finally {
            current = null
        }
    }
}

/**
 * Draws the current toast of [state] at the bottom of its bounds (give it the screen as
 * [modifier]; it passes touches through outside the toast). `bg.inverse` surface, action in
 * `accent.inverseText`; an error toast is `danger.subtle` with the `CircleAlert` icon, read as
 * "Error" before the message ([toastColors]). Announced politely to screen readers. It enters with `motion.base`
 * and leaves with `motion.fast`; with reduced motion it only fades.
 */
@Composable
fun MToastHost(state: MToastHostState, modifier: Modifier = Modifier) {
    val data = state.current
    val motion = MTheme.motion
    val timing = MTheme.timing
    val reduced = MTheme.reducedMotion
    var shown by remember { mutableStateOf<MToastData?>(null) }
    val progress = remember { Animatable(0f) }
    val rise = MTheme.space.s4

    LaunchedEffect(data) {
        if (data != null) {
            shown = data
            progress.snapTo(0f)
            progress.animateTo(1f, tween(if (reduced) motion.fast else motion.base, easing = motion.easeOut))
        } else {
            progress.animateTo(0f, tween(motion.fast, easing = motion.easeOut))
            shown = null
        }
    }
    val accessibility = LocalAccessibilityManager.current
    LaunchedEffect(data) {
        val toast = data ?: return@LaunchedEffect
        if (toast.kind == MToastKind.Info) {
            val hasAction = toast.actionLabel != null
            val base = if (hasAction) timing.toastAction else timing.toastInfo
            // With a screen reader or switch access on, the platform lengthens the time (for a toast
            // with an action, usually until it is closed), as the Material SnackbarHost does.
            val timeout = accessibility?.calculateRecommendedTimeoutMillis(
                base,
                containsIcons = true,
                containsText = true,
                containsControls = hasAction,
            ) ?: base
            delay(timeout)
            toast.finish(MToastResult.Dismissed)
        }
    }

    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        val toast = shown ?: return@Box
        val colors = toastColors(toast.kind, MTheme.colors)
        val shape = RoundedCornerShape(MTheme.radius.md)
        val close = stringResource(Res.string.ds_close)
        val errorLabel = stringResource(Res.string.ds_error_prefix)
        Row(
            Modifier
                .padding(MTheme.space.s4)
                .testTag(ToastTag)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = progress.value
                    translationY = if (reduced) 0f else (1f - progress.value) * rise.toPx()
                }
                .overlayElevation(shape)
                .background(colors.surface, shape)
                .heightIn(min = MTheme.targets.min)
                .padding(start = if (colors.icon != null) MTheme.space.s3 else MTheme.space.s4)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        ) {
            CompositionLocalProvider(LocalMContentColor provides colors.content) {
                if (colors.icon != null) {
                    MIcon(
                        MIcons.CircleAlert,
                        contentDescription = errorLabel,
                        modifier = Modifier.testTag(ToastErrorIconTag),
                        size = MTheme.sizes.icon20,
                        tint = colors.icon,
                    )
                }
                MText(
                    toast.message,
                    style = MTheme.type.bodyMd,
                    color = colors.content,
                    modifier = Modifier.weight(1f).padding(vertical = MTheme.space.s3),
                )
                if (toast.actionLabel != null) {
                    Box(
                        Modifier
                            .testTag(ToastActionTag)
                            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min)
                            .clickable(role = Role.Button) { toast.finish(MToastResult.ActionPerformed) }
                            .padding(horizontal = MTheme.space.s2),
                        contentAlignment = Alignment.Center,
                    ) {
                        MText(toast.actionLabel, style = MTheme.type.labelLg, color = colors.action)
                    }
                }
                if (toast.kind == MToastKind.Error || toast.actionLabel != null) {
                    Box(
                        Modifier
                            .testTag(ToastCloseTag)
                            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min)
                            .semantics { contentDescription = close }
                            .clickable(role = Role.Button) { toast.finish(MToastResult.Dismissed) },
                        contentAlignment = Alignment.Center,
                    ) {
                        MIcon(MIcons.X, contentDescription = null, size = MTheme.sizes.icon20, tint = colors.content)
                    }
                }
            }
        }
    }
}
