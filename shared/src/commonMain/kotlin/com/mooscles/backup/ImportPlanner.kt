package com.mooscles.backup

import com.mooscles.data.AppData
import com.mooscles.data.EditRules
import com.mooscles.data.RoutineFolder
import com.mooscles.data.RoutineTargetCheckpoint
import com.mooscles.data.WorkoutStatus

data class LocalState(val data: AppData, val checkpoints: List<RoutineTargetCheckpoint>)

data class ImportSummary(
    val workoutsAdded: Int,
    val workoutsKept: Int,
    val routinesAdded: Int,
    val routinesKept: Int,
    val customExercisesAdded: Int,
    val restoresPreferences: Boolean,
    /** True when the plan inserts no row of any kind and applies no preferences, so importing changes nothing. */
    val nothingToAdd: Boolean,
)

/** [entities] holds only what will be inserted; [preferences] is set only when they will be applied. */
data class ImportPlan(val entities: BackupEntities, val preferences: BackupPreferences?, val summary: ImportSummary)

/**
 * Decides what an import adds. The import only ever adds: a workout or routine whose id is already on
 * the phone is skipped with all of its children, and exercises, folders and checkpoints are skipped one
 * by one. Only what will be inserted is validated, and references resolve against inserted ∪ local.
 */
object ImportPlanner {
    fun plan(file: BackupFile, local: LocalState): ImportPlan {
        // Preferences are checked even when they will not be applied: an invalid file is refused whole.
        validatePreferences(file.preferences)
        val rows = file.data
        requireUniqueIds(rows)
        if (rows.workouts.count { it.status == WorkoutStatus.ACTIVE.name } > 1) invalid("the backup has more than one active workout")

        val phone = local.data
        val localExercises = phone.exercises.idSet { it.id }
        val localFolders = phone.routineFolders.idSet { it.id }
        val localRoutines = phone.routines.idSet { it.id }
        val localRoutineItems = phone.routineExercises.idSet { it.id }
        val localRoutineSets = phone.routineSets.idSet { it.id }
        val localWorkouts = phone.workouts.idSet { it.id }
        val localWorkoutItems = phone.workoutExercises.idSet { it.id }
        val localWorkoutSets = phone.sets.idSet { it.id }
        val localCheckpoints = local.checkpoints.idSet { checkpointKey(it.exerciseId, it.convention.name) }

        // Skipping happens on the rows, before conversion, so a skipped record is never validated.
        val routines = rows.routines.filter { it.id !in localRoutines }
        val routineItems = children(rows.routineExercises, "routine item", "routine", { it.id }, { it.routineId },
            inserted = routines.idSet { it.id }, kept = localRoutines, local = localRoutineItems)
        val routineSets = children(rows.routineSets, "planned set", "routine item", { it.id }, { it.routineExerciseId },
            inserted = routineItems.idSet { it.id }, kept = localRoutineItems + rows.routineExercises.idSet { it.id },
            local = localRoutineSets)
        val workouts = rows.workouts.filter { it.id !in localWorkouts }
        val workoutItems = children(rows.workoutExercises, "workout exercise", "workout", { it.id }, { it.workoutId },
            inserted = workouts.idSet { it.id }, kept = localWorkouts, local = localWorkoutItems)
        val workoutSets = children(rows.workoutSets, "workout set", "workout exercise", { it.id }, { it.workoutExerciseId },
            inserted = workoutItems.idSet { it.id }, kept = localWorkoutItems + rows.workoutExercises.idSet { it.id },
            local = localWorkoutSets)

        val entities = BackupData(
            exercises = rows.exercises.filter { it.id !in localExercises },
            routineFolders = rows.routineFolders.filter { it.id !in localFolders },
            routines = routines,
            routineExercises = routineItems,
            routineSets = routineSets,
            workouts = workouts,
            workoutExercises = workoutItems,
            workoutSets = workoutSets,
            routineTargetCheckpoints = rows.routineTargetCheckpoints.filter { checkpointKey(it.exerciseId, it.convention) !in localCheckpoints },
        ).toEntities()

        validate(entities, knownExercises = localExercises + entities.exercises.idSet { it.id })

        val folderIds = localFolders + entities.routineFolders.idSet { it.id }
        val folders = appendAfterLocal(entities.routineFolders, phone.routineFolders.maxOfOrNull { it.position }, { it.position }) {
            item, position -> item.copy(position = position)
        }
        // Routine positions are global across folders on the phone (unique, 0..N-1), so inserted routines
        // continue after the largest local position whatever folder they land in.
        val placedRoutines = appendAfterLocal(
            entities.routines.map { if (it.folderId in folderIds) it else it.copy(folderId = RoutineFolder.DEFAULT_ID) },
            phone.routines.maxOfOrNull { it.position }, { it.position },
        ) { item, position -> item.copy(position = position) }

        val restoresPreferences = phone.workouts.isEmpty() && phone.routines.isEmpty()
        val preferences = file.preferences.takeIf { restoresPreferences }?.let {
            if (it.selectedRoutineFolderId in folderIds) it else it.copy(selectedRoutineFolderId = RoutineFolder.DEFAULT_ID)
        }

        val nothingToAdd = preferences == null && with(entities) {
            exercises.isEmpty() && routineFolders.isEmpty() && routines.isEmpty() && routineExercises.isEmpty() &&
                routineSets.isEmpty() && workouts.isEmpty() && workoutExercises.isEmpty() && workoutSets.isEmpty() &&
                checkpoints.isEmpty()
        }

        return ImportPlan(
            entities = entities.copy(routineFolders = folders, routines = placedRoutines),
            preferences = preferences,
            summary = ImportSummary(
                workoutsAdded = entities.workouts.size,
                workoutsKept = rows.workouts.size - entities.workouts.size,
                routinesAdded = entities.routines.size,
                routinesKept = rows.routines.size - entities.routines.size,
                customExercisesAdded = entities.exercises.count { it.custom },
                restoresPreferences = restoresPreferences,
                nothingToAdd = nothingToAdd,
            ),
        )
    }

    private fun validatePreferences(preferences: BackupPreferences) {
        if (!PreferenceRules.validTheme(preferences.theme)) {
            invalid("preference theme \"${preferences.theme}\" is not one of ${PreferenceRules.THEMES.joinToString()}")
        }
        if (!PreferenceRules.validRest(preferences.defaultRestSeconds)) {
            invalid("preference defaultRestSeconds ${preferences.defaultRestSeconds} is outside ${PreferenceRules.REST_SECONDS}")
        }
    }

    private fun requireUniqueIds(rows: BackupData) {
        unique(rows.exercises, "exercise") { it.id }
        unique(rows.routineFolders, "folder") { it.id }
        unique(rows.routines, "routine") { it.id }
        unique(rows.routineExercises, "routine item") { it.id }
        unique(rows.routineSets, "planned set") { it.id }
        unique(rows.workouts, "workout") { it.id }
        unique(rows.workoutExercises, "workout exercise") { it.id }
        unique(rows.workoutSets, "workout set") { it.id }
        unique(rows.routineTargetCheckpoints, "target checkpoint") { checkpointKey(it.exerciseId, it.convention) }
    }

    private fun <T> unique(items: List<T>, kind: String, id: (T) -> String) {
        val seen = HashSet<String>()
        items.forEach { if (!seen.add(id(it))) invalid("duplicate $kind id \"${id(it)}\"") }
    }

    /**
     * Keeps the children whose parent will be inserted. A child of a parent that is kept (already on the
     * phone, or a file child of such a parent) belongs to an existing aggregate and is dropped, so an
     * import never adds rows to a workout or routine the phone already has.
     */
    private fun <T> children(
        items: List<T>, kind: String, parentKind: String, id: (T) -> String, parentId: (T) -> String,
        inserted: Set<String>, kept: Set<String>, local: Set<String>,
    ): List<T> = items.filter { item ->
        when (parentId(item)) {
            in inserted -> {
                if (id(item) in local) invalid("$kind \"${id(item)}\" is already on this phone in another $parentKind")
                true
            }
            in kept -> false
            else -> invalid("$kind \"${id(item)}\" references missing $parentKind \"${parentId(item)}\"")
        }
    }

    private fun validate(entities: BackupEntities, knownExercises: Set<String>) {
        entities.exercises.forEach {
            val record = "exercise \"${it.id}\""
            structural(record) { id(it.id); name(it.name) }
            if (it.custom) edit(record) { EditRules.name(it.name) }
        }
        entities.routineFolders.forEach {
            val record = "folder \"${it.id}\""
            structural(record) { id(it.id); count(it.position) }
            edit(record) { EditRules.name(it.name) }
        }
        entities.routines.forEach {
            val record = "routine \"${it.id}\""
            structural(record) { id(it.id); count(it.position) }
            edit(record) { EditRules.name(it.name) }
        }
        val plannedSetsByItem = entities.routineSets.groupingBy { it.routineExerciseId }.eachCount()
        entities.routineExercises.forEach {
            val record = "routine item \"${it.id}\""
            if (it.exerciseId !in knownExercises) invalid("$record references missing exercise \"${it.exerciseId}\"")
            structural(record) {
                id(it.id); count(it.position); count(it.plannedSets); count(it.targetReps)
                weight(it.targetWeightKg); it.restSeconds?.let(::count)
            }
            edit(record) {
                EditRules.plannedSets(it.plannedSets)
                EditRules.target(it.targetReps, it.targetWeightKg)
                EditRules.rest(it.restSeconds)
                EditRules.routineSetCount(plannedSetsByItem[it.id] ?: 0)
            }
        }
        entities.routineSets.forEach {
            val record = "planned set \"${it.id}\""
            structural(record) { id(it.id); count(it.position); count(it.targetReps); weight(it.targetWeightKg) }
            edit(record) { EditRules.target(it.targetReps, it.targetWeightKg) }
        }

        val activeWorkouts = HashSet<String>()
        entities.workouts.forEach {
            val record = "workout \"${it.id}\""
            structural(record) {
                id(it.id); name(it.name)
                require(it.startedAt > 0) { "start time must be positive" }
                require(it.endedAt == null || it.endedAt >= it.startedAt) { "end time must not be before start time" }
            }
            // Finished history keeps values older versions allowed; only the active workout is still edited.
            if (it.status == WorkoutStatus.ACTIVE) {
                activeWorkouts += it.id
                edit(record) { EditRules.name(it.name) }
            }
        }
        val activeItems = HashSet<String>()
        val setsByItem = entities.workoutSets.groupingBy { it.workoutExerciseId }.eachCount()
        entities.workoutExercises.forEach {
            val record = "workout exercise \"${it.id}\""
            structural(record) { id(it.id); id(it.exerciseId); name(it.name); count(it.position); count(it.restSeconds) }
            if (it.workoutId in activeWorkouts) {
                activeItems += it.id
                edit(record) {
                    EditRules.name(it.name)
                    EditRules.rest(it.restSeconds)
                    EditRules.workoutSetCount(setsByItem[it.id] ?: 0)
                }
            }
        }
        entities.workoutSets.forEach {
            val record = "workout set \"${it.id}\""
            structural(record) { id(it.id); count(it.position); count(it.reps); weight(it.weightKg) }
            if (it.workoutExerciseId in activeItems) edit(record) { EditRules.workoutSet(it) }
        }

        entities.checkpoints.forEach {
            val record = "target checkpoint \"${checkpointKey(it.exerciseId, it.convention.name)}\""
            structural(record) { id(it.exerciseId); count(it.reps); weight(it.weightKg) }
        }
    }

    private object Structure {
        fun id(value: String) = require(value.isNotBlank()) { "id is blank" }
        fun name(value: String) = require(value.isNotBlank()) { "name is blank" }
        fun count(value: Int) = require(value >= 0) { "value $value is negative" }
        fun weight(value: Double?) = require(value == null || (value.isFinite() && value >= 0)) { "weight $value is negative or not finite" }
    }

    private inline fun structural(record: String, checks: Structure.() -> Unit) = edit(record) { Structure.checks() }

    /** Rule failures become [BackupException.Invalid] naming the offending record. */
    private inline fun edit(record: String, checks: () -> Unit) {
        try {
            checks()
        } catch (error: IllegalArgumentException) {
            invalid("$record: ${error.message}")
        }
    }

    /**
     * Inserted items go after [localMax], contiguous, in the file's relative order (position, then list
     * order). With nothing local ([localMax] null) the file's positions are kept, so a round trip into an
     * empty phone stays exact. The returned list keeps the file's list order.
     */
    private fun <T> appendAfterLocal(items: List<T>, localMax: Int?, position: (T) -> Int, place: (T, Int) -> T): List<T> {
        if (localMax == null) return items
        val placed = items.toMutableList()
        items.indices.sortedWith(compareBy({ position(items[it]) }, { it }))
            .forEachIndexed { rank, index -> placed[index] = place(items[index], localMax + 1 + rank) }
        return placed
    }

    private fun checkpointKey(exerciseId: String, convention: String) = "$exerciseId/$convention"

    private inline fun <T> List<T>.idSet(id: (T) -> String): Set<String> = mapTo(HashSet(size)) { id(it) }

    private fun invalid(detail: String): Nothing = throw BackupException.Invalid(detail)
}
