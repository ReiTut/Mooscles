package com.mooscles.designsystem.illustration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.tokens.MoosclesIllustration
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the renderer puts a muscle highlight on the figure (Task 8: spans tuned on the pilot poses),
 * and how it gives the body weight and depth (Task 9: the pilot sheet's critique).
 */
class FigureHighlightDrawingTest {
    private val palette = FigurePalette(body = Color(1), far = Color(2), equipment = Color(3), highlight = Color(4))
    private val illustration = MoosclesIllustration()

    // At 100 × 100 a pixel is a box unit.
    private val stroke = illustration.figureStroke * 100f
    private val trunkStroke = illustration.figureTrunkStroke * 100f
    private val gap = illustration.figureGap * 100f

    private val standing = FigurePose(
        hipX = 50f, hipY = 50f, trunk = 0f, head = 0f,
        upperArmNear = 120f, forearmNear = 120f, upperArmFar = 240f, forearmFar = 240f,
        thighNear = 160f, shinNear = 180f, footNear = 90f, thighFar = 200f, shinFar = 180f, footFar = 90f,
        props = listOf(FigureProp.Dumbbell(FigureJoint.HAND_NEAR)),
    )

    private fun operations(pose: FigurePose, highlights: Set<FigureSegment>): List<FigureOperation> {
        val joints = FigureKinematics.solve(pose).joints
        return figureOperations(
            joints, FigureKinematics.equipment(pose, joints), highlights, palette, illustration, Size(100f, 100f), pose.frontView,
        )
    }

    private fun List<FigureOperation>.lines(color: Color) = filterIsInstance<FigureOperation.Line>().filter { it.color == color }

    private fun near(a: Offset, b: Offset) = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    /** The glutes wrap the hip: the whole width of the thigh's first 40%; the hamstrings the back of the rest. */
    @Test
    fun glutesAndHamstringsShareTheThigh() {
        val joints = FigureKinematics.solve(standing).joints
        val hip = joints.getValue(FigureJoint.HIP)
        val knee = joints.getValue(FigureJoint.KNEE_NEAR)
        val split = hip + (knee - hip) * 0.4f

        val glutes = operations(standing, setOf(FigureSegment.HIP)).lines(palette.highlight).single()
        assertTrue(near(hip, glutes.start) && near(split, glutes.end), "glutes from the hip to 40%: ${glutes.start}–${glutes.end}")
        assertEquals(stroke, glutes.width, "the glutes take the whole width")

        val hamstrings = operations(standing, setOf(FigureSegment.THIGH_BACK)).lines(palette.highlight).single()
        assertEquals(stroke / 2f, hamstrings.width, "the hamstrings take the back half")
        // Shifted a quarter stroke toward the back, from 40% to the knee.
        assertEquals(stroke / 4f, (hamstrings.start - split).getDistance(), 0.01f)
        assertEquals(stroke / 4f, (hamstrings.end - knee).getDistance(), 0.01f)
    }

    /** Side view: the far limbs are their own tone, unlit, and drawn over the equipment. Front view: both sides are body and lit. */
    @Test
    fun farLimbsAreBodyAndLitOnlyFromTheFront() {
        val side = operations(standing, setOf(FigureSegment.SHOULDER))
        assertEquals(1, side.lines(palette.highlight).size, "one deltoid from the side")
        assertEquals(5, side.lines(palette.far).size, "the far leg (3) and arm (2) in the far tone")
        val equipmentAt = side.indexOfFirst { it !is FigureOperation.Line || (it as FigureOperation.Line).color == palette.equipment }
        val farAt = side.indexOfFirst { it is FigureOperation.Line && it.color == palette.far }
        assertTrue(equipmentAt < farAt, "the equipment is drawn before the far limbs")

        val front = operations(standing.copy(frontView = true), setOf(FigureSegment.SHOULDER))
        val delts = front.lines(palette.highlight)
        assertEquals(2, delts.size, "both deltoids from the front")
        val joints = FigureKinematics.solve(standing).joints
        val shoulder = joints.getValue(FigureJoint.SHOULDER)
        for ((delt, elbow) in delts.zip(listOf(FigureJoint.ELBOW_FAR, FigureJoint.ELBOW_NEAR))) {
            assertTrue(near(shoulder, delt.start), "a deltoid starts at the shoulder")
            assertTrue(near(shoulder + (joints.getValue(elbow) - shoulder) * 0.4f, delt.end), "and covers 40% of the $elbow arm")
        }
        assertEquals(0, front.lines(palette.far).size, "no far tone from the front")
    }

    /** The trunk is heavier than the limbs (a body, not a pole), and its highlights halve its own width. */
    @Test
    fun trunkIsHeavierThanTheLimbs() {
        val joints = FigureKinematics.solve(standing).joints
        val hip = joints.getValue(FigureJoint.HIP)
        val shoulder = joints.getValue(FigureJoint.SHOULDER)
        val operations = operations(standing, setOf(FigureSegment.TORSO_FRONT))
        val trunk = operations.lines(palette.body).single { near(hip, it.start) && near(shoulder, it.end) }
        assertEquals(trunkStroke, trunk.width, "the trunk's stroke")
        assertTrue(trunk.width > stroke, "heavier than a limb")
        val chest = operations.lines(palette.highlight).single()
        assertEquals(trunkStroke / 2f, chest.width, "the chest takes the front half of the trunk")
        assertEquals(trunkStroke / 4f, (chest.start - hip).getDistance(), 0.01f)
    }

    /**
     * The head and the arms in front get a gap, cleared before they are drawn: the head's before the
     * trunk (so only the equipment and the far limbs under it go), each lit arm's right before its
     * bones. From the side only the near arm has one; from the front both arms do.
     */
    @Test
    fun gapsSetTheHeadAndTheArmsInFront() {
        val joints = FigureKinematics.solve(standing).joints
        val side = operations(standing, emptySet())
        val disc = side.filterIsInstance<FigureOperation.GapDisc>().single()
        assertTrue(near(joints.getValue(FigureJoint.HEAD), disc.center), "the head's gap is on the head")
        assertEquals(FigureSkeleton.HEAD_RADIUS + illustration.figureHeadStroke * 100f / 2f + gap, disc.radius, 0.01f)
        val trunkAt = side.indexOfFirst { it is FigureOperation.Line && it.width == trunkStroke }
        assertTrue(side.indexOf(disc) < trunkAt, "the head's gap comes before the trunk")
        val dumbbell = side.indexOfFirst { it is FigureOperation.Ring && it.color == palette.equipment }
        assertTrue(dumbbell in 0 until side.indexOf(disc), "and after the equipment it clears")

        val gaps = side.filterIsInstance<FigureOperation.Gap>()
        assertEquals(2, gaps.size, "the near upper arm and forearm")
        for (band in gaps) assertEquals(stroke + 2f * gap, band.width, 0.01f)
        val shoulder = joints.getValue(FigureJoint.SHOULDER)
        val elbow = joints.getValue(FigureJoint.ELBOW_NEAR)
        assertTrue(near(shoulder, gaps[0].start) && near(elbow, gaps[0].end), "the upper arm's gap")
        val nearArm = side.indexOfLast { it is FigureOperation.Line && near(shoulder, it.start) && near(elbow, it.end) }
        assertTrue(side.indexOf(gaps[1]) < nearArm, "the gaps come before the near arm")
        assertTrue(side.indexOf(gaps[0]) > trunkAt, "and after the trunk")

        val front = operations(standing.copy(frontView = true), emptySet())
        assertEquals(4, front.filterIsInstance<FigureOperation.Gap>().size, "both arms from the front")
    }

    /**
     * A kettlebell in the near hand is drawn over the arm, so the arm's gap cannot erase its bell
     * when the forearm points up (rack, overhead); every other weight, and a kettlebell in the far
     * hand seen from the side, stays behind the body.
     */
    @Test
    fun aKettlebellInTheNearHandIsDrawnOverTheArm() {
        fun drawn(pose: FigurePose): List<FigureOperation> {
            val joints = FigureKinematics.solve(pose).joints
            return figureOperations(
                joints, FigureKinematics.equipmentBehind(pose, joints), emptySet(), palette, illustration, Size(100f, 100f),
                pose.frontView, FigureKinematics.equipmentInFront(pose, joints),
            )
        }
        val overhead = standing.copy(upperArmNear = 0f, forearmNear = 0f)
        val near = drawn(overhead.copy(props = listOf(FigureProp.Kettlebell(FigureJoint.HAND_NEAR))))
        val lastNearArm = near.indexOfLast { it is FigureOperation.Line && it.color == palette.body }
        val bell = near.indexOfFirst { it is FigureOperation.Disc }
        assertTrue(bell > lastNearArm, "the bell is drawn after the near arm")
        assertEquals(near.size - 2, near.indexOfFirst { it is FigureOperation.Ring && it.color == palette.equipment }, "handle, then bell, last")

        val far = drawn(overhead.copy(props = listOf(FigureProp.Kettlebell(FigureJoint.HAND_FAR))))
        assertTrue(far.indexOfFirst { it is FigureOperation.Disc } < far.indexOfFirst { it is FigureOperation.Line && it.color == palette.far })
        val fromTheFront = drawn(overhead.copy(frontView = true, props = listOf(FigureProp.Kettlebell(FigureJoint.HAND_FAR))))
        assertEquals(fromTheFront.size - 1, fromTheFront.indexOfFirst { it is FigureOperation.Disc }, "from the front, either hand")

        val dumbbell = drawn(overhead.copy(props = listOf(FigureProp.Dumbbell(FigureJoint.HAND_NEAR))))
        assertEquals(0, dumbbell.indexOfFirst { it is FigureOperation.Ring && it.color == palette.equipment }, "a dumbbell stays first")
        // Both layers together are the whole equipment, in order.
        val pose = overhead.copy(props = listOf(FigureProp.Barbell(FigureJoint.HIP), FigureProp.Kettlebell(FigureJoint.HAND_NEAR)))
        val joints = FigureKinematics.solve(pose).joints
        assertEquals(
            FigureKinematics.equipment(pose, joints),
            FigureKinematics.equipmentBehind(pose, joints) + FigureKinematics.equipmentInFront(pose, joints),
        )
    }
}
