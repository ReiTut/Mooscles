package com.mooscles.screens.exercise

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.mooscles.data.ExerciseCatalog
import com.mooscles.screens.exercise.figures.ExerciseFigures
import com.mooscles.data.NameRule
import com.mooscles.designsystem.components.ExerciseEquipmentIconTag
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.illustration.AnimatedFigureTag
import com.mooscles.designsystem.illustration.BodyMapLegendTag
import com.mooscles.designsystem.illustration.BodyMapTag
import com.mooscles.designsystem.illustration.FigureFrameKey
import com.mooscles.designsystem.illustration.prepareBodyMap
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.designsystem.tokens.MoosclesTiming
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.assertTextNotCut
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.PlannedExerciseUi
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.UiError
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The exercise detail (spec "Detalhe do exercício"), in pt-BR. */
@OptIn(ExperimentalTestApi::class)
class ExerciseInfoScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val hold = MoosclesTiming().illustrationFrame
    private val fade = MoosclesMotion().base.toLong()

    /** From the start of the alternation (the end frame shown) to the middle of the start frame's hold. */
    private val toStart = hold + fade + hold / 2

    /** From the middle of one frame's hold to the middle of the next. */
    private val toNext = hold + fade

    // Pilot exercises (with a figure) and one still without a figure.
    private val bench = catalog("barbell_bench_flat")
    private val squat = catalog("barbell_back_squat_high_bar")
    private val curl = catalog("dumbbell_curl_standing")
    private val frontSquat = catalog("barbell_front_squat")
    private val mine = ExerciseUi("custom-ze", "Supino articulado do Zé", "Chest", "Plate-loaded machine", custom = true)
    private val exercises = listOf(bench, squat, curl, frontSquat, mine)

    private fun catalog(stableId: String): ExerciseUi {
        val entry = ExerciseCatalog.exercises.single { it.id == "catalog_$stableId" }
        return ExerciseUi(entry.id, entry.name, entry.muscle.label, entry.equipment.label, entry.convention.label)
    }

    /** The view model: records every call and clears the error as the real one does. */
    private class Fake(val state: MutableState<UiState>, private val recording: RecordingActions = RecordingActions()) :
        MoosclesActions by recording {
        val calls: List<String> get() = recording.calls

        override fun clearError() {
            recording.clearError()
            state.value = state.value.copy(error = null, errorCode = null)
        }
    }

    /** What the route asked of the app. */
    private class Requests {
        var back = 0
        val history = mutableListOf<String>()
        val routines = mutableListOf<String>()
        var workout = 0
    }

    private fun stateOf(
        history: List<WorkoutUi> = emptyList(),
        records: List<RecordUi> = emptyList(),
        routines: List<RoutineUi> = emptyList(),
        active: WorkoutUi? = null,
    ) = mutableStateOf(UiState(loading = false, exercises = exercises, history = history, records = records, routines = routines, active = active))

    private fun ComposeUiTest.show(
        exercise: ExerciseUi = bench,
        state: MutableState<UiState> = stateOf(),
        reducedMotion: Boolean = false,
        foreground: MutableState<Boolean> = mutableStateOf(true),
        fontScale: Float = 1f,
        size: DpSize? = null,
        probe: ((String) -> Unit)? = null,
        idle: Boolean = true,
        fake: Fake = Fake(state),
        exerciseId: String = exercise.id,
        photos: FakeExercisePhotos? = null,
    ): Requests {
        val requests = Requests()
        withScreen(locale = "pt-BR", reducedMotion = reducedMotion, fontScale = fontScale, size = size, probe = probe) {
            val route: @androidx.compose.runtime.Composable () -> Unit = {
                ExerciseInfoRoute(
                    exerciseId = exerciseId,
                    state = state.value,
                    actions = fake,
                    onBack = { requests.back++ },
                    onOpenHistory = { requests.history += it },
                    onOpenRoutine = { requests.routines += it },
                    onOpenWorkout = { requests.workout++ },
                    modifier = Modifier,
                    foreground = foreground.value,
                )
            }
            if (photos != null) photos.Provide(route) else route()
        }
        if (idle) waitForIdle()
        return requests
    }

    private fun ComposeUiTest.frame(): String = onNodeWithTag(AnimatedFigureTag).fetchSemanticsNode().config[FigureFrameKey]

    private fun ComposeUiTest.top(tag: String): Dp = onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().top

    private fun ComposeUiTest.textOf(node: SemanticsNodeInteraction): String {
        val config = node.fetchSemanticsNode().config
        return config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty().replace(' ', ' ')
    }

    private fun ComposeUiTest.mergedText(tag: String): String =
        onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }.orEmpty().replace(' ', ' ')

    private fun ComposeUiTest.exists(tag: String): Boolean = onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.toastWith(text: String) = onNode(hasText(text) and hasAnyAncestor(hasTestTag(TOAST)), useUnmergedTree = true)

    private fun ComposeUiTest.tap(tag: String) {
        onNodeWithTag(tag).performScrollTo().performClick()
        waitForIdle()
    }

    /** The cues and the body map in memory, as after the library opened: both draw in their first frame. */
    private fun preload() = runBlocking {
        ExerciseCues.load(portuguese = true)
        prepareBodyMap()
    }

    private fun ComposeUiTest.waitForCues() = waitUntil(timeoutMillis = 5_000) { exists(ExerciseInfoTags.CUES) }

    @Test
    fun readingOrderIsNameMediaMetaMusclesTextCuesHistoryActions() = runComposeUiTest {
        preload()
        show(state = stateOf(active = WorkoutUi("w9", "Treino", startedAt = 1)))
        waitForCues()
        // The figure is an image named after the exercise; the map has no node a reader stops on.
        onNodeWithTag(AnimatedFigureTag).assertContentDescriptionEquals("Ilustração de início e fim: Supino reto com barra")
        val map = onNodeWithTag(BodyMapTag, useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(SemanticsProperties.ContentDescription !in map && SemanticsProperties.Text !in map, "the map speaks: $map")
        assertTrue(!map.isMergingSemanticsOfDescendants, "the map is a node of its own: $map")
        // One column, nothing reordered: the reader goes top to bottom.
        val order = listOf(
            ExerciseInfoTags.TITLE, ExerciseInfoTags.MEDIA, ExerciseInfoTags.META, ExerciseInfoTags.MUSCLE_TEXT,
            ExerciseInfoTags.CUES, ExerciseInfoTags.HISTORY, ExerciseInfoTags.ADD_ROUTINE, ExerciseInfoTags.ADD_WORKOUT,
        )
        val tops = order.map { top(it) }
        assertEquals(tops.sorted(), tops, "tops of $order: $tops")
        for (tag in order) {
            val config = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config
            assertTrue(SemanticsProperties.TraversalIndex !in config && SemanticsProperties.IsTraversalGroup !in config, "$tag reorders: $config")
        }
        onNodeWithTag(ExerciseInfoTags.TITLE).assertTextEquals("Supino reto com barra")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /** The section titles start where the name, the chips and the cards start (16 dp), not indented a second time. */
    @Test
    fun sectionTitlesLineUpWithTheContent() = runComposeUiTest {
        preload()
        show()
        waitForCues()
        val start = onNodeWithTag(ExerciseInfoTags.TITLE).getUnclippedBoundsInRoot().left
        for (title in listOf("Músculos", "Como fazer", "Seu histórico")) {
            val node = onNodeWithText(title).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            val left = node.getUnclippedBoundsInRoot().left
            assertTrue((left - start).value in -0.5f..0.5f, "$title starts at $left, the content at $start")
        }
    }

    @Test
    fun pilotExerciseShowsTheAlternatingFigure() = runComposeUiTest {
        // Stopped before the figure first composes: with the clock running, the test policy stops it for good.
        mainClock.autoAdvance = false
        show(idle = false)
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame())
        mainClock.advanceTimeBy(toNext)
        assertEquals("end", frame())
        // While it alternates, a tap does nothing: no click action.
        assertEquals(null, onNodeWithTag(AnimatedFigureTag).fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick))
        // The frame is a square as wide as the content, up to 360 dp.
        val media = onNodeWithTag(ExerciseInfoTags.MEDIA).getUnclippedBoundsInRoot()
        assertTrue(media.width <= 360.dp + 0.5.dp && (media.width - media.height).value in -0.5f..0.5f, "media $media")
        mainClock.autoAdvance = true
    }

    @Test
    fun exerciseWithoutAFigureShowsTheEquipmentPlaceholder() {
        // Every catalog exercise has a figure but those left on the equipment icon on purpose: take one of
        // them, and the custom one.
        val figureless = ExerciseCatalog.exercises.first { it.id.removePrefix("catalog_") in ExerciseFigures.NO_FIGURE }
        assertEquals(null, ExerciseFigures.forExercise(figureless.id), "${figureless.id} has no figure")
        for (exercise in listOf(catalog(figureless.id.removePrefix("catalog_")), mine)) {
            runComposeUiTest {
                show(exercise, state = mutableStateOf(stateOf().value.let { it.copy(exercises = it.exercises + exercise) }))
                onNode(hasTestTag(ExerciseEquipmentIconTag) and hasAnyAncestor(hasTestTag(ExerciseInfoTags.MEDIA)), useUnmergedTree = true)
                    .assertIsDisplayed()
                onNodeWithTag(AnimatedFigureTag).assertDoesNotExist()
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun reducedMotionStopsOnTheEndAndATapToggles() = runComposeUiTest {
        show(reducedMotion = true)
        assertEquals("end", frame())
        val figure = onNodeWithTag(AnimatedFigureTag)
        assertEquals("Mostrar o início do movimento", figure.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        figure.performClick()
        waitForIdle()
        assertEquals("start", frame())
        assertEquals("Mostrar o fim do movimento", figure.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    }

    @Test
    fun notForegroundStopsTheFigure() = runComposeUiTest {
        val foreground = mutableStateOf(false)
        mainClock.autoAdvance = false
        show(foreground = foreground, idle = false)
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        mainClock.advanceTimeBy(toStart)
        assertEquals("end", frame(), "the figure alternates in the background")
        foreground.value = true
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeBy(toStart)
        assertEquals("start", frame(), "the figure does not alternate again in the foreground")
        foreground.value = false
        mainClock.advanceTimeByFrame()
        assertEquals("end", frame())
        mainClock.advanceTimeBy(toNext)
        assertEquals("end", frame())
        mainClock.autoAdvance = true
    }

    @Test
    fun onlyTheFigureRecomposesOnEachFrame() = runComposeUiTest {
        preload()
        val counts = mutableMapOf<String, Int>()
        mainClock.autoAdvance = false
        show(probe = { key -> counts[key] = (counts[key] ?: 0) + 1 }, idle = false)
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertTrue(exists(ExerciseInfoTags.CUES), "the cues in memory show in the first frames")
        val first = counts.toMap()
        assertTrue(ExerciseInfoProbe.BODY in first && ExerciseInfoProbe.FIGURE in first, "the probes report: $first")
        // Positive control (pre-flight F6): the figure's frame layer recomposes on each frame change,
        // which is what flips its frame key; four changes over two cycles.
        var flips = 0
        var shown = frame()
        repeat(4) {
            mainClock.advanceTimeBy(if (it == 0) toStart else toNext)
            val now = frame()
            if (now != shown) flips++
            shown = now
        }
        assertEquals(4, flips, "the figure's frames changed $flips times")
        assertEquals(first[ExerciseInfoProbe.BODY], counts[ExerciseInfoProbe.BODY], "the body recomposed with the figure: $counts")
        assertEquals(first[ExerciseInfoProbe.FIGURE], counts[ExerciseInfoProbe.FIGURE], "the media slot recomposed: $counts")
        mainClock.autoAdvance = true
    }

    @Test
    fun conventionCaptionOnlyWhenNotTotalLoad() {
        assertEquals("Total load", bench.convention)
        assertEquals("Per hand", curl.convention)
        runComposeUiTest {
            show(bench)
            for (chip in listOf("Barra", "Peito", "Carga total")) {
                onNode(hasText(chip) and hasAnyAncestor(hasTestTag(ExerciseInfoTags.META)), useUnmergedTree = true).assertIsDisplayed()
            }
            onNodeWithTag(ExerciseInfoTags.CAPTION).assertDoesNotExist()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            show(curl)
            onNode(hasText("Por mão") and hasAnyAncestor(hasTestTag(ExerciseInfoTags.META)), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(ExerciseInfoTags.CAPTION).assertTextEquals("Por mão: registre a carga de um halter.")
        }
    }

    @Test
    fun muscleTextSitsUnderTheMap() = runComposeUiTest {
        show(squat)
        val text = onNodeWithTag(ExerciseInfoTags.MUSCLE_TEXT).performScrollTo()
        text.assertTextEquals("Principais: quadríceps. Secundários: glúteos, adutores, lombar.")
        val map = onNodeWithTag(BodyMapTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val legend = onNodeWithTag(BodyMapLegendTag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val sentence = text.getUnclippedBoundsInRoot()
        assertTrue(map.bottom <= legend.top + 0.5.dp && legend.bottom <= sentence.top + 0.5.dp, "map $map, legend $legend, text $sentence")
        assertTrue(map.width <= 360.dp + 0.5.dp, "map $map")
        onNode(hasText("Músculos") and hasAnyAncestor(hasTestTag(ExerciseInfoTags.MUSCLES)), useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun cuesAreNumberedAndTheSectionHidesWithout() {
        val expected = runBlocking { ExerciseCues.cuesFor(ExerciseCues.load(portuguese = true), bench.id) }
        assertEquals(4, expected.size)
        runComposeUiTest {
            show(bench)
            waitForCues()
            onNode(hasText("Como fazer") and hasAnyAncestor(hasTestTag(ExerciseInfoTags.CUES)), useUnmergedTree = true)
                .performScrollTo().assertIsDisplayed()
            expected.forEachIndexed { index, cue ->
                assertEquals("${index + 1} $cue", mergedText(ExerciseInfoTags.cue(index)))
            }
            onNodeWithTag(ExerciseInfoTags.cue(expected.size)).assertDoesNotExist()
        }
        LocalAppLocale.restore()
        // A custom exercise has no cues: with the cues in memory, the section is not there from the first frame.
        runComposeUiTest {
            mainClock.autoAdvance = false
            show(mine, idle = false)
            mainClock.advanceTimeByFrame()
            onNodeWithTag(ExerciseInfoTags.MUSCLES).assertExists()
            onNodeWithTag(ExerciseInfoTags.CUES).assertDoesNotExist()
            mainClock.autoAdvance = true
            waitForIdle()
            onNodeWithTag(ExerciseInfoTags.CUES).assertDoesNotExist()
        }
    }

    /** Carry from Task 11: coming back to a detail, the cues already read are there in the first frame (never a null first). */
    @Test
    fun cuesInMemoryShowInTheFirstFrame() = runComposeUiTest {
        preload()
        mainClock.autoAdvance = false
        show(bench, idle = false)
        mainClock.advanceTimeByFrame()
        assertTrue(exists(ExerciseInfoTags.cue(0)), "the cues in memory are missing from the first frame")
        mainClock.autoAdvance = true
    }

    @Test
    fun historyCardWithoutHistory() = runComposeUiTest {
        show()
        onNodeWithTag(ExerciseInfoTags.HISTORY_NONE).performScrollTo().assertTextEquals("Você ainda não fez este exercício.")
        onNode(hasText("Seu histórico") and hasAnyAncestor(hasTestTag(ExerciseInfoTags.HISTORY)), useUnmergedTree = true).assertExists()
        onNodeWithTag(ExerciseInfoTags.HISTORY_LAST).assertDoesNotExist()
        onNodeWithTag(ExerciseInfoTags.HISTORY_BEST).assertDoesNotExist()
        onNodeWithTag(ExerciseInfoTags.HISTORY_SESSIONS).assertDoesNotExist()
        // Nothing more to see there; the menu keeps the entry.
        onNodeWithTag(ExerciseInfoTags.HISTORY_FULL).assertDoesNotExist()
    }

    @Test
    fun historyCardShowsLastBestAndSessions() = runComposeUiTest {
        val history = listOf(
            workout("w2", OCT_21, bench, SetUi("s3", reps = 5, weightKg = 60.0, completed = true, warmup = true), SetUi("s4", reps = 8, weightKg = 80.0, completed = true)),
            workout("w1", OCT_14, bench, SetUi("s1", reps = 3, weightKg = 100.0, completed = true)),
        )
        val records = listOf(RecordUi(bench, weightKg = 100.0, reps = 3, workoutId = "w1", date = OCT_14))
        show(state = stateOf(history = history, records = records))
        onNodeWithTag(ExerciseInfoTags.HISTORY_LAST).performScrollTo()
        assertEquals("Última vez 21 out · 80 kg × 8", mergedText(ExerciseInfoTags.HISTORY_LAST))
        assertEquals("Melhor 100 kg × 3", mergedText(ExerciseInfoTags.HISTORY_BEST))
        onNode(hasTestTag(PrBadgeTag) and hasAnyAncestor(hasTestTag(ExerciseInfoTags.HISTORY_BEST)), useUnmergedTree = true).assertExists()
        assertEquals("2 sessões", textOf(onNodeWithTag(ExerciseInfoTags.HISTORY_SESSIONS)))
    }

    @Test
    fun historyCardShowsASparklineFromTwoSessions() = runComposeUiTest {
        val history = listOf(
            workout("w2", OCT_21, bench, SetUi("s2", reps = 8, weightKg = 80.0, completed = true)),
            workout("w1", OCT_14, bench, SetUi("s1", reps = 8, weightKg = 70.0, completed = true)),
        )
        show(state = stateOf(history = history))
        waitUntil(timeoutMillis = 5_000) { exists(ExerciseInfoTags.HISTORY_SPARKLINE) }
        val node = onNodeWithTag(ExerciseInfoTags.HISTORY_SPARKLINE, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(emptyList(), node.children.filter { it.config.contains(SemanticsProperties.Text) || it.config.contains(SemanticsProperties.ContentDescription) })
        assertEquals("2 sessões", textOf(onNodeWithTag(ExerciseInfoTags.HISTORY_SESSIONS)))
    }

    @Test
    fun noSparklineWithOneSession() = runComposeUiTest {
        show(state = stateOf(history = listOf(workout("w1", OCT_14, bench, SetUi("s1", reps = 8, weightKg = 60.0, completed = true)))))
        waitForIdle()
        onNodeWithTag(ExerciseInfoTags.HISTORY_SPARKLINE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun viewProgressFromTheCardAndTheMenu() = runComposeUiTest {
        val requests = show(state = stateOf(history = listOf(workout("w1", OCT_14, bench, SetUi("s1", reps = 8, weightKg = 60.0, completed = true)))))
        onNodeWithTag(ExerciseInfoTags.HISTORY_FULL).assertTextEquals("Ver progresso")
        tap(ExerciseInfoTags.HISTORY_FULL)
        assertEquals(listOf(bench.id), requests.history)
        // The menu has it too, and closes.
        onNodeWithTag(ExerciseInfoTags.MENU).assertContentDescriptionEquals("Mais opções").performClick()
        waitForIdle()
        onNodeWithTag(ExerciseInfoTags.MENU_HISTORY).performClick()
        waitForIdle()
        assertEquals(listOf(bench.id, bench.id), requests.history)
        onNodeWithTag(ExerciseInfoTags.MENU_HISTORY).assertDoesNotExist()
    }

    @Test
    fun editIsOnlyForCustomExercises() {
        runComposeUiTest {
            show(bench)
            onNodeWithTag(ExerciseInfoTags.MENU).performClick()
            waitForIdle()
            onNodeWithTag(ExerciseInfoTags.MENU_HISTORY).assertIsDisplayed()
            onNodeWithTag(ExerciseInfoTags.MENU_EDIT).assertDoesNotExist()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            show(mine)
            onNodeWithTag(ExerciseInfoTags.MENU).performClick()
            waitForIdle()
            onNode(hasText("Editar exercício") and hasAnyAncestor(hasTestTag(ExerciseInfoTags.MENU_EDIT)), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(ExerciseInfoTags.MENU_EDIT).performClick()
            waitForIdle()
            onNodeWithTag(CustomExerciseFormTags.TITLE).assertTextEquals("Editar exercício")
            assertEquals(mine.name, onNodeWithTag(CustomExerciseFormTags.NAME).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
            onNodeWithTag(CustomExerciseFormTags.CANCEL).performClick()
            waitForIdle()
            onNodeWithTag(CustomExerciseFormTags.ROOT).assertDoesNotExist()
        }
    }

    @Test
    fun addToWorkoutOnlyWithAnActiveWorkout() {
        runComposeUiTest {
            show()
            onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE).assertExists()
            onNodeWithTag(ExerciseInfoTags.ADD_WORKOUT).assertDoesNotExist()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            val active = WorkoutUi("w9", "Treino", startedAt = 1, exercises = listOf(WorkoutExerciseUi("w9-0", squat)))
            val state = stateOf(active = active)
            val fake = Fake(state)
            val requests = show(state = state, fake = fake)
            onNodeWithTag(ExerciseInfoTags.ADD_WORKOUT).performScrollTo().assertTextEquals("Adicionar ao treino")
            // Two taps before the echo: one write.
            onNodeWithTag(ExerciseInfoTags.ADD_WORKOUT).performClick()
            onNodeWithTag(ExerciseInfoTags.ADD_WORKOUT).performClick()
            waitForIdle()
            assertEquals(listOf("addWorkoutExercise:w9,${bench.id}"), fake.calls.filter { it.startsWith("addWorkoutExercise:") })
            // Never on the tap: only once the workout shows the exercise.
            toastWith("Adicionado ao treino").assertDoesNotExist()
            state.value = state.value.copy(active = active.copy(exercises = active.exercises + WorkoutExerciseUi("w9-1", bench)))
            waitForIdle()
            toastWith("Adicionado ao treino").assertIsDisplayed()
            onNode(hasTestTag(TOAST_ACTION) and hasAnyAncestor(hasTestTag(TOAST)), useUnmergedTree = true).assertIsDisplayed()
            onNode(hasText("Abrir") and hasAnyAncestor(hasTestTag(TOAST_ACTION)), useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag(TOAST_ACTION, useUnmergedTree = true).performClick()
            waitForIdle()
            assertEquals(1, requests.workout)
        }
    }

    /** Review Focus 3: the state loses the exercise while the detail is open (a backup import, say). */
    @Test
    fun exerciseThatDisappearsShowsNotFound() = runComposeUiTest {
        // While the state loads, the exercise is not "missing" yet.
        val state = mutableStateOf(UiState())
        val requests = show(state = state)
        onNodeWithTag(ExerciseInfoTags.LOADING).assertExists()
        onNodeWithTag(ExerciseInfoTags.NOT_FOUND).assertDoesNotExist()
        state.value = stateOf().value
        waitForIdle()
        onNodeWithTag(ExerciseInfoTags.TITLE).assertTextEquals("Supino reto com barra")
        state.value = state.value.copy(exercises = exercises - bench)
        waitForIdle()
        onNodeWithTag(ExerciseInfoTags.NOT_FOUND).assertIsDisplayed()
        onNodeWithText("Exercício não encontrado").assertIsDisplayed()
        onNodeWithTag(ExerciseInfoTags.MEDIA).assertDoesNotExist()
        onNodeWithTag(ExerciseInfoTags.MENU).assertDoesNotExist()
        onNodeWithTag(ExerciseInfoTags.TITLE).assertDoesNotExist()
        // The detail stays open: going back is the person's choice.
        assertEquals(0, requests.back)
        // And back, when it returns (an import that brings it again).
        state.value = state.value.copy(exercises = exercises)
        waitForIdle()
        onNodeWithTag(ExerciseInfoTags.NOT_FOUND).assertDoesNotExist()
        onNodeWithTag(ExerciseInfoTags.TITLE).assertIsDisplayed()
    }

    @Test
    fun notFoundGoesBackToTheLibrary() = runComposeUiTest {
        val requests = show(exerciseId = "catalog_gone")
        onNodeWithText("Voltar à biblioteca").performClick()
        waitForIdle()
        assertEquals(1, requests.back)
        onNodeWithContentDescription("Voltar").performClick()
        waitForIdle()
        assertEquals(2, requests.back)
    }

    @Test
    fun addToRoutineToastsAfterTheEchoWithOpen() = runComposeUiTest {
        val routine = RoutineUi("r1", "Treino A", exercises = listOf(PlannedExerciseUi("p1", bench)))
        val state = stateOf(routines = listOf(routine))
        val fake = Fake(state)
        val requests = show(state = state, fake = fake)
        tap(ExerciseInfoTags.ADD_ROUTINE)
        onNodeWithTag(AddToRoutineSheetTags.routine("r1")).performClick()
        waitForIdle()
        assertEquals(listOf("addRoutineExercise:r1,${bench.id}"), fake.calls.filter { it.startsWith("addRoutineExercise:") })
        onNodeWithTag(AddToRoutineSheetTags.ROOT).assertDoesNotExist()
        toastWith("Adicionado a Treino A").assertDoesNotExist()
        // The routine already had the bench once: the echo is the second.
        state.value = state.value.copy(routines = listOf(routine.copy(exercises = routine.exercises + PlannedExerciseUi("p2", bench))))
        waitForIdle()
        toastWith("Adicionado a Treino A").assertIsDisplayed()
        onNode(hasText("Abrir") and hasAnyAncestor(hasTestTag(TOAST_ACTION)), useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(TOAST_ACTION, useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(listOf("r1"), requests.routines)
    }

    /** Review Focus 3: the routine is gone when the write runs; its error arrives before any echo. */
    @Test
    fun addToARoutineThatIsGoneShowsTheErrorNotTheToast() = runComposeUiTest {
        val routine = RoutineUi("r1", "Treino A")
        val state = stateOf(routines = listOf(routine))
        val fake = Fake(state)
        show(state = state, fake = fake)
        tap(ExerciseInfoTags.ADD_ROUTINE)
        onNodeWithTag(AddToRoutineSheetTags.routine("r1")).performClick()
        waitForIdle()
        state.value = state.value.copy(routines = emptyList(), error = "raw", errorCode = UiError.GENERIC, errorSubject = "r1", subjectFailures = mapOf("r1" to 1))
        waitForIdle()
        toastWith("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.").assertIsDisplayed()
        assertTrue("clearError:" in fake.calls, "${fake.calls}")
        // An error toast stays until it is closed, and the host shows one toast at a time: close it,
        // or a later "Added to" would only wait behind it.
        onNodeWithTag(TOAST_CLOSE, useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithTag(TOAST, useUnmergedTree = true).assertDoesNotExist()
        // The wait is dropped: a routine that later shows the exercise is not this add's echo.
        state.value = state.value.copy(routines = listOf(routine.copy(exercises = listOf(PlannedExerciseUi("p9", bench)))))
        waitForIdle()
        toastWith("Adicionado a Treino A").assertDoesNotExist()
    }

    /**
     * Pre-flight F10: an error about anything else, even a second one that replaces the first, leaves the wait alone,
     * so the "Added to" toast still comes with the echo.
     */
    @Test
    fun unrelatedErrorKeepsThePendingAddToast() = runComposeUiTest {
        val routine = RoutineUi("r1", "Treino A", exercises = listOf(PlannedExerciseUi("p1", bench)))
        val state = stateOf(routines = listOf(routine))
        val fake = Fake(state)
        show(state = state, fake = fake)
        tap(ExerciseInfoTags.ADD_ROUTINE)
        onNodeWithTag(AddToRoutineSheetTags.routine("r1")).performClick()
        waitForIdle()
        state.value = state.value.copy(error = "raw", errorCode = UiError.SAVE_FAILED, errorSubject = "r2", subjectFailures = mapOf("r2" to 1))
        waitForIdle()
        toastWith("Não foi possível salvar a última mudança. Seus dados continuam no aparelho.").assertIsDisplayed()
        onNodeWithTag(TOAST_CLOSE, useUnmergedTree = true).performClick()
        waitForIdle()
        state.value = state.value.copy(errorCode = UiError.GENERIC, errorSubject = null, subjectFailures = mapOf("r2" to 2))
        waitForIdle()
        onNodeWithTag(TOAST_CLOSE, useUnmergedTree = true).performClick()
        waitForIdle()
        // The echo of the add still comes: the toast is shown.
        state.value = state.value.copy(routines = listOf(routine.copy(exercises = routine.exercises + PlannedExerciseUi("p2", bench))))
        waitForIdle()
        toastWith("Adicionado a Treino A").assertIsDisplayed()
    }

    @Test
    fun customWithPhotoShowsThePhotoWithoutAlternation() = runComposeUiTest {
        val photos = FakeExercisePhotos().also { it.store.givePhoto(mine.id) }
        show(mine, photos = photos)
        waitUntil(timeoutMillis = 5_000) { exists(ExerciseInfoTags.PHOTO) }
        val bounds = onNodeWithTag(ExerciseInfoTags.PHOTO, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val frame = onNodeWithTag(ExerciseInfoTags.MEDIA).getUnclippedBoundsInRoot()
        // The photo fills the square frame.
        assertEquals(frame.width, bounds.width)
        assertEquals(frame.height, bounds.height)
        assertEquals(bounds.width, bounds.height)
        assertTrue(!exists(AnimatedFigureTag), "a photo does not alternate")
        assertTrue(!exists(ExerciseEquipmentIconTag), "the icon stands in only while there is no photo")
        onNodeWithTag(ExerciseInfoTags.PHOTO, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Foto de ${mine.name}")))
    }

    @Test
    fun photoThatTheSaveCouldNotKeepIsToastedOnce() = runComposeUiTest {
        val photos = FakeExercisePhotos()
        photos.photos.noteCommitFailed(mine.id)
        show(mine, photos = photos)
        waitForIdle()
        toastWith("Não foi possível salvar a foto.").assertIsDisplayed()
        // Taken, not repeated: a failure noted for another exercise is not shown here.
        assertEquals(emptySet(), photos.photos.failedCommits.value)
        photos.photos.noteCommitFailed("other")
        waitForIdle()
        assertEquals(setOf("other"), photos.photos.failedCommits.value)
    }

    @Test
    fun customWithoutAPhotoKeepsTheEquipmentIcon() = runComposeUiTest {
        show(mine, photos = FakeExercisePhotos())
        waitForIdle()
        assertTrue(!exists(ExerciseInfoTags.PHOTO))
        assertTrue(exists(ExerciseEquipmentIconTag))
    }

    /** Fix round 1 (I1): the longest name a custom exercise can have, at 2x on a small phone. */
    @Test
    fun longNameAtTwoXLeavesTheActionsInReach() {
        val name = "Supino inclinado articulado unilateral com pegada neutra, pausa isométrica no meio e descida lenta controlada até o fim."
        assertEquals(NameRule.MAX_LENGTH, name.length)
        val long = mine.copy(name = name)
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            runComposeUiTest {
                val state = mutableStateOf(UiState(loading = false, exercises = listOf(long)))
                show(long, state = state, fontScale = scale, size = DpSize(320.dp, 640.dp))
                // The fixed bar holds only back and the menu, one row whatever the name: the scrolling
                // content, the name first, starts right under it.
                val menu = onNodeWithTag(ExerciseInfoTags.MENU).getUnclippedBoundsInRoot()
                val start = onNodeWithTag(ExerciseInfoTags.TITLE).getUnclippedBoundsInRoot()
                assertTrue(start.top >= menu.bottom - 0.5.dp && start.top - menu.top <= 64.dp, "at $scale the menu is $menu, the name starts at ${start.top}")
                // The name is whole, as the scrolling content's heading.
                val title = onNodeWithTag(ExerciseInfoTags.TITLE).assertTextEquals(name)
                    .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                val results = mutableListOf<TextLayoutResult>()
                title.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
                assertTextNotCut(results.first(), "the name at $scale")
                // And the actions scroll into view.
                onNodeWithTag(ExerciseInfoTags.ADD_ROUTINE).performScrollTo().assertIsDisplayed()
            }
            LocalAppLocale.restore()
        }
    }

    @Test
    fun textsStayWholeAtTwoX() {
        val history = listOf(workout("w1", OCT_14, curl, SetUi("s1", reps = 12, weightKg = 22.5, completed = true)))
        val records = listOf(RecordUi(curl, weightKg = 22.5, reps = 12, workoutId = "w1", date = OCT_14))
        for (scale in listOf(2f, LINEAR_TWO_X_FONT_SCALE)) {
            runComposeUiTest {
                val state = stateOf(history = history, records = records, active = WorkoutUi("w9", "Treino", startedAt = 1))
                show(curl, state = state, fontScale = scale, size = DpSize(320.dp, 640.dp))
                waitForCues()
                val texts = onAllNodes(
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and hasAnyAncestor(hasTestTag(ExerciseInfoScreenTag)),
                    useUnmergedTree = true,
                ).fetchSemanticsNodes()
                var checked = 0
                for (node in texts) {
                    val results = mutableListOf<TextLayoutResult>()
                    val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: continue
                    if (!action(results) || results.isEmpty()) continue
                    val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
                    assertTextNotCut(results.first(), "\"$text\" at $scale")
                    checked++
                }
                // The name, 3 chips, the caption, 3 headings, the legend's 2, the muscle text, 4 cues and
                // their numbers, the card's 5 lines, the badge, "View full history" and the two actions.
                assertTrue(checked >= 27, "only $checked texts at $scale")
            }
            LocalAppLocale.restore()
        }
    }

    private fun workout(id: String, startedAt: Long, exercise: ExerciseUi, vararg sets: SetUi) =
        WorkoutUi(id, "Treino $id", startedAt, endedAt = startedAt + 3_600_000, exercises = listOf(WorkoutExerciseUi("$id-0", exercise, sets.toList())))

    private companion object {
        const val TOAST = "toast"
        const val TOAST_ACTION = "toast-action"
        const val TOAST_CLOSE = "toast-close"

        /** Noon UTC, the same day in every time zone a test device runs in. */
        const val OCT_21 = 1_792_584_000_000L
        const val OCT_14 = 1_791_979_200_000L
    }
}
