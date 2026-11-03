package com.mooscles.designsystem

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** The emulator's test input sends a real pointer cancel. */
internal const val INJECTS_TOUCH_CANCEL = true

/** The emulator's test input drives a long press and a drag (phase 4B spike S2). */
internal const val INJECTS_DRAG = true

/**
 * Puts the emulator back in touch mode after a test that asked for keyboard mode
 * (`InputModeManager.requestInputMode(Keyboard)`). Touch mode is the display's, not the test's:
 * left off, a cleared focus goes back to the first focusable in the tests that run after
 * (`FocusRingTest` failed that way in the full suite), and common code cannot turn it back on.
 */
internal fun restoreTouchMode() {
    InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
}

/**
 * The emulator's side of the chart captures (the iOS one, in its TestPlatform.kt, writes the PNG
 * for the CI artifact; not expect/actual). Here the PNG is written to the test app's external files
 * and copied by the shell to `/data/local/tmp/<name>.png`, which outlives the test APK, so
 * `adb pull` can fetch it for the spike report.
 */
internal fun saveChartsCapture(name: String, image: ImageBitmap) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val directory = checkNotNull(instrumentation.targetContext.getExternalFilesDir(null)) { "No external files directory" }
    val file = File(directory, "$name.png")
    file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    // The shell reads the app's external files; the copy is what stays after the APK is gone.
    val copy = "cp ${file.absolutePath} /data/local/tmp/$name.png && echo copied"
    val output = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(copy))
        .use { it.readBytes().decodeToString() }
    // A failed copy does not fail the test, but says so, so a missing PNG is plain in the log.
    if ("copied" in output) println("CHARTS capture /data/local/tmp/$name.png") else println("CHARTS capture copy failed for $name: $output")
}
