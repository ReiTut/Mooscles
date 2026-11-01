package com.mooscles.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.unit.LayoutDirection
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_loading
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.theme.MTheme

/**
 * A linear progress bar. [progress] is 0..1, or `null` for indeterminate. With reduced motion
 * the indeterminate bar rests as a segment of `progressIndeterminateFraction` of the track.
 */
@Composable
fun MProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = MTheme.colors
    val fraction = MTheme.sizes.progressIndeterminateFraction
    val reduced = MTheme.reducedMotion
    val phase = if (progress == null && !reduced) {
        rememberInfiniteTransition(label = "progress").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(MTheme.timing.skeletonPulse.toInt(), easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "progressPhase",
        )
    } else {
        null
    }
    val fill = colors.accentSolid
    val description = contentDescription ?: if (progress == null) stringResource(Res.string.ds_loading) else null
    Box(
        modifier
            .fillMaxWidth()
            .height(MTheme.sizes.progressBarHeight)
            .clip(MTheme.radius.full)
            .background(colors.bgInset)
            .semantics {
                if (description != null) this.contentDescription = description
                progressBarRangeInfo = if (progress == null) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                }
            }
            .drawBehind {
                val width = size.width
                val rtl = layoutDirection == LayoutDirection.Rtl
                // Offsets are measured from the start edge: mirrored in right-to-left.
                fun segment(startOffset: Float, length: Float) {
                    val left = if (rtl) width - startOffset - length else startOffset
                    drawRect(fill, topLeft = Offset(left, 0f), size = Size(length, size.height))
                }
                if (progress != null) {
                    segment(0f, width * progress.coerceIn(0f, 1f))
                } else {
                    val length = width * fraction
                    // The segment slides from fully before the track to fully past it.
                    segment(if (phase == null) 0f else -length + (width + length) * phase.value, length)
                }
            },
    )
}
