package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mooscles.designsystem.charts.AxisLabel
import com.mooscles.designsystem.charts.BarDatum
import com.mooscles.designsystem.charts.ChartDataButton
import com.mooscles.designsystem.charts.ChartScale
import com.mooscles.designsystem.charts.ChartTableRow
import com.mooscles.designsystem.charts.ChartTableRowData
import com.mooscles.designsystem.charts.LinePoint
import com.mooscles.designsystem.charts.MBarChart
import com.mooscles.designsystem.charts.MChartLegendRecord
import com.mooscles.designsystem.charts.MLineChart
import com.mooscles.designsystem.charts.MSparkline
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_chart_bars
import com.mooscles.designsystem.resources.gallery_sample_chart_flat
import com.mooscles.designsystem.resources.gallery_sample_chart_line
import com.mooscles.designsystem.resources.gallery_sample_chart_long
import com.mooscles.designsystem.resources.gallery_sample_chart_week
import com.mooscles.designsystem.resources.gallery_sample_chart_workout
import com.mooscles.designsystem.resources.gallery_section_charts
import com.mooscles.designsystem.theme.MTheme
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/** Sample data of the charts page: fixed, so every run draws the same. */
internal object ChartSamples {
    /** Eight sessions over four months, two of them records. */
    val line: List<LinePoint> = listOf(
        LinePoint(0.0, 80.0), LinePoint(14.0, 82.5), LinePoint(28.0, 85.0, record = true), LinePoint(45.0, 87.5, record = true),
        LinePoint(60.0, 85.0), LinePoint(75.0, 95.0, record = true), LinePoint(90.0, 90.0), LinePoint(110.0, 92.5),
    )

    /** Two sessions, no record. */
    val flat: List<LinePoint> = listOf(LinePoint(0.0, 60.0), LinePoint(7.0, 62.5))

    /** 300 sessions every 3 days from 40 to 100 kg with a small wobble, in steps of 0.25; a new high is a record. */
    val long: List<LinePoint> = run {
        var best = Double.NEGATIVE_INFINITY
        List(300) { i ->
            val wobble = ((i * 7) % 11 - 5) * 0.5
            val value = round((40.0 + 60.0 * i / 299 + wobble) * 4) / 4
            val record = value > best
            if (record) best = value
            LinePoint(i * 3.0, value, record = record && i > 0)
        }
    }

    /** Eight weeks of volume, the third one without workouts. */
    val bars: List<BarDatum> = listOf(9_800.0, 12_100.0, 0.0, 14_200.0, 11_000.0, 13_050.0, 12_500.0, 10_900.0).map { BarDatum(it) }

    val sparkline: List<Double> = line.map { it.y }
}

/**
 * The charts (spec D3 and D4): a line with and without records, with 2 and with 300 points, bars
 * with an empty week and a sparkline, each with "View data" and its table. The gallery's controls
 * give both themes and the 1.0, 1.3 and 2.0 text sizes.
 */
@Composable
fun ChartsPage() {
    val locale = currentNumberLocale()
    val kg: (Double) -> String = remember(locale) { { MoosclesNumbers.weight(MoosclesNumbers.plain(it), "kg", locale) } }
    val set: (Double) -> String = remember(locale) { { MoosclesNumbers.setText(MoosclesNumbers.plain(it * 0.9), "kg", 3, locale) } }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
    ) {
        PageHeader(stringResource(Res.string.gallery_section_charts))
        for ((name, points, summary) in listOf(
            Triple("MLineChart · record", ChartSamples.line, Res.string.gallery_sample_chart_line),
            Triple("MLineChart · 2", ChartSamples.flat, Res.string.gallery_sample_chart_flat),
            Triple("MLineChart · 300", ChartSamples.long, Res.string.gallery_sample_chart_long),
        )) {
            ChartSample(name, points.map { it.y }, points.map { it.record }, Res.string.gallery_sample_chart_workout, kg, set) { selected, onSelect ->
                val labels = remember(points) { ChartScale.labelIndices(points.size).map { AxisLabel(it, (it + 1).toString()) } }
                MLineChart(points, stringResource(summary), { kg(it) }, labels, selected, onSelect)
                if (points.any { it.record }) MChartLegendRecord()
            }
        }
        val bars = ChartSamples.bars
        ChartSample("MBarChart", bars.map { it.value }, null, Res.string.gallery_sample_chart_week, kg, null) { selected, onSelect ->
            val labels = remember { bars.indices.map { AxisLabel(it, (it + 1).toString()) } }
            MBarChart(bars, stringResource(Res.string.gallery_sample_chart_bars), kg, labels, selected, onSelect)
        }
        Sample("MSparkline") {
            ChartCard { MSparkline(ChartSamples.sparkline) }
        }
    }
}

@Composable
private fun ChartCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MTheme.colors.bgSurface, RoundedCornerShape(MTheme.radius.md))
            .padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3),
    ) { content() }
}

/**
 * A chart and its table, as a screen shows them: [chart] or, after "View data", one row per value
 * of [values], newest first, named by [rowLabel] with the number and marked by [records]; [set]
 * writes the sample set under the value.
 */
@Composable
private fun ChartSample(
    name: String,
    values: List<Double>,
    records: List<Boolean>?,
    rowLabel: StringResource,
    kg: (Double) -> String,
    set: ((Double) -> String)?,
    chart: @Composable (selected: Int?, onSelect: (Int?) -> Unit) -> Unit,
) {
    var showingTable by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Int?>(null) }
    Sample(name) {
        ChartCard {
            if (showingTable) {
                Column {
                    for (i in values.indices.reversed()) {
                        val value = values[i]
                        ChartTableRow(
                            ChartTableRowData(
                                key = i.toString(),
                                label = stringResource(rowLabel, i + 1),
                                value = kg(value),
                                detail = set?.invoke(value),
                                record = records?.get(i) == true,
                            ),
                        )
                    }
                }
            } else {
                chart(selected) { selected = it }
            }
            ChartDataButton(showingTable, { showingTable = !showingTable })
        }
    }
}
