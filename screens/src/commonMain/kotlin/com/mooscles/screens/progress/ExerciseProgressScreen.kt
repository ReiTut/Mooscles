package com.mooscles.screens.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.charts.AxisLabel
import com.mooscles.designsystem.charts.ChartDataButton
import com.mooscles.designsystem.charts.ChartScale
import com.mooscles.designsystem.charts.ChartTableRow
import com.mooscles.designsystem.charts.LinePoint
import com.mooscles.designsystem.charts.MChartLegendRecord
import com.mooscles.designsystem.charts.MLineChart
import com.mooscles.designsystem.charts.chartHeight
import com.mooscles.designsystem.components.MBottomSheet
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.Skeleton
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.conventionCaption
import com.mooscles.screens.format.conventionLabel
import com.mooscles.screens.format.currentWeekStart
import com.mooscles.screens.format.equipmentLabel
import com.mooscles.screens.format.exerciseName
import com.mooscles.screens.format.muscleLabel
import com.mooscles.screens.format.shortDate
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.progress_about
import com.mooscles.screens.resources.progress_assistance_legend
import com.mooscles.screens.resources.progress_highlight
import com.mooscles.screens.resources.progress_more
import com.mooscles.screens.resources.progress_no_estimate
import com.mooscles.screens.resources.progress_no_points
import com.mooscles.screens.resources.progress_one_session
import com.mooscles.screens.workout.MenuItem
import com.mooscles.screens.workout.RecompositionProbe
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/** The tag of the exercise progress screen's list. */
const val ExerciseProgressTag = "exercise-progress"

/** The test tags of [ExerciseProgressRoute]. */
internal object ProgressTags {
    const val MENU = "progress-menu"
    const val MENU_ABOUT = "progress-menu-about"
    const val TITLE = "progress-title"
    const val META = "progress-meta"
    const val CONVENTION = "progress-convention"
    const val CONVENTION_CAPTION = "progress-convention-caption"
    const val ASSISTANCE_LEGEND = "progress-assistance-legend"
    const val HIGHLIGHT = "progress-highlight"
    const val HIGHLIGHT_VALUE = "progress-highlight-value"
    const val SINCE = "progress-since"
    const val CHART = "progress-chart"
    const val CHART_SKELETON = "progress-chart-skeleton"
    const val CHART_MESSAGE = "progress-chart-message"
    const val READOUT = "progress-readout"
    const val LEGEND_RECORD = "progress-legend-record"
    const val DATA_TOGGLE = "progress-data-toggle"
    const val WEEKLY_NOTE = "progress-weekly-note"
    const val CONVENTION_NOTE = "progress-convention-note"
    const val EMPTY = "progress-empty"
    const val MISSING = "progress-missing"

    /** The records section's heading; every tag of the section starts with `progress-record`. */
    const val RECORDS = "progress-records"
    const val RECORDS_ALL = "progress-records-all"
    const val RECORD_WEIGHT_LABEL = "progress-record-weight-label"
    const val RECORD_WEIGHT = "progress-record-weight"
    const val RECORD_REPS_LABEL = "progress-record-reps-label"
    const val RECORD_ASSISTED_LABEL = "progress-record-assisted-label"

    /** The sessions' heading; every tag of the section starts with `progress-session`. */
    const val SESSIONS = "progress-sessions"

    /** The record badge on a set of a session row. */
    const val SESSION_RECORD = "progress-session-record"

    fun metric(metric: ProgressMetric) = "progress-metric-${metric.route}"
    fun period(period: ExercisePeriod) = "progress-period-${period.name}"

    /** The [index]th row of the data table, the newest first (from 0). */
    fun tableRow(index: Int) = "progress-table-row-$index"

    /** The [index]th reps record shown, the highest load first (from 0). */
    fun recordReps(index: Int) = "progress-record-reps-$index"

    /** The [index]th assisted reps record shown, the lowest assistance first (from 0). */
    fun recordAssisted(index: Int) = "progress-record-assisted-$index"

    /** The row of the session of [workoutId]. */
    fun session(workoutId: String) = "progress-session-$workoutId"
}

/** The keys this screen reports to `RecompositionProbe`, besides the rows' (their tags). */
internal object ProgressProbe {
    const val SCREEN = "progress-screen"
    const val CHART = "progress-chart-probe"
}

/** The axis texts the chart was given, in tick order: what a test reads to see that the marks are round. */
internal val ProgressAxisTexts = SemanticsPropertyKey<List<String>>("ProgressAxisTexts")
private var SemanticsPropertyReceiver.progressAxisTexts by ProgressAxisTexts

/**
 * What the screen reads from the world (spec D8): the clock and the zone, read on entry and at each period change, and
 * the first day of the week. Tests pass fixed ones.
 */
@Immutable
internal class ProgressSeams(
    val nowMillis: () -> Long,
    val zone: () -> TimeZone,
    val weekStart: @Composable () -> DayOfWeek,
) {
    companion object {
        val System = ProgressSeams(
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
            zone = { TimeZone.currentSystemDefault() },
            weekStart = { currentWeekStart() },
        )
    }
}

/**
 * A chart with the query it answers, the metrics of its series and how many sessions the series has in all. The cache
 * keeps the previous result while a new one is computed, so a chart that is still the previous metric's or period's
 * is drawn and worded by its own [query], never by the chips' new choice, and the chips show the [metrics] of the
 * same result.
 */
@Immutable
internal class ShownChart(
    val query: ExerciseChartQuery,
    val metrics: List<ProgressMetric>,
    val sessions: Int,
    val chart: ExerciseChart,
)

/**
 * The chart of [key] as one cache query: the convention's metrics ([ProgressMetrics.available], which on Added weight
 * read the sessions), the metric to show ([requested] when the convention has it, otherwise the first) and its chart
 * over [period]. One computation off the main thread, so with the index ready the chart lands on the next frame.
 */
internal data class ShownChartQuery(
    val key: SeriesKey,
    val requested: ProgressMetric?,
    val period: ExercisePeriod,
    val today: LocalDate,
    val zone: TimeZone,
    val weekStart: DayOfWeek,
) : ProgressQuery<ShownChart> {
    override fun compute(index: ProgressIndex): ShownChart {
        val sessions = index.sessions(key)
        val metrics = ProgressMetrics.available(key.convention, sessions)
        val chart = ExerciseChartQuery(key, ProgressMetrics.initial(requested, metrics), period, today, zone, weekStart)
        return ShownChart(chart, metrics, sessions.size, chart.compute(index))
    }
}

/**
 * The exercise progress screen (spec "Progresso do exercício", route `exercise/{id}?metric=`), one lazy list from the
 * top: the name in the screen's language (`title.md`, whole at any size) over "Chest · Barbell"; the current
 * convention with its caption (and, on Assistance, what less assistance means); the metric chips of the convention
 * (D6), starting on [metric] when the convention has it, otherwise on the first; the highlight, the last value in
 * `num.lg` and the change since the period's start as a fact; the period chips (D7, "All" first shown); and the
 * chart, with the reading of the selected point above it, the record legend when a point has the ring, and "View
 * data", which swaps the chart for its table rows as items of the list. A weekly chart says so under it.
 *
 * Under the chart, the line about the exercise's sessions in other conventions (plan decision 8), then its records
 * and its sessions ([progressSections]). An exercise without a session in any convention shows "You haven't done
 * this exercise yet." with "View in the library" ([onAbout]); one that is neither in the library nor in the history,
 * once the index is ready, "Exercise not found". A deleted exercise with history keeps working under its latest
 * snapshot's name.
 *
 * The index, the chart and the sections come from the cache ([rememberProgressIndex], [rememberProgressQuery]),
 * never computed in composition, the metrics with the chart in one query; until they arrive the chart's place is a
 * skeleton of its height, and after a failure it says there is nothing to chart. A new history keeps showing the
 * previous index's chart and sections, in the current unit, until its own is ready. The metric, the period, "View
 * data" and "View all" records are kept per exercise with `rememberSaveable`; the selection is not. Errors of the
 * state show as this screen's toast ([ScreenErrorToast]).
 */
@Composable
fun ExerciseProgressRoute(
    exerciseId: String,
    metric: String?,
    state: UiState,
    actions: MoosclesActions,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExerciseProgressScreen(exerciseId, metric, state, actions, onBack, onAbout, onOpenWorkout, modifier, ProgressSeams.System)
}

/** [ExerciseProgressRoute] with its [seams]. */
@Composable
internal fun ExerciseProgressScreen(
    exerciseId: String,
    metric: String?,
    state: UiState,
    actions: MoosclesActions,
    onBack: () -> Unit,
    onAbout: () -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
    seams: ProgressSeams = ProgressSeams.System,
) {
    RecompositionProbe(ProgressProbe.SCREEN)
    val host = remember { MToastHostState() }
    ScreenErrorToast(state.errorCode, actions, host)
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    // No index of the loading state's empty lists (a restore): it would say "not found" or "not done yet" for a moment.
    val load = if (state.loading) ProgressLoad.Loading else rememberProgressIndex(state.history, state.records)
    val index = load.valueOrNull
    val current = state.exercises.firstOrNull { it.id == exerciseId }
    val exercise = current ?: index?.snapshot(exerciseId)
    // The current convention, or, for an exercise that is gone, the one of its latest session.
    val convention = current?.convention ?: index?.conventionsOf(exerciseId)?.firstOrNull() ?: exercise?.convention
    val key = convention?.let { SeriesKey(exerciseId, it) }

    var metricChoice by rememberSaveable(exerciseId) { mutableStateOf(metric) }
    var periodName by rememberSaveable(exerciseId) { mutableStateOf(ExercisePeriod.ALL.name) }
    var showTable by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val period = ExercisePeriod.entries.firstOrNull { it.name == periodName } ?: ExercisePeriod.ALL
    val requested = ProgressMetric.parse(metricChoice)

    val calendar = remember(period) {
        val zone = seams.zone()
        ProgressCalendar(zone, ScreenDates.localDate(seams.nowMillis(), zone))
    }
    val weekStart = seams.weekStart()
    val shownLoad = key?.let { rememberProgressQuery(load, ShownChartQuery(it, requested, period, calendar.today, calendar.zone, weekStart)) }
    val shown = shownLoad?.valueOrNull
    // A new chart (another metric, period or history) starts with nothing selected.
    val selection = remember(shown) { mutableStateOf<Int?>(null) }
    val pounds = state.preferences.pounds
    // A failure shows that there is nothing to chart, never a skeleton that would wait for good.
    val failed = load is ProgressLoad.Failed || shownLoad is ProgressLoad.Failed
    // The sections stand on their own query: a failed chart query leaves them, a failed sections query hides them.
    val sections = key?.let { rememberProgressQuery(load, ProgressSectionsQuery(exerciseId, it.convention)) }?.valueOrNull
    var allRecords by rememberSaveable(exerciseId) { mutableStateOf(false) }
    val showAllRecords: () -> Unit = { allRecords = true }
    val name = exercise?.let { exerciseName(it) }.orEmpty()
    val otherConventions = sections?.otherConventions?.takeIf { it.isNotEmpty() }

    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(title = null, onBack = onBack) {
                MIconButton(
                    MIcons.EllipsisVertical,
                    stringResource(Res.string.progress_more),
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag(ProgressTags.MENU),
                )
            }
            val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag(ExerciseProgressTag),
                contentPadding = PaddingValues(bottom = bottom + MTheme.space.s6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (exercise != null) {
                    item(key = "title") { ProgressTitle(exercise) }
                }
                if (convention != null) {
                    item(key = "convention") { ProgressConvention(convention) }
                }
                if (key == null) {
                    when {
                        load is ProgressLoad.Failed -> item(key = "message") { ChartMessage(EmptyReason.NO_POINTS) }
                        // Neither in the library nor in a ready index's history: gone for good.
                        load is ProgressLoad.Ready && !state.loading -> item(key = "missing") { ProgressMissing() }
                        else -> item(key = "skeleton") { ChartSkeleton() }
                    }
                    return@LazyColumn
                }
                if (index != null && !failed && index.conventionsOf(exerciseId).isEmpty()) {
                    item(key = "empty") { ProgressEmpty(onAbout) }
                    return@LazyColumn
                }
                // Under the chart (or its sentence), before "View data".
                val conventionNote = { if (otherConventions != null) item(key = "convention-note") { ConventionNote(otherConventions) } }
                if (shown != null && !failed) {
                    val chosen = ProgressMetrics.initial(requested, shown.metrics)
                    item(key = "metrics") {
                        MetricChips(shown.metrics, chosen, shown.query.key.convention) {
                            selection.value = null
                            metricChoice = it.route
                        }
                    }
                }
                val chart = shown?.chart?.takeIf { !failed }
                if (chart?.last != null) {
                    item(key = "highlight") { ProgressHighlight(shown, pounds) }
                }
                item(key = "periods") {
                    PeriodChips(period) {
                        selection.value = null
                        periodName = it.name
                    }
                }
                when {
                    failed -> item(key = "message") { ChartMessage(EmptyReason.NO_POINTS) }
                    shown == null || chart == null -> item(key = "skeleton") { ChartSkeleton() }
                    chart.empty != null -> {
                        item(key = "message") { ChartMessage(chart.empty) }
                        conventionNote()
                    }
                    // One session of the exercise in all; one point of a longer history is still drawn.
                    shown.sessions == 1 -> {
                        item(key = "message") { ChartMessage(null) }
                        conventionNote()
                    }
                    else -> {
                        if (!showTable) {
                            item(key = "readout") { ProgressReadout(shown, pounds, selection) }
                            item(key = "chart") { ProgressChart(shown, pounds, selection) }
                        }
                        conventionNote()
                        item(key = "data") {
                            DataToggle(shown, showTable) {
                                selection.value = null
                                showTable = !showTable
                            }
                        }
                        if (showTable) {
                            val points = chart.points
                            items(points.size, key = { "row-${points[points.size - 1 - it].startedAt}" }) { i -> ProgressTableRow(shown, pounds, i) }
                        }
                    }
                }
                if (sections != null) {
                    progressSections(sections, name, key.convention, pounds, calendar, allRecords, showAllRecords, onOpenWorkout)
                }
            }
        }
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }

    if (menuOpen) {
        ProgressMenuSheet(
            exercise = exercise,
            onAbout = {
                menuOpen = false
                onAbout()
            },
            onDismiss = { menuOpen = false },
        )
    }
}

/** The width of an item of the list: the content column, up to `contentMaxWidth`, with the 16 dp sides. */
@Composable
private fun Modifier.progressItem(): Modifier =
    widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = MTheme.space.s4)

/** The name (a heading, `title.md`, wrapping whole at any size) and "Chest · Barbell" under it. */
@Composable
private fun ProgressTitle(exercise: ExerciseUi) {
    Column(Modifier.progressItem().padding(top = MTheme.space.s2), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        MText(
            exerciseName(exercise),
            MTheme.type.titleMd,
            color = MTheme.colors.textPrimary,
            modifier = Modifier.fillMaxWidth().testTag(ProgressTags.TITLE).semantics { heading() },
        )
        MText(
            stringResource(Res.string.progress_highlight, muscleLabel(exercise.muscle), equipmentLabel(exercise.equipment)),
            MTheme.type.bodyMd,
            color = MTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth().testTag(ProgressTags.META),
        )
    }
}

/** The convention the chart is in, its caption when it has one, and, on Assistance, what a lower value means. */
@Composable
private fun ProgressConvention(convention: String) {
    val caption = conventionCaption(convention)
    Column(
        Modifier.progressItem().padding(top = MTheme.space.s3).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s1),
    ) {
        MText(conventionLabel(convention), MTheme.type.labelMd, color = MTheme.colors.textPrimary, modifier = Modifier.testTag(ProgressTags.CONVENTION))
        if (caption != null) {
            MText(caption, MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.testTag(ProgressTags.CONVENTION_CAPTION))
        }
        if (WorkingSets.convention(convention) == LoadConvention.ASSISTANCE) {
            MText(
                stringResource(Res.string.progress_assistance_legend),
                MTheme.type.bodyMd,
                color = MTheme.colors.textSecondary,
                modifier = Modifier.testTag(ProgressTags.ASSISTANCE_LEGEND),
            )
        }
    }
}

/** One scrolling line of chips, at the list's sides, announced as one group ("1 of 4"). */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Box(Modifier.widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
            horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

/** The metrics of the convention as tabs ([MChipKind.Segmented]), the default first. */
@Composable
private fun MetricChips(metrics: List<ProgressMetric>, selected: ProgressMetric, convention: String, onSelect: (ProgressMetric) -> Unit) {
    ChipRow {
        for (metric in metrics) {
            MChip(
                label = metricLabel(metric, convention),
                selected = metric == selected,
                onClick = { if (metric != selected) onSelect(metric) },
                modifier = Modifier.testTag(ProgressTags.metric(metric)),
                kind = MChipKind.Segmented,
            )
        }
    }
}

/** The periods (D7) as one choice of a group ([MChipKind.SetType]). */
@Composable
private fun PeriodChips(selected: ExercisePeriod, onSelect: (ExercisePeriod) -> Unit) {
    ChipRow {
        for (period in ExercisePeriod.entries) {
            MChip(
                label = periodLabel(period),
                selected = period == selected,
                onClick = { if (period != selected) onSelect(period) },
                modifier = Modifier.testTag(ProgressTags.period(period)),
                kind = MChipKind.SetType,
            )
        }
    }
}

/**
 * The last value in `num.lg` (or the largest number style that fits the width, so it is never cut) with the metric's
 * name, and the change since the period's first point, as a fact in `text.secondary`: no danger color, no arrow. One
 * point has no change to tell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressHighlight(shown: ShownChart, pounds: Boolean) {
    val chart = shown.chart
    val last = chart.last ?: return
    val first = chart.first
    val metric = shown.query.metric
    val value = valueText(last, metric, pounds)
    val since = if (first != null && chart.points.size > 1) {
        sinceText(first, last, metric, pounds, shown.query.zone, chart.weekly)
    } else {
        null
    }
    BoxWithConstraints(Modifier.progressItem().padding(top = MTheme.space.s2).testTag(ProgressTags.HIGHLIGHT).semantics(mergeDescendants = true) {}) {
        val style = fittingNumberStyle(value, constraints.maxWidth)
        Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                MText(value, style, color = MTheme.colors.textPrimary, softWrap = false, modifier = Modifier.testTag(ProgressTags.HIGHLIGHT_VALUE))
                MText(metricLabel(metric, shown.query.key.convention), MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
            }
            if (since != null) {
                MText(since, MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.testTag(ProgressTags.SINCE))
            }
        }
    }
}

/** The largest of `num.lg`, `num.md` and `num.sm` in which [text] fits [maxWidth] px on one line (else `num.sm`). */
@Composable
private fun fittingNumberStyle(text: String, maxWidth: Int): TextStyle {
    val type = MTheme.type
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(text, maxWidth, type, measurer, density) {
        val candidates = listOf(type.numLg, type.numMd, type.numSm)
        candidates.firstOrNull { measurer.measure(text, it, softWrap = false, maxLines = 1, density = density).size.width <= maxWidth }
            ?: candidates.last()
    }
}

/** While the index or the chart is not ready: a skeleton of the chart's height ([chartHeight]). */
@Composable
private fun ChartSkeleton() {
    Box(Modifier.progressItem().padding(vertical = MTheme.space.s2)) {
        Skeleton(Modifier.fillMaxWidth().height(chartHeight()).testTag(ProgressTags.CHART_SKELETON))
    }
}

/**
 * The sentence in the chart's place: no estimate, nothing in the period (also the state of a failed build or query), or,
 * with no [reason], an exercise with one session in all.
 */
@Composable
private fun ChartMessage(reason: EmptyReason?) {
    val text = stringResource(
        when (reason) {
            EmptyReason.NO_ESTIMATE -> Res.string.progress_no_estimate
            EmptyReason.NO_POINTS, EmptyReason.NO_SESSIONS -> Res.string.progress_no_points
            null -> Res.string.progress_one_session
        },
    )
    MText(
        text,
        MTheme.type.bodyMd,
        color = MTheme.colors.textSecondary,
        modifier = Modifier.progressItem().padding(vertical = MTheme.space.s4).testTag(ProgressTags.CHART_MESSAGE),
    )
}

/**
 * The reading of the selected point, above the chart (not announced: the table is the screen reader's way). The line
 * keeps its place without a selection. Its own item, so a drag recomposes this line and the chart's selection only.
 */
@Composable
private fun ProgressReadout(shown: ShownChart, pounds: Boolean, selection: MutableState<Int?>) {
    val chart = shown.chart
    val query = shown.query
    val selected = selection.value?.takeIf { it in chart.points.indices }
    val readout = selected?.let { readoutText(chart.points[it], query.metric, pounds, query.zone, chart.weekly) }
    MText(
        readout.orEmpty(),
        MTheme.type.labelMd,
        color = MTheme.colors.textPrimary,
        modifier = Modifier.progressItem().padding(top = MTheme.space.s2).then(
            if (readout != null) Modifier.testTag(ProgressTags.READOUT) else Modifier.clearAndSetSemantics {},
        ),
    )
}

/**
 * The line chart and the record legend when a point has the ring. The values, the axis texts, the dates and the spoken
 * summary are made here once per chart; only [SelectableLineChart] reads [selection], so moving it rebuilds none of them.
 */
@Composable
private fun ProgressChart(shown: ShownChart, pounds: Boolean, selection: MutableState<Int?>) {
    RecompositionProbe(ProgressProbe.CHART)
    val chart = shown.chart
    val query = shown.query
    val metric = query.metric
    val zone = query.zone
    val points = remember(chart, metric, pounds) {
        chart.points.map { LinePoint(it.startedAt.toDouble(), ProgressNumbers.displayValue(it.value, metric, pounds), it.record) }
    }
    val range = remember(points) { ChartScale.line(points.map { it.y }) }
    val tickTexts = range.ticks.map { axisText(it, metric) }
    val locale = currentNumberLocale()
    val labels = remember(range, tickTexts, locale) {
        val byTick = range.ticks.zip(tickTexts).toMap()
        val label: (Double) -> String = { byTick[it] ?: MoosclesNumbers.localize(MoosclesNumbers.plain(it), locale) }
        label
    }
    val labelIndices = remember(points.size) { ChartScale.labelIndices(points.size) }
    val xLabels = labelIndices.map { AxisLabel(it, shortDate(chart.points[it].startedAt, zone)) }
    val summary = chartSummary(chart, metric, query.key.convention, query.period, pounds, zone, query.today)
    Column(Modifier.progressItem().padding(top = MTheme.space.s2), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        SelectableLineChart(
            points, summary, labels, xLabels, selection,
            Modifier.testTag(ProgressTags.CHART).semantics { progressAxisTexts = tickTexts },
            contentKey = metric to query.period,
        )
        if (chart.points.any(ChartPoint::record)) MChartLegendRecord(Modifier.testTag(ProgressTags.LEGEND_RECORD).semantics(mergeDescendants = true) {})
    }
}

/** [MLineChart] with the selection read here, the only place a drag recomposes. */
@Composable
private fun SelectableLineChart(
    points: List<LinePoint>,
    summary: String,
    labels: (Double) -> String,
    xLabels: List<AxisLabel>,
    selection: MutableState<Int?>,
    modifier: Modifier,
    contentKey: Any,
) {
    MLineChart(
        points = points,
        summary = summary,
        yLabel = labels,
        xLabels = xLabels,
        selected = selection.value?.takeIf { it in points.indices },
        onSelect = { selection.value = it },
        modifier = modifier,
        contentKey = contentKey,
    )
}

/** Row [newest] of the data table (0 is the newest point), formatted only while the list shows it. */
@Composable
private fun ProgressTableRow(shown: ShownChart, pounds: Boolean, newest: Int) {
    val query = shown.query
    val row = tableRow(shown.chart, newest, query.metric, pounds, query.zone, query.weekStart, query.today)
    ChartTableRow(row, Modifier.progressItem().testTag(ProgressTags.tableRow(newest)))
}

/** "View data" / "View chart", and the weekly note when the chart has one point per week. */
@Composable
private fun DataToggle(shown: ShownChart, showingTable: Boolean, onToggle: () -> Unit) {
    Column(Modifier.progressItem().padding(vertical = MTheme.space.s2), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        ChartDataButton(showingTable, onToggle, Modifier.testTag(ProgressTags.DATA_TOGGLE))
        if (shown.chart.weekly) {
            MText(weeklyNote(shown.query.metric), MTheme.type.bodyMd, color = MTheme.colors.textSecondary, modifier = Modifier.testTag(ProgressTags.WEEKLY_NOTE))
        }
    }
}

/** The screen's menu, a bottom sheet under the exercise's name: "About the exercise". */
@Composable
private fun ProgressMenuSheet(exercise: ExerciseUi?, onAbout: () -> Unit, onDismiss: () -> Unit) {
    MBottomSheet(onDismiss = onDismiss) {
        if (exercise != null) {
            MText(
                exerciseName(exercise),
                MTheme.type.titleMd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MTheme.space.s4)
                    .padding(bottom = MTheme.space.s2)
                    .semantics { heading() },
            )
        }
        MenuItem(MIcons.Info, stringResource(Res.string.progress_about), ProgressTags.MENU_ABOUT, onClick = onAbout)
        Spacer(Modifier.height(MTheme.space.s4))
    }
}
