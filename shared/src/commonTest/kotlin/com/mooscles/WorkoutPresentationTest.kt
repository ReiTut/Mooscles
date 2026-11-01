package com.mooscles

import com.mooscles.data.*
import com.mooscles.domain.RepsPr
import com.mooscles.domain.WeightPr
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.ui.PreferencesUi
import com.mooscles.ui.PreviousSetUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.RoutineTargetUi
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.TargetSetUi
import com.mooscles.ui.UiState
import com.mooscles.ui.WorkoutUi
import kotlin.math.nextUp
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Common tests (JVM and iOS): presentation caches must invalidate by saved values, never just row counts. */
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
        // routine-a is the active workout's source: its target line follows the edit (phase 4B, decision 8), and
        // the sets the workout logged stay the same objects.
        assertNotSame(first.active, next.active)
        first.active!!.exercises.zip(next.active!!.exercises).forEach { (before, after) -> assertSame(before.sets, after.sets) }
        assertEquals(15, next.active!!.exercises[0].target!!.working.single().reps)
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

    private val assisted = Exercise(id = "assisted-dip", name = "Assisted dip", muscle = MuscleGroup.TRICEPS,
        equipment = Equipment.SELECTORIZED_MACHINE, convention = LoadConvention.ASSISTANCE)

    /** 30 workouts (one active) with repeated loads, assistance and warm-ups, so every record path appears. */
    private fun generatedHistory(random: Random): AppData {
        val movements = listOf(bench, pull, assisted)
        val workouts = List(30) { index ->
            val start = 10_000L + index * 1_000L
            if (index == 17) Workout("w-$index", "Active", startedAt = start)
            else Workout("w-$index", "Session $index", startedAt = start, endedAt = start + 500, status = WorkoutStatus.FINISHED)
        }
        val entries = mutableListOf<WorkoutExercise>()
        val sets = mutableListOf<WorkoutSet>()
        workouts.forEach { workout ->
            repeat(random.nextInt(1, 4)) { position ->
                val e = entry("${workout.id}-e$position", workout.id, movements.random(random), position)
                entries += e
                repeat(random.nextInt(1, 5)) { index ->
                    sets += WorkoutSet("${e.id}-s$index", e.id, position = index, reps = random.nextInt(1, 10),
                        weightKg = random.nextInt(0, 9) * 2.5, completed = random.nextInt(5) != 0,
                        warmup = random.nextInt(6) == 0, assistance = random.nextInt(8) == 0)
                }
            }
        }
        return AppData(exercises = movements, workouts = workouts.shuffled(random),
            workoutExercises = entries.shuffled(random), sets = sets.shuffled(random))
    }

    private data class RecordFacts(val exerciseId: String, val convention: String, val kind: RecordKind, val weightKg: Double,
        val reps: Int, val previousKg: Double?, val previousReps: Int?, val assistance: Boolean, val workoutId: String, val date: Long)

    private fun RecordUi.facts() = RecordFacts(exercise.id, exercise.convention, kind, weightKg, reps, previousKg, previousReps,
        assistance, workoutId, date)

    @Test fun historyRecordsUnchangedByBatch() {
        val random = Random(2026)
        var beaten = 0
        var first = 0
        var reps = 0
        repeat(20) { round ->
            val data = generatedHistory(random)
            val ui = WorkoutPresentation().present(data, PreferencesUi())
            val finished = data.workouts.filter { it.status == WorkoutStatus.FINISHED }
            assertEquals(finished.sortedByDescending { it.startedAt }.map { it.id }, ui.history.map { it.id })
            ui.history.forEach { workout ->
                val w = finished.single { it.id == workout.id }
                val date = w.endedAt ?: w.startedAt
                val weightPrs = WorkoutCalculations.prsForWorkout(data, w.id)
                val repsPrs = WorkoutCalculations.repsPrsForWorkout(data, w.id)
                val expected = weightPrs.map {
                    RecordFacts(it.exerciseId, it.convention.label, RecordKind.WEIGHT, it.newWeightKg, it.reps,
                        it.previousWeightKg, null, false, w.id, date)
                } + repsPrs.map {
                    RecordFacts(it.exerciseId, it.convention.label, RecordKind.REPS, it.weightKg, it.newReps,
                        null, it.previousReps, it.assistance, w.id, date)
                }
                assertEquals(expected, workout.records.map { it.facts() }, "round $round workout ${w.id}")
                workout.exercises.forEach { entry ->
                    val pr = weightPrs.firstOrNull { it.exerciseId == entry.exercise.id && it.convention.label == entry.exercise.convention }
                    assertEquals(pr?.newWeightKg, entry.provisionalRecordKg, "round $round entry ${entry.id}")
                    assertEquals(pr?.firstRecord ?: false, entry.firstRecord, "round $round entry ${entry.id}")
                    val own = workout.records.filter { it.exercise.id == entry.exercise.id && it.exercise.convention == entry.exercise.convention }
                    assertTrue(own.containsAll(entry.records), "round $round entry ${entry.id}")
                }
                beaten += workout.records.count { !it.firstRecord }
                first += workout.records.count { it.firstRecord }
                reps += workout.records.count { it.kind == RecordKind.REPS }
            }
        }
        assertTrue(beaten > 20, "beaten records: $beaten")
        assertTrue(first > 20, "first records: $first")
        assertTrue(reps > 20, "reps records: $reps")
    }

    @Test fun activeWorkoutRecordsStillComputedAgainstHistory() {
        val data = AppData(
            exercises = listOf(bench),
            workouts = listOf(
                Workout("older", "Older", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED),
                Workout("latest", "Latest", startedAt = 2000, endedAt = 2500, status = WorkoutStatus.FINISHED),
                Workout("active", "Active", startedAt = 3000)
            ),
            workoutExercises = listOf(entry("older-bench", "older", bench, 0), entry("latest-bench", "latest", bench, 0),
                entry("active-bench", "active", bench, 0)),
            sets = listOf(
                WorkoutSet("o1", "older-bench", reps = 5, weightKg = 100.0, completed = true),
                WorkoutSet("l1", "latest-bench", reps = 5, weightKg = 110.0, completed = true),
                WorkoutSet("a1", "active-bench", reps = 3, weightKg = 120.0, completed = true)
            )
        )
        val ui = WorkoutPresentation().present(data, PreferencesUi())
        val active = ui.active!!
        val record = active.records.single { it.kind == RecordKind.WEIGHT }
        assertEquals(120.0, record.weightKg, 0.0)
        assertEquals(3, record.reps)
        assertEquals(110.0, record.previousKg!!, 0.0)
        assertFalse(record.firstRecord)
        assertEquals("active", record.workoutId)
        assertEquals(120.0, active.exercises.single().provisionalRecordKg!!, 0.0)
        assertFalse(active.exercises.single().firstRecord)
        assertEquals(listOf(record), active.exercises.single().records.filter { it.kind == RecordKind.WEIGHT })
        // The active session must not leak into the history's baseline.
        val latest = ui.history.single { it.id == "latest" }.records.single { it.kind == RecordKind.WEIGHT }
        assertEquals(100.0, latest.previousKg!!, 0.0)
        assertEquals(110.0, ui.records.single { it.kind == RecordKind.WEIGHT }.weightKg, 0.0)
    }

    /** One set of [recordData]: completed unless told otherwise. */
    private data class Row(val id: String, val kg: Double, val reps: Int, val completed: Boolean = true,
        val warmup: Boolean = false, val assistance: Boolean = false)

    /** One finished bench session (startedAt 1000) with [history], then the active one with one entry per list of [today]. */
    private fun recordData(history: List<Row>, vararg today: List<Row>): AppData {
        fun rows(entryId: String, list: List<Row>) = list.mapIndexed { index, row ->
            WorkoutSet(row.id, entryId, position = index, reps = row.reps, weightKg = row.kg, completed = row.completed,
                warmup = row.warmup, assistance = row.assistance)
        }
        val past = if (history.isEmpty()) emptyList() else listOf(entry("past-bench", "past", bench, 0))
        return AppData(exercises = listOf(bench),
            workouts = listOfNotNull(
                Workout("past", "Past", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED).takeIf { history.isNotEmpty() },
                Workout("active", "Active", startedAt = 3000)),
            workoutExercises = past + today.indices.map { entry("today-$it", "active", bench, it) },
            sets = rows("past-bench", history) + today.withIndex().flatMap { (index, list) -> rows("today-$index", list) })
    }

    private fun WorkoutUi.marks() = exercises.flatMap { e -> e.sets.mapNotNull { set -> set.record?.let { set.id to it } } }

    private fun UiState.marks() = active!!.marks()

    private fun present(data: AppData, pounds: Boolean = false) = WorkoutPresentation().present(data, PreferencesUi(pounds = pounds))

    private val heavierToday = recordData(listOf(Row("h1", 80.0, 6)),
        listOf(Row("t1", 85.0, 6), Row("t2", 87.5, 6), Row("t3", 87.5, 6)))

    @Test fun weightRecordMarksExactlyTheChosenSet() {
        assertEquals(listOf("t2" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), present(heavierToday).marks())
    }

    @Test fun heavierTieBreaksByReps() {
        val data = recordData(listOf(Row("h1", 80.0, 6)), listOf(Row("t1", 87.5, 5), Row("t2", 87.5, 6)))
        assertEquals(listOf("t2" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), present(data).marks())
    }

    @Test fun repsRecordMarksTheFirstWithMostRepsAtThatLoad() {
        val data = recordData(listOf(Row("h1", 80.0, 6)), listOf(Row("t1", 80.0, 8), Row("t2", 80.0, 8)))
        assertEquals(listOf("t1" to SetRecordUi(RecordKind.REPS, previousReps = 6)), present(data).marks())
    }

    @Test fun firstRecordMarksNothing() {
        val ui = present(recordData(emptyList(), listOf(Row("t1", 85.0, 6), Row("t2", 87.5, 6))))
        // The first records exist (the summary lists them); only the per-set mark is withheld (D18).
        assertTrue(ui.active!!.records.isNotEmpty())
        assertTrue(ui.active!!.records.all { it.firstRecord })
        assertEquals(emptyList(), ui.marks())
    }

    @Test fun warmupAndAssistanceRules() {
        val data = recordData(listOf(Row("h1", 80.0, 6), Row("h2", 20.0, 5, assistance = true)),
            listOf(Row("warmup", 100.0, 5, warmup = true), Row("assisted-heavy", 120.0, 3, assistance = true),
                Row("assisted-reps", 20.0, 8, assistance = true)))
        assertEquals(listOf("assisted-reps" to SetRecordUi(RecordKind.REPS, previousReps = 5)), present(data).marks())
    }

    @Test fun ulpToleranceIsNotARecord() {
        val ui = present(recordData(listOf(Row("h1", 80.0, 6)), listOf(Row("t1", 80.0.nextUp().nextUp(), 6))))
        assertTrue(ui.active!!.records.isEmpty())
        assertEquals(emptyList(), ui.marks())
    }

    @Test fun poundsDoNotChangeTheMarkedSet() {
        assertEquals(listOf("t2" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), present(heavierToday, pounds = true).marks())
        val reps = recordData(listOf(Row("h1", 80.0, 6)), listOf(Row("t1", 80.0, 8), Row("t2", 80.0, 8)))
        assertEquals(listOf("t1" to SetRecordUi(RecordKind.REPS, previousReps = 6)), present(reps, pounds = true).marks())
    }

    @Test fun recordMarksOneSetAcrossTwoEntriesOfTheSameExercise() {
        val data = recordData(listOf(Row("h1", 85.0, 5)),
            listOf(Row("pending", 90.0, 5, completed = false), Row("lighter", 80.0, 5)), listOf(Row("done", 90.0, 5)))
        val active = present(data).active!!
        assertEquals(listOf("today-0", "today-1"), active.exercises.map { it.id })
        assertEquals(listOf("done" to SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)), active.marks())
        assertTrue(active.exercises[0].sets.none { it.record != null })
    }

    @Test fun uncheckingTheRecordSetRemovesTheMark() {
        val presenter = WorkoutPresentation()
        fun unchecked(vararg ids: String) = heavierToday.copy(sets = heavierToday.sets.map {
            if (it.id in ids) it.copy(completed = false) else it
        })
        assertEquals(listOf("t2" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), presenter.present(heavierToday, PreferencesUi()).marks())
        // The mark moves to the set the domain picks next, and leaves when no set beats the history.
        assertEquals(listOf("t3" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), presenter.present(unchecked("t2"), PreferencesUi()).marks())
        assertEquals(listOf("t1" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0)), presenter.present(unchecked("t2", "t3"), PreferencesUi()).marks())
        assertEquals(emptyList(), presenter.present(unchecked("t1", "t2", "t3"), PreferencesUi()).marks())
    }

    /** Every set mark, in history and in the active session, is exactly the domain's choice, with the weight one first. */
    @Test fun setRecordsFollowTheDomainOnGeneratedHistories() {
        val random = Random(1729)
        var marks = 0
        repeat(20) { round ->
            val data = generatedHistory(random)
            val ui = WorkoutPresentation().present(data, PreferencesUi())
            (ui.history + listOfNotNull(ui.active)).forEach { workout ->
                val weight = WorkoutCalculations.prsForWorkout(data, workout.id).filterNot { it.firstRecord }
                val reps = WorkoutCalculations.repsPrsForWorkout(data, workout.id).filterNot { it.firstRecord }
                val expected = LinkedHashMap<String, SetRecordUi>()
                reps.forEach { expected[it.setId] = SetRecordUi(RecordKind.REPS, previousReps = it.previousReps) }
                weight.forEach { expected[it.setId] = SetRecordUi(RecordKind.WEIGHT, previousKg = it.previousWeightKg) }
                assertEquals(expected, workout.marks().toMap(), "round $round workout ${workout.id}")
                marks += expected.size
            }
        }
        assertTrue(marks > 20, "marked sets: $marks")
    }

    /**
     * Plan decision 5. Today's rules cannot beat both records with one set (a heavier load has no earlier set at the
     * same load to beat on reps), so the precedence is pinned on the merge itself.
     */
    @Test fun aSetHoldingBothRecordsShowsTheWeightOne() {
        val weight = WeightPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 80.0, 85.0, 8, false, "s1")
        val reps = listOf(RepsPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 85.0, false, 6, 8, false, "s1"),
            RepsPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 60.0, false, 10, 12, false, "s2"),
            RepsPr("bench", "Bench", LoadConvention.TOTAL_LOAD, 50.0, false, null, 12, true, "s3"))
        val first = weight.copy(previousWeightKg = null, firstRecord = true, setId = "s4")
        assertEquals(mapOf("s1" to SetRecordUi(RecordKind.WEIGHT, previousKg = 80.0), "s2" to SetRecordUi(RecordKind.REPS, previousReps = 10)),
            WorkoutPresentation.setRecords(listOf(weight, first), reps))
    }

    private fun previousData(): AppData = AppData(
        exercises = listOf(bench),
        workouts = listOf(
            Workout("older", "Older", startedAt = 1000, endedAt = 1500, status = WorkoutStatus.FINISHED),
            Workout("latest", "Latest", startedAt = 2000, endedAt = 2500, status = WorkoutStatus.FINISHED),
            Workout("active", "Active", startedAt = 3000)),
        workoutExercises = listOf(entry("older-bench", "older", bench, 0), entry("latest-bench", "latest", bench, 0),
            entry("active-bench", "active", bench, 0)),
        sets = listOf(
            WorkoutSet("o1", "older-bench", reps = 5, weightKg = 60.0, completed = true),
            WorkoutSet("l1", "latest-bench", position = 0, reps = 10, weightKg = 40.0, completed = true, warmup = true),
            WorkoutSet("l2", "latest-bench", position = 1, reps = 8, weightKg = 80.0, completed = true),
            WorkoutSet("l3", "latest-bench", position = 2, reps = 7, weightKg = 80.0),
            WorkoutSet("l4", "latest-bench", position = 3, reps = 6, weightKg = 82.5, completed = true),
            WorkoutSet("a1", "active-bench", position = 0, reps = 8, weightKg = 80.0),
            WorkoutSet("a2", "active-bench", position = 1, reps = 8, weightKg = 80.0)))

    @Test fun previousSetsAreTheLastSessionsCompletedSetsInOrder() {
        val ui = present(previousData())
        val entry = ui.active!!.exercises.single()
        assertEquals(listOf(PreviousSetUi(40.0, 10, true, false), PreviousSetUi(80.0, 8, false, false),
            PreviousSetUi(82.5, 6, false, false)), entry.previousSets)
        assertEquals(2000L, entry.previousDate)
        // In pounds the sets stay in exact kg; only the text follows the unit.
        assertEquals(entry.previousSets, present(previousData(), pounds = true).active!!.exercises.single().previousSets)
    }

    /** Only the active workout suggests from the session before; a finished one keeps just the "previous" text. */
    @Test fun historyEntriesCarryNoPreviousSets() {
        val latest = present(previousData()).history.single { it.id == "latest" }.exercises.single()
        assertEquals(emptyList(), latest.previousSets)
        assertNull(latest.previousDate)
        assertEquals("60 kg × 5", latest.previous)
    }

    @Test fun noHistoryHasNoPreviousSets() {
        val data = previousData().let { d -> d.copy(workouts = d.workouts.filter { it.id == "active" },
            workoutExercises = d.workoutExercises.filter { it.workoutId == "active" },
            sets = d.sets.filter { it.workoutExerciseId == "active-bench" }) }
        val entry = present(data).active!!.exercises.single()
        assertEquals(emptyList(), entry.previousSets)
        assertNull(entry.previousDate)
        assertNull(entry.previous)
    }

    @Test fun previousSetsAreReusedWhenOnlyTheActiveWorkoutChanges() {
        val presenter = WorkoutPresentation()
        val data = previousData()
        val before = presenter.present(data, PreferencesUi()).active!!.exercises.single()
        val after = presenter.present(data.copy(sets = data.sets.map {
            if (it.id == "a1") it.copy(completed = true, weightKg = 82.5) else it
        }), PreferencesUi()).active!!.exercises.single()
        assertNotSame(before, after)
        assertSame(before.previousSets, after.previousSets)
        assertEquals(before.previousDate, after.previousDate)
    }

    // Phase 4B (decisions 8 and 22, F16): the target line of the active workout and the routine's last session.

    private val row = Exercise(id = "row", name = "Row", muscle = MuscleGroup.BACK,
        equipment = Equipment.CABLE, convention = LoadConvention.TOTAL_LOAD)

    private fun targets(state: UiState) = state.active!!.exercises.map { it.target }

    @Test fun targetComesFromTheSourceRoutineWorkingSets() {
        val data = fixture().let { d -> d.copy(routineSets = d.routineSets.filterNot { it.routineExerciseId == "plan-a" } + listOf(
            // An imported warm-up may still say it follows the record: it is a warm-up all the same.
            RoutineSet("w1", "plan-a", position = 0, targetReps = 10, targetWeightKg = 40.0, inheritPr = true, warmup = true),
            RoutineSet("t1", "plan-a", position = 1, targetReps = 6, targetWeightKg = 22.0),
            RoutineSet("t2", "plan-a", position = 2, targetReps = 8, targetWeightKg = 20.0, inheritPr = false),
        )) }
        assertEquals(listOf(
            RoutineTargetUi(listOf(TargetSetUi(6, 22.0), TargetSetUi(8, 20.0)), warmups = 1),
            RoutineTargetUi(listOf(TargetSetUi(12, null)), warmups = 0),
            RoutineTargetUi(listOf(TargetSetUi(10, 17.5)), warmups = 0),
        ), targets(WorkoutPresentation().present(data, PreferencesUi())))
    }

    @Test fun sameExerciseTwiceMatchesByOccurrence() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        val planA = RoutineTargetUi(listOf(TargetSetUi(6, 22.0)), 0)
        val planB = RoutineTargetUi(listOf(TargetSetUi(10, 17.5)), 0)
        assertEquals(listOf(planA, RoutineTargetUi(listOf(TargetSetUi(12, null)), 0), planB), targets(first))
        // The n-th bench of the workout takes the n-th bench of the routine, by position, whatever the entry's id.
        val moved = presenter.present(data.copy(workoutExercises = data.workoutExercises.map {
            when (it.id) { "active-b" -> it.copy(position = 0); "active-a" -> it.copy(position = 2); else -> it }
        }), PreferencesUi())
        assertEquals(listOf("active-b", "active-pull", "active-a"), moved.active!!.exercises.map { it.id })
        assertEquals(planA, moved.active!!.exercises[0].target)
        assertEquals(planB, moved.active!!.exercises[2].target)
    }

    @Test fun replacedOrAddedEntryHasNoTarget() {
        val data = fixture().let { d -> d.copy(exercises = d.exercises + row) }
        // Pull-up replaced by an exercise outside the routine, and a third bench added: no target for either.
        val replaced = data.copy(workoutExercises = data.workoutExercises.map {
            if (it.id == "active-pull") entry("active-pull", "active", row, 1) else it
        } + entry("active-c", "active", bench, 3))
        val shown = targets(WorkoutPresentation().present(replaced, PreferencesUi()))
        assertEquals(RoutineTargetUi(listOf(TargetSetUi(6, 22.0)), 0), shown[0])
        assertNull(shown[1])
        assertEquals(RoutineTargetUi(listOf(TargetSetUi(10, 17.5)), 0), shown[2])
        assertNull(shown[3])
        // F16, accepted: the workout keeps no link to the routine entry it came from. Pull-up replaced by a bench
        // makes it the second bench, which takes the routine's second bench; the bench after it has none left.
        val swapped = data.copy(workoutExercises = data.workoutExercises.map {
            if (it.id == "active-pull") entry("active-pull", "active", bench, 1) else it
        })
        assertEquals(listOf(RoutineTargetUi(listOf(TargetSetUi(6, 22.0)), 0), RoutineTargetUi(listOf(TargetSetUi(10, 17.5)), 0), null),
            targets(WorkoutPresentation().present(swapped, PreferencesUi())))
    }

    @Test fun deletedRoutineDropsTheTarget() {
        val presenter = WorkoutPresentation()
        val data = fixture()
        val first = presenter.present(data, PreferencesUi())
        assertTrue(targets(first).all { it != null })
        val deleted = data.copy(routines = data.routines.filterNot { it.id == "routine-a" },
            routineExercises = data.routineExercises.filterNot { it.routineId == "routine-a" },
            routineSets = emptyList())
        val next = presenter.present(deleted, PreferencesUi())
        assertEquals(listOf(null, null, null), targets(next))
        // The sets the workout logged are the same objects: only the line went away.
        first.active!!.exercises.zip(next.active!!.exercises).forEach { (before, after) -> assertSame(before.sets, after.sets) }
    }

    @Test fun noSourceRoutineNoTarget() {
        val data = fixture().let { d -> d.copy(workouts = d.workouts.map { if (it.id == "active") it.copy(sourceRoutineId = null) else it }) }
        assertEquals(listOf(null, null, null), targets(WorkoutPresentation().present(data, PreferencesUi())))
    }

    @Test fun finishedWorkoutsHaveNoTarget() {
        val data = fixture().let { d -> d.copy(workouts = d.workouts.map {
            if (it.status == WorkoutStatus.FINISHED) it.copy(sourceRoutineId = "routine-a") else it
        }) }
        val presenter = WorkoutPresentation()
        val state = presenter.present(data, PreferencesUi())
        assertEquals(listOf("latest", "older"), state.history.map { it.id })
        assertTrue(state.history.flatMap { it.exercises }.all { it.target == null })
        assertTrue(state.active!!.exercises.all { it.target != null })
        // Rebuilt once the routines are known (a unit change rebuilds the history), still without a target.
        val pounds = presenter.present(data, PreferencesUi(pounds = true))
        assertNotSame(state.history, pounds.history)
        assertTrue(pounds.history.flatMap { it.exercises }.all { it.target == null })
    }

    @Test fun lastFinishedAtIsTheLatestFinishedWorkoutOfTheRoutine() {
        val data = fixture().let { d -> d.copy(workouts = d.workouts.map {
            if (it.status == WorkoutStatus.FINISHED) it.copy(sourceRoutineId = "routine-a") else it
        }) }
        val routines = WorkoutPresentation().present(data, PreferencesUi()).routines.associateBy { it.id }
        // The active workout of routine-a started later (3000): only finished sessions count, by their end.
        assertEquals(2500L, routines.getValue("routine-a").lastFinishedAt)
        assertNull(routines.getValue("routine-b").lastFinishedAt)
    }

    @Test fun routineIdentityKeptWhenOnlyAnotherRoutinesHistoryChanges() {
        val presenter = WorkoutPresentation()
        val data = fixture().let { d -> d.copy(workouts = d.workouts.map {
            if (it.id == "latest") it.copy(sourceRoutineId = "routine-a") else it
        }) }
        val first = presenter.present(data, PreferencesUi())
        assertSame(first.routines, presenter.present(data.copy(), PreferencesUi()).routines)
        val finishedB = data.copy(workouts = data.workouts +
            Workout("b-done", "B", startedAt = 3500, endedAt = 4000, status = WorkoutStatus.FINISHED, sourceRoutineId = "routine-b"))
        val next = presenter.present(finishedB, PreferencesUi())
        assertNotSame(first.routines, next.routines)
        assertSame(first.routines[0], next.routines[0])
        assertEquals(2500L, next.routines[0].lastFinishedAt)
        assertEquals(4000L, next.routines[1].lastFinishedAt)
        // A target edit in routine-a keeps routine-b, and routine-a's untouched entries.
        val editedData = finishedB.copy(routineSets = finishedB.routineSets.map {
            if (it.id == "target-b") it.copy(targetReps = 9) else it
        })
        val edited = presenter.present(editedData, PreferencesUi())
        assertSame(next.routines[1], edited.routines[1])
        assertNotSame(next.routines[0], edited.routines[0])
        assertSame(next.routines[0].exercises[0], edited.routines[0].exercises[0])
        assertSame(next.routines[0].exercises[1], edited.routines[0].exercises[1])
        assertEquals(9, edited.routines[0].exercises[2].setTargets.single().reps)
        // The active workout's entries whose target did not change stay the same objects.
        assertSame(next.active!!.exercises[0], edited.active!!.exercises[0])
        assertSame(next.active!!.exercises[1], edited.active!!.exercises[1])
        // Renaming routine-b, which is not the active workout's source, keeps the whole active workout.
        val renamed = presenter.present(editedData.copy(routines = editedData.routines.map {
            if (it.id == "routine-b") it.copy(name = "Renamed") else it
        }), PreferencesUi())
        assertSame(edited.active, renamed.active)
    }

    @Test fun warmupFlowsToRoutineSetUi() {
        val data = fixture().let { d -> d.copy(routineSets = d.routineSets.filterNot { it.routineExerciseId == "plan-a" } + listOf(
            RoutineSet("w1", "plan-a", position = 0, targetReps = 10, targetWeightKg = 40.0, inheritPr = true, warmup = true),
            RoutineSet("t1", "plan-a", position = 1, targetReps = 6, targetWeightKg = 22.0),
        )) }
        val plan = WorkoutPresentation().present(data, PreferencesUi()).routines.single { it.id == "routine-a" }.exercises[0]
        assertEquals(listOf(
            RoutineSetUi("w1", 10, 40.0, inherited = false, warmup = true),
            RoutineSetUi("t1", 6, 22.0, inherited = true, warmup = false),
        ), plan.setTargets)
        // The plan's summary columns follow the first working set, as the repository mirrors them.
        assertEquals(2, plan.sets)
        assertEquals(6, plan.reps)
        assertEquals(22.0, plan.weightKg!!, 0.0)
    }

    @Test fun routineOfAMissingFolderIsPresentedInTheDefaultOne() {
        val data = fixture().let { d -> d.copy(
            routineFolders = listOf(RoutineFolder(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME, 0), RoutineFolder("upper", "Upper", 1)),
            routines = listOf(d.routines[0].copy(folderId = "upper"), d.routines[1].copy(folderId = "gone")),
        ) }
        val routines = WorkoutPresentation().present(data, PreferencesUi()).routines
        assertEquals(listOf("upper", RoutineFolder.DEFAULT_ID), routines.map { it.folderId })
    }
}
