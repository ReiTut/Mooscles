package com.mooscles

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mooscles.data.*
import com.mooscles.domain.WorkoutCalculations
import com.mooscles.domain.WeightUnits
import com.mooscles.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.Locale

class AppViewModel(application: Application) : AndroidViewModel(application), MoosclesActions {
    private val app = application as MoosclesApplication
    private val repo = app.repository
    private val error = MutableStateFlow<String?>(null)
    private val initialized = MutableStateFlow(false)
    private val settingsRequest = MutableStateFlow(0L)
    private val installLaunches = Channel<Intent>(Channel.BUFFERED)
    val installIntents = installLaunches.receiveAsFlow()
    fun openUpdateSettings() { settingsRequest.value += 1 }
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val source = repo.data.catch { error.value = "Unable to open local data: ${it.message}"; emit(AppData()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())
    private val presentation = combine(source, app.preferences.flow) { data, prefs ->
        present(data, prefs, TimerUi(), null, false)
    }
    private val problems = combine(error, app.preferences.error, app.timer.error) { action, prefs, timer -> action ?: prefs ?: timer }
    private val baseUi = combine(presentation, app.timer.ui, problems, initialized, app.updates.ui) { base, timer, problem, ready, updates ->
        base.copy(timer = timer, error = problem, loading = !ready, updates = updates)
    }.catch { emit(UiState(loading = false, error = "Unable to read local preferences: ${it.message}")) }
    val ui = combine(baseUi, settingsRequest) { base, request -> base.copy(updateSettingsRequest = request) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            app.timer.ui.map { it.running || it.paused }.distinctUntilChanged().collect { busy ->
                if (!busy) runCatching { app.updates.onTrainingChanged() }
            }
        }
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
    override fun addRoutineSet(entryId: String) = act { repo.addRoutineSet(entryId) }
    override fun removeRoutineSet(setId: String) = act { repo.removeRoutineSet(setId) }
    override fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) = act { repo.updateRoutineSet(setId, reps, weightKg) }
    override fun inheritRoutineSet(setId: String) = act { repo.inheritRoutineSet(setId) }
    override fun updateRoutineExerciseRest(entryId: String, seconds: Int) = act { repo.updateRoutineExerciseRest(entryId, seconds) }
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
        app.trainingGate.withLock {
            app.updates.assertTrainingMayStart()
            if (repo.snapshot().activeWorkout == null) app.timer.clear()
            if (routineId == null) repo.startEmpty(defaultRestSeconds = ui.value.preferences.defaultRestSeconds)
            else repo.startRoutine(routineId, ui.value.preferences.defaultRestSeconds)
        }
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
        app.trainingGate.withLock {
        app.updates.assertTrainingMayStart()
        val data = repo.snapshot()
        val item = data.sets.first { it.id == setId }
        repo.updateSet(item.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance))
        val entry = data.workoutExercises.first { it.id == item.workoutExerciseId }
        val workout = data.workouts.first { it.id == entry.workoutId }
        if (completed && !item.completed && workout.status == WorkoutStatus.ACTIVE && ui.value.preferences.autoRest) app.timer.start(entry.name, entry.restSeconds)
        }
    }
    override fun finishWorkout() = act {
        val active = repo.snapshot().activeWorkout ?: return@act
        if (repo.finishWorkout(active.id)) app.timer.clear()
        app.updates.onTrainingChanged()
    }
    override fun discardWorkout() = act { repo.snapshot().activeWorkout?.let { repo.discardWorkout(it.id) }; app.timer.clear(); app.updates.onTrainingChanged() }
    override fun deleteWorkout(id: String) = act { repo.deleteWorkout(id) }
    override fun setPounds(pounds: Boolean) = act { app.preferences.pounds(pounds) }
    override fun setTheme(theme: String) = act { app.preferences.theme(theme.lowercase()) }
    override fun setAutoRest(enabled: Boolean) = act { app.preferences.autoRest(enabled) }
    override fun setDefaultRest(seconds: Int) = act { app.preferences.defaultRest(seconds) }
    override fun startTimer(exerciseName: String, seconds: Int) = act { app.trainingGate.withLock { app.updates.assertTrainingMayStart(); app.timer.start(exerciseName, seconds) } }
    override fun pauseTimer() = act { app.timer.pause() }
    override fun resumeTimer() = act { app.trainingGate.withLock { app.updates.assertTrainingMayStart(); app.timer.resume() } }
    override fun resetTimer() = act { app.trainingGate.withLock { app.updates.assertTrainingMayStart(); app.timer.reset() } }
    override fun skipTimer() = act { app.timer.skip(); app.updates.onTrainingChanged() }
    override fun adjustTimer(seconds: Int) = act { app.trainingGate.withLock { app.updates.assertTrainingMayStart(); app.timer.adjust(seconds) } }
    override fun checkForUpdates() = act { app.updates.requestCheck(true) }
    override fun setAutomaticUpdates(enabled: Boolean) = act { app.updates.setAutomatic(enabled) }
    override fun setUpdateWifiOnly(enabled: Boolean) = act { app.updates.setWifiOnly(enabled) }
    override fun setIncludePrereleases(enabled: Boolean) = act { app.updates.setPrereleases(enabled) }
    override fun downloadUpdate() = act { app.updates.enqueueDownload(manual = true) }
    override fun cancelUpdate() = act { app.updates.cancelDownload() }
    override fun installUpdate() = act { app.updates.requestInstall()?.let { installLaunches.send(it) } }

    private fun Exercise.toUi() = ExerciseUi(id, name, muscle.label, equipment.label, convention.label, custom)
    private fun present(data: AppData, prefs: PreferencesUi, timer: TimerUi, error: String?, loading: Boolean): UiState {
        val exercises = data.exercises.associateBy { it.id }
        fun snapshot(e: WorkoutExercise) = ExerciseUi(e.exerciseId, e.name, e.muscle.label, e.equipment.label, e.convention.label, exercises[e.exerciseId]?.custom ?: false)
        fun workout(w: Workout): WorkoutUi {
            val prs = WorkoutCalculations.prsForWorkout(data, w.id)
            val repsPrs = WorkoutCalculations.repsPrsForWorkout(data, w.id)
            val records = prs.map { p ->
                val e = data.workoutItems(w.id).first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.newWeightKg, p.reps, p.previousWeightKg, w.id, w.endedAt ?: w.startedAt)
            } + repsPrs.map { p ->
                val e = data.workoutItems(w.id).first { it.exerciseId == p.exerciseId && it.convention == p.convention }
                RecordUi(snapshot(e), p.weightKg, p.newReps, workoutId = w.id, date = w.endedAt ?: w.startedAt,
                    kind = RecordKind.REPS, previousReps = p.previousReps, assistance = p.assistance)
            }
            val entries = data.workoutItems(w.id).map { e ->
                val previous = WorkoutCalculations.performanceHistory(data, e.exerciseId, e.convention)
                    .firstOrNull { it.workout.id != w.id && it.workout.startedAt < w.startedAt }
                val pr = prs.firstOrNull { it.exerciseId == e.exerciseId && it.convention == e.convention }
                WorkoutExerciseUi(e.id, snapshot(e), data.exerciseSets(e.id).map { SetUi(it.id, it.reps, it.weightKg, it.completed, it.warmup, it.assistance) },
                    e.restSeconds, previous?.sets?.joinToString(" · ") {
                        val load = if (it.weightKg == 0.0 && !it.assistance && e.convention != LoadConvention.ASSISTANCE) "Bodyweight"
                        else "${if (it.assistance || e.convention == LoadConvention.ASSISTANCE) "Assistance " else ""}${String.format(Locale.US, "%.2f", WeightUnits.fromKg(it.weightKg, prefs.pounds)).trimEnd('0').trimEnd('.')} ${if (prefs.pounds) "lb" else "kg"}"
                        "$load × ${it.reps}"
                    }, pr?.newWeightKg, pr?.firstRecord ?: false,
                    records.filter { record ->
                        record.exercise.id == e.exerciseId && record.exercise.convention == e.convention.label &&
                            data.exerciseSets(e.id).any { set ->
                                val sameLoad = kotlin.math.abs(set.weightKg - record.weightKg) <=
                                    maxOf(Math.ulp(set.weightKg), Math.ulp(record.weightKg)) * 4
                                val assisted = set.assistance || e.convention == LoadConvention.ASSISTANCE
                                WorkoutCalculations.validCompleted(set) && !set.warmup && sameLoad &&
                                    if (record.kind == RecordKind.WEIGHT) !assisted
                                    else assisted == record.assistance && set.reps == record.reps
                            }
                    })
            }
            return WorkoutUi(w.id, w.name, w.startedAt, w.endedAt, entries, records, sourceRoutineId = w.sourceRoutineId)
        }
        val records = WorkoutCalculations.records(data).map { r ->
            val e = data.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
            RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps, workoutId = r.workoutId, date = r.achievedAt)
        } + WorkoutCalculations.repsRecords(data).map { r ->
            val e = data.workoutItems(r.workoutId).first { it.exerciseId == r.exerciseId && it.convention == r.convention }
            RecordUi(snapshot(e).copy(name = exercises[r.exerciseId]?.name ?: r.exerciseName), r.weightKg, r.reps,
                workoutId = r.workoutId, date = r.achievedAt, kind = RecordKind.REPS, assistance = r.assistance)
        }
        return UiState(loading, data.exercises.map { it.toUi() }, data.routines.sortedBy { it.position }.map { r ->
            RoutineUi(r.id, r.name, data.routineItems(r.id).mapNotNull { e -> exercises[e.exerciseId]?.let {
                val targets = data.plannedSets(e.id).map { row -> RoutineSetUi(row.id, row.targetReps, row.targetWeightKg, row.inheritPr) }
                PlannedExerciseUi(e.id, it.toUi(), targets.size, targets.firstOrNull()?.reps ?: 0,
                    targets.firstOrNull()?.weightKg, e.restSeconds ?: com.mooscles.domain.RestDuration.DEFAULT_SECONDS, targets)
            } })
        }, data.workouts.filter { it.status == WorkoutStatus.FINISHED }.sortedByDescending { it.startedAt }.map(::workout),
            data.activeWorkout?.let(::workout), records, timer, prefs, error)
    }
}
