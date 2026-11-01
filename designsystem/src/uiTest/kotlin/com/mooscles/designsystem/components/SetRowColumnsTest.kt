package com.mooscles.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.assertShownWhole
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.withTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The column titles over a [SetRow]: placed by the cells the row reports, and only over a one-line row. */
@OptIn(ExperimentalTestApi::class)
class SetRowColumnsTest {
    private val state = SetRowState(
        index = 4, total = 5, previous = "80 kg × 6", previousSpoken = "80 kilograms by 6",
        load = "85", loadSpoken = "85 kilograms", reps = 6, type = SetType.Normal, status = SetStatus.Pending,
    )

    /** The titles over one row [widthDp] wide, fed by the row's own report. */
    private fun ComposeUiTest.show(widthDp: Int, fontScale: Float = 1f): () -> SetRowCells? {
        var cells by mutableStateOf<SetRowCells?>(null)
        withTheme(fontScale = fontScale) {
            ProvideAppLocale("en") {
                Column(Modifier.width(widthDp.dp)) {
                    SetRowColumns("Set", "Previous", "kg", "Reps", cells)
                    SetRow(state, {}, {}, {}, {}, {}, modifier = Modifier.testTag("row"), onCells = { cells = it })
                }
            }
        }
        waitForIdle()
        return { cells }
    }

    private fun ComposeUiTest.bounds(tag: String): Rect = onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag("row")), useUnmergedTree = true)
        .fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.title(index: Int) = onNodeWithTag(setRowColumnTag(index), useUnmergedTree = true)

    @Test
    fun titlesSitOverTheCellsOfAOneLineRow() = runComposeUiTest {
        val cells = show(412)
        val reported = assertNotNull(cells())
        assertTrue(reported.oneLine, "a 412 dp row at 1x is one line: $reported")
        val previous = bounds(SetRowPreviousTag)
        val load = bounds(SetRowLoadTag)
        val reps = bounds(SetRowRepsTag)
        val titles = (0..3).map { title(it).fetchSemanticsNode() }
        for (node in titles) {
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action!!(results))
            assertShownWhole(results.first(), "column title")
        }
        val (set, prev, unit, repsTitle) = titles.map { it.boundsInRoot }
        assertTrue(set.right <= previous.left, "the set title stays over the index cell: $set, $previous")
        assertTrue(abs(prev.left - previous.left) < 1f, "previous starts with its cell: $prev, $previous")
        assertTrue(abs(unit.center.x - load.center.x) < 1f, "load centered: $unit, $load")
        assertTrue(abs(repsTitle.center.x - reps.center.x) < 1f, "reps centered: $repsTitle, $reps")
        // Not read by a screen reader: no title in the merged tree.
        assertEquals(0, onAllNodes(hasTestTag(setRowColumnTag(1))).fetchSemanticsNodes().size)
    }

    @Test
    fun nothingIsDrawnOverATwoLineRow() {
        for ((width, scale) in listOf(412 to 1.5f, 260 to 1f)) runComposeUiTest {
            val cells = show(width, scale)
            val reported = assertNotNull(cells())
            assertFalse(reported.oneLine, "$width dp at $scale is two lines")
            assertEquals(0f, onNodeWithTag(SetRowColumnsTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height, "$width dp at $scale")
            assertEquals(0, onAllNodes(hasTestTag(setRowColumnTag(0)), useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }
}
