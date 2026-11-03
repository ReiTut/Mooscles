package com.mooscles.screens.summary

import com.mooscles.data.LoadConvention
import com.mooscles.screens.format.NoBestSet
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The summary's best set per exercise: the share card's rule (one rule for the app), read from
 * the UI contract instead of the saved rows.
 */
class SummaryStatsTest {
    private fun entry(vararg sets: SetUi, convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        WorkoutExerciseUi("e1", ExerciseUi("bench", "Bench press", "Chest", "Barbell", convention.label), sets.toList())

    private fun set(id: String, kg: Double, reps: Int, completed: Boolean = true, warmup: Boolean = false,
                    assistance: Boolean = false) = SetUi(id, reps, kg, completed, warmup, assistance)

    @Test
    fun bestSetIsTheHeaviestNonWarmupThenMostReps() {
        val sets = entry(
            set("warm", 100.0, 3, warmup = true),
            set("light", 80.0, 10),
            set("six", 82.5, 6),
            set("eight", 82.5, 8),
            set("open", 90.0, 5, completed = false),
            set("assisted", 120.0, 5, assistance = true),
        )
        assertEquals("eight", SummaryStats.bestSet(sets)?.id)
    }

    /**
     * Deviation from the plan's prose ("falls back to the most reps"): the share card's rule wins
     * (controller ruling F7), so warm-ups never become a best set and the row says why instead.
     */
    @Test
    fun warmupOnlyHasNoBestSetAndSaysWarmUpOnly() {
        val sets = entry(set("w1", 40.0, 10, warmup = true), set("w2", 60.0, 5, warmup = true))
        assertNull(SummaryStats.bestSet(sets))
        assertEquals(NoBestSet.WARMUP_ONLY, SummaryStats.noBestSet(sets))
    }

    @Test
    fun assistanceIsNeverTheBestSet() {
        val machine = entry(set("a1", 30.0, 8), convention = LoadConvention.ASSISTANCE)
        assertNull(SummaryStats.bestSet(machine))
        assertEquals(NoBestSet.ASSISTANCE_ONLY, SummaryStats.noBestSet(machine))
        val flagged = entry(set("a2", 20.0, 8, assistance = true))
        assertNull(SummaryStats.bestSet(flagged))
        assertEquals(NoBestSet.ASSISTANCE_ONLY, SummaryStats.noBestSet(flagged))
        val mixed = entry(set("w", 40.0, 5, warmup = true), set("a3", 50.0, 8, assistance = true))
        assertEquals(NoBestSet.NO_WORKING_SET, SummaryStats.noBestSet(mixed))
    }

    @Test
    fun invalidCompletedSetsDoNotCount() {
        val sets = entry(set("ok", 22.0, 6), set("noReps", 150.0, 0), set("negative", -1.0, 3),
            set("nan", Double.NaN, 3))
        assertEquals("ok", SummaryStats.bestSet(sets)?.id)
        assertEquals(listOf("ok"), SummaryStats.completedSets(sets).map { it.id })
        assertEquals(NoBestSet.NO_WORKING_SET, SummaryStats.noBestSet(entry(set("zero", 50.0, 0))))
    }

    @Test
    fun unitRoundTripNoiseKeepsTheRepsTieBreak() {
        val sets = entry(set("noisy", 22.0.nextUp(), 6), set("nine", 22.0, 9))
        assertEquals("nine", SummaryStats.bestSet(sets)?.id)
    }

    @Test
    fun bodyweightPicksTheMostRepsAtZeroLoad() {
        val sets = entry(set("b8", 0.0, 8), set("b12", 0.0, 12), set("bw", 0.0, 30, warmup = true),
            convention = LoadConvention.BODYWEIGHT)
        assertEquals("b12", SummaryStats.bestSet(sets)?.id)
    }
}
