package com.mooscles

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.mooscles.designsystem.licenses.LicenseRuleTag
import com.mooscles.designsystem.licenses.LicensesScreen
import com.mooscles.ui.MoosclesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Critique #7: the bundled license files are shown as flowing paragraphs, with rules drawn as dividers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LicensesScreenHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun hardWrappedLinesFlowAndRulesBecomeDividers() {
        compose.setContent { MoosclesTheme("light") { LicensesScreen(onBack = {}) } }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("SIL OPEN FONT LICENSE", substring = true)).fetchSemanticsNodes().size >= 2
        }
        // The OFL preamble was five lines in the file; it is one paragraph now, after its heading
        // (once for Inter and once for Barlow Condensed).
        val preamble = hasText(
            "PREAMBLE\nThe goals of the Open Font License (OFL) are to stimulate worldwide development of collaborative " +
                "font projects, to support the font creation efforts",
            substring = true,
        )
        assertEquals(2, compose.onAllNodes(preamble).fetchSemanticsNodes().size)
        compose.onNode(hasText("Permission to use, copy, modify, and/or distribute this software for any purpose", substring = true))
            .assertExists()
        // Two rules in each OFL file, one in the Lucide file, two in the MuscleMap file (after its
        // preface and inside the source LICENSE) and one in the CC0 file (after its preface).
        assertEquals(8, compose.onAllNodesWithTag(LicenseRuleTag).fetchSemanticsNodes().size)
        // No text keeps a rule's dashes.
        assertEquals(0, compose.onAllNodes(hasText("---", substring = true)).fetchSemanticsNodes().size)
    }
}
