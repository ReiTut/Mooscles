package com.mooscles.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Small atomic commands shared by active tracking, routine editing, and history editing. */
class WorkoutRepository(private val db: WorkoutDatabase) {
    private val dao = db.dao()

    val data: Flow<AppData> = db.invalidationTracker.createFlow(
        "exercises", "routines", "routine_exercises", "workouts", "workout_exercises", "workout_sets"
    ).map { snapshot() }

    suspend fun initialize() = db.withTransaction { dao.seedExercises(ExerciseCatalog.exercises) }

    suspend fun snapshot(): AppData = db.withTransaction {
        AppData(dao.exercises(), dao.routines(), dao.routineExercises(), dao.workouts(), dao.workoutExercises(), dao.sets())
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
        dao.routineItems(id).forEach { dao.insertRoutineExercise(it.copy(id = newId(), routineId = copy.id)) }
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
        val item = RoutineExercise(routineId = routineId, exerciseId = exerciseId, position = dao.routineItems(routineId).size)
        dao.insertRoutineExercise(item)
        item.id
    }

    suspend fun updateRoutineExercise(item: RoutineExercise) = db.withTransaction {
        val existing = requireNotNull(dao.routineExercise(item.id)) { "Routine exercise not found." }
        require(existing.routineId == item.routineId) { "Cannot move an exercise to another routine." }
        requireNotNull(dao.exercise(item.exerciseId)) { "Exercise not found." }
        require(item.plannedSets in 1..50) { "Planned sets must be between 1 and 50." }
        require(item.targetReps in 0..999) { "Target reps must be blank or between 1 and 999." }
        require(item.targetWeightKg == null || (item.targetWeightKg.isFinite() && item.targetWeightKg >= 0)) { "Enter a valid non-negative target weight." }
        require(item.restSeconds == null || item.restSeconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        dao.updateRoutineExercise(item)
    }

    suspend fun replaceRoutineExercise(id: String, exerciseId: String) = db.withTransaction {
        val item = requireNotNull(dao.routineExercise(id)) { "Routine exercise not found." }
        requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        dao.updateRoutineExercise(item.copy(exerciseId = exerciseId, targetWeightKg = null))
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
    suspend fun startEmpty(name: String = "Workout", defaultRestSeconds: Int = 90, now: Long = System.currentTimeMillis()): String = db.withTransaction {
        require(defaultRestSeconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        dao.activeWorkout()?.id ?: Workout(name = checkedName(name), startedAt = now).also { dao.insertWorkout(it) }.id
    }

    suspend fun startRoutine(routineId: String, defaultRestSeconds: Int = 90, now: Long = System.currentTimeMillis()): String = db.withTransaction {
        dao.activeWorkout()?.let { return@withTransaction it.id }
        require(defaultRestSeconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        val routine = requireNotNull(dao.routine(routineId)) { "Routine not found." }
        val workout = Workout(name = routine.name, startedAt = now)
        dao.insertWorkout(workout)
        dao.routineItems(routineId).forEach { plan ->
            val exercise = requireNotNull(dao.exercise(plan.exerciseId)) { "Exercise not found." }
            val entry = snapshotExercise(exercise, workout.id, plan.position, plan.restSeconds ?: defaultRestSeconds)
            dao.insertWorkoutExercise(entry)
            repeat(plan.plannedSets) { position ->
                dao.insertSet(WorkoutSet(workoutExerciseId = entry.id, position = position,
                    weightKg = plan.targetWeightKg ?: 0.0,
                    assistance = exercise.convention == LoadConvention.ASSISTANCE))
            }
        }
        workout.id
    }

    suspend fun renameWorkout(id: String, name: String) = db.withTransaction {
        val item = requireNotNull(dao.workout(id)) { "Workout not found." }
        dao.updateWorkout(item.copy(name = checkedName(name)))
    }

    suspend fun addWorkoutExercise(workoutId: String, exerciseId: String, defaultRestSeconds: Int = 90): String = db.withTransaction {
        requireNotNull(dao.workout(workoutId)) { "Workout not found." }
        require(defaultRestSeconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        val entry = snapshotExercise(exercise, workoutId, dao.workoutItems(workoutId).size, defaultRestSeconds)
        dao.insertWorkoutExercise(entry)
        repeat(3) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = entry.id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
        entry.id
    }

    suspend fun replaceWorkoutExercise(id: String, exerciseId: String) = db.withTransaction {
        val old = requireNotNull(dao.workoutExercise(id)) { "Workout exercise not found." }
        val exercise = requireNotNull(dao.exercise(exerciseId)) { "Exercise not found." }
        dao.updateWorkoutExercise(snapshotExercise(exercise, old.workoutId, old.position, old.restSeconds).copy(id = old.id))
        dao.deleteExerciseSets(id)
        repeat(3) { position -> dao.insertSet(WorkoutSet(workoutExerciseId = id, position = position,
            assistance = exercise.convention == LoadConvention.ASSISTANCE)) }
    }

    suspend fun updateWorkoutExercise(item: WorkoutExercise) = db.withTransaction {
        val existing = requireNotNull(dao.workoutExercise(item.id)) { "Workout exercise not found." }
        require(existing.workoutId == item.workoutId && existing.exerciseId == item.exerciseId) { "Exercise identity cannot change." }
        require(item.restSeconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
        dao.updateWorkoutExercise(item.copy(name = checkedName(item.name)))
    }

    suspend fun updateWorkoutExerciseRest(id: String, seconds: Int) = db.withTransaction {
        require(seconds in 0..3600) { "Rest must be between 0 and 3600 seconds." }
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
        equipment = exercise.equipment, convention = exercise.convention, position = position, restSeconds = restSeconds
    )

    private fun <T> move(items: MutableList<T>, id: String, direction: Int, identity: (T) -> String) {
        val index = items.indexOfFirst { identity(it) == id }
        require(index >= 0) { "Item not found." }
        val destination = (index + direction.coerceIn(-1, 1)).coerceIn(0, items.lastIndex)
        if (index != destination) items.add(destination, items.removeAt(index))
    }
}
