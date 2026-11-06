package com.mooscles.screens.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.charts.calendarDayTag
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.assertClickablesLabeledAndSized
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.history.HistoryContent
import com.mooscles.screens.history.HistoryScreenTag
import com.mooscles.screens.progress.LocalProgressIndexCache
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.screens.progress.ProgressSeams
import com.mooscles.screens.records.RecordsRoute
import com.mooscles.screens.records.RecordsScreenTag
import com.mooscles.screens.share.ShareCardData
import com.mooscles.screens.share.ShareCards
import com.mooscles.screens.share.SharePreviewDestination
import com.mooscles.screens.share.SharePreviewSheet
import com.mooscles.screens.share.SharePreviewState
import com.mooscles.screens.share.rememberShareCardTexts
import com.mooscles.screens.shell.AppShellBottom
import com.mooscles.screens.shell.ShellTabs
import com.mooscles.screens.withScreen
import com.mooscles.screens.workout.workoutSetRowTag
import com.mooscles.screens.workoutdetail.WorkoutDetailRoute
import com.mooscles.screens.workoutdetail.WorkoutDetailTags
import com.mooscles.ui.RecordKind
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * The Phase 5B History path over real Room, DataStore and MoosclesViewModel, on both UI platforms.
 *
 * A fixed November calendar opens a saved workout; editing its set changes the saved weight, its
 * historical PR and the cached overall volume. The updated workout is then passed through the real
 * ShareCards snapshot mapper to the common preview. The page slot deliberately displays that model:
 * Android PNG encoding, FileProvider permissions and real share intents remain Android integration
 * checks, and no recipient or external application is contacted by this flow.
 *
 * Another active workout, its exact draft values and its paused rest timer must survive every step.
 */
@OptIn(ExperimentalTestApi::class)
class HistoryFlowTest {
    @AfterTest
    fun restoreLocale() {
        LocalAppLocale.restore()
    }

    @Test
    fun historyFlowPortugueseLightAtOneX() = runComposeUiTest(testTimeout = 6.minutes) {
        historyFlow(locale = "pt-BR", dark = false, fontScale = 1f)
    }

    @Test
    fun historyFlowEnglishDarkAtTwoX() = runComposeUiTest(testTimeout = 6.minutes) {
        historyFlow(locale = "en", dark = true, fontScale = LINEAR_TWO_X_FONT_SCALE)
    }

    private suspend fun ComposeUiTest.historyFlow(locale: String, dark: Boolean, fontScale: Float) {
        val harness = FlowHarness()
        try {
            val seed = seedHistory(harness)
            val vm = harness.createViewModel()
            val screen = mutableStateOf(ShellTabs.HISTORY)
            val selectedWorkout = mutableStateOf<String?>(null)
            val share = mutableStateOf<String?>(null)
            val destinations = mutableListOf<SharePreviewDestination>()
            withScreen(locale = locale, dark = dark, fontScale = fontScale, reducedMotion = true) {
                HistoryFlowApp(harness, vm, screen, selectedWorkout, share, destinations)
            }
            await("the saved history and active session load") {
                !vm.contentUi.value.loading && vm.contentUi.value.history.size == 2 && vm.contentUi.value.active?.id == seed.activeId
            }
            await("the paused timer loads") { vm.timerUi.value.paused }
            val activeBefore = requireNotNull(vm.contentUi.value.active)
            val timerBefore = vm.timerUi.value
            val beforeSet = activeBefore.exercises.flatMap { it.sets }.single { it.id == seed.activeSetId }
            assertEquals(22.123456789, beforeSet.weightKg)

            // A one-workout day opens its saved detail directly, without writing training state.
            await("the calendar day appears") { exists(hasTestTag(calendarDayTag(3))) }
            onNodeWithTag(HistoryScreenTag).performScrollToNode(hasTestTag(calendarDayTag(3)))
            onNodeWithTag(calendarDayTag(3)).assertIsDisplayed()
            capture("phase5b-history-calendar-$locale")
            onNodeWithTag(calendarDayTag(3)).performClick()
            capture("phase5b-history-after-day-$locale")
            await("the saved detail opens") { selectedWorkout.value == seed.latestId && screen.value == DETAIL }
            detailScroll(WorkoutDetailTags.PRS)
            onNodeWithTag(WorkoutDetailTags.PRS).assertIsDisplayed()
            sweep(locale, "detail-before")

            // Confirmation precedes editing; entering an exact new saved weight recalculates PRs.
            detailScroll(WorkoutDetailTags.EDIT)
            onNodeWithTag(WorkoutDetailTags.EDIT).performClick()
            onAllNodesWithText(if (locale == "pt-BR") "Editar treino" else "Edit workout").onLast().performClick()
            await("the saved editor is active") { exists(hasTestTag(WorkoutDetailTags.DONE)) }
            detailScroll(workoutSetRowTag(seed.latestSetId))
            val load = hasSetTextAction() and hasAnyAncestor(hasTestTag(SetRowLoadTag)) and
                hasAnyAncestor(hasTestTag(workoutSetRowTag(seed.latestSetId)))
            onNode(load, useUnmergedTree = true).performClick()
            onNode(load, useUnmergedTree = true).performClick()
            onNode(load, useUnmergedTree = true).performTextReplacement("21")
            await("the ViewModel echoes the saved weight and its new PR") {
                val latest = vm.contentUi.value.history.firstOrNull { it.id == seed.latestId }
                latest != null && latest.exercises.flatMap { it.sets }.singleOrNull { it.id == seed.latestSetId }?.weightKg == 21.0 &&
                    latest.records.any { it.kind == RecordKind.WEIGHT && it.exercise.id == BENCH_PRESS && it.weightKg == 21.0 && it.previousKg == 20.0 }
            }
            val saved = harness.repository.snapshot().sets.single { it.id == seed.latestSetId }
            assertEquals(21.0, saved.weightKg)
            assertEquals(6, saved.reps)
            assertTrue(saved.completed)
            onNodeWithTag(WorkoutDetailTags.DONE).performClick()
            detailScroll(WorkoutDetailTags.PRS)
            sweep(locale, "detail-edited")

            // The actual Records UI shows the recomputed beaten record at the new weight.
            onNodeWithTag("tab-${ShellTabs.RECORDS}").performClick()
            await("the Records tab opens") { screen.value == ShellTabs.RECORDS }
            val record = vm.contentUi.value.history.single { it.id == seed.latestId }.records.single {
                it.kind == RecordKind.WEIGHT && it.exercise.id == BENCH_PRESS
            }
            val recordTag = recordRowTag(record)
            await("the recalculated record is on the timeline") {
                runCatching { onNodeWithTag(RecordsScreenTag).performScrollToNode(hasTestTag(recordTag)) }.isSuccess && exists(hasTestTag(recordTag))
            }
            val recordDescription = onNodeWithTag(recordTag).fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            assertTrue("21" in recordDescription, "The timeline must expose the recalculated 21 kg record: $recordDescription")
            sweep(locale, "records-recalculated")

            // History shares the same updated index; the two workouts' volume is 20×7 + 21×6.
            onNodeWithTag("tab-${ShellTabs.HISTORY}").performClick()
            await("History returns") { screen.value == ShellTabs.HISTORY }
            onNodeWithTag("history-segment-progress").performClick()
            await("the overall statistics load") { exists(hasTestTag("history-period-stats")) }
            onNodeWithTag("history-progress-list").performScrollToNode(hasTestTag("history-period-stats"))
            val statsText = allText(hasTestTag("history-period-stats"))
            assertTrue("266" in statsText, "The aggregate uses the edited saved set: $statsText")
            sweep(locale, "overall-edited", numbers = 4)

            // Return through the same calendar and open the shared preview from saved detail.
            onNodeWithTag("history-segment-workouts").performClick()
            await("the calendar returns") { exists(hasTestTag(calendarDayTag(3))) }
            onNodeWithTag(calendarDayTag(3)).performClick()
            await("the same saved detail returns") { screen.value == DETAIL }
            detailScroll("share-workout")
            onNodeWithTag("share-workout").performClick()
            await("the common preview receives the saved card") { exists(hasTestTag("history-flow-card")) }
            val previewText = allText(hasTestTag("history-flow-card"))
            assertTrue("126" in previewText, "The card's saved workout volume is 21×6: $previewText")
            val previewDescription = onNodeWithTag("history-flow-card").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
            assertTrue("1" in previewDescription, "The preview describes the same inclusive PR count: $previewDescription")
            assertTrue(destinations.isEmpty(), "Opening a preview must not send a share action")
            sweep(locale, "share-preview")
            onNodeWithTag("share-preview-close").performClick()
            await("closing returns to saved detail") { share.value == null && !exists(hasTestTag("history-flow-card")) }

            // Viewing and editing history never changes the other session or rest timer.
            val activeAfter = requireNotNull(vm.contentUi.value.active)
            assertEquals(seed.activeId, activeAfter.id)
            assertEquals(beforeSet, activeAfter.exercises.flatMap { it.sets }.single { it.id == seed.activeSetId })
            assertEquals(timerBefore, vm.timerUi.value)
            assertTrue(vm.timerUi.value.paused)
            assertFalse(vm.timerUi.value.running)
            assertTrue(destinations.isEmpty())
        } finally {
            runOnUiThread { harness.close() }
        }
    }

    private fun ComposeUiTest.detailScroll(tag: String) {
        onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(tag))
        waitForIdle()
    }

    private fun ComposeUiTest.allText(ancestor: SemanticsMatcher): String =
        onAllNodes(ancestor or hasAnyAncestor(ancestor), useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }.joinToString(" | ") { it.text }

    private fun ComposeUiTest.sweep(locale: String, state: String, numbers: Int = 0) {
        waitForIdle()
        assertClickablesLabeledAndSized("history $locale $state")
        assertNumbersWhole(atLeast = numbers)
        capture("history-${if (locale == "pt-BR") "pt-light-1x" else "en-dark-2x"}-$state")
    }

    private suspend fun seedHistory(harness: FlowHarness): HistorySeed {
        val repository = harness.repository
        repository.initialize()
        suspend fun finished(name: String, day: Int, kg: Double, reps: Int): Pair<String, String> {
            val start = LocalDateTime(2026, 11, day, 10, 0).toInstant(UTC).toEpochMilliseconds()
            val id = repository.startEmpty(name, now = start)
            repository.addWorkoutExercise(id, BENCH_PRESS)
            val set = repository.workoutSets(id).first()
            repository.updateSet(set.copy(weightKg = kg, reps = reps, completed = true))
            check(repository.finishWorkout(id, now = start + 3_600_000))
            return id to set.id
        }
        finished("Earlier workout", 1, 20.0, 7)
        val latest = finished("Latest workout", 3, 22.0, 6)
        val activeId = repository.startEmpty("Keep this workout", now = NOW)
        val activeEntry = repository.addWorkoutExercise(activeId, BENCH_PRESS)
        val activeSet = repository.workoutSets(activeId).first()
        repository.updateSet(activeSet.copy(weightKg = 22.123456789, reps = 7, completed = false))
        harness.timer.start("Keep this rest", 120, activeEntry)
        harness.timer.pause()
        return HistorySeed(latest.first, latest.second, activeId, activeSet.id)
    }

    private class HistorySeed(val latestId: String, val latestSetId: String, val activeId: String, val activeSetId: String)

    private companion object {
        const val DETAIL = "saved-detail"
        val UTC = TimeZone.UTC
        val NOW = LocalDateTime(2026, 11, 4, 12, 0).toInstant(UTC).toEpochMilliseconds()
    }
}

/** No native navigation or export: this host switches the real common screens under their actual shell. */
@Composable
private fun HistoryFlowApp(
    harness: FlowHarness,
    vm: FlowViewModel,
    screen: MutableState<String>,
    selectedWorkout: MutableState<String?>,
    share: MutableState<String?>,
    destinations: MutableList<SharePreviewDestination>,
) {
    val ui by vm.contentUi.collectAsState()
    val timer = vm.timerUi.collectAsState()
    val scope = rememberCoroutineScope()
    val cache = remember(scope) { ProgressIndexCache(scope) }
    val retained = rememberSaveableStateHolder()
    val seams = remember { ProgressSeams({ LocalDateTime(2026, 11, 4, 12, 0).toInstant(TimeZone.UTC).toEpochMilliseconds() }, { TimeZone.UTC }, { DayOfWeek.SUNDAY }) }
    CompositionLocalProvider(LocalProgressIndexCache provides cache) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                retained.SaveableStateProvider(if (screen.value == "saved-detail") "detail:${selectedWorkout.value}" else screen.value) {
                    when (screen.value) {
                        ShellTabs.RECORDS -> RecordsRoute(ui, vm, onOpenProgress = { _, _ -> }, onOpenWorkout = {
                            selectedWorkout.value = it; screen.value = "saved-detail"
                        }, onOpenSettings = {})
                        "saved-detail" -> WorkoutDetailRoute(requireNotNull(selectedWorkout.value), ui, vm,
                            onBack = { screen.value = ShellTabs.HISTORY }, onExerciseProgress = {}, onRoutine = {},
                            onShare = { share.value = it })
                        else -> HistoryContent(ui, vm, onOpenWorkout = {
                            selectedWorkout.value = it; screen.value = "saved-detail"
                        }, onOpenSettings = {}, onOpenTrain = {}, onOpenProgress = {}, seams = seams)
                    }
                }
            }
            AppShellBottom(ui, timer, selectedTab = if (screen.value == ShellTabs.RECORDS) ShellTabs.RECORDS else ShellTabs.HISTORY,
                onSelectTab = { screen.value = it }, onReselectTab = { screen.value = it }, onOpenWorkout = {}, actions = vm,
                imeInsets = WindowInsets(0, 0, 0, 0))
        }
        share.value?.let { workoutId ->
            val texts = rememberShareCardTexts()
            val locale = currentNumberLocale()
            var card by remember(workoutId, texts, locale) { mutableStateOf<ShareCardData?>(null) }
            LaunchedEffect(workoutId, texts, locale) {
                val snapshot = harness.repository.snapshot()
                card = withContext(Dispatchers.Default) { ShareCards.from(snapshot, workoutId, ui.preferences.pounds, locale, TimeZone.UTC, texts) }
            }
            SharePreviewSheet(card?.let { SharePreviewState.Ready(1, it.prCount, workoutId) } ?: SharePreviewState.Loading,
                emptySet(), false, onRetry = {}, onDismiss = { share.value = null }, onShare = { destinations += it },
                page = { _, description, onLoaded, _ ->
                    SideEffect { onLoaded() }
                    val ready = requireNotNull(card)
                    Column(Modifier.fillMaxWidth().testTag("history-flow-card").semantics(mergeDescendants = true) { contentDescription = description }) {
                        MText(ready.workoutName, MTheme.type.titleMd)
                        MText(ready.setsText, MTheme.type.numMd)
                        MText(ready.prsText, MTheme.type.numMd)
                        MText(ready.volumeText, MTheme.type.numMd)
                    }
                })
        }
    }
}
