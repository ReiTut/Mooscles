package com.mooscles.photos

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A stored photo (full size or thumbnail) as JPEG [bytes]; [version] changes whenever the photo is replaced. */
class PhotoFile(val bytes: ByteArray, val version: Long)

/**
 * The photo of a custom exercise, kept as an app-private resized copy. Never part of a backup. A photo is first
 * staged as a draft (the form is still open) and then committed under the exercise id by rename.
 */
interface ExercisePhotoStore {
    /** Bumped after every change, so screens read again. */
    val revision: StateFlow<Long>

    /** Decodes [source] (a platform URI), orients, resizes and writes it as the draft [draftKey]; false if it cannot. */
    suspend fun stage(draftKey: String, source: String): Boolean

    suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile?

    /** Moves the draft to [exerciseId] by rename, replacing an older photo; false if it cannot. */
    suspend fun commit(draftKey: String, exerciseId: String): Boolean

    suspend fun discard(draftKey: String)

    suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile?

    suspend fun remove(exerciseId: String)

    /**
     * Deletes photos whose id is not in [customExerciseIds], drafts older than [draftMaxAgeMillis] and stale
     * temporary files. Callers pass ids from a successful read only: an empty set deletes every photo.
     */
    suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long = 86_400_000)
}

/** The store of a platform without photos: every read is null, every write fails or does nothing. */
object NoExercisePhotos : ExercisePhotoStore {
    override val revision: StateFlow<Long> = MutableStateFlow(0L)
    override suspend fun stage(draftKey: String, source: String) = false
    override suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile? = null
    override suspend fun commit(draftKey: String, exerciseId: String) = false
    override suspend fun discard(draftKey: String) {}
    override suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile? = null
    override suspend fun remove(exerciseId: String) {}
    override suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long) {}
}
