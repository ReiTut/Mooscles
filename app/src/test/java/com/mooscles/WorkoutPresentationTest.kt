package com.mooscles

import com.mooscles.data.*
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.RecordKind
import org.junit.Assert.*
import org.junit.Test

/** Plain JVM tests: presentation caches must invalidate by saved values, never just row counts. */
class WorkoutPresentationTest {
    private val bench = Exercise(id = "bench", name = "Bench", muscle = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL, convention = LoadConvention.TOTAL_LOAD)
    private val pull = Exercise(id = "pull", name = "Pull-up", muscle = MuscleGroup.BACK,
        equipment = Equipment.BODYWEIGHT, convention = LoadConvention.BODYWEIGHT)

    private fun entry(id: String, workout: String, exercise: Exercise, position: Int) =
        WorkoutExercise(id = id, workoutId = workout, exerciseId = exercise.id, name = exercise.name,
            muscle = exercise.muscle, equipment = exercise.equipment, convention = exercise.convention,
            position = position, restSeconds = 120 + position)

    private fun fixture(): AppData = AppData(
        exercises = listOf(bench, pull),
        routines = listOf(Routine("routine-a", "First", 0), Routine("routine-b", "Second", 1)),
        routineExercises = listOf(
            RoutineExercise("plan-a", "routine-a", bench.id, position = 0),
            RoutineExercise("plan-pull", "routine-a", pull.id, position = 1),
            RoutineExercise("plan-b", "routine-a", bench.id, position = 2)
        ),
        routineSets = listOf(
            RoutineSet("target-a", "plan-a", targetReps = 6, targetWeightKg = 22.0),
            RoutineSet("target-pull", "plan-pull", targetReps = 12),
            RoutineSet("target-b", "plan-b", targetReps = 10, targetWeightKg = 17.5, inheritPr = false)
        ),
        workouts = listOf(
            Workout("active", "Active", startedAt = 3000, sourceRoutineId = "routine-a"),
            Workout("latest", "Latest", startedAt = 2000, endedAt = 2500, status = WorkoutStatus.FINISHED),
            Workout("older", "Older", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED)
        ),
        workoutExercises = listOf(
            entry("active-a", "active", bench, 0), entry("active-pull", "active", pull, 1),
            entry("active-b", "active", bench, 2), entry("latest-bench", "latest", bench, 0),
            entry("latest-pull", "latest", pull, 1), entry("older-bench", "older", bench, 0)
        ),
        sets = listOf(
            WorkoutSet("a1", "active-a", position = 0, reps = 6, weightKg = 22.0),
            WorkoutSet("a2", "active-a", position = 1),
            WorkoutSet("pull1", "active-pull", reps = 12),
            WorkoutSet("b1", "active-b", reps = 10, weightKg = 17.5),
            WorkoutSet("latest-record", "latest-bench", reps = 6, weightKg = 22.0, completed = true),
            WorkoutSet("latest-bodyweight", "latest-pull", reps = 12, completed = true),
            WorkoutSet("older-record", "older-bench", reps = 7, weightKg = 20.0, completed = true)
        )
    )

    @Test fun singleActiveEditReusesHistoryRoutinesUnaffectedCardsAndUnaffectedSetRows() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        val changed = data.copy(sets = data.sets.map { if (it.id == "a1") it.copy(reps = 8, weightKg = 22.5) else it })
        val next = presenter.present(changed, PreferencesUi())
        assertSame(first.history, next.history)
        assertSame(first.records, next.records)
        assertSame(first.routines, next.routines)
        assertSame(first.exercises, next.exercises)
        assertNotSame(first.active, next.active)
        val before = first.active!!.exercises
        val after = next.active!!.exercises
        assertNotSame(before[0], after[0])
        assertNotSame(before[0].sets[0], after[0].sets[0])
        assertSame(before[0].sets[1], after[0].sets[1])
        assertSame(before[1], after[1])
        assertSame(before[2], after[2])
        assertEquals(listOf("active-a", "active-pull", "active-b"), after.map { it.id })
        assertEquals(listOf("bench", "pull", "bench"), after.map { it.exercise.id })
        assertEquals(8, after[0].sets[0].reps)
        assertEquals(22.5, after[0].sets[0].weightKg, 0.0)
        assertEquals(10, after[2].sets[0].reps)
        assertEquals(17.5, after[2].sets[0].weightKg, 0.0)
        assertEquals(0.0, after[1].sets[0].weightKg, 0.0)
        val unchanged = presenter.present(changed.copy(), PreferencesUi())
        assertSame(next.active, unchanged.active)
    }

    @Test fun correctingAndDeletingFinishedPerformanceInvalidatesRecordsPreviousAndActivePrs() {
        val presenter = WorkoutPresentation()
        val base = fixture()
        val data = base.copy(sets = base.sets.map { if (it.id == "a1") it.copy(weightKg = 23.0, reps = 4, completed = true) else it })
        val first = presenter.present(data, PreferencesUi())
        assertEquals("22 kg × 6", first.active!!.exercises[0].previous)
        assertEquals(23.0, first.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        val corrected = data.copy(sets = data.sets.map { if (it.id == "latest-record") it.copy(weightKg = 25.0, reps = 3) else it })
        val next = presenter.present(corrected, PreferencesUi())
        assertNotSame(first.history, next.history)
        assertNotSame(first.records, next.records)
        assertSame(first.routines, next.routines)
        assertEquals(25.0, next.records.single { it.kind == RecordKind.WEIGHT && it.exercise.id == "bench" }.weightKg, 0.0)
        assertEquals("25 kg × 3", next.active!!.exercises[0].previous)
        assertEquals("25 kg × 3", next.active!!.exercises[2].previous)
        assertNull(next.active!!.exercises[0].provisionalRecordKg)
        val deletedIds = corrected.workoutExercises.filter { it.workoutId == "latest" }.map { it.id }.toSet()
        val deleted = corrected.copy(workouts = corrected.workouts.filterNot { it.id == "latest" },
            workoutExercises = corrected.workoutExercises.filterNot { it.id in deletedIds },
            sets = corrected.sets.filterNot { it.workoutExerciseId in deletedIds })
        val finalUi = presenter.present(deleted, PreferencesUi())
        assertEquals(listOf("older"), finalUi.history.map { it.id })
        assertEquals(20.0, finalUi.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
        assertEquals("20 kg × 7", finalUi.active!!.exercises[0].previous)
        assertEquals(23.0, finalUi.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        assertNull(finalUi.active!!.exercises[1].previous)
        assertTrue(finalUi.records.none { it.exercise.id == "pull" })
    }

    @Test fun routineReorderingAndIndependentTargetEditsInvalidateImmediately() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        val changed = data.copy(
            routines = data.routines.map { it.copy(position = 1 - it.position) },
            routineExercises = data.routineExercises.map {
                it.copy(position = when (it.id) { "plan-b" -> 0; "plan-pull" -> 1; else -> 2 })
            },
            routineSets = data.routineSets.map {
                if (it.id == "target-b") it.copy(targetWeightKg = null, targetReps = 15) else it
            }
        )
        val next = presenter.present(changed, PreferencesUi())
        assertNotSame(first.routines, next.routines)
        assertSame(first.history, next.history)
        assertSame(first.records, next.records)
        assertSame(first.active, next.active)
        assertEquals(listOf("routine-b", "routine-a"), next.routines.map { it.id })
        val entries = next.routines.last().exercises
        assertEquals(listOf("plan-b", "plan-pull", "plan-a"), entries.map { it.id })
        assertNull(entries[0].setTargets.single().weightKg)
        assertEquals(15, entries[0].setTargets.single().reps)
        assertFalse(entries[0].setTargets.single().inherited)
        assertEquals(22.0, entries[2].setTargets.single().weightKg!!, 0.0)
        assertEquals(6, entries[2].setTargets.single().reps)
    }

    @Test fun poundsChangesRefreshPreviousDisplayWithoutChangingStoredKgOrRoutineTargets() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val kg = presenter.present(data, PreferencesUi())
        val pounds = presenter.present(data, PreferencesUi(pounds = true))
        assertEquals("22 kg × 6", kg.active!!.exercises[0].previous)
        assertEquals("48.5 lb × 6", pounds.active!!.exercises[0].previous)
        assertEquals("Bodyweight × 12", pounds.active!!.exercises[1].previous)
        assertEquals(22.0, pounds.active!!.exercises[0].sets[0].weightKg, 0.0)
        assertSame(kg.routines, pounds.routines)
        val back = presenter.present(data, PreferencesUi())
        assertEquals("22 kg × 6", back.active!!.exercises[0].previous)
        assertEquals(22.0, data.sets.first { it.id == "a1" }.weightKg, 0.0)
    }

    @Test fun inPlaceHistoricalCorrectionsAndDeletionInvalidateDefensiveCacheKeys() {
        val base = fixture()
        val rows = base.sets.map { if (it.id == "a1") it.copy(weightKg = 23.0, reps = 4, completed = true) else it }.toMutableList()
        val workouts = base.workouts.toMutableList()
        val entries = base.workoutExercises.toMutableList()
        val data = base.copy(workouts = workouts, workoutExercises = entries, sets = rows)
        val presenter = WorkoutPresentation()
        val first = presenter.present(data, PreferencesUi())
        assertEquals(23.0, first.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        val index = rows.indexOfFirst { it.id == "latest-record" }
        rows[index] = rows[index].copy(weightKg = 25.0, reps = 3)
        val corrected = presenter.present(data, PreferencesUi())
        assertNotSame(first.history, corrected.history)
        assertEquals("25 kg × 3", corrected.active!!.exercises[0].previous)
        assertNull(corrected.active!!.exercises[0].provisionalRecordKg)
        assertEquals(25.0, corrected.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
        workouts.removeAll { it.id == "latest" }
        // Orphaned rows must not keep the deleted workout in the historical baseline.
        val deleted = presenter.present(data, PreferencesUi())
        assertEquals(listOf("older"), deleted.history.map { it.id })
        assertEquals("20 kg × 7", deleted.active!!.exercises[0].previous)
        assertEquals(23.0, deleted.active!!.exercises[0].provisionalRecordKg!!, 0.0)
        assertEquals(20.0, deleted.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
        assertEquals(23.0, data.sets.first { it.id == "a1" }.weightKg, 0.0)
    }

    @Test fun historyCacheInvalidatesConventionEditsAndActiveBaselineWhenWorkoutOrderChanges() {
        val presenter = WorkoutPresentation()
        val base = fixture()
        val data = base.copy(sets = base.sets.map { if (it.id == "a1") it.copy(weightKg = 23.0, reps = 4, completed = true) else it })
        presenter.present(data, PreferencesUi())
        val movedVariation = data.copy(workoutExercises = data.workoutExercises.map {
            if (it.id == "latest-bench") it.copy(convention = LoadConvention.PER_HAND) else it
        })
        val changed = presenter.present(movedVariation, PreferencesUi())
        assertEquals("20 kg × 7", changed.active!!.exercises[0].previous)
        assertEquals(2, changed.records.count { it.kind == RecordKind.WEIGHT })
        val backdated = data.copy(workouts = data.workouts.map { if (it.id == "active") it.copy(startedAt = 1500) else it })
        val result = presenter.present(backdated, PreferencesUi())
        val record = result.active!!.records.single { it.kind == RecordKind.WEIGHT }
        assertEquals(20.0, record.previousKg!!, 0.0)
        assertEquals("20 kg × 7", result.active!!.exercises[0].previous)
    }
}
