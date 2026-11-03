package com.mooscles.screens.progress

import com.mooscles.data.LoadConvention

/** A chart of the exercise progress, by its route argument (`exercise/{id}?metric=`, spec D2). */
enum class ProgressMetric(val route: String) {
    /** The convention's load measure: load, added weight (bodyweight) or assistance. */
    LOAD("load"),
    REPS("reps"),
    E1RM("e1rm"),
    VOLUME("volume");

    companion object {
        /** The metric whose [route] is exactly [value]; null for anything else, a different case included. */
        fun parse(value: String?): ProgressMetric? = entries.firstOrNull { it.route == value }
    }
}

/** Which charts a convention has and what each reads from a [SessionPoint] (spec D6, plan decision 5). */
object ProgressMetrics {
    private val loadMetrics = listOf(ProgressMetric.LOAD, ProgressMetric.E1RM, ProgressMetric.VOLUME)

    /**
     * The metrics of [convention], the default first: Total load, Per hand and One side (and a label the enum does
     * not know) → load, 1RM, volume; Added weight → reps, plus load and volume only when a session of [sessions] has
     * a load above 0; Assistance → reps and assistance.
     */
    fun available(convention: String, sessions: List<SessionPoint>): List<ProgressMetric> =
        when (WorkingSets.convention(convention)) {
            LoadConvention.TOTAL_LOAD, LoadConvention.PER_HAND, LoadConvention.ONE_SIDE -> loadMetrics
            LoadConvention.BODYWEIGHT ->
                if (sessions.any { it.load != null }) listOf(ProgressMetric.REPS, ProgressMetric.LOAD, ProgressMetric.VOLUME)
                else listOf(ProgressMetric.REPS)
            LoadConvention.ASSISTANCE -> listOf(ProgressMetric.REPS, ProgressMetric.LOAD)
        }

    /** [requested] when the convention has it ([available]), otherwise the first: `e1rm` on Added weight opens reps. */
    fun initial(requested: ProgressMetric?, available: List<ProgressMetric>): ProgressMetric =
        requested?.takeIf { it in available } ?: available.firstOrNull() ?: ProgressMetric.LOAD

    /**
     * The value of [metric] in [point] and the set that holds it. Volume is a sum over the session, not one set's
     * value, so it is always null here: read [SessionPoint.volumeKg] instead.
     */
    fun value(point: SessionPoint, metric: ProgressMetric): SetValue? = when (metric) {
        ProgressMetric.LOAD -> point.load
        ProgressMetric.REPS -> point.reps
        ProgressMetric.E1RM -> point.estimate
        ProgressMetric.VOLUME -> null
    }

    /**
     * Whether [point] carries a record mark on [metric]'s chart: on the load chart (added weight included, never
     * assistance) when the load's set is a beaten weight record, on the reps chart when the reps' set is a beaten
     * reps record of the chart's group, and never on the 1RM and volume charts, which have no record in the domain
     * (D6). A record set at another load in the session is not the plotted set, so it marks nothing here.
     */
    fun marked(point: SessionPoint, metric: ProgressMetric, convention: String): Boolean = when (metric) {
        ProgressMetric.LOAD -> point.weightRecord && WorkingSets.convention(convention) != LoadConvention.ASSISTANCE
        ProgressMetric.REPS -> point.repsRecord
        ProgressMetric.E1RM, ProgressMetric.VOLUME -> false
    }
}
