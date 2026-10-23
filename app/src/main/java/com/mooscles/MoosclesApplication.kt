package com.mooscles

import android.app.Application
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import com.mooscles.timer.RestTimerPersistence
import com.mooscles.updates.UpdateManager
import com.mooscles.updates.UpdatePolicy

class MoosclesApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { WorkoutDatabase.open(this) }
    val repository by lazy { WorkoutRepository(database) }
    val preferences by lazy { AppPreferences(this) }
    val timer by lazy { RestTimerController(this, appScope) }
    val trainingGate = Mutex()
    val updates by lazy { UpdateManager(this, trainingGate, trainingBusy = {
        repository.activeWorkout() != null || UpdatePolicy.restTimerBusy(
            RestTimerPersistence(moosclesStore).snapshot(), System.currentTimeMillis())
    }) }
}
