package com.mooscles.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fixed cases of `SearchFolding.fold`, run on every platform. The expected values are what the
 * JVM's `Normalizer` chain gives; `SearchFoldingJvmEquivalenceTest` checks them against it.
 */
class SearchFoldingTest {
    @Test
    fun foldMatchesTheJvmTable() {
        cases.forEach { (input, expected) ->
            assertEquals(expected, SearchFolding.fold(input), "fold of \"$input\"")
        }
    }

    @Test
    fun tableRangesAreSortedAndDisjointOutsideAscii() {
        val starts = SearchFoldingTable.starts
        val ends = SearchFoldingTable.ends
        assertEquals(starts.size, ends.size)
        assertEquals(starts.size, SearchFoldingTable.replacements.size)
        assertTrue(starts.isNotEmpty())
        assertTrue(starts.first() >= 0x80, "ASCII is folded in code, not by the table")
        assertTrue(ends.last() <= 0x10FFFF)
        for (i in starts.indices) {
            assertTrue(starts[i] <= ends[i], "range $i is empty")
            if (i > 0) assertTrue(ends[i - 1] < starts[i], "range $i overlaps or is out of order")
        }
    }

    companion object {
        val cases: List<Pair<String, String>> = listOf(
            "Élévation" to "elevation",
            "Café crème" to "cafe creme",
            "é" to "e",
            "é" to "e",
            "İstanbul" to "istanbul",
            "Kettlebell" to "kettlebell",
            "Ångström" to "angstrom",
            "Straße" to "stra e",
            "ﬁ" to "",
            "Ｐｕｌｌ" to "",
            "한국" to "",
            "pull-up_2x" to "pull up 2x",
            "  Supino   reto  " to "supino reto",
            "a󠄀b" to "ab",
            "a ́ b" to "a b",
            "\uD800" to "",
            "a\uDC00b" to "a b",
            "💪 curl" to "curl",
            "" to "",
        )
    }
}
