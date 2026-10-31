package com.mooscles.screens.progress

import androidx.compose.runtime.Immutable
import com.mooscles.data.LoadConvention
import com.mooscles.screens.format.ScreenDates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** The period chips of the exercise chart (spec D7): calendar months back from today, or everything. */
enum class ExercisePeriod(val months: Int?) { THREE_MONTHS(3), SIX_MONTHS(6), ONE_YEAR(12), ALL(null) }

/** What to chart: the series, the metric, the period, and the calendar it is read in (zone and first day of the week). */
data class ExerciseChartQuery(
    val key: SeriesKey,
    val metric: ProgressMetric,
    val period: ExercisePeriod = ExercisePeriod.ALL,
    val today: LocalDate,
    val zone: TimeZone,
    val weekStart: DayOfWeek,
) : ProgressQuery<ExerciseChart> {
    override fun compute(index: ProgressIndex): ExerciseChart = ExerciseCharts.query(index, this)
}

/** The Library card's sparkline of [key] ([ExerciseCharts.sparkline]) as a cache query. */
data class SparklineQuery(val key: SeriesKey, val last: Int = 12) : ProgressQuery<List<Double>> {
    override fun compute(index: ProgressIndex): List<Double> = ExerciseCharts.sparkline(index, key, last)
}

/** Why a chart has no points: no session of the series, a 1RM with no set of up to 10 reps, or nothing of the metric in the period. */
enum class EmptyReason { NO_SESSIONS, NO_ESTIMATE, NO_POINTS }

/**
 * One plotted point, in kg (or reps). For a session it is the session's start and its set; for a weekly point
 * ([ExerciseChart.weekly]) [startedAt] is the start of the week (midnight local, P25), [workoutId] is null and
 * [set] is the set of the week's best session. [record] is true when the point's own set is a beaten record's
 * (for a weekly point, the plotted session's: another session's record never marks it); the 1RM and volume charts never have it (D6).
 */
@Immutable
data class ChartPoint(
    val startedAt: Long,
    val value: Double,
    val set: SetValue?,
    val record: Boolean,
    val workoutId: String?,
)

/**
 * The chart of one metric over one period. [best] is the highest point, or the lowest on Assistance's load (less help
 * is progress); [sessionCount] counts the sessions with a point in the period; [trend] (D10) leaves out the running
 * week of a weekly chart and is not inverted on Assistance: "down" there is progress.
 */
@Immutable
class ExerciseChart(
    val points: List<ChartPoint>,
    val weekly: Boolean,
    val sessionCount: Int,
    val first: ChartPoint?,
    val last: ChartPoint?,
    val best: ChartPoint?,
    val trend: Trend?,
    val empty: EmptyReason?,
)

object ExerciseCharts {
    /** More points than this reduce to one per week (spec D3, plan decision 7). */
    const val MAX_POINTS = 104

    fun query(index: ProgressIndex, query: ExerciseChartQuery): ExerciseChart {
        val all = index.sessions(query.key)
        if (all.isEmpty()) return empty(EmptyReason.NO_SESSIONS)
        val start = query.period.months?.let { query.today.minus(DatePeriod(months = it)) }
        val inPeriod = all.filter { start == null || ScreenDates.localDate(it.startedAt, query.zone) >= start }
        val sessions = inPeriod.mapNotNull { session ->
            valueOf(session, query.metric)?.let { session to it }
        }
        if (sessions.isEmpty()) {
            // A loaded working set without an estimate can only be over 10 reps; with no load there is no set to blame.
            val noEstimate = query.metric == ProgressMetric.E1RM && inPeriod.any { it.load != null }
            return empty(if (noEstimate) EmptyReason.NO_ESTIMATE else EmptyReason.NO_POINTS)
        }
        val convention = query.key.convention
        val perSession = sessions.map { (session, value) ->
            ChartPoint(session.startedAt, value.value, value.set, ProgressMetrics.marked(session, query.metric, convention), session.workoutId)
        }
        val lowerIsBetter = lowerIsBetter(query.key, query.metric)
        val weekly = perSession.size > MAX_POINTS
        val points = if (weekly) weeklyPoints(perSession, query, lowerIsBetter) else perSession
        val running = if (weekly) weekStartOf(query.today, query.weekStart).atStartOfDayIn(query.zone).toEpochMilliseconds() else null
        val forTrend = points.filter { it.startedAt != running }
        val trend = if (forTrend.size < 3) null else {
            val firstDay = ScreenDates.localDate(forTrend.first().startedAt, query.zone).toEpochDays()
            Trends.of(
                forTrend.map { (ScreenDates.localDate(it.startedAt, query.zone).toEpochDays() - firstDay).toDouble() },
                forTrend.map(ChartPoint::value),
            )
        }
        val best = if (lowerIsBetter) points.minByOrNull(ChartPoint::value) else points.maxByOrNull(ChartPoint::value)
        return ExerciseChart(points, weekly, sessions.size, points.first(), points.last(), best, trend, null)
    }

    /** The first metric of the convention over the last [last] sessions that have it, oldest first (the Library card). */
    fun sparkline(index: ProgressIndex, key: SeriesKey, last: Int = 12): List<Double> {
        val sessions = index.sessions(key)
        val metric = ProgressMetrics.available(key.convention, sessions).first()
        return sessions.mapNotNull { valueOf(it, metric)?.value }.takeLast(last)
    }

    private fun empty(reason: EmptyReason) = ExerciseChart(emptyList(), false, 0, null, null, null, null, reason)

    private class Value(val value: Double, val set: SetValue?)

    /** Volume is a sum over the session, with no set behind it; a session with none lifted has no volume point. */
    private fun valueOf(session: SessionPoint, metric: ProgressMetric): Value? =
        if (metric == ProgressMetric.VOLUME) {
            session.volumeKg.takeIf { it > 0.0 }?.let { Value(it, null) }
        } else {
            ProgressMetrics.value(session, metric)?.let { Value(it.value, it) }
        }

    private fun lowerIsBetter(key: SeriesKey, metric: ProgressMetric) =
        metric == ProgressMetric.LOAD && WorkingSets.convention(key.convention) == LoadConvention.ASSISTANCE

    private fun weekStartOf(date: LocalDate, first: DayOfWeek): LocalDate =
        date.minus((date.dayOfWeek.isoDayNumber - first.isoDayNumber + 7) % 7, DateTimeUnit.DAY)

    /**
     * One point per week, dated at the week's start: the best session (the lowest assistance on Assistance's load). On
     * equal values the marked session wins, then (on load) the one with more reps, then the first. The point carries
     * that session's mark, never another session's.
     */
    private fun weeklyPoints(points: List<ChartPoint>, query: ExerciseChartQuery, lowerIsBetter: Boolean): List<ChartPoint> {
        val order = compareBy<ChartPoint>({ if (lowerIsBetter) it.value else -it.value }, { !it.record }, { -(it.set?.reps ?: 0) })
        return points.groupBy { weekStartOf(ScreenDates.localDate(it.startedAt, query.zone), query.weekStart) }
            .map { (week, inWeek) ->
                val best = inWeek.sortedWith(order).first()
                ChartPoint(week.atStartOfDayIn(query.zone).toEpochMilliseconds(), best.value, best.set, best.record, null)
            }.sortedBy(ChartPoint::startedAt)
    }
}
