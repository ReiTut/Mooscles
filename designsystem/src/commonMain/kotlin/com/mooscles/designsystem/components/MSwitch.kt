package com.mooscles.designsystem.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme

/**
 * A switch. With [onCheckedChange] it is a control by itself (48 dp target, toggle haptics);
 * with null it only draws the state, for a row that owns the gesture ([SwitchRow]).
 * [contentDescription] names a standalone switch (needed whenever it is used outside a row).
 * On also shows a check in the thumb and moves the thumb, so the state is not by color alone.
 */
@Composable
fun MSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = MTheme.colors
    val haptics = MTheme.haptics
    val source = remember { MutableInteractionSource() }
    val trackWidth = MTheme.space.s12 + MTheme.space.s1
    val trackHeight = MTheme.space.s8
    val thumb = MTheme.space.s6
    val inset = MTheme.space.s1
    val travel = trackWidth - thumb - inset * 2
    val duration = if (MTheme.reducedMotion) MTheme.motion.instant else MTheme.motion.fast
    val thumbX by animateDpAsState(
        targetValue = if (checked) inset + travel else inset,
        animationSpec = tween(durationMillis = duration, easing = MTheme.motion.easeOut),
        label = "switchThumb",
    )
    val track = when {
        checked && enabled -> colors.accentSolid
        checked -> colors.textDisabled
        else -> colors.bgInset
    }
    val thumbColor = when {
        checked && enabled -> colors.accentOn
        checked -> colors.bgInset
        enabled -> colors.textSecondary
        else -> colors.textDisabled
    }
    val action = if (onCheckedChange == null) {
        Modifier
    } else {
        Modifier
            .mPressScale(source, enabled)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.perform(if (it) HapticEvent.ToggleOn else HapticEvent.ToggleOff)
                    onCheckedChange(it)
                },
            )
    }
    Box(
        modifier = modifier
            .then(if (onCheckedChange == null) Modifier else Modifier.sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min))
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(trackWidth, trackHeight)
                .mFocusRing(CircleShape, source)
                .background(track, CircleShape)
                .border(MTheme.sizes.border, if (enabled) colors.borderStrong else colors.borderSubtle, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier.offset(x = thumbX).size(thumb).background(thumbColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) MIcon(MIcons.Check, contentDescription = null, size = MTheme.sizes.icon16, tint = track)
            }
        }
    }
}
