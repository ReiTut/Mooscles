package com.mooscles.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconsTest {
    private fun paths(node: VectorNode): List<VectorPath> = when (node) {
        is VectorPath -> listOf(node)
        is VectorGroup -> node.flatMap { paths(it) }
    }

    private fun pathsOf(icon: ImageVector): List<VectorPath> = paths(icon.root)

    @Test
    fun everyIconIsOnTheTwentyFourGrid() {
        for ((name, icon) in MIcons.all) {
            assertEquals(24.dp, icon.defaultWidth, name)
            assertEquals(24.dp, icon.defaultHeight, name)
            assertEquals(24f, icon.viewportWidth, name)
            assertEquals(24f, icon.viewportHeight, name)
        }
    }

    private val outlineIcons get() = MIcons.all.filterNot { it.first.endsWith("Filled") }
    private val filledIcons get() = MIcons.all.filter { it.first.endsWith("Filled") }

    /** The outline icons (all but the `*Filled` variants): every path a stroke of 2, round, never filled. */
    @Test
    fun everyPathStrokesTwoWithRoundCapsAndJoinsAndNeverFills() {
        for ((name, icon) in outlineIcons) {
            val all = pathsOf(icon)
            assertTrue(all.isNotEmpty(), "$name has no path")
            for (path in all) {
                assertEquals(2f, path.strokeLineWidth, name)
                assertEquals(StrokeCap.Round, path.strokeLineCap, name)
                assertEquals(StrokeJoin.Round, path.strokeLineJoin, name)
                assertNull(path.fill, "$name fills a path")
            }
        }
    }

    /** Ruling F39: the outline icons are tinted through their black stroke. */
    @Test
    fun everyIconIsTintable() {
        for ((name, icon) in outlineIcons) {
            for (path in pathsOf(icon)) {
                val stroke = assertNotNull(path.stroke, name)
                assertEquals(SolidColor(Color.Black), stroke, name)
            }
        }
    }

    /**
     * Only the four `*Filled` variants fill, and each has a fill. A fill path is solid black with no
     * stroke (so it tints like the rest); any other path of theirs is the usual round stroke of 2.
     */
    @Test
    fun onlyFilledVariantsFill() {
        assertEquals(listOf("DumbbellFilled", "LayoutGridFilled", "HistoryFilled", "PlateRecordFilled"), filledIcons.map { it.first })
        for ((name, icon) in MIcons.all) {
            val fills = pathsOf(icon).filter { it.fill != null }
            if (name.endsWith("Filled")) assertTrue(fills.isNotEmpty(), "$name has no fill") else assertTrue(fills.isEmpty(), "$name fills a path")
        }
        for ((name, icon) in filledIcons) {
            for (path in pathsOf(icon)) {
                if (path.fill != null) {
                    assertEquals(SolidColor(Color.Black), path.fill, name)
                    assertNull(path.stroke, "$name strokes a fill")
                } else {
                    assertEquals(SolidColor(Color.Black), path.stroke, name)
                    assertEquals(2f, path.strokeLineWidth, name)
                    assertEquals(StrokeCap.Round, path.strokeLineCap, name)
                    assertEquals(StrokeJoin.Round, path.strokeLineJoin, name)
                }
            }
        }
    }

    @Test
    fun iconListIsExactly() {
        val expected = listOf(
            "ArrowLeft", "Check", "X", "Plus", "Minus", "ChevronRight", "ChevronDown", "ChevronUp",
            "EllipsisVertical", "CircleAlert", "CircleCheck", "Info", "Lock", "Sun", "Moon",
            "MonitorSmartphone", "Download", "Upload", "RefreshCw", "Timer", "SkipForward", "Pause",
            "Play", "Trash2", "Undo2", "FileText", "Wifi", "ImageOff",
            "History", "LayoutGrid", "Search", "Keyboard", "Share2", "Pencil", "ArrowUp", "ArrowDown",
            "ArrowLeftRight",
            // Phase 4A: the library.
            "Settings", "SearchX",
            // Phase 4B: the drag handle, and (Task 19) the routine menus' duplicate, move to folder, top and end.
            "GripVertical", "Copy", "FolderInput", "ArrowUpToLine", "ArrowDownToLine",
            // Phase 5A (plan decision 14): the chart's "View data" and "View chart".
            "Table", "ChartLine",
            "Plate", "PlateRecord", "Dumbbell", "Barbell", "Kettlebell", "Bench", "Cable", "Machine",
            "Rope", "Body",
            "DumbbellFilled", "LayoutGridFilled", "HistoryFilled", "PlateRecordFilled",
        )
        assertEquals(expected, MIcons.all.map { it.first })
        for ((name, icon) in MIcons.all) assertEquals(name, icon.name)
        assertEquals(60, expected.size)
        assertEquals(60, MIcons.all.map { it.second }.toSet().size)
    }
}
