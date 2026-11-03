package com.mooscles.designsystem.interaction

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.mooscles.designsystem.theme.MTheme

/** Scales the control to `motion.pressScale` while pressed (`motion.press`, `ease.out`). Disabled never scales. */
fun Modifier.mPressScale(interactionSource: MutableInteractionSource, enabled: Boolean): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val target = MotionPolicy.pressScale(pressed, enabled, MTheme.reducedMotion)
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = MTheme.motion.press, easing = MTheme.motion.easeOut),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
