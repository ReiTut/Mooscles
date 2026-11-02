package com.mooscles

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** New localized legacy labels must not silently fall back because a key/argument is missing. */
class LegacyStringsHostTest {
    private fun resources(language: String, file: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$language/$file.xml"))
        val nodes = document.documentElement.childNodes
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue ?: continue
                put(name, node.textContent)
            }
        }
    }

    private fun parity(file: String) {
        val en = resources("values", file)
        val pt = resources("values-pt-rBR", file)
        assertEquals(en.keys, pt.keys)
        val args = Regex("%[0-9]+\\$[sd]")
        for (key in en.keys) {
            assertTrue("Blank Portuguese $key", pt.getValue(key).isNotBlank())
            assertEquals("Arguments for $key", args.findAll(en.getValue(key)).map { it.value }.distinct().sorted().toList(),
                args.findAll(pt.getValue(key)).map { it.value }.distinct().sorted().toList())
        }
    }

    @Test fun existingScreenLabelsHaveMatchingPortugueseKeysAndArguments() = parity("legacy_strings")
    @Test fun existingSharePreviewHasMatchingPortugueseKeysAndArguments() = parity("share_strings")
}
