package com.mooscles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application), MoosclesActions {
    private val app = application as MoosclesApplication
    private val repo = app.repository
    private val error = MutableStateFlow<String?>(null)
    private val initialized = MutableStateFlow(false)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val source = repo.data.catch { error.value = "Unable to open local data: ${it.message}"; emit(AppData()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())
    private val presentation = combine(source, app.preferences.flow) { data, prefs ->
        present(data, prefs, TimerUi(), null, false)
    }
    private val problems = combine(error, app.preferences.error, app.timer.error) { action, prefs, timer -> action ?: prefs ?: timer }
    val ui = combine(presentation, app.timer.ui, problems, initialized) { base, timer, problem, ready ->
        base.copy(timer = timer, error = problem, loading = !ready)
    }.catch { emit(UiState(loading = false, error = "Unable to read local preferences: ${it.message}")) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            try { repo.initialize() } catch (e: Exception) { error.value = "Unable to initialize local data: ${e.message}" }
            initialized.value = true
            for (command in commands) try { command() } catch (e: Exception) { error.value = e.message ?: "Could not save this change. Please try again." }
        }
    }
    private fun act(block: suspend () -> Unit) { commands.trySend(block) }
    override fun clearError() { error.value = null; app.preferences.clearError(); app.timer.clearError() }
    override fun createRoutine(name: String) = act { repo.createRoutine(name) }
    override fun renameRoutine(id: String, name: String) = act { repo.renameRoutine(id, name) }
    override fun duplicateRoutine(id: String) = act { repo.duplicateRoutine(id) }
    override fun deleteRoutine(id: String) = act { repo.deleteRoutine(id) }
    override fun moveRoutine(id: String, direction: Int) = act { repo.moveRoutine(id, direction) }
    override fun addRoutineExercise(routineId: String, exerciseId: String) = act { repo.addRoutineExercise(routineId, exerciseId) }
    override fun replaceRoutineExercise(entryId: String, exerciseId: String) = act { repo.replaceRoutineExercise(entryId, exerciseId) }
    override fun removeRoutineExercise(entryId: String) = act { repo.removeRoutineExercise(entryId) }
    override fun moveRoutineExercise(entryId: String, direction: Int) = act { repo.moveRoutineExercise(entryId, direction) }
    override fun updateRoutineExercise(entryId: String, sets: Int, reps: Int, weightKg: Double?, restSeconds: Int) = act {
        val item = repo.snapshot().routineExercises.first { it.id == entryId }
        repo.updateRoutineExercise(item.copy(plannedSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds))
    }
    override fun saveCustomExercise(id: String?, name: String, muscle: String, equipment: String, convention: String) = act {
        val m = MuscleGroup.entries.firstOrNull { it.label == muscle || it.name == muscle } ?: MuscleGroup.FULL_BODY
        val e = Equipment.entries.firstOrNull { it.label == equipment || it.name == equipment } ?: Equipment.OTHER
        val c = LoadConvention.entries.firstOrNull { it.label == convention || it.name == convention } ?: LoadConvention.TOTAL_LOAD
        if (id == null) repo.createCustomExercise(name, m, e, c)
        else {
            val existing = repo.snapshot().exercises.first { it.id == id }
            repo.saveExercise(existing.copy(name = name, muscle = m, equipment = e, convention = c))
        }
    }
    override fun startWorkout(routineId: String?) = act {
        if (routineId == null) repo.startEmpty(defaultRestSeconds = ui.value.preferences.defaultRestSeconds)
        else repo.startRoutine(routineId, ui.value.preferences.defaultRestSeconds)
    }
    override fun renameWorkout(id: String, name: String) = act { repo.renameWorkout(id, name) }
    override fun addWorkoutExercise(workoutId: String, exerciseId: String) = act { repo.addWorkoutExercise(workoutId, exerciseId, ui.value.preferences.defaultRestSeconds) }
    override fun replaceWorkoutExercise(entryId: String, exerciseId: String) = act { repo.replaceWorkoutExercise(entryId, exerciseId) }
    override fun removeWorkoutExercise(entryId: String) = act { repo.removeWorkoutExercise(entryId) }
    override fun moveWorkoutExercise(entryId: String, direction: Int) = act { repo.moveWorkoutExercise(entryId, direction) }
    override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) = act { repo.updateWorkoutExerciseRest(entryId, seconds) }
    override fun addSet(entryId: String) = act { repo.addSet(entryId) }
    override fun removeSet(setId: String) = act { repo.removeSet(setId) }
    override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) = act {
        val data = repo.snapshot()
        val item = data.sets.first { it.id == setId }
        repo.updateSet(item.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance))
        val entry = data.workoutExercises.first { it.id == item.workoutExerciseId }
        val workout = data.workouts.first { it.id == entry.workoutId }
        if (completed && !item.completed && workout.status == WorkoutStatus.ACTIVE && ui.value.preferences.autoRest) app.timer.start(entry.name, entry.restSeconds)
    }
    override fun finishWorkout() = act {
        val active = repo.snapshot().activeWorkout ?: return@act
        if (repo.finishWorkout(active.id)) app.timer.skip()
    }
    override fun discardWorkout() = act { repo.snapshot().activeWorkout?.let { repo.discardWorkout(it.id) }; app.timer.skip() }
    override fun deleteWorkout(id: String) = act { repo.deleteWorkout(id) }
    override fun setPounds(pounds: Boolean) = act { app.preferences.pounds(pounds) }
    override fun setTheme(theme: String) = act { app.preferences.theme(theme.lowercase()) }
    override fun setAutoRest(enabled: Boolean) = act { app.preferences.autoRest(enabled) }
    override fun setDefaultRest(seconds: Int) = act { app.preferences.defaultRest(seconds) }
    override fun startTimer(exerciseName: String, seconds: Int) = act { app.timer.start(exerciseName, seconds) }
    override fun pauseTimer() = act { app.timer.pause() }
    override fun resumeTimer() = act { app.timer.resume() }
    override fun resetTimer() = act { app.timer.reset() }
    override fun skipTimer() = act { app.timer.skip() }
    override fun adjustTimer(seconds: Int) = act { app.timer.adjust(seconds) }

    private fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
    private fun present(data: AppData, prefs: PreferencesUi, timer: TimerUi, error: String?, loading: Boolean): UiState {
        val exercises = data.exercises.associateBy { it.id }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        fun workout(w: Workout): WorkoutUi {
            val prs = WorkoutCalculations.prsForWorkout(data, w.id)
            val entries = data.workoutItems(w.id).map { e ->
                val previous = WorkoutCalculations.performanceHistory(data, e.exerciseId, e.convention)
                    .firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                WorkoutExerciseUi(e.id, snapshot(e), data.exerciseSets(e.id).map { SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance) },
                    e.restSeconds, previous?.sets?.joinToString(" · ") { "${String.format(Locale.US, "%.2f", WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"} × ${it.reps}" },
                    pr?.newWeightKg, pr?.firstRecord ?: false)
            }
            val records = prs.map { p ->
                val e = data.workoutItems(w.id).first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.newWeightKg, p.reps, p.previousWeightKg, w.id, w.endedAt ?: w.startedAt)
            }
            return WorkoutUi(w.id, w.name, w.startedAt, w.endedAt, entries, records)
        }
        val records = WorkoutCalculations.records(data).map { r ->
            val e = data.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
            RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
        }
        return UiState(loading, data.exercises.map { it.toUi() }, data.routines.sortedBy { it.position }.map { r ->
            RoutineUi(r.id, r.name, data.routineItems(r.id).mapNotNull { e -> exercises[e.exerciseId]?.let {
                PlannedExerciseUi(e.id, it.toUi(), e.plannedSets, e.targetReps, e.targetWeightKg, e.restSeconds ?: prefs.defaultRestSeconds)
            } })
        }, data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedByDescending { it.startedAt }.map(::workout),
            data.activeWorkout?.let(::workout), records, timer, prefs, error)
    }
}
