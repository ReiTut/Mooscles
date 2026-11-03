package com.mooscles.screens.flow

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.mooscles.createMoosclesStore
import com.mooscles.data.WorkoutDatabase
import com.mooscles.data.openWorkoutDatabase
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.random.Random

// The simulator's side of FlowHarness: the same declarations as FlowPlatform.android.kt (not expect/actual).

/** A new, empty directory under the simulator's temporary directory; [deleteFlowDirectory] removes it. */
@OptIn(ExperimentalForeignApi::class)
internal fun createFlowDirectory(): String {
    val directory = NSTemporaryDirectory().trimEnd('/') + "/flow-" + Random.nextLong().toULong().toString(16)
    check(NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)) {
        "Could not create $directory"
    }
    return directory
}

@OptIn(ExperimentalForeignApi::class)
internal fun deleteFlowDirectory(directory: String) {
    NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
}

/** `<directory>/mooscles.db` through the iOS app's own path: [openWorkoutDatabase] (`BundledSQLiteDriver`). */
internal fun openFlowDatabase(directory: String): WorkoutDatabase = openWorkoutDatabase(directory)

/** The preferences file through the iOS app's own factory, [createMoosclesStore]; cancel [scope] to close it. */
internal fun openFlowStore(directory: String, scope: CoroutineScope): DataStore<Preferences> =
    createMoosclesStore(directory, scope)

/**
 * Writes [image] as `screens/build/ios-captures/<name>.png`, for the CI to upload. The simulator
 * runs the host's test binary in place (`screens/build/bin/iosSimulatorArm64/debugTest/test.kexe`),
 * so the module's build directory is found from the executable's own path; elsewhere the PNG goes
 * to the temporary directory. A PNG that cannot be encoded or written fails the flow: only the
 * capture itself may be unsupported (the caller handles that).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun saveFlowCapture(name: String, image: ImageBitmap) {
    val png = checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes) {
        "FLOW capture $name could not be encoded as PNG"
    }
    val executable = NSProcessInfo.processInfo.arguments.firstOrNull() as? String
    val build = executable?.takeIf { "/build/bin/" in it }?.substringBefore("/build/bin/")?.let { "$it/build" }
    val directory = (build ?: NSTemporaryDirectory().trimEnd('/')) + "/ios-captures"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
    val path = "$directory/$name.png"
    check(data.writeToFile(path, atomically = true)) { "FLOW capture not written: $path" }
    println("FLOW capture $path")
}
