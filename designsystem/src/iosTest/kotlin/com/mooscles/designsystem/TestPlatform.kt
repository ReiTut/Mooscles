package com.mooscles.designsystem

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * The Skiko test input drops `cancel()` (`InputDispatcher.skiko.kt`: "desktop don't have cancel
 * events as Android does"), so a cancelled gesture stays down for the rest of the test.
 */
internal const val INJECTS_TOUCH_CANCEL = false

/**
 * The Skiko test input is expected to drive a long press and a drag. Phase 4B (plan decision 2):
 * if the simulator's CI run shows it does not, this turns false and the drag tests stand down there
 * (the accessibility actions still run).
 */
internal const val INJECTS_DRAG = true

/** Nothing to do on the iOS simulator: its input mode belongs to the test's own scene. */
internal fun restoreTouchMode() = Unit

/**
 * Writes [image] as `designsystem/build/ios-captures/<name>.png`, for the CI to upload (the
 * emulator's side is in its TestPlatform.kt; not expect/actual). The simulator runs the host's test
 * binary in place (`designsystem/build/bin/iosSimulatorArm64/debugTest/test.kexe`), so the module's
 * build directory is found from the executable's path; elsewhere the PNG goes to the temporary
 * directory. A PNG that cannot be encoded or written fails the test.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun saveChartsCapture(name: String, image: ImageBitmap) {
    val png = checkNotNull(Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes) {
        "CHARTS capture $name could not be encoded as PNG"
    }
    val executable = NSProcessInfo.processInfo.arguments.firstOrNull() as? String
    val build = executable?.takeIf { "/build/bin/" in it }?.substringBefore("/build/bin/")?.let { "$it/build" }
    val directory = (build ?: NSTemporaryDirectory().trimEnd('/')) + "/ios-captures"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    val data = png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()) }
    val path = "$directory/$name.png"
    check(data.writeToFile(path, atomically = true)) { "CHARTS capture not written: $path" }
    println("CHARTS capture $path")
}
