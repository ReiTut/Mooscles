package com.mooscles.designsystem.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import com.mooscles.designsystem.theme.LocalMContentColor
import com.mooscles.designsystem.theme.MTheme

/**
 * The text of the design system. The color is [color] when given, else the container's
 * content color, else the color of [style], else `text.primary`.
 *
 * [caps] defaults to true for the `labelCaps` style and is explicit, so it survives `style.copy(...)`.
 * Caps text is drawn in upper case for the current locale, but that is visual only: the
 * semantics keep the original text, so screen readers and tests read it as written.
 *
 * [overflow] stays [TextOverflow.Clip] for numbers, which are measured never to need it; only a
 * name may end in an ellipsis (the in-progress workout bar).
 */
@Composable
fun MText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    caps: Boolean = style == MTheme.type.labelCaps,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val content = LocalMContentColor.current
    val resolved = when {
        color != Color.Unspecified -> color
        content != Color.Unspecified -> content
        style.color != Color.Unspecified -> style.color
        else -> MTheme.colors.textPrimary
    }
    val shown = if (caps) text.toUpperCase(Locale.current) else text
    val original = text
    BasicText(
        text = shown,
        modifier = if (shown != original) {
            modifier.clearAndSetSemantics { this.text = AnnotatedString(original) }
        } else {
            modifier
        },
        style = style.copy(color = resolved),
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
    )
}
