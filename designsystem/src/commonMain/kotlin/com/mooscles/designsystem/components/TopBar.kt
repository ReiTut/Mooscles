package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_back
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/**
 * The top bar: an optional labelled back button, the [title] as one heading text node and
 * the [actions]. At least 56 dp tall; the title wraps instead of being cut. Without a [title]
 * (null) the bar holds only back and the actions, at the end: a screen whose title is a long name
 * shows it in its scrolling content instead, so the bar never grows past the screen.
 */
@Composable
fun TopBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MTheme.sizes.rowMin)
            .padding(horizontal = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MIconButton(MIcons.ArrowLeft, stringResource(Res.string.ds_back), onClick = onBack)
        }
        if (title != null) {
            MText(
                title,
                MTheme.type.titleLg,
                color = MTheme.colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .then(if (onBack == null) Modifier.padding(start = MTheme.space.s2) else Modifier)
                    .semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}
