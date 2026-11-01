package com.mooscles.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SetSuggestionsTest {
    private val total = "Total load"
    private fun s(id: String, kg: Double = 0.0, reps: Int = 0, done: Boolean = false, w: Boolean = false, a: Boolean = false) =
        SetUi(id, reps, kg, done, w, a)
    private fun p(kg: Double, reps: Int, w: Boolean = false, a: Boolean = false) = PreviousSetUi(kg, reps, w, a)
    private val prev = listOf(p(80.0, 8), p(80.0, 8))
    private fun one(sets: List<SetUi>, previous: List<PreviousSetUi>, conv: String, idx: Int, cleared: Set<Pair<String, SetField>> = emptySet()) =
        SetSuggestions.forExercise(sets, previous, conv, cleared)[idx]

    @Test fun savedValuesAreNeverSuggestions() =
        assertEquals(SetSuggestion.None, one(listOf(s("a", 80.0, 8)), prev, total, 0))

    @Test fun zeroKgOnCommonLoadGetsTheLoadSuggestion() {
        for (c in listOf("Total load", "Per hand", "One side")) assertEquals(80.0, one(listOf(s("a", 0.0, 8)), prev, c, 0).weightKg)
    }

    @Test fun zeroKgOnAddedWeightAndAssistanceIsAValue() {
        for (c in listOf("Added weight", "Assistance")) {
            val r = one(listOf(s("a")), prev, c, 0)
            assertNull(r.weightKg); assertEquals(8, r.reps)
        }
    }

    @Test fun aValueAboveZeroIsNeverReplaced() {
        val r = one(listOf(s("a", 50.0, 0)), prev, total, 0)
        assertNull(r.weightKg); assertEquals(8, r.reps)
        assertEquals(SetSuggestion.None, one(listOf(s("a", 50.0, 5)), prev, total, 0))
    }

    @Test fun theCompletedSetAboveWins() =
        assertEquals(SetSuggestion(82.5, 8), one(listOf(s("a", 82.5, 8, true), s("b")), prev, total, 1))

    @Test fun nearestCompletedAboveSkipsPendingSetsInBetween() {
        val sets = listOf(s("a", 70.0, 5, true), s("b", 90.0, 9), s("c"))
        assertEquals(SetSuggestion(70.0, 5), one(sets, prev, total, 2))
    }

    @Test fun thePreviousSessionCoversTheFirstSet() =
        assertEquals(SetSuggestion(80.0, 8), one(listOf(s("a")), prev, total, 0))

    @Test fun pairingIsByKindThenOrder() {
        val sets = listOf(s("1", w = true), s("2", w = true), s("3"), s("4"))
        val previous = listOf(p(40.0, 10, w = true), p(80.0, 8), p(80.0, 6))
        assertEquals(listOf(p(40.0, 10, w = true), null, p(80.0, 8), p(80.0, 6)), SetSuggestions.pairPrevious(sets, previous))
    }

    @Test fun assistedCountsAsWorking() {
        val sets = listOf(s("1"), s("2", a = true))
        val previous = listOf(p(80.0, 8), p(80.0, 6, a = true))
        assertEquals(listOf(previous[0], previous[1]), SetSuggestions.pairPrevious(sets, previous))
    }

    @Test fun aWarmupAboveDoesNotFeedAWorkingSet() =
        assertEquals(SetSuggestion(80.0, 8), one(listOf(s("w", 40.0, 10, true, w = true), s("a")), listOf(p(80.0, 8)), total, 1))

    // The exact-kg-in-pounds rule has no failing case in this layer (nothing converts); it is covered where the screen writes values.

    @Test fun aClearedFieldGetsNoSuggestion() {
        val r = one(listOf(s("s1", 82.5, 8, true), s("s2")), prev, total, 1, setOf("s2" to SetField.LOAD))
        assertNull(r.weightKg); assertEquals(8, r.reps)
    }

    @Test fun completedSetsGetNoSuggestion() =
        assertEquals(SetSuggestion.None, one(listOf(s("a", 0.0, 8, true)), prev, total, 0))

    @Test fun effectiveUsesSuggestionsOnlyForEmptyFields() {
        assertEquals(EffectiveSet(82.5, 8), SetSuggestions.effective(s("a"), SetSuggestion(82.5, 8)))
        assertEquals(EffectiveSet(50.0, 5), SetSuggestions.effective(s("a", 50.0, 5), SetSuggestion.None))
        assertEquals(EffectiveSet(50.0, 8), SetSuggestions.effective(s("a", 50.0, 0), SetSuggestion(null, 8)))
    }

    @Test fun nothingWithoutASource() =
        assertEquals(SetSuggestion.None, one(listOf(s("a")), emptyList(), total, 0))

    @Test fun anEmptyCompletedAboveFallsThroughToThePreviousSessionLoad() {
        val r = one(listOf(s("a", 0.0, 8, true), s("b")), prev, total, 1)
        assertEquals(SetSuggestion(80.0, 8), r)
    }

    @Test fun aZeroRepsCompletedAboveFallsThroughToThePreviousSessionReps() {
        val r = one(listOf(s("a", 82.5, 0, true), s("b")), prev, total, 1)
        assertEquals(SetSuggestion(82.5, 8), r)
    }

    @Test fun aWarmupsOnlyPreviousSessionGivesNothingToWorkingSets() {
        val previous = listOf(p(40.0, 10, w = true))
        assertEquals(listOf(null, null), SetSuggestions.pairPrevious(listOf(s("1"), s("2")), previous))
        assertEquals(SetSuggestion.None, one(listOf(s("1")), previous, total, 0))
    }

    @Test fun pairingIgnoresTheOrderOfKindsInThePreviousList() {
        val previous = listOf(p(80.0, 8), p(40.0, 10, w = true))
        assertEquals(listOf(previous[1], previous[0]), SetSuggestions.pairPrevious(listOf(s("1", w = true), s("2")), previous))
    }

    @Test fun aCompletedWarmupAboveFeedsTheNextWarmup() {
        val sets = listOf(s("1", 40.0, 10, true, w = true), s("2", w = true))
        assertEquals(SetSuggestion(40.0, 10), one(sets, listOf(p(20.0, 12, w = true)), total, 1))
    }

    @Test fun aClearedRepsFieldGetsNoSuggestion() {
        val r = one(listOf(s("s1", 82.5, 8, true), s("s2")), prev, total, 1, setOf("s2" to SetField.REPS))
        assertEquals(82.5, r.weightKg); assertNull(r.reps)
    }
}
