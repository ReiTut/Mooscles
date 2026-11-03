package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.ui.SetField
import com.mooscles.ui.SetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActiveWorkoutScreenStateTest {
    private val scope = SaverScope { true }

    private fun roundTrip(state: ActiveWorkoutScreenState): ActiveWorkoutScreenState {
        val saved = with(ActiveWorkoutScreenState.Saver) { scope.save(state) }
        return requireNotNull(saved?.let { ActiveWorkoutScreenState.Saver.restore(it) })
    }

    @Test
    fun saverKeepsClearedAndEditingButNotOptimistic() {
        // An id with a colon, as the saved form splits on the first one.
        val state = ActiveWorkoutScreenState(
            cleared = setOf("s1" to SetField.REPS, "a:b" to SetField.LOAD),
            optimistic = mapOf("s1" to true, "s:2" to false),
            editing = SetEditTarget("s:3", SetField.REPS, keyboard = true, error = true),
        )
        val restored = roundTrip(state)
        assertEquals(state.cleared, restored.cleared)
        assertEquals(state.editing, restored.editing)
        // A write lost with the process must not come back as a full disc: the database decides.
        assertEquals(emptyMap(), restored.optimistic)
        assertFalse(restored.completedOf(SetUi("s1")))

        val empty = roundTrip(ActiveWorkoutScreenState())
        assertEquals(emptySet(), empty.cleared)
        assertEquals(emptyMap(), empty.optimistic)
        assertNull(empty.editing)
    }

    @Test
    fun droppingTheOptimisticValueShowsTheDatabaseAgain() {
        val pending = SetUi("s1", reps = 8, weightKg = 80.0)
        val done = SetUi("s2", reps = 8, weightKg = 80.0, completed = true)
        val state = ActiveWorkoutScreenState()
        state.expect("s1", true)
        state.expect("s2", false)
        assertTrue(state.completedOf(pending))
        assertFalse(state.completedOf(done))

        // The check of s1 failed: no echo will settle it.
        state.dropOptimistic("s1")
        assertFalse(state.completedOf(pending))
        assertFalse(state.completedOf(done), "only s1 was dropped")
        state.dropOptimistic("unknown")
        assertEquals(mapOf("s2" to false), state.optimistic)

        state.dropAllOptimistic()
        assertEquals(emptyMap(), state.optimistic)
        assertTrue(state.completedOf(done))
    }

    @Test
    fun saverKeepsTheLastRemovalAndTheTypeSheet() {
        val removed = RemovedSet("e:1", 3, SetUi("s:4", reps = 6, weightKg = 82.5, completed = true, warmup = true, assistance = true), 4)
        val restored = roundTrip(ActiveWorkoutScreenState(lastRemoved = removed, typeSheetFor = "s:2"))
        assertEquals(removed, restored.lastRemoved)
        assertEquals("s:2", restored.typeSheetFor)

        val empty = roundTrip(ActiveWorkoutScreenState())
        assertNull(empty.lastRemoved)
        assertNull(empty.typeSheetFor)
    }

    @Test
    fun saverKeepsTheSetsCompletedHereAndTheRecordsAnnounced() {
        // A recreation or a process death must neither replay a record nor forget a check whose
        // completion is still on its way (its record is still celebrated).
        val state = ActiveWorkoutScreenState(checkedHere = setOf("s:1", "s2"), announced = mapOf("bench|Total load|WEIGHT" to RecordBest(87.5, 6)))
        state.checkedHere("s3")
        state.uncheckedHere("s2")
        state.settleRecords(RecordStep(setOf("s:1"), emptyList(), state.announced + ("row|Total load|REPS|80.0" to RecordBest(80.0, 8))))
        val restored = roundTrip(state)
        assertEquals(setOf("s3"), restored.checkedHere)
        assertEquals(mapOf("bench|Total load|WEIGHT" to RecordBest(87.5, 6), "row|Total load|REPS|80.0" to RecordBest(80.0, 8)), restored.announced)
        // Removing a set forgets its pending check.
        restored.forget("s3")
        assertEquals(emptySet(), restored.checkedHere)

        val empty = roundTrip(ActiveWorkoutScreenState())
        assertEquals(emptySet(), empty.checkedHere)
        assertEquals(emptyMap(), empty.announced)
    }

    @Test
    fun forgettingASetDropsWhatWasKeptForItAlone() {
        val state = ActiveWorkoutScreenState(
            cleared = setOf("s4" to SetField.REPS, "s1" to SetField.REPS),
            optimistic = mapOf("s4" to true, "s1" to true),
            editing = SetEditTarget("s4", SetField.LOAD, keyboard = true),
            drafts = mapOf(("s4" to SetField.LOAD) to "82,", ("s1" to SetField.LOAD) to "80"),
            sent = mapOf(("s4" to SetField.REPS) to listOf(0.0), ("s1" to SetField.LOAD) to listOf(80.0)),
            typeSheetFor = "s4",
        )
        state.forget("s4")
        assertEquals(setOf("s1" to SetField.REPS), state.cleared)
        assertEquals(mapOf("s1" to true), state.optimistic)
        assertNull(state.editing)
        assertEquals(mapOf(("s1" to SetField.LOAD) to "80"), state.drafts)
        assertEquals(80.0, state.expected("s1", SetField.LOAD, 0.0), "another set's awaited write stays")
        assertEquals(6.0, state.expected("s4", SetField.REPS, 6.0), "the removed set's awaited write is gone")
        assertNull(state.typeSheetFor)

        // Another set's edit and sheet are not touched.
        val other = ActiveWorkoutScreenState(editing = SetEditTarget("s1", SetField.REPS), typeSheetFor = "s1")
        other.forget("s4")
        assertEquals(SetEditTarget("s1", SetField.REPS), other.editing)
        assertEquals("s1", other.typeSheetFor)

        // Closing the undo leaves nothing to undo.
        state.lastRemoved = RemovedSet("e1", 3, SetUi("s4"), 4)
        state.closeUndo()
        assertNull(state.lastRemoved)
    }
}
