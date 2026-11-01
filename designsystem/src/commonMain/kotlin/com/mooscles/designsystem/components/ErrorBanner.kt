package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_error_prefix
import org.jetbrains.compose.resources.stringResource
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.MoosclesColors

/**
 * The colors on an error surface (the banner and the error toast): `danger.subtle` behind,
 * `danger.text` for the alert icon, `text.primary` for the message and actions, `border.strong` for
 * an action's outline. Measured in `ErrorSurfaceColorsTest`: text 14.73 (light) and 12.14 (dark),
 * icon 5.41 and 5.75, outline 3.69 and 3.16.
 */
internal data class ErrorSurfaceColors(val surface: Color, val icon: Color, val content: Color, val outline: Color) {
    companion object {
        fun of(colors: MoosclesColors) = ErrorSurfaceColors(
            surface = colors.dangerSubtle,
            icon = colors.dangerText,
            content = colors.textPrimary,
            outline = colors.borderStrong,
        )
    }
}

/** The tag of the banner's icon. */
const val ErrorBannerIconTag = "error-banner-icon"

/**
 * An error notice on `danger.subtle` with the `CircleAlert` icon (decorative) and an "Error:" prefix
 * in the text, announced politely when it appears. [message] says what went wrong ("Could not
 * save."); the way out is the action ([actionLabel], "Try again"), so the message does not repeat it.
 * The action is a secondary button, `text.primary` with a `border.strong` outline: neutral, not the
 * green of a primary action, and legible on the error surface (see [ErrorSurfaceColors]).
 */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = ErrorSurfaceColors.of(MTheme.colors)
    val prefix = stringResource(Res.string.ds_error_prefix)
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(MTheme.radius.md))
            .padding(MTheme.space.s4)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.Top,
    ) {
        MIcon(
            MIcons.CircleAlert,
            contentDescription = null,
            size = MTheme.sizes.icon20,
            tint = colors.icon,
            modifier = Modifier.testTag(ErrorBannerIconTag),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
            MText("$prefix: $message", MTheme.type.bodyMd, color = colors.content)
            if (actionLabel != null && onAction != null) {
                MButton(actionLabel, onClick = onAction, variant = MButtonVariant.Secondary, size = MButtonSize.S)
            }
        }
    }
}
