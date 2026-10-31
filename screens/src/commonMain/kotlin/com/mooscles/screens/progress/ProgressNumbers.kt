package com.mooscles.screens.progress

import com.mooscles.domain.WeightUnits
import kotlin.math.floor

/** The numbers behind the progress texts: what a chart value is in the unit it is shown in. Pure, so it is tested on the host. */
object ProgressNumbers {
    /** From this value up, an axis label and a short number use the thousands form ("42 mil"). */
    const val SHORT_FROM = 10_000.0

    /**
     * An estimated 1RM is a guess, so it is shown to the nearest half kilo or the nearest pound, never more precisely.
     * The rounding happens in the unit it is shown in; the result is in that unit.
     */
    fun roundEstimate(kg: Double, pounds: Boolean): Double =
        if (pounds) floor(WeightUnits.kgToLb(kg) + 0.5) else floor(kg * 2 + 0.5) / 2

    /** [kgOrReps] as the screen shows it: reps stay reps, a 1RM is rounded ([roundEstimate]), other loads are converted from kg. */
    fun displayValue(kgOrReps: Double, metric: ProgressMetric, pounds: Boolean): Double = when (metric) {
        ProgressMetric.REPS -> kgOrReps
        ProgressMetric.E1RM -> roundEstimate(kgOrReps, pounds)
        ProgressMetric.LOAD, ProgressMetric.VOLUME -> WeightUnits.fromKg(kgOrReps, pounds)
    }

    /** Whether [value] is written in the thousands form on an axis. */
    fun isShort(value: Double): Boolean = value >= SHORT_FROM
}
