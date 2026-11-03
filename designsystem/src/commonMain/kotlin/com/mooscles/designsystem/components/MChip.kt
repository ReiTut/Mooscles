package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme

enum class MChipKind { Filter, SetType, Segmented, Menu }

/** The tag of the check icon, present only while the chip is selected. */
const val ChipCheckTag = "chip-check"

/** The tag of the chevron of an [MChipKind.Menu] chip. */
const val ChipMenuChevronTag = "chip-menu-chevron"

/**
 * A chip. Selected is `accent.subtle` plus a check icon, so it never relies on color alone.
 * [MChipKind.Filter] is a toggle; [MChipKind.SetType] and [MChipKind.Segmented] are one choice
 * of a group. [MChipKind.Menu] (plan decision 12) is a button that opens a sheet or menu: a
 * `ChevronDown` after the label and no check; [selected] (a filter it opens is on) draws the
 * selected look, and the label says what is on ("Equipment · 2"), so it is not color alone.
 * The drawn height is 40 and the target 48; leave 8 dp between chips. The click target is the
 * [modifier]'s node, so a `Modifier.focusRequester` passed in it can return the focus to the chip.
 */
@Composable
fun MChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: MChipKind = MChipKind.Filter,
    enabled: Boolean = true,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = MTheme.radius.full
    val container = when {
        selected && enabled -> colors.accentSubtle
        else -> Color.Transparent
    }
    val content = when {
        !enabled -> colors.textDisabled
        selected -> colors.accentText
        else -> colors.textPrimary
    }
    val outline = when {
        !enabled -> colors.borderSubtle
        selected -> colors.accentText
        else -> colors.borderStrong
    }
    val action = if (kind == MChipKind.Menu) {
        Modifier.clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    } else if (kind == MChipKind.Filter) {
        Modifier.toggleable(
            value = selected,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Checkbox,
            onValueChange = { onClick() },
        )
    } else {
        Modifier.selectable(
            selected = selected,
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = if (kind == MChipKind.Segmented) Role.Tab else Role.RadioButton,
            onClick = onClick,
        )
    }
    Box(
        modifier = modifier
            .sizeIn(minHeight = MTheme.targets.min)
            .mPressScale(source, enabled)
            .then(action),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = MTheme.sizes.buttonS)
                .mFocusRing(shape, source)
                .background(container, shape)
                .border(MTheme.sizes.border, outline, shape)
                .padding(horizontal = MTheme.space.s4),
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected && kind != MChipKind.Menu) {
                MIcon(MIcons.Check, contentDescription = null, size = MTheme.sizes.icon16, tint = content, modifier = Modifier.testTag(ChipCheckTag))
            }
            MText(label, style = MTheme.type.labelMd, color = content)
            if (kind == MChipKind.Menu) {
                MIcon(MIcons.ChevronDown, contentDescription = null, size = MTheme.sizes.icon16, tint = content, modifier = Modifier.testTag(ChipMenuChevronTag))
            }
        }
    }
}
