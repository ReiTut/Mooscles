package com.mooscles

import com.mooscles.backup.BackupApp
import com.mooscles.backup.BackupDocumentStore
import com.mooscles.data.WorkoutRepository
import com.mooscles.photos.ExercisePhotoStore
import com.mooscles.photos.NoExercisePhotos
import com.mooscles.timer.RestTimer
import com.mooscles.updates.UpdateCoordinator
import kotlinx.coroutines.sync.Mutex

/** The process-wide objects a [MoosclesViewModel] works with; each platform builds them once. */
class MoosclesServices(
    val repository: WorkoutRepository,
    val preferences: AppPreferences,
    val timer: RestTimer,
    val updates: UpdateCoordinator,
    /** Shared by workout start, backup import and update installation. */
    val trainingGate: Mutex,
    val documents: BackupDocumentStore,
    /** The app identity written into backups. */
    val about: BackupApp,
    /** Custom exercise photos; the default keeps platforms without them unchanged. */
    val photos: ExercisePhotoStore = NoExercisePhotos,
)
