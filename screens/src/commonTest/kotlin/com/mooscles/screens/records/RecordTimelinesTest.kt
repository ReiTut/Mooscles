package com.mooscles.screens.records

import com.mooscles.screens.format.BestSets
import com.mooscles.screens.progress.HOUR
import com.mooscles.screens.progress.ProgressIndex
import com.mooscles.screens.progress.RecordEvent
import com.mooscles.screens.progress.assistedDip
import com.mooscles.screens.progress.bench
import com.mooscles.screens.progress.currentOf
import com.mooscles.screens.progress.customRow
import com.mooscles.screens.progress.entry
import com.mooscles.screens.progress.pullUp
import com.mooscles.screens.progress.repsRecord
import com.mooscles.screens.progress.set
import com.mooscles.screens.progress.weightRecord
import com.mooscles.screens.progress.workout
import com.mooscles.ui.RecordUi
import com.mooscles.ui.WorkoutUi
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlin.math.nextUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordTimelinesTest {
    private val utc = TimeZone.UTC
    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    /** A workout that ends at [end] (records are dated by their workout's end). */
    private fun endingAt(id: String, end: String, records: (WorkoutUi) -> List<RecordUi>): WorkoutUi {
        val base = workout(id, at(end) - HOUR, entry(bench, set(100.0, 5)))
        return base.copy(records = records(base))
    }

    private class Base(val index: ProgressIndex, val w1: WorkoutUi, val w2: WorkoutUi, val w3: WorkoutUi)

    /**
     * Three workouts. w1 (Aug): first records of bench (weight, reps) and of the custom row. w2 (Sep): bench weight
     * beaten, pull-up first reps. w3 (Oct): bench weight and reps beaten. Current: bench weight (w3), bench reps (w3),
     * the custom row (w1, a first record never beaten) and the pull-up (w2, a first record).
     */
    private fun base(): Base {
        val w1 = endingAt("w1", "2026-08-10T10:00:00Z") {
            listOf(weightRecord(bench, 100.0, 5, null, it), repsRecord(bench, 100.0, 5, null, it),
                weightRecord(customRow, 50.0, 10, null, it))
        }
        val w2 = endingAt("w2", "2026-09-12T10:00:00Z") {
            listOf(weightRecord(bench, 105.0, 3, 100.0, it), repsRecord(pullUp, 0.0, 8, null, it))
        }
        val w3 = endingAt("w3", "2026-10-15T10:00:00Z") {
            listOf(weightRecord(bench, 110.0, 2, 105.0, it), repsRecord(bench, 90.0, 8, 5, it))
        }
        val current = listOf(w3.records[0], w3.records[1], w1.records[2], w2.records[1]).map(::currentOf)
        return Base(ProgressIndex.build(listOf(w3, w2, w1), current), w1, w2, w3)
    }

    private fun RecordsTimeline.events(): List<RecordEvent> = months.flatMap { it.items }
    private fun RecordsTimeline.ids(): List<String> = events().map { it.record.workoutId + it.record.kind.name.take(1) }
    private fun query(
        index: ProgressIndex, text: String = "", currentOnly: Boolean = false, firstRecords: Boolean = false,
        zone: TimeZone = utc,
    ) = RecordTimelines.query(index, RecordsQuery(text, currentOnly, firstRecords, zone))

    @Test
    fun beatenOnlyByDefaultNewestFirst() {
        val timeline = query(base().index)

        assertEquals(listOf("w3W", "w3R", "w2W"), timeline.ids())
        assertEquals(3, timeline.total)
        assertEquals(listOf(Month.OCTOBER to 2, Month.SEPTEMBER to 1), timeline.months.map { it.month to it.items.size })
        assertTrue(timeline.months.all { it.year == 2026 })
    }

    @Test
    fun firstRecordsOnlyWithTheChip() {
        val timeline = query(base().index, firstRecords = true)

        assertEquals(listOf("w3W", "w3R", "w2W", "w2R", "w1W", "w1R", "w1W"), timeline.ids())
        assertEquals(7, timeline.total)
        assertEquals(listOf(2, 2, 3), timeline.months.map { it.items.size })
    }

    @Test
    fun currentOnlyIsUiStateRecordsIncludingFirsts() {
        val b = base()
        val timeline = query(b.index, currentOnly = true, firstRecords = true)

        assertEquals(listOf("w3W", "w3R", "w2R", "w1W"), timeline.ids())
        assertTrue(timeline.events().all { it.current })
        assertEquals(timeline.ids(), query(b.index, currentOnly = true).ids(), "firstRecords is ignored")
        // The beaten ones keep their previous value from the history.
        assertEquals(105.0, timeline.events().first().record.previousKg)
    }

    @Test
    fun searchMatchesBothLanguagesAndAddsCurrentRecordsOfMatches() {
        val b = base()
        val pt = query(b.index, text = "supino")
        val en = query(b.index, text = "bench")

        assertEquals(listOf("w3W", "w3R", "w2W"), pt.ids())
        assertEquals(pt.ids(), en.ids())
        // A first record that is current shows up in a search of its exercise even without the chip.
        val row = query(b.index, text = "cable row")
        assertEquals(listOf("w1W"), row.ids())
        assertTrue(row.events().single().current)
        assertTrue(query(b.index, text = "nothing like this").months.isEmpty())
    }

    @Test
    fun aCurrentEventAppearsOnce() {
        val timeline = query(base().index, text = "bench", firstRecords = true)

        assertEquals(timeline.total, timeline.events().map(RecordTimelines::key).toSet().size)
        assertEquals(1, timeline.events().count { it.record.workoutId == "w3" && it.record.kind.name == "WEIGHT" })
        assertTrue(timeline.events().first { it.record.workoutId == "w3" && it.record.kind.name == "WEIGHT" }.current)
    }

    @Test
    fun blankSearchIsNoSearch() {
        val b = base()

        assertEquals(query(b.index).ids(), query(b.index, text = "   ").ids())
        assertEquals(query(b.index).ids(), query(b.index, text = " \t").ids())
    }

    @Test
    fun aStrayCurrentRecordWhoseWorkoutIsGoneDoesNotAppear() {
        val b = base()
        val stray = currentOf(weightRecord(bench, 200.0, 1, 190.0, b.w3.copy(id = "gone")))
        val index = ProgressIndex.build(listOf(b.w3, b.w2, b.w1), b.index.currentRecords.map { it.record } + stray)

        assertFalse(query(index, currentOnly = true).events().any { it.record.workoutId == "gone" })
        assertFalse(query(index, text = "bench").events().any { it.record.workoutId == "gone" })
    }

    @Test
    fun monthsUseTheLocalZone() {
        val saoPaulo = TimeZone.of("America/Sao_Paulo")
        // 23:30 UTC on Oct 31 is 20:30 in Sao Paulo, October; 02:30 UTC on Nov 1 is 23:30 on Oct 31 there, still October.
        val late = endingAt("late", "2026-10-31T23:30:00Z") { listOf(weightRecord(bench, 120.0, 1, 110.0, it)) }
        val early = endingAt("early", "2026-11-01T02:30:00Z") { listOf(weightRecord(bench, 125.0, 1, 120.0, it)) }
        val index = ProgressIndex.build(listOf(early, late), emptyList())

        val local = query(index, zone = saoPaulo)
        assertEquals(listOf(Month.OCTOBER), local.months.map { it.month })
        assertEquals(2, local.months.single().items.size)
        val utcMonths = query(index, zone = utc)
        assertEquals(listOf(Month.NOVEMBER, Month.OCTOBER), utcMonths.months.map { it.month })
    }

    @Test
    fun keysAreUniqueAcrossTheTimeline() {
        // 27.2155 kg and 27.22 kg both read "60 lb" but are not the same load: two reps records of one workout.
        assertFalse(BestSets.sameLoad(27.2155, 27.22))
        fun history(): List<WorkoutUi> {
            val loads = endingAt("k0", "2026-06-10T10:00:00Z") {
                listOf(repsRecord(bench, 27.2155, 8, 7, it), repsRecord(bench, 27.22, 10, 9, it),
                    weightRecord(bench, 27.22, 1, 22.0, it))
            }
            // Same exercise, load and kind; only the assistance differs.
            val assisted = endingAt("k1", "2026-07-10T10:00:00Z") {
                listOf(repsRecord(assistedDip, 20.0, 8, 7, it, assistance = true),
                    repsRecord(assistedDip, 20.0, 9, 8, it, assistance = false))
            }
            return listOf(assisted, loads)
        }
        val timeline = query(ProgressIndex.build(history(), emptyList()), firstRecords = true)

        val keys = timeline.events().map(RecordTimelines::key)
        assertEquals(5, keys.size)
        assertEquals(keys.size, keys.toSet().size)
        // A rebuilt index from copied inputs gives the same keys, in the same order.
        val rebuilt = ProgressIndex.build(history().map { it.copy() }, emptyList())
        assertEquals(keys, query(rebuilt, firstRecords = true).events().map(RecordTimelines::key))
    }

    @Test
    fun searchKeepsTheIndexOrderInsideAWorkout() {
        val w = endingAt("o", "2026-10-15T10:00:00Z") {
            listOf(weightRecord(bench, 110.0, 2, 105.0, it), repsRecord(bench, 85.0, 4, null, it),
                repsRecord(bench, 80.0, 6, 5, it))
        }
        // The weight record and the 85 kg first reps record are current; the 80 kg one is only beaten.
        val index = ProgressIndex.build(listOf(w), listOf(w.records[0], w.records[1]).map(::currentOf))

        val chip = query(index, firstRecords = true).events().map { it.record.weightKg }
        assertEquals(listOf(110.0, 85.0, 80.0), chip)
        assertEquals(chip, query(index, text = "bench").events().map { it.record.weightKg })
        assertEquals(chip, query(index, text = "supino").events().map { it.record.weightKg })
    }

    @Test
    fun aCurrentFallbackTakesItsPlaceByTheIndexOrder() {
        // The current record has no event in its workout (no 90 kg reps event), so it comes from UiState.records.
        val w = endingAt("f", "2026-10-15T10:00:00Z") {
            listOf(weightRecord(bench, 110.0, 2, 105.0, it), repsRecord(bench, 80.0, 6, 5, it))
        }
        val fallback = currentOf(repsRecord(bench, 90.0, 3, null, w))
        val index = ProgressIndex.build(listOf(w), listOf(fallback))

        assertEquals(listOf(110.0, 90.0, 80.0), query(index, text = "bench").events().map { it.record.weightKg })
    }

    @Test
    fun currentOnlyWithSearchListsTheCurrentRecordsOfTheMatches() {
        val b = base()

        assertEquals(listOf("w3W", "w3R"), query(b.index, text = "bench", currentOnly = true).ids())
        assertEquals(listOf("w1W"), query(b.index, text = "cable row", currentOnly = true).ids())
    }

    @Test
    fun aDeletedWorkoutLeavesTheTimelineAfterARebuild() {
        val b = base()
        val without = ProgressIndex.build(listOf(b.w2, b.w1), b.index.currentRecords.map { it.record })

        assertFalse(query(without, firstRecords = true).events().any { it.record.workoutId == "w3" })
        assertFalse(query(without, currentOnly = true).events().any { it.record.workoutId == "w3" })
        assertFalse(query(without, text = "bench").events().any { it.record.workoutId == "w3" })
    }

    @Test
    fun theSameMonthOfTwoYearsIsTwoMonths() {
        val a = endingAt("a", "2025-10-15T10:00:00Z") { listOf(weightRecord(bench, 100.0, 1, 90.0, it)) }
        val b = endingAt("b", "2026-10-15T10:00:00Z") { listOf(weightRecord(bench, 110.0, 1, 100.0, it)) }

        val months = query(ProgressIndex.build(listOf(b, a), emptyList())).months
        assertEquals(listOf(2026 to Month.OCTOBER, 2025 to Month.OCTOBER), months.map { it.year to it.month })
    }
}
