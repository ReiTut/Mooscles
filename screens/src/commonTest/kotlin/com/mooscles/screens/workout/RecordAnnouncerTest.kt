package com.mooscles.screens.workout

import com.mooscles.ui.ExerciseUi
import com.mooscles.ui.RecordKind
import com.mooscles.ui.SetRecordUi
import com.mooscles.ui.SetUi
import com.mooscles.ui.WorkoutExerciseUi
import com.mooscles.ui.WorkoutUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Which record the active workout celebrates (spec D17, D18): only the record a set carries in the
 * emission that completes a check made here, and only when it beats the best announced this session.
 */
class RecordAnnouncerTest {
    private val bench = ExerciseUi("bench", "Supino reto", "Chest", "Barbell", convention = "Total load")
    private val row = ExerciseUi("row", "Remada curvada", "Back", "Barbell", convention = "Total load")
    private val weight = SetRecordUi(RecordKind.WEIGHT, previousKg = 85.0)
    private val reps = SetRecordUi(RecordKind.REPS, previousReps = 6)

    private fun workout(vararg entries: Pair<ExerciseUi, List<SetUi>>) = WorkoutUi(
        id = "w1",
        name = "Treino A",
        startedAt = 0L,
        exercises = entries.mapIndexed { i, (exercise, sets) -> WorkoutExerciseUi("e${i + 1}", exercise, sets) },
    )

    private fun bench(vararg sets: SetUi) = workout(bench to sets.toList())

    /** Runs [states] in order from [checked], as the screen would, and returns the keys announced. */
    private fun announcedOver(checked: Set<String>, vararg states: WorkoutUi, start: Map<String, RecordBest> = emptyMap()): List<String> {
        var pending = checked
        var best = start
        val keys = mutableListOf<String>()
        for (state in states) {
            val step = RecordAnnouncer.step(state, pending, best)
            pending = pending - step.consumed
            best = step.announced
            keys += step.announce.map { it.key }
        }
        return keys
    }

    @Test
    fun announcesOnlySetsCompletedHere() {
        val state = workout(
            bench to listOf(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)),
            row to listOf(SetUi("r1", reps = 8, weightKg = 70.0, completed = true, record = reps)),
        )
        val step = RecordAnnouncer.step(state, checkedHere = setOf("r1"), announced = emptyMap())
        assertEquals(
            listOf(RecordEvent(key = "r1:70.0:8:REPS", setId = "r1", exerciseName = "Remada curvada", record = reps, weightKg = 70.0, reps = 8)),
            step.announce,
        )
        assertEquals(setOf("r1"), step.consumed)
        assertEquals(mapOf("row|Total load|REPS|70.0" to RecordBest(70.0, 8)), step.announced)
        // A check whose completion has not come back yet waits; a set checked here without a
        // record (or with a first record, which never has one) is consumed and says nothing.
        val waiting = RecordAnnouncer.step(bench(SetUi("s1", reps = 6, weightKg = 87.5)), setOf("s1"), emptyMap())
        assertEquals(emptySet(), waiting.consumed)
        val plain = RecordAnnouncer.step(bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true)), setOf("s1"), emptyMap())
        assertEquals(setOf("s1"), plain.consumed)
        assertEquals(emptyList(), plain.announce)
    }

    @Test
    fun recordsPresentAtOpenAreNotAnnounced() {
        // Resuming the workout or restarting the app: the records were already there.
        val state = bench(
            SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight),
            SetUi("s2", reps = 6, weightKg = 80.0, completed = true),
        )
        assertEquals(emptyList(), announcedOver(emptySet(), state))
        // Completing another set here does not announce the old one.
        assertEquals(emptyList(), announcedOver(setOf("s2"), state))
    }

    @Test
    fun aLaterSetBeatingItAnnouncesAgainWithTheNewValue() {
        val first = bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight), SetUi("s2", reps = 5, weightKg = 90.0))
        val start = RecordAnnouncer.step(first, setOf("s1"), emptyMap())
        assertEquals(listOf("s1:87.5:6:WEIGHT"), start.announce.map { it.key })
        // s2 checked later: the record moves to the heavier set; "before" is still the history's best.
        val later = bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true), SetUi("s2", reps = 5, weightKg = 90.0, completed = true, record = weight))
        val event = RecordAnnouncer.step(later, setOf("s2"), start.announced).announce.single()
        assertEquals("s2:90.0:5:WEIGHT", event.key)
        assertEquals(90.0, event.weightKg)
        assertEquals(5, event.reps)
        assertEquals(85.0, event.record.previousKg)
    }

    @Test
    fun losingARecordIsSilent() {
        val announced = mapOf("bench|Total load|WEIGHT" to RecordBest(87.5, 6))
        // Unchecked, or edited below the record: the presentation takes the record away.
        val unchecked = bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = false))
        val lighter = bench(SetUi("s1", reps = 6, weightKg = 80.0, completed = true))
        assertEquals(emptyList(), announcedOver(emptySet(), unchecked, lighter, start = announced))
        // Checked again, back at the value already announced: nothing new.
        val back = bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight))
        assertEquals(emptyList(), announcedOver(setOf("s1"), back, start = announced))
    }

    @Test
    fun theSameRecordIsAnnouncedOnce() {
        val state = bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight))
        // Every later emission (a scroll, a recreation, another write) finds the check consumed.
        assertEquals(listOf("s1:87.5:6:WEIGHT"), announcedOver(setOf("s1"), state, state, state.copy(name = "Treino B")))
    }

    @Test
    fun inheritingARecordAfterUncheckIsSilent() {
        // Three equal working sets above the history, all checked here: the record is on s1.
        val done = bench(
            SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight),
            SetUi("s2", reps = 6, weightKg = 87.5, completed = true),
            SetUi("s3", reps = 6, weightKg = 87.5, completed = true),
        )
        val start = RecordAnnouncer.step(done, setOf("s1", "s2", "s3"), emptyMap())
        assertEquals(listOf("s1:87.5:6:WEIGHT"), start.announce.map { it.key })
        assertEquals(setOf("s1", "s2", "s3"), start.consumed)
        // s1 unchecked (or removed): the record moves to s2, which was not just completed.
        val inherited = bench(
            SetUi("s1", reps = 6, weightKg = 87.5),
            SetUi("s2", reps = 6, weightKg = 87.5, completed = true, record = weight),
            SetUi("s3", reps = 6, weightKg = 87.5, completed = true),
        )
        val removed = bench(SetUi("s2", reps = 6, weightKg = 87.5, completed = true, record = weight), SetUi("s3", reps = 6, weightKg = 87.5, completed = true))
        assertEquals(emptyList(), announcedOver(emptySet(), inherited, removed, start = start.announced))
        // Checking s1 again at the same value: an equal record is not a new one.
        assertEquals(emptyList(), announcedOver(setOf("s1"), done, start = start.announced))
    }

    @Test
    fun correctingARecordSetDownIsSilent() {
        val start = RecordAnnouncer.step(bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)), setOf("s1"), emptyMap())
        // Fixed to 86 kg, still above the history's 85: still the record, not a new one.
        val corrected = bench(SetUi("s1", reps = 6, weightKg = 86.0, completed = true, record = weight))
        assertEquals(emptyList(), announcedOver(emptySet(), corrected, start = start.announced))
        // Even unchecked and checked again at 86: below the best announced.
        assertEquals(emptyList(), announcedOver(setOf("s1"), corrected, start = start.announced))
    }

    @Test
    fun typingIntoACompletedSetIsSilent() {
        val start = RecordAnnouncer.step(bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)), setOf("s1"), emptyMap())
        // Typing "92,5" over the done set writes 9, 92 and 92.5: no check, nothing announced.
        val typed = listOf(9.0, 92.0, 92.5).map { kg ->
            bench(SetUi("s1", reps = 6, weightKg = kg, completed = true, record = weight.takeIf { kg > 85.0 }))
        }
        assertEquals(emptyList(), announcedOver(emptySet(), *typed.toTypedArray(), start = start.announced))
    }

    @Test
    fun aNewBetterRecordAnnouncesAgain() {
        var best = RecordAnnouncer.step(bench(SetUi("s1", reps = 6, weightKg = 87.5, completed = true, record = weight)), setOf("s1"), emptyMap()).announced
        // Heavier: announced.
        val heavier = RecordAnnouncer.step(bench(SetUi("s2", reps = 3, weightKg = 90.0, completed = true, record = weight)), setOf("s2"), best)
        assertEquals(listOf("s2:90.0:3:WEIGHT"), heavier.announce.map { it.key })
        best = heavier.announced
        // The same load with more reps beats it too; with fewer, not.
        val fewer = RecordAnnouncer.step(bench(SetUi("s3", reps = 2, weightKg = 90.0, completed = true, record = weight)), setOf("s3"), best)
        assertEquals(emptyList(), fewer.announce)
        val more = RecordAnnouncer.step(bench(SetUi("s3", reps = 4, weightKg = 90.0, completed = true, record = weight)), setOf("s3"), best)
        assertEquals(listOf("s3:90.0:4:WEIGHT"), more.announce.map { it.key })
        // Reps records compete at their own load: more reps at 80 kg after 80 × 8.
        val eight = RecordAnnouncer.step(bench(SetUi("r1", reps = 8, weightKg = 80.0, completed = true, record = reps)), setOf("r1"), more.announced)
        assertEquals(listOf("r1:80.0:8:REPS"), eight.announce.map { it.key })
        val nine = RecordAnnouncer.step(bench(SetUi("r2", reps = 9, weightKg = 80.0, completed = true, record = reps)), setOf("r2"), eight.announced)
        assertEquals(listOf("r2:80.0:9:REPS"), nine.announce.map { it.key })
        val sameReps = RecordAnnouncer.step(bench(SetUi("r3", reps = 9, weightKg = 80.0, completed = true, record = reps)), setOf("r3"), nine.announced)
        assertTrue(sameReps.announce.isEmpty())
        // Another exercise has its own best.
        val other = RecordAnnouncer.step(workout(row to listOf(SetUi("x1", reps = 6, weightKg = 87.5, completed = true, record = weight))), setOf("x1"), nine.announced)
        assertEquals(listOf("x1:87.5:6:WEIGHT"), other.announce.map { it.key })
    }
    @Test
    fun repsRecordsCompeteWithinTheirAssistanceMode() {
        // The domain keeps reps records per assistance mode: 12 assisted reps at 80 kg do not
        // stand in the way of an unassisted record at the same load.
        val assisted = RecordAnnouncer.step(
            bench(SetUi("a1", reps = 12, weightKg = 80.0, completed = true, assistance = true, record = SetRecordUi(RecordKind.REPS, previousReps = 10))),
            setOf("a1"), emptyMap(),
        )
        assertEquals(listOf("a1:80.0:12:REPS"), assisted.announce.map { it.key })
        val unassisted = RecordAnnouncer.step(bench(SetUi("u1", reps = 8, weightKg = 80.0, completed = true, record = reps)), setOf("u1"), assisted.announced)
        assertEquals(listOf("u1:80.0:8:REPS"), unassisted.announce.map { it.key })
        // Each mode keeps its own best: fewer assisted reps after it say nothing.
        val fewer = RecordAnnouncer.step(
            bench(SetUi("a2", reps = 11, weightKg = 80.0, completed = true, assistance = true, record = SetRecordUi(RecordKind.REPS, previousReps = 10))),
            setOf("a2"), unassisted.announced,
        )
        assertEquals(emptyList(), fewer.announce)
    }
}
