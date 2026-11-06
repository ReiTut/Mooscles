package com.mooscles.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.mooscles.designsystem.charts.CalendarDayMark
import com.mooscles.designsystem.charts.MonthCalendar
import com.mooscles.designsystem.components.*
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.format.*
import com.mooscles.screens.progress.*
import com.mooscles.screens.resources.*
import com.mooscles.screens.workout.ExercisePickerSheet
import com.mooscles.screens.workout.ScreenErrorToast
import com.mooscles.screens.workout.unitSymbol
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.*
import org.jetbrains.compose.resources.stringResource

const val HistoryScreenTag = "history-list"

/** History owns only saveable view state; every filter/period query runs in the existing background cache. */
@Composable
fun HistoryRoute(
    state: UiState,
    actions: MoosclesActions,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrain: () -> Unit,
    onOpenProgress: (String) -> Unit,
    modifier: Modifier = Modifier,
) = HistoryContent(state, actions, onOpenWorkout, onOpenSettings, onOpenTrain, onOpenProgress, modifier)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryContent(
    state: UiState,
    actions: MoosclesActions,
    onOpenWorkout: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTrain: () -> Unit,
    onOpenProgress: (String) -> Unit,
    modifier: Modifier = Modifier,
    seams: ProgressSeams = ProgressSeams.System,
) {
    var progress by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var routineIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var other by rememberSaveable { mutableStateOf(false) }
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var beaten by rememberSaveable { mutableStateOf(false) }
    var dayText by rememberSaveable { mutableStateOf<String?>(null) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val calendar = rememberProgressCalendar(seams, retry)
    val weekStart = seams.weekStart()
    var monthOrdinal by rememberSaveable { mutableIntStateOf(calendar.today.year * 12 + calendar.today.month.number - 1) }
    var collapsed by rememberSaveable { mutableStateOf(false) }
    val month = remember(monthOrdinal) { LocalDate(monthOrdinal / 12, monthOrdinal % 12 + 1, 1) }
    val index = key(retry) { rememberProgressIndex(state.history, state.records, state.loading) }
    val existingIds = remember(state.routines) { state.routines.map { it.id }.toSet() }
    val day = remember(dayText) { dayText?.let(LocalDate::parse) }
    val query = remember(search.text, routineIds, other, exerciseId, beaten, day, calendar.zone, existingIds) {
        HistoryQuery(search.text, routineIds.toSet(), other, existingIds, exerciseId, beaten, day, calendar.zone)
    }
    // Reset only a changed query's pending result, so it can never wear another filter's count or month label.
    val resultLoad = key(query, retry) { rememberProgressQuery(index, query) }
    val result = resultLoad.valueOrNull
    val historicalExercises = remember(state.exercises, index.valueOrNull) {
        val stored = index.valueOrNull?.historyData?.exercises.orEmpty()
        val ids = stored.map { it.id }.toSet()
        (state.exercises + stored).distinctBy { it.id }.filter { it.id in ids }
    }
    val selectedExercise = historicalExercises.firstOrNull { it.id == exerciseId }
    val monthQuery = remember(month, calendar.zone) { HistoryCalendarQuery(month.year, month.month.number, calendar.zone) }
    val monthLoad = key(monthQuery, retry) { rememberProgressQuery(index, monthQuery) }
    val marks = monthLoad.valueOrNull
    val list = rememberLazyListState()
    val segments = rememberSaveableStateHolder()
    var hasShownList by remember { mutableStateOf(false) }
    val shownChoice = remember { ShownHistoryQuery() }
    val toasts = remember { MToastHostState() }
    ScreenErrorToast(state.errorCode, actions, toasts)
    val clear = { search = TextFieldValue(); routineIds = emptyList(); other = false; exerciseId = null; beaten = false; dayText = null }
    val filtered = search.text.isNotBlank() || routineIds.isNotEmpty() || other || exerciseId != null || beaten || day != null
    // The first result, including after recreation, keeps the saved scroll. Reset only when a later filter's
    // own result is shown, before that frame is measured, so old anchors cannot undo the reset.
    SideEffect {
        if (!state.loading && resultLoad !is ProgressLoad.Loading) hasShownList = true
        if (result != null && shownChoice.query != result.query) {
            if (shownChoice.query != null) list.requestScrollToItem(0)
            shownChoice.query = result.query
        }
    }
    Box(modifier.fillMaxSize().background(MTheme.colors.bgBase)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            TopBar(stringResource(Res.string.history_title), actions = {
                MIconButton(MIcons.Settings, stringResource(Res.string.history_settings), onOpenSettings, Modifier.testTag("history-settings"))
            })
            Row(Modifier.historyItem().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                MChip(stringResource(Res.string.history_workouts), !progress, { progress = false }, Modifier.weight(1f).testTag("history-segment-workouts"), MChipKind.Segmented)
                MChip(stringResource(Res.string.history_progress), progress, { progress = true }, Modifier.weight(1f).testTag("history-segment-progress"), MChipKind.Segmented)
            }
            if (progress) {
                segments.SaveableStateProvider("progress") {
                    HistoryProgress(index, calendar, weekStart, state.preferences.pounds, onOpenProgress, onOpenTrain)
                }
            } else if (!hasShownList && (state.loading || resultLoad is ProgressLoad.Loading)) {
                // Do not clamp a restored list position against a temporary one-item lazy skeleton.
                HistorySkeleton()
            } else {
                LazyColumn(Modifier.fillMaxSize().testTag(HistoryScreenTag), state = list,
                    contentPadding = PaddingValues(vertical = MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
                    if (!state.loading && state.history.isNotEmpty()) {
                        item("calendar", "calendar") {
                            val monthTitle = stringResource(Res.string.history_month, ScreenDates.monthYear(month.year, month.month), pluralCount(Res.plurals.history_workout_count, marks?.totalWorkouts ?: 0))
                            val dayMarks = marks?.dayWorkouts.orEmpty().map { (date, ids) ->
                                val at = LocalDate(month.year, month.month.number, date).atStartOfDayIn(calendar.zone).toEpochMilliseconds()
                                CalendarDayMark(date, ids.size, stringResource(Res.string.history_calendar_day,
                                    spokenDate(at, calendar.zone, calendar.today), pluralCount(Res.plurals.history_workout_count, ids.size),
                                    marks?.dayWorkoutNames?.get(date).orEmpty().joinToString()))
                            }
                            val dayLabels = (0..6).map { weekdayText(DayOfWeek.entries[(weekStart.ordinal + it) % 7]) }
                            val daysInMonth = month.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).day
                            val firstOffset = (month.dayOfWeek.ordinal - weekStart.ordinal + 7) % 7
                            val earliest = marks?.earliestDay
                            MonthCalendar(monthTitle, stringResource(Res.string.history_calendar_summary, monthTitle, dayMarks.joinToString { it.day.toString() }),
                                dayLabels, daysInMonth, firstOffset, dayMarks,
                                today = calendar.today.takeIf { it.year == month.year && it.month == month.month }?.day,
                                selectedDay = day?.takeIf { it.year == month.year && it.month == month.month }?.day,
                                onDay = { picked ->
                                    val ids = marks?.dayWorkouts?.get(picked).orEmpty()
                                    if (ids.size == 1) onOpenWorkout(ids.single()) else if (ids.isNotEmpty()) dayText = LocalDate(month.year, month.month.number, picked).toString()
                                },
                                onPrevious = if (earliest != null && month > LocalDate(earliest.year, earliest.month, 1)) ({ monthOrdinal-- }) else null,
                                onNext = if (month < LocalDate(calendar.today.year, calendar.today.month, 1)) ({ monthOrdinal++ }) else null,
                                collapsed = collapsed, onToggleCollapsed = { collapsed = !collapsed }, modifier = Modifier.historyItem().testTag("history-calendar"))
                        }
                        item("filters", "filters") {
                            Column(Modifier.historyItem(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                                MSearchField(search, { search = it }, stringResource(Res.string.history_search), { search = TextFieldValue() }, Modifier.testTag("history-search"))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
                                    MChip(stringResource(Res.string.history_routine) + if (routineIds.isNotEmpty() || other) " · ${routineIds.size + if (other) 1 else 0}" else "", routineIds.isNotEmpty() || other, { sheet = "routine" }, Modifier.testTag("history-filter-routine"), MChipKind.Menu)
                                    MText(stringResource(Res.string.history_and), MTheme.type.caption)
                                    MChip(stringResource(Res.string.history_exercise) + (selectedExercise?.let { " · " + exerciseName(it) } ?: ""), exerciseId != null, { sheet = "exercise" }, Modifier.testTag("history-filter-exercise"), MChipKind.Menu)
                                    MText(stringResource(Res.string.history_and), MTheme.type.caption)
                                    MChip(stringResource(Res.string.history_with_pr), beaten, { beaten = !beaten }, Modifier.testTag("history-filter-pr"))
                                    day?.let {
                                        val label = shortDate(it.atStartOfDayIn(calendar.zone).toEpochMilliseconds(), calendar.zone)
                                        MChip(stringResource(Res.string.history_remove_day, label), true, { dayText = null }, Modifier.testTag("history-filter-day"))
                                    }
                                    if (filtered) MButton(stringResource(Res.string.history_clear), clear, Modifier.testTag("history-clear"), MButtonVariant.Text, MButtonSize.S)
                                }
                            }
                        }
                    }
                    when {
                        state.loading || resultLoad is ProgressLoad.Loading -> item("loading") { HistorySkeleton() }
                        resultLoad is ProgressLoad.Failed -> item("error") {
                            EmptyState(MIcons.History, stringResource(Res.string.history_load_failed), "", Modifier.historyItem(), stringResource(Res.string.history_retry) to { retry++ })
                        }
                        state.history.isEmpty() -> item("empty") {
                            EmptyState(MIcons.History, stringResource(Res.string.history_empty), stringResource(Res.string.history_empty_body), Modifier.historyItem(), stringResource(Res.string.history_train) to onOpenTrain)
                        }
                        result?.workouts.isNullOrEmpty() -> item("none") {
                            EmptyState(MIcons.History, stringResource(Res.string.history_no_results), "", Modifier.historyItem(), stringResource(Res.string.history_clear) to clear)
                        }
                        else -> {
                            item("count") { MText(pluralCount(Res.plurals.history_workout_count, result!!.workouts.size), MTheme.type.caption,
                                modifier = Modifier.historyItem().testTag("history-count").semantics { liveRegion = LiveRegionMode.Polite }) }
                            result!!.months.forEach { group ->
                                stickyHeader("month-${group.month}") {
                                    MText(stringResource(Res.string.history_month, ScreenDates.monthYear(group.month.year, group.month.month), pluralCount(Res.plurals.history_workout_count, group.workouts.size)),
                                        MTheme.type.labelLg, modifier = Modifier.fillMaxWidth().background(MTheme.colors.bgBase).historyItem().padding(vertical = MTheme.space.s2).semantics { heading() })
                                }
                                items(group.items, key = { it.workout.id }, contentType = { "workout" }) { item ->
                                    HistoryWorkoutCard(item, state.preferences.pounds, calendar, { onOpenWorkout(item.workout.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
        MToastHost(toasts)
    }
    if (sheet == "routine") MBottomSheet(onDismiss = { sheet = null }) {
        MText(stringResource(Res.string.history_routine), MTheme.type.titleSm)
        val ids = result?.sourceRoutineIds.orEmpty()
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(state.routines.filter { it.id in ids }, key = { it.id }) { routine ->
                MChip(routine.name, routine.id in routineIds, { routineIds = if (routine.id in routineIds) routineIds - routine.id else routineIds + routine.id }, Modifier.fillMaxWidth().testTag("history-routine-${routine.id}"))
            }
            item { MChip(stringResource(Res.string.history_other), other, { other = !other }, Modifier.fillMaxWidth().testTag("history-routine-other")) }
        }
        MButton(stringResource(Res.string.history_done), { sheet = null }, Modifier.fillMaxWidth())
    }
    if (sheet == "exercise") {
        ExercisePickerSheet(historicalExercises, state.history, { exerciseId = it.id; sheet = null }, { sheet = null })
    }
}

/** A result choice already displayed in this composition; a new composition must not reset its restored list. */
private class ShownHistoryQuery(var query: HistoryQuery? = null)

@Composable
internal fun Modifier.historyItem(): Modifier = widthIn(max = MTheme.sizes.contentMaxWidth).fillMaxWidth().padding(horizontal = MTheme.space.s4)

@Composable
internal fun HistorySkeleton() {
    Column(Modifier.historyItem().testTag("history-skeleton"), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        repeat(3) { Skeleton(Modifier.fillMaxWidth().height(MTheme.sizes.media)) }
    }
}

@Composable
private fun HistoryWorkoutCard(item: HistoryWorkout, pounds: Boolean, calendar: ProgressCalendar, onOpen: () -> Unit) {
    val workout = item.workout
    val numberLocale = currentNumberLocale()
    val date = shortDateAged(workout.startedAt, calendar.zone, calendar.today)
    val localDate = remember(workout.startedAt, calendar.zone) { ScreenDates.localDate(workout.startedAt, calendar.zone) }
    val weekday = weekdayText(localDate.dayOfWeek)
    val sets = pluralCount(Res.plurals.history_set_count, item.completedSets)
    val volume = MoosclesNumbers.weight(MoosclesNumbers.plain(WeightUnits.fromKg(item.volumeKg, pounds)), unitSymbol(pounds), numberLocale)
    val duration = durationText(((workout.endedAt ?: workout.startedAt) - workout.startedAt).coerceAtLeast(0) / 1000)
    val stats = stringResource(Res.string.history_card_stats, duration, sets, volume)
    val pr = pluralCount(Res.plurals.history_pr_count, item.prCount)
    val spoken = stringResource(Res.string.history_card_spoken,
        "$weekday, ${spokenDate(workout.startedAt, calendar.zone, calendar.today)}", workout.name, stats, pr)
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.historyItem().testTag("history-workout-${workout.id}")
        .background(MTheme.colors.bgSurface, RoundedCornerShape(MTheme.radius.lg))
        .clickable(role = Role.Button, onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = spoken }
        .padding(MTheme.space.s4), verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        if (largeFont) {
            // At large text sizes the date moves above the content instead of squeezing the title and numbers.
            MText("$date · $weekday", MTheme.type.numSm, color = MTheme.colors.textSecondary)
            HistoryWorkoutCardContent(item, stats, largeFont = true)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MTheme.space.s4),
            verticalAlignment = Alignment.Top) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MText(localDate.day.toString(), MTheme.type.numMd)
                MText(weekday, MTheme.type.caption, color = MTheme.colors.textSecondary)
                if (localDate <= calendar.today.minus(DatePeriod(years = 1))) {
                    MText(localDate.year.toString(), MTheme.type.caption, color = MTheme.colors.textSecondary)
                }
            }
            Column(Modifier.weight(1f)) { HistoryWorkoutCardContent(item, stats, largeFont = false) }
        }
    }
}

@Composable
private fun HistoryWorkoutCardContent(item: HistoryWorkout, stats: String, largeFont: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        MText(item.workout.name, MTheme.type.titleSm, maxLines = if (largeFont) Int.MAX_VALUE else 2,
            overflow = if (largeFont) TextOverflow.Clip else TextOverflow.Ellipsis)
        MText(stats, MTheme.type.numSm)
        val names = item.previewExercises.map { exerciseName(it) }
        MText(names.joinToString(" · "), MTheme.type.caption, color = MTheme.colors.textSecondary)
        if (item.beatenPrCount > 0) PrBadge(label = pluralCount(Res.plurals.history_pr_count, item.beatenPrCount),
            spoken = pluralCount(Res.plurals.history_pr_count, item.beatenPrCount))
    }
}
