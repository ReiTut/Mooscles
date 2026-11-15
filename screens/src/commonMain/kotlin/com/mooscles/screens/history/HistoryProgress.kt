package com.mooscles.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.charts.*
import com.mooscles.designsystem.components.*
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.format.spokenWeight
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.illustration.BodyMap
import com.mooscles.designsystem.illustration.BodyMapLegend
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.exercise.toBodyRegion
import com.mooscles.screens.format.*
import com.mooscles.screens.progress.*
import com.mooscles.screens.resources.*
import com.mooscles.screens.workout.unitSymbol
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

@Composable
internal fun HistoryProgress(index: ProgressLoad<ProgressIndex>, calendar: ProgressCalendar, weekStart: DayOfWeek,
    pounds: Boolean, onProgress: (String) -> Unit, onTrain: () -> Unit) {
    var periodName by rememberSaveable { mutableStateOf(HistoryPeriod.THREE_MONTHS.name) }
    val period = HistoryPeriod.valueOf(periodName)
    // A null date follows the current week. A past selection keeps its date when the calendar advances.
    var selectedWeekStart by rememberSaveable { mutableStateOf<String?>(null) }
    val weekly = period == HistoryPeriod.WEEKLY
    val weekOffset = remember(weekly, selectedWeekStart, calendar.today, weekStart) {
        if (!weekly) 0 else selectedWeekStart?.let { saved ->
            val current = ExerciseCharts.weekStartOf(calendar.today, weekStart)
            val selected = ExerciseCharts.weekStartOf(LocalDate.parse(saved), weekStart)
            ((current.toEpochDays() - selected.toEpochDays()) / 7).toInt().coerceAtLeast(0)
        } ?: 0
    }
    val query = remember(period, calendar, weekStart, weekOffset) {
        OverallProgressQuery(period, calendar.today, calendar.zone, weekStart, weekOffset)
    }
    val load = key(query) { rememberProgressQuery(index, query) }
    val data = load.valueOrNull
    var musclesExpanded by rememberSaveable { mutableStateOf(false) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    var hasShownList by remember { mutableStateOf(false) }
    SideEffect { if (load !is ProgressLoad.Loading) hasShownList = true }
    if (!hasShownList && load is ProgressLoad.Loading) {
        // Wait outside the lazy list so its saved offset survives an index/query rebuilt after recreation.
        HistorySkeleton()
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("history-progress-list"), state = list, contentPadding = PaddingValues(vertical = MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
        item("periods") {
            Row(Modifier.historyItem().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                HistoryPeriod.entries.forEach { choice ->
                    MChip(historyPeriodLabel(choice), choice == period, { periodName = choice.name }, Modifier.testTag("history-period-${choice.name}"), MChipKind.SetType)
                }
            }
        }
        if (weekly) item("week-controls") {
            WeekControls(query, calendar,
                onPrevious = { selectedWeekStart = query.weekRangeStart.minus(1, DateTimeUnit.WEEK).toString() },
                onNext = {
                    selectedWeekStart = query.weekRangeStart.plus(1, DateTimeUnit.WEEK).toString().takeIf { weekOffset > 1 }
                })
        }
        when {
            load is ProgressLoad.Loading -> item("loading") { HistorySkeleton() }
            load is ProgressLoad.Failed -> item("failed") { EmptyState(MIcons.History, stringResource(Res.string.history_load_failed), "", Modifier.historyItem()) }
            weekly && data != null -> {
                item("muscle-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                        HistoryHeading(stringResource(Res.string.history_week_muscles))
                        MText(stringResource(Res.string.history_week_weighting), MTheme.type.caption,
                            color = MTheme.colors.textSecondary, modifier = Modifier.historyItem())
                    }
                }
                if (data.muscles.isEmpty() && data.fullBodySets == 0.0) item("no-weekly-sets") {
                    EmptyState(MIcons.History, stringResource(Res.string.history_week_empty), "", Modifier.historyItem())
                }
                muscleRows(data, weekly = true, expanded = true, onExpand = {})
                if (data.workouts > 0) item("totals") { HistoryTotals(data, pounds, weekly = true) }
            }
            data == null || !data.hasHistory -> item("empty") {
                EmptyState(MIcons.History, stringResource(Res.string.history_empty), stringResource(Res.string.history_empty_body), Modifier.historyItem(), stringResource(Res.string.history_train) to onTrain)
            }
            data.workouts == 0 -> item("no-period") {
                EmptyState(MIcons.History, stringResource(Res.string.history_no_period), "", Modifier.historyItem(), stringResource(Res.string.history_all) to { periodName = HistoryPeriod.ALL.name })
            }
            else -> {
                item("totals") { HistoryTotals(data, pounds) }
                item("frequency") {
                    MText(stringResource(Res.string.history_frequency, number(data.weeksWithWorkouts.toDouble()), number(data.weeks.toDouble())), MTheme.type.bodyMd,
                        modifier = Modifier.historyItem().testTag("history-frequency"))
                }
                item("workouts-chart") { HistoryBars(data, false, pounds, calendar) }
                item("volume-chart") { HistoryBars(data, true, pounds, calendar) }
                item("muscle-heading") { HistoryHeading(stringResource(Res.string.history_muscles)) }
                item("muscle-map") {
                    Column(Modifier.historyItem()) {
                        BodyMap(data.primaryRegions.mapTo(HashSet()) { it.toBodyRegion() }, data.secondaryRegions.mapTo(HashSet()) { it.toBodyRegion() },
                            Modifier.fillMaxWidth().graphicsLayer {
                                // Reuse the static vector drawing while this lazy item moves. Content/size
                                // changes invalidate the layer; other maps and chart drawing stay unchanged.
                                compositingStrategy = CompositingStrategy.Offscreen
                            })
                        BodyMapLegend(primaryLabel = stringResource(Res.string.history_muscle_more), secondaryLabel = stringResource(Res.string.history_muscle_less))
                    }
                }
                muscleRows(data, weekly = false, expanded = musclesExpanded, onExpand = { musclesExpanded = true })
                item("most-title") { HistoryHeading(stringResource(Res.string.history_most)) }
                items(data.mostPerformed, key = { "frequent-${it.exercise.id}" }) { item ->
                    MButton(exerciseName(item.exercise) + " · " + pluralCount(Res.plurals.history_workout_count, item.sessions), { onProgress(item.exercise.id) },
                        Modifier.historyItem().testTag("history-frequent-${item.exercise.id}"), MButtonVariant.Text)
                }
            }
        }
    }
}

@Composable
private fun WeekControls(query: OverallProgressQuery, calendar: ProgressCalendar, onPrevious: () -> Unit, onNext: () -> Unit) {
    val start = shortDateAged(query.weekRangeStart.atStartOfDayIn(calendar.zone).toEpochMilliseconds(), calendar.zone, calendar.today)
    val end = shortDateAged(query.weekRangeEnd.atStartOfDayIn(calendar.zone).toEpochMilliseconds(), calendar.zone, calendar.today)
    val spacing = Arrangement.spacedBy(MTheme.space.s2)
    val previous: @Composable (Modifier) -> Unit = { modifier ->
        MButton(stringResource(Res.string.history_week_previous), onPrevious,
            modifier.testTag("history-week-previous"), MButtonVariant.Secondary)
    }
    val next: @Composable (Modifier) -> Unit = { modifier ->
        MButton(stringResource(Res.string.history_week_next), onNext,
            modifier.testTag("history-week-next"), MButtonVariant.Secondary, enabled = query.weekOffset > 0)
    }
    Column(Modifier.historyItem(), verticalArrangement = spacing) {
        MText(stringResource(Res.string.history_week_range, start, end), MTheme.type.titleSm,
            modifier = Modifier.testTag("history-week-range").semantics { heading() })
        if (query.weekOffset == 0) {
            MText(stringResource(Res.string.history_week_to_date), MTheme.type.caption, color = MTheme.colors.textSecondary)
        }
        if (LocalDensity.current.fontScale >= 1.5f) {
            previous(Modifier.fillMaxWidth())
            next(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = spacing) {
                previous(Modifier.weight(1f))
                next(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.muscleRows(data: OverallProgress, weekly: Boolean, expanded: Boolean, onExpand: () -> Unit) {
    items(if (expanded) data.muscles else data.muscles.take(6), key = { "muscle-${it.region}" }) { muscle ->
        Column(Modifier.historyItem().testTag("history-muscle-${muscle.region}"), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
            MText(muscleRowLabel(regionName(muscle.region), muscle.sets, muscle.perWeek, weekly), MTheme.type.bodyMd)
            Box(Modifier.fillMaxWidth().height(MTheme.space.s1).background(MTheme.colors.borderSubtle)) {
                Box(Modifier.fillMaxWidth((muscle.sets / data.muscles.first().sets).toFloat()).fillMaxHeight().background(MTheme.colors.accentSolid))
            }
        }
    }
    if (data.muscles.size > 6 && !expanded) item("more-muscles") {
        MButton(stringResource(Res.string.history_more_muscles, number(data.muscles.size.toDouble())), onExpand, Modifier.historyItem(), MButtonVariant.Text)
    }
    if (data.fullBodySets > 0) item("full-body") {
        MText(muscleRowLabel(stringResource(Res.string.history_full_body), data.fullBodySets, data.fullBodySets / data.weeks, weekly),
            MTheme.type.bodyMd, modifier = Modifier.historyItem())
    }
}

@Composable
private fun muscleRowLabel(name: String, sets: Double, perWeek: Double, weekly: Boolean): String = if (weekly) {
    stringResource(if (sets == 1.0) Res.string.history_week_muscle_one else Res.string.history_week_muscle_many, name, number(sets))
} else {
    stringResource(Res.string.history_muscle_row, name, number(sets), number(perWeek))
}

@Composable
private fun HistoryTotals(data: OverallProgress, pounds: Boolean, weekly: Boolean = false) {
    val stats = buildList {
        add(stringResource(Res.string.history_workouts) to number(data.workouts.toDouble()))
        if (!weekly) add(stringResource(Res.string.history_per_week) to number(data.workoutsPerWeek))
        add(stringResource(if (weekly) Res.string.history_week_logged_sets else Res.string.history_sets) to number(data.sets.toDouble()))
        add(stringResource(Res.string.history_volume) to load(data.volumeKg, pounds))
    }
    val measurer = rememberTextMeasurer()
    val style = MTheme.type.numMd
    val font = LocalDensity.current.fontScale
    val gap = MTheme.space.s4
    BoxWithConstraints(Modifier.historyItem().testTag("history-period-stats")) {
        val half = with(LocalDensity.current) { ((maxWidth - gap) / 2).roundToPx() }
        val oneColumn = font >= 1.5f || stats.any { (_, value) -> measurer.measure(value, style, softWrap = false).size.width > half }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            stats.chunked(if (oneColumn) 1 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (label, value) -> Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        MText(value, style)
                        MText(label, MTheme.type.caption, color = MTheme.colors.textSecondary)
                    } }
                }
            }
        }
    }
}

@Composable
private fun HistoryBars(data: OverallProgress, volume: Boolean, pounds: Boolean, calendar: ProgressCalendar) {
    val title = stringResource(when {
        volume && data.bucketKind == HistoryBucketKind.WEEK -> Res.string.history_volume_week
        volume && data.bucketKind == HistoryBucketKind.MONTH -> Res.string.history_volume_month
        volume -> Res.string.history_volume_quarter
        data.bucketKind == HistoryBucketKind.WEEK -> Res.string.history_workouts_week
        data.bucketKind == HistoryBucketKind.MONTH -> Res.string.history_workouts_month
        else -> Res.string.history_workouts_quarter
    })
    var table by rememberSaveable(data.period, volume) { mutableStateOf(false) }
    var selected by remember(data, volume) { mutableStateOf<Int?>(null) }
    val values = remember(data, volume, pounds) { data.buckets.map { if (volume) WeightUnits.fromKg(it.volumeKg, pounds) else it.workouts.toDouble() } }
    val bars = remember(values) { values.map(::BarDatum) }
    val labels = data.buckets.map { bucket ->
        val millis = bucket.start.atStartOfDayIn(calendar.zone).toEpochMilliseconds()
        val base = when (bucket.kind) {
            HistoryBucketKind.WEEK -> chartWeekOf(millis, calendar.zone, calendar.today)
            HistoryBucketKind.MONTH -> ScreenDates.monthYear(bucket.start.year, bucket.start.month)
            HistoryBucketKind.QUARTER -> stringResource(Res.string.history_quarter, ((bucket.start.month.number - 1) / 3 + 1).toString(), bucket.start.year.toString())
        }
        if (bucket.partial) stringResource(Res.string.history_partial, base) else base
    }
    val texts = data.buckets.map { if (volume) load(it.volumeKg, pounds) else pluralCount(Res.plurals.history_workout_count, it.workouts) }
    val spoken = data.buckets.map { if (volume) spokenWeight(MoosclesNumbers.plain(WeightUnits.fromKg(it.volumeKg, pounds)), pounds) else pluralCount(Res.plurals.history_workout_count, it.workouts) }
    val max = values.indices.maxByOrNull { values[it] } ?: 0
    val total = if (volume) spokenWeight(MoosclesNumbers.plain(WeightUnits.fromKg(data.volumeKg, pounds)), pounds) else pluralCount(Res.plurals.history_workout_count, data.workouts)
    val mean = values.average().takeIf { it.isFinite() } ?: 0.0
    val trend = if (volume) data.volumeTrend else data.workoutTrend
    val trendText = trend?.let { stringResource(when (it) { Trend.UP -> Res.string.progress_trend_up; Trend.DOWN -> Res.string.progress_trend_down; Trend.FLAT -> Res.string.progress_trend_flat }) }
    val summary = stringResource(Res.string.history_chart_summary, title,
        spokenDate(data.rangeStart.atStartOfDayIn(calendar.zone).toEpochMilliseconds(), calendar.zone, calendar.today),
        spokenDate(data.rangeEnd.atStartOfDayIn(calendar.zone).toEpochMilliseconds(), calendar.zone, calendar.today), total, number(mean),
        spoken.getOrElse(max) { "0" }, labels.getOrElse(max) { "" }, values.count { it == 0.0 }.toString()) + (trendText?.let { "; $it" } ?: "")
    val locale = currentNumberLocale()
    val thousands = stringResource(Res.string.progress_short_thousands, "{value}")
    Column(Modifier.historyItem().testTag(if (volume) "history-chart-volume" else "history-chart-workouts"), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MText(title, MTheme.type.titleSm, modifier = Modifier.semantics { heading() })
        if (volume) MText(stringResource(Res.string.history_volume_legend), MTheme.type.caption, color = MTheme.colors.textSecondary)
        if (table) {
            MText(summary, MTheme.type.caption)
            data.buckets.indices.reversed().forEach { i -> ChartTableRow(ChartTableRowData("history-${volume}-$i", labels[i], texts[i])) }
        } else {
            selected?.takeIf { it in labels.indices }?.let { i -> MText(stringResource(Res.string.history_chart_readout, labels[i], texts[i]), MTheme.type.bodyMd) }
            MBarChart(bars, summary, yLabel = { value ->
                if (value >= ProgressNumbers.SHORT_FROM) thousands.replace("{value}", MoosclesNumbers.localize(MoosclesNumbers.plain(round(value / 1000 * 10) / 10), locale))
                else MoosclesNumbers.localize(MoosclesNumbers.plain(value), locale)
            }, xLabels = ChartScale.labelIndices(labels.size).map { AxisLabel(it, labels[it]) }, selected = selected, onSelect = { selected = it }, integerTicks = !volume, contentKey = data.query)
        }
        ChartDataButton(table, { table = !table })
    }
}

@Composable
internal fun HistoryHeading(text: String) { MText(text, MTheme.type.titleSm, modifier = Modifier.historyItem().semantics { heading() }) }

@Composable
internal fun number(value: Double): String = MoosclesNumbers.localize(MoosclesNumbers.plain(round(value * 10) / 10), currentNumberLocale())

@Composable
private fun load(kg: Double, pounds: Boolean) = MoosclesNumbers.weight(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), unitSymbol(pounds), currentNumberLocale())

@Composable
internal fun historyPeriodLabel(period: HistoryPeriod): String = stringResource(when (period) {
    HistoryPeriod.WEEKLY -> Res.string.history_period_weekly
    HistoryPeriod.FOUR_WEEKS -> Res.string.history_period_4w
    HistoryPeriod.THREE_MONTHS -> Res.string.progress_period_3m
    HistoryPeriod.SIX_MONTHS -> Res.string.progress_period_6m
    HistoryPeriod.ONE_YEAR -> Res.string.progress_period_1y
    HistoryPeriod.ALL -> Res.string.progress_period_all
})
