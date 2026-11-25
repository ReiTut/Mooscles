package com.mooscles.screens.history

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.designsystem.charts.CalendarCollapseTag
import com.mooscles.designsystem.charts.CalendarNextTag
import com.mooscles.designsystem.charts.CalendarPreviousTag
import com.mooscles.designsystem.charts.ChartDataButtonTag
import com.mooscles.designsystem.charts.calendarDayTag
import com.mooscles.designsystem.charts.chartTableRowTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertAtLeast
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.flow.capture
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.exercisePickerRowTag
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.TimerUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.concurrent.Volatile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/** History UI behavior; queries themselves are covered by HistoryModelTest, not repeated through screenshots. */
@OptIn(ExperimentalTestApi::class)
class HistoryScreenTest {
    @AfterTest fun restoreLocale() { LocalAppLocale.restore() }

    private val zone = TimeZone.UTC
    private val now = LocalDateTime(2026, 11, 4, 12, 0).toInstant(zone).toEpochMilliseconds()
    private val seams = ProgressSeams({ now }, { zone }, { DayOfWeek.SUNDAY })
    private fun catalog(id: String): ExerciseUi = ExerciseCatalog.exercises.single { it.id == "catalog_$id" }.let {
        ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label)
    }
    private val bench = catalog("barbell_bench_flat")
    private val row = catalog("cable_row_wide_overhand")
    private fun workout(id: String, day: Int, exercise: ExerciseUi = bench, routine: String? = "upper",
        withRecord: Boolean = true, month: Int = 11): WorkoutUi {
        val start = LocalDateTime(2026, month, day, 10, 0).toInstant(zone).toEpochMilliseconds()
        // Without a record: a first performance, which beats nothing.
        val record = RecordUi(exercise, 22.0, 6, previousKg = 20.0, workoutId = id, date = start + 3_600_000)
        val name = "Workout $id" + when (id) { "upper-a" -> " Alpha"; "upper-b" -> " Bravo"; else -> "" }
        return WorkoutUi(id, name, start, start + 3_600_000,
            listOf(WorkoutExerciseUi("entry-$id", exercise, listOf(SetUi("set-$id", 6, 22.0, true)))),
            if (withRecord) listOf(record) else emptyList(), routine)
    }
    private fun state(): MutableState<UiState> = mutableStateOf(UiState(loading = false,
        exercises = listOf(bench, row), routines = listOf(RoutineUi("upper", "Upper split"), RoutineUi("lower", "Lower split")),
        history = listOf(workout("upper-b", 4), workout("lower", 4, row, "lower", withRecord = false),
            workout("upper-a", 3), workout("other", 15, row, "deleted", month = 10)),
        active = WorkoutUi("active", "Unchanged active", now,
            exercises = listOf(WorkoutExerciseUi("active-entry", bench, listOf(SetUi("draft", 7, 22.123456789))))),
        timer = TimerUi(remainingSeconds = 75, running = true)))

    private class Calls {
        val workouts = mutableListOf<String>()
        val progress = mutableListOf<String>()
        var settings = 0
        var train = 0
        val actions = RecordingActions()
    }

    private fun ComposeUiTest.show(state: MutableState<UiState> = state(), locale: String? = "en",
        dark: Boolean = false, font: Float = 1f, size: DpSize? = null,
        restoration: ScreenRestoration? = null, cache: ProgressIndexCache? = null): Calls {
        val calls = Calls()
        withScreen(locale = locale, dark = dark, fontScale = font, size = size, restoration = restoration,
            reducedMotion = true) {
            CompositionLocalProvider(LocalProgressIndexCache provides cache) {
                HistoryContent(state.value, calls.actions, { calls.workouts += it }, { calls.settings++ },
                    { calls.train++ }, { calls.progress += it }, seams = seams)
            }
        }
        waitForIdle()
        return calls
    }
    private fun ComposeUiTest.exists(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun ComposeUiTest.awaitTag(tag: String) { waitUntil(timeoutMillis = 10_000) { exists(tag) }; waitForIdle() }
    private fun ComposeUiTest.historyScroll(tag: String) {
        onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag(tag)); waitForIdle()
    }
    private fun ComposeUiTest.progressScroll(matcher: SemanticsMatcher) {
        onNodeWithTag("history-progress-list").performScrollToNode(matcher); waitForIdle()
    }
    private fun ComposeUiTest.progressScroll(tag: String) = progressScroll(hasTestTag(tag))
    private fun ComposeUiTest.count(expected: String) {
        waitUntil(timeoutMillis = 10_000) {
            runCatching {
                onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag("history-count"))
                onNodeWithTag("history-count").fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                    .orEmpty().joinToString("") { it.text } == expected
            }.getOrDefault(false)
        }
        waitForIdle()
    }
    private fun ComposeUiTest.search(text: String) {
        historyScroll("history-search")
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("history-search"))).performTextReplacement(text)
        waitForIdle()
    }
    private fun ComposeUiTest.progress() {
        onNodeWithTag("history-segment-progress").performClick(); awaitTag("history-period-stats")
    }

    private fun benchWorkout(id: String, day: Int, workingSets: Int, month: Int = 11): WorkoutUi {
        val saved = workout(id, day, month = month)
        val working = List(workingSets) { SetUi("$id-working-$it", 6, 22.0, completed = true) }
        val excluded = listOf(
            SetUi("$id-warmup", 6, 12.0, completed = true, warmup = true),
            SetUi("$id-unfinished", 6, 22.0),
        )
        return saved.copy(exercises = listOf(saved.exercises.single().copy(sets = working + excluded)), records = emptyList())
    }

    private fun ComposeUiTest.assertWeeklyMuscle(region: String, expected: String) {
        val matchingRow = hasAnyAncestor(hasTestTag("history-muscle-$region")) and hasText(expected)
        awaitProgressRow(matchingRow)
        onNode(matchingRow).assertIsDisplayed()
    }

    private fun ComposeUiTest.awaitProgressRow(matcher: SemanticsMatcher) {
        waitUntil(conditionDescription = "Progress shows $matcher", timeoutMillis = 10_000) {
            runCatching { progressScroll(matcher) }.isSuccess
        }
    }

    @Test fun calendarSingleDayOpensAndMultipleDayFiltersWhilePreservingActiveWorkout() = runComposeUiTest {
        val state = state(); val original = state.value; val calls = show(state)
        awaitTag(calendarDayTag(3)); onNodeWithTag(calendarDayTag(3)).performClick()
        assertEquals(listOf("upper-a"), calls.workouts)
        onNodeWithTag(calendarDayTag(4)).performClick()
        count("2 workouts")
        historyScroll("history-filter-day"); onNodeWithTag("history-filter-day").performClick()
        count("4 workouts")
        assertSame(original.active, state.value.active)
        assertEquals(original.timer, state.value.timer)
        assertTrue(calls.actions.calls.isEmpty())
    }

    @Test fun calendarMonthNavigationAndCollapseKeepTheirStateThroughRecreation() = runComposeUiTest {
        val restore = screenRestoration()
        show(restoration = restore, locale = null)
        awaitTag(calendarDayTag(3))
        assertFalse(exists(CalendarNextTag))
        onNodeWithTag(CalendarPreviousTag).performClick(); awaitTag(calendarDayTag(15))
        assertFalse(exists(CalendarPreviousTag))
        onNodeWithTag(CalendarCollapseTag).performClick(); waitForIdle()
        assertFalse(exists(calendarDayTag(15)))
        restore.emulateSaveAndRestore(); awaitTag(CalendarNextTag)
        assertFalse(exists(CalendarPreviousTag))
        assertFalse(exists(calendarDayTag(15)))
        onNodeWithTag(CalendarCollapseTag).performClick(); awaitTag(calendarDayTag(15))
    }

    @Test fun bilingualSearchMatchesExerciseAndWorkoutWordsTogether() = runComposeUiTest {
        show()
        search("supino upper")
        count("2 workouts")
        // SearchFolding treats punctuation as a separator; use a distinct word instead of the ubiquitous token "a".
        search("barbell alpha")
        count("1 workout")
        historyScroll("history-workout-upper-a"); onNodeWithTag("history-workout-upper-a").assertIsDisplayed()
    }

    @Test fun routineMultiSelectionAndOtherWorkoutsFilterAsSpecified() = runComposeUiTest {
        show(); awaitTag(calendarDayTag(3)); historyScroll("history-filter-routine")
        onNodeWithTag("history-filter-routine").performClick(); awaitTag("history-routine-upper")
        onNodeWithTag("history-routine-upper").performClick(); waitForIdle()
        awaitTag("history-routine-lower"); onNodeWithTag("history-routine-lower").performClick()
        onNodeWithText("Done").performClick(); count("3 workouts")
        historyScroll("history-clear"); onNodeWithTag("history-clear").performClick(); count("4 workouts")
        historyScroll("history-filter-routine"); onNodeWithTag("history-filter-routine").performClick()
        awaitTag("history-routine-other"); onNodeWithTag("history-routine-other").performClick()
        onNodeWithText("Done").performClick(); count("1 workout")
        historyScroll("history-workout-other"); onNodeWithTag("history-workout-other").assertIsDisplayed()
    }

    @Test fun historicalExercisePickerFiltersWithoutChangingRoutineOrSession() = runComposeUiTest {
        val state = state(); val before = state.value; val calls = show(state)
        awaitTag(calendarDayTag(3)); historyScroll("history-filter-exercise")
        onNodeWithTag("history-filter-exercise").performClick(); awaitTag(exercisePickerRowTag(bench.id, recent = true))
        onNodeWithTag(exercisePickerRowTag(bench.id, recent = true)).performClick(); count("2 workouts")
        assertEquals(before, state.value)
        assertTrue(calls.actions.calls.isEmpty())
    }

    @Test fun routineSearchDayAndRecordFiltersComposeWithoutLosingTheirSelections() = runComposeUiTest {
        val state = state(); val before = state.value; val calls = show(state)
        awaitTag(calendarDayTag(3)); historyScroll("history-filter-routine")
        onNodeWithTag("history-filter-routine").performClick(); awaitTag("history-routine-upper")
        onNodeWithTag("history-routine-upper").performClick()
        onNodeWithText("Done").performClick(); count("2 workouts")
        search("bravo"); count("1 workout")
        historyScroll("history-calendar")
        onNodeWithTag(calendarDayTag(4)).performClick(); count("1 workout")
        historyScroll("history-filter-pr")
        onNodeWithTag("history-filter-pr").performClick(); count("1 workout")
        historyScroll("history-workout-upper-b")
        onNodeWithTag("history-workout-upper-b").assertIsDisplayed()
        historyScroll("history-clear"); onNodeWithTag("history-clear").performClick(); count("4 workouts")
        assertEquals(before, state.value)
        assertTrue(calls.actions.calls.isEmpty())
    }

    @Test fun prFilterExcludesAWorkoutWithoutRecords() = runComposeUiTest {
        show(); awaitTag(calendarDayTag(3)); historyScroll("history-workout-lower")
        val description = onNodeWithTag("history-workout-lower").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertFalse(description.contains("1 PR"), description)
        historyScroll("history-filter-pr"); onNodeWithTag("history-filter-pr").performClick(); count("3 workouts")
        assertFalse(exists("history-workout-lower"))
    }

    @Test fun progressChangesPeriodsAndWorkoutsChartToItsDataTable() = runComposeUiTest {
        show(); progress()
        onNodeWithTag("history-period-${HistoryPeriod.THREE_MONTHS.name}").assertIsSelected()
        onNodeWithTag("history-period-${HistoryPeriod.ONE_YEAR.name}").performScrollTo().performClick()
        awaitTag("history-period-stats")
        val toggle = hasTestTag(ChartDataButtonTag) and hasAnyAncestor(hasTestTag("history-chart-workouts"))
        progressScroll(toggle); onNode(toggle).performClick()
        progressScroll(chartTableRowTag("history-false-11"))
        onNodeWithTag(chartTableRowTag("history-false-11")).assertIsDisplayed()
        progressScroll(toggle); onNode(toggle).performClick(); waitForIdle()
        assertFalse(exists(chartTableRowTag("history-false-11")))
    }

    @Test fun weeklyShowsCalendarTotalsAndRetainsPreviousWeekAcrossSegments() = runComposeUiTest {
        val state = state().also {
            it.value = it.value.copy(history = listOf(
                benchWorkout("current-week", day = 3, workingSets = 6),
                benchWorkout("previous-week", day = 31, workingSets = 10, month = 10),
            ))
        }
        show(state); progress()
        val weekly = onNodeWithTag("history-period-WEEKLY").assertTextEquals("Weekly")
        val fourWeeks = onNodeWithTag("history-period-FOUR_WEEKS").assertTextEquals("4 weeks")
        assertTrue(weekly.getUnclippedBoundsInRoot().left < fourWeeks.getUnclippedBoundsInRoot().left)
        weekly.performClick().assertIsSelected()
        onNodeWithTag("history-week-range").assertTextEquals("Nov 1 – Nov 7")
        onNodeWithTag("history-week-next").assertIsNotEnabled()
        progressScroll(hasText("Week to date")); onNodeWithText("Week to date").assertIsDisplayed()
        assertWeeklyMuscle("CHEST", "chest · 6 sets")
        progressScroll("history-week-range")
        capture("history-weekly-en")

        progressScroll("history-week-previous")
        onNodeWithTag("history-week-previous").assertTextEquals("Previous week").performClick()
        onNodeWithTag("history-week-range").assertTextEquals("Oct 25 – Oct 31")
        assertWeeklyMuscle("CHEST", "chest · 10 sets")

        onNodeWithTag("history-segment-workouts").performClick(); awaitTag("history-calendar")
        onNodeWithTag("history-segment-progress").performClick(); awaitTag("history-progress-list")
        progressScroll("history-period-WEEKLY"); weekly.assertIsSelected()
        progressScroll("history-week-range")
        onNodeWithTag("history-week-range").assertTextEquals("Oct 25 – Oct 31")
        onNodeWithTag("history-week-next").assertTextEquals("Next week").assertIsEnabled().performClick()
        assertWeeklyMuscle("CHEST", "chest · 6 sets")
        progressScroll("history-week-next"); onNodeWithTag("history-week-next").assertIsNotEnabled()

        progressScroll("history-period-FOUR_WEEKS"); fourWeeks.performClick().assertIsSelected()
        awaitProgressRow(hasAnyAncestor(hasTestTag("history-muscle-CHEST")) and hasText("chest · 16 sets · 4 per week"))
    }

    @Test fun weeklyRefreshesWhenSavedWorkoutsChangeAndExplainsAnEmptyWeek() = runComposeUiTest {
        val current = benchWorkout("current-week", day = 3, workingSets = 6)
        val state = state().also { it.value = it.value.copy(history = listOf(current)) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope)
        try {
            show(state, cache = cache); progress()
            onNodeWithTag("history-period-WEEKLY").performClick()
            assertWeeklyMuscle("CHEST", "chest · 6 sets")

            val entry = current.exercises.single()
            val edited = current.copy(exercises = listOf(entry.copy(sets = entry.sets.drop(2))))
            runOnIdle { state.value = state.value.copy(history = listOf(edited)) }
            assertWeeklyMuscle("CHEST", "chest · 4 sets")

            val warmupOnly = benchWorkout("warmup-only", day = 4, workingSets = 0)
            runOnIdle { state.value = state.value.copy(history = listOf(warmupOnly)) }
            awaitProgressRow(hasText("No working sets this week"))
            onNodeWithText("No working sets this week").assertIsDisplayed()

            val imported = benchWorkout("imported", day = 2, workingSets = 7)
            runOnIdle { state.value = state.value.copy(history = listOf(warmupOnly, imported)) }
            assertWeeklyMuscle("CHEST", "chest · 7 sets")
            assertWeeklyMuscle("TRICEPS", "triceps · 3.5 sets")
        } finally { scope.cancel() }
    }

    @Test fun weeklyInPortugueseShowsAccessibleNavigationAndFractionalTotalsAtTwoX() = runComposeUiTest {
        val current = benchWorkout("current-week", day = 3, workingSets = 7)
        val state = state().also { it.value = it.value.copy(history = listOf(current)) }
        show(state, locale = "pt-BR", font = LINEAR_TWO_X_FONT_SCALE, size = DpSize(320.dp, 640.dp)); progress()
        onNodeWithTag("history-period-WEEKLY").assertTextEquals("Semanal").performClick()
        progressScroll("history-week-range"); onNodeWithTag("history-week-range").assertTextEquals("1 nov – 7 nov")
        for ((tag, label) in listOf("history-week-previous" to "Semana anterior", "history-week-next" to "Próxima semana")) {
            progressScroll(tag)
            val node = onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode()
            assertEquals(label, spokenLabel(node))
            assertEquals(Role.Button, node.config.getOrNull(SemanticsProperties.Role))
            assertAtLeast(node, 48f, 48f, label)
        }
        onNodeWithTag("history-week-next").assertIsNotEnabled()
        assertButtonTextsWhole("Weekly in Brazilian Portuguese at 320 dp and 2× text")
        capture("history-weekly-pt-navigation-320-2x")
        progressScroll(hasText("Séries por músculo")); onNodeWithText("Séries por músculo").assertIsDisplayed()
        assertWeeklyMuscle("TRICEPS", "tríceps · 3,5 séries")
        capture("history-weekly-pt-muscles-320-2x")
    }

    @Test fun mostPerformedOpensExerciseProgressAndSettingsCallbackWorks() = runComposeUiTest {
        val calls = show(); progress(); progressScroll("history-frequent-${bench.id}")
        onNodeWithTag("history-frequent-${bench.id}").performClick()
        assertEquals(listOf(bench.id), calls.progress)
        onNodeWithTag("history-settings").performClick(); assertEquals(1, calls.settings)
    }

    @Test fun emptyHistoryOffersTrainAndNoCalendarOrFilters() = runComposeUiTest {
        val calls = show(mutableStateOf(UiState(loading = false)))
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(androidx.compose.ui.test.hasText("Your workouts appear here")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Your workouts appear here").assertIsDisplayed()
        assertFalse(exists("history-calendar")); assertFalse(exists("history-search"))
        onNodeWithText("Go to Train").performClick(); assertEquals(1, calls.train)
    }

    @Test fun noResultsClearActionRestoresHistory() = runComposeUiTest {
        show(); search("no matching workout")
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag("history-count")).fetchSemanticsNodes().isEmpty() &&
            onAllNodes(androidx.compose.ui.test.hasText("No workouts found")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag(HistoryScreenTag).performScrollToNode(androidx.compose.ui.test.hasText("No workouts found"))
        onNodeWithText("No workouts found").assertIsDisplayed()
        historyScroll("history-clear"); onNodeWithTag("history-clear").performScrollTo().performClick(); count("4 workouts")
    }

    @Test fun progressPeriodAndDataTableSurviveRecreation() = runComposeUiTest {
        val restore = screenRestoration(); show(restoration = restore, locale = null); progress()
        onNodeWithTag("history-period-${HistoryPeriod.ONE_YEAR.name}").performScrollTo().performClick()
        awaitTag("history-period-stats")
        val toggle = hasTestTag(ChartDataButtonTag) and hasAnyAncestor(hasTestTag("history-chart-workouts"))
        progressScroll(toggle); onNode(toggle).performClick()
        progressScroll(chartTableRowTag("history-false-11"))
        restore.emulateSaveAndRestore(); awaitTag("history-period-stats")
        progressScroll(chartTableRowTag("history-false-11")); onNodeWithTag(chartTableRowTag("history-false-11")).assertIsDisplayed()
        onNodeWithTag("history-period-${HistoryPeriod.ONE_YEAR.name}").performScrollTo().assertIsSelected()
    }

    @Test fun workoutListRestoresItsNonzeroOffsetAndNewSearchStillReturnsToTheTop() = runComposeUiTest {
        val state = state().also { it.value = it.value.copy(history = (1..40).map { n -> workout("many-$n", 4) }) }
        val restore = screenRestoration()
        show(state, restoration = restore, locale = null)
        count("40 workouts")
        val rowMatcher = SemanticsMatcher("History workout card") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("history-workout-") == true && it.boundsInRoot.height > 0f
        }
        fun visibleRows() = onAllNodes(rowMatcher).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag] }
        onNodeWithTag(HistoryScreenTag).performScrollToIndex(24); waitForIdle()
        val first = visibleRows().first()
        val before = onNodeWithTag(first).getUnclippedBoundsInRoot().top
        assertFalse(exists("history-calendar"), "The saved position must actually be below the calendar")
        restore.emulateSaveAndRestore()
        waitUntil(timeoutMillis = 10_000) { visibleRows().isNotEmpty() }
        waitForIdle()
        assertEquals(first, visibleRows().first())
        assertEquals(before.value, onNodeWithTag(first).getUnclippedBoundsInRoot().top.value, 0.5f)
        search("barbell")
        waitUntil(timeoutMillis = 10_000) {
            if (!exists("history-calendar")) false else {
                val listTop = onNodeWithTag(HistoryScreenTag).getUnclippedBoundsInRoot().top
                abs(onNodeWithTag("history-calendar").getUnclippedBoundsInRoot().top.value - (listTop + 16.dp).value) <= 0.5f
            }
        }
    }

    @Test fun switchingSegmentsRetainsProgressPeriodTableAndListPosition() = runComposeUiTest {
        show(locale = null); progress()
        onNodeWithTag("history-period-${HistoryPeriod.ONE_YEAR.name}").performScrollTo().performClick()
        awaitTag("history-period-stats")
        val toggle = hasTestTag(ChartDataButtonTag) and hasAnyAncestor(hasTestTag("history-chart-workouts"))
        progressScroll(toggle); onNode(toggle).performClick()
        val row = chartTableRowTag("history-false-11")
        progressScroll(row)
        val before = onNodeWithTag(row).getUnclippedBoundsInRoot().top
        onNodeWithTag("history-segment-workouts").performClick(); awaitTag("history-calendar")
        onNodeWithTag("history-segment-progress").performClick(); waitForIdle()
        awaitTag(row)
        onNodeWithTag(row).assertIsDisplayed()
        assertEquals(before.value, onNodeWithTag(row).getUnclippedBoundsInRoot().top.value, 0.5f)
        progressScroll("history-period-${HistoryPeriod.ONE_YEAR.name}")
        onNodeWithTag("history-period-${HistoryPeriod.ONE_YEAR.name}").assertIsSelected()
    }

    @Test fun portugueseNarrowTwoXHistoryAndStatisticsKeepNumbersWhole() = runComposeUiTest {
        show(locale = "pt-BR", font = LINEAR_TWO_X_FONT_SCALE, size = DpSize(320.dp, 640.dp))
        awaitTag(calendarDayTag(3)); count("4 treinos"); assertNumbersWhole(atLeast = 0)
        historyScroll("history-workout-upper-a"); onNodeWithTag("history-workout-upper-a").assertIsDisplayed()
        progress(); progressScroll("history-period-stats"); assertNumbersWhole(atLeast = 4)
    }

    @Test fun englishDarkNarrowTwoXHistoryAndStatisticsKeepNumbersWhole() = runComposeUiTest {
        show(locale = "en", dark = true, font = LINEAR_TWO_X_FONT_SCALE, size = DpSize(320.dp, 640.dp))
        awaitTag(calendarDayTag(3)); count("4 workouts"); assertNumbersWhole(atLeast = 0)
        progress(); progressScroll("history-period-stats"); assertNumbersWhole(atLeast = 4)
    }

    @Test fun failedIndexShowsRetryAndRecoveringBuildLoadsTheSameHistory() = runComposeUiTest {
        val attempts = object { @Volatile var value = 0 }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            attempts.value++
            if (attempts.value == 1) error("Synthetic failure")
            ProgressIndex.build(history, records)
        })
        try {
            show(cache = cache)
            waitUntil(timeoutMillis = 10_000) { onAllNodes(androidx.compose.ui.test.hasText("Could not load history")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("Try again").performClick(); count("4 workouts")
            assertTrue(attempts.value >= 2)
        } finally { scope.cancel() }
    }
}
