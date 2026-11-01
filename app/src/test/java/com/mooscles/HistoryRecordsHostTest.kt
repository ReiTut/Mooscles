package com.mooscles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
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
        scrollToMilestones()
        assertMilestoneText("Bench press")
        assertMilestoneText("Weight PR")
        assertMilestoneText("100 kg → 105 kg × 3")
        assertMilestoneText("Pull-up")
        assertMilestoneText("Reps PR")
        assertMilestoneText("8 → 12 reps · Bodyweight")
        compose.onNodeWithText("separate from records beaten", substring = true).assertDoesNotExist()
    }

    @Test fun exerciseWithBothRecordTypesDisplaysBothAndCountsOnce() {
        val workout = workout(listOf(
            RecordUi(bench, 105.0, 3, previousKg = 100.0),
            RecordUi(bench, 100.0, 11, kind = RecordKind.REPS, previousReps = 10),
        ))
        assertEquals(1, workout.prExerciseCount)
        showDetail(workout)
        assertDisplayedPrExerciseCount(1)
        scrollToMilestones()

        assertMilestoneText("Weight PR")
        assertMilestoneText("100 kg → 105 kg × 3")
        assertMilestoneText("Reps PR")
        assertMilestoneText("10 → 11 reps · 100 kg")
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
        scrollToMilestones()

        assertMilestoneText("Weight PR")
        assertMilestoneText("2 first records · separate from records beaten")
        assertMilestoneText("Weight PR · First record")
        assertMilestoneText("Reps PR · First record")
        assertMilestoneText("10 kg × 5")
        assertMilestoneText("5 reps · 10 kg")
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
        scrollToMilestones()
        assertMilestoneText("3 first records · separate from records beaten")
        assertMilestoneText("Weight PR · First record")
        compose.onAllNodesWithText("Reps PR · First record").assertCountEquals(2)
        assertMilestoneText("20 kg × 10")
        assertMilestoneText("10 reps · 20 kg")
        assertMilestoneText("10 reps · Bodyweight")
    }

    @Test fun workoutWithoutRecordsDisplaysZeroExercisesWithPrs() {
        val workout = workout(emptyList())
        assertEquals(0, workout.prExerciseCount)
        assertEquals(0, workout.beatenPrExerciseCount)
        assertEquals(0, workout.firstRecordExerciseCount)
        showDetail(workout)

        assertDisplayedPrExerciseCount(0)
        compose.onNodeWithText("New milestones").assertDoesNotExist()
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

    private fun showDetail(workout: WorkoutUi) {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(430.dp, 900.dp)) {
                    WorkoutDetailScreen(workout, UiState(loading = false), noActions,
                        onDeleted = {}, onExerciseHistory = {})
                }
            }
        }
    }

    private fun scrollToMilestones() {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("New milestones"))
    }

    private fun assertDisplayedPrExerciseCount(expected: Int) {
        compose.onNodeWithTag("workout-pr-exercise-count").performScrollTo()
            .assert(hasAnyDescendant(hasText(expected.toString())))
            .assert(hasAnyDescendant(hasText("Exercises with PRs")))
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
