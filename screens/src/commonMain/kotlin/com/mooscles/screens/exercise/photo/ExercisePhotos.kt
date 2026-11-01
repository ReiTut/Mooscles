package com.mooscles.screens.exercise.photo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.mooscles.designsystem.components.ExerciseMedia
import com.mooscles.photos.ExercisePhotoStore
import com.mooscles.photos.PhotoFile
import com.mooscles.screens.exercise.exerciseMedia
import com.mooscles.ui.ExerciseUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * What the host gives the form to pick a photo (plan Task 10; the Android launchers come in Task 15). A launcher is
 * remembered in the composition that shows the button, hands back a platform URI (null when the person backs out)
 * and starts the picker when the returned function is called.
 */
interface PhotoCapture {
    /** Whether the device has a camera app to take a photo with; "Take photo" shows only then. */
    val cameraAvailable: Boolean

    @Composable
    fun rememberGalleryLauncher(onResult: (uri: String?) -> Unit): () -> Unit

    @Composable
    fun rememberCameraLauncher(onResult: (uri: String?) -> Unit): () -> Unit
}

/**
 * The photos of custom exercises, as the screens use them: the [store], and the [capture] that picks a photo (null on a
 * platform without one: the form then has no "Photo" row). Decoded images are kept in [cache].
 */
class ExercisePhotos(val store: ExercisePhotoStore, val capture: PhotoCapture?) {
    internal val cache = PhotoCache<ImageBitmap>()
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * The ids of exercises whose photo could not be kept after their save (a failed `commit`). The form notes one just
     * before it reports the save, and the detail of that exercise takes it and shows "Couldn't save the photo.": the form
     * (and, after a creation, the library under it) is gone by then, so nothing there could show it.
     */
    internal val failedCommits = MutableStateFlow<Set<String>>(emptySet())

    internal fun noteCommitFailed(exerciseId: String) = failedCommits.update { it + exerciseId }

    internal fun clearCommitFailure(exerciseId: String) = failedCommits.update { it - exerciseId }

    /** True once for each noted failure of [exerciseId]. */
    internal fun takeCommitFailure(exerciseId: String): Boolean {
        var found = false
        failedCommits.update { found = exerciseId in it; it - exerciseId }
        return found
    }

    /** Drops the draft [key] without waiting: the form that asked for it is already leaving, so its own scope would end first. */
    internal fun discardInBackground(key: String) {
        background.launch { runCatching { store.discard(key) } }
    }
}

/**
 * The host's [ExercisePhotos]. Without one (null, the default) no screen has a "Photo" row and none reads a photo at
 * all: every custom exercise shows its equipment icon, as before.
 */
val LocalExercisePhotos = staticCompositionLocalOf<ExercisePhotos?> { null }

/** Which photo of the store a screen shows: the one of an exercise, or a draft of the open form. */
internal sealed interface PhotoSource {
    data class Exercise(val id: String) : PhotoSource
    data class Draft(val key: String) : PhotoSource
}

private val NoRevision = MutableStateFlow(0L)

/**
 * The image of [source] ([thumbnail] or full size), or null while it is read and decoded, and when there is none.
 * Read again whenever the store's revision changes; the previous image stays until the new one is ready. Reading
 * and decoding never happen in composition: the file is read in the effect, the decoding runs on
 * [Dispatchers.Default] and a version already decoded comes from [ExercisePhotos.cache]. Without [photos] nothing is read.
 */
@Composable
internal fun rememberStoredPhoto(photos: ExercisePhotos?, source: PhotoSource, thumbnail: Boolean): ImageBitmap? {
    var image by remember(source, thumbnail) { mutableStateOf<ImageBitmap?>(null) }
    val revision by remember(photos) { photos?.store?.revision ?: NoRevision }.collectAsState()
    if (photos != null) {
        LaunchedEffect(photos, source, thumbnail, revision) {
            val file = readPhoto(photos.store, source, thumbnail)
            image = if (file == null) null else photos.cache.imageOf(source, thumbnail, file.version) {
                withContext(Dispatchers.Default) { runCatching { file.bytes.decodeToImageBitmap() }.getOrNull() }
            }
        }
    }
    return if (photos == null) null else image
}

private suspend fun readPhoto(store: ExercisePhotoStore, source: PhotoSource, thumbnail: Boolean): PhotoFile? = when (source) {
    is PhotoSource.Exercise -> store.read(source.id, thumbnail)
    is PhotoSource.Draft -> store.draft(source.key, thumbnail)
}

/**
 * What a list, the picker, the workout card or the detail shows for [exercise] (spec D5): for a custom exercise with a
 * photo, the photo ([ExerciseMedia.Image], cropped square by the thumbnail); otherwise what [exerciseMedia] gives (the
 * still figure of a catalog exercise, or nothing, which is the equipment icon). The photo is [thumbnail] size or full.
 */
@Composable
fun rememberExerciseMedia(exercise: ExerciseUi, thumbnail: Boolean): ExerciseMedia {
    val base = remember(exercise) { exerciseMedia(exercise) }
    return rememberCustomPhotoMedia(exercise, thumbnail) ?: base
}

/** The [ExerciseMedia.Image] of [exercise]'s photo once it is ready; null for a catalog exercise, without a provider or without a photo. */
@Composable
internal fun rememberCustomPhotoMedia(exercise: ExerciseUi, thumbnail: Boolean): ExerciseMedia? {
    val photos = LocalExercisePhotos.current
    if (photos == null || !exercise.custom) return null
    val image = rememberStoredPhoto(photos, PhotoSource.Exercise(exercise.id), thumbnail)
    return remember(image) { image?.let { ExerciseMedia.Image(BitmapPainter(it)) } }
}
