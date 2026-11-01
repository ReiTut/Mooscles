package com.mooscles.screens.a11y

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.charts.LineChartTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.illustration.prepareBodyMap
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenFrame
import com.mooscles.screens.assertButtonTextsWhole
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.exercise.ExerciseCues
import com.mooscles.screens.exercise.ExerciseInfoRoute
import com.mooscles.screens.exercise.ExerciseInfoTags
import com.mooscles.screens.isPlaced
import com.mooscles.screens.library.TALL
import com.mooscles.screens.library.TopAnchored
import com.mooscles.screens.progress.ExerciseProgressScreen
import com.mooscles.screens.progress.ExerciseProgressTag
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.progress.ProgressTags
import com.mooscles.screens.records.RecordTimelines
import com.mooscles.screens.records.RecordsQuery
import com.mooscles.screens.records.RecordsScreen
import com.mooscles.screens.records.RecordsScreenTag
import com.mooscles.screens.records.RecordsTags
import com.mooscles.screens.screenReaderOrder
import com.mooscles.screens.spokenLabel
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The accessibility and large text sweep of the phase 5A screens (spec "Acessibilidade", plan Task 15): the Records tab
 * (the timeline, "Current only", a search, no records, loading, the rules sheet), the exercise progress (Total load with
 * records, Added weight, Assistance, one session, the data table open, no session) and the detail's "Your history"
 * card with its sparkline, each in light and dark × at 1.0, 1.3 and 2.0 ([SWEEP_FONT_SCALES]: 2.01 too on Android) ×
 * on 320 and 412 dp wide phones, in Portuguese (the longer texts), and at the top, the middle and the end of its scroll
 * ([Pass]). One test per state: it reaches the state once, then changes the theme, the scale and the width within the
 * same composition ([ScreenFrame] over [Combo] state) and checks each combination: every clickable has a label and a
 * 48 dp target ([assertClickablesLabeledAndSized]), every number is whole ([assertNumbersWhole]; at least as many as
 * the pass shows), no button text is cut ([assertButtonTextsWhole]), every progress chart shown is one image node
 * ([Role.Image]) with a summary ([assertChartsSpeak]; a pass that is for the chart needs one), and, in a window of its
 * own (the rules sheet), the text is laid out at the combination's scale ([assertDrawnAt]). All the failures of a
 * state are reported together. The lists are only scrolled with the test clock running.
 *
 * Plus the screen reader's order over the Records tab ([recordsReadingOrder]: the top, the search, the chips, the
 * month's header, its rows) and over the whole progress screen in a window tall enough that nothing scrolls
 * ([progressReadingOrder]: the order of Task 10).
 */
@OptIn(ExperimentalTestApi::class)
class ProgressAccessibilitySweepTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    // --- The data -------------------------------------------------------------------------------

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 31, 12, 0).toInstant(utc).toEpochMilliseconds()
    private val recordsSeams = ProgressSeams(nowMillis = { now }, zone = { utc })
    private val progressSeams = ProgressSeams(nowMillis = { now }, zone = { utc }, weekStart = { DayOfWeek.MONDAY })

    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime(y, mo, d, 9, 0).toInstant(utc).toEpochMilliseconds()

    private val catalog = ExerciseCatalog.exercises.map { ExerciseUi(it.id, it.name, it.muscle.label, it.equipment.label, it.convention.label) }
    private fun catalogExercise(stableId: String) = catalog.single { it.id == "catalog_$stableId" }

    private val bench = catalogExercise("barbell_bench_flat")
    private val pushUp = catalogExercise("bodyweight_push_up")
    /** Added weight: a load chart only because a session has a load above 0. */
    private val pullUp = catalogExercise("bodyweight_pull_up")
    private val assisted = catalogExercise("selectorized_assisted_pull_up")
    /** One session in all. */
    private val incline = catalogExercise("dumbbell_bench_incline")
    /** In the library, never done. */
    private val frontSquat = catalogExercise("barbell_front_squat")

    /** A workout's sets as given (load kg, reps), each completed. */
    private class Session(val kg: Double, val reps: Int)

    /**
     * One finished workout per session of [exercise], [everyDays] apart from [start], ids `<prefix><i>`; with [records]
     * of [kind], each session after the first beats the one before it (the first is a first record). The last record
     * is current: `UiState.records` holds it without the value it beat, as the presentation gives it.
     */
    private class Series(val workouts: List<WorkoutUi>, val current: RecordUi?)

    private fun series(
        prefix: String,
        exercise: ExerciseUi,
        start: Long,
        everyDays: Int,
        sessions: List<Session>,
        kind: RecordKind? = RecordKind.WEIGHT,
        assistance: Boolean = false,
    ): Series {
        var last: RecordUi? = null
        val workouts = sessions.mapIndexed { i, session ->
            val id = "$prefix$i"
            val startedAt = start + i * everyDays * DAY_MS
            val before = sessions.getOrNull(i - 1)
            val record = kind?.let {
                RecordUi(
                    exercise, session.kg, session.reps,
                    previousKg = if (it == RecordKind.WEIGHT) before?.kg else null,
                    workoutId = id, date = startedAt + HOUR_MS, kind = it,
                    previousReps = if (it == RecordKind.REPS) before?.reps else null,
                )
            }
            if (record != null) last = record
            WorkoutUi(
                id, "Treino $id", startedAt, startedAt + HOUR_MS,
                exercises = listOf(
                    WorkoutExerciseUi("e-$id", exercise, List(2) { j -> SetUi("s-$id-$j", session.reps, session.kg, completed = true, assistance = assistance) }),
                ),
                records = listOfNotNull(record),
            )
        }
        return Series(workouts, last?.copy(previousKg = null, previousReps = null))
    }

    /** Bench: twelve sessions, 12 days apart from June 2, 60 to 87.5 kg × 5, each a load record. */
    private val benchSeries = series("b", bench, at(2026, 6, 2), 12, List(12) { Session(60.0 + 2.5 * it, 5) })
    /** Push-ups: six sessions, 10 to 15 reps, each a reps record. */
    private val pushSeries = series("p", pushUp, at(2026, 7, 3), 15, List(6) { Session(0.0, 10 + it) }, RecordKind.REPS)
    /** Pull-ups with added weight: 5, 7.5 and 10 kg × 6, load records. */
    private val pullSeries = series("u", pullUp, at(2026, 9, 4), 10, listOf(Session(5.0, 6), Session(7.5, 6), Session(10.0, 6)))
    /** Assisted pull-ups: 35, 30 and 25 kg of assistance × 8, no record. */
    private val assistedSeries = series("a", assisted, at(2026, 9, 5), 10, listOf(Session(35.0, 8), Session(30.0, 8), Session(25.0, 8)), kind = null, assistance = true)
    private val inclineSeries = series("i", incline, at(2026, 10, 6), 1, listOf(Session(24.0, 10)), kind = null)

    private val all = listOf(benchSeries, pushSeries, pullSeries, assistedSeries, inclineSeries)
    private val history = all.flatMap { it.workouts }.sortedByDescending { it.startedAt }
    private val current = all.mapNotNull { it.current }

    private fun stateOf(withHistory: Boolean = true, loading: Boolean = false) = UiState(
        loading = loading,
        exercises = catalog,
        history = if (withHistory) history else emptyList(),
        records = if (withHistory) current else emptyList(),
    )

    /** The timeline without a chip or a search, as the tab lists it: each month's header, then its rows. */
    private val timelineTags: List<String> = RecordTimelines.query(ProgressIndex.build(history, current), RecordsQuery(zone = utc)).months.flatMap { month ->
        listOf(RecordsTags.month(month.year, month.month.number)) + month.items.map { RecordsTags.row(RecordTimelines.key(it)) }
    }

    // --- The combinations -----------------------------------------------------------------------

    /** One combination of the sweep: the theme, the font scale and the phone's width. */
    private data class Combo(val dark: Boolean, val fontScale: Float, val width: Dp) {
        override fun toString() = "${if (dark) "dark" else "light"} ${fontScale}x ${width.value.toInt()}dp"
    }

    private val combos: List<Combo> = listOf(false, true).flatMap { dark ->
        SWEEP_FONT_SCALES.flatMap { scale -> listOf(320.dp, 412.dp).map { Combo(dark, scale, it) } }
    }

    /** A phone of [width]: 640 dp tall at 320, 800 at 412. */
    private fun sizeOf(width: Dp) = DpSize(width, if (width < 400.dp) 640.dp else 800.dp)

    /**
     * One look at a state: [scroll] brings part of it into view (null: as it is), and the tags in [required] must then
     * be shown; at least [numbers] number texts; with [chart], a progress chart must be shown; with [badges], a record
     * row's badge must be shown (so [assertBadgesInsideTheirRows] checks one at least).
     */
    private class Pass(
        val name: String,
        val required: List<String> = emptyList(),
        val numbers: Int = 0,
        val chart: Boolean = false,
        val badges: Boolean = false,
        val scroll: (ComposeUiTest.() -> Unit)? = null,
    )

    /**
     * Sets [content] once, in the frame of the current [Combo] (light, 1.0, 412 dp first), lets [reach] bring it to the
     * state, then checks each combination and pass (see the class comment); the text under [scaled] (a window's) is
     * laid out at the combination's scale.
     */
    private fun ComposeUiTest.sweep(
        state: String,
        passes: List<Pass>,
        scaled: String? = null,
        reach: ComposeUiTest.() -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        var combo by mutableStateOf(Combo(dark = false, fontScale = 1f, width = 412.dp))
        setContent {
            ScreenFrame(dark = combo.dark, fontScale = combo.fontScale, locale = "pt-BR", size = sizeOf(combo.width)) { content() }
        }
        waitForIdle()
        reach()
        waitForIdle()
        val failures = mutableListOf<String>()
        var buttonTexts = 0
        for (each in combos) {
            combo = each
            waitForIdle()
            for (pass in passes) {
                val where = "$state, $each, ${pass.name}"
                try {
                    pass.scroll?.invoke(this)
                    waitForIdle()
                    // What this pass is for must be there: a check of nothing passes in the void.
                    for (tag in pass.required) {
                        assertTrue(onAllNodes(hasTestTag(tag) and isPlaced, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "$where: no $tag shown")
                    }
                    assertClickablesLabeledAndSized(where)
                    assertNumbersWhole(atLeast = pass.numbers)
                    buttonTexts += assertButtonTextsWhole(where, except = recordBadge)
                    assertBadgesInsideTheirRows(where, required = pass.badges)
                    assertChartsSpeak(where, required = pass.chart)
                    if (scaled != null) assertDrawnAt(scaled, each.fontScale, where)
                } catch (failure: AssertionError) {
                    val message = failure.message.orEmpty()
                    failures += if (message.startsWith(where)) message else "$where: $message"
                }
            }
        }
        println("SWEEP $state: ${combos.size} combinations x ${passes.size} passes, $buttonTexts button texts measured, ${failures.size} failing")
        assertTrue(failures.isEmpty(), "${failures.size} checks of $state fail:\n" + failures.joinToString("\n"))
    }

    /**
     * A record row's badge: its word ("PR", "Primeiro") is set by the row as the badge's semantics text, for the row's
     * merged node, over `PrBadge`, which clears its own drawn label from the semantics. No layout stands behind that
     * text, so [assertButtonTextsWhole] leaves it out and [assertBadgesInsideTheirRows] checks the drawn badge.
     */
    private val recordBadge = hasTestTag(RecordsTags.BADGE) or hasTestTag(RecordsTags.BADGE_FIRST)

    /**
     * Every placed badge has a size and lies whole inside its row (the row's first line wraps rather than cut it),
     * unclipped; with [required], there is one at least (a pass over record rows that found none checked nothing).
     */
    private fun ComposeUiTest.assertBadgesInsideTheirRows(where: String, required: Boolean) {
        val badges = onAllNodes(recordBadge and isPlaced, useUnmergedTree = true).fetchSemanticsNodes()
        if (required) assertTrue(badges.isNotEmpty(), "$where: no record badge shown")
        for (badge in badges) {
            var row = badge.parent
            while (row != null && row.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordsTags.ROW_PREFIX) != true) row = row.parent
            assertTrue(row != null, "$where: a badge outside a row")
            // Unclipped (a row at the list's edge is clipped by the viewport, not cut).
            val inner = Rect(badge.positionInRoot, badge.size.toSize())
            val outer = Rect(row.positionInRoot, row.size.toSize())
            assertTrue(inner.width > 0f && inner.height > 0f, "$where: a badge of no size in ${row.config[SemanticsProperties.TestTag]}")
            assertTrue(
                inner.left >= outer.left - SLACK_PX && inner.right <= outer.right + SLACK_PX && inner.top >= outer.top - SLACK_PX && inner.bottom <= outer.bottom + SLACK_PX,
                "$where: the badge $inner is not inside its row $outer (${row.config[SemanticsProperties.TestTag]})",
            )
        }
    }

    /** Every progress chart shown is one image node with a summary; with [required], there is one. */
    private fun ComposeUiTest.assertChartsSpeak(where: String, required: Boolean) {
        val charts = onAllNodes(hasTestTag(LineChartTag) and isPlaced).fetchSemanticsNodes()
        if (required) assertTrue(charts.isNotEmpty(), "$where: no chart shown")
        for (chart in charts) {
            assertEquals(Role.Image, chart.config.getOrNull(SemanticsProperties.Role), "$where: the chart's role")
            val summary = chart.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("")
            assertTrue(summary.isNotBlank(), "$where: the chart has no summary")
        }
    }

    /** A text under [tag] (or [tag]'s own) is laid out at [fontScale]: the combination reaches its window. */
    private fun ComposeUiTest.assertDrawnAt(tag: String, fontScale: Float, where: String) {
        val text = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val node = onAllNodes((hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and text, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        assertTrue(node != null, "$where: no text under $tag")
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "$where: $tag has no text layout")
        val drawn = results.first().layoutInput.density.fontScale
        assertTrue(abs(drawn - fontScale) < 0.001f, "$where: the text under $tag is laid out at ${drawn}x")
    }

    private fun ComposeUiTest.exists(tag: String) = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.await(what: String, condition: () -> Boolean) = waitUntil(what, timeoutMillis = WAIT_MS, condition = condition)

    // --- The Records tab ------------------------------------------------------------------------

    @Composable
    private fun Records(state: UiState) {
        RecordsScreen(state, RecordingActions(), onOpenProgress = { _, _ -> }, onOpenWorkout = {}, onOpenSettings = {}, seams = recordsSeams)
    }

    private fun ComposeUiTest.awaitRows() = await("the timeline is built") { onAllNodes(rowMatcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private val rowMatcher = SemanticsMatcher("a record row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordsTags.ROW_PREFIX) == true
    }

    /** The list at its [index]th item (the month headers are items), which must then be shown. */
    private fun listAt(name: String, index: Int, tag: String) = Pass(name, listOf(tag), badges = true) {
        onNodeWithTag(RecordsScreenTag).performScrollToIndex(index)
    }

    /** The first, the middle and the last row of the timeline, from [tags] (the headers and the rows, in order). */
    private fun timelinePasses(tags: List<String>): List<Pass> {
        val rows = tags.indices.filter { tags[it].startsWith(RecordsTags.ROW_PREFIX) }
        val middle = rows[rows.size / 2]
        return listOf(
            Pass("top", listOf(RecordsTags.SEARCH, RecordsTags.CURRENT_ONLY, tags[rows.first()]), badges = true) { onNodeWithTag(RecordsScreenTag).performScrollToIndex(0) },
            listAt("middle", middle, tags[middle]),
            listAt("end", rows.last(), tags[rows.last()]),
        )
    }

    @Test
    fun recordsTimeline() = runComposeUiTest(testTimeout = 8.minutes) {
        assertTrue(timelineTags.size > 20, "the timeline is long enough to scroll: ${timelineTags.size} items")
        sweep("records timeline", timelinePasses(timelineTags), reach = { awaitRows() }) { Records(stateOf()) }
    }

    /** "Current only": the records that still hold, first ones included. */
    @Test
    fun recordsCurrentOnly() = runComposeUiTest(testTimeout = 5.minutes) {
        val rows = current.map { RecordsTags.row(RecordTimelines.key(RecordEvent(it, "", 0, current = true))) }
        sweep(
            "records current only",
            listOf(
                Pass("top", listOf(RecordsTags.CURRENT_ONLY), badges = true) { onNodeWithTag(RecordsScreenTag).performScrollToIndex(0) },
                Pass("end", badges = true) { onNodeWithTag(RecordsScreenTag).performScrollToNode(hasTestTag(rows.last())) },
            ),
            reach = {
                awaitRows()
                onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
                waitForIdle()
                await("only the current records are listed") { onAllNodes(rowMatcher, useUnmergedTree = true).fetchSemanticsNodes().size <= current.size }
            },
        ) { Records(stateOf()) }
    }

    /** "supino": the bench's records, its current one on top. */
    @Test
    fun recordsWithSearch() = runComposeUiTest(testTimeout = 5.minutes) {
        val benchRows = timelineTags.filter { it.startsWith(RecordsTags.ROW_PREFIX) && "|${bench.id}|" in it }
        sweep(
            "records search",
            listOf(
                Pass("top", listOf(benchRows.first()), badges = true) { onNodeWithTag(RecordsScreenTag).performScrollToIndex(0) },
                Pass("middle", listOf(benchRows[benchRows.size / 2]), badges = true) {
                    onNodeWithTag(RecordsScreenTag).performScrollToNode(hasTestTag(benchRows[benchRows.size / 2]))
                },
                Pass("end", listOf(benchRows.last()), badges = true) { onNodeWithTag(RecordsScreenTag).performScrollToNode(hasTestTag(benchRows.last())) },
            ),
            reach = {
                awaitRows()
                onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(RecordsTags.SEARCH))).performTextReplacement("supino")
                waitForIdle()
                await("only the bench is listed") {
                    val tags = onAllNodes(rowMatcher, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
                    tags.isNotEmpty() && tags.all { "|${bench.id}|" in it }
                }
            },
        ) { Records(stateOf()) }
    }

    @Test
    fun recordsEmpty() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("records empty", listOf(Pass("as shown", listOf(RecordsTags.EMPTY))), reach = { await("the empty state shows") { exists(RecordsTags.EMPTY) } }) {
            Records(stateOf(withHistory = false))
        }
    }

    @Test
    fun recordsLoading() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("records loading", listOf(Pass("as shown", listOf(RecordsTags.SKELETON, RecordsTags.SEARCH)))) { Records(stateOf(loading = true)) }
    }

    @Test
    fun recordsRulesSheet() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "records rules", scaled = RecordsTags.RULES_SHEET,
            reach = {
                awaitRows()
                onNodeWithTag(RecordsTags.RULES).performClick()
                await("the sheet opens") { exists(RecordsTags.RULES_SHEET) }
            },
            passes = listOf(
                Pass("top", listOf(RecordsTags.rule(0))),
                Pass("end", listOf(RecordsTags.rule(3))) { onNodeWithTag(RecordsTags.rule(3)).performScrollTo() },
            ),
        ) { Records(stateOf()) }
    }

    // --- The exercise progress ------------------------------------------------------------------

    @Composable
    private fun Progress(exercise: ExerciseUi, state: UiState = stateOf()) {
        ExerciseProgressScreen(
            exerciseId = exercise.id,
            metric = null,
            state = state,
            actions = RecordingActions(),
            onBack = {},
            onAbout = {},
            onOpenWorkout = {},
            seams = progressSeams,
        )
    }

    private fun ComposeUiTest.listTo(tag: String) {
        onNodeWithTag(ExerciseProgressTag).performScrollToNode(hasTestTag(tag))
    }

    private fun ComposeUiTest.awaitTag(tag: String) = await("$tag shows") {
        runCatching { listTo(tag) }.isSuccess && exists(tag)
    }

    private val progressTop: ComposeUiTest.() -> Unit = { onNodeWithTag(ExerciseProgressTag).performScrollToIndex(0) }

    /**
     * The top (the name and, with a chart, the highlight), the chart, the middle (a session between the newest and the
     * oldest) and the end (the oldest session).
     */
    private fun progressPasses(middle: String, oldest: String, topNumbers: Int, vararg top: String) = listOf(
        Pass("top", listOf(ProgressTags.TITLE) + top, numbers = topNumbers, scroll = progressTop),
        Pass("chart", listOf(ProgressTags.CHART), chart = true) { listTo(ProgressTags.CHART) },
        Pass("middle", listOf(ProgressTags.session(middle)), numbers = 1) { listTo(ProgressTags.session(middle)) },
        Pass("end", listOf(ProgressTags.session(oldest)), numbers = 1) { listTo(ProgressTags.session(oldest)) },
    )

    /** Bench, on the load: the highlight, the chart with its record marks, the records and the sessions. */
    @Test
    fun progressTotalLoadWithRecords() = runComposeUiTest(testTimeout = 8.minutes) {
        val passes = listOf(
            Pass("top", listOf(ProgressTags.TITLE, ProgressTags.HIGHLIGHT_VALUE), numbers = 1, scroll = progressTop),
            Pass("chart", listOf(ProgressTags.CHART), chart = true) { listTo(ProgressTags.CHART) },
            Pass("legend", listOf(ProgressTags.LEGEND_RECORD)) { listTo(ProgressTags.LEGEND_RECORD) },
            Pass("records", listOf(ProgressTags.RECORD_WEIGHT)) { listTo(ProgressTags.RECORD_WEIGHT) },
            Pass("end", listOf(ProgressTags.session("b0")), numbers = 1) { listTo(ProgressTags.session("b0")) },
        )
        sweep("progress total load", passes, reach = { awaitTag(LineChartTag) }) { Progress(bench) }
    }

    /** Pull-ups with added weight: reps first, then the added weight and the volume. */
    @Test
    fun progressAddedWeight() = runComposeUiTest(testTimeout = 8.minutes) {
        sweep("progress added weight", progressPasses("u1", "u0", 0, ProgressTags.CONVENTION_CAPTION), reach = { awaitTag(LineChartTag) }) { Progress(pullUp) }
    }

    /** Assisted pull-ups: reps and assistance, with what less assistance means. */
    @Test
    fun progressAssistance() = runComposeUiTest(testTimeout = 8.minutes) {
        sweep("progress assistance", progressPasses("a1", "a0", 0, ProgressTags.ASSISTANCE_LEGEND), reach = { awaitTag(LineChartTag) }) { Progress(assisted) }
    }

    /** One session in all: the sentence in the chart's place, then the session. */
    @Test
    fun progressOneSession() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep(
            "progress one session",
            listOf(
                Pass("top", listOf(ProgressTags.TITLE), scroll = progressTop),
                // At 2x on a 320 dp phone the name, the metrics and the highlight fill the first screen.
                Pass("message", listOf(ProgressTags.CHART_MESSAGE)) { listTo(ProgressTags.CHART_MESSAGE) },
                Pass("end", listOf(ProgressTags.session("i0")), numbers = 1) { listTo(ProgressTags.session("i0")) },
            ),
            reach = { awaitTag(ProgressTags.CHART_MESSAGE) },
        ) { Progress(incline) }
    }

    /** "View data" on the bench: its twelve rows, newest first, in place of the chart. */
    @Test
    fun progressTableOpen() = runComposeUiTest(testTimeout = 8.minutes) {
        val rows = benchSeries.workouts.size
        sweep(
            "progress table",
            listOf(
                Pass("top", listOf(ProgressTags.TITLE), numbers = 1, scroll = progressTop),
                Pass("middle", listOf(ProgressTags.tableRow(rows / 2)), numbers = 1) { listTo(ProgressTags.tableRow(rows / 2)) },
                Pass("end", listOf(ProgressTags.tableRow(rows - 1)), numbers = 1) { listTo(ProgressTags.tableRow(rows - 1)) },
            ),
            reach = {
                awaitTag(ProgressTags.DATA_TOGGLE)
                onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
                waitForIdle()
                awaitTag(ProgressTags.tableRow(0))
                assertTrue(!exists(LineChartTag), "the table replaces the chart")
            },
        ) { Progress(bench) }
    }

    /** In the library, never done: "You haven't done this exercise yet." with "View in the library". */
    @Test
    fun progressEmpty() = runComposeUiTest(testTimeout = 5.minutes) {
        sweep("progress empty", listOf(Pass("as shown", listOf(ProgressTags.TITLE, ProgressTags.EMPTY))), reach = { awaitTag(ProgressTags.EMPTY) }) {
            Progress(frontSquat)
        }
    }

    // --- "Your history" with the sparkline ------------------------------------------------------

    /** The cues and the body map in memory, as after the library opened: both draw in their first frame. */
    private fun preload() = runBlocking {
        ExerciseCues.load(portuguese = true)
        prepareBodyMap()
    }

    /**
     * The bench's detail at the top (its name and figure), in the middle (the muscles, between the top and the card), at
     * its history card (the sparkline and "View progress") and at its end.
     */
    @Test
    fun historyCardWithSparkline() = runComposeUiTest(testTimeout = 5.minutes) {
        preload()
        sweep(
            "history card",
            listOf(
                Pass("top", listOf(ExerciseInfoTags.TITLE, ExerciseInfoTags.MEDIA)) {
                    onNodeWithTag(ExerciseInfoTags.TITLE, useUnmergedTree = true).performScrollTo()
                },
                Pass("middle", listOf(ExerciseInfoTags.MUSCLES)) { onNodeWithTag(ExerciseInfoTags.MUSCLES, useUnmergedTree = true).performScrollTo() },
                Pass("card", listOf(ExerciseInfoTags.HISTORY, ExerciseInfoTags.HISTORY_SPARKLINE, ExerciseInfoTags.HISTORY_FULL), numbers = 1) {
                    onNodeWithTag(ExerciseInfoTags.HISTORY_FULL, useUnmergedTree = true).performScrollTo()
                },
                Pass("end", listOf(ExerciseInfoTags.ADD_ROUTINE), numbers = 1) { onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE, useUnmergedTree = true).performScrollTo() },
            ),
            reach = { await("the sparkline shows") { exists(ExerciseInfoTags.HISTORY_SPARKLINE) } },
        ) {
            ExerciseInfoRoute(bench.id, stateOf(), RecordingActions(), onBack = {}, onOpenHistory = {}, onOpenRoutine = {}, onOpenWorkout = {})
        }
    }

    // --- The screen reader ----------------------------------------------------------------------

    /** The region of [node], by the first of its tags (or its ancestors') that [regions] maps, else by [byLabel]. */
    private fun regionOf(node: SemanticsNode, regions: (String) -> String?, byLabel: (String) -> String? = { null }): String? {
        var current: SemanticsNode? = node
        while (current != null) {
            current.config.getOrNull(SemanticsProperties.TestTag)?.let { tag -> regions(tag)?.let { return it } }
            current = current.parent
        }
        return byLabel(spokenLabel(node))
    }

    /** The regions [order] goes through, each once in a row; fails on a node of no region. */
    private fun sequenceOf(order: List<SemanticsNode>, region: (SemanticsNode) -> String?): List<String> {
        val read = order.joinToString("\n") { "${region(it)} '${spokenLabel(it)}' ${it.boundsInRoot}" }
        val regions = order.map { node ->
            val found = region(node)
            assertTrue(found != null, "a node of no region: '${spokenLabel(node)}' ${node.config}\n$read")
            found
        }
        return regions.fold(mutableListOf()) { acc, it -> acc.apply { if (lastOrNull() != it) add(it) } }
    }

    /**
     * The Records tab's order ([screenReaderOrder]) on a 412 dp phone: the top bar ("Recordes", Settings, the rules),
     * the search, the chips, then the timeline: a month's header before its rows.
     */
    @Test
    fun recordsReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        setContent { ScreenFrame(locale = "pt-BR", size = sizeOf(412.dp)) { Records(stateOf()) } }
        waitForIdle()
        awaitRows()
        waitForIdle()
        val regions: (String) -> String? = { tag ->
            when {
                tag == RecordsTags.SETTINGS || tag == RecordsTags.RULES -> "top"
                tag == RecordsTags.SEARCH -> "search"
                tag == RecordsTags.CURRENT_ONLY || tag == RecordsTags.FIRST -> "chips"
                tag.startsWith(RecordsTags.MONTH_PREFIX) -> "month"
                tag.startsWith(RecordsTags.ROW_PREFIX) -> "rows"
                else -> null
            }
        }
        val order = screenReaderOrder().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        val sequence = sequenceOf(order) { regionOf(it, regions) { label -> if (label == "Recordes") "top" else null } }
        assertEquals(listOf("top", "search", "chips", "month", "rows"), sequence.take(5), "the screen reader's order: $sequence")
        assertTrue(sequence.drop(3).all { it == "month" || it == "rows" }, "the timeline is only headers and rows: $sequence")
        // The first month's header is the newest record's month, and its first row is the newest record.
        val first = order.first { regionOf(it, regions) == "rows" }.config[SemanticsProperties.TestTag]
        assertEquals(timelineTags.first { it.startsWith(RecordsTags.ROW_PREFIX) }, first)
    }

    /**
     * The progress screen's whole order ([screenReaderOrder]), in a window tall enough that nothing scrolls: the bar,
     * the name, the convention, the metrics, the highlight, the periods, the chart, "View data", the records and the
     * sessions (the order of Task 10).
     */
    @Test
    fun progressReadingOrder() = runComposeUiTest(testTimeout = 2.minutes) {
        setContent { ScreenFrame(locale = "pt-BR") { TopAnchored(TALL) { Progress(bench) } } }
        waitForIdle()
        await("the chart and the sessions show") { exists(LineChartTag) && exists(ProgressTags.session("b0")) }
        waitForIdle()
        val name = ExerciseNames.display(bench.id, bench.name, custom = false, portuguese = true)
        fun region(node: SemanticsNode): String? {
            var current: SemanticsNode? = node
            while (current != null) {
                val tag = current.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
                when {
                    tag == ProgressTags.MENU -> return "bar"
                    tag == ProgressTags.TITLE || tag == ProgressTags.META -> return "name"
                    tag.startsWith("progress-metric-") -> return "metric"
                    tag == ProgressTags.HIGHLIGHT -> return "highlight"
                    tag.startsWith("progress-period-") -> return "period"
                    tag == ProgressTags.CHART || tag == LineChartTag || tag == ProgressTags.LEGEND_RECORD -> return "chart"
                    tag == ProgressTags.DATA_TOGGLE -> return "data"
                    tag.startsWith("progress-record") -> return "records"
                    tag.startsWith("progress-session") -> return "sessions"
                }
                current = current.parent
            }
            val label = spokenLabel(node)
            return when {
                label == "Voltar" -> "bar"
                label.startsWith("Carga total") -> "convention"
                else -> null
            }
        }
        val order = screenReaderOrder().filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        val sequence = sequenceOf(order, ::region)
        assertEquals(
            listOf("bar", "name", "convention", "metric", "highlight", "period", "chart", "data", "records", "sessions"),
            sequence,
            "the screen reader's order",
        )
        assertEquals(name, spokenLabel(order.first { region(it) == "name" }))
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
        const val WAIT_MS = 10_000L
        const val SLACK_PX = 1f
    }
}
