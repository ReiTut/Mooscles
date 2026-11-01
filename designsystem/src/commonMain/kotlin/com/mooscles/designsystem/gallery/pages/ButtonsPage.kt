package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MIconButtonStyle
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_button
import com.mooscles.designsystem.resources.gallery_sample_icon_label
import com.mooscles.designsystem.resources.gallery_sample_long_label
import com.mooscles.designsystem.resources.gallery_section_buttons
import com.mooscles.designsystem.resources.gallery_section_icon_buttons
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Each button variant once (only one Primary per screen), the sizes, the states and the icon
 * buttons. Every sample is named as the code spells it, so the variants can be told apart.
 */
@Composable
fun ButtonsPage() {
    val label = stringResource(Res.string.gallery_sample_button)
    val iconLabel = stringResource(Res.string.gallery_sample_icon_label)
    var toggled by remember { mutableStateOf(false) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_buttons))
        for (variant in MButtonVariant.entries) {
            Sample("variant = ${variant.name}") { MButton(label, onClick = {}, variant = variant) }
        }
        Sample("size = ${MButtonSize.entries.joinToString { it.name }}") {
            // These rows wrap: at 200% text one line cannot hold them and the last item would be squeezed.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (size in MButtonSize.entries) {
                    MButton(label, onClick = {}, variant = MButtonVariant.Secondary, size = size)
                }
            }
        }
        Sample("icon = Plus") { MButton(label, onClick = {}, variant = MButtonVariant.Tonal, icon = MIcons.Plus) }
        Sample("enabled = false") { MButton(label, onClick = {}, variant = MButtonVariant.Secondary, enabled = false) }
        Sample("loading = true") { MButton(label, onClick = {}, variant = MButtonVariant.Tonal, loading = true) }
        Sample("size = L, fillMaxWidth") {
            MButton(
                stringResource(Res.string.gallery_sample_long_label),
                onClick = {},
                variant = MButtonVariant.Secondary,
                size = MButtonSize.L,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PageHeader(stringResource(Res.string.gallery_section_icon_buttons))
        Sample("${MIconButtonStyle.entries.joinToString { it.name }}, 56, disabled, toggled") {
            // Centered on one line: the 56 dp button must not sit lower than the 48 dp ones.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                for (style in MIconButtonStyle.entries) {
                    MIconButton(MIcons.Plus, iconLabel, onClick = {}, style = style)
                }
                MIconButton(MIcons.Plus, iconLabel, onClick = {}, size = MTheme.sizes.iconButton56)
                MIconButton(MIcons.Plus, iconLabel, onClick = {}, enabled = false)
                MIconButton(MIcons.Sun, iconLabel, onClick = { toggled = !toggled }, toggled = toggled)
            }
        }
    }
}
