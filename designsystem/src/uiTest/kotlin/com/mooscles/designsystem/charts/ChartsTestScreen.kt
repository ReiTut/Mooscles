package com.mooscles.designsystem.charts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.theme.MTheme
import kotlin.concurrent.Volatile

/** The tag of the text that shows the screen's selection ("none" or the index). */
internal const val ChartsTestSelectedTag = "charts-test-selected"

/** The chart a [ChartsTestScreen] shows. */
internal sealed interface ChartsTestChart {
    data class Line(val points: List<LinePoint>) : ChartsTestChart
    data class Bar(val bars: List<BarDatum>, val integerTicks: Boolean = false) : ChartsTestChart
}

/** Counts the drawing caches each chart builds, through [LocalChartPathBuilds] (draw phase, UI thread). */
internal class ChartBuildCounter {
    @Volatile var line = 0
    @Volatile var bar = 0
    @Volatile var spark = 0

    fun record(kind: String) {
        when (kind) {
            CHART_BUILD_LINE -> line++
            CHART_BUILD_BAR -> bar++
            CHART_BUILD_SPARK -> spark++
            else -> error("Unknown chart build $kind")
        }
    }
}

internal fun kilograms(value: Double): String = MoosclesNumbers.weight(MoosclesNumbers.plain(value), "kg", NumberLocale.EN)

/** One table row per point, newest first, as a screen would build them. */
internal fun rowsFor(points: List<LinePoint>): List<ChartTableRowData> = points.indices.reversed().map { i ->
    ChartTableRowData(key = i.toString(), label = "Workout ${i + 1}", value = kilograms(points[i].y), detail = "${kilograms(points[i].y)} × 3", record = points[i].record)
}

/**
 * What a screen with a chart does (spec D4): the chart, or its table in its place, and the
 * "View data" button under it, with the choice in `rememberSaveable`. The selection is shown in a
 * text node ([ChartsTestSelectedTag]), so a test reads what composition saw; [onSelected] also gets
 * every change.
 */
@Composable
internal fun ChartsTestScreen(
    chart: ChartsTestChart,
    summary: String,
    rows: List<ChartTableRowData>,
    modifier: Modifier = Modifier,
    chartModifier: Modifier = Modifier,
    contentKey: Any? = null,
    onSelected: (Int?) -> Unit = {},
) {
    var showingTable by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val select: (Int?) -> Unit = {
        selected = it
        onSelected(it)
    }
    Column(modifier.fillMaxWidth().padding(MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        if (showingTable) {
            Column { for (row in rows) ChartTableRow(row) }
        } else {
            when (chart) {
                is ChartsTestChart.Line -> MLineChart(
                    chart.points,
                    summary,
                    { kilograms(it) },
                    ChartScale.labelIndices(chart.points.size).map { AxisLabel(it, (it + 1).toString()) },
                    selected,
                    select,
                    chartModifier,
                    contentKey,
                )
                is ChartsTestChart.Bar -> MBarChart(
                    chart.bars,
                    summary,
                    ::kilograms,
                    chart.bars.indices.map { AxisLabel(it, (it + 1).toString()) },
                    selected,
                    select,
                    chartModifier,
                    chart.integerTicks,
                    contentKey,
                )
            }
        }
        ChartDataButton(showingTable, { showingTable = !showingTable })
        MText(selected?.toString() ?: "none", MTheme.type.caption, modifier = Modifier.testTag(ChartsTestSelectedTag))
    }
}
