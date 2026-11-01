package com.mooscles

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mooscles.backup.BackupException
import com.mooscles.backup.BackupCodec
import com.mooscles.backup.BackupFile
import com.mooscles.backup.BackupTexts
import com.mooscles.backup.ImportPlanner
import com.mooscles.data.*
import com.mooscles.domain.currentIsoInstant
import com.mooscles.ui.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The screens' state and commands, shared by every platform. What only a platform can do (the rest timer, the
 * updater, picked documents, tracing) comes in through [services] and [platform].
 */
abstract class MoosclesViewModel(
    private val services: MoosclesServices,
    private val platform: ViewModelPlatform = ViewModelPlatform.None,
) : ViewModel(), MoosclesActions {
    private val repo = services.repository
    /**
     * The failed action's text, code and subject, and every subject's failure count: one value, so a screen never
     * sees one without the others.
     */
    private val error = MutableStateFlow(ActionErrors())
    private val initialized = MutableStateFlow(false)
    private val startingWorkout = MutableStateFlow(false)
    private val discardingWorkout = MutableStateFlow(false)
    private val discardNavigationRequest = MutableStateFlow(0L)
    private val backup = MutableStateFlow(BackupUi())
    /** The file read by [prepareImport], kept until it is confirmed or cancelled. Only touched inside commands. */
    private var pendingImportFile: BackupFile? = null
    private val presenter = WorkoutPresentation()
    private val settingsRequest = MutableStateFlow(0L)
    fun openUpdateSettings() { settingsRequest.value += 1 }
    /** A queued command and the id it is about ([UiState.errorSubject] when it fails). */
    private class Command(val subject: String?, val block: suspend () -> Unit)
    /** [problem] is the one shown, until cleared; [failures] counts each subject's failed commands and is never cleared. */
    private data class ActionErrors(val problem: UiProblem? = null, val failures: Map<String, Int> = emptyMap())
    private val commands = Channel<Command>(Channel.UNLIMITED)
    /** Null until Room's first snapshot (or its failure): an empty placeholder must never be presented as saved data. */
    private val source = repo.data.catch { show(UiProblem("Unable to open local data: ${it.message}", UiError.LOCAL_DATA_UNAVAILABLE)); emit(AppData()) }
        .stateIn<AppData?>(viewModelScope, SharingStarted.Eagerly, null)
    /**
     * Presentation leaves the main thread: a large history takes seconds to present on a debug build.
     * One-at-a-time confinement keeps [presenter]'s caches sequential without locks; never run it in composition.
     */
    private val presentationDispatcher = Dispatchers.Default.limitedParallelism(1)
    // Pairs that arrive while a slow present() runs are conflated: only the latest one is worth presenting.
    private val presentation = combine(source.filterNotNull(), services.preferences.flow) { data, prefs -> data to prefs }
        .conflate()
        .map { (data, prefs) -> platform.presentation { presenter.present(data, prefs) } }
        .flowOn(presentationDispatcher)
    private val problems = combine(error, services.preferences.error, services.timer.error, services.timer.errorCode) { action, prefs, timer, timerCode ->
        UiErrors.current(action.problem, prefs, timer, timerCode) to action.failures
    }
    // Ready needs both: initialization finished and a presentation of Room's data (combine waits for the first one).
    private val baseUi = combine(presentation, problems, initialized, services.updates.ui, startingWorkout) { base, (problem, failures), ready, updates, starting ->
        base.copy(error = problem?.text, errorCode = problem?.code, errorSubject = problem?.subject, subjectFailures = failures,
            loading = !ready, updates = updates, startingWorkout = starting)
    }.catch { emit(UiState(loading = false, error = "Unable to read local preferences: ${it.message}", errorCode = UiError.LOCAL_DATA_UNAVAILABLE)) }
    /** Screens do not observe countdown ticks. Only the dock collects [timerUi]. */
    val contentUi = combine(baseUi, settingsRequest, discardingWorkout, discardNavigationRequest, backup) { base, request, discarding, discarded, backup ->
        base.copy(updateSettingsRequest = request, discardingWorkout = discarding, discardNavigationRequest = discarded, backup = backup)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())
    val timerUi = services.timer.ui
    // Retained for consumers needing a complete snapshot; the Activity uses contentUi instead.
    val ui = combine(contentUi, timerUi) { base, timer -> base.copy(timer = timer) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    init {
        viewModelScope.launch {
            services.timer.ui.map { it.running || it.paused }.distinctUntilChanged().collect { busy ->
                if (!busy) runCatching { services.updates.onTrainingChanged() }
            }
        }
        viewModelScope.launch {
            var initializedOk = false
            try { repo.initialize(); initializedOk = true } catch (e: CancellationException) { throw e }
            catch (e: Exception) { show(UiProblem("Unable to initialize local data: ${e.message}", UiError.LOCAL_DATA_UNAVAILABLE)) }
            initialized.value = true
            if (initializedOk) removeOrphanPhotos()
            for (command in commands) try { command.block() }
            catch (e: CancellationException) {
                // Only a cleared ViewModel ends the loop. A timed-out wait or a cancelled child is one failed
                // command: rethrowing it would silently drop every later command. Its message is not user copy.
                currentCoroutineContext().ensureActive()
                fail(UiProblem(SAVE_FAILED, UiErrors.from(e), command.subject))
            }
            catch (e: Exception) { fail(UiProblem(e.message ?: SAVE_FAILED, UiErrors.from(e), command.subject)) }
        }
    }
    /**
     * Deletes the photos of custom exercises that no longer exist. Runs only after a successful initialize, with the
     * ids from a read of its own, and inline before the first command, so a photo being committed is never swept. It is
     * not a database write: a failure is ignored, a cancellation is not.
     */
    private suspend fun removeOrphanPhotos() {
        try {
            val ids = repo.snapshot().exercises.filter { it.custom }.map { it.id }.toSet()
            services.photos.removeOrphans(ids)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* a failed sweep only leaves files for the next start */ }
    }
    /** Shows [problem] (or clears the one shown with null); the failure counts stay. */
    private fun show(problem: UiProblem?) = error.update { it.copy(problem = problem) }
    /** Shows a failed command's [problem] and counts it against its subject, in one update. */
    private fun fail(problem: UiProblem) = error.update { current ->
        val subject = problem.subject
        ActionErrors(problem, if (subject == null) current.failures else current.failures + (subject to (current.failures[subject] ?: 0) + 1))
    }
    /** Queues [block]; a failure is reported about [subject] (plan decision 7). */
    private fun act(subject: String? = null, block: suspend () -> Unit) { commands.trySend(Command(subject, block)) }
    /** Host-test seam: queues [block] behind the screen's own commands. */
    @VisibleForTesting fun enqueueCommandForTest(block: suspend () -> Unit) = act(block = block)
    /** How long a command waits for saved data to reach the screen; host tests shorten it. */
    @VisibleForTesting var screenWaitMillis = 30_000L
    /** Backup failures belong to the backup section, not the global error, and always release [BackupUi.busy]. */
    private fun backupAct(fileFailure: String, working: String, block: suspend () -> Unit) = act { backupWork(fileFailure, working, block) }
    /** [fileFailure] replaces the raw message of file-access errors (IOException, SecurityException...). */
    private suspend fun backupWork(fileFailure: String?, working: String, block: suspend () -> Unit) {
        backup.update { it.copy(busy = true, working = working, message = null, messageIsError = false) }
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            // Only file-access failures get the friendly copy; a database error must not read as "try another location".
            val fileAccess = services.documents.isFileAccessFailure(e)
            backup.update { it.copy(message = if (fileAccess && fileFailure != null) fileFailure else e.message ?: SAVE_FAILED, messageIsError = true) }
        }
        finally { backup.update { it.copy(busy = false, working = null) } }
    }
    override fun clearError() { show(null); services.preferences.clearError(); services.timer.clearError() }
    override fun createRoutine(name: String) = act {
        val savedFolder = services.preferences.snapshot().selectedRoutineFolderId
        val folderId = repo.routineFolder(savedFolder)?.id ?: RoutineFolder.DEFAULT_ID
        repo.createRoutine(name, folderId)
    }
    /** The screen's folder, not the saved selection: the screen already shows where the routine goes. */
    override fun createRoutine(name: String, folderId: String, id: String) = act(id) { repo.createRoutine(name, folderId, id) }
    override fun renameRoutine(id: String, name: String) = act { repo.renameRoutine(id, name) }
    override fun duplicateRoutine(id: String) = act { repo.duplicateRoutine(id) }
    override fun duplicateRoutine(id: String, copyName: String, newId: String) = act(newId) { repo.duplicateRoutine(id, copyName, newId) }
    override fun reorderRoutines(folderId: String, ids: List<String>) = act { repo.reorderFolderRoutines(folderId, ids) }
    override fun reorderRoutineFolders(ids: List<String>) = act { repo.reorderRoutineFolders(ids) }
    /** A deleted selected folder hands the selection to the default one, where its routines went. */
    override fun deleteRoutineFolder(id: String) = act(id) {
        repo.deleteRoutineFolder(id)
        if (services.preferences.snapshot().selectedRoutineFolderId == id) services.preferences.selectRoutineFolder(RoutineFolder.DEFAULT_ID)
    }
    override fun deleteRoutine(id: String) = act { repo.deleteRoutine(id) }
    override fun createRoutineFolder(name: String) = act {
        val id = repo.createRoutineFolder(name)
        services.preferences.selectRoutineFolder(id)
    }
    override fun renameRoutineFolder(id: String, name: String) = act { repo.renameRoutineFolder(id, name) }
    override fun selectRoutineFolder(id: String) = act {
        dataRequireNotNull(repo.routineFolder(id), DataError.FOLDER_NOT_FOUND) { "Folder unavailable." }
        services.preferences.selectRoutineFolder(id)
    }
    override fun moveRoutineToFolder(routineId: String, folderId: String) = act { repo.moveRoutineToFolder(routineId, folderId) }
    override fun addRoutineExercise(routineId: String, exerciseId: String) = act(routineId) { repo.addRoutineExercise(routineId, exerciseId) }
    override fun addRoutineExercises(routineId: String, exerciseIds: List<String>) = act(routineId) { repo.addRoutineExercises(routineId, exerciseIds) }
    override fun reorderRoutineExercises(routineId: String, ids: List<String>) = act { repo.reorderRoutineExercises(routineId, ids) }
    override fun replaceRoutineExercise(entryId: String, exerciseId: String) = act { repo.replaceRoutineExercise(entryId, exerciseId) }
    override fun removeRoutineExercise(entryId: String) = act { repo.removeRoutineExercise(entryId) }
    override fun addRoutineSet(entryId: String) = act { repo.addRoutineSet(entryId) }
    override fun removeRoutineSet(setId: String) = act { repo.removeRoutineSet(setId) }
    override fun updateRoutineSet(setId: String, reps: Int, weightKg: Double?) = act { repo.updateRoutineSet(setId, reps, weightKg) }
    override fun inheritRoutineSet(setId: String) = act { repo.inheritRoutineSet(setId) }
    override fun setRoutineSetWarmup(setId: String, warmup: Boolean) = act { repo.setRoutineSetWarmup(setId, warmup) }
    override fun restoreRoutineSet(entryId: String, position: Int, set: RoutineSetUi) = act(set.id) {
        repo.restoreRoutineSet(entryId, position, RoutineSet(id = set.id, routineExerciseId = entryId, position = position,
            targetReps = set.reps, targetWeightKg = set.weightKg, inheritPr = set.inherited, warmup = set.warmup))
    }
    override fun copyRoutineSetToAll(entryId: String, sourceSetId: String) = act(entryId) { repo.copyRoutineSetToAll(sourceSetId) }
    override fun restoreRoutineSetTargets(entryId: String, exerciseId: String, previous: List<RoutineSetUi>) = act(entryId) {
        // The sets keep their places: the position is not part of what the copy changed.
        repo.restoreRoutineSetTargets(entryId, exerciseId, previous.map { set ->
            RoutineSet(id = set.id, routineExerciseId = entryId, targetReps = set.reps, targetWeightKg = set.weightKg,
                inheritPr = set.inherited, warmup = set.warmup)
        })
    }
    override fun updateRoutineExerciseRest(entryId: String, seconds: Int) = act { repo.updateRoutineExerciseRest(entryId, seconds) }
    override fun createCustomExercise(id: String, name: String, muscle: String, equipment: String, convention: String) = act(id) {
        repo.createCustomExercise(name, muscleOf(muscle), equipmentOf(equipment), conventionOf(convention), id)
    }
    override fun saveCustomExercise(id: String?, name: String, muscle: String, equipment: String, convention: String) = act(id) {
        val m = muscleOf(muscle)
        val e = equipmentOf(equipment)
        val c = conventionOf(convention)
        if (id == null) repo.createCustomExercise(name, m, e, c)
        else {
            val existing = dataRequireNotNull(repo.exercise(id), DataError.EXERCISE_NOT_FOUND) { "Exercise unavailable" }
            repo.saveExercise(existing.copy(name = name, muscle = m, equipment = e, convention = c))
        }
    }
    private fun muscleOf(label: String) = MuscleGroup.fromLabelOrName(label) ?: MuscleGroup.FULL_BODY
    private fun equipmentOf(label: String) = Equipment.fromLabelOrName(label) ?: Equipment.OTHER
    private fun conventionOf(label: String) = LoadConvention.fromLabelOrName(label) ?: LoadConvention.TOTAL_LOAD
    override fun startWorkout(routineId: String?) {
        if (!startingWorkout.compareAndSet(false, true)) return
        show(null)
        act {
            platform.startingSession {
            try {
                val id = services.trainingGate.withLock {
                    services.updates.assertTrainingMayStart()
                    if (repo.activeWorkout() == null) services.timer.clear()
                    val prefs = services.preferences.snapshot()
                    if (routineId == null) repo.startEmpty(defaultRestSeconds = prefs.defaultRestSeconds)
                    else repo.startRoutine(routineId, prefs.defaultRestSeconds)
                }
                // Keep loading visible until saved data reaches the editor, also on resume.
                try {
                    withTimeout(screenWaitMillis) { contentUi.first { it.active?.id == id } }
                } catch (failure: TimeoutCancellationException) {
                    throw IllegalStateException("Your workout was saved, but the screen could not open. Return to Workout to resume it.", failure)
                }
            } finally {
                startingWorkout.value = false
            }
            }
        }
    }
    override fun renameWorkout(id: String, name: String) = act { repo.renameWorkout(id, name) }
    override fun addWorkoutExercise(workoutId: String, exerciseId: String) = act(workoutId) { repo.addWorkoutExercise(workoutId, exerciseId, services.preferences.snapshot().defaultRestSeconds) }
    override fun replaceWorkoutExercise(entryId: String, exerciseId: String) = act { repo.replaceWorkoutExercise(entryId, exerciseId) }
    override fun removeWorkoutExercise(entryId: String) = act { repo.removeWorkoutExercise(entryId) }
    override fun moveWorkoutExercise(entryId: String, direction: Int) = act { repo.moveWorkoutExercise(entryId, direction) }
    override fun updateWorkoutExerciseRest(entryId: String, seconds: Int) = act { repo.updateWorkoutExerciseRest(entryId, seconds) }
    override fun addSet(entryId: String) = act { repo.addSet(entryId) }
    override fun removeSet(setId: String) = act { repo.removeSet(setId) }
    // Through the gate like updateSet: the set put back may be a completed one.
    override fun restoreSet(entryId: String, position: Int, set: SetUi) = act(set.id) {
        services.trainingGate.withLock {
            services.updates.assertTrainingMayStart()
            repo.restoreSet(entryId, position, WorkoutSet(id = set.id, workoutExerciseId = entryId, position = position,
                reps = set.reps, weightKg = set.weightKg, completed = set.completed, warmup = set.warmup, assistance = set.assistance))
        }
    }
    override fun updateSet(setId: String, reps: Int, weightKg: Double, completed: Boolean, warmup: Boolean, assistance: Boolean) = act {
        services.trainingGate.withLock {
        services.updates.assertTrainingMayStart()
        val item = dataRequireNotNull(repo.workoutSet(setId), DataError.SET_NOT_FOUND) { "Set unavailable" }
        repo.updateSet(item.copy(reps = reps, weightKg = weightKg, completed = completed, warmup = warmup, assistance = assistance))
        if (completed && !item.completed && services.preferences.snapshot().autoRest) {
            val entry = dataRequireNotNull(repo.workoutExercise(item.workoutExerciseId), DataError.WORKOUT_EXERCISE_NOT_FOUND) { "Workout exercise unavailable" }
            // A 0:00 rest turns this exercise's rest off (supersets): start(…, 0) would only finish at once.
            if (entry.restSeconds > 0 && repo.workout(entry.workoutId)?.status == WorkoutStatus.ACTIVE)
                services.timer.start(entry.name, entry.restSeconds, entry.id)
        }
        }
    }
    override fun finishWorkout() = act {
        val active = repo.activeWorkout() ?: return@act
        if (repo.finishWorkout(active.id)) services.timer.clear()
        services.updates.onTrainingChanged()
    }
    override fun discardWorkout() {
        if (!discardingWorkout.compareAndSet(false, true)) return
        show(null)
        act {
            try {
                services.trainingGate.withLock {
                    repo.activeWorkout()?.let { repo.discardWorkout(it.id) }
                    // Unlike clear(), this propagates storage failures instead of reporting success.
                    services.timer.clearForSessionEnd()
                }
                // Cards and navigation must observe the durable deletion together.
                try {
                    withTimeout(screenWaitMillis) { contentUi.first { it.active == null } }
                } catch (failure: TimeoutCancellationException) {
                    throw IllegalStateException("The workout was discarded, but the screen could not update. Open History or Workout to refresh it.", failure)
                }
                discardNavigationRequest.value += 1
            } finally {
                discardingWorkout.value = false
            }
            services.updates.onTrainingChanged()
        }
    }
    override fun deleteWorkout(id: String) = act { repo.deleteWorkout(id) }
    override fun setPounds(pounds: Boolean) = act { services.preferences.pounds(pounds) }
    override fun setTheme(theme: String) = act { services.preferences.theme(theme.lowercase()) }
    override fun setAutoRest(enabled: Boolean) = act { services.preferences.autoRest(enabled) }
    override fun setDefaultRest(seconds: Int) = act { services.preferences.defaultRest(seconds) }
    override fun startTimer(exerciseName: String, seconds: Int, workoutExerciseId: String?) = act { services.trainingGate.withLock { services.updates.assertTrainingMayStart(); services.timer.start(exerciseName, seconds, workoutExerciseId) } }
    override fun pauseTimer() = act { services.timer.pause() }
    override fun resumeTimer() = act { services.trainingGate.withLock { services.updates.assertTrainingMayStart(); services.timer.resume() } }
    override fun resetTimer() = act { services.trainingGate.withLock { services.updates.assertTrainingMayStart(); services.timer.reset() } }
    override fun skipTimer() = act { services.timer.skip(); services.updates.onTrainingChanged() }
    override fun adjustTimer(seconds: Int) = act { services.trainingGate.withLock { services.updates.assertTrainingMayStart(); services.timer.adjust(seconds) } }
    override fun checkForUpdates() = act { services.updates.requestCheck(true) }
    override fun setAutomaticUpdates(enabled: Boolean) = act { services.updates.setAutomatic(enabled) }
    override fun setUpdateWifiOnly(enabled: Boolean) = act { services.updates.setWifiOnly(enabled) }
    override fun setIncludePrereleases(enabled: Boolean) = act { services.updates.setPrereleases(enabled) }
    override fun downloadUpdate() = act { services.updates.enqueueDownload(manual = true) }
    override fun cancelUpdate() = act { services.updates.cancelDownload() }
    override fun installUpdate() = act { install() }
    override fun exportData(uri: String) = backupAct(BackupTexts.EXPORT_FILE_FAILED, BackupTexts.WORKING_EXPORT) {
        val preferences = services.preferences.snapshot().toBackup()
        val about = services.about
        val file = try {
            val file = repo.exportBackup(preferences, about, currentIsoInstant())
            val text = withContext(Dispatchers.Default) { BackupCodec.encode(file) }
            services.documents.write(uri, text)
            file
        } catch (e: Exception) {
            // The picker already created the document: do not leave an empty or partial backup behind.
            if (e !is CancellationException) services.documents.discard(uri)
            throw e
        }
        val data = file.data
        backup.update { it.copy(message = BackupTexts.exported(data.workouts.size, data.routines.size, data.exercises.count { e -> e.custom })) }
    }
    /** Reads and plans only; nothing is written until [confirmImport]. */
    override fun prepareImport(uri: String) = backupAct(BackupTexts.IMPORT_FILE_FAILED, BackupTexts.WORKING_CHECK) {
        pendingImportFile = null
        backup.update { it.copy(pendingImport = null) }
        val text = services.documents.read(uri)
        // Decode before reading the phone, so a file that is not a backup never pays for a full database read.
        val file = withContext(Dispatchers.Default) { BackupCodec.decode(text) }
        val local = repo.localState()
        val plan = withContext(Dispatchers.Default) { ImportPlanner.plan(file, local) }
        pendingImportFile = file
        backup.update { it.copy(pendingImport = plan.summary) }
    }
    override fun confirmImport() = act {
        // A repeated confirm finds nothing pending and must leave the previous outcome on screen.
        val file = pendingImportFile ?: return@act
        backupWork(null, BackupTexts.WORKING_IMPORT) {
            // Close the dialog now; the private file stays until the import ran so the work below still has it.
            backup.update { it.copy(pendingImport = null) }
            try {
                // The phone may have changed since the preview: the repository plans again inside its transaction.
                // Preferences are restored as the transaction's last step, so a failed restore rolls the data back
                // and no install can start in between. DataStore is not part of the Room transaction: a commit
                // failure after the restore would still leave the settings applied.
                val plan = services.trainingGate.withLock {
                    services.updates.assertTrainingMayStart()
                    repo.importBackup(file) { plan -> plan.preferences?.let { services.preferences.restore(it) } }
                }
                backup.update { it.copy(message = BackupTexts.imported(plan.summary)) }
            } finally {
                pendingImportFile = null
                backup.update { it.copy(pendingImport = null) }
            }
        }
    }
    override fun cancelImport() = act {
        pendingImportFile = null
        backup.update { it.copy(pendingImport = null) }
    }
    override fun clearBackupMessage() = act { backup.update { it.copy(message = null) } }
    /** Starts the platform installer for a verified update; runs inside a command, from [installUpdate]. */
    protected abstract suspend fun install()

    private companion object {
        const val SAVE_FAILED = "Could not save this change. Please try again."
    }
}
