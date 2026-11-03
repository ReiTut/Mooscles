package com.mooscles.designsystem.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.theme.LocalMContentColor
import com.mooscles.designsystem.theme.MTheme

/**
 * Draws [icon] at [size] in [tint]. [Color.Unspecified], the default of [LocalMContentColor]
 * outside a container that sets one, resolves to `MTheme.colors.textPrimary`. A null
 * [contentDescription] marks the icon as decorative.
 */
@Composable
fun MIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = MTheme.sizes.icon24,
    tint: Color = LocalMContentColor.current,
) {
    val color = if (tint == Color.Unspecified) MTheme.colors.textPrimary else tint
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}
