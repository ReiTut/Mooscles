package com.mooscles.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    val query = remember(period, calendar, weekStart) { OverallProgressQuery(period, calendar.today, calendar.zone, weekStart) }
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
        when {
            load is ProgressLoad.Loading -> item("loading") { HistorySkeleton() }
            load is ProgressLoad.Failed -> item("failed") { EmptyState(MIcons.History, stringResource(Res.string.history_load_failed), "", Modifier.historyItem()) }
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
                        BodyMap(data.primaryRegions.mapTo(HashSet()) { it.toBodyRegion() }, data.secondaryRegions.mapTo(HashSet()) { it.toBodyRegion() }, Modifier.fillMaxWidth())
                        BodyMapLegend(primaryLabel = stringResource(Res.string.history_muscle_more), secondaryLabel = stringResource(Res.string.history_muscle_less))
                    }
                }
                items(if (musclesExpanded) data.muscles else data.muscles.take(6), key = { "muscle-${it.region}" }) { muscle ->
                    Column(Modifier.historyItem().testTag("history-muscle-${muscle.region}"), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                        MText(stringResource(Res.string.history_muscle_row, regionName(muscle.region), number(muscle.sets), number(muscle.perWeek)), MTheme.type.bodyMd)
                        Box(Modifier.fillMaxWidth().height(MTheme.space.s1).background(MTheme.colors.borderSubtle)) {
                            Box(Modifier.fillMaxWidth((muscle.sets / data.muscles.first().sets).toFloat()).fillMaxHeight().background(MTheme.colors.accentSolid))
                        }
                    }
                }
                if (data.muscles.size > 6 && !musclesExpanded) item("more-muscles") {
                    MButton(stringResource(Res.string.history_more_muscles, number(data.muscles.size.toDouble())), { musclesExpanded = true }, Modifier.historyItem(), MButtonVariant.Text)
                }
                if (data.fullBodySets > 0) item("full-body") {
                    MText(stringResource(Res.string.history_muscle_row, stringResource(Res.string.history_full_body), number(data.fullBodySets), number(data.fullBodySets / data.weeks)), MTheme.type.bodyMd, modifier = Modifier.historyItem())
                }
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
private fun HistoryTotals(data: OverallProgress, pounds: Boolean) {
    val labels = listOf(stringResource(Res.string.history_workouts), stringResource(Res.string.history_per_week), stringResource(Res.string.history_sets), stringResource(Res.string.history_volume))
    val values = listOf(number(data.workouts.toDouble()), number(data.workoutsPerWeek), number(data.sets.toDouble()), load(data.volumeKg, pounds))
    val measurer = rememberTextMeasurer()
    val style = MTheme.type.numMd
    val font = LocalDensity.current.fontScale
    val gap = MTheme.space.s4
    BoxWithConstraints(Modifier.historyItem().testTag("history-period-stats")) {
        val half = with(LocalDensity.current) { ((maxWidth - gap) / 2).roundToPx() }
        val oneColumn = font >= 1.5f || values.any { measurer.measure(it, style, softWrap = false).size.width > half }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            values.indices.toList().chunked(if (oneColumn) 1 else 2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { i -> Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                        MText(values[i], style)
                        MText(labels[i], MTheme.type.caption, color = MTheme.colors.textSecondary)
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
    HistoryPeriod.FOUR_WEEKS -> Res.string.history_period_4w
    HistoryPeriod.THREE_MONTHS -> Res.string.progress_period_3m
    HistoryPeriod.SIX_MONTHS -> Res.string.progress_period_6m
    HistoryPeriod.ONE_YEAR -> Res.string.progress_period_1y
    HistoryPeriod.ALL -> Res.string.progress_period_all
})
