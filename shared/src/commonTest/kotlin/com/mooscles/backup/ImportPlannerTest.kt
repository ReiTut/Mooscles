package com.mooscles.backup

import com.mooscles.data.AppData
import com.mooscles.data.EditRules
import com.mooscles.data.Equipment
import com.mooscles.data.Exercise
import com.mooscles.data.LoadConvention
import com.mooscles.data.MuscleGroup
import com.mooscles.data.Routine
import com.mooscles.data.RoutineExercise
import com.mooscles.data.RoutineFolder
import com.mooscles.data.RoutineSet
import com.mooscles.data.RoutineTargetCheckpoint
import com.mooscles.data.Workout
import com.mooscles.data.WorkoutExercise
import com.mooscles.data.WorkoutSet
import com.mooscles.data.WorkoutStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportPlannerTest {
    private val bench = Exercise("bench", "Bench press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, custom = false)
    private val curl = Exercise("curl", "Cable curl", MuscleGroup.BICEPS, Equipment.CABLE, LoadConvention.TOTAL_LOAD, custom = true)
    private val defaultFolder = RoutineFolder(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME, 0)
    private val upper = RoutineFolder("upper", "Upper", 1)
    private val push = Routine("push", "Push", 0, "upper")
    private val pushBench = RoutineExercise("re1", "push", "bench", 0, 2, 8, 50.0, 90)
    private val pushSets = listOf(
        RoutineSet("rs1", "re1", 0, 8, 50.0, inheritPr = true),
        RoutineSet("rs2", "re1", 1, 8, 50.0, inheritPr = false),
    )
    private val finished = Workout("w1", "Push", 1_000L, 2_000L, WorkoutStatus.FINISHED, "push")
    private val active = Workout("w2", "Arms", 3_000L, null, WorkoutStatus.ACTIVE, null)
    private val finishedBench = WorkoutExercise(
        "we1", "w1", "bench", "Bench press", MuscleGroup.CHEST, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, 0, 90,
    )
    private val activeCurl = WorkoutExercise(
        "we2", "w2", "curl", "Cable curl", MuscleGroup.BICEPS, Equipment.CABLE, LoadConvention.TOTAL_LOAD, 0, 60,
    )
    private val workoutSets = listOf(
        WorkoutSet("ws1", "we1", 0, 8, 50.0, completed = true),
        WorkoutSet("ws2", "we1", 1, 8, 50.0, completed = true),
        WorkoutSet("ws3", "we2", 0, 10, 20.0, completed = false),
    )
    private val checkpoint = RoutineTargetCheckpoint("bench", LoadConvention.TOTAL_LOAD, 50.0, 8)
    private val sample = AppData(
        exercises = listOf(bench, curl),
        routines = listOf(push),
        routineExercises = listOf(pushBench),
        workouts = listOf(finished, active),
        workoutExercises = listOf(finishedBench, activeCurl),
        sets = workoutSets,
        routineSets = pushSets,
        routineFolders = listOf(defaultFolder, upper),
    )
    private val preferences = BackupPreferences(pounds = true, theme = "dark", autoRest = false, defaultRestSeconds = 90, selectedRoutineFolderId = "upper")

    /** A fresh install: the seeded default folder and the catalog, nothing the user made. */
    private val fresh = LocalState(AppData(exercises = listOf(bench), routineFolders = listOf(defaultFolder)), emptyList())

    private fun fileWith(
        data: AppData = sample,
        checkpoints: List<RoutineTargetCheckpoint> = listOf(checkpoint),
        prefs: BackupPreferences = preferences,
        edit: (BackupData) -> BackupData = { it },
    ) = BackupFile(
        format = BackupCodec.FORMAT,
        formatVersion = BackupCodec.FORMAT_VERSION,
        exportedAt = "2026-10-27T12:00:00Z",
        app = BackupApp("1.5.0", 17, "android"),
        preferences = prefs,
        data = edit(BackupData.from(data, checkpoints)),
    )

    private fun assertInvalid(vararg mentions: String, block: () -> Unit) {
        val error = assertFailsWith<BackupException.Invalid> { block() }
        mentions.forEach { assertTrue(error.detail.contains(it), "\"${error.detail}\" should mention $it") }
    }

    private fun assertNothingInserted(entities: BackupEntities) {
        assertEquals(BackupEntities(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), entities)
    }

    @Test
    fun emptyLocalImportsEverythingAndRestoresPreferences() {
        val plan = ImportPlanner.plan(fileWith(), fresh)

        assertEquals(listOf(curl), plan.entities.exercises)
        assertEquals(listOf(upper), plan.entities.routineFolders)
        assertEquals(listOf(push), plan.entities.routines)
        assertEquals(listOf(pushBench), plan.entities.routineExercises)
        assertEquals(pushSets, plan.entities.routineSets)
        assertEquals(listOf(finished, active), plan.entities.workouts)
        assertEquals(listOf(finishedBench, activeCurl), plan.entities.workoutExercises)
        assertEquals(workoutSets, plan.entities.workoutSets)
        assertEquals(listOf(checkpoint), plan.entities.checkpoints)
        assertEquals(ImportSummary(workoutsAdded = 2, workoutsKept = 0, routinesAdded = 1, routinesKept = 0, customExercisesAdded = 1, restoresPreferences = true, nothingToAdd = false), plan.summary)
        assertEquals(preferences, plan.preferences)
    }

    /** Exported, written as text and read back: a planned warm-up arrives as one, next to a working set. */
    @Test
    fun plannedWarmupsImport() {
        val withWarmup = sample.copy(routineSets = listOf(pushSets[0].copy(inheritPr = false, warmup = true), pushSets[1]))
        val file = BackupCodec.decode(BackupCodec.encode(fileWith(withWarmup)))
        val plan = ImportPlanner.plan(file, fresh)

        assertEquals(withWarmup.routineSets, plan.entities.routineSets)
        assertEquals(listOf(true, false), plan.entities.routineSets.map { it.warmup })
    }

    @Test
    fun existingWorkoutSkipsWholeAggregateEvenWithExtraSets() {
        val localWorkout = AppData(
            exercises = listOf(bench), routineFolders = listOf(defaultFolder),
            workouts = listOf(finished), workoutExercises = listOf(finishedBench), sets = workoutSets.take(2),
        )
        val file = fileWith(
            AppData(
                exercises = listOf(bench), routineFolders = listOf(defaultFolder),
                workouts = listOf(finished.copy(name = "Changed")), workoutExercises = listOf(finishedBench),
                sets = workoutSets.take(2) + WorkoutSet("ws9", "we1", 2, 5, 60.0, completed = true),
            ),
            checkpoints = emptyList(),
        )

        val plan = ImportPlanner.plan(file, LocalState(localWorkout, emptyList()))

        assertTrue(plan.entities.workoutSets.isEmpty())
        assertTrue(plan.entities.workoutExercises.isEmpty())
        assertTrue(plan.entities.workouts.isEmpty())
        assertEquals(1, plan.summary.workoutsKept)
        assertEquals(0, plan.summary.workoutsAdded)
    }

    @Test
    fun existingRoutineSkipsItsItemsAndPlannedSets() {
        val localRoutine = fresh.data.copy(
            routineFolders = listOf(defaultFolder, upper),
            routines = listOf(push), routineExercises = listOf(pushBench), routineSets = pushSets.take(1),
        )
        val extraItem = RoutineExercise("re9", "push", "bench", 1, 1, 5, null, null)
        val file = fileWith(
            localRoutine.copy(
                routineExercises = listOf(pushBench, extraItem),
                routineSets = pushSets + RoutineSet("rs9", "re9", 0, 5, null, inheritPr = false),
            ),
            checkpoints = emptyList(),
        )

        val plan = ImportPlanner.plan(file, LocalState(localRoutine, emptyList()))

        assertTrue(plan.entities.routines.isEmpty())
        assertTrue(plan.entities.routineExercises.isEmpty())
        assertTrue(plan.entities.routineSets.isEmpty())
        assertEquals(1, plan.summary.routinesKept)
        assertEquals(0, plan.summary.routinesAdded)
    }

    @Test
    fun planningSameFileAgainstItsOwnImportIsEmpty() {
        val plan = ImportPlanner.plan(fileWith(), LocalState(sample, listOf(checkpoint)))

        assertNothingInserted(plan.entities)
        assertNull(plan.preferences)
        assertEquals(ImportSummary(0, 2, 0, 1, 0, restoresPreferences = false, nothingToAdd = true), plan.summary)
    }

    @Test
    fun fileAddingOnlyAFolderOrCatalogExerciseHasSomethingToAdd() {
        val local = LocalState(sample, listOf(checkpoint))
        val legs = RoutineFolder("legs", "Legs", 2)

        val folderOnly = ImportPlanner.plan(fileWith(sample.copy(routineFolders = sample.routineFolders + legs)), local)

        assertEquals(listOf("legs"), folderOnly.entities.routineFolders.map { it.id })
        assertEquals(ImportSummary(0, 2, 0, 1, 0, restoresPreferences = false, nothingToAdd = false), folderOnly.summary)

        val squat = Exercise("squat", "Back squat", MuscleGroup.QUADRICEPS, Equipment.BARBELL, LoadConvention.TOTAL_LOAD, custom = false)
        val catalogOnly = ImportPlanner.plan(fileWith(sample.copy(exercises = sample.exercises + squat)), local)

        assertEquals(listOf(squat), catalogOnly.entities.exercises)
        assertEquals(0, catalogOnly.summary.customExercisesAdded)
        assertFalse(catalogOnly.summary.nothingToAdd)
    }

    @Test
    fun routineWithUnknownFolderGoesToDefaultFolder() {
        val localFolder = RoutineFolder("legs", "Legs", 1)
        val local = LocalState(fresh.data.copy(routineFolders = listOf(defaultFolder, localFolder)), emptyList())
        val orphan = Routine("orphan", "Orphan", 0, "deleted-folder")
        val onPhoneFolder = Routine("squat-day", "Squat day", 0, "legs")
        val file = fileWith(sample.copy(routines = listOf(push, orphan, onPhoneFolder)))

        val plan = ImportPlanner.plan(file, local)

        val folders = plan.entities.routines.associate { it.id to it.folderId }
        assertEquals(RoutineFolder.DEFAULT_ID, folders["orphan"])
        assertEquals("upper", folders["push"])
        assertEquals("legs", folders["squat-day"])
    }

    @Test
    fun importedRoutinesAppendAfterLocalOnesInSameFolder() {
        val local = LocalState(
            fresh.data.copy(routines = listOf(Routine("a", "A", 0), Routine("b", "B", 1))),
            emptyList(),
        )
        // Listed out of order: the file's position, not its list order, is the relative order.
        val file = fileWith(
            sample.copy(
                routines = listOf(Routine("y", "Y", 1), Routine("x", "X", 0), push),
                routineExercises = emptyList(), routineSets = emptyList(),
            ),
        )

        val plan = ImportPlanner.plan(file, local)

        // Routine positions are global on the phone: every inserted routine continues after the local
        // maximum, ordered by (file position, file index), whatever folder it lands in.
        val positions = plan.entities.routines.associate { it.id to it.position }
        assertEquals(2, positions["x"])
        assertEquals(3, positions["push"])
        assertEquals(4, positions["y"])
    }

    @Test
    fun importedRoutinesInSeveralFoldersGetUniquePositionsAfterGlobalMax() {
        val legs = RoutineFolder("legs", "Legs", 2)
        val local = LocalState(
            fresh.data.copy(
                routineFolders = listOf(defaultFolder, upper, legs),
                routines = listOf(Routine("a", "A", 0, "legs"), Routine("b", "B", 1, "legs"), Routine("c", "C", 2, "legs")),
            ),
            emptyList(),
        )
        val file = fileWith(
            sample.copy(
                routines = listOf(Routine("z", "Z", 0, RoutineFolder.DEFAULT_ID), push.copy(position = 1), Routine("x", "X", 0, "upper")),
                routineExercises = emptyList(), routineSets = emptyList(),
            ),
        )

        val plan = ImportPlanner.plan(file, local)

        val positions = plan.entities.routines.map { it.position }
        assertEquals(positions.size, positions.toSet().size)
        assertEquals(listOf("z" to 3, "push" to 5, "x" to 4), plan.entities.routines.map { it.id to it.position })
        assertEquals(listOf(RoutineFolder.DEFAULT_ID, "upper", "upper"), plan.entities.routines.map { it.folderId })
    }

    @Test
    fun routinePositionsKeptWhenPhoneHasNoRoutines() {
        val file = fileWith(sample.copy(routines = listOf(push.copy(position = 7), Routine("x", "X", 3, RoutineFolder.DEFAULT_ID))))

        val plan = ImportPlanner.plan(file, fresh)

        assertEquals(listOf(7, 3), plan.entities.routines.map { it.position })
    }

    @Test
    fun importedFoldersAppendAfterLocalOnes() {
        val local = LocalState(fresh.data.copy(routineFolders = listOf(defaultFolder, RoutineFolder("legs", "Legs", 4))), emptyList())
        val file = fileWith(sample.copy(routineFolders = listOf(defaultFolder, RoutineFolder("arms", "Arms", 2), upper)))

        val plan = ImportPlanner.plan(file, local)

        assertEquals(listOf("arms" to 6, "upper" to 5), plan.entities.routineFolders.map { it.id to it.position })
    }

    @Test
    fun preferencesKeptWhenLocalHasData() {
        val withWorkout = LocalState(fresh.data.copy(workouts = listOf(Workout("old", "Old", 10L, 20L, WorkoutStatus.FINISHED))), emptyList())
        val withRoutine = LocalState(fresh.data.copy(routines = listOf(Routine("old", "Old", 0))), emptyList())

        listOf(withWorkout, withRoutine).forEach { local ->
            val plan = ImportPlanner.plan(fileWith(), local)
            assertNull(plan.preferences)
            assertFalse(plan.summary.restoresPreferences)
        }
    }

    @Test
    fun selectedFolderMissingAfterImportFallsBackToDefault() {
        val plan = ImportPlanner.plan(fileWith(prefs = preferences.copy(selectedRoutineFolderId = "gone")), fresh)

        assertEquals(RoutineFolder.DEFAULT_ID, plan.preferences?.selectedRoutineFolderId)
    }

    @Test
    fun duplicateIdInsideFileIsInvalid() {
        assertInvalid("w1") { ImportPlanner.plan(fileWith(sample.copy(workouts = listOf(finished, finished))), fresh) }
        assertInvalid("ws1") { ImportPlanner.plan(fileWith(sample.copy(sets = workoutSets + workoutSets[0])), fresh) }
        assertInvalid("bench") { ImportPlanner.plan(fileWith(checkpoints = listOf(checkpoint, checkpoint.copy(reps = 3))), fresh) }
        // Even when the duplicated record already exists on the phone, the file is inconsistent.
        assertInvalid("upper") {
            ImportPlanner.plan(fileWith(sample.copy(routineFolders = listOf(defaultFolder, upper, upper))), LocalState(sample, emptyList()))
        }
    }

    @Test
    fun missingReferenceIsInvalid() {
        assertInvalid("re1", "nope") {
            ImportPlanner.plan(fileWith(sample.copy(routineExercises = listOf(pushBench.copy(routineId = "nope")))), fresh)
        }
        assertInvalid("re1", "nope") {
            ImportPlanner.plan(fileWith(sample.copy(routineExercises = listOf(pushBench.copy(exerciseId = "nope")))), fresh)
        }
        assertInvalid("rs1", "nope") {
            ImportPlanner.plan(fileWith(sample.copy(routineSets = listOf(pushSets[0].copy(routineExerciseId = "nope")))), fresh)
        }
        assertInvalid("we1", "nope") {
            ImportPlanner.plan(fileWith(sample.copy(workoutExercises = listOf(finishedBench.copy(workoutId = "nope"), activeCurl))), fresh)
        }
        assertInvalid("ws1", "nope") {
            ImportPlanner.plan(fileWith(sample.copy(sets = listOf(workoutSets[0].copy(workoutExerciseId = "nope")))), fresh)
        }
    }

    @Test
    fun referenceToExerciseOnlyOnPhoneResolves() {
        // The file's routine uses "bench", which the file omits but the phone has.
        val file = fileWith(sample.copy(exercises = listOf(curl)))

        val plan = ImportPlanner.plan(file, fresh)

        assertEquals(listOf(pushBench), plan.entities.routineExercises)
    }

    @Test
    fun childIdTakenByAnotherAggregateOnPhoneIsInvalid() {
        // The phone already uses "we1" inside a different workout; inserting it would collide.
        val local = LocalState(
            fresh.data.copy(
                workouts = listOf(Workout("other", "Other", 10L, 20L, WorkoutStatus.FINISHED)),
                workoutExercises = listOf(finishedBench.copy(workoutId = "other")),
            ),
            emptyList(),
        )

        assertInvalid("we1") { ImportPlanner.plan(fileWith(), local) }
    }

    /** Checked even when the phone keeps its own preferences, so an invalid file is always refused whole. */
    private val withData = LocalState(fresh.data.copy(workouts = listOf(Workout("old", "Old", 10L, 20L, WorkoutStatus.FINISHED))), emptyList())

    @Test
    fun invalidThemeIsInvalid() {
        for (theme in listOf("neon", "DARK", "")) {
            for (local in listOf(fresh, withData)) {
                assertInvalid("theme", "\"$theme\"") { ImportPlanner.plan(fileWith(prefs = preferences.copy(theme = theme)), local) }
            }
        }
    }

    @Test
    fun outOfRangeRestIsInvalid() {
        for (seconds in listOf(-1, 301, 900)) {
            for (local in listOf(fresh, withData)) {
                assertInvalid("defaultRestSeconds", "$seconds") {
                    ImportPlanner.plan(fileWith(prefs = preferences.copy(defaultRestSeconds = seconds)), local)
                }
            }
        }
    }

    @Test
    fun twoActiveWorkoutsAreInvalid() {
        val second = active.copy(id = "w3")

        assertInvalid("active") { ImportPlanner.plan(fileWith(sample.copy(workouts = listOf(finished, active, second))), fresh) }
    }

    @Test
    fun finishedHistoryWithLongRestIsAccepted() {
        val longRest = finishedBench.copy(restSeconds = 400)
        // An old completed set with zero reps is also outside today's editor rules.
        val oldSet = workoutSets[0].copy(reps = 0, completed = true)
        val file = fileWith(sample.copy(workoutExercises = listOf(longRest, activeCurl), sets = listOf(oldSet) + workoutSets.drop(1)))

        val plan = ImportPlanner.plan(file, fresh)

        assertEquals(400, plan.entities.workoutExercises.first { it.id == "we1" }.restSeconds)
        assertEquals(oldSet, plan.entities.workoutSets.first { it.id == "ws1" })
    }

    @Test
    fun routineWithLongRestIsInvalid() {
        assertInvalid("re1") { ImportPlanner.plan(fileWith(sample.copy(routineExercises = listOf(pushBench.copy(restSeconds = 400)))), fresh) }
    }

    @Test
    fun activeWorkoutFollowsEditRules() {
        assertInvalid("we2") {
            ImportPlanner.plan(fileWith(sample.copy(workoutExercises = listOf(finishedBench, activeCurl.copy(restSeconds = 400)))), fresh)
        }
        assertInvalid("ws3") {
            ImportPlanner.plan(fileWith(sample.copy(sets = workoutSets.dropLast(1) + workoutSets[2].copy(reps = 0, completed = true))), fresh)
        }
        assertInvalid("w2") { ImportPlanner.plan(fileWith(sample.copy(workouts = listOf(finished, active.copy(name = "x".repeat(121))))), fresh) }
    }

    @Test
    fun editRulesApplyToFoldersRoutinesAndCustomExercises() {
        val tooLong = "x".repeat(121)
        assertInvalid("upper") { ImportPlanner.plan(fileWith(sample.copy(routineFolders = listOf(defaultFolder, upper.copy(name = tooLong)))), fresh) }
        assertInvalid("push") { ImportPlanner.plan(fileWith(sample.copy(routines = listOf(push.copy(name = tooLong)))), fresh) }
        assertInvalid("curl") { ImportPlanner.plan(fileWith(sample.copy(exercises = listOf(bench, curl.copy(name = tooLong)))), fresh) }
        assertInvalid("re1") { ImportPlanner.plan(fileWith(sample.copy(routineExercises = listOf(pushBench.copy(plannedSets = 0)))), fresh) }
        assertInvalid("rs1") {
            ImportPlanner.plan(fileWith(sample.copy(routineSets = listOf(pushSets[0].copy(targetReps = 10_000), pushSets[1]))), fresh)
        }
        val tooManyPlanned = List(EditRules.MAX_ROUTINE_SETS + 1) { RoutineSet("p$it", "re1", it, 8, null) }
        assertInvalid("re1") { ImportPlanner.plan(fileWith(sample.copy(routineSets = tooManyPlanned)), fresh) }
        val tooManyActive = List(EditRules.MAX_WORKOUT_SETS + 1) { WorkoutSet("a$it", "we2", it, 1, 1.0) }
        assertInvalid("we2") { ImportPlanner.plan(fileWith(sample.copy(sets = workoutSets.take(2) + tooManyActive)), fresh) }
    }

    @Test
    fun invalidRecordAlreadyOnPhoneDoesNotFailFile() {
        val broken = fileWith(
            sample.copy(
                workouts = listOf(finished.copy(name = " "), active),
                sets = listOf(workoutSets[0].copy(weightKg = -1.0)) + workoutSets.drop(1),
            ),
        )
        val local = LocalState(
            fresh.data.copy(workouts = listOf(finished), workoutExercises = listOf(finishedBench), sets = workoutSets.take(2)),
            emptyList(),
        )

        val plan = ImportPlanner.plan(broken, local)

        assertEquals(1, plan.summary.workoutsKept)
        assertEquals(listOf(active), plan.entities.workouts)
        // The same record is rejected when it would actually be inserted.
        assertInvalid("w1") { ImportPlanner.plan(broken, fresh) }
    }

    @Test
    fun negativeOrNonFiniteNumbersAreInvalid() {
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { weight ->
            val file = fileWith(sample.copy(sets = listOf(workoutSets[0].copy(weightKg = weight)) + workoutSets.drop(1)))
            assertInvalid("ws1") { ImportPlanner.plan(file, fresh) }
        }
        assertInvalid("we1") {
            ImportPlanner.plan(fileWith(sample.copy(workoutExercises = listOf(finishedBench.copy(position = -1), activeCurl))), fresh)
        }
        assertInvalid("bench") { ImportPlanner.plan(fileWith(checkpoints = listOf(checkpoint.copy(weightKg = Double.NaN))), fresh) }
    }

    @Test
    fun blankIdsNamesAndBadTimesAreInvalid() {
        assertInvalid { ImportPlanner.plan(fileWith(sample.copy(sets = workoutSets + WorkoutSet("", "we1", 2))), fresh) }
        assertInvalid("we1") {
            ImportPlanner.plan(fileWith(sample.copy(workoutExercises = listOf(finishedBench.copy(name = ""), activeCurl))), fresh)
        }
        assertInvalid("bench") { ImportPlanner.plan(fileWith(sample.copy(exercises = listOf(bench.copy(id = "bench2", name = " "), bench, curl))), fresh) }
        assertInvalid("w1") { ImportPlanner.plan(fileWith(sample.copy(workouts = listOf(finished.copy(startedAt = 0L), active))), fresh) }
        assertInvalid("w1") { ImportPlanner.plan(fileWith(sample.copy(workouts = listOf(finished.copy(endedAt = 999L), active))), fresh) }
    }

    @Test
    fun customExercisesWithSameNameButDifferentIdsBothSurvive() {
        val local = LocalState(fresh.data.copy(exercises = listOf(bench, curl)), emptyList())
        val sameNameElsewhere = curl.copy(id = "curl-from-tablet")
        val file = fileWith(sample.copy(exercises = listOf(bench, sameNameElsewhere), workoutExercises = listOf(finishedBench, activeCurl.copy(exerciseId = "curl-from-tablet"))))

        val plan = ImportPlanner.plan(file, local)

        assertEquals(listOf(sameNameElsewhere), plan.entities.exercises)
        assertEquals(1, plan.summary.customExercisesAdded)
    }

    @Test
    fun catalogExerciseMissingLocallyIsInsertedFromFile() {
        val rower = Exercise("rower", "Seated row", MuscleGroup.BACK, Equipment.CABLE, LoadConvention.TOTAL_LOAD, custom = false)
        val item = RoutineExercise("re2", "push", "rower", 1, 1, 10, null, null)
        val file = fileWith(sample.copy(exercises = listOf(bench, curl, rower), routineExercises = listOf(pushBench, item)))

        val plan = ImportPlanner.plan(file, fresh)

        assertEquals(listOf(curl, rower), plan.entities.exercises)
        assertEquals(listOf(pushBench, item), plan.entities.routineExercises)
        assertEquals(1, plan.summary.customExercisesAdded)
    }
}
