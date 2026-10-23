package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

/** Exact old presenter is retained below as a parity and measured preparation baseline. */
class WorkoutPreparationPerformanceTest {
    @Test fun chainedNearEqualLoadsPreserveLegacyRawEncounterOrderWhenSelectingRepetitionBaselines() {
        val exercise = Exercise("variation", "Bench", MuscleGroup.CHEST, Equipment.BARBELL)
        val older = Workout("older", "Older", 1_000, 1_500, WorkoutStatus.FINISHED)
        val newer = Workout("newer", "Newer", 2_000, 2_500, WorkoutStatus.FINISHED)
        val active = Workout("active", "Active", 3_000)
        fun entry(id: String, workout: Workout, position: Int) = WorkoutExercise(id, workout.id, exercise.id,
            exercise.name, exercise.muscle, exercise.equipment, exercise.convention, position)
        val olderEntry = entry("older-entry", older, 1)
        val newerEntry = entry("newer-entry", newer, 0)
        val activeEntry = entry("active-entry", active, 0)
        val a = 20.0
        val b = (1..3).fold(a) { value, _ -> Math.nextUp(value) }
        val c = (1..6).fold(a) { value, _ -> Math.nextUp(value) }
        val saved = AppData(exercises = listOf(exercise), workouts = listOf(active, newer, older),
            workoutExercises = listOf(newerEntry, activeEntry, olderEntry), sets = listOf(
                WorkoutSet("older-a", olderEntry.id, 0, 10, a, completed = true),
                WorkoutSet("older-c", olderEntry.id, 1, 14, c, completed = true),
                WorkoutSet("newer-b", newerEntry.id, 0, 12, b, completed = true),
                WorkoutSet("active-a", activeEntry.id, 0, 11, a, completed = true)))
        val reference = legacyPresent(saved, PreferencesUi(), TimerUi(), null, false)
        val record = reference.active!!.records.single { it.kind == RecordKind.REPS }
        assertEquals(11, record.reps)
        assertNull(record.previousReps)
        assertEquals(reference, WorkoutPresenter().present(saved, PreferencesUi()))
    }

    @Test fun deterministicRandomizedSavedSnapshotsMatchExistingPresentation() {
        val random = kotlin.random.Random(142)
        repeat(32) { case ->
            val data = fixture(4, 9)
            val workouts = data.workouts.map { workout ->
                val start = 1_000L * random.nextInt(1, 5)
                workout.copy(startedAt = start, endedAt = if (workout.status == WorkoutStatus.FINISHED) start + random.nextInt(1, 3) else null)
            }.shuffled(random)
            val catalog = data.exercises.map { exercise -> exercise.copy(name =
                if (case % 3 == 0) "Identical exercise names" else if (random.nextBoolean()) "Renamed ${exercise.id}" else exercise.name) }
            val entries = data.workoutExercises.map { entry -> entry.copy(position = random.nextInt(0, 7),
                name = if (case % 3 == 0) "Identical exercise names" else if (random.nextInt(3) == 0) "Snapshot ${random.nextInt(4)}" else entry.name) }.shuffled(random)
            val sets = data.sets.map { set ->
                val weight = listOf(0.0, 10.0, 20.0, 21.0)[random.nextInt(4)]
                set.copy(position = random.nextInt(0, 4), weightKg = if (random.nextBoolean()) weight else Math.nextUp(weight),
                    reps = random.nextInt(0, 17), completed = random.nextInt(4) != 0,
                    warmup = random.nextInt(6) == 0, assistance = random.nextInt(5) == 0)
            }.shuffled(random)
            val saved = data.copy(exercises = catalog, workouts = workouts, workoutExercises = entries, sets = sets)
            val prefs = PreferencesUi(pounds = case % 2 == 0)
            val old = legacyPresent(saved, prefs, TimerUi(), null, false)
            val new = WorkoutPresenter().present(saved, prefs)
            assertEquals("Historical presentation case $case", old.history, new.history)
            assertEquals("Global records case $case", old.records, new.records)
            assertEquals("Active workout case $case", old.active, new.active)
            assertEquals(old.routines, new.routines)
        }
    }

    @Test fun indexedPresentationMatchesExistingRecordsHistoryTargetsAndPreviousCards() {
        val data = fixture(7, 12)
        listOf(false, true).forEach { pounds ->
            val prefs = PreferencesUi(pounds = pounds)
            assertEquals(legacyPresent(data, prefs, TimerUi(), null, false), WorkoutPresenter().present(data, prefs))
        }
    }

    @Test fun duplicateVariationMetadataAndInterleavedRecordOrderingMatchExistingPresentation() {
        val data = fixture(5, 9)
        val duplicate = data.workoutExercises.first()
        val edited = data.copy(workoutExercises = data.workoutExercises.map {
            if (it.id == duplicate.id) it.copy(name = "Old snapshot name") else it
        }, exercises = data.exercises.map { if (it.id == duplicate.exerciseId) it.copy(name = "Renamed catalog") else it })
        assertEquals(legacyPresent(edited, PreferencesUi(), TimerUi(), null, false), WorkoutPresenter().present(edited, PreferencesUi()))
    }

    @Test fun backdatedActiveSessionsUseOnlyEarlierEligibleFinishedHistory() {
        val data = fixture(9, 6)
        val active = data.activeWorkout!!
        val backdated = data.copy(workouts = data.workouts.map { if (it.id == active.id) it.copy(startedAt = 3_000L) else it })
        assertEquals(legacyPresent(backdated, PreferencesUi(), TimerUi(), null, false), WorkoutPresenter().present(backdated, PreferencesUi()))
    }

    @Test fun incompleteWarmupAssistanceBodyweightAndFloatingPointRecordsMatchExistingPresentation() {
        val data = fixture(4, 12)
        val edited = data.copy(sets = data.sets.mapIndexed { index, row -> when {
            index % 31 == 0 -> row.copy(weightKg = Double.NaN)
            index % 29 == 0 -> row.copy(reps = 0, completed = true)
            index % 23 == 0 -> row.copy(weightKg = -1.0)
            index % 19 == 0 -> row.copy(weightKg = Math.nextUp(row.weightKg))
            else -> row
        } })
        assertEquals(legacyPresent(edited, PreferencesUi(pounds = true), TimerUi(), null, false),
            WorkoutPresenter().present(edited, PreferencesUi(pounds = true)))
    }

    @Test fun everyCardAndSetIsPreparedBeforePublishingIncludingTheLastCardAfterRestoration() {
        val data = fixture(20, 60)
        val first = WorkoutPresenter().present(data, PreferencesUi())
        val restored = WorkoutPresenter().present(data, PreferencesUi())
        val active = first.active!!
        assertEquals(60, active.exercises.size)
        assertTrue(active.exercises.all { it.sets.size == 4 && !it.previous.isNullOrEmpty() })
        assertEquals(data.workoutItems(active.id).map { it.id }, active.exercises.map { it.id })
        assertEquals(data.exerciseSets(active.exercises.last().id).map { it.id }, active.exercises.last().sets.map { it.id })
        assertEquals(first, restored)
        assertEquals(143, active.exercises.last().restSeconds)
        assertEquals("routine", active.sourceRoutineId)
    }

    @Test fun unrelatedRoutinePreferencesAndTimerCopiesReuseTheEntirePreparedWorkout() {
        val data = fixture(8, 24)
        val presenter = WorkoutPresenter()
        val initial = presenter.present(data, PreferencesUi())
        val metrics = presenter.metrics()
        val unrelated = data.copy(routines = data.routines.map { it.copy(name = "Renamed plan") })
        val updated = presenter.present(unrelated, PreferencesUi(theme = "dark", autoRest = false))
        assertSame(initial.active, updated.active)
        assertSame(initial.active!!.exercises, updated.active!!.exercises)
        assertSame(initial.history, updated.history)
        assertSame(initial.records, updated.records)
        repeat(120) { tick ->
            val timed = updated.copy(timer = TimerUi(remainingSeconds = 120 - tick, running = true))
            assertSame(initial.active, timed.active)
            assertSame(initial.active.exercises, timed.active!!.exercises)
        }
        assertEquals(metrics.historyPreparations, presenter.metrics().historyPreparations)
        assertEquals(metrics.preparedExerciseCards, presenter.metrics().preparedExerciseCards)
    }

    @Test fun changingOneSetOnlyRebuildsItsOwningExerciseAndNeverRescansHistory() {
        val data = fixture(6, 24)
        val presenter = WorkoutPresenter()
        val before = presenter.present(data, PreferencesUi())
        val metrics = presenter.metrics()
        val entry = before.active!!.exercises[11]
        val row = data.sets.single { it.id == entry.sets.last().id }
        val updated = data.copy(sets = data.sets.map { if (it.id == row.id) it.copy(reps = 37, weightKg = 83.5, completed = false) else it })
        val after = presenter.present(updated, PreferencesUi())
        assertNotSame(entry, after.active!!.exercises[11])
        before.active.exercises.indices.filter { it != 11 }.forEach { index ->
            assertSame("Unchanged card $index was rebuilt", before.active.exercises[index], after.active.exercises[index])
        }
        assertSame(before.history, after.history)
        assertEquals(metrics.historyPreparations, presenter.metrics().historyPreparations)
        assertEquals(metrics.preparedExerciseCards + 1, presenter.metrics().preparedExerciseCards)
        assertEquals(37, after.active.exercises[11].sets.last().reps)
        assertEquals(83.5, after.active.exercises[11].sets.last().weightKg, 0.0)
    }

    @Test fun orderOnlyChangeMovesPersistentInstancesAndReusesAllCardAndSetObjects() {
        val data = fixture(3, 18)
        val presenter = WorkoutPresenter()
        val before = presenter.present(data, PreferencesUi())
        val order = data.workoutItems(data.activeWorkout!!.id).map { it.id }.reversed()
        val positions = order.withIndex().associate { it.value to it.index }
        val reordered = data.copy(workoutExercises = data.workoutExercises.map { entry ->
            positions[entry.id]?.let { entry.copy(position = it) } ?: entry
        })
        val metrics = presenter.metrics()
        val after = presenter.present(reordered, PreferencesUi())
        assertEquals(order, after.active!!.exercises.map { it.id })
        val old = before.active!!.exercises.associateBy { it.id }
        after.active.exercises.forEach { card ->
            assertSame(old.getValue(card.id), card)
            assertSame(old.getValue(card.id).sets, card.sets)
        }
        assertEquals(metrics.preparedExerciseCards, presenter.metrics().preparedExerciseCards)
        assertSame(before.history, after.history)
        assertEquals(after, WorkoutPresenter().present(reordered, PreferencesUi()))
    }

    @Test fun editedAndDeletedHistoryInvalidateRecordsAndPreviousCardsWithoutStaleCaches() {
        val data = fixture(6, 12)
        val presenter = WorkoutPresenter()
        presenter.present(data, PreferencesUi())
        val deleted = data.workouts.first { it.status == WorkoutStatus.FINISHED }
        val entries = data.workoutExercises.filter { it.workoutId == deleted.id }.map { it.id }.toSet()
        val changed = data.copy(workouts = data.workouts.filterNot { it.id == deleted.id },
            workoutExercises = data.workoutExercises.filterNot { it.id in entries },
            sets = data.sets.filterNot { it.workoutExerciseId in entries })
        assertEquals(legacyPresent(changed, PreferencesUi(), TimerUi(), null, false), presenter.present(changed, PreferencesUi()))
        val discarded = changed.copy(workouts = changed.workouts.filterNot { it.status == WorkoutStatus.ACTIVE },
            workoutExercises = changed.workoutExercises.filterNot { it.workoutId == data.activeWorkout!!.id })
        assertNull(presenter.present(discarded, PreferencesUi()).active)
    }

    @Test fun measuredSubstantialHistoryAndRepeatedSnapshotsDoNotRebuildPreparedCards() {
        // This measures CPU preparation, not Android frames. Device scroll timing is reported
        // separately. Include repeated variations and every full card's previous-performance text.
        val data = fixture(30, 48)
        val prefs = PreferencesUi()
        WorkoutPresenter().present(fixture(2, 6), prefs)
        val startLegacy = System.nanoTime()
        val legacy = legacyPresent(data, prefs, TimerUi(), null, false)
        val legacyNanos = System.nanoTime() - startLegacy
        val startPrepared = System.nanoTime()
        val presenter = WorkoutPresenter()
        val prepared = presenter.present(data, prefs)
        val preparedNanos = System.nanoTime() - startPrepared
        assertEquals(legacy, prepared)
        val metrics = presenter.metrics()
        val repeatedStarted = System.nanoTime()
        repeat(120) {
            val state = presenter.present(data.copy(), prefs.copy(theme = if (it % 2 == 0) "dark" else "light"))
            assertSame(prepared.active, state.active)
            assertSame(prepared.history, state.history)
        }
        val repeatNanos = System.nanoTime() - repeatedStarted
        assertEquals(metrics.preparedExerciseCards, presenter.metrics().preparedExerciseCards)
        assertEquals(metrics.historyPreparations, presenter.metrics().historyPreparations)
        assertTrue("Preparation must finish within a generous 10s host ceiling", preparedNanos < 10_000_000_000L)
        val report = "Workout preparation host benchmark: 30 history sessions / 48 cards / 5952 sets; " +
            "legacy=${legacyNanos / 1_000_000.0}ms; indexed=${preparedNanos / 1_000_000.0}ms; " +
            "120 cached snapshots=${repeatNanos / 1_000_000.0}ms; cardsBuilt=${metrics.preparedExerciseCards}; " +
            "repeatedExtraCards=${presenter.metrics().preparedExerciseCards - metrics.preparedExerciseCards}"
        println(report)
        java.io.File("build/reports/workout-preparation-benchmark.txt").apply { parentFile.mkdirs(); writeText(report + "\n") }
    }

    private fun fixture(historyCount: Int, exercisesPerWorkout: Int): AppData {
        val variations = 6
        val catalog = (0 until variations).map { index ->
            Exercise("variation-$index", "Exercise $index", MuscleGroup.entries[index], Equipment.entries[index],
                LoadConvention.entries[index % LoadConvention.entries.size], custom = index % 2 == 0)
        }
        val workouts = (0..historyCount).map { index -> Workout("workout-$index", "Session $index", 1_000L * (index + 1),
            if (index < historyCount) 1_000L * (index + 1) + 500 else null,
            if (index < historyCount) WorkoutStatus.FINISHED else WorkoutStatus.ACTIVE,
            sourceRoutineId = if (index == historyCount) "routine" else null) }
        val entries = workouts.flatMapIndexed { workoutIndex, workout -> (0 until exercisesPerWorkout).map { index ->
            val exercise = catalog[index % catalog.size]
            WorkoutExercise("entry-$workoutIndex-$index", workout.id, exercise.id, exercise.name, exercise.muscle,
                exercise.equipment, exercise.convention, index, 120 + index % 36)
        } }
        val sets = entries.flatMap { entry -> (0 until 4).map { index ->
            val workoutIndex = entry.workoutId.substringAfterLast('-').toInt()
            WorkoutSet("${entry.id}-set-$index", entry.id, index, reps = 7 + index + workoutIndex % 4,
                weightKg = if (entry.convention == LoadConvention.BODYWEIGHT) 0.0 else (20 + workoutIndex / 3 + index).toDouble(),
                completed = entry.workoutId != workouts.last().id || index < 2,
                warmup = index == 0, assistance = index == 2 && entry.position % 3 == 0)
        } }
        val plans = (0 until exercisesPerWorkout).map { index -> RoutineExercise("plan-$index", "routine", catalog[index % variations].id,
            position = index, plannedSets = 4, targetReps = 8, targetWeightKg = 20.0, restSeconds = 120 + index % 36) }
        val targets = plans.flatMap { plan -> (0 until 4).map { index -> RoutineSet("${plan.id}-set-$index", plan.id, index, 8 + index, 20.0 + index, index % 2 == 0) } }
        return AppData(catalog, listOf(Routine("routine", "Substantial routine")), plans, workouts, entries, sets, targets)
    }

    private fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
    private fun legacyPresent(data: AppData, prefs: PreferencesUi, timer: TimerUi, error: String?, loading: Boolean): UiState {
        val exercises = data.exercises.associateBy { it.id }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        fun workout(w: Workout): WorkoutUi {
            val prs = WorkoutCalculations.prsForWorkout(data, w.id)
            val repsPrs = WorkoutCalculations.repsPrsForWorkout(data, w.id)
            val records = prs.map { p ->
                val e = data.workoutItems(w.id).first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.newWeightKg, p.reps, p.previousWeightKg, w.id, w.endedAt ?: w.startedAt)
            } + repsPrs.map { p ->
                val e = data.workoutItems(w.id).first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.weightKg, p.newReps, workoutId = w.id, date = w.endedAt ?: w.startedAt,
                    kind = RecordKind.REPS, previousReps = p.previousReps, assistance = p.assistance)
            }
            val entries = data.workoutItems(w.id).map { e ->
                val previous = WorkoutCalculations.performanceHistory(data, e.exerciseId, e.convention)
                    .firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                WorkoutExerciseUi(e.id, snapshot(e), data.exerciseSets(e.id).map { SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance) },
                    e.restSeconds, previous?.sets?.joinToString(" · ") {
                        val load = if (it.weightKg == 0.0 && !it.assistance && e.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                        else "${if (it.assistance || e.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${String.format(Locale.US, "%.2f", WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"}"
                        "$load × ${it.reps}"
                    }, pr?.newWeightKg, pr?.firstRecord ?: false,
                    records.filter { record ->
                        record.exercise.id == e.exerciseId && record.exercise.convention == e.convention.label &&
                            data.exerciseSets(e.id).any { set ->
                                val sameLoad = kotlin.math.abs(set.weightKg - record.weightKg) <=
                                    maxOf(Math.ulp(set.weightKg), Math.ulp(record.weightKg)) * 4
                                val assisted = set.assistance || e.convention == LoadConvention.ASSISTANCE
                                WorkoutCalculations.validCompleted(set) && !set.warmup && sameLoad &&
                                    if (record.kind == RecordKind.WEIGHT) !assisted
                                    else assisted == record.assistance && set.reps == record.reps
                            }
                    })
            }
            return WorkoutUi(w.id, w.name, w.startedAt, w.endedAt, entries, records, sourceRoutineId = w.sourceRoutineId)
        }
        val records = WorkoutCalculations.records(data).map { r ->
            val e = data.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
            RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
        } + WorkoutCalculations.repsRecords(data).map { r ->
            val e = data.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
            RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps,
                workoutId = r.workoutId, date = r.achievedAt, kind = RecordKind.REPS, assistance = r.assistance)
        }
        return UiState(loading, data.exercises.map { it.toUi() }, data.routines.sortedBy { it.position }.map { r ->
            RoutineUi(r.id, r.name, data.routineItems(r.id).mapNotNull { e -> exercises[e.exerciseId]?.let {
                val targets = data.plannedSets(e.id).map { row -> RoutineSetUi(row.id, row.targetReps, row.targetWeightKg, row.inheritPr) }
                PlannedExerciseUi(e.id, it.toUi(), targets.size, targets.firstOrNull()?.reps ?: 0,
                    targets.firstOrNull()?.weightKg, e.restSeconds ?: com.mooscles.domain.RestDuration.DEFAULT_SECONDS, targets)
            } })
        }, data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedByDescending { it.startedAt }.map(::workout),
            data.activeWorkout?.let(::workout), records, timer, prefs, error)
    }
}
