package com.mooscles.sharing

import com.mooscles.data.AppData
import com.mooscles.data.Equipment
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import com.mooscles.domain.WeightUnits
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class WorkoutShareCardTest {
    private val utc = ZoneId.of("UTC")
    private fun workout(id: String = "selected", start: Long = 1000, end: Long? = start + 123000,
                        status: WorkoutStatus = WorkoutStatus.FINISHED) =
        Workout(id, "Saved workout $id", start, end, status)

    private fun exercise(id: String = "entry", variation: String = "bench", workout: String = "selected",
                         position: Int = 0, convention: LoadConvention = LoadConvention.TOTAL_LOAD) =
        WorkoutExercise(id, workout, variation, "Saved $variation", MuscleGroup.CHEST,
            Equipment.BARBELL, convention, position)

    private fun set(entry: WorkoutExercise, kg: Double, reps: Int, position: Int = 0,
                    completed: Boolean = true, warmup: Boolean = false, assistance: Boolean = false) =
        WorkoutSet("${entry.id}-$position-$kg-$reps", entry.id, position, reps, kg, completed, warmup, assistance)

    private fun data(entry: WorkoutExercise, vararg sets: WorkoutSet) =
        AppData(workouts = listOf(workout()), workoutExercises = listOf(entry), sets = sets.toList())

    private fun card(data: AppData, workoutId: String = "selected", pounds: Boolean = false,
                     locale: Locale = Locale.US) = WorkoutShareCards.from(data, workoutId, pounds, locale, utc)

    @Test fun `best loaded set keeps the actual highest weight and reps together`() {
        val entry = exercise()
        val result = card(data(entry, set(entry, 20.0, 20), set(entry, 22.0, 6), set(entry, 22.0, 8)))
        assertEquals("22 kg × 8", result.rows.single().bestSetText)
        assertEquals(3, result.rows.single().completedSets)
        assertEquals("708 kg", result.volumeText)
        assertEquals("2:03", result.durationText)
    }

    @Test fun `warmup assistance unfinished and invalid sets cannot become best working set`() {
        val entry = exercise()
        val result = card(data(entry, set(entry, 22.0, 6), set(entry, 100.0, 3, warmup = true),
            set(entry, 110.0, 3, assistance = true), set(entry, 120.0, 3, completed = false),
            set(entry, -1.0, 3), set(entry, Double.NaN, 3), set(entry, 150.0, 0)))
        assertEquals("22 kg × 6", result.rows.single().bestSetText)
        assertEquals(3, result.rows.single().completedSets)
        assertEquals("432 kg", result.volumeText)
    }

    @Test fun `bodyweight chooses highest completed eligible reps and preserves zero load`() {
        val entry = exercise(convention = LoadConvention.BODYWEIGHT)
        val result = card(data(entry, set(entry, 0.0, 8), set(entry, 0.0, 12),
            set(entry, 0.0, 30, warmup = true), set(entry, 0.0, 40, completed = false)))
        assertEquals("Bodyweight × 12", result.rows.single().bestSetText)
        assertEquals("0 kg", result.volumeText)
        assertTrue(result.rows.single().hasPr)
        assertEquals(1, result.prCount)
    }

    @Test fun `loaded bodyweight working sets display actual added weight and not body mass`() {
        val entry = exercise(convention = LoadConvention.BODYWEIGHT)
        val result = card(data(entry, set(entry, 0.0, 12), set(entry, 5.0, 6)))
        assertEquals("5 kg × 6", result.rows.single().bestSetText)
        assertEquals("30 kg", result.volumeText)
    }

    @Test fun `pounds convert weight and volume once without multiplying per-hand loads`() {
        val entry = exercise(convention = LoadConvention.PER_HAND)
        val result = card(data(entry, set(entry, WeightUnits.lbToKg(22.0), 6)), pounds = true)
        assertEquals("22 lb × 6", result.rows.single().bestSetText)
        assertEquals("132 lb", result.volumeText)
    }

    @Test fun `binary unit round trip noise does not defeat the higher repetition tie break`() {
        val entry = exercise()
        val result = card(data(entry, set(entry, Math.nextUp(22.0), 6), set(entry, 22.0, 9)))
        assertEquals("22 kg × 9", result.rows.single().bestSetText)
    }

    @Test fun `every completed exercise is retained in saved order with long names intact`() {
        val first = exercise(id = "first", variation = "first", position = 0)
            .copy(name = "An exceptionally long exercise name with equipment and variation details that must remain complete")
        val second = exercise(id = "second", variation = "second", position = 1)
        val third = exercise(id = "third", variation = "third", position = 2)
        val noSets = exercise(id = "unfinished", variation = "unfinished", position = 3)
        val result = card(AppData(workouts = listOf(workout()),
            workoutExercises = listOf(third, noSets, second, first),
            sets = listOf(set(third, 30.0, 2), set(first, 20.0, 10), set(second, 10.0, 8),
                set(noSets, 100.0, 10, completed = false))))
        assertEquals(listOf(first.name, second.name, third.name), result.rows.map { it.exerciseName })
        assertEquals(3, result.rows.size)
    }

    @Test fun `nonworking-only rows remain truthful instead of inventing a best set`() {
        val warmup = exercise(id = "warmup", position = 0)
        val assistance = exercise(id = "assistance", position = 1, convention = LoadConvention.ASSISTANCE)
        val mixed = exercise(id = "mixed", position = 2)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(warmup, assistance, mixed),
            sets = listOf(set(warmup, 40.0, 5, warmup = true), set(assistance, 50.0, 8),
                set(mixed, 40.0, 5, warmup = true), set(mixed, 50.0, 8, assistance = true))))
        assertEquals(listOf("Warm-up only", "Assistance only", "No working set"), result.rows.map { it.bestSetText })
        assertEquals(listOf(1, 1, 2), result.rows.map { it.completedSets })
    }

    @Test fun `first and beaten weight and bodyweight repetition records count each variation once`() {
        val bench = exercise()
        val duplicate = exercise(id = "duplicate", position = 2)
        val pullup = exercise(id = "pullup", variation = "pullup", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(bench, pullup, duplicate),
            sets = listOf(set(bench, 22.0, 6), set(duplicate, 22.0, 8), set(pullup, 0.0, 12))))
        assertEquals(2, result.prCount)
        assertEquals(listOf(false, true, true), result.rows.map { it.hasPr })
    }

    @Test fun `duplicate variation marks only the row containing the record setting set`() {
        val losing = exercise(id = "losing", position = 0)
        val winning = exercise(id = "winning", position = 1)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(losing, winning),
            sets = listOf(set(losing, 22.0, 6), set(winning, 22.0, 8))))
        assertEquals(listOf(false, true), result.rows.map { it.hasPr })
        assertEquals(1, result.prCount)
    }

    @Test fun `warmup duplicate cannot inherit a working set record badge`() {
        val warmup = exercise(id = "warmup", position = 0)
        val winning = exercise(id = "winning", position = 1)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(warmup, winning),
            sets = listOf(set(warmup, 22.0, 8, warmup = true), set(winning, 22.0, 8))))
        assertEquals(listOf(false, true), result.rows.map { it.hasPr })
        assertEquals(1, result.prCount)
    }

    @Test fun `bodyweight duplicate repetition badge belongs to the matching completed set`() {
        val losing = exercise(id = "losing", position = 0, convention = LoadConvention.BODYWEIGHT)
        val winning = exercise(id = "winning", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout()), workoutExercises = listOf(losing, winning),
            sets = listOf(set(losing, 0.0, 8), set(winning, 0.0, 12))))
        assertEquals(listOf(false, true), result.rows.map { it.hasPr })
        assertEquals(1, result.prCount)
    }

    @Test fun `non record exercise is unmarked while independent improved variations are marked`() {
        val oldBench = exercise(id = "oldbench", workout = "old")
        val oldPullup = exercise(id = "oldpullup", variation = "pullup", workout = "old", convention = LoadConvention.BODYWEIGHT)
        val bench = exercise()
        val pullup = exercise(id = "pullup", variation = "pullup", position = 1, convention = LoadConvention.BODYWEIGHT)
        val result = card(AppData(workouts = listOf(workout("old", 0), workout()),
            workoutExercises = listOf(oldBench, oldPullup, bench, pullup),
            sets = listOf(set(oldBench, 22.0, 8), set(oldPullup, 0.0, 8),
                set(bench, 22.0, 6), set(pullup, 0.0, 12))))
        assertEquals(1, result.prCount)
        assertFalse(result.rows[0].hasPr)
        assertTrue(result.rows[1].hasPr)
    }

    @Test fun `workout number is selected chronological ordinal and excludes active workouts`() {
        val target = workout("selected", 2000)
        val result = card(AppData(workouts = listOf(workout("last", 3000), target,
            workout("active", 500, null, WorkoutStatus.ACTIVE), workout("first", 1000))))
        assertEquals(2, result.workoutNumber)
        assertEquals("selected", result.workoutId)
        assertEquals(target.name, result.workoutName)
    }

    @Test fun `equal timestamps use deterministic finished chronology not input order`() {
        val result = card(AppData(workouts = listOf(workout("z", 1000, 3000),
            workout("selected", 1000, 2000), workout("a", 1000, 2000))))
        assertEquals(2, result.workoutNumber)
    }

    @Test fun `card uses selected saved snapshot and ignores catalog changes and active session`() {
        val saved = exercise().copy(name = "Original saved bench")
        val active = exercise(id = "activeentry", workout = "active")
        val result = card(AppData(workouts = listOf(workout(), workout("active", 5000, null, WorkoutStatus.ACTIVE)),
            workoutExercises = listOf(saved, active), sets = listOf(set(saved, 22.0, 6), set(active, 999.0, 99))))
        assertEquals("Original saved bench", result.rows.single().exerciseName)
        assertEquals("22 kg × 6", result.rows.single().bestSetText)
        assertEquals("132 kg", result.volumeText)
        assertEquals("2:03", result.durationText)
    }

    @Test fun `locale and zone apply to dates and numbers without translating English app labels`() {
        val entry = exercise()
        val source = data(entry, set(entry, 22.5, 6))
        val us = card(source)
        val de = card(source, locale = Locale.GERMANY)
        val shifted = WorkoutShareCards.from(source, "selected", false, Locale.US, ZoneId.of("Pacific/Auckland"))
        assertEquals("22,5 kg × 6", de.rows.single().bestSetText)
        assertNotEquals(us.dateTime, de.dateTime)
        assertNotEquals(us.dateTime, shifted.dateTime)
    }

    @Test fun `duration supports hours and absent end time cannot grow`() {
        val long = card(AppData(workouts = listOf(workout(end = 1000 + 3_661_000))))
        assertEquals("1:01:01", long.durationText)
        val incompleteOldSnapshot = card(AppData(workouts = listOf(workout(end = null))))
        assertEquals("0:00", incompleteOldSnapshot.durationText)
    }

    @Test fun `missing and active workouts fail with a useful sharing message`() {
        val missing = runCatching { card(AppData()) }.exceptionOrNull()
        assertTrue(missing is IllegalArgumentException)
        assertTrue(missing!!.message!!.contains("no longer available"))
        val active = runCatching { card(AppData(workouts = listOf(workout(status = WorkoutStatus.ACTIVE)))) }.exceptionOrNull()
        assertTrue(active is IllegalArgumentException)
        assertEquals("Finish this workout before sharing it.", active!!.message)
    }
}
