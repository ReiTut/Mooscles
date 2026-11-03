package com.mooscles.designsystem

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.mooscles.designsystem.tokens.interFamily
import com.mooscles.designsystem.tokens.numberFamily
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tabular figures and the Inter weight axis, measured with the platform text stack (the
 * phase 2 spike, now against the design system families). Runs on the iOS simulator
 * (iosSimulatorArm64Test) and on the emulator (connectedAndroidDeviceTest), so a font swap
 * that loses `tnum` or the `wght` axis fails there. Widths are in px at Density(2f), 48 sp.
 */
class TnumRegressionTest {

    @Test
    fun interWithoutTnumIsProportional() {
        val (ones, eights) = measure(
            Request("1111", Face.INTER, 400, tnum = false),
            Request("8888", Face.INTER, 400, tnum = false),
        )
        assertTrue(abs(ones - eights) > TOLERANCE, "Inter 400 without tnum should be proportional: $ones vs $eights")
    }

    @Test
    fun barlowIsReallyLoaded() {
        val (barlow, inter, default) = measure(
            Request("8888", Face.NUMBER, 600, tnum = false),
            Request("8888", Face.INTER, 600, tnum = false),
            Request("8888", Face.DEFAULT, 600, tnum = false),
        )
        assertTrue(barlow < 0.85f * inter, "Barlow 600 ($barlow) should be narrower than 0.85 x Inter 600 ($inter)")
        assertTrue(abs(barlow - default) > TOLERANCE, "Barlow 600 ($barlow) matches FontFamily.Default ($default)")
    }

    @Test
    fun interWithTnumIsTabular() {
        assertTabular(Face.INTER, listOf(400, 500, 600, 700))
    }

    @Test
    fun barlowWithTnumIsTabular() {
        assertTabular(Face.NUMBER, listOf(500, 600, 700))
    }

    @Test
    fun barlowTimerKeepsWidthWithTnum() {
        val (zero, nine) = measure(
            Request("0:00", Face.NUMBER, 600, tnum = true),
            Request("9:59", Face.NUMBER, 600, tnum = true),
        )
        assertTrue(abs(zero - nine) <= TOLERANCE, "Barlow 600 timer changes width with tnum: $zero vs $nine")
    }

    @Test
    fun interWeightAxisApplies() {
        val (regular, bold) = measure(
            Request("8888", Face.INTER, 400, tnum = false),
            Request("8888", Face.INTER, 700, tnum = false),
        )
        assertTrue(abs(regular - bold) > TOLERANCE, "Inter wght axis did not change the width: $regular vs $bold")
    }

    /** Every digit, repeated four times ("0000" to "9999"), has the same width with `tnum` at each weight. */
    private fun assertTabular(face: Face, weights: List<Int>) {
        val texts = DIGITS.map { it.toString().repeat(4) }
        val requests = weights.flatMap { w -> texts.map { Request(it, face, w, tnum = true) } }
        val widths = measure(*requests.toTypedArray())
        val failures = weights.mapIndexedNotNull { i, w ->
            val row = widths.subList(i * texts.size, (i + 1) * texts.size)
            if (row.max() - row.min() > TOLERANCE) "$face $w: " + texts.zip(row).joinToString { (t, v) -> "$t=$v" } else null
        }
        assertTrue(failures.isEmpty(), "Digits differ in width with tnum: $failures")
    }

    private enum class Face { INTER, NUMBER, DEFAULT }

    private class Request(val text: String, val face: Face, val weight: Int, val tnum: Boolean)

    /** Measures every request in one composition and returns the unconstrained line widths in px. */
    @OptIn(ExperimentalTestApi::class)
    private fun measure(vararg requests: Request): List<Float> {
        var widths: List<Float> = emptyList()
        runComposeUiTest {
            setContent {
                val measurer = rememberTextMeasurer()
                val inter = interFamily()
                val number = numberFamily()
                widths = requests.map { request ->
                    val family = when (request.face) {
                        Face.INTER -> inter
                        Face.NUMBER -> number
                        Face.DEFAULT -> FontFamily.Default
                    }
                    measurer.measure(
                        request.text,
                        TextStyle(
                            fontFamily = family,
                            fontWeight = FontWeight(request.weight),
                            fontSize = 48.sp,
                            fontFeatureSettings = if (request.tnum) "tnum" else null,
                        ),
                        density = Density(2f),
                    ).multiParagraph.intrinsics.maxIntrinsicWidth
                }
            }
            // v2 queues the composition instead of running it inside setContent.
            waitForIdle()
        }
        check(widths.size == requests.size) { "Measured ${widths.size} of ${requests.size} texts" }
        return widths
    }

    private companion object {
        const val DIGITS = "0123456789"
        const val TOLERANCE = 0.5f
    }
}
