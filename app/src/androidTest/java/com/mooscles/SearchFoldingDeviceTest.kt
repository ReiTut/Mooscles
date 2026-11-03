package com.mooscles

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mooscles.data.SearchFolding
import java.text.Normalizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The common `SearchFolding.fold` against ART's `Normalizer` chain, which the app used before.
 * The table was generated on the JVM (Unicode 15.0); a difference here means the device's ICU
 * folds some character differently from what the app now ships.
 */
@RunWith(AndroidJUnit4::class)
class SearchFoldingDeviceTest {
    @Test
    fun foldEqualsArtNormalizerForEveryBmpCodePoint() {
        var checked = 0
        val mismatches = mutableListOf<String>()
        for (codePoint in 0..0xFFFF) {
            if (codePoint in 0xD800..0xDFFF) continue
            val alone = codePoint.toChar().toString()
            for (input in listOf(alone, "a${alone}b")) {
                val expected = legacyFold(input)
                val actual = SearchFolding.fold(input)
                if (actual != expected) mismatches += "U+%04X in \"%s\": fold \"%s\", ART \"%s\"".format(codePoint, input, actual, expected)
                checked++
            }
        }
        assertEquals(2 * (0x10000 - 0x800), checked)
        assertEquals("fold differs from ART:\n" + mismatches.take(20).joinToString("\n"), 0, mismatches.size)
    }

    /** The search normalization before SearchFolding, verbatim. */
    private fun legacyFold(value: String): String = Regex("[^a-z0-9]+").replace(
        Regex("\\p{M}+").replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT), " "
    ).trim()
}
