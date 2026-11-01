package com.mooscles.screens.summary

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.data.LoadConvention
import com.mooscles.designsystem.components.PrBadgeTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.interaction.HapticEvent
import com.mooscles.designsystem.interaction.MotionChoice
import com.mooscles.designsystem.interaction.RecordingHaptics
import com.mooscles.designsystem.tokens.MoosclesMotion
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.ScreenRestoration
import com.mooscles.screens.assertNumbersWhole
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The end-of-workout summary (spec D19). Loads use the design system's no-break space before the unit. */
@OptIn(ExperimentalTestApi::class)
class WorkoutSummaryScreenTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val utc = TimeZone.UTC
    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime(y, mo, d, h, mi).toInstant(utc).toEpochMilliseconds()

    // 2024-10-29 is a Tuesday.
    private val start = at(2024, 10, 29, 18, 2)
    private val end = at(2024, 10, 29, 18, 44)
    private val now = end + 60_000

    private fun exercise(id: String, name: String, convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        ExerciseUi(id, name, "Chest", "Barbell", convention.label)

    private val bench = exercise("bench", "Supino reto")
    private val row = exercise("row", "Remada curvada")
    private val squat = exercise("squat", "Agachamento")
    private val curl = exercise("curl", "Rosca direta")

    private fun done(id: String, kg: Double, reps: Int, warmup: Boolean = false) =
        SetUi(id, reps, kg, completed = true, warmup = warmup)

    private val weightRecord = RecordUi(bench, 87.5, 6, previousKg = 85.0)
    private val repsRecord = RecordUi(squat, 80.0, 8, kind = RecordKind.REPS, previousReps = 6)
    private val firstRecord = RecordUi(row, 82.5, 8)

    private fun workout(records: List<RecordUi> = listOf(weightRecord, repsRecord, firstRecord)) = WorkoutUi(
        id = "w1", name = "Treino A", startedAt = start, endedAt = end,
        exercises = listOf(
            WorkoutExerciseUi("e1", bench, listOf(done("b1", 80.0, 8), done("b2", 87.5, 6))),
            WorkoutExerciseUi("e2", row, listOf(done("r1", 80.0, 8), done("r2", 82.5, 8), done("r3", 82.5, 6))),
            // s3 is done without reps: not a valid set, so neither a set nor a best set.
            WorkoutExerciseUi("e3", squat, listOf(done("s1", 80.0, 8), SetUi("s2", 8, 80.0), done("s3", 80.0, 0))),
            WorkoutExerciseUi("e4", curl, listOf(done("c1", 10.0, 12, warmup = true))),
            // Only an invalid completed set: not an exercise of the summary.
            WorkoutExerciseUi("e5", exercise("press", "Desenvolvimento"), listOf(done("p1", 40.0, 0))),
        ),
        records = records,
    )

    /** [count] finished workouts, one a day back from [end], this one first. */
    private fun history(count: Int): List<WorkoutUi> = List(count) { day ->
        if (day == 0) workout() else WorkoutUi("h$day", "Old", start - day * DAY, end - day * DAY)
    }

    /** Scrolls the summary to the node, then asserts it is on screen. */
    private fun SemanticsNodeInteraction.shown(): SemanticsNodeInteraction = performScrollTo().assertIsDisplayed()

    /** The summary of [workout] on a [size] screen that fits every test device, the content scrolling. */
    private fun ComposeUiTest.summary(
        workout: WorkoutUi = workout(),
        history: List<WorkoutUi> = history(1),
        locale: String? = "pt-BR",
        fontScale: Float = 1f,
        density: Float? = null,
        size: DpSize? = DpSize(360.dp, 640.dp),
        reducedMotion: Boolean = false,
        haptics: RecordingHaptics = RecordingHaptics(),
        restoration: ScreenRestoration? = null,
        calls: MutableList<String> = mutableListOf(),
    ) {
        withScreen(locale = locale, fontScale = fontScale, density = density, size = size, reducedMotion = reducedMotion,
            haptics = haptics, restoration = restoration) {
            WorkoutSummaryScreen(
                workout = workout, history = history, pounds = false,
                onDone = { calls += "done" }, onShare = { calls += "share" }, onDetails = { calls += "details" },
                nowMillis = now, zone = utc,
            )
        }
        waitForIdle()
    }

    @Test
    fun headerSaysWorkoutCompleteWithTheRange() = runComposeUiTest {
        summary()
        onNodeWithText("Treino concluído").shown()
        onNodeWithText("Treino A").shown()
        onNodeWithText("ter., 29 out · 18:02–18:44").shown()
    }

    @Test
    fun statsTwoColumnsThenOneAtOneAndAHalf() {
        runComposeUiTest {
            summary()
            onNodeWithText("Duração").shown()
            onNodeWithText("Séries").shown()
            onNodeWithText("Volume").shown()
            onNodeWithText("Exercícios").shown()
            onNodeWithText("42 min").shown()
            // The valid completed sets and the exercises that have one (the share card's count), not the
            // contract's raw `completedSets` (9) and `completedExercises` (5).
            onNodeWithText("7").shown()
            // workout.volumeKg: every completed set, the warm-up too (80×8 + 87,5×6 + 80×8 + 82,5×8 + 82,5×6 + 80×8 + 10×12).
            onNodeWithText("3.720\u00A0kg").shown()
            onNodeWithText("4").shown()
            val duration = onNodeWithTag(summaryStatTag(SummaryStat.DURATION)).getUnclippedBoundsInRoot()
            val sets = onNodeWithTag(summaryStatTag(SummaryStat.SETS)).getUnclippedBoundsInRoot()
            val volume = onNodeWithTag(summaryStatTag(SummaryStat.VOLUME)).getUnclippedBoundsInRoot()
            assertEquals(duration.top, sets.top, "Sets sits beside Duration")
            assertTrue(sets.left > duration.left, "Sets is the second column")
            assertTrue(volume.top >= duration.bottom, "Volume opens the second row")
            assertEquals(duration.left, volume.left)
        }
        runComposeUiTest {
            summary(fontScale = 1.5f)
            val duration = onNodeWithTag(summaryStatTag(SummaryStat.DURATION)).getUnclippedBoundsInRoot()
            val sets = onNodeWithTag(summaryStatTag(SummaryStat.SETS)).getUnclippedBoundsInRoot()
            assertEquals(duration.left, sets.left, "One column at 1.5")
            assertTrue(sets.top >= duration.bottom, "Sets under Duration at 1.5")
        }
    }

    @Test
    fun recordLines() = runComposeUiTest {
        summary()
        val records = hasAnyAncestor(hasTestTag(SummaryRecordsTag))
        onNode(hasText("Recordes") and records).shown()
        onNode(hasText("Supino reto · 87,5\u00A0kg\u00A0×\u00A06 · antes 85\u00A0kg") and records).shown()
        onNode(hasText("Agachamento · 80\u00A0kg\u00A0×\u00A08 · antes 80\u00A0kg\u00A0×\u00A06") and records).shown()
        assertEquals(2, onAllNodes(hasTestTag(PrBadgeTag) and records, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun firstRecordsSection() = runComposeUiTest {
        summary(workout(records = listOf(firstRecord)))
        val first = hasAnyAncestor(hasTestTag(SummaryFirstRecordsTag))
        onNode(hasText("Primeiros registros") and first).shown()
        onNode(hasText("Remada curvada · 82,5\u00A0kg\u00A0×\u00A08") and first).shown()
        assertEquals(1, onAllNodes(hasTestTag(PrBadgeTag) and first, useUnmergedTree = true).fetchSemanticsNodes().size)
        // Only first records: no "Records" heading with nothing beaten under it.
        assertEquals(0, onAllNodes(hasTestTag(SummaryRecordsTag)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasText("Recordes")).fetchSemanticsNodes().size)
    }

    @Test
    fun noRecordsHidesTheSection() = runComposeUiTest {
        summary(workout(records = emptyList()))
        assertEquals(0, onAllNodes(hasTestTag(SummaryRecordsTag)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasTestTag(SummaryFirstRecordsTag)).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasTestTag(PrBadgeTag), useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasText("Recordes")).fetchSemanticsNodes().size)
        assertEquals(0, onAllNodes(hasText("Primeiros registros")).fetchSemanticsNodes().size)
    }

    @Test
    fun consistencyLine() {
        runComposeUiTest {
            summary(history = history(12))
            onNodeWithText("12 treinos nas últimas 4 semanas").shown()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            // The history may not hold this workout yet: it still counts, once.
            summary(history = emptyList())
            onNodeWithText("1 treino nas últimas 4 semanas").shown()
        }
        LocalAppLocale.restore()
        runComposeUiTest {
            // 28 calendar days, today included: the workout 28 days back is out.
            summary(history = history(29), locale = "en")
            onNodeWithText("28 workouts in the last 4 weeks").shown()
        }
    }

    @Test
    fun perExerciseBestSet() = runComposeUiTest {
        summary()
        // Each row is read as one: the name, then the best set.
        onNodeWithTag(summaryExerciseTag("e2")).assert(hasText("Remada curvada")).assert(hasText("82,5\u00A0kg × 8"))
        onNodeWithTag(summaryExerciseTag("e1")).assert(hasText("87,5\u00A0kg × 6"))
        onNodeWithTag(summaryExerciseTag("e3")).assert(hasText("80\u00A0kg × 8"))
        onNodeWithTag(summaryExerciseTag("e4")).assert(hasText("Só aquecimento"))
        assertEquals(0, onAllNodes(hasTestTag(summaryExerciseTag("e5"))).fetchSemanticsNodes().size)
    }

    @Test
    fun actionsCallBack() = runComposeUiTest {
        val calls = mutableListOf<String>()
        summary(calls = calls)
        onNodeWithText("Concluir").performScrollTo().performClick()
        onNodeWithText("Compartilhar").performScrollTo().performClick()
        onNodeWithText("Ver detalhes").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("done", "share", "details"), calls)
    }

    @Test
    fun entersOnceWithTheFinishSpringAndHaptic() = runComposeUiTest {
        val haptics = RecordingHaptics()
        val restoration = screenRestoration()
        // No forced language: leaving a ProvideAppLocale bumps the theme's locale epoch, which
        // rebuilds the tree under a new key, and nothing saved would be found on the way back.
        summary(haptics = haptics, restoration = restoration, locale = null)
        val motion = MoosclesMotion()
        onNodeWithTag(SummaryStatsTag)
            .assert(SemanticsMatcher.expectValue(SummaryEntrance, MotionChoice.Spring(motion.springFinish, 0.92f, Dp(0f))))
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
        restoration.emulateSaveAndRestore()
        waitForIdle()
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList(), "restored: no second haptic")
        onNodeWithTag(SummaryStatsTag).assert(SemanticsMatcher.keyNotDefined(SummaryEntrance)).shown()
    }

    @Test
    fun reducedMotionFades() = runComposeUiTest {
        val haptics = RecordingHaptics()
        summary(reducedMotion = true, haptics = haptics)
        onNodeWithTag(SummaryStatsTag)
            .assert(SemanticsMatcher.expectValue(SummaryEntrance, MotionChoice.Fade(MoosclesMotion().fast)))
            .shown()
        assertEquals(listOf(HapticEvent.SetCompleted), haptics.events.toList())
    }

    @Test
    fun numbersWholeAtTwoHundred() {
        // 2.0 with the device's density; just over 2 at density 1, the iOS text sizes; and the
        // two-column layout at 1.0 and 1.3, where num.lg would not fit 320 dp.
        // At 412 dp, 1.0 and 2.0, the other width of the spec's text-fit rule.
        val cases = listOf(
            Triple(2f, null, 320), Triple(LINEAR_TWO_X_FONT_SCALE, 1f, 320), Triple(1f, null, 320), Triple(1.3f, null, 320),
            Triple(1f, null, 412), Triple(2f, null, 412),
        )
        for ((scale, density, width) in cases) {
            runComposeUiTest {
                summary(fontScale = scale, density = density, size = DpSize(width.dp, 640.dp))
                // Four stats and the best sets of three exercises.
                assertNumbersWhole(atLeast = 7)
            }
            LocalAppLocale.restore()
        }
    }

    /** Plan decision 11: the exercise lines and the records show the catalog's pt-BR name, never the stored English one. */
    @Test
    fun catalogNamesFollowPortuguese() = runComposeUiTest {
        val bench = exercise("catalog_barbell_bench_flat", "Barbell bench press")
        val workout = WorkoutUi(
            id = "w1", name = "Treino A", startedAt = start, endedAt = end,
            exercises = listOf(WorkoutExerciseUi("e1", bench, listOf(done("b1", 80.0, 8), done("b2", 87.5, 6)))),
            records = listOf(RecordUi(bench, 87.5, 6, previousKg = 85.0)),
        )
        summary(workout = workout, history = listOf(workout))
        onNode(hasText("Supino reto com barra") and hasAnyAncestor(hasTestTag(summaryExerciseTag("e1"))), useUnmergedTree = true)
            .assertExists()
        onNode(hasText("Supino reto com barra · 87,5\u00A0kg\u00A0×\u00A06 · antes 85\u00A0kg") and hasAnyAncestor(hasTestTag(SummaryRecordsTag)))
            .shown()
        onAllNodesWithText("Barbell bench press", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}
