package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SectionHeader
import com.mooscles.designsystem.theme.MTheme

/**
 * The section header of a gallery page. The pages pad their content by `space.s4`, and
 * [SectionHeader] brings its own `space.s4` inset, so the header steps out of the page padding
 * by that much: its text then starts on the same line as the samples under it.
 */
@Composable
internal fun PageHeader(text: String) {
    SectionHeader(text, Modifier.bleedHorizontally(MTheme.space.s4))
}

/**
 * The name of a sample, as the code spells it (such as "variant = Tonal"), above [content].
 * Like the token names on Foundations, these are code, the same in every language.
 */
@Composable
internal fun Sample(name: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        MText(name, MTheme.type.caption, color = MTheme.colors.textSecondary)
        content()
    }
}

/** Measures the element [inset] wider on each side and places it that far out, so it covers the parent's padding. */
internal fun Modifier.bleedHorizontally(inset: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extra = inset.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra * 2, maxWidth = constraints.maxWidth + extra * 2),
    )
    layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
}
