package com.mooscles.photos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mooscles.screens.exercise.photo.PhotoCapture
import java.io.File
import java.util.UUID

/** The `FileProvider` authority that serves the capture folder (declared in the manifest, shared with the workout cards). */
internal fun captureAuthority(context: Context) = "${context.packageName}.workout-shares"

/**
 * The gallery and the camera for the custom exercise form (plan 4B decision 11, spike S3): no permission is asked. The
 * gallery is the system photo picker (`PickVisualMedia`, images only; the platform falls back to the document picker),
 * and the camera writes into a new file of `cache/photo-capture/` that the camera app reaches through the app's
 * `FileProvider`. The store deletes that file once it has been staged ([AndroidExercisePhotoStore.stage]); a cancelled shot
 * deletes it here.
 */
class AndroidPhotoCapture(private val context: Context) : PhotoCapture {
    // Asked once per instance (the form reads it in composition, on every keystroke); the host keeps one instance.
    override val cameraAvailable: Boolean by lazy {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
    }

    @Composable
    override fun rememberGalleryLauncher(onResult: (uri: String?) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onResult)
        var launching by remember { mutableStateOf(false) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            launching = false
            latest(uri?.toString())
        }
        // The picker is in front once launched; a second tap before it shows is ignored. It may be missing on a device
        // without the document picker: that is a cancelled pick.
        return remember(launcher) {
            {
                if (!launching) {
                    launching = true
                    try {
                        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } catch (_: ActivityNotFoundException) {
                        launching = false
                        latest(null)
                    }
                }
            }
        }
    }

    @Composable
    override fun rememberCameraLauncher(onResult: (uri: String?) -> Unit): () -> Unit {
        val latest by rememberUpdatedState(onResult)
        // The Android process can die while the camera app is in front (pre-flight F12): the file the camera writes is
        // remembered across it, and the result that comes back afterwards is handed to the new composition with it.
        var pending by rememberSaveable { mutableStateOf<String?>(null) }
        val appContext = remember { context.applicationContext }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            val uri = pending
            pending = null
            if (taken && uri != null) {
                latest(uri)
            } else {
                uri?.let { discardCapture(appContext, it) }
                latest(null)
            }
        }
        return remember(launcher) {
            {
                // One shot at a time: while the camera is in front, a second tap makes no second file.
                if (pending == null) {
                    val uri = newCaptureUri(appContext)
                    pending = uri.toString()
                    try {
                        launcher.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        // The camera app went away after the check: a cancelled shot, its file cleaned.
                        pending = null
                        discardCapture(appContext, uri.toString())
                        latest(null)
                    }
                }
            }
        }
    }

    internal companion object {
        /** A new, empty file in the capture folder, as a `content://` URI the camera app may write. */
        fun newCaptureUri(context: Context): Uri {
            val dir = File(context.cacheDir, PHOTO_CAPTURE_DIR).apply { mkdirs() }
            // Shots left behind by a process that died before the result came back.
            val stale = System.currentTimeMillis() - STALE_MILLIS
            dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < stale) it.delete() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            return FileProvider.getUriForFile(context, captureAuthority(context), file)
        }

        private const val STALE_MILLIS = 24L * 60 * 60 * 1000
    }
}

/**
 * Deletes the capture file behind [uri] when it is one of ours (a `content://` URI of the capture folder); any other
 * URI (a gallery pick, a file the person chose) is never touched. Returns whether a file was deleted.
 */
internal fun discardCapture(context: Context, uri: String): Boolean {
    val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
    if (parsed.scheme != "content" || parsed.authority != captureAuthority(context)) return false
    if (parsed.pathSegments.firstOrNull() != "photo_capture") return false
    return runCatching { context.contentResolver.delete(parsed, null, null) > 0 }.getOrDefault(false)
}
