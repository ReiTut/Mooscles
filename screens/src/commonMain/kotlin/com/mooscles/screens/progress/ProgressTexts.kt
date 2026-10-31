package com.mooscles.screens.progress

import androidx.compose.runtime.Composable
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.charts.ChartTableRowData
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.format.spokenDate
import com.mooscles.screens.format.weekOf
import com.mooscles.screens.resources.*
import com.mooscles.screens.workout.bestSetText
import com.mooscles.screens.workout.loadSpoken
import com.mooscles.screens.workout.unitSymbol
import com.mooscles.ui.RecordKind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Clock
import com.mooscles.designsystem.resources.Res as DsRes
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.ds_unit_lb_spoken

// The words of the exercise progress screen and of the records tab. Values arrive in exact kg (or reps) and are
// shown in the unit of [pounds]; dates come from the string resources, never from a platform formatter.

/** The minus sign of a negative change, U+2212, not the hyphen. */
private const val MINUS = "−"

/** The chart's name: "Load", or "Added weight" and "Assistance" when the convention names the load that way. */
@Composable
fun metricLabel(metric: ProgressMetric, convention: String): String = stringResource(
    when (metric) {
        ProgressMetric.LOAD -> when (WorkingSets.convention(convention)) {
            LoadConvention.BODYWEIGHT -> Res.string.progress_metric_added
            LoadConvention.ASSISTANCE -> Res.string.progress_metric_assistance
            else -> Res.string.progress_metric_load
        }
        ProgressMetric.REPS -> Res.string.progress_metric_reps
        ProgressMetric.E1RM -> Res.string.progress_metric_e1rm
        ProgressMetric.VOLUME -> Res.string.progress_metric_volume
    },
)

/** The period chip: "3 months", "All". */
@Composable
fun periodLabel(period: ExercisePeriod): String = stringResource(
    when (period) {
        ExercisePeriod.THREE_MONTHS -> Res.string.progress_period_3m
        ExercisePeriod.SIX_MONTHS -> Res.string.progress_period_6m
        ExercisePeriod.ONE_YEAR -> Res.string.progress_period_1y
        ExercisePeriod.ALL -> Res.string.progress_period_all
    },
)

/** The period inside a sentence: "last 6 months". */
@Composable
private fun periodSpoken(period: ExercisePeriod): String = stringResource(
    when (period) {
        ExercisePeriod.THREE_MONTHS -> Res.string.progress_period_spoken_3m
        ExercisePeriod.SIX_MONTHS -> Res.string.progress_period_spoken_6m
        ExercisePeriod.ONE_YEAR -> Res.string.progress_period_spoken_1y
        ExercisePeriod.ALL -> Res.string.progress_period_spoken_all
    },
)

@Composable
private fun loadString(displayValue: Double, pounds: Boolean): String =
    MoosclesNumbers.weight(MoosclesNumbers.plain(displayValue), unitSymbol(pounds), currentNumberLocale())

@Composable
private fun repsString(reps: Int): String = pluralCount(Res.plurals.progress_reps_count, reps)

/** A chart value as written: "92,5 kg", "12 repetições". */
@Composable
fun valueText(point: ChartPoint, metric: ProgressMetric, pounds: Boolean): String = valueString(point.value, metric, pounds)

@Composable
private fun valueString(kgOrReps: Double, metric: ProgressMetric, pounds: Boolean): String {
    val shown = ProgressNumbers.displayValue(kgOrReps, metric, pounds)
    return if (metric == ProgressMetric.REPS) repsString(round(shown).toInt()) else loadString(shown, pounds)
}

/** A chart value as read aloud, with the unit in full: "92,5 quilos", "12 repetições". */
@Composable
private fun spokenValue(kgOrReps: Double, metric: ProgressMetric, pounds: Boolean): String {
    val shown = ProgressNumbers.displayValue(kgOrReps, metric, pounds)
    if (metric == ProgressMetric.REPS) return repsString(round(shown).toInt())
    val number = MoosclesNumbers.localize(MoosclesNumbers.plain(shown), currentNumberLocale())
    return stringResource(if (pounds) DsRes.string.ds_unit_lb_spoken else DsRes.string.ds_unit_kg_spoken, number)
}

/** An axis label for [value], already in the unit shown: the thousands form from 10.000 ("42 mil"), else the plain number. */
@Composable
fun axisText(value: Double, metric: ProgressMetric): String {
    val locale = currentNumberLocale()
    if (metric != ProgressMetric.REPS && ProgressNumbers.isShort(value)) {
        val thousands = round(value / 1000.0 * 10.0) / 10.0
        return stringResource(Res.string.progress_short_thousands, MoosclesNumbers.localize(MoosclesNumbers.plain(thousands), locale))
    }
    return MoosclesNumbers.localize(MoosclesNumbers.plain(value), locale)
}

/**
 * The change from [first] to [last] as a fact, with its sign: "+12,5 kg desde 3 jun", "−5 kg desde 3 jun", "0 kg desde 3 jun".
 * On a [weekly] chart the first point is a week's start, so it reads "desde a semana de 3 jun" (P25).
 */
@Composable
fun sinceText(first: ChartPoint, last: ChartPoint, metric: ProgressMetric, pounds: Boolean, zone: TimeZone, weekly: Boolean = false): String {
    val change = ProgressNumbers.displayValue(last.value, metric, pounds) - ProgressNumbers.displayValue(first.value, metric, pounds)
    // The sign follows what is shown: a change under half a hundredth is 0.
    val magnitude = MoosclesNumbers.plain(abs(change))
    val sign = when {
        magnitude == "0" -> ""
        change > 0 -> "+"
        else -> MINUS
    }
    val amount = if (metric == ProgressMetric.REPS) repsString(round(abs(change)).toInt()) else loadString(abs(change), pounds)
    return stringResource(
        if (weekly) Res.string.progress_since_week else Res.string.progress_since,
        sign + amount,
        shortDate(first.startedAt, zone),
    )
}

/** The reading of a selected point: "28 out · 92,5 kg · 85 kg × 3"; a weekly point says "Semana de 6 out" (P25). */
@Composable
fun readoutText(point: ChartPoint, metric: ProgressMetric, pounds: Boolean, zone: TimeZone, weekly: Boolean = false): String {
    val date = if (weekly) weekOf(point.startedAt, zone) else shortDate(point.startedAt, zone)
    val value = valueText(point, metric, pounds)
    val set = point.set?.takeIf { metric != ProgressMetric.VOLUME }
        ?: return stringResource(Res.string.progress_highlight, date, value)
    return stringResource(Res.string.progress_readout, date, value, bestSetText(set.weightKg, set.reps, pounds))
}

/** The note under a weekly chart: one point per week, the best workout of it (never the sum, not even for volume). */
@Composable
fun weeklyNote(metric: ProgressMetric): String =
    stringResource(if (metric == ProgressMetric.VOLUME) Res.string.progress_weekly_note_volume else Res.string.progress_weekly_note)

private fun currentDay(zone: TimeZone): LocalDate = ScreenDates.localDate(Clock.System.now().toEpochMilliseconds(), zone)

/**
 * The spoken summary of a chart (spec D4): what it is, the period, the first and the last value with their dates in
 * full, the highest (the lowest on Assistance's load) with its date, the trend when there is one, and the count. A
 * weekly chart names the weeks and says it is the week's best workout. The trend words do not invert on Assistance.
 */
@Composable
fun chartSummary(
    chart: ExerciseChart,
    metric: ProgressMetric,
    convention: String,
    period: ExercisePeriod,
    pounds: Boolean,
    zone: TimeZone,
    today: LocalDate = currentDay(zone),
): String {
    val label = metricLabel(metric, convention)
    val periodText = periodSpoken(period)
    val first = chart.first
    val last = chart.last
    val best = chart.best
    if (first == null || last == null || best == null) return stringResource(Res.string.progress_highlight, label, periodText)
    val lowest = metric == ProgressMetric.LOAD && WorkingSets.convention(convention) == LoadConvention.ASSISTANCE
    val template = when {
        chart.weekly && lowest -> Res.string.progress_summary_lowest_weekly
        chart.weekly -> Res.string.progress_summary_weekly
        lowest -> Res.string.progress_summary_lowest
        else -> Res.string.progress_summary
    }
    var text = stringResource(
        template, label, periodText,
        spokenValue(first.value, metric, pounds), spokenDate(first.startedAt, zone, today),
        spokenValue(last.value, metric, pounds), spokenDate(last.startedAt, zone, today),
        spokenValue(best.value, metric, pounds), spokenDate(best.startedAt, zone, today),
    )
    chart.trend?.let { trend ->
        val word = stringResource(
            when (trend) {
                Trend.UP -> Res.string.progress_trend_up
                Trend.DOWN -> Res.string.progress_trend_down
                Trend.FLAT -> Res.string.progress_trend_flat
            },
        )
        text = stringResource(Res.string.progress_summary_part, text, word)
    }
    text = stringResource(Res.string.progress_summary_part, text, pluralCount(Res.plurals.progress_workouts, chart.sessionCount)) + "."
    return if (chart.weekly) "$text ${weeklyNote(metric)}" else text
}

/**
 * The data table behind a chart (D4), the newest row first: the date or the week, the value, the set when there is
 * one and whether it is a record. The running week of a weekly chart says so (D7). [today] is for that.
 */
@Composable
fun tableRows(
    chart: ExerciseChart,
    metric: ProgressMetric,
    pounds: Boolean,
    zone: TimeZone,
    weekStart: DayOfWeek,
    today: LocalDate = currentDay(zone),
): List<ChartTableRowData> = chart.points.indices.map { tableRow(chart, it, metric, pounds, zone, weekStart, today) }

/** Row [newest] of [tableRows] (0 is the newest point), alone: a lazy list formats only the rows it shows. */
@Composable
fun tableRow(
    chart: ExerciseChart,
    newest: Int,
    metric: ProgressMetric,
    pounds: Boolean,
    zone: TimeZone,
    weekStart: DayOfWeek,
    today: LocalDate = currentDay(zone),
): ChartTableRowData {
    val point = chart.points[chart.points.size - 1 - newest]
    val running = chart.weekly && point.startedAt == ExerciseCharts.weekStartOf(today, weekStart).atStartOfDayIn(zone).toEpochMilliseconds()
    val label = when {
        !chart.weekly -> shortDate(point.startedAt, zone)
        running -> stringResource(Res.string.progress_highlight, weekOf(point.startedAt, zone), stringResource(Res.string.progress_this_week))
        else -> weekOf(point.startedAt, zone)
    }
    val set = point.set?.takeIf { metric != ProgressMetric.VOLUME }
    return ChartTableRowData(
        key = point.startedAt.toString(),
        label = label,
        value = valueText(point, metric, pounds),
        detail = set?.let { bestSetText(it.weightKg, it.reps, pounds) },
        record = point.record,
    )
}

/** "outubro de 2026 · 5 recordes": the header of a month of the records timeline. */
@Composable
fun recordsMonthHeader(year: Int, month: Month, count: Int): String =
    stringResource(Res.string.records_month, ScreenDates.monthYear(year, month), pluralCount(Res.plurals.records_count, count))

/** "Carga" or "Repetições". */
@Composable
fun recordKindLabel(kind: RecordKind): String =
    stringResource(if (kind == RecordKind.WEIGHT) Res.string.records_kind_weight else Res.string.records_kind_reps)

/**
 * A record's line: "87,5 kg × 6 · antes 85 kg" for a load record, "80 kg × 8 · antes 6 repetições" for a reps record,
 * and just the set for a first record (no previous value).
 */
@Composable
fun recordWasText(kind: RecordKind, weightKg: Double, reps: Int, previousKg: Double?, previousReps: Int?, pounds: Boolean): String {
    val set = bestSetText(weightKg, reps, pounds)
    return when {
        kind == RecordKind.WEIGHT && previousKg != null ->
            stringResource(Res.string.records_was_load, set, loadString(WeightUnits.fromKg(previousKg, pounds), pounds))
        kind == RecordKind.REPS && previousReps != null ->
            pluralCount(Res.plurals.records_was_reps, previousReps, set, previousReps)
        else -> set
    }
}

/** A record's set with its date: "100 kg × 3 · 12 set". */
@Composable
fun recordLine(weightKg: Double, reps: Int, epochMillis: Long, pounds: Boolean, zone: TimeZone): String =
    stringResource(Res.string.progress_highlight, bestSetText(weightKg, reps, pounds), shortDate(epochMillis, zone))

@Composable
private fun spokenSet(weightKg: Double, reps: Int, pounds: Boolean): String =
    if (weightKg == 0.0) bestSetText(weightKg, reps, pounds) else spokenLoadByReps(loadSpoken(weightKg, pounds), reps)

/**
 * A record row read aloud, without the visual dot: "Supino, Carga: 87,5 quilos por 6, antes 85 quilos, 28 de outubro,
 * Atual". [current] adds "Atual"; [first] adds "Primeiro registro".
 */
@Composable
fun recordSpoken(
    exerciseName: String,
    kind: RecordKind,
    weightKg: Double,
    reps: Int,
    previousKg: Double?,
    previousReps: Int?,
    pounds: Boolean,
    epochMillis: Long,
    zone: TimeZone,
    today: LocalDate,
    current: Boolean,
    first: Boolean,
): String {
    var detail = spokenSet(weightKg, reps, pounds)
    val was = when {
        kind == RecordKind.WEIGHT && previousKg != null -> stringResource(Res.string.records_spoken_was_load, loadSpoken(previousKg, pounds))
        kind == RecordKind.REPS && previousReps != null -> pluralCount(Res.plurals.records_spoken_was_reps, previousReps, previousReps)
        else -> null
    }
    if (was != null) detail = stringResource(Res.string.records_spoken_join, detail, was)
    var text = stringResource(Res.string.records_spoken, exerciseName, recordKindLabel(kind), detail, spokenDate(epochMillis, zone, today))
    if (current) text = stringResource(Res.string.records_spoken_join, text, stringResource(Res.string.records_current))
    if (first) text = stringResource(Res.string.records_spoken_join, text, stringResource(Res.string.records_first_badge))
    return text
}

/** A session row of the progress screen read aloud: "28 de outubro, Treino A: A 40 × 10, 80 × 8". */
@Composable
fun sessionSpoken(epochMillis: Long, workoutName: String, setsText: String, zone: TimeZone, today: LocalDate): String =
    stringResource(Res.string.progress_session_spoken, spokenDate(epochMillis, zone, today), workoutName, setsText)
