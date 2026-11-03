package com.mooscles.designsystem.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_chart_record
import com.mooscles.designsystem.resources.ds_chart_view_chart
import com.mooscles.designsystem.resources.ds_chart_view_data
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.tabular
import org.jetbrains.compose.resources.stringResource

/** The tag of [ChartDataButton]. */
const val ChartDataButtonTag = "chart-data-button"

/** The tag of the [ChartTableRow] of [key]. */
fun chartTableRowTag(key: String): String = "chart-table-row-$key"

/**
 * The visible switch between a chart and its data table (spec D4): "View data" with the table icon
 * while the chart shows, "View chart" with the chart icon while the table shows. A 48 dp secondary
 * button. The caller keeps [showingTable] (in `rememberSaveable`, one per chart).
 */
@Composable
fun ChartDataButton(showingTable: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MButton(
        text = stringResource(if (showingTable) Res.string.ds_chart_view_chart else Res.string.ds_chart_view_data),
        onClick = onClick,
        modifier = modifier.testTag(ChartDataButtonTag),
        variant = MButtonVariant.Secondary,
        size = MButtonSize.M,
        icon = if (showingTable) MIcons.ChartLine else MIcons.Table,
    )
}

/**
 * One line of a chart's data table: the date (or week) at the start, the value at the end with the set under it, and
 * "Record" with the record icon when [ChartTableRowData.record]. A screen reader reads it as one node: date, value,
 * set, "Record". The value and the set always keep their whole width. When the date's longest word and the value do
 * not fit side by side (large font sizes, a long week label), the date takes the whole line and the value goes under
 * it, start-aligned, so neither is ever cut or broken mid-word.
 */
@Composable
fun ChartTableRow(row: ChartTableRowData, modifier: Modifier = Modifier) {
    val gap = MTheme.space.s3
    Layout(
        content = {
            MText(row.label, MTheme.type.bodyMd.tabular(), color = MTheme.colors.textPrimary)
            Column(horizontalAlignment = Alignment.End) {
                val end = TextAlign.End
                MText(row.value, MTheme.type.labelLg.tabular().copy(textAlign = end), color = MTheme.colors.textPrimary)
                row.detail?.let { MText(it, MTheme.type.caption.tabular().copy(textAlign = end), color = MTheme.colors.textSecondary) }
                if (row.record) RecordMark()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag(chartTableRowTag(row.key))
            .semantics(mergeDescendants = true) {}
            .heightIn(min = MTheme.targets.min)
            .padding(vertical = MTheme.space.s2),
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val labelMin = measurables[0].minIntrinsicWidth(constraints.maxHeight)
        val value = measurables[1].measure(loose)
        val sideBySide = labelMin + gapPx + value.width <= width
        val label = measurables[0].measure(loose.copy(maxWidth = if (sideBySide) width - gapPx - value.width else width))
        if (sideBySide) {
            val height = maxOf(label.height, value.height).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                label.placeRelative(0, (height - label.height) / 2)
                value.placeRelative(width - value.width, (height - value.height) / 2)
            }
        } else {
            val content = label.height + value.height
            val height = content.coerceIn(constraints.minHeight, constraints.maxHeight)
            val top = (height - content) / 2
            layout(width, height) {
                label.placeRelative(0, top)
                value.placeRelative(0, top + label.height)
            }
        }
    }
}

/** The legend of the record ring: the record icon in `chart.pr` and "Record". */
@Composable
fun MChartLegendRecord(modifier: Modifier = Modifier) {
    RecordMark(modifier)
}

/** The record icon and the word, never the color alone. */
@Composable
private fun RecordMark(modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MIcon(
            MIcons.PlateRecord,
            contentDescription = null,
            size = MTheme.sizes.icon16 * LocalDensity.current.fontScale,
            tint = MTheme.colors.chartPr,
        )
        MText(stringResource(Res.string.ds_chart_record), MTheme.type.caption, color = MTheme.colors.prText)
    }
}
