package com.mooscles.designsystem.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.KeyMoment
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.MotionPolicy
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_pr_label
import com.mooscles.designsystem.resources.ds_pr_spoken
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** The tag of the badge, for tests. */
const val PrBadgeTag = "pr-badge"

/**
 * The personal record badge: a pill with the record icon and "PR", so it is never color alone.
 * It is read as "personal record". [soft] uses the quiet `pr.subtle` fill instead of `pr.solid`.
 * With [animateIn] it appears with the `PersonalRecord` key moment (a spring, or a fade with reduced motion).
 * [label] and [spoken] replace "PR" and "personal record" for a badge that means something else in words, such as
 * the records timeline's first record ("First"), so two badges never differ by their fill alone.
 */
@Composable
fun PrBadge(
    modifier: Modifier = Modifier,
    soft: Boolean = false,
    animateIn: Boolean = false,
    label: String? = null,
    spoken: String? = null,
) {
    val colors = MTheme.colors
    val spokenText = spoken ?: stringResource(Res.string.ds_pr_spoken)
    val labelText = label ?: stringResource(Res.string.ds_pr_label)
    val container = if (soft) colors.prSubtle else colors.prSolid
    val content = if (soft) colors.prText else colors.prOn
    val choice = MotionPolicy.keyMoment(KeyMoment.PersonalRecord, MTheme.reducedMotion)
    // Captured once: the entrance belongs to the moment the badge enters, even if the caller
    // stops asking for it on the next recomposition.
    val animate = remember { animateIn }
    val fadeMs = MTheme.motion.fast
    val riseFromPx = with(LocalDensity.current) { ((choice as? MotionChoice.Spring)?.riseDp ?: Dp(0f)).toPx() }
    val scale = remember { Animatable(if (animate && choice is MotionChoice.Spring) choice.fromScale else 1f) }
    val rise = remember { Animatable(if (animate) riseFromPx else 0f) }
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!animate) return@LaunchedEffect
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
            // Whatever ended the entrance, the badge rests fully visible.
            withContext(NonCancellable) {
                scale.snapTo(1f)
                rise.snapTo(0f)
                alpha.snapTo(1f)
            }
        }
    }
    Row(
        modifier
            .testTag(PrBadgeTag)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = rise.value
            }
            .background(container, MTheme.radius.full)
            .padding(horizontal = MTheme.space.s2, vertical = MTheme.space.s1)
            .clearAndSetSemantics { contentDescription = spokenText },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(MIcons.PlateRecord, contentDescription = null, size = MTheme.sizes.icon16, tint = content)
        MText(labelText, MTheme.type.labelMd, color = content)
    }
}
