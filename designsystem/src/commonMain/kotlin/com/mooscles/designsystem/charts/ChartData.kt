package com.mooscles.designsystem.charts

import androidx.compose.runtime.Immutable

/**
 * A point of [MLineChart]: [x] places it in time (any unit, proportional: the chart spreads the
 * smallest to the largest across its width), [y] is the value in the unit the screen shows, and
 * [record] draws the record ring around it.
 */
@Immutable
data class LinePoint(val x: Double, val y: Double, val record: Boolean = false)

/** One bucket of [MBarChart], in the unit the screen shows; zero draws no bar. */
@Immutable
data class BarDatum(val value: Double)

/** The text under point or bar [index]. Labels that do not fit are dropped (the table has them all). */
@Immutable
data class AxisLabel(val index: Int, val text: String)

/**
 * One line of a chart's data table (spec D4): [label] (the date or "Week of 6 Oct"), [value], an
 * optional [detail] (the set, "85 kg × 3") and [record], which writes "Record". [key] identifies the line.
 */
@Immutable
data class ChartTableRowData(
    val key: String,
    val label: String,
    val value: String,
    val detail: String? = null,
    val record: Boolean = false,
)

/** An axis from [min] to [max], with the round values where its marks and grid lines go. */
@Immutable
data class ChartRange(val min: Double, val max: Double, val ticks: List<Double>)
