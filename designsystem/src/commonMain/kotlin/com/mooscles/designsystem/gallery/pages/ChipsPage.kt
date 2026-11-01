package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MInfoChip
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_chip
import com.mooscles.designsystem.resources.gallery_sample_menu_chip
import com.mooscles.designsystem.resources.gallery_section_chip_kinds
import com.mooscles.designsystem.resources.gallery_section_info_chips
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.stringResource

/** Each chip kind, unselected, selected and disabled, then the read-only chip with and without an icon. */
@Composable
fun ChipsPage() {
    val label = stringResource(Res.string.gallery_sample_chip)
    val menuLabel = stringResource(Res.string.gallery_sample_menu_chip)
    var selected by remember { mutableStateOf(MChipKind.Filter) }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_chip_kinds))
        for (kind in MChipKind.entries) {
            // A menu chip's label carries its count, as the equipment filter's does.
            Sample("kind = ${kind.name}") { ChipRow(kind, if (kind == MChipKind.Menu) menuLabel else label, selected) { selected = it } }
        }
        PageHeader(stringResource(Res.string.gallery_section_info_chips))
        Sample("MInfoChip") {
            Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
                MInfoChip(label, icon = MIcons.Barbell)
                MInfoChip(label)
            }
        }
    }
}

/** One kind: unselected, selected and disabled (which of the first two is selected follows the taps). */
@Composable
private fun ChipRow(kind: MChipKind, label: String, selected: MChipKind, onSelect: (MChipKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2), verticalAlignment = Alignment.CenterVertically) {
        MChip(label, selected = selected == kind, onClick = { onSelect(kind) }, kind = kind)
        MChip(label, selected = selected != kind, onClick = { onSelect(kind) }, kind = kind)
        MChip(label, selected = false, onClick = {}, kind = kind, enabled = false)
    }
}
