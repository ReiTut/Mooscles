package com.mooscles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.screens.workoutdetail.WorkoutDetailRoute
import com.mooscles.screens.workoutdetail.WorkoutDetailTags
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryRecordsHostTest {
    @get:Rule val compose = createComposeRule()
    @After fun restoreLocale() { LocalAppLocale.restore() }

    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", "Total load")
    private val pullup = ExerciseUi("pullup", "Pull-up", "Back", "Bodyweight", "Added weight")

    @Test fun summaryDisplaysBothExercisesAndTheirPreviousAndNewRecords() {
        val benchWeight = RecordUi(bench, 105.0, 3, previousKg = 100.0)
        val pullupReps = RecordUi(pullup, 0.0, 12, kind = RecordKind.REPS, previousReps = 8)
        val workout = workout(listOf(benchWeight, pullupReps))
        assertEquals(2, workout.prExerciseCount)
        assertEquals(0, workout.firstRecordExerciseCount)
        showDetail(workout)

        assertDisplayedPrExerciseCount(2)
        scrollToRecords()
        assertMilestoneText("Bench press · 105\u00a0kg\u00a0×\u00a03 · before 100\u00a0kg")
        compose.onNode(hasText("Weight PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.RECORDS))).performScrollTo().assertIsDisplayed()
        assertMilestoneText("Pull-up · Bodyweight\u00a0×\u00a012 · before Bodyweight\u00a0×\u00a08")
        assertMilestoneText("Reps PR")
        compose.onNodeWithTag(WorkoutDetailTags.FIRST).assertDoesNotExist()
    }

    @Test fun exerciseWithBothRecordTypesDisplaysBothAndCountsOnce() {
        val workout = workout(listOf(
            RecordUi(bench, 105.0, 3, previousKg = 100.0),
            RecordUi(bench, 100.0, 11, kind = RecordKind.REPS, previousReps = 10),
        ))
        assertEquals(1, workout.prExerciseCount)
        showDetail(workout)
        assertDisplayedPrExerciseCount(1)
        scrollToRecords()

        assertMilestoneText("Weight PR")
        assertMilestoneText("Bench press · 105\u00a0kg\u00a0×\u00a03 · before 100\u00a0kg")
        assertMilestoneText("Reps PR")
        assertMilestoneText("Bench press · 100\u00a0kg\u00a0×\u00a011 · before 100\u00a0kg\u00a0×\u00a010")
    }

    @Test fun firstRecordsHaveTheirOwnLabelsAndDoNotIncreaseTheBeatenExerciseCount() {
        val workout = workout(listOf(
            RecordUi(bench, 105.0, 3, previousKg = 100.0),
            RecordUi(pullup, 10.0, 5),
            RecordUi(pullup, 10.0, 5, kind = RecordKind.REPS),
        ))
        assertEquals(2, workout.prExerciseCount)
        assertEquals(1, workout.beatenPrExerciseCount)
        assertEquals(1, workout.firstRecordExerciseCount)
        showDetail(workout)
        assertDisplayedPrExerciseCount(2)
        scrollToRecords()

        compose.onNode(hasText("Weight PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.RECORDS))).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.FIRST))
        compose.onNodeWithTag(WorkoutDetailTags.FIRST).assertIsDisplayed()
        compose.onNodeWithText("First records").assertIsDisplayed()
        compose.onAllNodes(hasText("Weight PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(1)
        compose.onAllNodes(hasText("Reps PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(1)
        compose.onAllNodes(hasText("Pull-up · 10\u00a0kg\u00a0×\u00a05") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(2)
    }

    @Test fun firstBenchWeightAndRepsRecordsAndFirstBodyweightPullupRecordCountTwoExercises() {
        val barbellBench = bench.copy(name = "Barbell bench press")
        val wideGripPullup = pullup.copy(name = "Wide-grip pull-up")
        val workout = workout(listOf(
            RecordUi(barbellBench, 20.0, 10),
            RecordUi(barbellBench, 20.0, 10, kind = RecordKind.REPS),
            RecordUi(wideGripPullup, 0.0, 10, kind = RecordKind.REPS),
        ))
        assertEquals(2, workout.prExerciseCount)
        assertEquals(0, workout.beatenPrExerciseCount)
        assertEquals(2, workout.firstRecordExerciseCount)
        showDetail(workout)

        assertDisplayedPrExerciseCount(2)
        compose.onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.FIRST))
        compose.onNodeWithTag(WorkoutDetailTags.FIRST).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutDetailTags.RECORDS).assertDoesNotExist()
        compose.onAllNodes(hasText("Weight PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(1)
        compose.onAllNodes(hasText("Reps PR") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(2)
        compose.onAllNodes(hasText("Barbell bench press · 20\u00a0kg\u00a0×\u00a010") and hasAnyAncestor(hasTestTag(WorkoutDetailTags.FIRST))).assertCountEquals(2)
        assertMilestoneText("Wide-grip pull-up · Bodyweight\u00a0×\u00a010")
    }

    @Test fun workoutWithoutRecordsDisplaysZeroExercisesWithPrs() {
        val workout = workout(emptyList())
        assertEquals(0, workout.prExerciseCount)
        assertEquals(0, workout.beatenPrExerciseCount)
        assertEquals(0, workout.firstRecordExerciseCount)
        showDetail(workout)

        assertDisplayedPrExerciseCount(0)
        compose.onNodeWithTag(WorkoutDetailTags.FIRST).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutDetailTags.RECORDS).assertDoesNotExist()
    }

    @Test fun narrowLargeFontKeepsPrsAfterSetsWithoutDuplicatingTheStatistic() {
        val records = (1..11).map { RecordUi(bench.copy(id = "variation-$it"), 22.0, 8) }
        showDetail(workout(records), fontScale = 2f, width = 320)
        compose.onNodeWithTag("workout-pr-exercise-count").performScrollTo().assertIsDisplayed()
            .assert(hasText("11")).assertContentDescriptionEquals("11 exercises with personal records")
        compose.onAllNodesWithTag("workout-pr-exercise-count").assertCountEquals(1)
        val sets = compose.onNodeWithTag("workout-set-count").getUnclippedBoundsInRoot()
        val prs = compose.onNodeWithTag("workout-pr-exercise-count").getUnclippedBoundsInRoot()
        assertEquals(sets.left, prs.left)
        org.junit.Assert.assertTrue(prs.top >= sets.bottom)
    }

    @Test @Config(sdk = [35], qualifiers = "pt-rBR")
    fun zeroPrsUsePortuguesePluralInSavedDetail() {
        showDetail(workout(emptyList()))
        compose.onNodeWithTag("workout-pr-exercise-count").performScrollTo()
            .assert(hasText("0")).assertContentDescriptionEquals("0 exercícios com recordes pessoais")
    }

    private fun workout(records: List<RecordUi>): WorkoutUi {
        val entries = records.groupBy { it.exercise.id to it.exercise.convention }.values.map { achievements ->
            val record = achievements.first()
            WorkoutExerciseUi("entry-${record.exercise.id}", record.exercise,
                sets = listOf(SetUi("set-${record.exercise.id}", record.reps, record.weightKg, completed = true)),
                records = achievements)
        }
        return WorkoutUi("workout", "Record session", startedAt = 1_000, endedAt = 61_000,
            exercises = entries, records = records)
    }

    private fun showDetail(workout: WorkoutUi, fontScale: Float = 1f, width: Int = 430) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme {
                    MoosclesDesignTheme(dark = false, reducedMotion = true) {
                        ProvideAppLocale(locale) {
                            Box(Modifier.size(width.dp, 900.dp)) {
                                WorkoutDetailRoute(workout.id, UiState(loading = false, history = listOf(workout)), noActions,
                                    onBack = {}, onExerciseProgress = {}, onRoutine = {}, onShare = {})
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scrollToRecords() {
        compose.onNodeWithTag(WorkoutDetailTags.LIST).performScrollToNode(hasTestTag(WorkoutDetailTags.RECORDS))
    }

    private fun assertDisplayedPrExerciseCount(expected: Int) {
        compose.onNodeWithTag("workout-pr-exercise-count").performScrollTo()
            .assert(hasText(expected.toString()))
            .assert(hasText("PRs"))
            .assertContentDescriptionEquals("$expected " + if (expected == 1) "exercise with a personal record" else "exercises with personal records")
        compose.onAllNodesWithTag("workout-pr-exercise-count").assertCountEquals(1)
        val sets = compose.onNodeWithTag("workout-set-count").getUnclippedBoundsInRoot()
        val prs = compose.onNodeWithTag("workout-pr-exercise-count").getUnclippedBoundsInRoot()
        assertEquals(sets.top, prs.top)
    }

    private fun assertMilestoneText(text: String) {
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private val noActions = Proxy.newProxyInstance(
        MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "History test actions"
            else -> null
        }
    } as MoosclesActions
}
