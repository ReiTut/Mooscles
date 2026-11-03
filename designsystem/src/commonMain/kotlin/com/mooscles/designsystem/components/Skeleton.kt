package com.mooscles.designsystem.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors

/** The opacity the skeleton is drawn with; exposed so tests can read it. */
val SkeletonAlpha = SemanticsPropertyKey<Float>("SkeletonAlpha")
private var SemanticsPropertyReceiver.skeletonAlpha by SkeletonAlpha

/**
 * The fill of a skeleton block: `border.subtle`, the decorative neutral of dividers, the timer track
 * and the chart grid. The spec's `bg.raised` is white in the light theme, 1.03:1 on `bg.surface` and
 * 1.12:1 on `bg.base`, so the blocks vanished; `border.subtle` is 1.36 and 1.24 there (dark: 1.50 and
 * 1.64, against 1.12 and 1.23). It stays decorative, below 3:1 on purpose: a skeleton carries no
 * information of its own, and a 3:1 fill (`border.strong`) would draw dark grey bars over the page.
 */
internal fun skeletonFill(colors: MoosclesColors): Color = colors.borderSubtle

/**
 * A loading placeholder block of `border.subtle` ([skeletonFill]). Its opacity pulses over
 * `timing.skeletonPulse`; with reduced motion it stays still. Give it a size with [modifier].
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MTheme.radius.xs),
) {
    val low = MTheme.sizes.skeletonMinAlpha
    val alpha: State<Float> = if (MTheme.reducedMotion) {
        remember { mutableStateOf(1f) }
    } else {
        rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 1f,
            targetValue = low,
            animationSpec = infiniteRepeatable(
                tween(MTheme.timing.skeletonPulse.toInt() / 2, easing = MTheme.motion.easeInOut),
                RepeatMode.Reverse,
            ),
            label = "skeletonAlpha",
        )
    }
    Box(
        modifier
            .semantics { skeletonAlpha = alpha.value }
            .graphicsLayer { this.alpha = alpha.value }
            .background(skeletonFill(MTheme.colors), shape),
    )
}
