package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.theme.MTheme

/** Pressed feedback (a filled background) and the focus ring for a whole row. */
@Composable
private fun Modifier.rowFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(MTheme.radius.md)
    return this
        .mFocusRing(shape, source)
        .background(if (pressed) MTheme.colors.bgInset else Color.Transparent, shape)
}

@Composable
private fun RowBody(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
) {
    val colors = MTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = MTheme.sizes.rowMin)
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            MText(title, MTheme.type.bodyLg, color = if (enabled) colors.textPrimary else colors.textDisabled)
            if (subtitle != null) {
                MText(subtitle, MTheme.type.bodyMd, color = if (enabled) colors.textSecondary else colors.textDisabled)
            }
        }
        trailing?.invoke()
    }
}

/**
 * A settings row, at least 56 dp tall. With [onClick] the whole row is a button and, when no
 * [trailing] is given, shows a chevron. [modifier] (and its test tag) lands on the clickable node.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val tail: (@Composable () -> Unit)? = trailing ?: if (onClick != null) {
        {
            MIcon(
                MIcons.ChevronRight,
                contentDescription = null,
                size = MTheme.sizes.icon20,
                tint = if (enabled) MTheme.colors.textTertiary else MTheme.colors.textDisabled,
            )
        }
    } else {
        null
    }
    val base = modifier.fillMaxWidth()
    Box(
        modifier = if (onClick == null) {
            base
        } else {
            base.rowFeedback(source).clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
        },
    ) { RowBody(title, subtitle, enabled, null, tail) }
}

/**
 * The container of a set of [RadioRow]s that answer one question. It is a selectable group,
 * so screen readers announce the options as one group with their position ("1 of 3").
 */
@Composable
fun RadioGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().selectableGroup(), content = content)
}

/** A single choice in a group: the whole row is a radio button. Put the rows of one question in a [RadioGroup]. */
@Composable
fun RadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .rowFeedback(source)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        RowBody(title, subtitle, enabled, leading = { MRadio(selected, onClick = null, enabled = enabled) }, trailing = null)
    }
}

/** A setting that is on or off: the whole row toggles, with toggle haptics. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val haptics = MTheme.haptics
    Box(
        modifier = modifier
            .fillMaxWidth()
            .rowFeedback(source)
            .toggleable(
                value = checked,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.perform(if (it) HapticEvent.ToggleOn else HapticEvent.ToggleOff)
                    onCheckedChange(it)
                },
            ),
    ) {
        RowBody(title, subtitle, enabled, leading = null, trailing = { MSwitch(checked, onCheckedChange = null, enabled = enabled) })
    }
}

/** A section title (`title.sm`, not upper case) announced as a heading. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    MText(
        text,
        MTheme.type.titleSm,
        color = MTheme.colors.textSecondary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = MTheme.space.s4, end = MTheme.space.s4, top = MTheme.space.s4, bottom = MTheme.space.s2)
            .semantics { heading() },
    )
}
