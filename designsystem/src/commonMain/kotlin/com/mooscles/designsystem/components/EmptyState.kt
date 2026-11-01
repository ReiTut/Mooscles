package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.theme.MTheme

/**
 * An empty list or screen. Phase 2 draws [icon] at `media` size in `text.tertiary` where the
 * chalk illustration will go (phase 4). The primary action is a Primary button and the secondary
 * a Text button; both are optional.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryAction: Pair<String, () -> Unit>? = null,
    secondaryAction: Pair<String, () -> Unit>? = null,
) {
    val colors = MTheme.colors
    Column(
        modifier.fillMaxWidth().padding(MTheme.space.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) {
        MIcon(icon, contentDescription = null, size = MTheme.sizes.media, tint = colors.textTertiary)
        MText(
            title,
            MTheme.type.titleMd.copy(textAlign = TextAlign.Center),
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        MText(body, MTheme.type.bodyMd.copy(textAlign = TextAlign.Center), color = colors.textSecondary)
        if (primaryAction != null) {
            MButton(primaryAction.first, onClick = primaryAction.second, variant = MButtonVariant.Primary)
        }
        if (secondaryAction != null) {
            MButton(secondaryAction.first, onClick = secondaryAction.second, variant = MButtonVariant.Text)
        }
    }
}
