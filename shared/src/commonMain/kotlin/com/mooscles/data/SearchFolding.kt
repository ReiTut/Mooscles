package com.mooscles.data

/**
 * Folds text for the exercise search: accents and other combining marks are dropped, Latin letters
 * become lowercase ASCII, and every run of anything else becomes one space, trimmed at the ends.
 *
 * It gives, for every string, what the app computed on the JVM before this module existed:
 * NFD with the JDK's `Normalizer`, `\p{M}` removed, `lowercase(Locale.ROOT)`, `[^a-z0-9]+` turned
 * into a space and `trim()`. Outside ASCII it reads [SearchFoldingTable], generated from that chain
 * one code point at a time (Unicode 15.0, JDK 21), so the search behaves the same on every platform.
 */
object SearchFolding {
    fun fold(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val unit = value[index]
            val codePoint: Int
            if (unit.isHighSurrogate() && index + 1 < value.length && value[index + 1].isLowSurrogate()) {
                codePoint = 0x10000 + ((unit.code - 0xD800) shl 10) + (value[index + 1].code - 0xDC00)
                index += 2
            } else {
                codePoint = unit.code // a lone surrogate falls through to a separator
                index++
            }
            when (codePoint) {
                in 'a'.code..'z'.code, in '0'.code..'9'.code -> out.append(codePoint.toChar())
                in 'A'.code..'Z'.code -> out.append((codePoint + ('a' - 'A')).toChar())
                in 0..0x7F -> out.separator()
                else -> {
                    val replacement = replacementOf(codePoint)
                    if (replacement == null) {
                        out.separator()
                    } else {
                        for (char in replacement) if (char == ' ') out.separator() else out.append(char)
                    }
                }
            }
        }
        if (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        return out.toString()
    }

    /** A space between words: never at the start, never two in a row. */
    private fun StringBuilder.separator() {
        if (isNotEmpty() && this[length - 1] != ' ') append(' ')
    }

    /** The table's replacement for [codePoint], or null when it is a separator. */
    private fun replacementOf(codePoint: Int): String? {
        val starts = SearchFoldingTable.starts
        var low = 0
        var high = starts.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                starts[middle] > codePoint -> high = middle - 1
                SearchFoldingTable.ends[middle] < codePoint -> low = middle + 1
                else -> return SearchFoldingTable.replacements[middle]
            }
        }
        return null
    }
}
