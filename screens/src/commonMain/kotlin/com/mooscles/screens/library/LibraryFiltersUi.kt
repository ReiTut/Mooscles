package com.mooscles.screens.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.mooscles.data.Equipment
import com.mooscles.data.MuscleGroup
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.library_clear_filters
import com.mooscles.screens.resources.library_equipment
import com.mooscles.screens.resources.library_equipment_count
import com.mooscles.screens.resources.library_mine
import org.jetbrains.compose.resources.stringResource

/**
 * The muscle filter (spec "Biblioteca", Filtros): one scrolling line with a chip for each of the 12
 * [MuscleGroup]s, in the order of the enum and in the screen's language. Each chip is a checkbox
 * ([MChipKind.Filter]); [selected] holds the English labels of the ones that are on, and
 * [onToggle] gets the label of the chip that was touched.
 */
@Composable
internal fun MuscleFilterRow(selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MTheme.space.s4),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (group in MuscleGroup.entries) {
            MChip(
                label = muscleLabel(group.label),
                selected = group.label in selected,
                onClick = { onToggle(group.label) },
                modifier = Modifier.testTag(LibraryTags.muscleChip(group.label)),
                kind = MChipKind.Filter,
            )
        }
    }
}

/**
 * The "Equipment" chip ([MChipKind.Menu]), which opens [EquipmentFilterSheet] through [onOpen]:
 * "Equipment" with no equipment chosen, "Equipment · 2" with two. The chosen ones are also its state
 * description ("Barbell, Dumbbell"), so the filter it holds is said in words, never by the color
 * alone. [focusRequester] lets the screen give the focus back to it when the sheet closes.
 */
@Composable
internal fun EquipmentFilterChip(
    selected: Set<String>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val label = if (selected.isEmpty()) {
        stringResource(Res.string.library_equipment)
    } else {
        stringResource(Res.string.library_equipment_count, selected.size)
    }
    val chosen = Equipment.entries.filter { it.label in selected }.map { equipmentLabel(it.label) }.joinToString(", ")
    MChip(
        label = label,
        selected = selected.isNotEmpty(),
        onClick = onOpen,
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .semantics { if (chosen.isNotEmpty()) stateDescription = chosen },
        kind = MChipKind.Menu,
    )
}

/**
 * The equipment filter: a sheet with the 9 [Equipment]s as checkbox chips, any number of them on
 * ("or"). Every touch reports the new set to [onChange] at once; the sheet closes with Back, the
 * scrim or a drag ([MBottomSheet], a window of its own that keeps the focus inside it), and then
 * [onDismiss] runs, where the caller drops the sheet and gives the focus back to the chip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EquipmentFilterSheet(selected: Set<String>, onChange: (Set<String>) -> Unit, onDismiss: () -> Unit) {
    MBottomSheet(onDismiss = onDismiss, modifier = Modifier.testTag(LibraryTags.EQUIPMENT_SHEET)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            MText(
                stringResource(Res.string.library_equipment),
                MTheme.type.titleMd,
                color = MTheme.colors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MTheme.space.s4)
                    .padding(bottom = MTheme.space.s2)
                    .semantics { heading() },
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4).padding(bottom = MTheme.space.s6),
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            ) {
                for (equipment in Equipment.entries) {
                    val on = equipment.label in selected
                    MChip(
                        label = equipmentLabel(equipment.label),
                        selected = on,
                        onClick = { onChange(if (on) selected - equipment.label else selected + equipment.label) },
                        modifier = Modifier.testTag(LibraryTags.equipmentOption(equipment.label)),
                        kind = MChipKind.Filter,
                    )
                }
            }
        }
    }
}

/**
 * The library's filters, above the count: [MuscleFilterRow], then a wrapping line with
 * [EquipmentFilterChip], "My exercises" (a checkbox chip; only with [mine], the exercise picker
 * leaves it out) and, only while a filter is on, "Clear filters", which turns them all off (the
 * search stays). The line wraps, so at 360 dp or with large text nothing runs off the screen; 8 dp
 * between targets either way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LibraryFilterBar(
    filters: LibraryFilters,
    onChange: (LibraryFilters) -> Unit,
    onOpenEquipment: () -> Unit,
    equipmentFocus: FocusRequester,
    modifier: Modifier = Modifier,
    mine: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MuscleFilterRow(
            selected = filters.muscles,
            onToggle = { label ->
                onChange(filters.copy(muscles = if (label in filters.muscles) filters.muscles - label else filters.muscles + label))
            },
        )
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = MTheme.space.s4),
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        ) {
            EquipmentFilterChip(
                selected = filters.equipment,
                onOpen = onOpenEquipment,
                modifier = Modifier.testTag(LibraryTags.EQUIPMENT_CHIP),
                focusRequester = equipmentFocus,
            )
            if (mine) {
                MChip(
                    label = stringResource(Res.string.library_mine),
                    selected = filters.mineOnly,
                    onClick = { onChange(filters.copy(mineOnly = !filters.mineOnly)) },
                    modifier = Modifier.testTag(LibraryTags.MINE_CHIP),
                    kind = MChipKind.Filter,
                )
            }
            if (filters.active) {
                MButton(
                    stringResource(Res.string.library_clear_filters),
                    onClick = { onChange(LibraryFilters()) },
                    modifier = Modifier.testTag(LibraryTags.CLEAR_FILTERS),
                    variant = MButtonVariant.Text,
                    size = MButtonSize.S,
                )
            }
        }
    }
}
