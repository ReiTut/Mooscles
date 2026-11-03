package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.components.MSwitch
import com.mooscles.designsystem.components.RadioGroup
import com.mooscles.designsystem.components.RadioRow
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.components.SettingsRow
import com.mooscles.designsystem.components.SwitchRow
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_icon_label
import com.mooscles.designsystem.resources.gallery_sample_row
import com.mooscles.designsystem.resources.gallery_sample_subtitle
import com.mooscles.designsystem.resources.gallery_section_rows
import org.jetbrains.compose.resources.stringResource

/** The top bar and every kind of row, plain and with a subtitle, enabled and disabled. */
@Composable
fun RowsPage() {
    val title = stringResource(Res.string.gallery_sample_row)
    val subtitle = stringResource(Res.string.gallery_sample_subtitle)
    val actionLabel = stringResource(Res.string.gallery_sample_icon_label)
    var on by remember { mutableStateOf(true) }
    var choice by remember { mutableStateOf(0) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        TopBar(title, onBack = {}, actions = { MIconButton(MIcons.Plus, actionLabel, onClick = {}) })
        SectionHeader(stringResource(Res.string.gallery_section_rows))
        SettingsRow(title, subtitle = subtitle, onClick = {})
        SettingsRow(title, trailing = { MSwitch(checked = on, onCheckedChange = { on = it }, contentDescription = title) })
        SettingsRow(title, onClick = {}, enabled = false)
        SwitchRow(title, checked = on, onCheckedChange = { on = it }, subtitle = subtitle)
        SwitchRow(title, checked = false, onCheckedChange = {}, enabled = false)
        RadioGroup {
            for (index in 0..1) {
                RadioRow(title, selected = choice == index, onSelect = { choice = index }, subtitle = subtitle)
            }
            RadioRow(title, selected = false, onSelect = {}, enabled = false)
        }
    }
}
