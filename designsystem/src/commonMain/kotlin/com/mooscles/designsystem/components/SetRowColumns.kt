package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import com.mooscles.designsystem.theme.MTheme

const val SetRowColumnsTag = "set-row-columns"

/** The test tag of the column title [index] of a [SetRowColumns]: 0 set, 1 previous, 2 load, 3 reps. */
fun setRowColumnTag(index: Int): String = "set-row-column-$index"

/**
 * Where the five cells of a [SetRow] sat in its last layout, in px from the start of its content
 * (inside its horizontal padding): index, previous, load, reps and check, in that order.
 * [oneLine] is false when the row was laid out in two lines (large text, or a narrow screen).
 */
@Immutable
data class SetRowCells(val oneLine: Boolean, val starts: List<Int>, val widths: List<Int>)

/**
 * The column titles of the set rows below (`label.caps`, `text.secondary`, one line each): the set
 * number's centered over the index cell, the previous result's at the start of its cell, the load's
 * and the reps' centered over theirs, with nothing over the check. [cells] is what a [SetRow]
 * reported (its `onCells`), so the titles sit over the cells as that row measured them.
 *
 * Titles only mean something over a one-line row: when [cells] is null, the row was two lines, or
 * a title is wider than its cell and the gap beside it, nothing is drawn and the header is 0 tall.
 * The titles are hidden from screen readers; each row already reads its values with their names.
 */
@Composable
fun SetRowColumns(set: String, previous: String, load: String, reps: String, cells: SetRowCells?, modifier: Modifier = Modifier) {
    val gap = with(LocalDensity.current) { MTheme.targets.gap.roundToPx() }
    val style = MTheme.type.labelCaps
    val color = MTheme.colors.textSecondary
    val titles = listOf(set, previous, load, reps)
    val shown = cells?.takeIf { it.oneLine && it.starts.size >= 4 && it.widths.size >= 4 }
    Layout(
        content = {
            // Composed only over a one-line row. Upper case here rather than through MText's caps:
            // the titles are hidden from screen readers anyway, and the text keeps its layout result.
            if (shown != null) {
                titles.forEachIndexed { i, title ->
                    MText(
                        title.toUpperCase(Locale.current), style,
                        modifier = Modifier.testTag(setRowColumnTag(i)), color = color, caps = false, softWrap = false, maxLines = 1,
                    )
                }
            }
        },
        modifier = modifier
            .testTag(SetRowColumnsTag)
            .fillMaxWidth()
            .clearAndSetSemantics {}
            // The same padding as the row, so the cells' starts line up.
            .padding(SetRowPadding),
    ) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val cells = shown ?: return@Layout layout(width, 0) {}
        val placeables = measurables.map { it.measure(Constraints()) }
        // A title may spill into the gap beside its cell, never further.
        val fits = placeables.indices.all { placeables[it].width <= cells.widths[it] + gap }
        if (!fits) return@Layout layout(width, 0) {}
        val height = placeables.maxOf { it.height }
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val x = if (i == 1) cells.starts[i] else cells.starts[i] + (cells.widths[i] - placeable.width) / 2
                placeable.placeRelative(x, (height - placeable.height) / 2)
            }
        }
    }
}
