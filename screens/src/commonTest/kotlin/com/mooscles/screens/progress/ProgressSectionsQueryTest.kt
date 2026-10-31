package com.mooscles.screens.progress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the exercise progress screen lists under its chart ([ProgressSectionsQuery]): records and sessions. */
class ProgressSectionsQueryTest {
    @Test
    fun recordsAreTheCurrentOnesOfTheConventionWithTheirPreviousValue() {
        val w1 = workout("w1", 1 * DAY, entry(bench, set(95.0, 3)))
        val w2Start = 2 * DAY
        val w2 = workout("w2", w2Start, entry(bench, set(100.0, 3)))
        val record = weightRecord(bench, 100.0, 3, previousKg = 95.0, workout = w2)
        val history = listOf(w2.copy(records = listOf(record)), w1)
        // The state's copy has no previous value; the index gives the history's event, which has it.
        val sections = ProgressSectionsQuery(bench.id, bench.convention).compute(ProgressIndex.build(history, listOf(currentOf(record))))
        assertEquals(95.0, sections.weight?.record?.previousKg)
        assertEquals("w2", sections.weight?.record?.workoutId)
        // A current record whose workout was deleted is gone with it.
        val deleted = ProgressSectionsQuery(bench.id, bench.convention).compute(ProgressIndex.build(listOf(w1), listOf(currentOf(record))))
        assertNull(deleted.weight)
    }

    @Test
    fun repsRecordsAreTheConventionsGroupHighestLoadFirst() {
        val w = workout("w", DAY, entry(bench, set(60.0, 10), set(80.0, 8), set(40.0, 12, assistance = true)))
        val records = listOf(
            repsRecord(bench, 60.0, 10, null, w),
            repsRecord(bench, 80.0, 8, null, w),
            repsRecord(bench, 40.0, 12, null, w, assistance = true),
        )
        val history = listOf(w.copy(records = records))
        val sections = ProgressSectionsQuery(bench.id, bench.convention).compute(ProgressIndex.build(history, records.map(::currentOf)))
        // The assisted record is the other group's, as on the chart, and has its own list (D19: the old screen showed it).
        assertEquals(listOf(80.0, 60.0), sections.reps.map { it.record.weightKg })
        assertEquals(listOf(40.0), sections.assisted.map { it.record.weightKg })
        assertNull(sections.weight)
    }

    @Test
    fun assistanceRepsRecordsLowestAssistanceFirst() {
        val w = workout("w", DAY, entry(assistedDip, set(30.0, 8, assistance = true), set(20.0, 6, assistance = true)))
        val records = listOf(
            repsRecord(assistedDip, 30.0, 8, null, w, assistance = true),
            repsRecord(assistedDip, 20.0, 6, null, w, assistance = true),
        )
        val history = listOf(w.copy(records = records))
        val sections = ProgressSectionsQuery(assistedDip.id, assistedDip.convention)
            .compute(ProgressIndex.build(history, records.map(::currentOf)))
        assertEquals(listOf(20.0, 30.0), sections.reps.map { it.record.weightKg })
        // On Assistance every record is assisted: no second group.
        assertEquals(emptyList(), sections.assisted)
    }

    @Test
    fun sessionsAreEveryConventionNewestFirstAndTheOthersAreListed() {
        val perHand = customRow.copy(convention = "Per hand")
        val history = listOf(
            workout("c3", 30 * DAY, entry(customRow, set(70.0, 6))),
            workout("c2", 20 * DAY, entry(perHand, set(30.0, 8))),
            workout("c1", 10 * DAY, entry(perHand, set(25.0, 10))),
            workout("x", 25 * DAY, entry(bench, set(80.0, 5))),
        )
        val index = ProgressIndex.build(history, emptyList())
        val sections = ProgressSectionsQuery(customRow.id, customRow.convention).compute(index)
        assertEquals(listOf("c3", "c2", "c1"), sections.sessions.map { it.point.workoutId })
        assertEquals(listOf("Total load", "Per hand", "Per hand"), sections.sessions.map { it.convention })
        assertEquals(listOf("Per hand"), sections.otherConventions)
        // Keys are unique along the list.
        assertEquals(3, sections.sessions.map { it.key }.distinct().size)
        // An exercise with sessions only in its old convention still lists them.
        val onlyOld = ProgressSectionsQuery(customRow.id, "One side").compute(index)
        assertEquals(listOf("c3", "c2", "c1"), onlyOld.sessions.map { it.point.workoutId })
        assertEquals(listOf("Total load", "Per hand"), onlyOld.otherConventions)
    }
}
