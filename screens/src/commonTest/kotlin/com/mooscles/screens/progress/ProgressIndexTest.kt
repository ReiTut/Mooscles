package com.mooscles.screens.progress

import com.mooscles.domain.EstimatedOneRepMax
import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import kotlin.math.abs
import kotlin.math.nextDown
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProgressIndexTest {
    /** Newest first, as `UiState.history` orders it. */
    private fun index(vararg workouts: WorkoutUi, records: List<RecordUi> = emptyList()): ProgressIndex =
        ProgressIndex.build(workouts.sortedByDescending { it.startedAt }, records)

    private fun ProgressIndex.only(exercise: ExerciseUi): SessionPoint = sessions(keyOf(exercise)).single()

    @Test
    fun loadIsTheHeaviestWorkingSetTiesByRepsThenFirst() {
        val heavy = 100.0.nextUp()
        val heavier = heavy.nextUp() // unit round-trip noise: the same load as [heavy]
        val point = index(workout("w", DAY, entry(bench, set(100.0, 5), set(heavy, 8), set(heavier, 8), set(90.0, 12))))
            .only(bench)

        assertEquals(SetValue(heavy, heavy, 8), point.load)
    }

    @Test
    fun estimateIsTheBestEpleyOfSetsUpToTenReps() {
        val point = index(workout("w", DAY, entry(bench, set(100.0, 3), set(90.0, 8), set(95.0, 10), set(110.0, 12))))
            .only(bench)

        // 110 × 12 would estimate 154 kg, but it is above ten reps; it is still the heaviest set.
        assertEquals(SetValue(EstimatedOneRepMax.estimate(95.0, 10)!!, 95.0, 10), point.estimate)
        assertEquals(SetValue(110.0, 110.0, 12), point.load)
    }

    @Test
    fun volumeOfEachWorkoutMatchesItsWorkoutVolume() {
        val history = listOf(
            workout("a", DAY,
                entry(bench, set(60.0, 10, warmup = true), set(100.0, 5), set(40.0, 12, assistance = true),
                    set(120.0, 3, completed = false)),
                entry(dumbbellPress, set(30.0, 10), set(32.5, 8)),
                entry(bench, set(102.5, 4)),
            ),
            workout("b", 3 * DAY,
                entry(pullUp, set(0.0, 10), set(10.0, 6), set(0.0, 8, warmup = true)),
                entry(assistedDip, set(30.0, 8), set(20.0, 6)),
                entry(customRow, set(55.0, 12), set(0.0, 0)),
            ),
            workout("c", 5 * DAY,
                entry(ExerciseUi("custom-new", "New", "Back", "Cable", "Something new", custom = true), set(70.0, 8)),
                entry(bench.copy(convention = "Per hand"), set(45.0, 10)),
            ),
        )
        val index = index(*history.toTypedArray())

        history.forEach { workout ->
            val keys = workout.exercises.map { SeriesKey(it.exercise.id, it.exercise.convention) }.distinct()
            val sum = keys.sumOf { key -> index.sessions(key).filter { it.workoutId == workout.id }.sumOf { it.volumeKg } }
            assertTrue(abs(sum - workout.volumeKg) <= 1e-9, "${workout.id}: $sum != ${workout.volumeKg}")
        }
    }

    @Test
    fun twoEntriesOfOneExerciseInAWorkoutAreOneSession() {
        val first = entry(bench, set(100.0, 5), set(100.0, 4, completed = false))
        val second = entry(bench, set(105.0, 3))
        val point = index(workout("w", DAY, first, entry(customRow, set(50.0, 10)), second)).only(bench)

        assertEquals(SetValue(105.0, 105.0, 3), point.load)
        assertEquals(listOf(first.sets[0].id, second.sets[0].id), point.sets.map { it.id })
        assertEquals(100.0 * 5 + 105.0 * 3, point.volumeKg)
        assertEquals("w", point.workoutId)
        assertEquals("Workout w", point.workoutName)
        assertEquals(DAY, point.startedAt)
    }

    @Test
    fun warmupsAndIncompleteSetsFollowTheDomain() {
        val warmup = set(140.0, 10, warmup = true)
        val working = set(100.0, 5)
        val point = index(workout("w", DAY, entry(bench, warmup, working, set(150.0, 12, completed = false)))).only(bench)

        assertEquals(SetValue(100.0, 100.0, 5), point.load)
        assertEquals(SetValue(EstimatedOneRepMax.estimate(100.0, 5)!!, 100.0, 5), point.estimate)
        assertEquals(SetValue(5.0, 100.0, 5), point.reps)
        assertEquals(140.0 * 10 + 100.0 * 5, point.volumeKg)
        assertEquals(listOf(warmup.id, working.id), point.sets.map { it.id })
    }

    @Test
    fun bodyweightWithoutLoadHasOnlyReps() {
        val sessions = index(workout("w", DAY, entry(pullUp, set(0.0, 8), set(0.0, 10)))).sessions(keyOf(pullUp))
        val point = sessions.single()

        assertNull(point.load)
        assertNull(point.estimate)
        assertEquals(SetValue(10.0, 0.0, 10), point.reps)
        assertEquals(0.0, point.volumeKg)
        assertEquals(listOf(ProgressMetric.REPS), ProgressMetrics.available(pullUp.convention, sessions))
    }

    @Test
    fun bodyweightWithLoadAddsAddedWeightAndVolume() {
        val sessions = index(workout("w", DAY, entry(pullUp, set(10.0, 6), set(0.0, 10)))).sessions(keyOf(pullUp))
        val point = sessions.single()

        assertEquals(SetValue(10.0, 10.0, 6), point.load)
        assertNull(point.estimate, "no estimate on added weight (D5)")
        assertEquals(SetValue(10.0, 0.0, 10), point.reps)
        assertEquals(60.0, point.volumeKg)
        assertEquals(
            listOf(ProgressMetric.REPS, ProgressMetric.LOAD, ProgressMetric.VOLUME),
            ProgressMetrics.available(pullUp.convention, sessions),
        )
    }

    @Test
    fun assistedSetsStayOutOfLoadAndInTheirRepsGroup() {
        val index = index(workout("w", DAY,
            entry(bench, set(40.0, 15, assistance = true), set(80.0, 6)),
            entry(assistedDip, set(30.0, 8), set(20.0, 6)),
        ))
        val benchPoint = index.only(bench)
        val dipPoint = index.only(assistedDip)

        assertEquals(SetValue(80.0, 80.0, 6), benchPoint.load)
        assertEquals(SetValue(6.0, 80.0, 6), benchPoint.reps, "the assisted 15 reps are another group")
        assertEquals(480.0, benchPoint.volumeKg)
        assertEquals(SetValue(20.0, 20.0, 6), dipPoint.load, "assistance: the lowest")
        assertEquals(SetValue(8.0, 30.0, 8), dipPoint.reps)
        assertNull(dipPoint.estimate)
        assertEquals(0.0, dipPoint.volumeKg)
    }

    @Test
    fun assistanceIsTheLowestLoggedAboveZero() {
        val low = 15.0.nextUp()
        val sessions = index(
            workout("first", DAY, entry(assistedDip, set(0.0, 10), set(25.0, 8), set(15.0, 6), set(low, 7))),
            workout("second", 2 * DAY, entry(assistedDip, set(0.0, 12))),
        ).sessions(keyOf(assistedDip))

        assertEquals(listOf("first", "second"), sessions.map { it.workoutId }, "oldest first")
        assertEquals(SetValue(low, low, 7), sessions[0].load, "lowest above 0, ties by the most reps")
        assertNull(sessions[1].load, "a session with only 0 has no assistance point")
        assertNull(ProgressMetrics.value(sessions[1], ProgressMetric.LOAD))
        assertEquals(SetValue(12.0, 0.0, 12), sessions[1].reps)
        assertEquals(listOf(ProgressMetric.REPS, ProgressMetric.LOAD), ProgressMetrics.available(assistedDip.convention, sessions))
    }

    @Test
    fun marksMatchTheRecordsExactly() {
        val w1 = workout("w1", DAY,
            entry(bench, set(100.0, 5), set(60.0, 10, assistance = true)),
            entry(pullUp, set(0.0, 8)),
            entry(assistedDip, set(20.0, 8)),
        )
        val w1Records = listOf(
            weightRecord(bench, 100.0, 5, null, w1), repsRecord(bench, 100.0, 5, null, w1),
            repsRecord(bench, 60.0, 10, null, w1, assistance = true), repsRecord(pullUp, 0.0, 8, null, w1),
            repsRecord(assistedDip, 20.0, 8, null, w1, assistance = true),
        )
        val w2 = workout("w2", 3 * DAY,
            entry(bench, set(105.0, 3), set(100.0, 6), set(60.0, 12, assistance = true)),
            entry(pullUp, set(10.0, 5)),
            entry(assistedDip, set(20.0, 10)),
        )
        val w2Records = listOf(
            weightRecord(bench, 105.0, 3, 100.0, w2), repsRecord(bench, 105.0, 3, null, w2),
            repsRecord(bench, 100.0, 6, 5, w2), repsRecord(bench, 60.0, 12, 10, w2, assistance = true),
            weightRecord(pullUp, 10.0, 5, null, w2), repsRecord(pullUp, 10.0, 5, null, w2),
            repsRecord(assistedDip, 20.0, 10, 8, w2, assistance = true),
        )
        val w3 = workout("w3", 5 * DAY, entry(bench, set(100.0, 8)), entry(pullUp, set(12.5, 5), set(0.0, 10)))
        val w3Records = listOf(
            repsRecord(bench, 100.0, 8, 6, w3), weightRecord(pullUp, 12.5, 5, 10.0, w3), repsRecord(pullUp, 0.0, 10, 8, w3),
        )
        val w4 = workout("w4", 7 * DAY, entry(bench, set(60.0, 14, assistance = true), set(90.0, 3)))
        val w4Records = listOf(repsRecord(bench, 60.0, 14, 12, w4, assistance = true), repsRecord(bench, 90.0, 3, null, w4))
        val history = listOf(
            w1.copy(records = w1Records), w2.copy(records = w2Records), w3.copy(records = w3Records), w4.copy(records = w4Records),
        )
        val current = listOf(
            currentOf(w2Records[0]), currentOf(w3Records[0]), currentOf(w2Records[3]), currentOf(w3Records[1]),
            currentOf(w3Records[2]), currentOf(w2Records[6]),
        )
        val index = index(*history.toTypedArray(), records = current)
        val all = history.flatMap { it.records } + current
        val exercises = listOf(bench, pullUp, assistedDip)

        fun group(convention: String) = convention == "Assistance"
        // A mark needs a beaten record of the kind, series, group and session whose set is the plotted one.
        fun beaten(kind: RecordKind, key: SeriesKey, point: SessionPoint): Boolean {
            val plotted = (if (kind == RecordKind.WEIGHT) point.load else point.reps) ?: return false
            return all.any {
                it.kind == kind && !it.firstRecord && it.exercise.id == key.exerciseId &&
                    it.exercise.convention == key.convention && it.workoutId == point.workoutId &&
                    (kind == RecordKind.WEIGHT || it.assistance == group(key.convention)) &&
                    it.weightKg == plotted.weightKg && it.reps == plotted.reps
            }
        }
        var marks = 0
        exercises.forEach { exercise ->
            val key = keyOf(exercise)
            index.sessions(key).forEach { point ->
                assertEquals(beaten(RecordKind.WEIGHT, key, point), point.weightRecord, "${exercise.id} ${point.workoutId}")
                assertEquals(beaten(RecordKind.REPS, key, point), point.repsRecord, "${exercise.id} ${point.workoutId}")
                assertEquals(point.weightRecord && !group(key.convention),
                    ProgressMetrics.marked(point, ProgressMetric.LOAD, key.convention))
                assertEquals(point.repsRecord, ProgressMetrics.marked(point, ProgressMetric.REPS, key.convention))
                if (point.weightRecord) marks++
                if (point.repsRecord) marks++
            }
        }
        // Here every beaten record of a chart's group is its session's plotted set, so each marks it: count them
        // the other way around. The record tests below cover a record set that is not the plotted one.
        val beatenRecords = all.filter { !it.firstRecord }.filter {
            it.kind == RecordKind.WEIGHT || it.assistance == group(it.exercise.convention)
        }.map { Triple(it.kind, it.exercise.id, it.workoutId) }.distinct()
        assertEquals(beatenRecords.size, marks)
        beatenRecords.forEach { (kind, id, workoutId) ->
            val exercise = exercises.first { it.id == id }
            val point = index.sessions(keyOf(exercise)).single { it.workoutId == workoutId }
            assertTrue(if (kind == RecordKind.WEIGHT) point.weightRecord else point.repsRecord, "$kind $id $workoutId")
        }
        // The assisted bench reps record of w4 is beaten, but not in the bench reps chart's group.
        assertFalse(index.sessions(keyOf(bench)).single { it.workoutId == "w4" }.repsRecord)
    }

    @Test
    fun assistanceRepsRecordAtAnotherLoadLeavesThePointUnmarked() {
        // The review's case: 30 × 8 is the session's most reps, but the record is 20 × 7 (beating 20 × 6).
        val earlier = workout("earlier", DAY, entry(assistedDip, set(30.0, 12), set(20.0, 6)))
        val session = workout("session", 3 * DAY, entry(assistedDip, set(30.0, 8), set(20.0, 7)))
        val record = repsRecord(assistedDip, 20.0, 7, 6, session, assistance = true)
        val point = index(earlier, session.copy(records = listOf(record)), records = listOf(currentOf(record)))
            .sessions(keyOf(assistedDip)).last()

        assertEquals(SetValue(8.0, 30.0, 8), point.reps)
        assertFalse(point.repsRecord, "30 × 8 is not the record set")
        assertFalse(ProgressMetrics.marked(point, ProgressMetric.REPS, assistedDip.convention))
        assertFalse(ProgressMetrics.marked(point, ProgressMetric.LOAD, assistedDip.convention))
    }

    @Test
    fun loadPointDrawnByTheWeightRecordIsMarked() {
        val earlier = workout("earlier", DAY, entry(bench, set(100.0, 5)))
        val session = workout("session", 3 * DAY, entry(bench, set(80.0, 10), set(105.0, 3)))
        val point = index(earlier, session.copy(records = listOf(weightRecord(bench, 105.0, 3, 100.0, session))))
            .sessions(keyOf(bench)).last()

        assertEquals(SetValue(105.0, 105.0, 3), point.load)
        assertTrue(point.weightRecord)
        assertTrue(ProgressMetrics.marked(point, ProgressMetric.LOAD, bench.convention))

        // Unit noise: the load point is the noisy ×8 (BestSets), the domain's record the exact ×3; no mark.
        val noisy = 100.0.nextDown().nextDown()
        val other = workout("other", 3 * DAY, entry(bench, set(100.0, 3), set(noisy, 8)))
        val noisyPoint = index(workout("first", DAY, entry(bench, set(95.0, 5))),
            other.copy(records = listOf(weightRecord(bench, 100.0, 3, 95.0, other)))).sessions(keyOf(bench)).last()
        assertEquals(SetValue(noisy, noisy, 8), noisyPoint.load)
        assertFalse(noisyPoint.weightRecord, "the plotted set is not the record's")
    }

    @Test
    fun repsRecordAtALighterLoadLeavesTheRepsPointUnmarked() {
        val earlier = workout("earlier", DAY, entry(bench, set(100.0, 12), set(80.0, 6)))
        val session = workout("session", 3 * DAY, entry(bench, set(100.0, 10), set(80.0, 8)))
        val point = index(earlier, session.copy(records = listOf(repsRecord(bench, 80.0, 8, 6, session))))
            .sessions(keyOf(bench)).last()

        assertEquals(SetValue(10.0, 100.0, 10), point.reps)
        assertFalse(point.repsRecord)
        assertFalse(ProgressMetrics.marked(point, ProgressMetric.REPS, bench.convention))
    }

    @Test
    fun repsTieTakesTheRecordSet() {
        val earlier = workout("earlier", DAY, entry(bench, set(100.0, 9), set(80.0, 6)))
        val session = workout("session", 3 * DAY, entry(bench, set(100.0, 8), set(80.0, 8)))
        val point = index(earlier, session.copy(records = listOf(repsRecord(bench, 80.0, 8, 6, session))))
            .sessions(keyOf(bench)).last()

        assertEquals(SetValue(8.0, 80.0, 8), point.reps, "the same 8 reps, read on the record set")
        assertTrue(point.repsRecord)
    }

    @Test
    fun workoutsEndingTogetherKeepTheirEventsApart() {
        // Same end, different starts; then the same start too, by id. The history's order is ignored.
        val a = workout("a", 2 * DAY, entry(bench, set(100.0, 5))).copy(endedAt = 4 * DAY)
        val b = workout("b", 3 * DAY, entry(bench, set(100.0, 5)), entry(customRow, set(50.0, 10))).copy(endedAt = 4 * DAY)
        val c = workout("c", 3 * DAY, entry(customRow, set(50.0, 10))).copy(endedAt = 4 * DAY)
        val aRecords = listOf(weightRecord(bench, 100.0, 5, null, a))
        val bRecords = listOf(weightRecord(customRow, 50.0, 10, null, b), weightRecord(bench, 100.0, 5, 95.0, b))
        val cRecords = listOf(weightRecord(customRow, 50.0, 10, null, c))
        val history = listOf(a.copy(records = aRecords), b.copy(records = bRecords), c.copy(records = cRecords))
        val expected = listOf("c", "b", "b", "a")

        listOf(history, history.reversed()).forEach { order ->
            val index = ProgressIndex.build(order, records = listOf(currentOf(bRecords[1]), currentOf(bRecords[1])))
            assertEquals(expected, index.events.map { it.record.workoutId })
            assertEquals(listOf(bench.id, customRow.id), index.events.filter { it.record.workoutId == "b" }.map { it.record.exercise.id })
            assertSame(index.events[1], index.currentRecords.single(), "two current records of one event are one")
        }
    }

    @Test
    fun firstRecordIsNeverAMark() {
        val plain = workout("w", DAY, entry(bench, set(100.0, 5)))
        val records = listOf(weightRecord(bench, 100.0, 5, null, plain), repsRecord(bench, 100.0, 5, null, plain))
        val index = index(plain.copy(records = records), records = records.map(::currentOf))
        val point = index.only(bench)

        assertFalse(point.weightRecord)
        assertFalse(point.repsRecord)
        assertFalse(ProgressMetrics.marked(point, ProgressMetric.LOAD, bench.convention))
        assertFalse(ProgressMetrics.marked(point, ProgressMetric.REPS, bench.convention))
    }

    @Test
    fun metricsPerConvention() {
        val load = listOf(ProgressMetric.LOAD, ProgressMetric.E1RM, ProgressMetric.VOLUME)
        listOf("Total load", "Per hand", "One side").forEach {
            assertEquals(load, ProgressMetrics.available(it, emptyList()), it)
        }
        val withoutLoad = index(workout("a", DAY, entry(pullUp, set(0.0, 8)))).sessions(keyOf(pullUp))
        val withLoad = index(
            workout("a", DAY, entry(pullUp, set(0.0, 8))),
            workout("b", 2 * DAY, entry(pullUp, set(5.0, 6))),
        ).sessions(keyOf(pullUp))
        assertEquals(listOf(ProgressMetric.REPS), ProgressMetrics.available("Added weight", withoutLoad))
        assertEquals(listOf(ProgressMetric.REPS), ProgressMetrics.available("Added weight", emptyList()))
        assertEquals(
            listOf(ProgressMetric.REPS, ProgressMetric.LOAD, ProgressMetric.VOLUME),
            ProgressMetrics.available("Added weight", withLoad),
        )
        assertEquals(listOf(ProgressMetric.REPS, ProgressMetric.LOAD), ProgressMetrics.available("Assistance", emptyList()))
    }

    @Test
    fun initialMetricIsTheRequestedOneOrTheFirst() {
        val total = ProgressMetrics.available("Total load", emptyList())
        val bodyweight = ProgressMetrics.available("Added weight", emptyList())
        val assistance = ProgressMetrics.available("Assistance", emptyList())

        assertEquals(ProgressMetric.REPS, ProgressMetrics.initial(ProgressMetric.E1RM, bodyweight))
        assertEquals(ProgressMetric.LOAD, ProgressMetrics.initial(null, total))
        assertEquals(ProgressMetric.VOLUME, ProgressMetrics.initial(ProgressMetric.VOLUME, total))
        assertEquals(ProgressMetric.REPS, ProgressMetrics.initial(null, assistance))
        assertEquals(ProgressMetric.LOAD, ProgressMetrics.initial(ProgressMetric.LOAD, assistance))
    }

    @Test
    fun parseKnowsTheFourRoutesAndNothingElse() {
        assertEquals(ProgressMetric.LOAD, ProgressMetric.parse("load"))
        assertEquals(ProgressMetric.REPS, ProgressMetric.parse("reps"))
        assertEquals(ProgressMetric.E1RM, ProgressMetric.parse("e1rm"))
        assertEquals(ProgressMetric.VOLUME, ProgressMetric.parse("volume"))
        assertNull(ProgressMetric.parse("LOAD"))
        assertNull(ProgressMetric.parse(""))
        assertNull(ProgressMetric.parse(null))
    }

    @Test
    fun unknownConventionLabelFollowsTotalLoad() {
        val odd = ExerciseUi("custom-odd", "Odd", "Back", "Cable", "Something new", custom = true)
        val workout = workout("w", DAY, entry(odd, set(70.0, 8), set(30.0, 12, assistance = true), set(40.0, 10, warmup = true)))
        val sessions = index(workout).sessions(keyOf(odd))
        val point = sessions.single()

        assertEquals(listOf(ProgressMetric.LOAD, ProgressMetric.E1RM, ProgressMetric.VOLUME),
            ProgressMetrics.available(odd.convention, sessions))
        assertEquals(SetValue(70.0, 70.0, 8), point.load)
        assertEquals(SetValue(EstimatedOneRepMax.estimate(70.0, 8)!!, 70.0, 8), point.estimate)
        assertEquals(SetValue(8.0, 70.0, 8), point.reps)
        assertEquals(workout.volumeKg, point.volumeKg)
    }

    @Test
    fun conventionsAreListedMostRecentFirst() {
        val perHand = customRow.copy(convention = "Per hand")
        val oneSide = customRow.copy(convention = "One side")
        val index = index(
            workout("a", DAY, entry(perHand, set(20.0, 10))),
            workout("b", 3 * DAY, entry(customRow, set(45.0, 8))),
            workout("c", 5 * DAY, entry(perHand, set(22.5, 10))),
            workout("d", 7 * DAY, entry(oneSide, set(25.0, 8, completed = false))),
        )

        assertEquals(listOf("Per hand", "Total load"), index.conventionsOf(customRow.id), "a convention needs a session")
        assertEquals(listOf("a", "c"), index.sessions(keyOf(perHand)).map { it.workoutId })
        assertEquals(emptyList(), index.conventionsOf("missing"))
        assertEquals(emptyList(), index.sessions(keyOf(oneSide)))
    }

    @Test
    fun deletedExerciseKeepsItsLatestSnapshot() {
        val old = ExerciseUi("custom-gone", "Old name", "Back", "Cable", "Per hand", custom = true)
        val renamed = old.copy(name = "Renamed", convention = "Total load")
        val index = index(
            workout("a", DAY, entry(old, set(20.0, 10))),
            workout("b", 3 * DAY, entry(renamed, set(45.0, 8))),
        )

        assertEquals(renamed, index.snapshot("custom-gone"))
        assertNull(index.snapshot("custom-unknown"))
    }

    @Test
    fun eventsAreNewestFirstThenWorkoutOrderThenWeightBeforeReps() {
        val older = workout("older", DAY, entry(bench, set(100.0, 5)))
        val olderRecords = listOf(repsRecord(bench, 100.0, 5, null, older), weightRecord(bench, 100.0, 5, null, older))
        val newer = workout("newer", 3 * DAY,
            entry(dumbbellPress, set(30.0, 10)), entry(bench, set(80.0, 10), set(105.0, 3)), entry(bench, set(100.0, 6)),
        )
        val dumbbellReps = repsRecord(dumbbellPress, 30.0, 10, null, newer)
        val benchReps80 = repsRecord(bench, 80.0, 10, null, newer)
        val benchReps100 = repsRecord(bench, 100.0, 6, 5, newer)
        val benchWeight = weightRecord(bench, 105.0, 3, 100.0, newer)
        val index = index(
            older.copy(records = olderRecords),
            newer.copy(records = listOf(benchReps80, benchReps100, benchWeight, dumbbellReps)),
        )

        assertEquals(
            listOf(dumbbellReps, benchWeight, benchReps100, benchReps80, olderRecords[1], olderRecords[0]),
            index.events.map { it.record },
        )
        assertEquals(listOf(0, 1, 1, 1, 0, 0), index.events.map { it.order })
        assertEquals(listOf("Workout newer", "Workout newer", "Workout newer", "Workout newer", "Workout older",
            "Workout older"), index.events.map { it.workoutName })
        assertTrue(index.events.none { it.current })
    }

    @Test
    fun currentMatchesExerciseConventionKindLoadAssistanceAndWorkout() {
        val first = workout("first", DAY, entry(bench, set(95.0, 5)))
        val plain = workout("w", 3 * DAY, entry(bench, set(100.0, 5), set(50.0, 12, assistance = true)))
        val weight = weightRecord(bench, 100.0, 5, 95.0, plain)
        val assisted = repsRecord(bench, 50.0, 12, 10, plain, assistance = true)
        val history = arrayOf(first, plain.copy(records = listOf(weight, assisted)))
        fun currentFlags(vararg records: RecordUi) = index(*history, records = records.toList()).events.map { it.current }

        assertEquals(listOf(false, false), currentFlags())
        assertEquals(listOf(true, false), currentFlags(currentOf(weight).copy(weightKg = 100.0.nextUp())), "same load")
        assertEquals(listOf(false, true), currentFlags(currentOf(assisted)))
        val misses = listOf(
            currentOf(weight).copy(exercise = bench.copy(id = "catalog_barbell_bench_incline")),
            currentOf(weight).copy(exercise = bench.copy(convention = "Per hand")),
            currentOf(weight).copy(kind = RecordKind.REPS),
            currentOf(weight).copy(weightKg = 100.5),
            currentOf(weight).copy(workoutId = "first"),
            currentOf(assisted).copy(assistance = false),
        )
        misses.forEach { assertEquals(listOf(false, false), currentFlags(it), it.toString()) }
    }

    @Test
    fun currentRecordShowsItsPreviousValueAndIsNotFirst() {
        val workout = workout("w", 3 * DAY, entry(bench, set(105.0, 3), set(100.0, 6)))
        val weight = weightRecord(bench, 105.0, 3, 100.0, workout)
        val reps = repsRecord(bench, 100.0, 6, 5, workout)
        val index = index(workout.copy(records = listOf(weight, reps)), records = listOf(currentOf(reps), currentOf(weight)))

        assertEquals(listOf(weight, reps), index.currentRecords.map { it.record }, "the history's record, in event order")
        assertEquals(100.0, index.currentRecords[0].record.previousKg)
        assertEquals(5, index.currentRecords[1].record.previousReps)
        assertTrue(index.currentRecords.none { it.record.firstRecord })
        assertTrue(index.currentRecords.all { it.current })
        assertSame(index.events[0], index.currentRecords[0], "a current event is one event")
        assertSame(index.events[1], index.currentRecords[1])
        assertTrue(index.only(bench).weightRecord)
    }

    @Test
    fun currentRecordWithoutItsEventFallsBackToItself() {
        val workout = workout("w", DAY, entry(customRow, set(50.0, 10)), entry(bench, set(100.0, 5)))
        val current = weightRecord(bench, 100.0, 5, null, workout)
        val index = index(workout, records = listOf(current))
        val event = index.currentRecords.single()

        assertSame(current, event.record)
        assertEquals("Workout w", event.workoutName)
        assertEquals(1, event.order)
        assertTrue(event.current)
        assertTrue(index.events.isEmpty())
    }

    @Test
    fun currentRecordOfAMissingWorkoutIsDropped() {
        // A record list newer than the history (or the reverse) can name a workout the history no longer has.
        val gone = workout("gone", DAY, entry(bench, set(100.0, 5)))
        val index = index(workout("w", 3 * DAY, entry(customRow, set(50.0, 10))),
            records = listOf(weightRecord(bench, 100.0, 5, null, gone)))

        assertTrue(index.currentRecords.isEmpty())
        assertTrue(index.searchRecords("bench").isEmpty(), "a dropped record is not searchable")
    }

    @Test
    fun volumeHasNoSetValue() {
        val point = index(workout("w", DAY, entry(bench, set(100.0, 5), set(80.0, 8)))).only(bench)

        assertNull(ProgressMetrics.value(point, ProgressMetric.VOLUME), "volume is a session sum, read volumeKg")
        assertEquals(100.0 * 5 + 80.0 * 8, point.volumeKg)
        assertSame(point.load, ProgressMetrics.value(point, ProgressMetric.LOAD))
        assertSame(point.reps, ProgressMetrics.value(point, ProgressMetric.REPS))
        assertSame(point.estimate, ProgressMetrics.value(point, ProgressMetric.E1RM))
    }

    @Test
    fun onlyLoadAndRepsCarryMarks() {
        val first = workout("first", DAY, entry(bench, set(100.0, 5)), entry(assistedDip, set(20.0, 8)))
        val second = workout("second", 3 * DAY, entry(bench, set(105.0, 6)), entry(assistedDip, set(20.0, 10)))
        val records = listOf(
            weightRecord(bench, 105.0, 6, 100.0, second), repsRecord(bench, 105.0, 6, null, second),
            repsRecord(assistedDip, 20.0, 10, 8, second, assistance = true),
        )
        val index = index(first, second.copy(records = records))
        val benchPoint = index.sessions(keyOf(bench)).last()
        val dipPoint = index.sessions(keyOf(assistedDip)).last()

        assertTrue(ProgressMetrics.marked(benchPoint, ProgressMetric.LOAD, bench.convention))
        assertFalse(ProgressMetrics.marked(benchPoint, ProgressMetric.REPS, bench.convention), "a first reps record")
        assertFalse(ProgressMetrics.marked(benchPoint, ProgressMetric.E1RM, bench.convention))
        assertFalse(ProgressMetrics.marked(benchPoint, ProgressMetric.VOLUME, bench.convention))
        assertTrue(ProgressMetrics.marked(dipPoint, ProgressMetric.REPS, assistedDip.convention))
        assertFalse(ProgressMetrics.marked(dipPoint, ProgressMetric.LOAD, assistedDip.convention))
    }

    @Test
    fun searchRecordsFindsTheCatalogNameInBothLanguages() {
        val workout = workout("w", DAY, entry(bench, set(100.0, 5)), entry(customRow, set(50.0, 10)), entry(pullUp, set(0.0, 8)))
        val records = listOf(weightRecord(bench, 100.0, 5, null, workout), weightRecord(customRow, 50.0, 10, null, workout))
        val index = index(workout.copy(records = records), records = records.map(::currentOf))

        assertEquals(setOf(bench.id), index.searchRecords("supino"))
        assertEquals(setOf(bench.id), index.searchRecords("bench"))
        assertEquals(setOf(customRow.id), index.searchRecords("cable row"))
        assertEquals(emptySet(), index.searchRecords("pull-up"), "an exercise without a record is not found")
    }
}
