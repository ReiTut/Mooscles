package com.mooscles.screens.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.charts.LineChartTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The top of the exercise progress screen (spec "Progresso do exercício"): name, metrics, highlight, period and chart, in pt-BR. */
@OptIn(ExperimentalTestApi::class)
class ExerciseProgressScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 31, 12, 0).toInstant(utc).toEpochMilliseconds()
    private val seams = ProgressSeams(nowMillis = { now }, zone = { utc }, weekStart = { DayOfWeek.MONDAY })

    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime(y, mo, d, 9, 0).toInstant(utc).toEpochMilliseconds()

    /** The app writes the unit after a no-break space. */
    private fun String.nb() = replace(" kg", " kg").replace(" lb", " lb")

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private val bench = catalog("barbell_bench_flat")
    private val pushUp = catalog("bodyweight_push_up")
    private val pullUp = catalog("bodyweight_pull_up")
    private val assisted = catalog("selectorized_assisted_pull_up")
    private val exercises = listOf(bench, pushUp, pullUp, assisted)

    /** A finished workout of one exercise, every set completed: (load kg, reps). */
    private fun workout(id: String, startedAt: Long, exercise: ExerciseUi, vararg sets: Pair<Double, Int>, records: List<RecordUi> = emptyList()) =
        WorkoutUi(
            id, "Treino $id", startedAt, startedAt + 3_600_000,
            exercises = listOf(
                WorkoutExerciseUi(
                    "e-$id", exercise,
                    sets.mapIndexed { i, (kg, reps) -> SetUi("s-$id-$i", reps, kg, completed = true, assistance = exercise == assisted) },
                ),
            ),
            records = records,
        )

    /** Bench: 80 × 5 on Jun 3, 95 × 3 on Sep 12 (the beaten load record when [record]), 92.5 × 5 on Oct 28. */
    private fun benchHistory(record: Boolean = false) = listOf(
        workout("w3", at(2026, 10, 28), bench, 92.5 to 5),
        workout(
            "w2", at(2026, 9, 12), bench, 95.0 to 3,
            records = if (record) listOf(RecordUi(bench, 95.0, 3, previousKg = 80.0, workoutId = "w2", date = at(2026, 9, 12) + 3_600_000)) else emptyList(),
        ),
        workout("w1", at(2026, 6, 3), bench, 80.0 to 5),
    )

    private fun stateOf(history: List<WorkoutUi>, pounds: Boolean = false) =
        UiState(loading = false, exercises = exercises, history = history, preferences = PreferencesUi(pounds = pounds))

    private fun ComposeUiTest.show(
        state: MutableState<UiState>,
        exerciseId: MutableState<String> = mutableStateOf(bench.id),
        metric: String? = null,
        fontScale: Float = 1f,
        size: DpSize? = null,
        restoration: ScreenRestoration? = null,
        cache: ProgressIndexCache? = null,
        locale: String? = "pt-BR",
    ) {
        withScreen(locale = locale, reducedMotion = true, fontScale = fontScale, size = size, restoration = restoration) {
            CompositionLocalProvider(LocalProgressIndexCache provides cache) {
                ExerciseProgressScreen(
                    exerciseId = exerciseId.value,
                    metric = metric,
                    state = state.value,
                    actions = RecordingActions(),
                    onBack = {},
                    onAbout = {},
                    onOpenWorkout = {},
                    seams = seams,
                )
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The node of [tag] in the unmerged tree: the parts of a merged node (the highlight, the convention). */
    private fun ComposeUiTest.part(tag: String) = onNodeWithTag(tag, useUnmergedTree = true)

    /** Waits until the list can scroll to [tag]: an item that comes once the chart is ready. */
    private fun ComposeUiTest.waitAndScrollTo(tag: String) {
        waitUntil(timeoutMillis = 5_000) { runCatching { scrollTo(tag) }.isSuccess }
    }

    private fun ComposeUiTest.chartReady() = waitForTag(LineChartTag)

    private fun ComposeUiTest.summary(): String =
        onNodeWithTag(LineChartTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("")

    private fun ComposeUiTest.scrollTo(tag: String) {
        onNodeWithTag(ExerciseProgressTag).performScrollToNode(hasTestTag(tag))
    }

    private fun ComposeUiTest.assertGone(tag: String) {
        assertEquals(0, onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size, "$tag should not be shown")
    }

    @Test
    fun nameIsTheFirstContentLineWithMuscleAndEquipment() = runComposeUiTest {
        show(mutableStateOf(stateOf(benchHistory())))
        chartReady()
        val name = ExerciseNames.display(bench.id, bench.name, custom = false, portuguese = true)
        assertEquals("Supino reto com barra", name)
        onNodeWithTag(ProgressTags.TITLE).assertTextEquals(name)
        onNodeWithTag(ProgressTags.META).assertTextEquals("Peito · Barra")
        val list = onNodeWithTag(ExerciseProgressTag).getUnclippedBoundsInRoot()
        val title = onNodeWithTag(ProgressTags.TITLE).getUnclippedBoundsInRoot()
        val meta = onNodeWithTag(ProgressTags.META).getUnclippedBoundsInRoot()
        val convention = part(ProgressTags.CONVENTION).getUnclippedBoundsInRoot()
        assertTrue(title.top - list.top < 16.dp, "the name is the list's first line: ${title.top} vs ${list.top}")
        assertTrue(title.bottom <= meta.top && meta.bottom <= convention.top, "name, then muscle and equipment, then the convention")
    }

    @Test
    fun metricsFollowTheConvention() = runComposeUiTest {
        val history = listOf(
            workout("p1", at(2026, 10, 1), pushUp, 0.0 to 12, 0.0 to 10),
            workout("p2", at(2026, 10, 8), pushUp, 0.0 to 14),
            workout("u1", at(2026, 10, 2), pullUp, 0.0 to 8),
            workout("u2", at(2026, 10, 9), pullUp, 10.0 to 6),
            workout("a1", at(2026, 10, 3), assisted, 30.0 to 8),
            workout("a2", at(2026, 10, 10), assisted, 25.0 to 6),
        ) + benchHistory()
        val id = mutableStateOf(bench.id)
        show(mutableStateOf(stateOf(history)), exerciseId = id)

        fun expect(metrics: Map<ProgressMetric, String>, convention: String) {
            waitForTag(ProgressTags.metric(metrics.keys.first()))
            for (metric in ProgressMetric.entries) {
                val label = metrics[metric]
                if (label == null) assertGone(ProgressTags.metric(metric))
                else onNodeWithTag(ProgressTags.metric(metric)).assertTextContains(label)
            }
            onNodeWithTag(ProgressTags.metric(metrics.keys.first())).assertIsSelected()
            part(ProgressTags.CONVENTION).assertTextEquals(convention)
        }

        expect(mapOf(ProgressMetric.LOAD to "Carga", ProgressMetric.E1RM to "1RM estimado", ProgressMetric.VOLUME to "Volume"), "Carga total")
        assertGone(ProgressTags.CONVENTION_CAPTION)
        assertGone(ProgressTags.ASSISTANCE_LEGEND)

        id.value = pushUp.id
        waitForIdle()
        expect(mapOf(ProgressMetric.REPS to "Repetições"), "Peso adicional")
        part(ProgressTags.CONVENTION_CAPTION).assertTextEquals("Peso adicional: deixe 0 para só o peso do corpo.")

        id.value = pullUp.id
        waitForIdle()
        expect(
            mapOf(ProgressMetric.REPS to "Repetições", ProgressMetric.LOAD to "Peso adicional", ProgressMetric.VOLUME to "Volume"),
            "Peso adicional",
        )

        id.value = assisted.id
        waitForIdle()
        expect(mapOf(ProgressMetric.REPS to "Repetições", ProgressMetric.LOAD to "Assistência"), "Assistência")
        part(ProgressTags.CONVENTION_CAPTION).assertTextEquals("Assistência: registre a assistência da máquina.")
        part(ProgressTags.ASSISTANCE_LEGEND).assertTextEquals("Menos assistência, menos ajuda da máquina.")
    }

    @Test
    fun metricArgumentOpensThatMetricAndAMissingOneTheFirst() {
        val history = benchHistory() + listOf(workout("u1", at(2026, 10, 2), pullUp, 10.0 to 6), workout("u2", at(2026, 10, 9), pullUp, 12.5 to 5))
        val cases = listOf(
            Triple(bench.id, "e1rm", ProgressMetric.E1RM),
            Triple(pullUp.id, "e1rm", ProgressMetric.REPS),
            Triple(bench.id, null, ProgressMetric.LOAD),
            Triple(bench.id, "E1RM", ProgressMetric.LOAD),
        )
        for ((id, argument, expected) in cases) {
            runComposeUiTest {
                show(mutableStateOf(stateOf(history)), exerciseId = mutableStateOf(id), metric = argument)
                waitForTag(ProgressTags.metric(expected))
                onNodeWithTag(ProgressTags.metric(expected)).assertIsSelected()
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun highlightShowsTheLastValueAndTheDifferenceAsAFact() {
        runComposeUiTest {
            show(mutableStateOf(stateOf(benchHistory())))
            waitForTag(ProgressTags.HIGHLIGHT_VALUE)
            part(ProgressTags.HIGHLIGHT_VALUE).assertTextEquals("92,5 kg".nb())
            part(ProgressTags.SINCE).assertTextEquals("+12,5 kg desde 3\u00A0jun".nb())
            // One node: the value, the metric and the change.
            onNodeWithTag(ProgressTags.HIGHLIGHT).assertTextEquals("92,5 kg".nb(), "Carga", "+12,5 kg desde 3\u00A0jun".nb())
        }
        LocalAppLocale.restore()
        // A drop is said the same way, with the minus sign and no other mark.
        val lower = benchHistory().toMutableList().also { it[0] = workout("w3", at(2026, 10, 28), bench, 75.0 to 5) }
        runComposeUiTest {
            show(mutableStateOf(stateOf(lower)))
            waitForTag(ProgressTags.SINCE)
            part(ProgressTags.SINCE).assertTextEquals("\u22125 kg desde 3\u00A0jun".nb())
        }
    }

    /** Critique of phase 5A: "12 repetições" already names the metric, so the highlight does not add "Repetições". */
    @Test
    fun repsHighlightDoesNotRepeatTheMetric() = runComposeUiTest {
        val history = listOf(
            workout("p2", at(2026, 10, 10), pullUp, 0.0 to 12),
            workout("p1", at(2026, 9, 3), pullUp, 0.0 to 8),
        )
        show(mutableStateOf(stateOf(history)), exerciseId = mutableStateOf(pullUp.id))
        waitForTag(ProgressTags.SINCE)
        onNodeWithTag(ProgressTags.HIGHLIGHT).assertTextEquals("12 repetições", "+4 repetições desde 3 set")
    }

    @Test
    fun allIsTheDefaultPeriodAndChipsChangeThePointsAndClearTheSelection() = runComposeUiTest {
        show(mutableStateOf(stateOf(benchHistory())))
        chartReady()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.ALL)).assertIsSelected()
        for (period in ExercisePeriod.entries - ExercisePeriod.ALL) onNodeWithTag(ProgressTags.period(period)).assertIsNotSelected()
        assertTrue(summary().startsWith("Carga, todo o período:") && summary().endsWith("3 treinos."), summary())
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 1f, height / 2f)) }
        waitForTag(ProgressTags.READOUT)

        onNodeWithTag(ProgressTags.period(ExercisePeriod.THREE_MONTHS)).performClick()
        waitUntil(timeoutMillis = 5_000) { summary().contains("últimos 3 meses") }
        onNodeWithTag(ProgressTags.period(ExercisePeriod.THREE_MONTHS)).assertIsSelected()
        assertEquals(
            "Carga, últimos 3 meses: de 95 quilos em 12 de setembro a 92,5 quilos em 28 de outubro; " +
                "máximo 95 quilos em 12 de setembro; 2 treinos.",
            summary(),
        )
        assertGone(ProgressTags.READOUT)
        part(ProgressTags.HIGHLIGHT_VALUE).assertTextEquals("92,5 kg".nb())
        part(ProgressTags.SINCE).assertTextEquals("−2,5 kg desde 12\u00A0set".nb())
    }

    @Test
    fun chartIsOneImageNodeWithTheSpokenSummary() = runComposeUiTest {
        show(mutableStateOf(stateOf(benchHistory())))
        chartReady()
        val node = onNodeWithTag(LineChartTag).fetchSemanticsNode()
        assertEquals(Role.Image, node.config[SemanticsProperties.Role])
        assertEquals(
            "Carga, todo o período: de 80 quilos em 3 de junho a 92,5 quilos em 28 de outubro; " +
                "máximo 95 quilos em 12 de setembro; tendência em alta; 3 treinos.",
            summary(),
        )
        // Nothing else inside the chart speaks: one node, the image.
        val speaking = onAllNodes(hasAnyAncestor(hasTestTag(ProgressTags.CHART)), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.config.contains(SemanticsProperties.Text) || it.config.contains(SemanticsProperties.ContentDescription) }
        assertEquals(listOf(LineChartTag), speaking.map { it.config[SemanticsProperties.TestTag] })
    }

    @Test
    fun viewDataSwapsToTheTableNewestFirstAndBack() = runComposeUiTest {
        show(mutableStateOf(stateOf(benchHistory())))
        chartReady()
        scrollTo(ProgressTags.DATA_TOGGLE)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).assertTextContains("Ver dados").performClick()
        waitForTag(ProgressTags.tableRow(0))
        assertGone(LineChartTag)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).assertTextContains("Ver gráfico")
        val expected = listOf("28\u00A0out" to "92,5 kg × 5", "12\u00A0set" to "95 kg × 3", "3\u00A0jun" to "80 kg × 5")
        for ((i, row) in expected.withIndex()) {
            scrollTo(ProgressTags.tableRow(i))
            onNodeWithTag(ProgressTags.tableRow(i)).assertTextContains(row.first).assertTextContains(row.second.nb())
        }
        scrollTo(ProgressTags.DATA_TOGGLE)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
        chartReady()
        assertGone(ProgressTags.tableRow(0))
        onNodeWithTag(ProgressTags.DATA_TOGGLE).assertTextContains("Ver dados")
    }

    @Test
    fun selectingAPointShowsTheReadout() = runComposeUiTest {
        show(mutableStateOf(stateOf(benchHistory())))
        chartReady()
        assertGone(ProgressTags.READOUT)
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 1f, height / 2f)) }
        waitForTag(ProgressTags.READOUT)
        onNodeWithTag(ProgressTags.READOUT).assertTextEquals("28\u00A0out · 92,5 kg · 92,5 kg × 5".nb())
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(1f, height / 2f)) }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag(ProgressTags.READOUT).fetchSemanticsNodes().singleOrNull()
                ?.config?.get(SemanticsProperties.Text)?.joinToString("") { it.text } == "3\u00A0jun · 80 kg · 80 kg × 5".nb()
        }
    }

    @Test
    fun recordLegendOnlyWithAMark() = runComposeUiTest {
        val state = mutableStateOf(stateOf(benchHistory(record = false)))
        show(state)
        chartReady()
        assertGone(ProgressTags.LEGEND_RECORD)
        state.value = stateOf(benchHistory(record = true))
        waitForTag(ProgressTags.LEGEND_RECORD)
        onNodeWithTag(ProgressTags.LEGEND_RECORD).assertTextContains("Recorde")
        // The 1RM never carries a record (D6).
        onNodeWithTag(ProgressTags.metric(ProgressMetric.E1RM)).performClick()
        waitUntil(timeoutMillis = 5_000) { summary().startsWith("1RM estimado") }
        assertGone(ProgressTags.LEGEND_RECORD)
    }

    @Test
    fun estimateIsRoundedToHalfAKiloOrOnePound() = runComposeUiTest {
        // 100 × 5 is 116.67 kg by Epley: 116.5 kg, or 257.2 lb: 257 lb.
        val history = listOf(workout("w2", at(2026, 10, 28), bench, 100.0 to 5), workout("w1", at(2026, 10, 1), bench, 90.0 to 5))
        val state = mutableStateOf(stateOf(history))
        show(state, metric = "e1rm")
        waitForTag(ProgressTags.HIGHLIGHT_VALUE)
        part(ProgressTags.HIGHLIGHT_VALUE).assertTextEquals("116,5 kg".nb())
        state.value = stateOf(history, pounds = true)
        waitUntil(timeoutMillis = 5_000) {
            part(ProgressTags.HIGHLIGHT_VALUE).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text } == "257 lb".nb()
        }
    }

    @Test
    fun poundsConvertValuesAndTicksAreRound() = runComposeUiTest {
        val history = listOf(
            workout("w3", at(2026, 10, 28), bench, 100.0 to 5),
            workout("w2", at(2026, 9, 12), bench, 90.0 to 5),
            workout("w1", at(2026, 6, 3), bench, 80.0 to 5),
        )
        val state = mutableStateOf(stateOf(history))
        show(state)
        chartReady()
        fun ticks() = onNodeWithTag(ProgressTags.CHART).fetchSemanticsNode().config[ProgressAxisTexts]
        assertEquals(listOf("80", "90", "100"), ticks())
        state.value = stateOf(history, pounds = true)
        waitUntil(timeoutMillis = 5_000) { ticks() != listOf("80", "90", "100") }
        // 80, 90 and 100 kg are 176.37, 198.42 and 220.46 lb: the marks are round pounds, not converted kilos.
        assertEquals(listOf("180", "200", "220"), ticks())
        part(ProgressTags.HIGHLIGHT_VALUE).assertTextEquals("220,46 lb".nb())
        assertTrue(summary().contains("220,46 libras"), summary())
    }

    @Test
    fun loadingShowsTheChartSkeleton() = runComposeUiTest {
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            runBlocking { gate.await() }
            ProgressIndex.build(history, records)
        })
        try {
            show(mutableStateOf(stateOf(benchHistory())), cache = cache)
            onNodeWithTag(ProgressTags.CHART_SKELETON).assertExists()
            onNodeWithTag(ProgressTags.TITLE).assertTextEquals("Supino reto com barra")
            assertGone(LineChartTag)
            assertGone(ProgressTags.HIGHLIGHT)
            val skeleton = onNodeWithTag(ProgressTags.CHART_SKELETON).getUnclippedBoundsInRoot()
            assertEquals(200f, (skeleton.bottom - skeleton.top).value, 0.5f)
            gate.complete(Unit)
            chartReady()
            assertGone(ProgressTags.CHART_SKELETON)
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun failedBuildShowsNoDataInsteadOfTheSkeleton() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { _, _ -> error("build failed on purpose") })
        try {
            show(mutableStateOf(stateOf(benchHistory())), cache = cache)
            waitForTag(ProgressTags.CHART_MESSAGE)
            onNodeWithTag(ProgressTags.CHART_MESSAGE).assertTextEquals("Nenhum treino com essa medida neste período.")
            assertGone(ProgressTags.CHART_SKELETON)
            assertGone(LineChartTag)
            onNodeWithTag(ProgressTags.TITLE).assertTextEquals("Supino reto com barra")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun chartArrivesOnTheSecondFrameWithTheIndexReady() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope)
        val state = stateOf(benchHistory())
        try {
            val index = runBlocking { cache.get(state.history, state.records) }
            mainClock.autoAdvance = false
            show(mutableStateOf(state), cache = cache)
            if (onAllNodesWithTag(ProgressTags.CHART_SKELETON).fetchSemanticsNodes().isEmpty()) mainClock.advanceTimeByFrame()
            // Frame 1: the index is there in the same frame; the one chart query (metrics included) starts.
            onNodeWithTag(ProgressTags.CHART_SKELETON).assertExists()
            val query = ShownChartQuery(SeriesKey(bench.id, bench.convention), null, ExercisePeriod.ALL, LocalDate(2026, 10, 31), utc, DayOfWeek.MONDAY)
            runBlocking { withTimeout(5_000) { while (cache.peekQuery(index, query) == null) delay(10) } }
            waitForIdle()
            // Frame 2: the chart, with its metric chips, and no second hop.
            mainClock.advanceTimeByFrame()
            waitForIdle()
            onNodeWithTag(LineChartTag).assertExists()
            onNodeWithTag(ProgressTags.metric(ProgressMetric.LOAD)).assertIsSelected()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun weeklyChartSaysWeekOfAndShowsTheNote() = runComposeUiTest {
        // 110 sessions, one every 3 days up to Fri Oct 30: more than 104, so one point per week (weeks from Monday).
        val history = (0 until 110).map { i ->
            val day = LocalDate(2026, 10, 30).minus(3 * i, DateTimeUnit.DAY)
            workout("k$i", day.atTime(9, 0).toInstant(utc).toEpochMilliseconds(), bench, 60.0 + i % 5 to 5)
        }
        show(mutableStateOf(stateOf(history)))
        chartReady()
        assertTrue(summary().contains("na semana de"), summary())
        part(ProgressTags.SINCE).assertTextContains("desde a semana de", substring = true)
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 1f, height / 2f)) }
        waitForTag(ProgressTags.READOUT)
        onNodeWithTag(ProgressTags.READOUT).assertTextContains("Semana de 26\u00A0out · ", substring = true)
        scrollTo(ProgressTags.WEEKLY_NOTE)
        onNodeWithTag(ProgressTags.WEEKLY_NOTE).assertTextEquals("Um ponto por semana: o melhor da semana.")
        onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
        waitAndScrollTo(ProgressTags.tableRow(0))
        onNodeWithTag(ProgressTags.tableRow(0)).assertTextContains("Semana de 26\u00A0out · esta semana (em andamento)")
        onNodeWithTag(ProgressTags.WEEKLY_NOTE).assertExists()
    }

    @Test
    fun onePointOfALongerHistoryIsStillDrawn() = runComposeUiTest {
        val history = listOf(
            workout("w3", at(2026, 10, 28), bench, 92.5 to 5),
            workout("w2", at(2026, 2, 10), bench, 90.0 to 5),
            workout("w1", at(2026, 1, 6), bench, 85.0 to 5),
        )
        show(mutableStateOf(stateOf(history)))
        chartReady()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.THREE_MONTHS)).performClick()
        waitUntil(timeoutMillis = 5_000) { summary().contains("últimos 3 meses") }
        // Only "the exercise has one session" says the chart comes later; one point of three is drawn, with its row.
        assertGone(ProgressTags.CHART_MESSAGE)
        scrollTo(ProgressTags.DATA_TOGGLE)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
        waitAndScrollTo(ProgressTags.tableRow(0))
        onNodeWithTag(ProgressTags.tableRow(0)).assertTextContains("28\u00A0out")
        assertGone(ProgressTags.tableRow(1))
    }

    @Test
    fun oneSessionShowsTheSentenceInsteadOfTheChart() = runComposeUiTest {
        show(mutableStateOf(stateOf(listOf(workout("w1", at(2026, 10, 28), bench, 92.5 to 5)))))
        waitForTag(ProgressTags.CHART_MESSAGE)
        onNodeWithTag(ProgressTags.CHART_MESSAGE).assertTextEquals("O gráfico aparece a partir de 2 treinos.")
        part(ProgressTags.HIGHLIGHT_VALUE).assertTextEquals("92,5 kg".nb())
        assertGone(ProgressTags.SINCE)
        assertGone(LineChartTag)
        assertGone(ProgressTags.DATA_TOGGLE)
    }

    @Test
    fun noEstimateInPeriodShowsTheSentence() = runComposeUiTest {
        val history = listOf(workout("w2", at(2026, 10, 28), bench, 60.0 to 12), workout("w1", at(2026, 10, 1), bench, 60.0 to 15))
        show(mutableStateOf(stateOf(history)), metric = "e1rm")
        waitForTag(ProgressTags.CHART_MESSAGE)
        onNodeWithTag(ProgressTags.CHART_MESSAGE).assertTextEquals("Sem séries de até 10 repetições para estimar o 1RM neste período.")
        assertGone(ProgressTags.HIGHLIGHT)
        assertGone(LineChartTag)
    }

    @Test
    fun emptyPeriodShowsNoPoints() = runComposeUiTest {
        val history = listOf(workout("w2", at(2025, 2, 10), bench, 80.0 to 5), workout("w1", at(2025, 1, 6), bench, 75.0 to 5))
        show(mutableStateOf(stateOf(history)))
        chartReady()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.THREE_MONTHS)).performClick()
        waitForTag(ProgressTags.CHART_MESSAGE)
        onNodeWithTag(ProgressTags.CHART_MESSAGE).assertTextEquals("Nenhum treino com essa medida neste período.")
        assertGone(LineChartTag)
        assertGone(ProgressTags.HIGHLIGHT)
        onNodeWithTag(ProgressTags.period(ExercisePeriod.ALL)).performClick()
        chartReady()
        assertGone(ProgressTags.CHART_MESSAGE)
    }

    @Test
    fun stateSurvivesRecreation() = runComposeUiTest {
        val restoration = screenRestoration()
        // In the system language: leaving a language override rebuilds the theme's tree (LocaleEpoch), which a
        // recreation would then lose along with the state it is meant to prove.
        show(mutableStateOf(stateOf(benchHistory())), restoration = restoration, locale = null)
        chartReady()
        val first = summary()
        onNodeWithTag(ProgressTags.metric(ProgressMetric.E1RM)).performClick()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.ONE_YEAR)).performClick()
        waitUntil(timeoutMillis = 5_000) { summary() != first }
        scrollTo(ProgressTags.DATA_TOGGLE)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
        waitForTag(ProgressTags.tableRow(0))

        restoration.emulateSaveAndRestore()
        waitAndScrollTo(ProgressTags.tableRow(0))
        onNodeWithTag(ProgressTags.metric(ProgressMetric.E1RM)).assertIsSelected()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.ONE_YEAR)).assertIsSelected()
        assertGone(LineChartTag)
        onNodeWithTag(ProgressTags.tableRow(0)).assertTextContains("108 kg".nb())

        // The selection is not kept.
        scrollTo(ProgressTags.DATA_TOGGLE)
        onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
        waitAndScrollTo(ProgressTags.CHART)
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 1f, height / 2f)) }
        waitForTag(ProgressTags.READOUT)
        restoration.emulateSaveAndRestore()
        waitAndScrollTo(ProgressTags.CHART)
        chartReady()
        assertGone(ProgressTags.READOUT)
    }

    @Test
    fun textsWholeAtTwoXAndTheChartStopsAtOneAndAHalf() {
        // Large volumes too: 10 × 100 kg × 10 is "10.000 kg", the widest highlight.
        val heavy = (1..4).map { day ->
            workout("v$day", at(2026, 10, day * 7), bench, *Array(10) { 100.0 + day to 10 })
        }
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            for (metric in listOf<String?>(null, "volume")) {
                runComposeUiTest {
                    show(mutableStateOf(stateOf(heavy)), metric = metric, fontScale = scale, size = DpSize(320.dp, 800.dp))
                    waitForTag(ProgressTags.HIGHLIGHT_VALUE)
                    assertNumbersWhole(atLeast = 1)
                    scrollTo(ProgressTags.CHART)
                    chartReady()
                    val chart = onNodeWithTag(ProgressTags.CHART).getUnclippedBoundsInRoot()
                    assertEquals(300f, (chart.bottom - chart.top).value, 0.5f, "at $scale the chart is 1.5 times its token, no more")
                    scrollTo(ProgressTags.DATA_TOGGLE)
                    onNodeWithTag(ProgressTags.DATA_TOGGLE).performClick()
                    waitForTag(ProgressTags.tableRow(0))
                    assertNumbersWhole(atLeast = 1)
                }
                LocalAppLocale.restore()
            }
        }
    }
}
