package com.mooscles

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mooscles.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in additive setup. Existing application data, settings and timers stay untouched. */
@RunWith(AndroidJUnit4::class)
class ThreadComparisonFixtureTest {
    @Test
    fun appendComparisonRoutineWithoutChangingExistingData() = runBlocking(Dispatchers.IO) {
        assumeTrue("Pass -e threadComparisonFixture true to append the comparison routine",
            InstrumentationRegistry.getArguments().getString("threadComparisonFixture") == "true")
        val app = ApplicationProvider.getApplicationContext<MoosclesApplication>()
        val repo = app.repository
        // Do not initialize, synchronize PRs, or read/write settings here.
        // Initialization could legitimately modify other routines' inherited targets.
        val added = app.database.writeTransaction {
            val before = repo.snapshot()
            val builtIns = before.exercises.filterNot { it.custom }.sortedBy { it.id }
            val weighted = builtIns.filter { it.convention == LoadConvention.TOTAL_LOAD }.take(7)
            val bodyweight = builtIns.firstOrNull { it.convention == LoadConvention.BODYWEIGHT }
            require(weighted.size == 7 && bodyweight != null) {
                "Open the application once to initialize its exercise catalog before adding this fixture."
            }
            val catalog = weighted + bodyweight
            val existing = before.routines.firstOrNull { it.id == ROUTINE_ID }
            if (existing != null) {
                verifyRoutine(before, catalog)
                return@writeTransaction false
            }
            val dao = app.database.dao()
            val newEntryIds = catalog.indices.mapTo(hashSetOf()) { "$ROUTINE_ID-exercise-$it" }
            val position = (before.routines.maxOfOrNull { it.position } ?: -1) + 1
            dao.insertRoutine(Routine(ROUTINE_ID, ROUTINE_NAME, position))
            catalog.forEachIndexed { index, exercise ->
                val entryId = "$ROUTINE_ID-exercise-$index"
                val load = if (exercise.convention == LoadConvention.BODYWEIGHT) null else 20.0
                dao.insertRoutineExercise(RoutineExercise(entryId, ROUTINE_ID, exercise.id, index,
                    plannedSets = 4, targetReps = 8, targetWeightKg = load, restSeconds = 120))
                repeat(4) { setIndex ->
                    dao.insertRoutineSet(RoutineSet("$entryId-set-$setIndex", entryId, setIndex,
                        targetReps = 8, targetWeightKg = load, inheritPr = false))
                }
            }
            val after = repo.snapshot()
            verifyRoutine(after, catalog)
            // Check preservation within the same transaction, without racing a UI writer.
            require(after.exercises == before.exercises && after.workouts == before.workouts &&
                after.workoutExercises == before.workoutExercises && after.sets == before.sets) {
                "Comparison setup must not modify exercise or workout data."
            }
            require(after.routines.filterNot { it.id == ROUTINE_ID } == before.routines &&
                after.routineExercises.filterNot { it.routineId == ROUTINE_ID } == before.routineExercises &&
                after.routineSets.filterNot { it.routineExerciseId in newEntryIds } == before.routineSets) {
                "Comparison setup must not modify existing routine prescriptions."
            }
            true
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("routineId", ROUTINE_ID)
            putString("routineName", ROUTINE_NAME)
            putString("status", if (added) "created" else "verified-existing")
            putInt("exerciseCount", 8)
            putInt("setsPerExercise", 4)
            putBoolean("existingDataPreserved", true)
        })
    }

    private fun verifyRoutine(data: AppData, catalog: List<Exercise>) {
        val routine = requireNotNull(data.routines.firstOrNull { it.id == ROUTINE_ID })
        require(routine.name == ROUTINE_NAME) {
            "The reserved comparison routine ID already exists with another name; it was not changed."
        }
        val entries = data.routineItems(ROUTINE_ID)
        require(entries.size == 8) { "The existing comparison routine must contain exactly eight exercises; it was not changed." }
        entries.forEachIndexed { index, entry ->
            val exercise = catalog[index]
            val load = if (exercise.convention == LoadConvention.BODYWEIGHT) null else 20.0
            require(entry.id == "$ROUTINE_ID-exercise-$index" && entry.exerciseId == exercise.id &&
                entry.position == index && entry.plannedSets == 4 && entry.targetReps == 8 &&
                entry.targetWeightKg == load && entry.restSeconds == 120) {
                "The existing comparison exercise $index differs from the fixed prescription; it was not changed."
            }
            val targets = data.plannedSets(entry.id)
            require(targets.size == 4 && targets.withIndex().all { (setIndex, set) ->
                set.id == "${entry.id}-set-$setIndex" && set.position == setIndex &&
                    set.targetReps == 8 && set.targetWeightKg == load && !set.inheritPr
            }) { "The existing comparison sets differ from the fixed prescription; they were not changed." }
        }
    }

    private companion object {
        const val ROUTINE_ID = "thread-comparison-routine"
        const val ROUTINE_NAME = "Thread comparison"
    }
}
