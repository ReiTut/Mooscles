package com.mooscles.updates

import com.mooscles.ui.UpdateUi
import kotlinx.coroutines.flow.Flow

/** The app updater as the view model drives it. Installation itself is platform code (MoosclesViewModel.install). */
interface UpdateCoordinator {
    val ui: Flow<UpdateUi>
    /** Called when a workout or the rest timer ends, so a deferred update can install. */
    suspend fun onTrainingChanged()
    /** Throws [TrainingBlockedException] while an update is installing: training must not start then. */
    suspend fun assertTrainingMayStart()
    suspend fun requestCheck(manual: Boolean)
    suspend fun setAutomatic(enabled: Boolean)
    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun setPrereleases(enabled: Boolean)
    suspend fun enqueueDownload(manual: Boolean = false)
    suspend fun cancelDownload(markCancelled: Boolean = true)
}

/**
 * Training refused because an update is installing. An [IllegalArgumentException], the type the check threw
 * before it had its own, so the message and every existing handler stay the same.
 */
class TrainingBlockedException(message: String) : IllegalArgumentException(message)
