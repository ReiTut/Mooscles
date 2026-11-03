package com.mooscles.photos

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The exercise photos in `files/exercise_photos/`: `<id>.jpg` and `thumbs/<id>.jpg`, with drafts in the same shape
 * under `drafts/`. Every write goes to a `.tmp` file first and is renamed into place, so a reader never sees half a
 * photo and an older photo stays whole until the rename. Not in the backup, not shared.
 */
class AndroidExercisePhotoStore(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ExercisePhotoStore {
    private val root get() = File(context.filesDir, DIRECTORY)
    private val gate = Mutex()
    /** Renames [from] over [to]; a test replaces it to fail one rename. */
    @androidx.annotation.VisibleForTesting
    internal var moveFile: (File, File) -> Unit = ::atomicMove
    private val revisions = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = revisions.asStateFlow()

    private fun full(dir: File, key: String) = File(dir, "$key.jpg")
    private fun thumb(dir: File, key: String) = File(File(dir, THUMBS), "$key.jpg")
    private fun drafts() = File(root, DRAFTS)
    private fun bump() = revisions.update { it + 1 }

    override suspend fun stage(draftKey: String, source: String): Boolean = withContext(io) {
        try {
            stageFrom(draftKey, source)
        } finally {
            // A camera shot lives only until it is staged (plan 4B, F12); a gallery pick is not ours to delete.
            discardCapture(context, source)
        }
    }

    private suspend fun stageFrom(draftKey: String, source: String): Boolean {
        if (!safe(draftKey)) return false
        val uri = source.toUri()
        val processed = PhotoProcessing.process({ context.contentResolver.openInputStream(uri) ?: throw IOException("No stream") }, Build.VERSION.SDK_INT)
            ?: return false
        return gate.withLock {
            val dir = drafts()
            var tmpFull: File? = null
            var tmpThumb: File? = null
            try {
                tmpFull = tmp(dir).also { it.writeBytes(processed.full) }
                tmpThumb = tmp(File(dir, THUMBS)).also { it.writeBytes(processed.thumb) }
                // The photo first, the thumbnail (derived from it) second.
                moveFile(tmpFull, full(dir, draftKey))
                moveFile(tmpThumb, thumb(dir, draftKey))
                bump()
                true
            } catch (_: IOException) {
                false
            } finally {
                tmpFull?.delete()
                tmpThumb?.delete()
            }
        }
    }

    override suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile? = withContext(io) {
        if (!safe(draftKey)) null else loadPhoto(drafts(), draftKey, thumbnail)
    }

    override suspend fun commit(draftKey: String, exerciseId: String): Boolean = withContext(io) {
        if (!safe(draftKey) || !safe(exerciseId)) return@withContext false
        gate.withLock {
            val draftFull = full(drafts(), draftKey)
            val draftThumb = thumb(drafts(), draftKey)
            if (!draftFull.isFile || !draftThumb.isFile) return@withLock false
            val targetFull = full(root, exerciseId)
            val targetThumb = thumb(root, exerciseId)
            val oldFull = targetFull.takeIf { it.isFile }?.lastModified()
            val oldThumb = targetThumb.takeIf { it.isFile }?.lastModified()
            // The old photo is copied aside, so a failed second rename can put the old pair back whole.
            var backup: File? = null
            var movedFull = false
            try {
                if (targetFull.isFile) {
                    backup = tmp(root).also { Files.copy(targetFull.toPath(), it.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES) }
                }
                // The photo first, the thumbnail (derived from it) second: a reader between the two renames sees
                // the new photo with the old thumbnail at worst, never a thumbnail for a photo that is not there.
                moveFile(draftFull, targetFull)
                movedFull = true
                moveFile(draftThumb, targetThumb)
            } catch (_: IOException) {
                var restored = !movedFull
                if (movedFull) {
                    restored = try {
                        // Back to the draft (kept whole for a retry), then the old photo over the new one.
                        moveFile(targetFull, draftFull)
                        backup?.let { moveFile(it, targetFull) }
                        true
                    } catch (_: IOException) { false }
                }
                // Revision tells the truth: it moves if the rollback itself did not complete.
                if (!restored) bump()
                return@withLock false
            } finally {
                backup?.delete()
            }
            // The version is the modified time; make sure a replacement never reuses the older value.
            newer(targetFull, oldFull)
            newer(targetThumb, oldThumb)
            bump()
            true
        }
    }

    override suspend fun discard(draftKey: String) {
        if (!safe(draftKey)) return
        withContext(io) {
            gate.withLock {
                val deleted = full(drafts(), draftKey).delete() or thumb(drafts(), draftKey).delete()
                if (deleted) bump()
            }
        }
    }

    override suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile? = withContext(io) {
        if (!safe(exerciseId)) null else loadPhoto(root, exerciseId, thumbnail)
    }

    override suspend fun remove(exerciseId: String) {
        if (!safe(exerciseId)) return
        withContext(io) {
            gate.withLock {
                val deleted = full(root, exerciseId).delete() or thumb(root, exerciseId).delete()
                if (deleted) bump()
            }
        }
    }

    override suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long) = withContext(io) {
        gate.withLock {
            var changed = false
            val cutoff = System.currentTimeMillis() - draftMaxAgeMillis
            for (dir in listOf(root, File(root, THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.forEach { file ->
                    if (file.name.removeSuffix(SUFFIX) !in customExerciseIds && file.delete()) changed = true
                }
            }
            // A thumbnail whose photo is gone (an interrupted commit) is not shown and is not kept.
            File(root, THUMBS).listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) && !File(root, file.name).isFile }
                ?.forEach { if (it.delete()) changed = true }
            for (dir in listOf(drafts(), File(drafts(), THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) && file.lastModified() < cutoff }
                    ?.forEach { if (it.delete()) changed = true }
            }
            // Leftovers of a write that was interrupted before its rename.
            for (dir in listOf(root, File(root, THUMBS), drafts(), File(drafts(), THUMBS))) {
                dir.listFiles { file -> file.isFile && file.name.endsWith(TEMP) && file.lastModified() < cutoff }
                    ?.forEach { it.delete() }
            }
            if (changed) bump()
        }
    }

    /** A thumbnail only counts while its photo exists: the photo is the truth, the thumbnail derives from it. */
    private fun loadPhoto(dir: File, key: String, thumbnail: Boolean): PhotoFile? =
        if (!full(dir, key).isFile) null else load(if (thumbnail) thumb(dir, key) else full(dir, key))

    private fun load(file: File): PhotoFile? = try {
        // Version first: a commit between the two reads yields new bytes under an old version, which the next read fixes.
        val version = file.lastModified()
        if (file.isFile) PhotoFile(file.readBytes(), version) else null
    } catch (_: IOException) {
        null
    }

    private fun tmp(dir: File): File {
        dir.mkdirs()
        return File.createTempFile("photo", TEMP, dir)
    }

    /** Keeps [from] on failure: a draft must survive a failed commit. */
    private fun atomicMove(from: File, to: File) {
        to.parentFile?.mkdirs()
        Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Makes [file]'s modified time later than [previous], so two commits in one clock tick still differ. */
    private fun newer(file: File, previous: Long?) {
        if (previous != null && file.lastModified() <= previous) file.setLastModified(previous + 1_000)
    }

    /** Ids and keys become file names: no separators, no dots, not empty. */
    private fun safe(name: String) = name.isNotEmpty() && name.length <= 128 && name.none { it == '/' || it == '\\' || it == '.' || it.isISOControl() }

    private companion object {
        const val DIRECTORY = "exercise_photos"
        const val THUMBS = "thumbs"
        const val DRAFTS = "drafts"
        const val SUFFIX = ".jpg"
        const val TEMP = ".tmp"
    }
}
