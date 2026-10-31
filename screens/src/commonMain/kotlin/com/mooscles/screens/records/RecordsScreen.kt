package com.mooscles.screens.records

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import com.mooscles.designsystem.components.EmptyState
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MSearchField
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.MToastHost
import com.mooscles.designsystem.components.MToastHostState
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.format.ScreenDates
import com.mooscles.screens.format.catalogInPortuguese
import com.mooscles.screens.progress.ProgressLoad
import com.mooscles.screens.progress.ProgressMetric
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.progress.recordsMonthHeader
import com.mooscles.screens.progress.rememberProgressIndex
import com.mooscles.screens.progress.rememberProgressQuery
import com.mooscles.screens.progress.valueOrNull
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.records_current_only
import com.mooscles.screens.resources.records_empty_body
import com.mooscles.screens.resources.records_empty_title
import com.mooscles.screens.resources.records_first
import com.mooscles.screens.resources.records_none_found
import com.mooscles.screens.resources.records_only_first_action
import com.mooscles.screens.resources.records_only_first_body
import com.mooscles.screens.resources.records_only_first_title
import com.mooscles.screens.resources.records_rules
import com.mooscles.screens.resources.records_search
import com.mooscles.screens.resources.records_settings
import com.mooscles.screens.resources.records_title
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.RecordKind
import com.mooscles.ui.UiState
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import org.jetbrains.compose.resources.stringResource

/** The tag of the Records tab's list (and of the skeleton in its place while the timeline loads). */
const val RecordsScreenTag = "records-list"

/** The test tags of [RecordsRoute]. */
internal object RecordsTags {
    const val SETTINGS = "records-settings"
    const val RULES = "records-rules"
    const val RULES_SHEET = "records-rules-sheet"
    /** The search field's box, around the field: the node with the text action is the field inside it. */
    const val SEARCH = "records-search"
    const val CURRENT_ONLY = "records-current-only"
    const val FIRST = "records-first"
    const val EMPTY = "records-empty"
    const val ONLY_FIRST = "records-only-first"
    const val NONE_FOUND = "records-none-found"
    const val SKELETON = "records-skeleton-row"

    /** The parts of a row (unmerged tree). */
    const val ROW_NAME = "records-row-name"
    const val ROW_KIND = "records-row-kind"
    const val ROW_VALUE = "records-row-value"
    const val ROW_DATE = "records-row-date"
    const val ROW_CURRENT = "records-row-current"
    const val BADGE = "records-row-badge"
    const val BADGE_FIRST = "records-row-badge-first"

    /** Every row's tag starts with this, and nothing else's does. */
    const val ROW_PREFIX = "record-"

    /** Every month header's tag starts with this. */
    const val MONTH_PREFIX = "records-month-"

    /** The row of the record whose [RecordTimelines.key] is [key]. */
    fun row(key: String) = ROW_PREFIX + key

    /** The header of a month: `records-month-2026-10`. */
    fun month(year: Int, month: Int) = MONTH_PREFIX + year + "-" + month.toString().padStart(2, '0')

    /** The [index]th rule of the rules sheet, from 0. */
    fun rule(index: Int) = "records-rule-$index"
}

/** What the tab reads from the world: the clock (for the spoken dates' year) and the zone the months are in. Tests pass fixed ones. */
@Immutable
internal class RecordsSeams(val nowMillis: () -> Long, val zone: () -> TimeZone) {
    companion object {
        val System = RecordsSeams(
            nowMillis = { Clock.System.now().toEpochMilliseconds() },
            zone = { TimeZone.currentSystemDefault() },
        )
    }
}

/** The zone the months are read in and "today", taken once when the tab opens. */
@Immutable
private class RecordsCalendar(val zone: TimeZone, val today: LocalDate)

/**
 * The Records tab (spec "Recordes (aba)" and D11): "Records" with the Settings shortcut ([onOpenSettings]) and "How we
 * count records" (`Info`), which opens [RecordRulesSheet]; the search ("Search exercise", both languages) and the chips
 * "Current only" and "First records" fixed under the top bar; and the timeline, by local month newest first under a
 * sticky header ("October 2026 · 5 records"), one [RecordRow] per record.
 *
 * Without a chip the timeline is the records that beat something; "First records" adds the first ones, and "Current
 * only" lists just the records that still hold (first ones included) and leaves the other chip without a say. A search
 * keeps the records of the exercises it finds, and adds their current records even when they are first ones, so the
 * current record is on top without a chip; with "Current only" it is the intersection. A row opens [onOpenProgress] on
 * the record's metric ([ProgressMetric.LOAD] or [ProgressMetric.REPS]); its accessibility action "Open workout" opens
 * [onOpenWorkout].
 *
 * The timeline comes from the progress cache ([rememberProgressIndex], [rememberProgressQuery] with a [RecordsQuery]),
 * never computed in composition. While [UiState.loading], the index or the query is on its way, the list is a
 * skeleton; with no record at all, an empty state; with only first records and nothing asked for, an empty state whose
 * button turns "First records" on; otherwise, with none that fits, "No records found". A failed build or query shows
 * "No records found" too, never a skeleton that would wait for good. The search, the chips, the open
 * sheet and the scroll are kept with `rememberSaveable`; a new search or chip starts the list from the top once its own
 * result is shown (a newer month it adds is above the old first row). Errors of
 * the state show as this screen's toast ([ScreenErrorToast]).
 */
@Composable
fun RecordsRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenProgress: (exerciseId: String, metric: ProgressMetric) -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RecordsScreen(state, actions, onOpenProgress, onOpenWorkout, onOpenSettings, modifier, RecordsSeams.System)
}

/** [RecordsRoute] with its [seams]. */
@Composable
internal fun RecordsScreen(
    state: UiState,
    actions: MoosclesActions,
    onOpenProgress: (exerciseId: String, metric: ProgressMetric) -> Unit,
    onOpenWorkout: (workoutId: String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    seams: RecordsSeams = RecordsSeams.System,
) {
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var currentOnly by rememberSaveable { mutableStateOf(false) }
    var firstRecords by rememberSaveable { mutableStateOf(false) }
    var rulesOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val host = remember { MToastHostState() }
    val rulesFocus = remember { FocusRequester() }
    var focusReturns by remember { mutableIntStateOf(0) }
    val calendar = remember {
        val zone = seams.zone()
        RecordsCalendar(zone, ScreenDates.localDate(seams.nowMillis(), zone))
    }

    val text = search.text.trim()
    // No index of the loading state's empty lists: kept as the previous index, it would show "no records" until the
    // real one is built.
    val index = if (state.loading) ProgressLoad.Loading else rememberProgressIndex(state.history, state.records)
    val timeline = rememberProgressQuery(index, RecordsQuery(text, currentOnly, firstRecords, calendar.zone))

    // The callbacks are read when a row is tapped, so a host's new lambdas never recompose the rows.
    val openProgress by rememberUpdatedState(onOpenProgress)
    val openWorkout by rememberUpdatedState(onOpenWorkout)
    // The search keeps its focus after "Search" on the keyboard. The screen lets it go before it leaves (a row, its
    // "Open workout", Settings) and when the list is dragged, so the field never holds the focus across a visit. Only
    // the search's focus: a row reached by keyboard keeps its own. In touch mode (a tap) a cleared focus goes nowhere
    // and the scroll comes back exactly; outside it (keyboard, mouse) Android hands the focus to the first focusable
    // row, and one half under the month's sticky header is brought into view (task 13 fix round 1 report).
    val focusManager = LocalFocusManager.current
    var searchFocused by remember { mutableStateOf(false) }
    val releaseSearch = { if (searchFocused) focusManager.clearFocus() }
    val actionsOf = remember(focusManager) {
        RowActions(
            open = { event ->
                val record = event.record
                releaseSearch()
                openProgress(record.exercise.id, if (record.kind == RecordKind.WEIGHT) ProgressMetric.LOAD else ProgressMetric.REPS)
            },
            openWorkout = { event ->
                releaseSearch()
                openWorkout(event.record.workoutId)
            },
        )
    }

    // A new search or chip starts the list from the top once its own result is shown: while the previous result is
    // still there, a scroll would be undone by the new one, which keeps its first row in place and so hides a newer
    // month it adds above it. The scroll is asked for before the frame's measure, which applies it with the new rows.
    // The first result shown (a recreation's too) keeps the scroll it has.
    val shownQuery = timeline.valueOrNull?.query
    val scrolledFor = remember { ShownChoice() }
    SideEffect {
        if (shownQuery != null && shownQuery != scrolledFor.query) {
            if (scrolledFor.query != null) listState.requestScrollToItem(0)
            scrolledFor.query = shownQuery
        }
    }
    // A drag, not any scroll: a new search scrolls to the top while the person is still typing.
    LaunchedEffect(listState, focusManager) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) releaseSearch() }
    }
    LaunchedEffect(focusReturns) {
        if (focusReturns == 0) return@LaunchedEffect
        // Once the button is back in the layout under the closed sheet.
        withFrameNanos { }
        runCatching { rulesFocus.requestFocus() }
    }

    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(
                title = stringResource(Res.string.records_title),
                actions = {
                    MIconButton(
                        MIcons.Settings,
                        contentDescription = stringResource(Res.string.records_settings),
                        onClick = {
                            releaseSearch()
                            onOpenSettings()
                        },
                        modifier = Modifier.testTag(RecordsTags.SETTINGS),
                    )
                    MIconButton(
                        MIcons.Info,
                        contentDescription = stringResource(Res.string.records_rules),
                        onClick = { rulesOpen = true },
                        modifier = Modifier.focusRequester(rulesFocus).testTag(RecordsTags.RULES),
                    )
                },
            )
            Box(Modifier.fillMaxWidth().testTag(RecordsTags.SEARCH).onFocusChanged { searchFocused = it.hasFocus }.padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2)) {
                MSearchField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = stringResource(Res.string.records_search),
                    onClear = { search = TextFieldValue() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RecordsChips(
                currentOnly = currentOnly,
                firstRecords = firstRecords,
                onCurrentOnly = { currentOnly = !currentOnly },
                onFirstRecords = { firstRecords = !firstRecords },
            )
            val shown = timeline.valueOrNull
            val content = Modifier.weight(1f).fillMaxWidth()
            // A loading index passes through the query as loading, a failed one as failed.
            if (state.loading || timeline is ProgressLoad.Loading) {
                RecordsSkeleton(content)
            } else {
                val built = index.valueOrNull
                val empty = when {
                    // A failure: nothing to show, said neutrally (the person may have many records).
                    shown == null -> EmptyKind.NONE_FOUND
                    built != null && built.events.isEmpty() && built.currentRecords.isEmpty() -> EmptyKind.NO_RECORDS
                    // Records, all first ones, hidden by D11 with nothing asked for: say where they are.
                    text.isEmpty() && !currentOnly && !firstRecords -> EmptyKind.ONLY_FIRST
                    else -> EmptyKind.NONE_FOUND
                }
                RecordsList(
                    timeline = shown,
                    empty = empty,
                    pounds = state.preferences.pounds,
                    calendar = calendar,
                    listState = listState,
                    actions = actionsOf,
                    onShowFirstRecords = { firstRecords = true },
                    modifier = content,
                )
            }
        }
        ScreenErrorToast(state.errorCode, actions, host)
        MToastHost(host, Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)))
    }

    if (rulesOpen) {
        RecordRulesSheet(
            onDismiss = {
                rulesOpen = false
                focusReturns++
            },
        )
    }
}

/** The query of the result the list last showed, so a new one is scrolled to the top once. Not state: nothing reads it to compose. */
private class ShownChoice(var query: RecordsQuery? = null)

/** What a row does when tapped and with its accessibility action, made once per screen. */
@Immutable
internal class RowActions(val open: (RecordEvent) -> Unit, val openWorkout: (RecordEvent) -> Unit)

/**
 * What the list says when it has no row: no record at all; only first records, which the timeline hides without the
 * chip; or nothing that fits the search or the chips (also after a failure).
 */
private enum class EmptyKind { NO_RECORDS, ONLY_FIRST, NONE_FOUND }

/** "Current only" and "First records", filters ([MChipKind.Filter], checkboxes) on one scrolling line. */
@Composable
private fun RecordsChips(currentOnly: Boolean, firstRecords: Boolean, onCurrentOnly: () -> Unit, onFirstRecords: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MTheme.space.s4),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MChip(
            label = stringResource(Res.string.records_current_only),
            selected = currentOnly,
            onClick = onCurrentOnly,
            modifier = Modifier.testTag(RecordsTags.CURRENT_ONLY),
            kind = MChipKind.Filter,
        )
        // "Current only" lists every current record, first ones included: this chip keeps its choice, without a say.
        MChip(
            label = stringResource(Res.string.records_first),
            selected = firstRecords,
            onClick = onFirstRecords,
            modifier = Modifier.testTag(RecordsTags.FIRST),
            kind = MChipKind.Filter,
            enabled = !currentOnly,
        )
    }
}

/** While the timeline is on its way: rows shaped like [RecordRow]. Not lazy, so the list's saved scroll waits for the list. */
@Composable
private fun RecordsSkeleton(modifier: Modifier) {
    Column(modifier.testTag(RecordsScreenTag).padding(top = MTheme.space.s2)) {
        repeat(SKELETON_ROWS) { RecordSkeletonRow(Modifier.testTag(RecordsTags.SKELETON)) }
    }
}

/**
 * The timeline: a sticky header per month and its rows; or, without a row, the empty state ([EmptyKind.NO_RECORDS])
 * or "No records found". [timeline] is null after a failure.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordsList(
    timeline: RecordsTimeline?,
    empty: EmptyKind,
    pounds: Boolean,
    calendar: RecordsCalendar,
    listState: LazyListState,
    actions: RowActions,
    onShowFirstRecords: () -> Unit,
    modifier: Modifier,
) {
    val portuguese = catalogInPortuguese()
    val bottom = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier.testTag(RecordsScreenTag),
        state = listState,
        contentPadding = PaddingValues(top = MTheme.space.s2, bottom = bottom + MTheme.space.s6),
    ) {
        if (timeline == null || timeline.total == 0) {
            item(key = "empty") {
                when (empty) {
                    EmptyKind.NO_RECORDS -> NoRecords()
                    EmptyKind.ONLY_FIRST -> OnlyFirstRecords(onShowFirstRecords)
                    EmptyKind.NONE_FOUND -> NoneFound()
                }
            }
            return@LazyColumn
        }
        for (month in timeline.months) {
            stickyHeader(key = "month-${month.year}-${month.month.number}", contentType = "month") {
                MonthHeader(month.year, month.month, month.items.size)
            }
            items(month.items, key = { RecordsTags.row(RecordTimelines.key(it)) }, contentType = { "record" }) { event ->
                RecordRow(event, portuguese, pounds, calendar.zone, calendar.today, actions)
            }
        }
    }
}

/** "October 2026 · 5 records", a heading on the page's background so the rows pass under it. */
@Composable
private fun MonthHeader(year: Int, month: Month, count: Int) {
    MText(
        recordsMonthHeader(year, month, count),
        MTheme.type.labelMd,
        color = MTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MTheme.colors.bgBase)
            .testTag(RecordsTags.month(year, month.number))
            .semantics { heading() }
            .padding(horizontal = MTheme.space.s4, vertical = MTheme.space.s2),
    )
}

/** No record at all: the first one shows up here, once working sets are completed. */
@Composable
private fun NoRecords() {
    EmptyState(
        icon = MIcons.PlateRecord,
        title = stringResource(Res.string.records_empty_title),
        body = stringResource(Res.string.records_empty_body),
        modifier = Modifier.testTag(RecordsTags.EMPTY),
    )
}

/** Every record is a first record, hidden without the chip: a button turns "First records" on. */
@Composable
private fun OnlyFirstRecords(onShowFirstRecords: () -> Unit) {
    EmptyState(
        icon = MIcons.PlateRecord,
        title = stringResource(Res.string.records_only_first_title),
        body = stringResource(Res.string.records_only_first_body),
        modifier = Modifier.testTag(RecordsTags.ONLY_FIRST),
        primaryAction = stringResource(Res.string.records_only_first_action) to onShowFirstRecords,
    )
}

/** Records exist, but none fits the search or the chips; or the timeline could not be made. */
@Composable
private fun NoneFound() {
    MText(
        stringResource(Res.string.records_none_found),
        MTheme.type.titleSm.copy(textAlign = TextAlign.Center),
        color = MTheme.colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecordsTags.NONE_FOUND)
            .padding(MTheme.space.s6),
    )
}

private const val SKELETON_ROWS = 6
