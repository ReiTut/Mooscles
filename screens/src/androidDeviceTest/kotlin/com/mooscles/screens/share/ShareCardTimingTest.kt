package com.mooscles.screens.share

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The condition of spike S1: the real card, drawn and encoded as the app does (page by page, PNG
 * by `Bitmap.compress`), timed after a warm-up that loads the fonts and the measurer. It logs
 * `SHARE_TIMING median=<ms> runs=[...]` and asserts nothing about time: the decision rule (a
 * median above 500 ms reopens S1) is read from the log.
 */
class ShareCardTimingTest {
    private fun paintAndEncode(painter: ShareCardPainter, inputs: ShareCardInputs): Int {
        var bytes = 0
        painter.layout(longWorkoutCard(), inputs.measurer).forEach { layout ->
            val bitmap = painter.paint(layout).asAndroidBitmap()
            try {
                val out = ByteArrayOutputStream()
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                bytes += out.size()
            } finally {
                bitmap.recycle()
            }
        }
        return bytes
    }

    @Test
    fun paintAndEncodeTheLongWorkout() = runBlocking {
        val inputs = captureShareCardInputs()
        val painter = ShareCardPainter(inputs.styles)
        val times = withContext(Dispatchers.Default) {
            repeat(WARM_UP) { paintAndEncode(painter, inputs) }
            List(RUNS) {
                val start = SystemClock.elapsedRealtime()
                val bytes = paintAndEncode(painter, inputs)
                check(bytes > 0)
                SystemClock.elapsedRealtime() - start
            }
        }
        val median = times.sorted()[RUNS / 2]
        println("SHARE_TIMING median=$median runs=$times")
        assertTrue(times.size == RUNS)
    }

    private companion object {
        const val WARM_UP = 2
        const val RUNS = 7
    }
}
