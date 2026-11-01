package com.mooscles.screens.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mooscles.AppPreferences
import com.mooscles.MoosclesServices
import com.mooscles.MoosclesViewModel
import com.mooscles.backup.BackupApp
import com.mooscles.backup.BackupDocumentStore
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.domain.currentTimeMillis
import com.mooscles.screens.summary.WorkoutSummaryScreen
import com.mooscles.screens.workout.ActiveWorkoutRoute
import com.mooscles.timer.InProcessRestTimer
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.ui.UpdateUi
import com.mooscles.updates.UpdateCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex

/** The common view model as a platform subclasses it, with nothing to install. */
internal class FlowViewModel(services: MoosclesServices) : MoosclesViewModel(services) {
    override suspend fun install() {}
}

/** An updater with nothing to offer: training is always allowed. */
private object NoUpdates : UpdateCoordinator {
    override val ui: Flow<UpdateUi> = flowOf(UpdateUi())
    override suspend fun onTrainingChanged() {}
    override suspend fun assertTrainingMayStart() {}
    override suspend fun requestCheck(manual: Boolean) {}
    override suspend fun setAutomatic(enabled: Boolean) {}
    override suspend fun setWifiOnly(enabled: Boolean) {}
    override suspend fun setPrereleases(enabled: Boolean) {}
    override suspend fun enqueueDownload(manual: Boolean) {}
    override suspend fun cancelDownload(markCancelled: Boolean) {}
}

/** No documents: the flow never exports or imports. */
private object NoDocuments : BackupDocumentStore {
    override suspend fun write(uri: String, text: String) = error("The flow writes no document.")
    override suspend fun discard(uri: String) {}
    override suspend fun read(uri: String): String = error("The flow reads no document.")
    override fun isFileAccessFailure(failure: Exception): Boolean = false
}

/**
 * The real data stack of one end-to-end flow (spec D6), in a fresh directory: Room through the
 * platform's own path ([openFlowDatabase]), the preferences file through DataStore
 * ([openFlowStore]), the real [WorkoutRepository] and [AppPreferences], the [InProcessRestTimer]
 * over the same `timer_*` keys, and the common [MoosclesViewModel] ([FlowViewModel]) over them,
 * owned by a [ViewModelStore]. [close] clears the view model and closes and deletes everything.
 *
 * Build it, seed [repository], and only then call [createViewModel], so its initialization reads
 * the seeded data as an app opening would.
 */
internal class FlowHarness {
    val directory: String = createFlowDirectory()
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: WorkoutDatabase = openFlowDatabase(directory)
    val repository = WorkoutRepository(database)
    // One DataStore for the file, shared by the preferences and the timer, as in the app.
    private val store = openFlowStore(directory, storeScope)
    val preferences = AppPreferences(store)
    private val persistence = RestTimerPersistence(store, ::currentTimeMillis)
    val timer = InProcessRestTimer(persistence, timerScope, { repository.activeWorkout()?.id }, ::currentTimeMillis)
    val services = MoosclesServices(
        repository = repository,
        preferences = preferences,
        timer = timer,
        updates = NoUpdates,
        trainingGate = Mutex(),
        documents = NoDocuments,
        about = BackupApp(versionName = "flow", versionCode = 1, platform = "test"),
    )
    private val viewModels = ViewModelStore()

    fun createViewModel(): FlowViewModel =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { FlowViewModel(services) } })[FlowViewModel::class]

    /**
     * Clears the view model (cancelling its scope), stops the timer and the store and waits for
     * their work to end (a tick or a write must not hit a closed database or a deleted file), then
     * closes the database and deletes the files.
     */
    fun close() {
        viewModels.clear()
        runBlocking {
            timerScope.coroutineContext.job.cancelAndJoin()
            storeScope.coroutineContext.job.cancelAndJoin()
        }
        database.close()
        deleteFlowDirectory(directory)
    }
}

/**
 * The app's path through the workout as `:app` hosts it (`MoosclesApp`): the `workout` route over
 * the view model's `contentUi`, its `timerUi` read only by the rest timer, and, once the route asks
 * for it, the summary of the finished workout. Clear of the system bars and the keyboard
 * ([WindowInsets.safeDrawing]), as the app's window is, so a soft keyboard never pans the window.
 */
@Composable
internal fun FlowApp(viewModel: FlowViewModel) {
    val state by viewModel.contentUi.collectAsState()
    val timer = viewModel.timerUi.collectAsState()
    var summaryId by rememberSaveable { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val finished = summaryId?.let { id -> state.history.firstOrNull { it.id == id } }
        if (finished == null) {
            ActiveWorkoutRoute(
                state = state,
                timer = timer,
                actions = viewModel,
                onMinimize = {},
                onOpenSummary = { summaryId = it },
                onExerciseHistory = {},
                onChooseRoutine = {},
            )
        } else {
            WorkoutSummaryScreen(finished, state.history, state.preferences.pounds, onDone = {}, onShare = {}, onDetails = {})
        }
    }
}
