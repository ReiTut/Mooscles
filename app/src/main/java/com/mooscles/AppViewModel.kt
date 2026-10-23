package com.mooscles

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mooscles.data.*
import com.mooscles.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withLock

class AppViewModel(application: Application) : AndroidViewModel(application), MoosclesActions {
    private val app = application as MoosclesApplication
    private val repo = app.repository
    private val error = MutableStateFlow<String?>(null)
    private val initialized = MutableStateFlow(false)
    private val workoutPreparing = MutableStateFlow(false)
    private val discardedWorkoutId = MutableStateFlow<String?>(null)
    private val settingsRequest = MutableStateFlow(0L)
    private val installLaunches = Channel<Intent>(Channel.BUFFERED)
    val installIntents = installLaunches.receiveAsFlow()
    fun openUpdateSettings() { settingsRequest.value += 1 }
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val source = repo.data.map<AppData, AppData?> { it }
        .catch { error.value = "Unable to open local data: ${it.message}"; emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val presenter = WorkoutPresenter()
    private val presentation = combine(source, app.preferences.flow) { data, prefs ->
        if (data == null) UiState() else presenter.present(data, prefs)
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    private val problems = combine(error, app.preferences.error, app.timer.error) { action, prefs, timer -> action ?: prefs ?: timer }
    private val baseUi = combine(presentation, app.timer.ui, problems, initialized, app.updates.ui) { base, timer, problem, ready, updates ->
        base.copy(timer = timer, error = problem, loading = base.loading || !ready, updates = updates)
    }.catch { emit(UiState(loading = false, error = "Unable to read local preferences: ${it.message}")) }
    val ui = combine(baseUi, settingsRequest, workoutPreparing, discardedWorkoutId) { base, request, preparing, discarded ->
        base.copy(updateSettingsRequest = request, workoutPreparing = preparing, discardedWorkoutId = discarded)
    }
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
    override fun reorderRoutineExercises(routineId: String, orderedIds: List<String>) = act { repo.reorderRoutineExercises(routineId, orderedIds) }
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
    override fun startWorkout(routineId: String?) {
        workoutPreparing.value = true
        act {
            try {
                val id = app.trainingGate.withLock {
                    app.updates.assertTrainingMayStart()
                    if (repo.snapshot().activeWorkout == null) app.timer.clear()
                    if (routineId == null) repo.startEmpty(defaultRestSeconds = ui.value.preferences.defaultRestSeconds)
                    else repo.startRoutine(routineId, ui.value.preferences.defaultRestSeconds)
                }
                // Both new and restored workouts become interactive only after every card
                // (including its previous performance and records) is prepared off-main.
                withTimeout(30_000) { presentation.first { !it.loading && it.active?.id == id } }
            } finally { workoutPreparing.value = false }
        }
    }
    override fun renameWorkout(id: String, name: String) = act { repo.renameWorkout(id, name) }
    override fun addWorkoutExercise(workoutId: String, exerciseId: String) = act { repo.addWorkoutExercise(workoutId, exerciseId, ui.value.preferences.defaultRestSeconds) }
    override fun replaceWorkoutExercise(entryId: String, exerciseId: String) = act { repo.replaceWorkoutExercise(entryId, exerciseId) }
    override fun removeWorkoutExercise(entryId: String) = act { repo.removeWorkoutExercise(entryId) }
    override fun moveWorkoutExercise(entryId: String, direction: Int) = act { repo.moveWorkoutExercise(entryId, direction) }
    override fun reorderWorkoutExercises(workoutId: String, orderedIds: List<String>) = act { repo.reorderWorkoutExercises(workoutId, orderedIds) }
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
        if (completed && !item.completed && workout.status == WorkoutStatus.ACTIVE && ui.value.preferences.autoRest) app.timer.start(entry.name, entry.restSeconds, entry.id)
        }
    }
    override fun finishWorkout() = act {
        val active = repo.snapshot().activeWorkout ?: return@act
        if (repo.finishWorkout(active.id)) app.timer.clear()
        app.updates.onTrainingChanged()
    }
    override fun discardWorkout() = act {
        app.trainingGate.withLock {
            val id = repo.snapshot().activeWorkout?.id ?: return@withLock
            repo.discardWorkout(id)
            app.timer.clearForSessionEnd()
            withTimeout(30_000) { presentation.first { !it.loading && it.active?.id != id } }
            discardedWorkoutId.value = id
        }
        app.updates.onTrainingChanged()
    }
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

}
