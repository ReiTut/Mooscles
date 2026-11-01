package com.mooscles.screens.exercise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.mooscles.photos.ExercisePhotoStore
import com.mooscles.photos.PhotoFile
import com.mooscles.screens.exercise.photo.ExercisePhotos
import com.mooscles.screens.exercise.photo.LocalExercisePhotos
import com.mooscles.screens.exercise.photo.PhotoCapture
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** A 1 x 1 PNG, so the screens have something real to decode. */
@OptIn(ExperimentalEncodingApi::class)
internal val TinyPng: ByteArray = Base64.decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVQI12P4//8/AAX+Av7czFnnAAAAAElFTkSuQmCC",
)

/**
 * An in-memory [ExercisePhotoStore] that logs every call as `"<method>:<args>"` in [log]. A staged draft is the
 * [TinyPng]; [stageWorks] and [commitWorks] make the two steps that can fail fail. Safe to call from any thread
 * (the screens discard drafts in the background).
 */
internal class FakeExercisePhotoStore : ExercisePhotoStore {
    private data class Data(
        val calls: List<String> = emptyList(),
        val drafts: Map<String, PhotoFile> = emptyMap(),
        val photos: Map<String, PhotoFile> = emptyMap(),
        val versions: Long = 0,
    )

    private val data = MutableStateFlow(Data())
    private val revisionFlow = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = revisionFlow

    @Volatile var stageWorks = true

    /** While set, [stage] waits for it (the photo is still being prepared). */
    @Volatile var stageGate: CompletableDeferred<Unit>? = null
    @Volatile var commitWorks = true

    /** The calls so far, in order. */
    val log: List<String> get() = data.value.calls

    /** Adds a line of the test's own to [log], to see its order among the store's calls. */
    fun note(line: String) = data.update { it.copy(calls = it.calls + line) }

    /** The photo an exercise already has. */
    fun givePhoto(exerciseId: String) {
        data.update { it.copy(photos = it.photos + (exerciseId to PhotoFile(TinyPng, it.versions + 1)), versions = it.versions + 1) }
        revisionFlow.update { it + 1 }
    }

    fun hasPhoto(exerciseId: String) = exerciseId in data.value.photos

    fun hasDraft(key: String) = key in data.value.drafts

    override suspend fun stage(draftKey: String, source: String): Boolean {
        note("stage:$draftKey:$source")
        stageGate?.await()
        if (!stageWorks) return false
        data.update { it.copy(drafts = it.drafts + (draftKey to PhotoFile(TinyPng, it.versions + 1)), versions = it.versions + 1) }
        revisionFlow.update { it + 1 }
        return true
    }

    override suspend fun draft(draftKey: String, thumbnail: Boolean): PhotoFile? = data.value.drafts[draftKey]

    override suspend fun commit(draftKey: String, exerciseId: String): Boolean {
        note("commit:$draftKey:$exerciseId")
        if (!commitWorks) return false
        val draft = data.value.drafts[draftKey] ?: return false
        data.update { it.copy(drafts = it.drafts - draftKey, photos = it.photos + (exerciseId to draft)) }
        revisionFlow.update { it + 1 }
        return true
    }

    override suspend fun discard(draftKey: String) {
        note("discard:$draftKey")
        data.update { it.copy(drafts = it.drafts - draftKey) }
        revisionFlow.update { it + 1 }
    }

    override suspend fun read(exerciseId: String, thumbnail: Boolean): PhotoFile? = data.value.photos[exerciseId]

    override suspend fun remove(exerciseId: String) {
        note("remove:$exerciseId")
        data.update { it.copy(photos = it.photos - exerciseId) }
        revisionFlow.update { it + 1 }
    }

    override suspend fun removeOrphans(customExerciseIds: Set<String>, draftMaxAgeMillis: Long) {}
}

/**
 * A [PhotoCapture] whose launchers answer at once with [galleryUri] / [cameraUri] (null: the person backed out), and
 * count their launches.
 */
internal class FakeCapture(override val cameraAvailable: Boolean = true) : PhotoCapture {
    @Volatile var galleryUri: String? = "content://gallery/1"
    @Volatile var cameraUri: String? = "content://camera/1"
    var galleryLaunches = 0
    var cameraLaunches = 0

    @Composable
    override fun rememberGalleryLauncher(onResult: (uri: String?) -> Unit): () -> Unit =
        { galleryLaunches++; onResult(galleryUri) }

    @Composable
    override fun rememberCameraLauncher(onResult: (uri: String?) -> Unit): () -> Unit =
        { cameraLaunches++; onResult(cameraUri) }
}

/** The photos a test gives the screens: a [store] and a [capture]. */
internal class FakeExercisePhotos(cameraAvailable: Boolean = true) {
    val store = FakeExercisePhotoStore()
    val capture = FakeCapture(cameraAvailable)
    val photos = ExercisePhotos(store, capture)

    /** Shows [content] with these photos provided, as the app does. */
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalExercisePhotos provides photos, content = content)
    }
}
