package com.mooscles.designsystem.illustration

import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The generated body map data (BodyMapPaths.kt, from designsystem/illustrations/musclemap). */
class BodyMapPathsTest {
    private val views = listOf(
        "front" to (BodyMapPaths.frontOutline to BodyMapPaths.front),
        "back" to (BodyMapPaths.backOutline to BodyMapPaths.back),
    )

    private val everyPath: List<Pair<String, String>> = views.flatMap { (view, data) ->
        data.first.map { "$view outline" to it } +
            data.second.flatMap { (region, paths) -> paths.map { "$view $region" to it } }
    }

    @Test
    fun everyRegionHasPathsInSomeView() {
        for (region in BodyRegion.entries) {
            val front = bodyMapPathData(front = true, region)
            val back = bodyMapPathData(front = false, region)
            assertTrue(front.isNotEmpty() || back.isNotEmpty(), "$region has no paths")
        }
        assertEquals(17, BodyRegion.entries.size)
        // The spike's grouping (Task 1 report): paths per region, front and back.
        val counts = BodyRegion.entries.associateWith { bodyMapPathData(true, it).size to bodyMapPathData(false, it).size }
        assertEquals(
            mapOf(
                BodyRegion.ABDOMINALS to (4 to 2), BodyRegion.ABDUCTORS to (2 to 2), BodyRegion.ADDUCTORS to (2 to 2),
                BodyRegion.BICEPS to (2 to 0), BodyRegion.CALVES to (2 to 2), BodyRegion.CHEST to (2 to 0),
                BodyRegion.FOREARMS to (2 to 2), BodyRegion.GLUTES to (0 to 2), BodyRegion.HAMSTRINGS to (0 to 2),
                BodyRegion.LATS to (0 to 2), BodyRegion.LOWER_BACK to (0 to 2), BodyRegion.MIDDLE_BACK to (0 to 2),
                BodyRegion.NECK to (2 to 4), BodyRegion.QUADRICEPS to (4 to 0), BodyRegion.SHOULDERS to (4 to 4),
                BodyRegion.TRAPS to (2 to 4), BodyRegion.TRICEPS to (2 to 2),
            ),
            counts,
        )
        assertEquals(1, BodyMapPaths.frontOutline.size)
        assertEquals(1, BodyMapPaths.backOutline.size)
    }

    /** The source has no neck surface: the neck is the trapezius paths (spike decision), with none of its own. */
    @Test
    fun neckFollowsTheSpikeDecision() {
        assertFalse(BodyRegion.NECK in BodyMapPaths.front || BodyRegion.NECK in BodyMapPaths.back, "the data has NECK paths")
        for (front in listOf(true, false)) {
            assertEquals(bodyMapPathData(front, BodyRegion.TRAPS), bodyMapPathData(front, BodyRegion.NECK))
        }
        assertEquals(BodyRegion.TRAPS, BodyMapGeometry.drawnAs(BodyRegion.NECK))
        for (region in BodyRegion.entries - BodyRegion.NECK) assertEquals(region, BodyMapGeometry.drawnAs(region))
    }

    @Test
    fun everyPathParses() {
        assertEquals(60, everyPath.size)
        for ((where, d) in everyPath) {
            val nodes = PathParser().parsePathString(d).toNodes()
            assertTrue(nodes.isNotEmpty(), "$where does not parse")
            assertTrue(nodes.first() is PathNode.MoveTo, "$where does not start with a move")
            assertTrue(nodes.last() is PathNode.Close, "$where is not closed")
        }
    }

    @Test
    fun pathsStayInsideTheViewBox() {
        assertEquals(1024f, BodyMapPaths.VIEW_WIDTH)
        assertEquals(1536f, BodyMapPaths.VIEW_HEIGHT)
        assertEquals(2f * 1024f / 1536f, BodyMapAspectRatio)
        for ((where, d) in everyPath) {
            for (node in PathParser().parsePathString(d).toNodes()) {
                val points = when (node) {
                    is PathNode.MoveTo -> listOf(node.x to node.y)
                    is PathNode.LineTo -> listOf(node.x to node.y)
                    is PathNode.CurveTo -> listOf(node.x1 to node.y1, node.x2 to node.y2, node.x3 to node.y3)
                    PathNode.Close -> emptyList()
                    // The source uses absolute M, L, C and Z only (the generator refuses anything else).
                    else -> error("$where has an unexpected node $node")
                }
                for ((x, y) in points) {
                    assertTrue(x in 0f..BodyMapPaths.VIEW_WIDTH && y in 0f..BodyMapPaths.VIEW_HEIGHT, "$where has ($x, $y)")
                }
            }
        }
    }
}
