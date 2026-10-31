package com.mooscles

import com.mooscles.data.*
import com.mooscles.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkoutStartupPresentationTest {
    private val movement = Exercise("bench", "Bench", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD)
    private val oldWorkout = Workout("history", "Previous", 1000, 1500, WorkoutStatus.FINISHED)
    private val active = Workout("active", "Today", 2000, sourceRoutineId = "routine")
    private fun entry(id: String, workoutId: String, position: Int = 0) = WorkoutExercise(id, workoutId,
        movement.id, movement.name, movement.muscle, movement.equipment, movement.convention, position, 123)
    private val oldEntry = entry("old-entry", oldWorkout.id)
    private val currentEntry = entry("current-entry", active.id)
    private val historySet = WorkoutSet("history-set", oldEntry.id, reps = 8, weightKg = 24.0, completed = true)
    private val currentSet = WorkoutSet("current-set", currentEntry.id, reps = 6, weightKg = 31.125)
    private fun full(row: WorkoutSet = currentSet, historical: WorkoutSet? = historySet) = AppData(
        exercises = listOf(movement), workouts = listOfNotNull(active, oldWorkout.takeIf { historical != null }),
        workoutExercises = listOfNotNull(currentEntry, oldEntry.takeIf { historical != null }),
        sets = listOfNotNull(row, historical))
    private fun essential(row: WorkoutSet = currentSet) = ActiveWorkoutSnapshot(active,
        listOf(movement), listOf(currentEntry), listOf(row))

    @Test fun essentialStartsWithExactSavedRowsAndFullEnrichmentReusesTheEditor() {
        val presenter = WorkoutPresentation()
        val archive = full().copy(workouts = listOf(oldWorkout), workoutExercises = listOf(oldEntry), sets = listOf(historySet))
        val before = presenter.present(archive, PreferencesUi())
        val first = presenter.presentEssential(essential(), PreferencesUi())
        assertTrue(first.secondaryLoading)
        assertEquals(active.id, first.active!!.id)
        assertEquals("routine", first.active!!.sourceRoutineId)
        assertEquals(31.125, first.active!!.exercises.single().sets.single().weightKg, 0.0)
        assertEquals(123, first.active!!.exercises.single().restSeconds)
        assertSame(before.history, first.history)
        assertSame(before.routines, first.routines)
        val enriched = presenter.present(full(), PreferencesUi())
        assertFalse(enriched.secondaryLoading)
        assertSame(first.active!!.exercises.single().sets, enriched.active!!.exercises.single().sets)
        assertEquals("24 kg × 8", enriched.active!!.exercises.single().previous)
    }

    @Test fun pendingSavedEditCannotBeOverwrittenByAnOlderPreparedArchive() {
        val initial = essential()
        val handoff = WorkoutStartHandoff(1, initial)
        val presenter = WorkoutPresentation()
        val first = presenter.presentEssential(initial, PreferencesUi())
        val oldSource = full()
        val delayed = PreparedWorkoutContent(oldSource, handoff, presenter.present(oldSource, PreferencesUi()))
        val changed = currentSet.copy(reps = 7, weightKg = 32.125, completed = true)
        val pending = handoff.copy(snapshot = initial.withSet(changed), revision = 1, editing = true)
        val editing = presenter.presentEssential(pending.snapshot, PreferencesUi())
        assertFalse(pending.snapshot.matches(oldSource))
        assertSame(editing, currentWorkoutContent(editing, delayed, oldSource, pending, emptySet()))
        assertEquals(32.125, editing.active!!.exercises.single().sets.single().weightKg, 0.0)
        assertTrue(editing.active!!.exercises.single().sets.single().completed)
        assertEquals(first.active!!.id, editing.active!!.id)
    }

    @Test fun newerSourceRejectsEarlierWorkerResultEvenWhenSessionIdIsTheSame() {
        val presenter = WorkoutPresentation()
        val firstSource = full()
        val first = presenter.present(firstSource, PreferencesUi())
        val nextSource = full(currentSet.copy(reps = 9))
        val next = presenter.present(nextSource, PreferencesUi())
        val stale = PreparedWorkoutContent(firstSource, null, first)
        assertSame(next, currentWorkoutContent(next, stale, nextSource, null, emptySet()))
        val current = PreparedWorkoutContent(nextSource, null, next)
        assertSame(next, currentWorkoutContent(first, current, nextSource, null, emptySet()))
    }

    @Test fun endedOrReplacedSessionsCannotBeRevivedByDelayedPreparation() {
        val presenter = WorkoutPresentation()
        val data = full()
        val old = WorkoutStartHandoff(1, essential())
        val first = presenter.presentEssential(old.snapshot, PreferencesUi())
        val prepared = PreparedWorkoutContent(data, old, first)
        assertNull(currentWorkoutContent(first, prepared, data, old, setOf(active.id)).active)
        val replacement = old.copy(token = 2, snapshot = old.snapshot.copy(workout = active.copy(id = "replacement")))
        val replacedUi = presenter.presentEssential(replacement.snapshot, PreferencesUi())
        assertSame(replacedUi, currentWorkoutContent(replacedUi, prepared, data, replacement, emptySet()))
        assertEquals("replacement", replacedUi.active!!.id)
    }

    @Test fun fullCatchupRevalidatesHistoricalCorrectionsAndDeletion() {
        val presenter = WorkoutPresentation()
        val completed = currentSet.copy(completed = true)
        presenter.present(full(completed), PreferencesUi())
        presenter.presentEssential(essential(completed), PreferencesUi())
        val corrected = presenter.present(full(completed, historySet.copy(weightKg = 40.0)), PreferencesUi())
        assertTrue(corrected.active!!.exercises.single().records.none { it.kind == RecordKind.WEIGHT })
        assertEquals("40 kg × 8", corrected.active!!.exercises.single().previous)
        val deleted = presenter.present(full(completed, null), PreferencesUi())
        assertNull(deleted.active!!.exercises.single().previous)
        assertTrue(deleted.active!!.exercises.single().records.any { it.kind == RecordKind.WEIGHT && it.firstRecord })
    }

    @Test fun ordinaryEditsAfterCatchupDoNotReturnToPartialPrPresentation() {
        val presenter = WorkoutPresentation()
        val first = presenter.present(full(currentSet.copy(completed = true)), PreferencesUi())
        val changed = presenter.present(full(currentSet.copy(reps = 7, completed = true)), PreferencesUi())
        assertFalse(first.secondaryLoading)
        assertFalse(changed.secondaryLoading)
        assertTrue(first.active!!.records.isNotEmpty())
        assertTrue(changed.active!!.records.isNotEmpty())
        assertEquals(7, changed.active!!.exercises.single().sets.single().reps)
    }

    @Test fun matchingRequiresEverySavedIdentityValueFlagOrderAndRestPrescription() {
        val snapshot = essential()
        assertTrue(snapshot.matches(full()))
        assertFalse(snapshot.matches(full(currentSet.copy(completed = true))))
        assertFalse(snapshot.matches(full(currentSet.copy(warmup = true))))
        assertFalse(snapshot.matches(full(currentSet.copy(assistance = true))))
        assertFalse(snapshot.matches(full(currentSet.copy(weightKg = 31.12500000001))))
        assertFalse(snapshot.withRest(currentEntry.id, 180).matches(full()))
        assertFalse(snapshot.copy(workout = active.copy(id = "different")).matches(full()))
    }
}
