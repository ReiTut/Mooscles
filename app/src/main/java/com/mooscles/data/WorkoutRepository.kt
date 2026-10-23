package com.mooscles.data

import androidx.room.withTransaction
import com.mooscles.domain.RestDuration
import com.mooscles.domain.RoutineTargetRecords
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small atomic commands shared by active tracking, routine editing, and history editing. */
class WorkoutRepository(private val db: WorkoutDatabase) {
    private val dao = db.dao()

    val data: Flow<AppData> = db.invalidationTracker.createFlow(
        "exercises", "routines", "routine_exercises", "routine_sets", "workouts", "workout_exercises", "workout_sets"
    ).map { snapshot() }

    suspend fun initialize() = db.withTransaction {
        dao.seedExercises(ExerciseCatalog.exercises)
        val catalog = ExerciseCatalog.exercises.associateBy { it.id }
        dao.exercises().filterNot { it.custom }.forEach { existing ->
            catalog[existing.id]?.let { current ->
                if (existing.name != current.name || existing.muscle != current.muscle || existing.equipment != current.equipment) {
                    dao.updateExercise(existing.copy(name = current.name, muscle = current.muscle, equipment = current.equipment))
                }
            }
        }
        dao.routineExercises().forEach { ensurePlannedSets(it) }
        // Update configurable values only; completed workout snapshots stay unchanged.
        dao.routineExercises().filter { (it.restSeconds ?: 0) > RestDuration.MAX_SECONDS }.forEach {
            dao.updateRoutineExercise(it.copy(restSeconds = RestDuration.MAX_SECONDS))
        }
        val activeIds = dao.workouts().filter { it.status == WorkoutStatus.ACTIVE }.map { it.id }.toSet()
        dao.workoutExercises().filter { it.workoutId in activeIds && it.restSeconds > RestDuration.MAX_SECONDS }.forEach {
            dao.updateWorkoutExercise(it.copy(restSeconds = RestDuration.MAX_SECONDS))
        }
        synchronizeRoutineTargets()
    }

    suspend fun snapshot(): AppData = db.withTransaction {
        AppData(dao.exercises(), dao.routines(), dao.routineExercises(), dao.workouts(), dao.workoutExercises(), dao.sets(), dao.routineSets())
    }

    /** Run only inside a repository transaction, never while rendering UI or editing an active set. */
    private suspend fun synchronizeRoutineTargets() {
        val records = RoutineTargetRecords.latest(snapshot()).associateBy { it.exerciseId to it.convention }
        val checkpoints = dao.targetCheckpoints().associateBy { it.exerciseId to it.convention }
        val exercises = dao.exercises().associateBy { it.id }
        dao.routineExercises().forEach { plan ->
            val exercise = requireNotNull(exercises[plan.exerciseId]) { "Exercise not found." }
            val record = records[plan.exerciseId to exercise.convention]
            val reps = record?.reps ?: 0
            val weightKg = record?.weightKg
            val changed = dao.plannedSets(plan.id).filter {
                it.inheritPr && (it.targetReps != reps || it.targetWeightKg != weightKg)
            }
            changed.forEach { row ->
                dao.updateRoutineSet(row.copy(targetWeightKg = weightKg, targetReps = reps))
            }
            if (changed.isNotEmpty()) mirrorPlan(plan.id)
        }
        // Row modes now protect manual values. A deleted/corrected maximum must not leave an old
        // checkpoint suppressing new inherited targets; retain markers only for compatibility.
        records.values.forEach { record ->
            if (checkpoints[record.exerciseId to record.convention]?.record() != record) {
                dao.saveTargetCheckpoint(RoutineTargetCheckpoint(record.exerciseId, record.convention, record.weightKg, record.reps))
            }
        }
    }

    private suspend fun inheritedSet(plan: RoutineExercise, position: Int, id: String = newId()): RoutineSet {
        val exercise = requireNotNull(dao.exercise(plan.exerciseId)) { "Exercise not found." }
        val record = RoutineTargetRecords.latest(snapshot()).firstOrNull {
            it.exerciseId == plan.exerciseId && it.convention == exercise.convention
        }
        return RoutineSet(id, plan.id, position, record?.reps ?: 0, record?.weightKg, inheritPr = true)
    }

    /** Fallback for plans inserted by old import/test clients; real upgrades are handled by migration. */
    private suspend fun ensurePlannedSets(plan: RoutineExercise): List<RoutineSet> {
        var rows = dao.plannedSets(plan.id)
        if (rows.isEmpty()) {
            repeat(plan.plannedSets) { position ->
                dao.insertRoutineSet(RoutineSet(routineExerciseId = plan.id, position = position,
                    targetReps = plan.targetReps, targetWeightKg = plan.targetWeightKg, inheritPr = false))
            }
            rows = dao.plannedSets(plan.id)
        }
        return rows
    }

    /** Retain the old columns for compatibility; independent rows remain the source of truth. */
    private suspend fun mirrorPlan(id: String) {
        val plan = requireNotNull(dao.routineExercise(id)) { "Routine exercise not found." }
        val rows = dao.plannedSets(id)
        val first = rows.firstOrNull()
        dao.updateRoutineExercise(plan.copy(plannedSets = rows.size,
            targetReps = first?.targetReps ?: 0, targetWeightKg = first?.targetWeightKg))
    }

    private fun validateTarget(reps: Int, weightKg: Double?) {
        require(reps in 0..9999) { "Target reps must be blank or between 1 and 9999." }
        require(weightKg == null || (weightKg.isFinite() && weightKg in 0.0..100000.0)) { "Enter a valid non-negative target weight." }
    }

    private fun checkedName(value: String): String = value.trim().also {
        require(it.isNotEmpty()) { "Enter a name." }
        require(it.length <= 120) { "Names must be 120 characters or fewer." }
    }

    suspend fun createRoutine(name: String): String = db.withTransaction {
        val item = Routine(name = checkedName(name), position = dao.routines().size)
        dao.insertRoutine(item)
        item.id
    }

    suspend fun renameRoutine(id: String, name: String) = db.withTransaction {
        val item = requireNotNull(dao.routine(id)) { "Routine not found." }
        dao.updateRoutine(item.copy(name = checkedName(name)))
    }

    suspend fun duplicateRoutine(id: String): String = db.withTransaction {
        val original = requireNotNull(dao.routine(id)) { "Routine not found." }
        val copy = original.copy(id = newId(), name = (original.name.take(113) + " (copy)"), position = dao.routines().size)
        dao.insertRoutine(copy)
        dao.routineItems(id).forEach { originalPlan ->
            val plan = originalPlan.copy(id = newId(), routineId = copy.id)
            dao.insertRoutineExercise(plan)
            ensurePlannedSets(originalPlan).forEach { row ->
                dao.insertRoutineSet(row.copy(id = newId(), routineExerciseId = plan.id))
            }
        }
        copy.id
    }

    suspend fun deleteRoutine(id: String) = db.withTransaction {
        dao.deleteRoutine(id)
        dao.routines().forEachIndexed { index, item -> dao.updateRoutine(item.copy(position = index)) }
    }

    suspend fun moveRoutine(id: String, direction: Int) = db.withTransaction {
        val items = dao.routines().toMutableList()
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateRoutine(item.copy(position = index)) }
    }

    suspend fun reorderRoutines(ids: List<String>) = db.withTransaction {
        val items = dao.routines()
        require(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size) { "Invalid routine order." }
        ids.forEachIndexed { index, id -> dao.updateRoutine(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun addRoutineExercise(routineId: String, exerciseId: String): String = db.withTransaction {
        requireNotNull(dao.routine(routineId)) { "Routine not found." }
        requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        val item = RoutineExercise(routineId = routineId, exerciseId = exerciseId, position = dao.routineItems(routineId).size,
            restSeconds = RestDuration.DEFAULT_SECONDS)
        dao.insertRoutineExercise(item)
        repeat(item.plannedSets) { dao.insertRoutineSet(inheritedSet(item, it)) }
        mirrorPlan(item.id)
        item.id
    }

    suspend fun updateRoutineExercise(item: RoutineExercise) = db.withTransaction {
        val existing = requireNotNull(dao.routineExercise(item.id)) { "Routine exercise not found." }
        require(existing.routineId == item.routineId) { "Cannot move an exercise to another routine." }
        requireNotNull(dao.exercise(item.exerciseId)) { "Exercise not found." }
        require(item.plannedSets in 1..50) { "Planned sets must be between 1 and 50." }
        validateTarget(item.targetReps, item.targetWeightKg)
        require(item.restSeconds == null || item.restSeconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        require(existing.exerciseId == item.exerciseId) { "Use replacement to change exercise identity." }
        val rows = ensurePlannedSets(existing)
        val editedTargets = existing.targetReps != item.targetReps || existing.targetWeightKg != item.targetWeightKg
        rows.drop(item.plannedSets).forEach { dao.deleteRoutineSet(it.id) }
        rows.take(item.plannedSets).forEach { row ->
            if (editedTargets) dao.updateRoutineSet(row.copy(targetReps = item.targetReps,
                targetWeightKg = item.targetWeightKg, inheritPr = false))
        }
        for (position in rows.size until item.plannedSets) {
            dao.insertRoutineSet(if (editedTargets) RoutineSet(routineExerciseId = item.id, position = position,
                targetReps = item.targetReps, targetWeightKg = item.targetWeightKg, inheritPr = false)
                else inheritedSet(item, position))
        }
        dao.updateRoutineExercise(item)
        mirrorPlan(item.id)
    }

    suspend fun addRoutineSet(entryId: String): String = db.withTransaction {
        val plan = requireNotNull(dao.routineExercise(entryId)) { "Routine exercise not found." }
        val rows = ensurePlannedSets(plan)
        require(rows.size < 50) { "A maximum of 50 planned sets is supported per exercise." }
        val row = inheritedSet(plan, rows.size)
        dao.insertRoutineSet(row)
        mirrorPlan(entryId)
        row.id
    }

    suspend fun removeRoutineSet(setId: String) = db.withTransaction {
        val row = requireNotNull(dao.routineSet(setId)) { "Routine set not found." }
        val rows = dao.plannedSets(row.routineExerciseId)
        require(rows.size > 1) { "Keep at least one planned set." }
        dao.deleteRoutineSet(setId)
        dao.plannedSets(row.routineExerciseId).forEachIndexed { index, item ->
            dao.updateRoutineSet(item.copy(position = index))
        }
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) = db.withTransaction {
        validateTarget(reps, weightKg)
        val row = requireNotNull(dao.routineSet(setId)) { "Routine set not found." }
        dao.updateRoutineSet(row.copy(targetReps = reps, targetWeightKg = weightKg, inheritPr = false))
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun inheritRoutineSet(setId: String) = db.withTransaction {
        val row = requireNotNull(dao.routineSet(setId)) { "Routine set not found." }
        val plan = requireNotNull(dao.routineExercise(row.routineExerciseId)) { "Routine exercise not found." }
        dao.updateRoutineSet(inheritedSet(plan, row.position, row.id))
        mirrorPlan(row.routineExerciseId)
    }

    suspend fun updateRoutineExerciseRest(entryId: String, seconds: Int) = db.withTransaction {
        require(seconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        val plan = requireNotNull(dao.routineExercise(entryId)) { "Routine exercise not found." }
        dao.updateRoutineExercise(plan.copy(restSeconds = seconds))
    }

    suspend fun replaceRoutineExercise(id: String, exerciseId: String) = db.withTransaction {
        val item = requireNotNull(dao.routineExercise(id)) { "Routine exercise not found." }
        requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        if (item.exerciseId == exerciseId) return@withTransaction
        val replacement = item.copy(exerciseId = exerciseId, targetWeightKg = null, targetReps = 0)
        dao.updateRoutineExercise(replacement)
        ensurePlannedSets(item).forEach { row ->
            dao.updateRoutineSet(inheritedSet(replacement, row.position, row.id))
        }
        mirrorPlan(id)
    }

    suspend fun removeRoutineExercise(id: String) = db.withTransaction {
        val item = dao.routineExercise(id) ?: return@withTransaction
        dao.deleteRoutineExercise(id)
        dao.routineItems(item.routineId).forEachIndexed { index, entry -> dao.updateRoutineExercise(entry.copy(position = index)) }
    }

    suspend fun moveRoutineExercise(id: String, direction: Int) = db.withTransaction {
        val entry = requireNotNull(dao.routineExercise(id)) { "Routine exercise not found." }
        val items = dao.routineItems(entry.routineId).toMutableList()
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateRoutineExercise(item.copy(position = index)) }
    }

    suspend fun reorderRoutineExercises(routineId: String, ids: List<String>) = db.withTransaction {
        val items = dao.routineItems(routineId)
        require(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size) { "Invalid exercise order." }
        ids.forEachIndexed { index, id -> dao.updateRoutineExercise(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun saveExercise(item: Exercise) = db.withTransaction {
        val valid = item.copy(name = checkedName(item.name))
        val existing = dao.exercise(item.id)
        require(existing == null || existing.custom) { "Only custom exercises can be edited." }
        if (existing == null) dao.insertExercise(valid.copy(custom = true)) else dao.updateExercise(valid.copy(custom = true))
    }

    suspend fun createCustomExercise(name: String, muscle: MuscleGroup, equipment: Equipment, convention: LoadConvention): String {
        val item = Exercise(name = checkedName(name), muscle = muscle, equipment = equipment, convention = convention, custom = true)
        saveExercise(item)
        return item.id
    }

    /** Returning the existing active id makes double taps and process recreation idempotent. */
    suspend fun startEmpty(name: String = "Workout", defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS, now: Long = System.currentTimeMillis()): String = db.withTransaction {
        require(defaultRestSeconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        dao.activeWorkout()?.id ?: Workout(name = checkedName(name), startedAt = now).also { dao.insertWorkout(it) }.id
    }

    suspend fun startRoutine(routineId: String, defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS, now: Long = System.currentTimeMillis()): String = db.withTransaction {
        dao.activeWorkout()?.let { return@withTransaction it.id }
        require(defaultRestSeconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        val routine = requireNotNull(dao.routine(routineId)) { "Routine not found." }
        val workout = Workout(name = routine.name, startedAt = now, sourceRoutineId = routine.id)
        dao.insertWorkout(workout)
        dao.routineItems(routineId).forEach { plan ->
            val exercise = requireNotNull(dao.exercise(plan.exerciseId)) { "Exercise not found." }
            val entry = snapshotExercise(exercise, workout.id, plan.position, (plan.restSeconds ?: RestDuration.DEFAULT_SECONDS).coerceAtMost(RestDuration.MAX_SECONDS))
            dao.insertWorkoutExercise(entry)
            ensurePlannedSets(plan).forEach { row ->
                dao.insertSet(WorkoutSet(workoutExerciseId = entry.id, position = row.position,
                    reps = row.targetReps,
                    weightKg = row.targetWeightKg ?: 0.0,
                    assistance = exercise.convention == LoadConvention.ASSISTANCE))
            }
        }
        workout.id
    }

    suspend fun renameWorkout(id: String, name: String) = db.withTransaction {
        val item = requireNotNull(dao.workout(id)) { "Workout not found." }
        dao.updateWorkout(item.copy(name = checkedName(name)))
    }

    suspend fun addWorkoutExercise(workoutId: String, exerciseId: String, defaultRestSeconds: Int = RestDuration.DEFAULT_SECONDS): String = db.withTransaction {
        requireNotNull(dao.workout(workoutId)) { "Workout not found." }
        require(defaultRestSeconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        val entry = snapshotExercise(exercise, workoutId, dao.workoutItems(workoutId).size, defaultRestSeconds)
        dao.insertWorkoutExercise(entry)
        repeat(2) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = entry.id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
        entry.id
    }

    suspend fun replaceWorkoutExercise(id: String, exerciseId: String) = db.withTransaction {
        val old = requireNotNull(dao.workoutExercise(id)) { "Workout exercise not found." }
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        dao.updateWorkoutExercise(snapshotExercise(exercise, old.workoutId, old.position, old.restSeconds).copy(id = old.id))
        dao.deleteExerciseSets(id)
        repeat(2) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
    }

    suspend fun updateWorkoutExercise(item: WorkoutExercise) = db.withTransaction {
        val existing = requireNotNull(dao.workoutExercise(item.id)) { "Workout exercise not found." }
        require(existing.workoutId == item.workoutId && existing.exerciseId == item.exerciseId) { "Exercise identity cannot change." }
        require(item.restSeconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        dao.updateWorkoutExercise(item.copy(name = checkedName(item.name)))
    }

    suspend fun updateWorkoutExerciseRest(id: String, seconds: Int) = db.withTransaction {
        require(seconds in 0..RestDuration.MAX_SECONDS) { "Rest must be between 0:00 and 5:00." }
        val item = requireNotNull(dao.workoutExercise(id)) { "Workout exercise not found." }
        dao.updateWorkoutExercise(item.copy(restSeconds = seconds))
    }

    suspend fun removeWorkoutExercise(id: String) = db.withTransaction {
        val item = dao.workoutExercise(id) ?: return@withTransaction
        dao.deleteWorkoutExercise(id)
        dao.workoutItems(item.workoutId).forEachIndexed { index, entry -> dao.updateWorkoutExercise(entry.copy(position = index)) }
    }

    suspend fun moveWorkoutExercise(id: String, direction: Int) = db.withTransaction {
        val entry = requireNotNull(dao.workoutExercise(id)) { "Workout exercise not found." }
        val items = dao.workoutItems(entry.workoutId).toMutableList()
        move(items, id, direction) { it.id }
        items.forEachIndexed { index, item -> dao.updateWorkoutExercise(item.copy(position = index)) }
    }

    suspend fun reorderWorkoutExercises(workoutId: String, ids: List<String>) = db.withTransaction {
        val items = dao.workoutItems(workoutId)
        require(ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size) { "Invalid exercise order." }
        ids.forEachIndexed { index, id -> dao.updateWorkoutExercise(items.first { it.id == id }.copy(position = index)) }
    }

    suspend fun addSet(workoutExerciseId: String): String = db.withTransaction {
        val exercise = requireNotNull(dao.workoutExercise(workoutExerciseId)) { "Workout exercise not found." }
        val sets = dao.exerciseSets(workoutExerciseId)
        require(sets.size < 100) { "A maximum of 100 sets is supported per exercise." }
        val last = sets.lastOrNull()
        val item = WorkoutSet(workoutExerciseId = workoutExerciseId, position = sets.size,
            weightKg = last?.weightKg ?: 0.0,
            assistance = last?.assistance ?: (exercise.convention == LoadConvention.ASSISTANCE))
        dao.insertSet(item)
        item.id
    }

    private fun validateSet(item: WorkoutSet) {
        require(item.reps in 0..9999) { "Reps must be between 0 and 9999." }
        require(item.weightKg.isFinite() && item.weightKg >= 0 && item.weightKg <= 100000) { "Enter a valid non-negative weight." }
        require(!item.completed || item.reps > 0) { "Enter at least 1 rep before completing a set." }
    }

    suspend fun updateSet(item: WorkoutSet) = db.withTransaction {
        val existing = requireNotNull(dao.set(item.id)) { "Set not found." }
        require(existing.workoutExerciseId == item.workoutExerciseId) { "Cannot move a set to another exercise." }
        validateSet(item)
        dao.updateSet(item)
    }

    suspend fun setCompleted(id: String, completed: Boolean): WorkoutSet = db.withTransaction {
        val item = requireNotNull(dao.set(id)) { "Set not found." }.copy(completed = completed)
        validateSet(item)
        dao.updateSet(item)
        item
    }

    suspend fun removeSet(id: String) = db.withTransaction {
        val item = dao.set(id) ?: return@withTransaction
        dao.deleteSet(id)
        dao.exerciseSets(item.workoutExerciseId).forEachIndexed { index, entry -> dao.updateSet(entry.copy(position = index)) }
    }

    /** All child mutations are already durable; final status/end time changes atomically once. */
    suspend fun finishWorkout(id: String, now: Long = System.currentTimeMillis()): Boolean = db.withTransaction {
        val workout = requireNotNull(dao.workout(id)) { "Workout not found." }
        if (workout.status == WorkoutStatus.FINISHED) return@withTransaction false
        val sets = dao.workoutItems(id).flatMap { dao.exerciseSets(it.id) }
        require(sets.any { it.completed }) { "Complete at least one set before finishing." }
        sets.forEach(::validateSet)
        dao.updateWorkout(workout.copy(status = WorkoutStatus.FINISHED, endedAt = now.coerceAtLeast(workout.startedAt)))
        synchronizeRoutineTargets()
        true
    }

    suspend fun discardWorkout(id: String) = db.withTransaction {
        val workout = dao.workout(id) ?: return@withTransaction
        require(workout.status == WorkoutStatus.ACTIVE) { "Only active workouts can be discarded." }
        dao.deleteWorkout(id)
    }

    suspend fun deleteWorkout(id: String) = db.withTransaction { dao.deleteWorkout(id) }

    private fun snapshotExercise(exercise: Exercise, workoutId: String, position: Int, restSeconds: Int) = WorkoutExercise(
        workoutId = workoutId, exerciseId = exercise.id, name = exercise.name, muscle = exercise.muscle,
        equipment = exercise.equipment, convention = exercise.convention, position = position,
        restSeconds = restSeconds.coerceIn(0, RestDuration.MAX_SECONDS)
    )

    private fun <T> move(items: MutableList<T>, id: String, direction: Int, identity: (T) -> String) {
        val index = items.indexOfFirst { identity(it) == id }
        require(index >= 0) { "Item not found." }
        val destination = (index + direction.coerceIn(-1, 1)).coerceIn(0, items.lastIndex)
        if (index != destination) items.add(destination, items.removeAt(index))
    }
}
