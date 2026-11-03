package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.theme.MTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val PrToastTag = "pr-toast"

/** For tests: which entrance the toast chose, `"Spring"` or `"Fade"`. */
internal val MotionChoiceUsed = SemanticsPropertyKey<String>("MotionChoiceUsed")

internal var SemanticsPropertyReceiver.motionChoiceUsed by MotionChoiceUsed

/**
 * The notice of a new personal record: a quiet `bg.inverse` card with the record plate, [title]
 * and [detail]. It enters with the `PersonalRecord` key moment (a spring, or a fade with reduced
 * motion), is read out politely, has no action, and asks [onDismiss] after `timing.prToast`.
 * While [visible] is false nothing is drawn.
 */
@Composable
fun PrToast(
    title: String,
    detail: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val dismiss = rememberUpdatedState(onDismiss)
    val hold = MTheme.timing.prToast
    // A record shown in place of another (new text) starts the hold again.
    LaunchedEffect(title, detail) {
        delay(hold)
        dismiss.value()
    }
    PrToastCard(title, detail, modifier)
}

@Composable
private fun PrToastCard(title: String, detail: String, modifier: Modifier) {
    val colors = MTheme.colors
    val choice = MotionPolicy.keyMoment(KeyMoment.PersonalRecord, MTheme.reducedMotion)
    val fadeMs = MTheme.motion.fast
    val riseFromPx = with(LocalDensity.current) { ((choice as? MotionChoice.Spring)?.riseDp ?: Dp(0f)).toPx() }
    // The entrance runs once, when the card appears.
    val scale = remember { Animatable(if (choice is MotionChoice.Spring) choice.fromScale else 1f) }
    val rise = remember { Animatable(riseFromPx) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        try {
            when (choice) {
                is MotionChoice.Spring -> {
                    val spec = spring<Float>(choice.tokens.dampingRatio, choice.tokens.stiffness)
                    coroutineScope {
                        launch { scale.animateTo(1f, spec) }
                        launch { rise.animateTo(0f, spec) }
                        launch { alpha.animateTo(1f, tween(fadeMs)) }
                    }
                }
                is MotionChoice.Fade -> alpha.animateTo(1f, tween(choice.durationMs))
            }
        } finally {
            withContext(NonCancellable) {
                scale.snapTo(1f)
                rise.snapTo(0f)
                alpha.snapTo(1f)
            }
        }
    }
    val shape = RoundedCornerShape(MTheme.radius.md)
    val used = if (choice is MotionChoice.Spring) "Spring" else "Fade"
    Row(
        modifier
            .testTag(PrToastTag)
            .padding(MTheme.space.s4)
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = rise.value
            }
            .overlayElevation(shape)
            .background(colors.bgInverse, shape)
            .padding(MTheme.space.s3)
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                motionChoiceUsed = used
            },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.background(colors.prSolid, MTheme.radius.full).padding(MTheme.space.s2),
            contentAlignment = Alignment.Center,
        ) {
            MIcon(MIcons.PlateRecord, contentDescription = null, size = MTheme.sizes.icon24, tint = colors.prOn)
        }
        Column(Modifier.weight(1f)) {
            MText(title, MTheme.type.labelLg, color = colors.textInverse)
            MText(detail, MTheme.type.bodyMd, color = colors.textInverse)
        }
    }
}
