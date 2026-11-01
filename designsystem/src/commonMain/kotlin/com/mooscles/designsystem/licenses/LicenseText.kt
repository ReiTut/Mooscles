package com.mooscles.designsystem.licenses

/** One block of a license as the screen shows it. */
sealed interface LicenseBlock {
    /** Running text. A line break is kept only after a heading line such as "PREAMBLE". */
    data class Paragraph(val text: String) : LicenseBlock

    /** A line made only of `-`, `=`, `_` or `*` in the file, drawn as a divider. */
    data object Rule : LicenseBlock
}

private val ruleLine = Regex("""[-=_*]{3,}""")

/** A short all-capitals title on a line of its own, such as "PERMISSION & CONDITIONS". */
private val headingLine = Regex("""[A-Z][A-Z &]{2,39}""")

/** A line that starts a list item, such as CC0's "iv. rights protecting…" or "b. Affirmer offers…". */
private val listItemLine = Regex("""(?:[ivx]{1,4}|[a-z])\. .*""")

/**
 * Splits a license file into blocks for a narrow screen. The files are hard-wrapped near 72
 * columns, which a phone wraps again into ragged lines, so the lines of a paragraph are joined
 * into flowing text. Blank lines still separate paragraphs (several count as one), a rule line
 * becomes [LicenseBlock.Rule] instead of wrapping onto a second line, and a heading line and each
 * item of a list ("i." to "vii.", "a." to "d.") keep their own line. The words are not changed.
 */
fun licenseBlocks(text: String): List<LicenseBlock> {
    val blocks = mutableListOf<LicenseBlock>()
    val lines = mutableListOf<String>()
    fun flush() {
        if (lines.isEmpty()) return
        val paragraph = StringBuilder()
        lines.forEachIndexed { index, line ->
            if (index > 0) {
                val ownLine = headingLine.matches(lines[index - 1]) || listItemLine.matches(line)
                paragraph.append(if (ownLine) "\n" else " ")
            }
            paragraph.append(line)
        }
        blocks += LicenseBlock.Paragraph(paragraph.toString())
        lines.clear()
    }
    for (raw in text.replace("\r\n", "\n").split('\n')) {
        val line = raw.trim()
        when {
            line.isEmpty() -> flush()
            ruleLine.matches(line) -> {
                flush()
                blocks += LicenseBlock.Rule
            }
            else -> lines += line
        }
    }
    flush()
    return blocks
}
