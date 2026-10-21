package com.mooscles

import android.app.Application
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.WorkoutRepository
import com.mooscles.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MoosclesApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val database by lazy { WorkoutDatabase.open(this) }
    val repository by lazy { WorkoutRepository(database) }
    val preferences by lazy { AppPreferences(this) }
    val timer by lazy { RestTimerController(this, appScope) }
}
