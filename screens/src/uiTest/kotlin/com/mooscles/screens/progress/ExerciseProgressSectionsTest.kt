package com.mooscles.screens.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.charts.LineChartTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.library.TopAnchored
import com.mooscles.screens.screenReaderOrder
import com.mooscles.screens.spokenLabel
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The lower part of the exercise progress screen (spec "Progresso do exercício"): the records of the exercise, its
 * sessions, the other-convention line and the screen's states (no session, not found, a new history), in pt-BR.
 */
@OptIn(ExperimentalTestApi::class)
class ExerciseProgressSectionsTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 31, 12, 0).toInstant(utc).toEpochMilliseconds()
    private val seams = ProgressSeams(nowMillis = { now }, zone = { utc }, weekStart = { DayOfWeek.MONDAY })

    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime(y, mo, d, 9, 0).toInstant(utc).toEpochMilliseconds()

    /** The app writes the unit after a no-break space. */
    private fun String.nb() = replace(" kg", "\u00A0kg").replace(" lb", "\u00A0lb")

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private val bench = catalog("barbell_bench_flat")
    private val row = ExerciseUi("custom-row", "Remada do Pedro", bench.muscle, bench.equipment, "Total load", custom = true)
    private val rowPerHand = row.copy(convention = "Per hand")
    private val assistedPullUp = catalog("selectorized_assisted_pull_up")

    /** A finished workout of one exercise: its [sets] as given, and the history's records of it. */
    private fun workout(id: String, startedAt: Long, exercise: ExerciseUi, sets: List<SetUi>, records: List<RecordUi> = emptyList()) =
        WorkoutUi(
            id, "Treino $id", startedAt, startedAt + 3_600_000,
            exercises = listOf(WorkoutExerciseUi("e-$id", exercise, sets)),
            records = records,
        )

    /** Completed sets of (load kg, reps). */
    private fun sets(id: String, vararg sets: Pair<Double, Int>) =
        sets.mapIndexed { i, (kg, reps) -> SetUi("s-$id-$i", reps, kg, completed = true) }

    /** Bench: 80 × 5 on Jun 3, 95 × 3 on Sep 12, 92.5 × 5 on Oct 28. */
    private fun benchHistory() = listOf(
        workout("w3", at(2026, 10, 28), bench, sets("w3", 92.5 to 5)),
        workout("w2", at(2026, 9, 12), bench, sets("w2", 95.0 to 3)),
        workout("w1", at(2026, 6, 3), bench, sets("w1", 80.0 to 5)),
    )

    private fun stateOf(
        history: List<WorkoutUi>,
        records: List<RecordUi> = emptyList(),
        exercises: List<ExerciseUi> = listOf(bench, row),
        pounds: Boolean = false,
    ) = UiState(loading = false, exercises = exercises, history = history, records = records, preferences = PreferencesUi(pounds = pounds))

    private class Calls {
        val about = mutableListOf<Unit>()
        val workouts = mutableListOf<String>()
        val probe = mutableListOf<String>()
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState>,
        exerciseId: String = bench.id,
        calls: Calls = Calls(),
        cache: ProgressIndexCache? = null,
        tall: Dp? = null,
        restoration: ScreenRestoration? = null,
        locale: String? = "pt-BR",
    ): Calls {
        withScreen(locale = locale, reducedMotion = true, probe = { calls.probe += it }, restoration = restoration) {
            TopAnchored(tall) {
                CompositionLocalProvider(LocalProgressIndexCache provides cache) {
                    ExerciseProgressScreen(
                        exerciseId = exerciseId,
                        metric = null,
                        state = state.value,
                        actions = RecordingActions(),
                        onBack = {},
                        onAbout = { calls.about += Unit },
                        onOpenWorkout = { calls.workouts += it },
                        seams = seams,
                    )
                }
            }
        }
        waitForIdle()
        return calls
    }

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.assertGone(tag: String) {
        assertEquals(0, onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size, "$tag should not be shown")
    }

    private fun ComposeUiTest.top(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    private fun ComposeUiTest.contentDescription(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")

    private fun ComposeUiTest.summary(): String =
        onNodeWithTag(LineChartTag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("")

    private val tall = 2400.dp

    /** A beaten record of the history and the same record as `UiState.records` holds it (no previous value). */
    private fun record(
        workoutId: String, date: Long, kg: Double, reps: Int, kind: RecordKind = RecordKind.WEIGHT,
        previousKg: Double? = null, previousReps: Int? = null, exercise: ExerciseUi = bench,
    ): Pair<RecordUi, RecordUi> {
        val event = RecordUi(exercise, kg, reps, previousKg, workoutId, date, kind, previousReps)
        return event to event.copy(previousKg = null, previousReps = null)
    }

    @Test
    fun currentWeightRecordAndUpToFiveRepsRecordsWithViewAll() = runComposeUiTest {
        // The load record: 100 × 3 on Oct 28, beating 95 kg. Seven reps records, one per load from 40 to 100 kg.
        val weight = record("w8", at(2026, 10, 28), 100.0, 3, previousKg = 95.0)
        val loads = listOf(40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val reps = loads.mapIndexed { i, kg ->
            record("r$i", at(2026, 8, 1 + i), kg, 12 - i, RecordKind.REPS, previousReps = if (i == 6) 2 else null)
        }
        val history = loads.indices.map { i ->
            workout("r$i", at(2026, 8, 1 + i), bench, sets("r$i", loads[i] to 12 - i), records = listOf(reps[i].first))
        } + workout("w8", at(2026, 10, 28), bench, sets("w8", 100.0 to 3), records = listOf(weight.first))
        show(mutableStateOf(stateOf(history, records = listOf(weight.second) + reps.map { it.second })), tall = tall)
        waitForTag(ProgressTags.RECORDS)
        onNodeWithTag(ProgressTags.RECORDS).assertTextEquals("Recordes deste exercício")
        // The current record carries its previous value from the history (the state's copy has none).
        onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("100 kg × 3 · antes 95 kg · 28 out".nb())
        assertEquals(
            "Supino reto com barra, Carga: 100 quilos por 3, antes 95 quilos, 28 de outubro",
            contentDescription(ProgressTags.RECORD_WEIGHT),
        )
        // Up to five reps records, the highest loads first, then "View all 7", which expands in place.
        val expected = listOf("100 kg × 6 · antes 2 repetições · 7 ago", "90 kg × 7 · 6 ago", "80 kg × 8 · 5 ago", "70 kg × 9 · 4 ago", "60 kg × 10 · 3 ago")
        for ((i, text) in expected.withIndex()) onNodeWithTag(ProgressTags.recordReps(i)).assertTextEquals(text.nb())
        assertGone(ProgressTags.recordReps(5))
        onNodeWithTag(ProgressTags.RECORDS_ALL).assertTextContains("Ver todos os 7").performClick()
        waitForTag(ProgressTags.recordReps(6))
        onNodeWithTag(ProgressTags.recordReps(5)).assertTextEquals("50 kg × 11 · 2 ago".nb())
        onNodeWithTag(ProgressTags.recordReps(6)).assertTextEquals("40 kg × 12 · 1 ago".nb())
        assertGone(ProgressTags.RECORDS_ALL)
        // Without other conventions there is no line about them.
        assertGone(ProgressTags.CONVENTION_NOTE)
    }

    @Test
    fun sessionsNewestFirstWithWarmupLetterAndRecordBadge() = runComposeUiTest {
        val warm = SetUi("s-w2-0", 10, 40.0, completed = true, warmup = true)
        val pr = SetUi("s-w2-1", 8, 80.0, completed = true, record = SetRecordUi(RecordKind.WEIGHT, previousKg = 75.0))
        val skipped = SetUi("s-w2-2", 8, 80.0, completed = false)
        val history = listOf(
            workout("w3", at(2026, 10, 28), bench, sets("w3", 82.5 to 6)),
            workout("w2", at(2026, 9, 12), bench, listOf(warm, pr, skipped, SetUi("s-w2-3", 6, 82.5, completed = true))),
            workout("w1", at(2026, 6, 3), bench, sets("w1", 75.0 to 8)),
        )
        show(mutableStateOf(stateOf(history)), tall = tall)
        waitForTag(ProgressTags.session("w1"))
        onNodeWithTag(ProgressTags.SESSIONS).assertTextEquals("Sessões")
        assertTrue(top(ProgressTags.SESSIONS) < top(ProgressTags.session("w3")))
        assertTrue(top(ProgressTags.session("w3")) < top(ProgressTags.session("w2")))
        assertTrue(top(ProgressTags.session("w2")) < top(ProgressTags.session("w1")))
        // Date, workout, then the completed sets in a line: "A" before the warm-up; the incomplete one is left out.
        onNodeWithTag(ProgressTags.session("w2"))
            .assertTextEquals("12 set", "Treino w2", "A\u00A040 × 10", "·", "80 × 8", "·", "82,5 × 6")
        // The badge sits on the set that beat the record, only there.
        fun badges(workoutId: String) = onAllNodes(
            hasTestTag(ProgressTags.SESSION_RECORD) and hasAnyAncestor(hasTestTag(ProgressTags.session(workoutId))),
            useUnmergedTree = true,
        ).fetchSemanticsNodes().size
        assertEquals(1, badges("w2"))
        assertEquals(0, badges("w1"))
        assertEquals(0, badges("w3"))
    }

    @Test
    fun sessionIsOneNodeAndOpensTheWorkout() = runComposeUiTest {
        val history = listOf(
            workout("w2", at(2026, 9, 12), bench, listOf(
                SetUi("a", 10, 40.0, completed = true, warmup = true),
                SetUi("b", 8, 80.0, completed = true, record = SetRecordUi(RecordKind.WEIGHT, previousKg = 75.0)),
            )),
            workout("w1", at(2026, 6, 3), bench, sets("w1", 75.0 to 8)),
        )
        val calls = show(mutableStateOf(stateOf(history)), tall = tall)
        val tag = ProgressTags.session("w2")
        waitForTag(tag)
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        assertEquals(Role.Button, node.config[SemanticsProperties.Role])
        assertEquals(
            "12 de setembro, Treino w2: aquecimento, 40 quilos por 10, 80 quilos por 8, recorde pessoal",
            node.config[SemanticsProperties.ContentDescription].first(),
        )
        // One stop for the reader: nothing inside the row can be acted on alone.
        assertEquals(0, onAllNodes(hasAnyAncestor(hasTestTag(tag)) and hasClickAction(), useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag(tag).performClick()
        waitForIdle()
        assertEquals(listOf("w2"), calls.workouts)
    }

    @Test
    fun conventionChangedShowsTheOtherConventionLine() = runComposeUiTest {
        // Logged as Per hand in June and July, as Total load since September; the exercise is Total load now.
        val perHandRecord = record("c2", at(2026, 7, 10), 30.0, 8, previousKg = 25.0, exercise = rowPerHand)
        val totalRecord = record("c4", at(2026, 10, 20), 70.0, 6, previousKg = 60.0, exercise = row)
        val history = listOf(
            workout("c4", at(2026, 10, 20), row, sets("c4", 70.0 to 6), records = listOf(totalRecord.first)),
            workout("c3", at(2026, 9, 15), row, sets("c3", 60.0 to 8)),
            workout("c2", at(2026, 7, 10), rowPerHand, sets("c2", 30.0 to 8), records = listOf(perHandRecord.first)),
            workout("c1", at(2026, 6, 5), rowPerHand, sets("c1", 25.0 to 10)),
        )
        show(mutableStateOf(stateOf(history, records = listOf(perHandRecord.second, totalRecord.second))), exerciseId = row.id, tall = tall)
        waitForTag(ProgressTags.CONVENTION_NOTE)
        onNodeWithTag(ProgressTags.CONVENTION_NOTE).assertTextEquals("Os treinos registrados como Por mão não entram neste gráfico.")
        // The chart is the current convention's: two workouts.
        assertTrue(summary().endsWith("2 treinos."), summary())
        // Under the chart, before the records.
        assertTrue(top(ProgressTags.CHART) < top(ProgressTags.CONVENTION_NOTE))
        assertTrue(top(ProgressTags.CONVENTION_NOTE) < top(ProgressTags.RECORDS))
        // The records are the current convention's; the sessions are all of them, the old ones included.
        onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("70 kg × 6 · antes 60 kg · 20 out".nb())
        for (id in listOf("c4", "c3", "c2", "c1")) onNodeWithTag(ProgressTags.session(id)).assertExists()
        onNodeWithTag(ProgressTags.session("c2")).assertTextContains("30 × 8", substring = true)
    }

    @Test
    fun deletedExerciseUsesTheSnapshotName() = runComposeUiTest {
        val history = listOf(
            workout("c2", at(2026, 10, 20), row, sets("c2", 70.0 to 6)),
            workout("c1", at(2026, 9, 15), row, sets("c1", 60.0 to 8)),
        )
        show(mutableStateOf(stateOf(history, exercises = listOf(bench))), exerciseId = row.id, tall = tall)
        waitForTag(LineChartTag)
        onNodeWithTag(ProgressTags.TITLE).assertTextEquals("Remada do Pedro")
        assertGone(ProgressTags.MISSING)
        assertGone(ProgressTags.EMPTY)
        onNodeWithTag(ProgressTags.session("c2")).assertExists()
    }

    @Test
    fun noSessionShowsTheEmptyStateWithViewInLibrary() = runComposeUiTest {
        // History of another exercise only.
        val calls = show(mutableStateOf(stateOf(listOf(workout("c1", at(2026, 9, 15), row, sets("c1", 60.0 to 8))))))
        waitForTag(ProgressTags.EMPTY)
        onNodeWithTag(ProgressTags.TITLE).assertTextEquals("Supino reto com barra")
        onNode(hasAnyAncestor(hasTestTag(ProgressTags.EMPTY)) and hasText("Você ainda não fez este exercício.")).assertExists()
        assertGone(ProgressTags.CHART_SKELETON)
        assertGone(ProgressTags.CHART_MESSAGE)
        assertGone(ProgressTags.period(ExercisePeriod.ALL))
        assertGone(ProgressTags.RECORDS)
        assertGone(ProgressTags.SESSIONS)
        onNode(hasAnyAncestor(hasTestTag(ProgressTags.EMPTY)) and hasClickAction()).assertTextContains("Ver na biblioteca").performClick()
        waitForIdle()
        assertEquals(1, calls.about.size)
    }

    @Test
    fun missingExerciseWithoutHistoryShowsNotFound() = runComposeUiTest {
        // While the index is built the screen waits; once it is ready and has no such exercise, it says so.
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            runBlocking { gate.await() }
            ProgressIndex.build(history, records)
        })
        try {
            show(mutableStateOf(stateOf(benchHistory())), exerciseId = "custom-gone", cache = cache)
            onNodeWithTag(ProgressTags.CHART_SKELETON).assertExists()
            assertGone(ProgressTags.MISSING)
            gate.complete(Unit)
            waitForTag(ProgressTags.MISSING)
            onNode(hasAnyAncestor(hasTestTag(ProgressTags.MISSING)) and hasText("Exercício não encontrado")).assertExists()
            assertGone(ProgressTags.CHART_SKELETON)
            assertGone(ProgressTags.TITLE)
            assertGone(ProgressTags.EMPTY)
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun aboutMenuCallsOnAbout() = runComposeUiTest {
        val calls = show(mutableStateOf(stateOf(benchHistory())))
        waitForTag(LineChartTag)
        onNodeWithTag(ProgressTags.MENU).performClick()
        waitForTag(ProgressTags.MENU_ABOUT)
        onNodeWithTag(ProgressTags.MENU_ABOUT).assertTextContains("Sobre o exercício").performClick()
        waitForIdle()
        assertEquals(1, calls.about.size)
        assertGone(ProgressTags.MENU_ABOUT)
    }

    @Test
    fun movingTheSelectionDoesNotRecomposeTheSessions() = runComposeUiTest {
        val weight = record("w2", at(2026, 9, 12) + 3_600_000, 95.0, 3, previousKg = 80.0)
        val history = benchHistory().map { if (it.id == "w2") it.copy(records = listOf(weight.first)) else it }
        val state = mutableStateOf(stateOf(history, records = listOf(weight.second)))
        val calls = show(state, tall = tall)
        waitForTag(ProgressTags.session("w1"))
        waitForTag(ProgressTags.RECORD_WEIGHT)
        fun rows() = calls.probe.filter { it.startsWith("progress-session-") || it.startsWith("progress-record") }
        waitForIdle()
        calls.probe.clear()

        // A drag on the chart: the readout and the chart's selection only.
        onNodeWithTag(LineChartTag).performTouchInput { click(Offset(width - 1f, height / 2f)) }
        waitForTag(ProgressTags.READOUT)
        waitForIdle()
        assertEquals(emptyList(), rows(), "the selection recomposed the rows")
        assertTrue(ProgressProbe.SCREEN !in calls.probe, "the selection recomposed the screen: ${calls.probe}")
        assertTrue(ProgressProbe.CHART !in calls.probe, "the selection recomposed the whole chart: ${calls.probe}")

        // A state emission that changes nothing they show: the screen runs again, the chart and the rows skip.
        calls.probe.clear()
        state.value = state.value.copy(updateSettingsRequest = 1)
        waitForIdle()
        assertTrue(ProgressProbe.SCREEN in calls.probe, "control: the screen did not recompose")
        assertEquals(emptyList(), rows(), "an unrelated state recomposed the rows")
        assertTrue(ProgressProbe.CHART !in calls.probe, "an unrelated state recomposed the chart")

        // Controls: another period redraws the chart (the rows stay), and the unit redraws the rows.
        calls.probe.clear()
        onNodeWithTag(ProgressTags.period(ExercisePeriod.ONE_YEAR)).performClick()
        waitUntil(timeoutMillis = 5_000) { summary().contains("último ano") }
        waitForIdle()
        assertTrue(ProgressProbe.CHART in calls.probe, "control: another period did not recompose the chart")
        assertEquals(emptyList(), rows(), "another period recomposed the rows")
        calls.probe.clear()
        state.value = state.value.copy(preferences = PreferencesUi(pounds = true))
        waitUntil(timeoutMillis = 5_000) { "progress-session-w1" in calls.probe }
        assertTrue("progress-record-weight" in calls.probe, "control: the unit did not recompose the record")
    }

    @Test
    fun newHistoryKeepsThePreviousChartUntilTheIndexIsReady() = runComposeUiTest {
        val first = benchHistory()
        val second = listOf(workout("w4", at(2026, 10, 30), bench, sets("w4", 97.5 to 3))) + first
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            if (history === second) runBlocking { gate.await() }
            ProgressIndex.build(history, records)
        })
        try {
            val state = mutableStateOf(stateOf(first))
            show(state, cache = cache, tall = tall)
            waitForTag(ProgressTags.session("w3"))
            waitForTag(LineChartTag)
            val before = summary()

            state.value = stateOf(second)
            waitForIdle()
            // The build is held: the previous chart and sessions stay, never the skeleton.
            onNodeWithTag(LineChartTag).assertExists()
            assertEquals(before, summary())
            assertGone(ProgressTags.CHART_SKELETON)
            onNodeWithTag(ProgressTags.session("w3")).assertExists()
            assertGone(ProgressTags.session("w4"))

            gate.complete(Unit)
            waitForTag(ProgressTags.session("w4"))
            waitUntil(timeoutMillis = 5_000) { summary().endsWith("4 treinos.") }
            assertGone(ProgressTags.CHART_SKELETON)
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun poundsSwitchFormatsInTheNewUnit() = runComposeUiTest {
        // The app's switch makes a new history list: its index is held, and the previous one is shown in pounds.
        val weight = record("w3", at(2026, 10, 28) + 3_600_000, 100.0, 3, previousKg = 95.0)
        val first = listOf(
            workout("w3", at(2026, 10, 28), bench, sets("w3", 100.0 to 3), records = listOf(weight.first)),
            workout("w1", at(2026, 6, 3), bench, sets("w1", 80.0 to 5)),
        )
        val second = first.toList()
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            if (history === second) runBlocking { gate.await() }
            ProgressIndex.build(history, records)
        })
        try {
            val state = mutableStateOf(stateOf(first, records = listOf(weight.second)))
            show(state, cache = cache, tall = tall)
            waitForTag(ProgressTags.RECORD_WEIGHT)
            onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("100 kg × 3 · antes 95 kg · 28 out".nb())

            state.value = stateOf(second, records = listOf(weight.second), pounds = true)
            waitForIdle()
            // 100 kg is 220.46 lb, 95 kg 209.44 lb.
            onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("220,46 lb × 3 · antes 209,44 lb · 28 out".nb())
            assertTrue(contentDescription(ProgressTags.RECORD_WEIGHT).contains("220,46 libras por 3, antes 209,44 libras"))
            onNodeWithTag(ProgressTags.session("w3")).assertTextContains("220,46 × 3")
            onNodeWithTag(ProgressTags.HIGHLIGHT).assertTextContains("220,46 lb".nb())
            assertTrue(summary().contains("220,46 libras"), summary())

            gate.complete(Unit)
            waitForIdle()
            onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("220,46 lb × 3 · antes 209,44 lb · 28 out".nb())
            onNodeWithTag(ProgressTags.session("w1")).assertTextContains("176,37 × 5")
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    /**
     * The whole order ([screenReaderOrder]) in a window tall enough that the list does not scroll: the bar, then
     * name, convention, metric, highlight, period, chart (its summary), "View data", records, sessions (spec
     * "Acessibilidade").
     */
    @Test
    fun readingOrderFollowsTheSpec() = runComposeUiTest {
        val weight = record("w3", at(2026, 10, 28) + 3_600_000, 92.5, 5)
        val reps = record("w1", at(2026, 6, 3) + 3_600_000, 80.0, 5, RecordKind.REPS)
        val history = benchHistory().map {
            when (it.id) {
                "w3" -> it.copy(records = listOf(weight.first))
                "w1" -> it.copy(records = listOf(reps.first))
                else -> it
            }
        }
        show(mutableStateOf(stateOf(history, records = listOf(weight.second, reps.second))), tall = tall)
        waitForTag(ProgressTags.session("w1"))
        waitForTag(LineChartTag)
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
                    tag == ProgressTags.CHART || tag == LineChartTag -> return "chart"
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
        val read = order.joinToString("\n") { "${region(it)} '${spokenLabel(it)}'" }
        val regions = order.map { assertNotNullRegion(region(it), read) }
        val sequence = regions.fold(mutableListOf<String>()) { acc, it -> acc.apply { if (lastOrNull() != it) add(it) } }
        assertEquals(
            listOf("bar", "name", "convention", "metric", "highlight", "period", "chart", "data", "records", "sessions"),
            sequence,
            read,
        )
        assertEquals(name, spokenLabel(order.first { region(it) == "name" }))
    }

    @Test
    fun assistedRepsRecordsAreTheirOwnGroupLowestAssistanceFirst() = runComposeUiTest {
        // A load exercise with one unassisted reps record and six assisted ones (a band), 10 to 35 kg of help.
        val plain = record("p", at(2026, 8, 20), 80.0, 8, RecordKind.REPS)
        val helps = listOf(35.0, 30.0, 25.0, 20.0, 15.0, 10.0)
        val assisted = helps.mapIndexed { i, kg ->
            val event = RecordUi(bench, kg, 10 + i, workoutId = "a$i", date = at(2026, 9, 1 + i), kind = RecordKind.REPS, assistance = true)
            event to event
        }
        val history = helps.indices.map { i ->
            workout("a$i", at(2026, 9, 1 + i), bench, listOf(SetUi("s-a$i", 10 + i, helps[i], completed = true, assistance = true)),
                records = listOf(assisted[i].first))
        } + workout("p", at(2026, 8, 20), bench, sets("p", 80.0 to 8), records = listOf(plain.first))
        show(mutableStateOf(stateOf(history, records = listOf(plain.second) + assisted.map { it.second })), tall = tall)
        waitForTag(ProgressTags.RECORD_ASSISTED_LABEL)
        onNodeWithTag(ProgressTags.recordReps(0)).assertTextEquals("80 kg × 8 · 20 ago".nb())
        assertGone(ProgressTags.recordReps(1))
        onNodeWithTag(ProgressTags.RECORD_ASSISTED_LABEL).assertTextEquals("Repetições assistidas")
        assertTrue(top(ProgressTags.recordReps(0)) < top(ProgressTags.RECORD_ASSISTED_LABEL))
        // The least help first, and the reading says the set was assisted.
        onNodeWithTag(ProgressTags.recordAssisted(0)).assertTextEquals("10 kg × 15 · 6 set".nb())
        onNodeWithTag(ProgressTags.recordAssisted(4)).assertTextEquals("30 kg × 11 · 2 set".nb())
        assertTrue(contentDescription(ProgressTags.recordAssisted(0)).endsWith("6 de setembro, série assistida"), contentDescription(ProgressTags.recordAssisted(0)))
        assertGone(ProgressTags.recordAssisted(5))
        // One "View all" for both groups.
        onNodeWithTag(ProgressTags.RECORDS_ALL).assertTextContains("Ver todos os 7").performClick()
        waitForTag(ProgressTags.recordAssisted(5))
        onNodeWithTag(ProgressTags.recordAssisted(5)).assertTextEquals("35 kg × 10 · 1 set".nb())
        assertGone(ProgressTags.RECORDS_ALL)
    }

    @Test
    fun sessionOfAnotherConventionSaysItsConvention() = runComposeUiTest {
        val history = listOf(
            workout("c4", at(2026, 10, 20), row, sets("c4", 70.0 to 6)),
            workout("c2", at(2026, 7, 10), rowPerHand, sets("c2", 30.0 to 8)),
        )
        show(mutableStateOf(stateOf(history)), exerciseId = row.id, tall = tall)
        waitForTag(ProgressTags.session("c2"))
        onNodeWithTag(ProgressTags.session("c2")).assertTextEquals("10 jul · Por mão", "Treino c2", "30 × 8")
        assertTrue(contentDescription(ProgressTags.session("c2")).startsWith("10 de julho, Treino c2, Por mão: 30 quilos por 8"))
        // The chart's own convention says nothing more.
        onNodeWithTag(ProgressTags.session("c4")).assertTextEquals("20 out", "Treino c4", "70 × 6")
        assertTrue(!contentDescription(ProgressTags.session("c4")).contains("Por mão"))
    }

    @Test
    fun assistedSetAndZeroLoadSayWhatTheyAre() = runComposeUiTest {
        val history = listOf(
            workout("b1", at(2026, 10, 20), bench, listOf(
                SetUi("x", 12, 40.0, completed = true, assistance = true),
                SetUi("y", 12, 0.0, completed = true),
            )),
            // On Assistance every set is assisted, and the convention already says so: no letter.
            workout("h1", at(2026, 10, 21), assistedPullUp, listOf(SetUi("z", 8, 30.0, completed = true, assistance = true))),
        )
        show(mutableStateOf(stateOf(history, exercises = listOf(bench, assistedPullUp))), tall = tall)
        waitForTag(ProgressTags.session("b1"))
        onNodeWithTag(ProgressTags.session("b1")).assertTextEquals("20 out", "Treino b1", "AS 40 × 12", "·", "Peso corporal × 12")
        assertTrue(
            contentDescription(ProgressTags.session("b1")).startsWith("20 de outubro, Treino b1: série assistida, 40 quilos por 12, 12 repetições"),
            contentDescription(ProgressTags.session("b1")),
        )
    }

    @Test
    fun assistanceSessionsHaveNoAssistedLetter() = runComposeUiTest {
        val history = listOf(
            workout("h2", at(2026, 10, 22), assistedPullUp, listOf(SetUi("z2", 8, 25.0, completed = true, assistance = true))),
            workout("h1", at(2026, 10, 21), assistedPullUp, listOf(SetUi("z", 8, 30.0, completed = true, assistance = true))),
        )
        show(mutableStateOf(stateOf(history, exercises = listOf(bench, assistedPullUp))), exerciseId = assistedPullUp.id, tall = tall)
        waitForTag(ProgressTags.session("h1"))
        onNodeWithTag(ProgressTags.session("h1")).assertTextEquals("21 out", "Treino h1", "30 × 8")
        assertTrue(!contentDescription(ProgressTags.session("h1")).contains("série assistida"))
    }

    @Test
    fun datesAYearOrMoreAgoShowTheYear() = runComposeUiTest {
        // Today is Oct 31, 2026: Sep 12, 2025 is more than a year ago; Nov 5, 2025 is not.
        val old = record("w0", at(2025, 9, 12), 100.0, 3)
        val history = listOf(
            workout("w1", at(2025, 11, 5), bench, sets("w1", 90.0 to 5)),
            workout("w0", at(2025, 9, 12), bench, sets("w0", 100.0 to 3), records = listOf(old.first)),
        )
        show(mutableStateOf(stateOf(history, records = listOf(old.second))), tall = tall)
        waitForTag(ProgressTags.session("w0"))
        onNodeWithTag(ProgressTags.RECORD_WEIGHT).assertTextEquals("100 kg × 3 · 12 set 2025".nb())
        onNodeWithTag(ProgressTags.session("w0")).assertTextContains("12 set 2025")
        onNodeWithTag(ProgressTags.session("w1")).assertTextContains("5 nov")
        onNodeWithTag(ProgressTags.session("w1")).assertTextEquals("5 nov", "Treino w1", "90 × 5")
    }

    @Test
    fun viewAllSurvivesRecreation() = runComposeUiTest {
        val loads = listOf(40.0, 50.0, 60.0, 70.0, 80.0, 90.0, 100.0)
        val reps = loads.mapIndexed { i, kg -> record("r$i", at(2026, 8, 1 + i), kg, 12 - i, RecordKind.REPS) }
        val history = loads.indices.map { i ->
            workout("r$i", at(2026, 8, 1 + i), bench, sets("r$i", loads[i] to 12 - i), records = listOf(reps[i].first))
        }
        val restoration = screenRestoration()
        // In the system language, as the screen's other recreation test (LocaleEpoch).
        show(mutableStateOf(stateOf(history, records = reps.map { it.second })), tall = tall, restoration = restoration, locale = null)
        waitForTag(ProgressTags.RECORDS_ALL)
        onNodeWithTag(ProgressTags.RECORDS_ALL).performClick()
        waitForTag(ProgressTags.recordReps(6))
        restoration.emulateSaveAndRestore()
        waitForTag(ProgressTags.recordReps(6))
        assertGone(ProgressTags.RECORDS_ALL)
    }

    private fun assertNotNullRegion(region: String?, read: String): String {
        assertTrue(region != null, "a node of no region in:\n$read")
        return region
    }
}
