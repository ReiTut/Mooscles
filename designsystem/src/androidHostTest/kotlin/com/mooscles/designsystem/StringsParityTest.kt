package com.mooscles.designsystem

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reads the string XML files of the repository, so parity holds without running any UI. */
class StringsParityTest {
    private class Entry(val kind: String, val name: String, val value: String, val items: Map<String, String>)

    private val root = File("src/commonMain/composeResources")

    private fun load(dir: String): Map<String, Entry> {
        val result = mutableMapOf<String, Entry>()
        val files = File(root, dir).listFiles { f -> f.name.startsWith("strings_") && f.name.endsWith(".xml") }.orEmpty()
        assertTrue(files.isNotEmpty(), "No strings_*.xml in $dir")
        for (file in files) {
            val text = file.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            Regex("<string\\s+name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach {
                result["string:${it.groupValues[1]}"] = Entry("string", it.groupValues[1], it.groupValues[2], emptyMap())
            }
            Regex("<plurals\\s+name=\"([^\"]+)\"[^>]*>(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).findAll(text).forEach { p ->
                val items = Regex("<item\\s+quantity=\"([^\"]+)\"[^>]*>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
                    .findAll(p.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                result["plurals:${p.groupValues[1]}"] = Entry("plurals", p.groupValues[1], "", items)
            }
        }
        return result
    }

    private val en get() = load("values")
    private val pt get() = load("values-pt-rBR")

    private fun texts(e: Entry): List<String> = if (e.kind == "string") listOf(e.value) else e.items.values.toList()

    @Test
    fun everyEnglishKeyExistsInPortugueseAndViceVersa() {
        assertEquals(en.keys, pt.keys)
    }

    @Test
    fun pluralsDefineOneAndOtherInBothLanguages() {
        for ((label, map) in listOf("en" to en, "pt" to pt)) {
            for (e in map.values.filter { it.kind == "plurals" }) {
                assertTrue("one" in e.items && "other" in e.items, "$label plurals ${e.name} needs one and other")
            }
        }
    }

    @Test
    fun placeholdersMatchPerKey() {
        val placeholder = Regex("%(\\d+\\$)?[sd]")
        val english = en
        val portuguese = pt
        for ((key, e) in english) {
            val other = portuguese[key] ?: continue
            if (e.kind == "string") {
                assertEquals(
                    placeholder.findAll(e.value).map { it.value }.toSet(),
                    placeholder.findAll(other.value).map { it.value }.toSet(), key,
                )
            } else {
                val all = { x: Entry -> x.items.values.flatMap { v -> placeholder.findAll(v).map { it.value } }.toSet() }
                assertEquals(all(e), all(other), key)
            }
        }
    }

    @Test
    fun noEmptyValues() {
        for ((label, map) in listOf("en" to en, "pt" to pt)) {
            for (e in map.values) {
                if (e.kind == "plurals") assertTrue(e.items.isNotEmpty(), "$label ${e.name} has no items")
                for (t in texts(e)) assertTrue(t.isNotBlank(), "$label ${e.name} is empty")
            }
        }
    }

    @Test
    fun copyHasNoExclamationOrEmoji() {
        for ((label, map) in listOf("en" to en, "pt" to pt)) {
            for (e in map.values) for (t in texts(e)) {
                assertTrue('!' !in t, "$label ${e.name} has an exclamation mark")
                var i = 0
                while (i < t.length) {
                    val cp = t.codePointAt(i)
                    val emoji = cp in 0x1F000..0x1FAFF || cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x2600..0x27BF || cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF || cp == 0xFE0F || cp == 0x200D
                    assertTrue(!emoji, "$label ${e.name} has an emoji")
                    i += Character.charCount(cp)
                }
            }
        }
    }
}
