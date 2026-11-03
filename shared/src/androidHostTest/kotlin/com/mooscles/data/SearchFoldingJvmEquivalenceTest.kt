package com.mooscles.data

import java.io.File
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The common `SearchFolding.fold` against the JVM chain the app used before it:
 * NFD, drop `\p{M}`, lowercase in `Locale.ROOT`, turn every run of non `[a-z0-9]` into a space and
 * trim. The fold reads a table generated from this same chain, so these tests prove the table is
 * complete and the fold assembles it the way the chain does on whole strings.
 *
 * To regenerate the table (only when the JDK used for the build moves to a newer Unicode, which
 * changes the app's search on purpose):
 * `MOOSCLES_WRITE_FOLDING_TABLE=$PWD/shared/src/commonMain/kotlin/com/mooscles/data/SearchFoldingTable.kt
 *  ./gradlew :shared:testAndroidHostTest --tests "*SearchFoldingJvmEquivalenceTest.writeTableWhenRequested"`
 */
class SearchFoldingJvmEquivalenceTest {
    @Test
    fun foldEqualsNormalizerForEveryCodePoint() {
        var checked = 0
        val mismatches = mutableListOf<String>()
        for (codePoint in 0..0x10FFFF) {
            if (codePoint in 0xD800..0xDFFF) continue
            val alone = String(Character.toChars(codePoint))
            for (input in listOf(alone, "a${alone}b")) {
                val expected = legacyFold(input)
                val actual = SearchFolding.fold(input)
                if (actual != expected) mismatches += "U+%04X in \"%s\": fold \"%s\", JVM \"%s\"".format(codePoint, input, actual, expected)
                checked++
            }
        }
        assertEquals(2 * (0x110000 - 0x800), checked)
        assertEquals(0, mismatches.size, "fold differs from the JVM:\n" + mismatches.take(20).joinToString("\n"))
    }

    @Test
    fun foldEqualsNormalizerForLoneSurrogates() {
        val mismatches = (0xD800..0xDFFF).flatMap { unit ->
            val alone = unit.toChar().toString()
            listOf(alone, "a${alone}b", "${alone}${alone}", "a\uD83D${alone}").filter { SearchFolding.fold(it) != legacyFold(it) }
        }
        assertEquals(emptyList(), mismatches)
    }

    @Test
    fun foldEqualsNormalizerForCombiningSequences() {
        val letters = ((0x0041..0x024F) + (0x1E00..0x1EFF)).filter(Character::isLetter)
        var checked = 0
        val mismatches = mutableListOf<String>()
        for (letter in letters) for (mark in 0x0300..0x036F) {
            val input = String(Character.toChars(letter)) + mark.toChar()
            for (text in listOf(input, "x$input y", input + input)) {
                if (SearchFolding.fold(text) != legacyFold(text)) mismatches += "U+%04X U+%04X".format(letter, mark)
                checked++
            }
        }
        assertEquals(3 * letters.size * 0x70, checked)
        assertEquals(0, mismatches.size, "fold differs from the JVM:\n" + mismatches.take(20).joinToString("\n"))
    }

    @Test
    fun foldEqualsNormalizerForRandomStrings() {
        val pool = randomPool()
        val random = Random(1027)
        val mismatches = mutableListOf<String>()
        repeat(50_000) {
            val text = buildString {
                repeat(random.nextInt(1, 25)) { appendCodePoint(pool[random.nextInt(pool.size)]) }
            }
            if (SearchFolding.fold(text) != legacyFold(text)) {
                mismatches += text.map { "U+%04X".format(it.code) }.joinToString(" ")
            }
        }
        assertEquals(0, mismatches.size, "fold differs from the JVM:\n" + mismatches.take(20).joinToString("\n"))
    }

    @Test
    fun fixedCommonCasesAreWhatTheJvmGives() {
        SearchFoldingTest.cases.forEach { (input, expected) -> assertEquals(legacyFold(input), expected, "case \"$input\"") }
    }

    @Test
    fun tableHeaderRecordsTheUnicodeVersionOfThisJdk() {
        assertEquals(jdkUnicodeVersion(), SearchFoldingTable.UNICODE_VERSION)
    }

    @Test
    fun tableEqualsAFreshGeneration() {
        val (starts, ends, replacements) = generateRanges()
        assertEquals(starts.toList(), SearchFoldingTable.starts.toList())
        assertEquals(ends.toList(), SearchFoldingTable.ends.toList())
        assertEquals(replacements, SearchFoldingTable.replacements.toList())
    }

    /** Writes the table source only when `MOOSCLES_WRITE_FOLDING_TABLE=<file>` is set. */
    @Test
    fun writeTableWhenRequested() {
        val target = System.getenv("MOOSCLES_WRITE_FOLDING_TABLE")?.takeIf(String::isNotBlank) ?: return
        File(target).writeText(tableSource())
    }

    private fun StringBuilder.appendCodePoint(codePoint: Int) {
        if (codePoint in 0xD800..0xDFFF) append(codePoint.toChar()) else append(Character.toChars(codePoint))
    }

    private fun randomPool(): List<Int> = buildList {
        addAll(0x20..0x7E) // printable ASCII
        addAll(0xA0..0xFF) // Latin-1
        addAll(0x0100..0x024F) // Latin Extended-A and -B
        addAll(0x1E00..0x1EFF) // Latin Extended Additional
        addAll(0x0300..0x036F) // combining marks
        addAll(0x0370..0x03FF) // Greek
        addAll(0x0400..0x04FF) // Cyrillic
        addAll(0xAC00..0xAC00 + 400) // Hangul syllables
        addAll(0x4E00..0x4E00 + 400) // CJK
        addAll(0x1F300..0x1F64F) // emoji, written as surrogate pairs
        addAll(listOf(0xD800, 0xDBFF, 0xDC00, 0xDFFF)) // lone surrogates
        add(0xE0100) // variation selector 17
        addAll(listOf(0x0130, 0x212A, 0x212B, 0x03A3, 0x00DF, 0xFB01, 0xFF30))
    }

    private fun jdkUnicodeVersion(): String {
        // Normalizer and the regex classes share one Unicode version per JDK release.
        val feature = jdkFeature()
        return UNICODE_BY_JDK[feature] ?: error("Unknown Unicode version for JDK $feature; add it to UNICODE_BY_JDK")
    }

    // Runtime.version() is not in the Android stubs this source set compiles against.
    private fun jdkFeature(): String = System.getProperty("java.specification.version").orEmpty()

    private fun tableSource(): String {
        val (starts, ends, replacements) = generateRanges()
        // Spaces inside a replacement are written as '_', so each line splits on its first space.
        val data = starts.indices.joinToString("\n") { i ->
            val range = if (starts[i] == ends[i]) "%04X".format(starts[i]) else "%04X-%04X".format(starts[i], ends[i])
            if (replacements[i].isEmpty()) range else range + " " + replacements[i].replace(' ', '_')
        }
        val unicode = jdkUnicodeVersion()
        val q = "\"\"\""
        return """
            |// GENERATED FILE. Do not edit by hand.
            |//
            |// Generated by SearchFoldingJvmEquivalenceTest.writeTableWhenRequested from the JDK's Normalizer
            |// on JDK ${jdkFeature()} (Unicode $unicode). To regenerate, run from the repository root:
            |// MOOSCLES_WRITE_FOLDING_TABLE=${'$'}PWD/shared/src/commonMain/kotlin/com/mooscles/data/SearchFoldingTable.kt \
            |//   ./gradlew :shared:testAndroidHostTest --tests "*SearchFoldingJvmEquivalenceTest.writeTableWhenRequested"
            |//
            |// Each line of DATA is a code point or an inclusive range (hex), then what it folds to: nothing
            |// (a combining mark) or ASCII letters and digits, with '_' standing for a separator. A code point
            |// outside ASCII and outside every range is a separator. ${replacements.size} ranges.
            |package com.mooscles.data
            |
            |internal object SearchFoldingTable {
            |    const val UNICODE_VERSION = "$unicode"
            |
            |    private val lines = DATA.trim().lines()
            |    val starts = IntArray(lines.size) { lines[it].substringBefore(' ').substringBefore('-').toInt(16) }
            |    val ends = IntArray(lines.size) { lines[it].substringBefore(' ').substringAfter('-').toInt(16) }
            |    val replacements = Array(lines.size) { lines[it].substringAfter(' ', "").replace('_', ' ') }
            |}
            |
            |private const val DATA = $q
            |
        """.trimMargin() + data + "\n" + q + "\n"
    }

    companion object {
        private val UNICODE_BY_JDK = mapOf("17" to "13.0", "21" to "15.0", "24" to "16.0", "25" to "16.0")

        /**
         * The search normalization before this module, kept verbatim as the reference. It is copied
         * on purpose in ExerciseSearchEquivalenceTest too, so each test stands on its own reference.
         */
        fun legacyFold(value: String): String = Regex("[^a-z0-9]+").replace(
            Regex("\\p{M}+").replace(Normalizer.normalize(value, Normalizer.Form.NFD), "").lowercase(Locale.ROOT), " "
        ).trim()

        /**
         * What the legacy chain makes of one code point alone, with every non `[a-z0-9]` character
         * kept as a single space so a separator inside a replacement survives. ASCII is left out:
         * the fold handles it in code, and the every-code-point test covers it.
         */
        fun generateRanges(): Triple<IntArray, IntArray, List<String>> {
            val starts = mutableListOf<Int>()
            val ends = mutableListOf<Int>()
            val replacements = mutableListOf<String>()
            for (codePoint in 0x80..0x10FFFF) {
                if (codePoint in 0xD800..0xDFFF) continue
                val chain = Regex("\\p{M}+").replace(
                    Normalizer.normalize(String(Character.toChars(codePoint)), Normalizer.Form.NFD), ""
                ).lowercase(Locale.ROOT)
                val folded = Regex("[^a-z0-9]+").replace(chain, " ")
                if (folded == " ") continue // a separator, the default
                if (replacements.isNotEmpty() && ends.last() == codePoint - 1 && replacements.last() == folded) {
                    ends[ends.lastIndex] = codePoint
                } else {
                    starts += codePoint; ends += codePoint; replacements += folded
                }
            }
            return Triple(starts.toIntArray(), ends.toIntArray(), replacements)
        }
    }
}
