package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.theme.MTheme

/**
 * A read-only chip (plan decision 12), such as the equipment, muscle group or logging convention
 * of the exercise detail. It is not clickable and takes no focus, and it has no border, so it never
 * looks like the [MChip] a person taps: `text.primary` on `bg.inset`, with an optional [icon] in
 * `text.secondary` before the label (decorative: the chip reads as its [label]). The drawn height is
 * 32 dp, and it grows with the text, which wraps instead of being cut at 200%.
 */
@Composable
fun MInfoChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = MTheme.colors
    val shape = MTheme.radius.full
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .heightIn(min = MTheme.sizes.infoChipHeight)
            .background(colors.bgInset, shape)
            .padding(horizontal = MTheme.space.s3, vertical = MTheme.space.s1),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            MIcon(icon, contentDescription = null, size = MTheme.sizes.icon16, tint = colors.textSecondary)
        }
        MText(label, MTheme.type.labelMd, color = colors.textPrimary)
    }
}
