package com.mooscles.screens.records

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.ExerciseNames
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.assertShownWhole
import com.mooscles.screens.isPlaced
import com.mooscles.screens.library.TALL
import com.mooscles.screens.library.TopAnchored
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.progress.ProgressMetric
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The Records tab (spec "Recordes (aba)" and D11): the timeline, its rows, chips, search, states and the rules, in pt-BR. */
@OptIn(ExperimentalTestApi::class)
class RecordsScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val utc = TimeZone.UTC
    private val now = LocalDateTime(2026, 10, 31, 12, 0).toInstant(utc).toEpochMilliseconds()
    private val seams = ProgressSeams(nowMillis = { now }, zone = { utc })

    private fun at(y: Int, mo: Int, d: Int) = LocalDateTime(y, mo, d, 9, 0).toInstant(utc).toEpochMilliseconds()

    /** A record's date: its workout's end, an hour after it started. */
    private fun end(y: Int, mo: Int, d: Int) = at(y, mo, d) + HOUR

    /** The app writes the unit after a no-break space. */
    private fun String.nb() = replace(" kg", " kg").replace(" lb", " lb")

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    private val bench = catalog("barbell_bench_flat")
    private val pushUp = catalog("bodyweight_push_up")
    private val squat = catalog("barbell_back_squat_high_bar")

    // The history's records (WorkoutUi.records), with what they beat.
    private val benchFirstW = RecordUi(bench, 80.0, 6, workoutId = "w1", date = end(2026, 8, 5))
    private val benchFirstR = RecordUi(bench, 80.0, 6, workoutId = "w1", date = end(2026, 8, 5), kind = RecordKind.REPS)
    private val pushFirst = RecordUi(pushUp, 0.0, 10, workoutId = "w5", date = end(2026, 9, 1), kind = RecordKind.REPS)
    private val benchW85 = RecordUi(bench, 85.0, 5, previousKg = 80.0, workoutId = "w2", date = end(2026, 9, 12))
    private val squatFirst = RecordUi(squat, 100.0, 5, workoutId = "w6", date = end(2026, 10, 10))
    private val pushR12 = RecordUi(pushUp, 0.0, 12, previousReps = 10, workoutId = "w4", date = end(2026, 10, 20), kind = RecordKind.REPS)
    private val benchW875 = RecordUi(bench, 87.5, 6, previousKg = 85.0, workoutId = "w3", date = end(2026, 10, 28))
    private val benchR8 = RecordUi(bench, 80.0, 8, previousReps = 6, workoutId = "w3", date = end(2026, 10, 28), kind = RecordKind.REPS)

    /** A finished workout, every set completed: one entry per exercise, each set (load kg, reps). */
    private fun workout(id: String, startedAt: Long, vararg entries: Pair<ExerciseUi, List<Pair<Double, Int>>>, records: List<RecordUi>) =
        WorkoutUi(
            id, "Treino $id", startedAt, startedAt + HOUR,
            exercises = entries.mapIndexed { e, (exercise, sets) ->
                WorkoutExerciseUi("e-$id-$e", exercise, sets.mapIndexed { i, (kg, reps) -> SetUi("s-$id-$e-$i", reps, kg, completed = true) })
            },
            records = records,
        )

    private val history = listOf(
        workout("w3", at(2026, 10, 28), bench to listOf(87.5 to 6, 80.0 to 8), records = listOf(benchW875, benchR8)),
        workout("w4", at(2026, 10, 20), pushUp to listOf(0.0 to 12), records = listOf(pushR12)),
        workout("w6", at(2026, 10, 10), squat to listOf(100.0 to 5), records = listOf(squatFirst)),
        workout("w2", at(2026, 9, 12), bench to listOf(85.0 to 5), records = listOf(benchW85)),
        workout("w5", at(2026, 9, 1), pushUp to listOf(0.0 to 10), records = listOf(pushFirst)),
        workout("w1", at(2026, 8, 5), bench to listOf(80.0 to 6), records = listOf(benchFirstW, benchFirstR)),
    )

    /** `UiState.records`: the records that still hold, as the presentation gives them (no previous value). */
    private val current = listOf(
        benchW875.copy(previousKg = null),
        benchR8.copy(previousReps = null),
        pushR12.copy(previousReps = null),
        squatFirst,
    )

    private fun stateOf(history: List<WorkoutUi> = this.history, records: List<RecordUi> = current, pounds: Boolean = false) =
        UiState(loading = false, exercises = listOf(bench, pushUp, squat), history = history, records = records, preferences = PreferencesUi(pounds = pounds))

    /** What the screen asked of its host. */
    private class Calls {
        val progress = mutableListOf<Pair<String, ProgressMetric>>()
        val workouts = mutableListOf<String>()
        var settings = 0
    }

    private fun ComposeUiTest.show(
        state: MutableState<UiState> = mutableStateOf(stateOf()),
        fontScale: Float = 1f,
        size: DpSize? = null,
        tall: Boolean = false,
        restoration: ScreenRestoration? = null,
        cache: ProgressIndexCache? = null,
        locale: String? = "pt-BR",
        probe: ((String) -> Unit)? = null,
        seams: ProgressSeams = this@RecordsScreenTest.seams,
    ): Calls {
        val calls = Calls()
        withScreen(locale = locale, reducedMotion = true, fontScale = fontScale, size = size, restoration = restoration, probe = probe) {
            TopAnchored(if (tall) TALL else null) {
                CompositionLocalProvider(LocalProgressIndexCache provides cache) {
                    RecordsScreen(
                        state = state.value,
                        actions = RecordingActions(),
                        onOpenProgress = { id, metric -> calls.progress += id to metric },
                        onOpenWorkout = { calls.workouts += it },
                        onOpenSettings = { calls.settings++ },
                        seams = seams,
                    )
                }
            }
        }
        waitForIdle()
        return calls
    }

    private fun rowTag(record: RecordUi) = RecordsTags.row(RecordTimelines.key(RecordEvent(record, "", 0, current = false)))

    private val rowMatcher = SemanticsMatcher("a record row") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordsTags.ROW_PREFIX) == true
    }

    private val headerMatcher = SemanticsMatcher("a month header") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(RecordsTags.MONTH_PREFIX) == true
    }

    /** The row tags the list shows, top to bottom. */
    private fun ComposeUiTest.rows(): List<String> =
        onAllNodes(rowMatcher and isPlaced).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f }
            .sortedBy { it.boundsInRoot.top }
            .map { it.config[SemanticsProperties.TestTag] }

    /** The month headers' texts, top to bottom. */
    private fun ComposeUiTest.headers(): List<String> =
        onAllNodes(headerMatcher and isPlaced).fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }
            .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

    private fun ComposeUiTest.waitForRows() {
        waitUntil(timeoutMillis = 5_000) { onAllNodes(rowMatcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun ComposeUiTest.waitForTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** The part [partTag] of the row of [record] (unmerged tree). */
    private fun ComposeUiTest.part(record: RecordUi, partTag: String) =
        onNode(hasTestTag(partTag) and hasAnyAncestor(hasTestTag(rowTag(record))), useUnmergedTree = true)

    private fun ComposeUiTest.partCount(record: RecordUi, partTag: String) =
        onAllNodes(hasTestTag(partTag) and hasAnyAncestor(hasTestTag(rowTag(record))), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun ComposeUiTest.assertGone(tag: String) {
        assertEquals(0, onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size, "$tag should not be shown")
    }

    private fun ComposeUiTest.search(text: String) {
        onNode(hasSetTextAction()).performTextReplacement(text)
        waitForIdle()
    }

    private fun ComposeUiTest.spoken(record: RecordUi): String =
        onNodeWithTag(rowTag(record)).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("")

    @Test
    fun timelineGroupsByMonthNewestFirstWithBeatenRecordsOnly() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        assertEquals(listOf("outubro de 2026 · 3 recordes", "setembro de 2026 · 1 recorde"), headers())
        // Newest first; in one workout, load before reps (the index's order). No first record without the chip.
        assertEquals(listOf(benchW875, benchR8, pushR12, benchW85).map(::rowTag), rows())
        onNodeWithTag(RecordsTags.month(2026, 10)).assertExists()
        onNodeWithTag(RecordsTags.month(2026, 9)).assertExists()
        onNodeWithTag(RecordsTags.CURRENT_ONLY).assertIsOff()
        onNodeWithTag(RecordsTags.FIRST).assertIsOff()
        // The headers sit above their rows.
        val october = onNodeWithTag(RecordsTags.month(2026, 10)).fetchSemanticsNode().boundsInRoot
        val september = onNodeWithTag(RecordsTags.month(2026, 9)).fetchSemanticsNode().boundsInRoot
        val pushRow = onNodeWithTag(rowTag(pushR12)).fetchSemanticsNode().boundsInRoot
        val benchRow = onNodeWithTag(rowTag(benchW85)).fetchSemanticsNode().boundsInRoot
        assertTrue(october.bottom <= onNodeWithTag(rowTag(benchW875)).fetchSemanticsNode().boundsInRoot.top + 0.5f)
        assertTrue(pushRow.bottom <= september.top + 0.5f && september.bottom <= benchRow.top + 0.5f)
    }

    @Test
    fun rowIsOneNodeWithBadgeNameKindValuePreviousDateAndCurrent() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        val name = ExerciseNames.display(bench.id, bench.name, custom = false, portuguese = true)
        assertEquals("Supino reto com barra", name)
        assertEquals("$name, Carga: 87,5 quilos por 6, antes 85 quilos, 28 de outubro, Atual", spoken(benchW875))
        part(benchW875, RecordsTags.ROW_NAME).assertTextEquals(name)
        part(benchW875, RecordsTags.ROW_KIND).assertTextEquals("Carga")
        part(benchW875, RecordsTags.ROW_VALUE).assertTextEquals("87,5 kg × 6 · antes 85 kg".nb())
        part(benchW875, RecordsTags.ROW_DATE).assertTextEquals("28 out")
        part(benchW875, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")
        assertEquals(1, partCount(benchW875, RecordsTags.BADGE))
        assertEquals(0, partCount(benchW875, RecordsTags.BADGE_FIRST))
        // One node: the row is the only clickable, and nothing inside it is a node of its own for a screen reader.
        val clickables = onAllNodes(hasClickAction() and (hasTestTag(rowTag(benchW875)) or hasAnyAncestor(hasTestTag(rowTag(benchW875)))), useUnmergedTree = true)
        assertEquals(1, clickables.fetchSemanticsNodes().size)
        val merged = onNodeWithTag(rowTag(benchW875)).fetchSemanticsNode()
        assertTrue(merged.children.isEmpty(), "the row merges its parts: ${merged.children.size} children")
        // A beaten record that no longer holds has no "Atual".
        assertEquals(0, partCount(benchW85, RecordsTags.ROW_CURRENT))
        assertTrue(!spoken(benchW85).contains("Atual"), spoken(benchW85))
    }

    @Test
    fun repsRecordReadsWasNReps() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        part(benchR8, RecordsTags.ROW_KIND).assertTextEquals("Repetições")
        part(benchR8, RecordsTags.ROW_VALUE).assertTextEquals("80 kg × 8 · antes 6 repetições".nb())
        part(benchR8, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")
        assertEquals("Supino reto com barra, Repetições: 80 quilos por 8, antes 6 repetições, 28 de outubro, Atual", spoken(benchR8))
    }

    @Test
    fun bodyweightRepsRecordReadsBodyweight() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        part(pushR12, RecordsTags.ROW_VALUE).assertTextEquals("Peso corporal × 12 · antes 10 repetições")
        part(pushR12, RecordsTags.ROW_KIND).assertTextEquals("Repetições")
        part(pushR12, RecordsTags.ROW_DATE).assertTextEquals("20 out")
        // Read with "por" like every other set, never "×" (critique of phase 5A).
        assertTrue(spoken(pushR12).contains("Repetições: peso corporal por 12, antes 10 repetições"), spoken(pushR12))
    }

    @Test
    fun assistedRepsRecordSaysAssistance() = runComposeUiTest {
        val assisted = catalog("selectorized_assisted_pull_up")
        // An Assistance exercise, and an assisted set of a load exercise: the number is help, not load.
        val dip = RecordUi(assisted, 30.0, 8, previousReps = 6, workoutId = "a1", date = end(2026, 10, 15), kind = RecordKind.REPS, assistance = true)
        val benchAssisted = RecordUi(bench, 20.0, 10, previousReps = 7, workoutId = "a1", date = end(2026, 10, 15), kind = RecordKind.REPS, assistance = true)
        val history = listOf(
            WorkoutUi(
                "a1", "Treino a1", at(2026, 10, 15), at(2026, 10, 15) + HOUR,
                exercises = listOf(
                    WorkoutExerciseUi("e-a1-0", assisted, listOf(SetUi("s-a1-0", 8, 30.0, completed = true, assistance = true))),
                    WorkoutExerciseUi("e-a1-1", bench, listOf(SetUi("s-a1-1", 10, 20.0, completed = true, assistance = true))),
                ),
                records = listOf(dip, benchAssisted),
            ),
        )
        show(mutableStateOf(stateOf(history = history, records = emptyList())), tall = true)
        waitForRows()
        part(dip, RecordsTags.ROW_VALUE).assertTextEquals("30 kg de assistência × 8 · antes 6 repetições".nb())
        part(dip, RecordsTags.ROW_KIND).assertTextEquals("Repetições")
        assertTrue(spoken(dip).contains("30 quilos de assistência por 8, antes 6 repetições"), spoken(dip))
        part(benchAssisted, RecordsTags.ROW_VALUE).assertTextEquals("20 kg de assistência × 10 · antes 7 repetições".nb())
        assertTrue(spoken(benchAssisted).contains("20 quilos de assistência por 10"), spoken(benchAssisted))
    }

    @Test
    fun onlyFirstRecordsOffersTheChip() = runComposeUiTest {
        // A new person: two workouts, every record a first one, which the timeline hides without the chip.
        val firsts = history.filter { it.id == "w5" || it.id == "w1" }
        show(mutableStateOf(stateOf(history = firsts, records = listOf(pushFirst, benchFirstW, benchFirstR))), tall = true)
        waitForTag(RecordsTags.ONLY_FIRST)
        onNode(hasAnyAncestor(hasTestTag(RecordsTags.ONLY_FIRST)) and hasText("Nenhum recorde batido ainda"), useUnmergedTree = true).assertExists()
        onNode(
            hasAnyAncestor(hasTestTag(RecordsTags.ONLY_FIRST)) and hasText("Seus primeiros registros ficam em “Primeiros registros”."),
            useUnmergedTree = true,
        ).assertExists()
        assertGone(RecordsTags.EMPTY)
        assertGone(RecordsTags.NONE_FOUND)
        onNode(hasClickAction() and hasAnyAncestor(hasTestTag(RecordsTags.ONLY_FIRST)) and hasText("Ver primeiros registros")).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 3 }
        onNodeWithTag(RecordsTags.FIRST).assertIsOn()
        assertEquals(listOf(pushFirst, benchFirstW, benchFirstR).map(::rowTag), rows())
        // A search that finds nothing is still "nothing found", not this state.
        onNodeWithTag(RecordsTags.FIRST).performClick()
        search("remada curvada")
        waitForTag(RecordsTags.NONE_FOUND)
        assertGone(RecordsTags.ONLY_FIRST)
    }

    @Test
    fun firstRecordsChipAddsThemWithTheSoftBadge() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        onNodeWithTag(RecordsTags.FIRST).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 8 }
        onNodeWithTag(RecordsTags.FIRST).assertIsOn()
        assertEquals(
            listOf(benchW875, benchR8, pushR12, squatFirst, benchW85, pushFirst, benchFirstW, benchFirstR).map(::rowTag),
            rows(),
        )
        assertEquals(listOf("outubro de 2026 · 4 recordes", "setembro de 2026 · 2 recordes", "agosto de 2026 · 2 recordes"), headers())
        for (first in listOf(squatFirst, pushFirst, benchFirstW, benchFirstR)) {
            assertEquals(1, partCount(first, RecordsTags.BADGE_FIRST), rowTag(first))
            assertEquals(0, partCount(first, RecordsTags.BADGE), rowTag(first))
            assertTrue(spoken(first).endsWith("Primeiro registro"), spoken(first))
            // Told apart in words, not by the fill alone.
            part(first, RecordsTags.BADGE_FIRST).assertTextEquals("Primeiro")
        }
        part(benchW875, RecordsTags.BADGE).assertTextEquals("PR")
        part(benchW85, RecordsTags.BADGE).assertTextEquals("PR")
        // A first record has no previous value: just the set.
        part(benchFirstW, RecordsTags.ROW_VALUE).assertTextEquals("80 kg × 6".nb())
        part(squatFirst, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")
        assertEquals(1, partCount(benchW875, RecordsTags.BADGE))

        onNodeWithTag(RecordsTags.FIRST).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 4 }
    }

    @Test
    fun currentOnlyListsEveryCurrentRecordAndIgnoresTheOtherChip() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        onNodeWithTag(RecordsTags.FIRST).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 8 }
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 4 }
        onNodeWithTag(RecordsTags.CURRENT_ONLY).assertIsOn()
        // The other chip keeps its choice but has no say while "Só atuais" is on.
        onNodeWithTag(RecordsTags.FIRST).assertIsOn().assertIsNotEnabled()
        assertEquals(listOf(benchW875, benchR8, pushR12, squatFirst).map(::rowTag), rows())
        assertEquals(listOf("outubro de 2026 · 4 recordes"), headers())
        for (record in listOf(benchW875, benchR8, pushR12, squatFirst)) {
            part(record, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")
        }
        // A current record is shown as its history event, with what it beat (P1); one never beaten is a first record.
        part(benchW875, RecordsTags.ROW_VALUE).assertTextEquals("87,5 kg × 6 · antes 85 kg".nb())
        part(pushR12, RecordsTags.ROW_VALUE).assertTextEquals("Peso corporal × 12 · antes 10 repetições")
        assertEquals(1, partCount(benchW875, RecordsTags.BADGE))
        assertEquals(1, partCount(squatFirst, RecordsTags.BADGE_FIRST))

        // Off, the timeline comes back with the first records the other chip still asks for.
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().size == 8 }
    }

    @Test
    fun searchInPortugueseFindsTheCatalogNameWithItsCurrentRecordOnTop() = runComposeUiTest {
        show(tall = true)
        waitForRows()
        search("supino")
        waitUntil(timeoutMillis = 5_000) { rows().size == 3 }
        assertEquals(listOf(benchW875, benchR8, benchW85).map(::rowTag), rows())
        part(benchW875, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")

        // A current first record of what the search finds shows without the chip, on top.
        val squatName = ExerciseNames.display(squat.id, squat.name, custom = false, portuguese = true)
        assertTrue(squatName.startsWith("Agachamento"), squatName)
        search("agachamento")
        waitUntil(timeoutMillis = 5_000) { rows() == listOf(rowTag(squatFirst)) }
        part(squatFirst, RecordsTags.ROW_NAME).assertTextEquals(squatName)
        part(squatFirst, RecordsTags.ROW_CURRENT).assertTextEquals("Atual")
        assertEquals(1, partCount(squatFirst, RecordsTags.BADGE_FIRST))

        // "Só atuais" with a search is the intersection.
        search("supino")
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitUntil(timeoutMillis = 5_000) { rows() == listOf(benchW875, benchR8).map(::rowTag) }

        // Only spaces is no search.
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        search("   ")
        waitUntil(timeoutMillis = 5_000) { rows().size == 4 }
    }

    @Test
    fun noMatchShowsNoRecordsFound() = runComposeUiTest {
        show()
        waitForRows()
        search("remada curvada")
        waitForTag(RecordsTags.NONE_FOUND)
        onNodeWithTag(RecordsTags.NONE_FOUND).assertTextEquals("Nenhum recorde encontrado")
        assertEquals(0, onAllNodes(rowMatcher).fetchSemanticsNodes().size)
        assertGone(RecordsTags.EMPTY)
        assertGone(RecordsTags.SKELETON)
    }

    @Test
    fun emptyHistoryShowsTheFirstRecordEmptyState() = runComposeUiTest {
        show(mutableStateOf(stateOf(history = emptyList(), records = emptyList())))
        waitForTag(RecordsTags.EMPTY)
        onNode(hasTestTag(RecordsTags.EMPTY)).assertIsDisplayed()
        onNode(hasAnyAncestor(hasTestTag(RecordsTags.EMPTY)) and hasText("Seu primeiro recorde aparece aqui"), useUnmergedTree = true).assertExists()
        onNode(
            hasAnyAncestor(hasTestTag(RecordsTags.EMPTY)) and hasText("Conclua séries de trabalho para registrar recordes de carga e de repetições."),
            useUnmergedTree = true,
        ).assertExists()
        assertGone(RecordsTags.NONE_FOUND)
        assertGone(RecordsTags.SKELETON)
        // The search and the rules are still there.
        onNodeWithTag(RecordsTags.SEARCH).assertExists()
        onNodeWithTag(RecordsTags.RULES).assertExists()
    }

    @Test
    fun loadingShowsSkeleton() = runComposeUiTest {
        // The app's state still loading: skeleton, never the empty state of a list on its way.
        val state = mutableStateOf(UiState())
        val gate = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { history, records ->
            if (history.isNotEmpty()) runBlocking { gate.await() }
            ProgressIndex.build(history, records)
        })
        try {
            show(state, cache = cache)
            waitForTag(RecordsTags.SKELETON)
            assertGone(RecordsTags.EMPTY)
            // Loaded, but the index is not built yet: still the skeleton.
            state.value = stateOf()
            waitForIdle()
            onAllNodesWithTag(RecordsTags.SKELETON).fetchSemanticsNodes().isNotEmpty().let { assertTrue(it, "skeleton while the index builds") }
            assertEquals(0, onAllNodes(rowMatcher).fetchSemanticsNodes().size)
            assertGone(RecordsTags.EMPTY)
            onNodeWithTag(RecordsTags.SEARCH).assertExists()
            gate.complete(Unit)
            waitForRows()
            assertGone(RecordsTags.SKELETON)
        } finally {
            gate.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun failedBuildShowsNoneFoundNotTheSkeleton() = runComposeUiTest {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val cache = ProgressIndexCache(scope, build = { _, _ -> error("build failed on purpose") })
        try {
            show(cache = cache)
            // Neutral: a person with many records is never told to go and set their first one.
            waitForTag(RecordsTags.NONE_FOUND)
            assertGone(RecordsTags.SKELETON)
            assertGone(RecordsTags.EMPTY)
            assertGone(RecordsTags.ONLY_FIRST)
            search("supino")
            waitForTag(RecordsTags.NONE_FOUND)
            assertGone(RecordsTags.SKELETON)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun tapOpensProgressWithTheRecordsMetric() = runComposeUiTest {
        val calls = show(tall = true)
        waitForRows()
        onNodeWithTag(rowTag(benchW875)).performClick()
        onNodeWithTag(rowTag(benchR8)).performClick()
        onNodeWithTag(rowTag(pushR12)).performClick()
        waitForIdle()
        assertEquals(listOf(bench.id to ProgressMetric.LOAD, bench.id to ProgressMetric.REPS, pushUp.id to ProgressMetric.REPS), calls.progress)
        assertTrue(calls.workouts.isEmpty())
    }

    @Test
    fun openWorkoutAccessibilityAction() = runComposeUiTest {
        val calls = show(tall = true)
        waitForRows()
        val actions = onNodeWithTag(rowTag(benchW85)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Abrir treino"), actions.map { it.label })
        runOnIdle { assertTrue(actions.single().action()) }
        waitForIdle()
        assertEquals(listOf("w2"), calls.workouts)
        assertTrue(calls.progress.isEmpty())
    }

    /**
     * The search keeps its focus after typing; leaving with it would move the focus into the list and save a scroll the
     * person did not make. A row, its "Open workout" and Settings let the focus go before they leave.
     */
    @Test
    fun leavingLetsTheSearchFocusGo() = runComposeUiTest {
        val calls = show()
        waitForRows()
        search("supino")
        onNode(hasSetTextAction()).assertIsFocused()
        onNodeWithTag(rowTag(benchW875)).performClick()
        waitForIdle()
        assertEquals(listOf(bench.id to ProgressMetric.LOAD), calls.progress)
        onNode(hasSetTextAction()).assertIsNotFocused()

        search("")
        search("supino")
        onNode(hasSetTextAction()).assertIsFocused()
        val openWorkout = onNodeWithTag(rowTag(benchW85)).fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        runOnIdle { assertTrue(openWorkout.action()) }
        waitForIdle()
        assertEquals(listOf("w2"), calls.workouts)
        onNode(hasSetTextAction()).assertIsNotFocused()

        search("")
        search("supino")
        onNode(hasSetTextAction()).assertIsFocused()
        onNodeWithTag(RecordsTags.SETTINGS).performClick()
        waitForIdle()
        assertEquals(1, calls.settings)
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    /** A drag on the list lets the search's focus go; the scroll to the top of a new search does not. */
    @Test
    fun draggingTheListLetsTheSearchFocusGoButANewSearchKeepsIt() = runComposeUiTest {
        val many = (1..30).map { i ->
            val month = 1 + (30 - i) % 12
            val year = 2024 + (30 - i) / 12
            val record = RecordUi(bench, 80.0 + i, 5, previousKg = 79.0 + i, workoutId = "m$i", date = end(year, month, 3))
            workout("m$i", at(year, month, 3), bench to listOf(80.0 + i to 5), records = listOf(record))
        }.sortedByDescending { it.startedAt }
        show(mutableStateOf(stateOf(history = many, records = emptyList())))
        waitForRows()
        onNodeWithTag(RecordsScreenTag).performScrollToIndex(20)
        waitForIdle()
        // A new search scrolls the list to the top while the field still has the focus.
        search("supino")
        waitUntil(timeoutMillis = 5_000) { rows().firstOrNull() == rowTag(many.first().records.single()) }
        onNode(hasSetTextAction()).assertIsFocused()
        onNodeWithTag(RecordsScreenTag).performTouchInput { swipeUp() }
        waitForIdle()
        onNode(hasSetTextAction()).assertIsNotFocused()
    }

    /**
     * Final review of phase 5A (FR-I1): a chip whose result adds a newer month shows that month on top. The list goes to
     * the top once the new result is shown, not before: scrolled while the previous result is still there, the new one
     * keeps the previous first row in place, which leaves the newer month above the screen. The queries wait for a gate
     * here, so the previous result is surely still shown when the chip is tapped.
     */
    @Test
    fun aChipThatAddsANewerMonthShowsItOnTop() = runComposeUiTest {
        val many = (1..30).map { i ->
            val month = 1 + (30 - i) % 12
            val year = 2024 + (30 - i) / 12
            val record = RecordUi(bench, 80.0 + i, 5, previousKg = 79.0 + i, workoutId = "m$i", date = end(year, month, 3))
            workout("m$i", at(year, month, 3), bench to listOf(80.0 + i to 5), records = listOf(record))
        }.sortedByDescending { it.startedAt }
        // A first record in October, newer than every beaten one (the newest in June): hidden without the chip.
        val squatFirst = RecordUi(squat, 100.0, 5, workoutId = "sq", date = end(2026, 10, 10))
        val history = listOf(workout("sq", at(2026, 10, 10), squat to listOf(100.0 to 5), records = listOf(squatFirst))) + many
        val gate = GatedContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            show(mutableStateOf(stateOf(history = history, records = emptyList())), cache = ProgressIndexCache(scope, gate))
            waitForRows()
            assertEquals("junho de 2026 · 1 recorde", headers().first())
            onNodeWithTag(RecordsScreenTag).performScrollToIndex(20)
            waitForIdle()

            gate.hold = CompletableDeferred()
            onNodeWithTag(RecordsTags.FIRST).performClick()
            waitForIdle()
            gate.hold?.complete(Unit)
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(rowTag(squatFirst)).fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            // On screen, inside the list: a header placed above it (in the list's top padding) is not shown.
            onNodeWithTag(RecordsTags.month(2026, 10)).assertIsDisplayed()
            onNodeWithTag(rowTag(squatFirst)).assertIsDisplayed()
            val list = onNodeWithTag(RecordsScreenTag).getUnclippedBoundsInRoot()
            val october = onNodeWithTag(RecordsTags.month(2026, 10)).getUnclippedBoundsInRoot()
            assertTrue(october.top >= list.top, "the October header is at the list's top: ${october.top} vs ${list.top}")
        } finally {
            gate.hold?.complete(Unit)
            scope.cancel()
        }
    }

    /** Runs on [Dispatchers.Default], each block first waiting for [hold] when it is set. */
    private class GatedContext : CoroutineDispatcher() {
        @Volatile
        var hold: CompletableDeferred<Unit>? = null

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            val held = hold
            Dispatchers.Default.dispatch(context, Runnable {
                held?.let { runBlocking { it.await() } }
                block.run()
            })
        }
    }

    /** Final review of phase 5A (CR-4): the tab left open over midnight reads its dates in the new day. */
    @Test
    fun midnightMovesTodayForTheSpokenDates() = runComposeUiTest {
        val midnight = LocalDateTime(2027, 1, 1, 0, 0).toInstant(utc).toEpochMilliseconds()
        var clock = midnight - 1_000
        show(seams = ProgressSeams(nowMillis = { clock }, zone = { utc }))
        waitForRows()
        // Still 2026: a date of this year is read without its year.
        assertTrue(spoken(benchW875).endsWith("28 de outubro, Atual"), spoken(benchW875))

        clock = midnight + 1_000
        mainClock.advanceTimeBy(1_500)
        waitUntil(timeoutMillis = 5_000) { spoken(benchW875).endsWith("28 de outubro de 2026, Atual") }
    }

    @Test
    fun rulesSheetListsTheFourRulesAtTwoX() = runComposeUiTest {
        show(fontScale = LINEAR_TWO_X_FONT_SCALE, size = DpSize(320.dp, 640.dp))
        onNodeWithTag(RecordsTags.RULES).assert(hasContentDescriptionExactly("Como contamos recordes"))
        onNodeWithTag(RecordsTags.RULES).performClick()
        waitForTag(RecordsTags.RULES_SHEET)
        val inSheet = hasAnyAncestor(hasTestTag("sheet"))
        onNode(hasText("Como contamos recordes") and inSheet, useUnmergedTree = true).assertExists()
        val rules = listOf(
            "Carga: a série de trabalho mais pesada.",
            "Repetições: mais repetições na mesma carga, inclusive peso corporal.",
            "Aquecimento e séries incompletas não contam.",
            "Séries assistidas têm recordes à parte.",
        )
        // The sheet scrolls (a long text at 2x in a short window).
        onNodeWithTag(RecordsTags.RULES_SHEET).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        rules.forEachIndexed { i, text ->
            val tag = RecordsTags.rule(i)
            onNodeWithTag(RecordsTags.RULES_SHEET).performScrollToNode(hasTestTag(tag))
            onNode(hasTestTag(tag) and inSheet, useUnmergedTree = true).assertTextEquals(text).assertIsDisplayed()
            // The sheet's window (MOverlayWindow) takes the host's 2x, not the system's text size.
            val layout = textLayout(tag)
            assertEquals(LINEAR_TWO_X_FONT_SCALE, layout.layoutInput.density.fontScale, 0.001f, tag)
        }
    }

    private fun ComposeUiTest.textLayout(tag: String): TextLayoutResult {
        val node = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!(results) && results.isNotEmpty(), "$tag has no layout")
        return results.first()
    }

    @Test
    fun settingsShortcutCallsBack() = runComposeUiTest {
        val calls = show()
        onNodeWithTag(RecordsTags.SETTINGS).assert(hasContentDescriptionExactly("Configurações"))
        onNodeWithTag(RecordsTags.SETTINGS).performClick()
        waitForIdle()
        assertEquals(1, calls.settings)
        assertTrue(calls.progress.isEmpty() && calls.workouts.isEmpty())
    }

    @Test
    fun stateSurvivesRecreation() = runComposeUiTest {
        // Many bench load records, one a month, so the list scrolls. No cache of the test's own: the recreated screen
        // builds the index again, as after a process death, and the scroll must wait for the list instead of being lost.
        val many = (1..30).map { i ->
            val month = 1 + (30 - i) % 12
            val year = 2024 + (30 - i) / 12
            val record = RecordUi(bench, 80.0 + i, 5, previousKg = 79.0 + i, workoutId = "m$i", date = end(year, month, 3))
            workout("m$i", at(year, month, 3), bench to listOf(80.0 + i to 5), records = listOf(record))
        }.sortedByDescending { it.startedAt }
        val restoration = screenRestoration()
        // In the system language: leaving a language override rebuilds the theme's tree, which a recreation would
        // then lose along with the state it is meant to prove. The search is bilingual.
        show(mutableStateOf(stateOf(history = many, records = emptyList())), restoration = restoration, locale = null)
        waitForRows()
        search("supino")
        onNodeWithTag(RecordsTags.FIRST).performClick()
        waitForIdle()
        onNodeWithTag(RecordsScreenTag).performScrollToIndex(20)
        waitForIdle()
        val top = rows().first()

        restoration.emulateSaveAndRestore()
        waitForRows()
        waitForIdle()
        assertEquals("supino", onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        onNodeWithTag(RecordsTags.FIRST).assertIsOn()
        onNodeWithTag(RecordsTags.CURRENT_ONLY).assertIsOff()
        assertEquals(top, rows().first())

        // A new choice starts the list from the top again.
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitForIdle()
        onNodeWithTag(RecordsTags.CURRENT_ONLY).assertIsOn()
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitUntil(timeoutMillis = 5_000) { rows().firstOrNull() == rowTag(many.first().records.single()) }

        // "Só atuais" and the open rules sheet come back too.
        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
        waitForTag(RecordsTags.NONE_FOUND)
        onNodeWithTag(RecordsTags.RULES).performClick()
        waitForTag(RecordsTags.RULES_SHEET)
        restoration.emulateSaveAndRestore()
        waitForTag(RecordsTags.RULES_SHEET)
        waitForTag(RecordsTags.NONE_FOUND)
        onNodeWithTag(RecordsTags.CURRENT_ONLY).assertIsOn()
        onNodeWithTag(RecordsTags.FIRST).assertIsOn().assertIsNotEnabled()
    }

    @Test
    fun textsWholeAtTwoX() {
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            for (onlyCurrent in listOf(false, true)) {
                runComposeUiTest {
                    show(fontScale = scale, size = DpSize(320.dp, 800.dp))
                    waitForRows()
                    // With "Só atuais", a first record's value is a number alone ("100 kg × 5").
                    if (onlyCurrent) {
                        onNodeWithTag(RecordsTags.CURRENT_ONLY).performClick()
                        waitUntil(timeoutMillis = 5_000) { rows().size >= 2 }
                    }
                    val listTag = RecordsScreenTag
                    for (record in if (onlyCurrent) listOf(benchW875, squatFirst) else listOf(benchW875, benchR8)) {
                        onNodeWithTag(listTag).performScrollToNode(hasTestTag(rowTag(record)))
                        waitForIdle()
                        assertShownWhole(partLayout(record, RecordsTags.ROW_KIND), "kind at $scale")
                        assertShownWhole(partLayout(record, RecordsTags.ROW_CURRENT), "current at $scale")
                        assertShownWhole(partLayout(record, RecordsTags.ROW_DATE), "date at $scale")
                    }
                    if (onlyCurrent) assertNumbersWhole(atLeast = 1)
                }
                LocalAppLocale.restore()
            }
        }
    }

    private fun ComposeUiTest.partLayout(record: RecordUi, partTag: String): TextLayoutResult {
        val node = part(record, partTag).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!(results) && results.isNotEmpty(), "$partTag has no layout")
        return results.first()
    }

    @Test
    fun unrelatedStateChangesDoNotRecomposeTheRows() = runComposeUiTest {
        val counts = HashMap<String, Int>()
        val state = mutableStateOf(stateOf())
        show(state, tall = true, probe = { key -> counts[key] = (counts[key] ?: 0) + 1 })
        waitForRows()
        waitForIdle()
        val key = RecordsTags.row(RecordTimelines.key(RecordEvent(benchW875, "", 0, current = false)))
        val before = counts[key] ?: 0
        assertTrue(before > 0, "the probe counts the row")
        // A routine and the timer change: nothing the rows show.
        state.value = state.value.copy(routines = listOf(RoutineUi("r1", "Treino A")))
        waitForIdle()
        state.value = state.value.copy(timer = state.value.timer.copy(remainingSeconds = 42))
        waitForIdle()
        assertEquals(before, counts[key] ?: 0, "an unrelated change recomposed the row")
        // Control: the unit is something the rows show.
        state.value = state.value.copy(preferences = state.value.preferences.copy(pounds = true))
        waitForIdle()
        assertTrue((counts[key] ?: 0) > before, "the unit change should recompose the row")
        part(benchW875, RecordsTags.ROW_VALUE).assertTextEquals("192,9 lb × 6 · antes 187,39 lb".nb())
    }

    private fun hasText(text: String) = androidx.compose.ui.test.hasText(text)

    private fun hasContentDescriptionExactly(text: String) = androidx.compose.ui.test.hasContentDescriptionExactly(text)

    private companion object {
        const val HOUR = 3_600_000L
    }
}
