package com.mooscles.designsystem.licenses

import com.mooscles.designsystem.licenses.LicenseBlock.Paragraph
import com.mooscles.designsystem.licenses.LicenseBlock.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class LicenseTextTest {
    @Test
    fun hardWrappedLinesOfAParagraphFlowTogether() {
        val text = "The goals of the Open Font License (OFL) are to stimulate worldwide\n" +
            "development of collaborative font projects, to support the font creation\n" +
            "efforts of academic and linguistic communities."
        assertEquals(
            listOf(
                Paragraph(
                    "The goals of the Open Font License (OFL) are to stimulate worldwide development of " +
                        "collaborative font projects, to support the font creation efforts of academic and linguistic communities.",
                ),
            ),
            licenseBlocks(text),
        )
    }

    @Test
    fun blankLinesSeparateParagraphsAndSeveralCountAsOne() {
        val text = "First paragraph,\nstill first.\n\n\nSecond, with a trailing space \nand CRLF.\r\n\r\nThird.\n"
        assertEquals(
            listOf(Paragraph("First paragraph, still first."), Paragraph("Second, with a trailing space and CRLF."), Paragraph("Third.")),
            licenseBlocks(text),
        )
    }

    @Test
    fun ruleLinesBecomeRulesAndSplitTheText() {
        val text = "https://scripts.sil.org/OFL\n\n\n" +
            "-----------------------------------------------------------\n" +
            "SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007\n" +
            "-----------------------------------------------------------\n\n" +
            "ISC text.\n\n---\n\nThe following icons"
        assertEquals(
            listOf(
                Paragraph("https://scripts.sil.org/OFL"),
                Rule,
                Paragraph("SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007"),
                Rule,
                Paragraph("ISC text."),
                Rule,
                Paragraph("The following icons"),
            ),
            licenseBlocks(text),
        )
    }

    @Test
    fun aHeadingKeepsItsOwnLineButCapitalisedProseFlows() {
        val text = "PERMISSION & CONDITIONS\nPermission is hereby granted, free of charge,\nto any person.\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\" AND THE AUTHOR DISCLAIMS ALL WARRANTIES\nWITH REGARD TO THIS SOFTWARE."
        assertEquals(
            listOf(
                Paragraph("PERMISSION & CONDITIONS\nPermission is hereby granted, free of charge, to any person."),
                Paragraph("THE SOFTWARE IS PROVIDED \"AS IS\" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE."),
            ),
            licenseBlocks(text),
        )
    }

    /** CC0's lists ("i." to "vii.", "a." to "d.") keep one item per line instead of one wall of text. */
    @Test
    fun listItemsStartTheirOwnLines() {
        val text = "  i. the right to reproduce, adapt,\n     communicate a Work;\n" +
            " ii. moral rights retained;\n" +
            "vii. other similar rights.\n\n" +
            "4. Limitations and Disclaimers.\n\n" +
            " a. No trademark rights are waived,\n    abandoned.\n" +
            " b. Affirmer offers the Work as-is.\n\n" +
            "The goals of the license, i.e. a public\ndomain, and a. b. c. inside a line stay in it."
        assertEquals(
            listOf(
                Paragraph("i. the right to reproduce, adapt, communicate a Work;\nii. moral rights retained;\nvii. other similar rights."),
                Paragraph("4. Limitations and Disclaimers."),
                Paragraph("a. No trademark rights are waived, abandoned.\nb. Affirmer offers the Work as-is."),
                Paragraph("The goals of the license, i.e. a public domain, and a. b. c. inside a line stay in it."),
            ),
            licenseBlocks(text),
        )
    }

    @Test
    fun noWordIsAddedOrLost() {
        val text = "Copyright 2020 The Inter Project Authors (https://github.com/rsms/inter)\n\n" +
            "This Font Software is licensed under the SIL Open Font License, Version 1.1.\n" +
            "This license is copied below, and is also available with a FAQ at:\nhttps://scripts.sil.org/OFL\n\n" +
            "-----\nPREAMBLE\nThe goals of the OFL,\nin short.\n"
        val words = { s: String -> s.split(Regex("\\s+")).filter { it.isNotEmpty() && it != "-----" } }
        val shown = licenseBlocks(text).filterIsInstance<Paragraph>().joinToString(" ") { it.text }
        assertEquals(words(text), words(shown))
    }
}
