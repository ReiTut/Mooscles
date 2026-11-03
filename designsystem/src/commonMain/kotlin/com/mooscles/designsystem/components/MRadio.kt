package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme

/**
 * A radio button. With [onClick] it is a control by itself (48 dp target); with null it only
 * draws the state, for a row that owns the gesture ([RadioRow]). Selected shows an inner dot
 * and a thicker ring, not only a color.
 */
@Composable
fun MRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val ring = when {
        !enabled -> colors.borderSubtle
        selected -> colors.accentSolid
        else -> colors.borderStrong
    }
    val dot = if (enabled) colors.accentSolid else colors.textDisabled
    val action = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .mPressScale(source, enabled)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
    }
    Box(
        modifier = modifier
            .then(if (onClick == null) Modifier else Modifier.sizeIn(minWidth = MTheme.targets.min, minHeight = MTheme.targets.min))
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(MTheme.sizes.icon24)
                .mFocusRing(CircleShape, source)
                .border(if (selected) MTheme.sizes.borderSelected else MTheme.sizes.border, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(MTheme.space.s3).background(dot, CircleShape))
        }
    }
}
