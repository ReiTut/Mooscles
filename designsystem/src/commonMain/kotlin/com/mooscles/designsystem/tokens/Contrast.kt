package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.2 relative luminance and contrast ratio, for opaque sRGB colors only. */
object Contrast {
    /** Relative luminance of an opaque color (WCAG 2.2, linearization threshold 0.04045). */
    fun relativeLuminance(c: Color): Double {
        require(c.alpha == 1f) { "Contrast needs opaque colors; got alpha ${c.alpha}" }
        return 0.2126 * linear(c.red) + 0.7152 * linear(c.green) + 0.0722 * linear(c.blue)
    }

    /** Contrast ratio between two opaque colors, from 1.0 (same) to 21.0 (black on white). */
    fun ratio(a: Color, b: Color): Double {
        require(a.alpha == 1f && b.alpha == 1f) { "Contrast needs opaque colors" }
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun linear(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
