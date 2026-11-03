package com.mooscles.screens.workout

import androidx.compose.runtime.saveable.SaverScope
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which exercise cards are open (spec D9): the current one, the person's own choices, and the advance. */
class CardExpansionTest {
    private val bench = ExerciseUi("bench", "Bench press", "Chest", "Barbell", convention = "Total load")

    private fun entry(id: String, vararg done: Boolean) =
        WorkoutExerciseUi(id, bench, sets = done.mapIndexed { i, completed -> SetUi("$id-s$i", reps = 6, weightKg = 80.0, completed = completed) })

    private fun open(exercises: List<WorkoutExerciseUi>, expanded: Map<String, Boolean>, manual: Set<String>): List<String> =
        exercises.filter { CardExpansion.isOpen(it.id, exercises, expanded, manual) }.map { it.id }

    @Test
    fun currentIsTheFirstWithAPendingSet() {
        val workout = listOf(entry("e1", true, true), entry("e0"), entry("e2", true, false), entry("e3", false))
        // e1 is done and e0 has no set at all: neither has a pending set.
        assertEquals("e2", CardExpansion.current(workout))
        assertEquals(listOf("e2"), open(workout, emptyMap(), emptySet()), "the others start collapsed")
        assertNull(CardExpansion.current(listOf(entry("e1", true), entry("e0"))))
        assertNull(CardExpansion.current(emptyList()))
        assertEquals(emptyList(), open(listOf(entry("e1", true)), emptyMap(), emptySet()))
    }

    @Test
    fun manualChoiceIsNeverOverridden() {
        val workout = listOf(entry("e1", true), entry("e2", false), entry("e3", true))
        // The person closed the current card and opened a finished one.
        val expanded = mapOf("e2" to false, "e3" to true)
        val manual = setOf("e2", "e3")
        assertEquals(listOf("e3"), open(workout, expanded, manual))
        val advanced = CardExpansion.advance(workout, expanded, manual)
        assertEquals(mapOf("e1" to false, "e2" to false, "e3" to true), advanced)
        assertEquals(listOf("e3"), open(workout, advanced, manual))

        // Through the screen state, as a tap on the header does.
        val screen = ActiveWorkoutScreenState()
        screen.toggleCard("e2", open = true)
        screen.toggleCard("e3", open = false)
        assertEquals(emptySet(), screen.advanceCards(workout))
        assertEquals(listOf("e3"), open(workout, screen.expanded, screen.manual))
    }

    @Test
    fun afterTheLastPendingTheNextPendingOpens() {
        val before = listOf(entry("e1", true, false), entry("e2", false, false), entry("e3", false))
        val screen = ActiveWorkoutScreenState()
        assertEquals(emptySet(), screen.advanceCards(before), "the current card was already open")
        assertEquals(mapOf("e1" to true, "e2" to false, "e3" to false), screen.expanded)

        // The last pending set of e1 is done: until the advance, the cards stay as they are.
        val after = listOf(entry("e1", true, true), entry("e2", false, false), entry("e3", false))
        assertEquals("e2", CardExpansion.current(after))
        assertEquals(listOf("e1"), open(after, screen.expanded, screen.manual))
        assertEquals(setOf("e2"), screen.advanceCards(after))
        assertEquals(listOf("e2"), open(after, screen.expanded, screen.manual))
        // The header of e2 comes after the collapsed e1, a single item.
        assertEquals(1, CardExpansion.headerIndex("e2", after, screen.expanded, screen.manual))
        assertEquals(1 + 2 + 2 + 1, CardExpansion.headerIndex("e3", after, screen.expanded, screen.manual))
        assertNull(CardExpansion.headerIndex("gone", after, screen.expanded, screen.manual))

        // An entry removed from the workout is forgotten.
        screen.toggleCard("e3", open = false)
        screen.advanceCards(after.take(2))
        assertEquals(mapOf("e1" to false, "e2" to true), screen.expanded)
        assertEquals(emptySet(), screen.manual)
    }

    @Test
    fun rowIndexCountsTheItemsBeforeTheRow() {
        // e1 done and collapsed (1 item); e2 open: header, column header, then its rows.
        val workout = listOf(entry("e1", true, true), entry("e2", true, false, false), entry("e3", false))
        assertEquals(1 + 2 + 0, CardExpansion.rowIndex("e2-s0", workout, emptyMap(), emptySet()))
        assertEquals(1 + 2 + 2, CardExpansion.rowIndex("e2-s2", workout, emptyMap(), emptySet()))
        // From the list's first card index, as the screen lays them out.
        assertEquals(3 + 1 + 2 + 1, CardExpansion.rowIndex("e2-s1", workout, emptyMap(), emptySet(), first = 3))
        // A collapsed card has no rows: the set's header.
        assertEquals(1 + (3 + 3), CardExpansion.rowIndex("e3-s0", workout, emptyMap(), emptySet()))
        assertEquals(0, CardExpansion.rowIndex("e1-s1", workout, emptyMap(), emptySet()))
        // Opened by hand, the done card's rows count.
        assertEquals(2 + 1, CardExpansion.rowIndex("e1-s1", workout, mapOf("e1" to true), setOf("e1")))
        assertNull(CardExpansion.rowIndex("gone", workout, emptyMap(), emptySet()))
    }

    @Test
    fun advanceWithNoExercisesKeepsRestoredChoices() {
        // Restored after a process death, while the workout is still loading.
        val screen = ActiveWorkoutScreenState(expanded = mapOf("e1" to false, "e2" to true), manual = setOf("e2"))
        assertEquals(emptySet(), screen.advanceCards(emptyList()))
        assertEquals(mapOf("e1" to false, "e2" to true), screen.expanded)
        assertEquals(setOf("e2"), screen.manual)

        // Once the exercises arrive, the choice by hand still holds.
        val workout = listOf(entry("e1", false), entry("e2", true))
        screen.advanceCards(workout)
        assertEquals(mapOf("e1" to true, "e2" to true), screen.expanded)
        assertEquals(setOf("e2"), screen.manual)
    }

    @Test
    fun openCardsSurviveTheSaver() {
        val scope = SaverScope { true }
        val state = ActiveWorkoutScreenState(expanded = mapOf("e:1" to true, "e2" to false), manual = setOf("e2"))
        val saved = with(ActiveWorkoutScreenState.Saver) { scope.save(state) }
        val restored = requireNotNull(saved?.let { ActiveWorkoutScreenState.Saver.restore(it) })
        assertEquals(state.expanded, restored.expanded)
        assertEquals(state.manual, restored.manual)
        assertTrue(CardExpansion.isOpen("e:1", emptyList(), restored.expanded, restored.manual))
        assertFalse(CardExpansion.isOpen("e2", emptyList(), restored.expanded, restored.manual))
    }
}
