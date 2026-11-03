package com.mooscles

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.mooscles.backup.AndroidBackupDocuments
import com.mooscles.backup.BackupApp
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.data.open
import com.mooscles.photos.AndroidExercisePhotoStore
import com.mooscles.screens.progress.ProgressIndexCache
import com.mooscles.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.updates.UpdateManager
import com.mooscles.updates.UpdatePolicy

class MoosclesApplication : Application() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguages.wrap(base)) }
    override fun onCreate() { super.onCreate(); AppLanguages.migrateToFramework(this) }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (Build.VERSION.SDK_INT < 33) AppLanguages.applyLegacy(this)
    }
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { WorkoutDatabase.open(this) }
    val repository by lazy { WorkoutRepository(database) }
    val preferences by lazy { AppPreferences(this) }
    val timer by lazy { RestTimerController(this, appScope) }
    val photos by lazy { AndroidExercisePhotoStore(this) }
    /**
     * The one progress index of the process (spec D15, plan 5A decision 9): built off the main thread and shared by the
     * Records tab, the exercise progress and the detail's sparkline, so leaving and reopening a screen never builds it
     * again. Handed to the screens by `LocalProgressIndexCache` ([ProvideProgressIndex]). Each build is a trace section
     * ([ProgressIndexTrace]).
     */
    val progressIndex: ProgressIndexCache by lazy { ProgressIndexCache(appScope, build = ProgressIndexTrace::build) }
    val trainingGate = Mutex()
    val updates by lazy { UpdateManager(this, trainingGate, trainingBusy = {
        repository.activeWorkout() != null || UpdatePolicy.restTimerBusy(
            RestTimerPersistence(moosclesStore).snapshot(), System.currentTimeMillis())
    }) }
    /** What the view model works with, in the order it used to create them: repository, preferences, timer, updates. */
    val services by lazy {
        MoosclesServices(repository, preferences, timer, updates, trainingGate, AndroidBackupDocuments(this),
            BackupApp(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, "android"), photos)
    }
}
