package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme

enum class MIconButtonStyle { Neutral, Outlined, Tonal }

/**
 * An icon-only button; [contentDescription] is its label and is required. [toggled] null means
 * a plain button; true or false makes it a toggle that reports its state (and draws a border
 * while on, so the state is not by color alone). The target is at least 48 dp.
 */
@Composable
fun MIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = MTheme.sizes.iconButton48,
    style: MIconButtonStyle = MIconButtonStyle.Neutral,
    enabled: Boolean = true,
    toggled: Boolean? = null,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    val on = toggled == true
    val tonal = style == MIconButtonStyle.Tonal || on
    val container = when {
        !enabled -> Color.Transparent
        tonal -> colors.accentSubtle
        pressed -> colors.bgInset
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> colors.textDisabled
        tonal -> colors.accentText
        else -> colors.textPrimary
    }
    val outline = when {
        !enabled -> if (style == MIconButtonStyle.Outlined) colors.borderSubtle else Color.Transparent
        on -> colors.accentText
        style == MIconButtonStyle.Outlined -> colors.borderStrong
        else -> Color.Transparent
    }
    val press = Modifier.mPressScale(source, enabled)
    val action = if (toggled == null) {
        Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.toggleable(
            value = toggled,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onValueChange = { onClick() },
        )
    }
    Box(
        modifier = modifier
            .sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min)
            .then(press)
            .semantics { this.contentDescription = contentDescription }
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .mFocusRing(shape, source)
                .background(container, shape)
                .then(if (outline != Color.Transparent) Modifier.border(MTheme.sizes.border, outline, shape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            MIcon(icon, contentDescription = null, size = MTheme.sizes.icon24, tint = content)
        }
    }
}
