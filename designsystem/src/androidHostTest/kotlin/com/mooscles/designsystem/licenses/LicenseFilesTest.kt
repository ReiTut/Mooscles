package com.mooscles.designsystem.licenses

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The real license files of phase 4A, read from the repository (JVM only: `commonTest` runs on the
 * iOS simulator too, where these files are not at hand). Each becomes blocks without losing a word.
 */
class LicenseFilesTest {
    private val licenses = File("src/commonMain/composeResources/files/licenses")

    /** The file's text with LF line ends, whatever the checkout did to them (`text=auto` on Windows). */
    private fun read(file: File) = file.readText().replace("\r\n", "\n")

    private fun words(text: String) = text.split(Regex("\\s+")).filter { it.isNotEmpty() && !Regex("[-=_*]{3,}").matches(it) }

    private fun assertKeepsItsWords(text: String) {
        val shown = licenseBlocks(text).filterIsInstance<LicenseBlock.Paragraph>().joinToString(" ") { it.text }
        assertEquals(words(text), words(shown))
    }

    private fun sha256(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    /** The body map's notice: where it comes from, then MuscleMap's LICENSE exactly as the source ships it. */
    @Test
    fun muscleMapLicenseKeepsItsWords() {
        val text = read(File(licenses, "musclemap_mit.txt"))
        assertKeepsItsWords(text)
        assertTrue("170cfacda79376dd59ac0cf2f7dfab8ff9f4443a" in text, "the source commit")
        assertTrue("https://github.com/Jsplice/MuscleMap" in text, "the source repository")
        val source = read(File("illustrations/musclemap/LICENSE"))
        assertEquals("f7347b3759d6cb8c4a27d229a446aaed2af50e23012755615b98eb874af38a3d", sha256(source), "the LICENSE copied from the source")
        assertTrue(text.endsWith("\n\n-----\n\n$source"), "the notice ends with the source LICENSE, unchanged")
        assertTrue("traced by hand in Inkscape" in text.replace(Regex("\\s+"), " "), "the preface says the paths are hand-traced")
        assertTrue("MIT License" in source && "Permission is hereby granted, free of charge" in source)
    }

    /** The own media's notice: what it covers, then the official CC0 1.0 legal code, unchanged. */
    @Test
    fun mediaLicenseKeepsItsWords() {
        val text = read(File(licenses, "media-CC0.md"))
        assertKeepsItsWords(text)
        val (wrapped, legalCode) = text.split("\n\n-----\n\n", limit = 2)
        // The preface may be re-wrapped: compare with single spaces.
        val preface = wrapped.replace(Regex("\\s+"), " ")
        for (covered in listOf("exercise figures", "poses", "exercise tips", "Brazilian Portuguese exercise names")) {
            assertTrue(covered in preface, "the preface covers $covered")
        }
        assertTrue("The body map is not covered" in preface)
        // creativecommons.org/publicdomain/zero/1.0/legalcode.txt, fetched 2026-10-29.
        assertEquals("a2010f343487d3f7618affe54f789f5487602331c0a8d03f49e9a7c547cf0499", sha256(legalCode))
        assertTrue(legalCode.startsWith("Creative Commons Legal Code\n\nCC0 1.0 Universal\n"))
    }
}
