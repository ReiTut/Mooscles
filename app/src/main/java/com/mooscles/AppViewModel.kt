package com.mooscles

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Trace
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mooscles.data.*
import com.mooscles.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class AppViewModel(application: Application) : AndroidViewModel(application), MoosclesActions {
    private val app = application as MoosclesApplication
    private val repo = app.repository
    private val error = MutableStateFlow<String?>(null)
    private val initialized = MutableStateFlow(false)
    private val startingWorkout = MutableStateFlow(false)
    private val discardingWorkout = MutableStateFlow(false)
    private val discardNavigationRequest = MutableStateFlow(0L)
    private val presenter = WorkoutPresentation()
    private val settingsRequest = MutableStateFlow(0L)
    private val installLaunches = Channel<Intent>(Channel.BUFFERED)
    val installIntents = installLaunches.receiveAsFlow()
    fun openUpdateSettings() { settingsRequest.value += 1 }
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val source = repo.data.catch { error.value = "Unable to open local data: ${it.message}"; emit(AppData()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppData())
    // Collected by contentUi in viewModelScope: prepare card details on the UI thread.
    private val presentation = combine(source, app.preferences.flow) { data, prefs -> data to prefs }
        .map { (data, prefs) ->
            Trace.beginSection("Mooscles:present")
            try { presenter.present(data, prefs) } finally { Trace.endSection() }
        }
    private val problems = combine(error, app.preferences.error, app.timer.error) { action, prefs, timer -> action ?: prefs ?: timer }
    private val baseUi = combine(presentation, problems, initialized, app.updates.ui, startingWorkout) { base, problem, ready, updates, starting ->
        base.copy(error = problem, loading = !ready, updates = updates, startingWorkout = starting)
    }.catch { emit(UiState(loading = false, error = "Unable to read local preferences: ${it.message}")) }
    /** Screens do not observe countdown ticks. Only the dock collects [timerUi]. */
    val contentUi = combine(baseUi, settingsRequest, discardingWorkout, discardNavigationRequest) { base, request, discarding, discarded ->
        base.copy(updateSettingsRequest = request, discardingWorkout = discarding, discardNavigationRequest = discarded)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    val timerUi = app.timer.ui
    // Retained for consumers needing a complete snapshot; the Activity uses contentUi instead.
    val ui = combine(contentUi, timerUi) { base, timer -> base.copy(timer = timer) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            app.timer.ui.map { it.running || it.paused }.distinctUntilChanged().collect { busy ->
                if (!busy) runCatching { app.updates.onTrainingChanged() }
            }
        }
        viewModelScope.launch {
            try { repo.initialize() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = "Unable to initialize local data: ${e.message}" }
            initialized.value = true
            for (command in commands) try { command() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Could not save this change. Please try again." }
        }
    }
    private fun act(block: suspend () -> Unit) { commands.trySend(block) }
    override fun clearError() { error.value = null; app.preferences.clearError(); app.timer.clearError() }
    override fun createRoutine(name: String) = act {
        val savedFolder = app.preferences.snapshot().selectedRoutineFolderId
        val folderId = repo.routineFolder(savedFolder)?.id ?: RoutineFolder.DEFAULT_ID
        repo.createRoutine(name, folderId)
    }
    override fun renameRoutine(id: String, name: String) = act { repo.renameRoutine(id, name) }
    override fun duplicateRoutine(id: String) = act { repo.duplicateRoutine(id) }
    override fun deleteRoutine(id: String) = act { repo.deleteRoutine(id) }
    override fun moveRoutine(id: String, direction: Int) = act { repo.moveRoutine(id, direction) }
    override fun createRoutineFolder(name: String) = act {
        val id = repo.createRoutineFolder(name)
        app.preferences.selectRoutineFolder(id)
    }
    override fun renameRoutineFolder(id: String, name: String) = act { repo.renameRoutineFolder(id, name) }
    override fun selectRoutineFolder(id: String) = act {
        requireNotNull(repo.routineFolder(id)) { "Folder unavailable." }
        app.preferences.selectRoutineFolder(id)
    }
    override fun moveRoutineToFolder(routineId: String, folderId: String) = act { repo.moveRoutineToFolder(routineId, folderId) }
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
        val item = requireNotNull(repo.routineExercise(entryId)) { "Routine exercise unavailable" }
        repo.updateRoutineExercise(item.copy(plannedSets = sets, targetReps = reps, targetWeightKg = weightKg, restSeconds = restSeconds))
    }
    override fun saveCustomExercise(id: String?, name: String, muscle: String, equipment: String, convention: String) = act {
        val m = MuscleGroup.entries.firstOrNull { it.label == muscle || it.name == muscle } ?: MuscleGroup.FULL_BODY
        val e = Equipment.entries.firstOrNull { it.label == equipment || it.name == equipment } ?: Equipment.OTHER
        val c = LoadConvention.entries.firstOrNull { it.label == convention || it.name == convention } ?: LoadConvention.TOTAL_LOAD
        if (id == null) repo.createCustomExercise(name, m, e, c)
        else {
            val existing = requireNotNull(repo.exercise(id)) { "Exercise unavailable" }
            repo.saveExercise(existing.copy(name = name, muscle = m, equipment = e, convention = c))
        }
    }
    override fun startWorkout(routineId: String?) {
        if (!startingWorkout.compareAndSet(false, true)) return
        error.value = null
        act {
            val traceCookie = System.identityHashCode(this)
            if (Build.VERSION.SDK_INT >= 29) Trace.beginAsyncSection("Mooscles:startSession", traceCookie)
            try {
                val id = app.trainingGate.withLock {
                    app.updates.assertTrainingMayStart()
                    if (repo.activeWorkout() == null) app.timer.clear()
                    val prefs = app.preferences.snapshot()
                    if (routineId == null) repo.startEmpty(defaultRestSeconds = prefs.defaultRestSeconds)
                    else repo.startRoutine(routineId, prefs.defaultRestSeconds)
                }
                // Keep loading visible until saved data reaches the editor, also on resume.
                try {
                    withTimeout(30_000) { contentUi.first { it.active?.id == id } }
                } catch (failure: TimeoutCancellationException) {
                    throw IllegalStateException("Your workout was saved, but the screen could not open. Return to Workout to resume it.", failure)
                }
            } finally {
                startingWorkout.value = false
                if (Build.VERSION.SDK_INT >= 29) Trace.endAsyncSection("Mooscles:startSession", traceCookie)
            }
        }
    }
    override fun renameWorkout(id: String, name: String) = act { repo.renameWorkout(id, name) }
    override fun addWorkoutExercise(workoutId: String, exerciseId: String) = act { repo.addWorkoutExercise(workoutId, exerciseId, app.preferences.snapshot().defaultRestSeconds) }
    override fun replaceWorkoutExercise(entryId: String, exerciseId: String) = act { repo.replaceWorkoutExercise(entryId, exerciseId) }
    override fun removeWorkoutExercise(entryId: String) = act { repo.removeWorkoutExercise(entryId) }
    override fun moveWorkoutExercise(entryId: String, direction: Int) = act { repo.moveWorkoutExercise(entryId, direction) }
    override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) = act { repo.updateWorkoutExerciseRest(entryId, seconds) }
    override fun addSet(entryId: String) = act { repo.addSet(entryId) }
    override fun removeSet(setId: String) = act { repo.removeSet(setId) }
    override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) = act {
        app.trainingGate.withLock {
        app.updates.assertTrainingMayStart()
        val item = requireNotNull(repo.workoutSet(setId)) { "Set unavailable" }
        repo.updateSet(item.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance))
        if (completed && !item.completed && app.preferences.snapshot().autoRest) {
            val entry = requireNotNull(repo.workoutExercise(item.workoutExerciseId)) { "Workout exercise unavailable" }
            if (repo.workout(entry.workoutId)?.status == WorkoutStatus.ACTIVE)
                app.timer.start(entry.name, entry.restSeconds, entry.id)
        }
        }
    }
    override fun finishWorkout() = act {
        val active = repo.activeWorkout() ?: return@act
        if (repo.finishWorkout(active.id)) app.timer.clear()
        app.updates.onTrainingChanged()
    }
    override fun discardWorkout() {
        if (!discardingWorkout.compareAndSet(false, true)) return
        error.value = null
        act {
            try {
                app.trainingGate.withLock {
                    repo.activeWorkout()?.let { repo.discardWorkout(it.id) }
                    // Unlike clear(), this propagates storage failures instead of reporting success.
                    app.timer.clearForSessionEnd()
                }
                // Cards and navigation must observe the durable deletion together.
                withTimeout(30_000) { contentUi.first { it.active == null } }
                discardNavigationRequest.value += 1
            } finally {
                discardingWorkout.value = false
            }
            app.updates.onTrainingChanged()
        }
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
