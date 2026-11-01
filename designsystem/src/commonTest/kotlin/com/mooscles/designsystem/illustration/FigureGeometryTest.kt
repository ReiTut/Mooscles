package com.mooscles.designsystem.illustration

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigureGeometryTest {
    private val tolerance = 0.01f

    /** A standing pose: everything hangs straight down, the trunk and head straight up. */
    private fun standing(props: List<FigureProp> = emptyList()) = FigurePose(
        hipX = 50f, hipY = 50f, trunk = 0f, head = 0f,
        upperArmNear = 180f, forearmNear = 180f, upperArmFar = 180f, forearmFar = 180f,
        thighNear = 180f, shinNear = 180f, footNear = 90f, thighFar = 180f, shinFar = 180f, footFar = 90f,
        props = props,
    )

    private fun randomPose(random: Random): FigurePose {
        fun angle() = random.nextFloat() * 720f - 360f
        return FigurePose(
            hipX = random.nextFloat() * 100f, hipY = random.nextFloat() * 100f,
            trunk = angle(), head = angle(),
            upperArmNear = angle(), forearmNear = angle(), upperArmFar = angle(), forearmFar = angle(),
            thighNear = angle(), shinNear = angle(), footNear = angle(),
            thighFar = angle(), shinFar = angle(), footFar = angle(),
        )
    }

    private fun distance(a: Offset, b: Offset): Float = (a - b).getDistance()

    private fun assertNear(expected: Offset, actual: Offset, message: String) {
        assertTrue(
            abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
            "$message: expected $expected, got $actual",
        )
    }

    private fun assertInside(bounds: Rect, point: Offset, message: String) {
        assertTrue(
            point.x >= bounds.left - tolerance && point.x <= bounds.right + tolerance &&
                point.y >= bounds.top - tolerance && point.y <= bounds.bottom + tolerance,
            "$message: $point outside $bounds",
        )
    }

    @Test
    fun limbLengthsAreTheSkeletons() {
        val bones = listOf(
            Triple(FigureJoint.HIP, FigureJoint.SHOULDER, FigureSkeleton.TRUNK),
            Triple(FigureJoint.SHOULDER, FigureJoint.NECK, FigureSkeleton.NECK),
            Triple(FigureJoint.NECK, FigureJoint.HEAD, FigureSkeleton.HEAD_RADIUS),
            Triple(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, FigureSkeleton.UPPER_ARM),
            Triple(FigureJoint.ELBOW_NEAR, FigureJoint.HAND_NEAR, FigureSkeleton.FOREARM),
            Triple(FigureJoint.SHOULDER, FigureJoint.ELBOW_FAR, FigureSkeleton.UPPER_ARM),
            Triple(FigureJoint.ELBOW_FAR, FigureJoint.HAND_FAR, FigureSkeleton.FOREARM),
            Triple(FigureJoint.HIP, FigureJoint.KNEE_NEAR, FigureSkeleton.THIGH),
            Triple(FigureJoint.KNEE_NEAR, FigureJoint.ANKLE_NEAR, FigureSkeleton.SHIN),
            Triple(FigureJoint.ANKLE_NEAR, FigureJoint.TOE_NEAR, FigureSkeleton.FOOT),
            Triple(FigureJoint.HIP, FigureJoint.KNEE_FAR, FigureSkeleton.THIGH),
            Triple(FigureJoint.KNEE_FAR, FigureJoint.ANKLE_FAR, FigureSkeleton.SHIN),
            Triple(FigureJoint.ANKLE_FAR, FigureJoint.TOE_FAR, FigureSkeleton.FOOT),
        )
        val random = Random(4_2026)
        repeat(50) { index ->
            val pose = randomPose(random)
            val joints = FigureKinematics.solve(pose).joints
            assertEquals(FigureJoint.entries.toSet(), joints.keys, "pose $index solves every joint")
            for ((from, to, length) in bones) {
                val actual = distance(joints.getValue(from), joints.getValue(to))
                assertTrue(abs(actual - length) <= tolerance, "pose $index: $from-$to is $actual, the skeleton says $length")
            }
        }
    }

    @Test
    fun theHipIsWhereThePoseSaysIt() {
        val random = Random(7)
        repeat(10) {
            val pose = randomPose(random)
            assertNear(Offset(pose.hipX, pose.hipY), FigureKinematics.solve(pose).joints.getValue(FigureJoint.HIP), "hip")
        }
    }

    @Test
    fun zeroPointsUpAndNinetyPointsForward() {
        val pose = standing().copy(hipX = 40f, hipY = 60f, trunk = 0f, thighNear = 90f)
        val joints = FigureKinematics.solve(pose).joints
        assertNear(Offset(40f, 60f - FigureSkeleton.TRUNK), joints.getValue(FigureJoint.SHOULDER), "trunk at 0 degrees")
        assertNear(Offset(40f + FigureSkeleton.THIGH, 60f), joints.getValue(FigureJoint.KNEE_NEAR), "thigh at 90 degrees")
        // Positive is clockwise: 180 hangs straight down, 270 (or -90) points backward.
        val down = FigureKinematics.solve(pose.copy(thighFar = 180f, shinFar = -90f)).joints
        val knee = Offset(40f, 60f + FigureSkeleton.THIGH)
        assertNear(knee, down.getValue(FigureJoint.KNEE_FAR), "thigh at 180 degrees")
        assertNear(knee + Offset(-FigureSkeleton.SHIN, 0f), down.getValue(FigureJoint.ANKLE_FAR), "shin at -90 degrees")
    }

    @Test
    fun boundsCoverEveryJointAndProp() {
        val props = listOf(
            FigureProp.Barbell(FigureJoint.HAND_NEAR),
            FigureProp.Dumbbell(FigureJoint.HAND_FAR),
            FigureProp.Kettlebell(FigureJoint.HAND_NEAR),
            FigureProp.Bench(x = 20f, y = 70f, length = 40f, angle = 90f),
            FigureProp.Cable(anchorX = 90f, anchorY = 10f, to = FigureJoint.HAND_NEAR),
            FigureProp.Seat(x = 30f, y = 75f, length = 20f, angle = 90f),
            FigureProp.Pad(x = 70f, y = 80f, radius = 4f),
            FigureProp.Bar(x = 10f, y = 3f, length = 30f),
            FigureProp.Box(x = 60f, y = 70f, width = 25f, height = 20f),
            FigureProp.Platform(FigureJoint.ANKLE_NEAR, length = 24f, angle = 90f),
            FigureProp.Rail(x = 8f, top = 6f, bottom = 90f),
            FigureProp.Floor(y = 94f),
        )
        val pose = standing(props).copy(upperArmNear = 120f, forearmNear = 60f)
        val geometry = FigureKinematics.solve(pose)
        for ((joint, point) in geometry.joints) assertInside(geometry.bounds, point, joint.name)
        // The head is a circle around its joint, not a point.
        val head = geometry.joints.getValue(FigureJoint.HEAD)
        assertInside(geometry.bounds, head - Offset(0f, FigureSkeleton.HEAD_RADIUS), "top of the head")
        // Each prop on its own: its outermost points sit on or inside the bounds, and widen them.
        val bare = FigureKinematics.solve(standing().copy(upperArmNear = 120f, forearmNear = 60f)).bounds
        val hand = geometry.joints.getValue(FigureJoint.HAND_NEAR)
        assertInside(geometry.bounds, Offset(90f, 10f), "cable anchor")
        assertInside(geometry.bounds, Offset(10f, 3f), "bar start")
        assertInside(geometry.bounds, Offset(40f, 3f), "bar end")
        assertInside(geometry.bounds, Offset(85f, 90f), "box corner")
        assertInside(geometry.bounds, Offset(8f, 6f), "rail top")
        assertInside(geometry.bounds, hand - Offset(0f, FigureSkeleton.PLATE_RADIUS), "top of the plate")
        assertTrue(geometry.bounds.top < bare.top, "the bar above the figure widens the bounds")
        assertTrue(geometry.bounds.right > bare.right, "the cable anchor widens the bounds")
        // The floor runs under the whole figure.
        assertEquals(94f, geometry.bounds.bottom, tolerance, "the floor is the lowest thing")
    }

    @Test
    fun handPropsFollowTheHand() {
        for (prop in listOf(
            FigureProp.Barbell(FigureJoint.HAND_NEAR),
            FigureProp.Dumbbell(FigureJoint.HAND_NEAR),
            FigureProp.Kettlebell(FigureJoint.HAND_NEAR),
        )) {
            val low = FigureKinematics.solve(standing(listOf(prop)))
            val high = FigureKinematics.solve(standing(listOf(prop)).copy(upperArmNear = 0f, forearmNear = 0f))
            val shift = high.joints.getValue(FigureJoint.HAND_NEAR) - low.joints.getValue(FigureJoint.HAND_NEAR)
            val before = FigureKinematics.propShapes(prop, low.joints)
            val after = FigureKinematics.propShapes(prop, high.joints)
            assertEquals(before.size, after.size, "$prop")
            assertTrue(before.isNotEmpty(), "$prop draws something")
            for ((a, b) in before.zip(after)) {
                assertEquals(a::class, b::class, "$prop keeps its shapes")
                val moved = a.bounds().translate(shift)
                val actual = b.bounds()
                assertNear(moved.topLeft, actual.topLeft, "$prop moves with the hand")
                assertNear(moved.bottomRight, actual.bottomRight, "$prop moves with the hand")
            }
            // The weight is held at the hand: the hand is inside its outline.
            assertInside(after.first().bounds(), high.joints.getValue(FigureJoint.HAND_NEAR), "$prop at the hand")
        }
    }

    /**
     * A Smith machine's rail is one vertical line at its x, from its top to its bottom, whatever the
     * body does: the bar slides along it, so it does not follow a joint.
     */
    @Test
    fun railIsAVerticalLineThatStaysPut() {
        val rail = FigureProp.Rail(x = 44f, top = 8f, bottom = 88f)
        val low = FigureKinematics.solve(standing(listOf(rail)))
        val high = FigureKinematics.solve(standing(listOf(rail)).copy(upperArmNear = 0f, forearmNear = 0f))
        val expected = listOf(PropShape.Line(Offset(44f, 8f), Offset(44f, 88f)))
        assertEquals(expected, FigureKinematics.propShapes(rail, low.joints))
        assertEquals(expected, FigureKinematics.propShapes(rail, high.joints))
        assertEquals(8f, low.bounds.top, tolerance, "the rail's top bounds the standing figure")
    }

    /**
     * The kinematics as they were before the shoulder lift (ef88cad), copied verbatim: every joint
     * from the hip outward, the arms from the top of the trunk.
     */
    private fun fixedSkeletonJoints(pose: FigurePose): Map<FigureJoint, Offset> {
        fun bone(from: Offset, angle: Float, length: Float) = from + FigureKinematics.direction(angle) * length
        val hip = Offset(pose.hipX, pose.hipY)
        val shoulder = bone(hip, pose.trunk, FigureSkeleton.TRUNK)
        val neck = bone(shoulder, pose.head, FigureSkeleton.NECK)
        val head = bone(neck, pose.head, FigureSkeleton.HEAD_RADIUS)
        val elbowNear = bone(shoulder, pose.upperArmNear, FigureSkeleton.UPPER_ARM)
        val handNear = bone(elbowNear, pose.forearmNear, FigureSkeleton.FOREARM)
        val elbowFar = bone(shoulder, pose.upperArmFar, FigureSkeleton.UPPER_ARM)
        val handFar = bone(elbowFar, pose.forearmFar, FigureSkeleton.FOREARM)
        val kneeNear = bone(hip, pose.thighNear, FigureSkeleton.THIGH)
        val ankleNear = bone(kneeNear, pose.shinNear, FigureSkeleton.SHIN)
        val toeNear = bone(ankleNear, pose.footNear, FigureSkeleton.FOOT)
        val kneeFar = bone(hip, pose.thighFar, FigureSkeleton.THIGH)
        val ankleFar = bone(kneeFar, pose.shinFar, FigureSkeleton.SHIN)
        val toeFar = bone(ankleFar, pose.footFar, FigureSkeleton.FOOT)
        return mapOf(
            FigureJoint.HIP to hip, FigureJoint.SHOULDER to shoulder, FigureJoint.NECK to neck, FigureJoint.HEAD to head,
            FigureJoint.ELBOW_NEAR to elbowNear, FigureJoint.HAND_NEAR to handNear,
            FigureJoint.ELBOW_FAR to elbowFar, FigureJoint.HAND_FAR to handFar,
            FigureJoint.KNEE_NEAR to kneeNear, FigureJoint.ANKLE_NEAR to ankleNear, FigureJoint.TOE_NEAR to toeNear,
            FigureJoint.KNEE_FAR to kneeFar, FigureJoint.ANKLE_FAR to ankleFar, FigureJoint.TOE_FAR to toeFar,
        )
    }

    /** Raw float bits of every joint, so -0 and 0 or a last-bit rounding difference would show. */
    private fun bits(joints: Map<FigureJoint, Offset>): Map<FigureJoint, Pair<Int, Int>> =
        joints.mapValues { (_, point) -> Pair(point.x.toRawBits(), point.y.toRawBits()) }

    /**
     * Shoulder lift (barbell lot, A1): with no lift, the default, every joint lands where the fixed
     * skeleton put it, bit for bit, so no existing figure moves. A sample of random poses, the
     * standing one and a few written-out ones (a bench press, a squat, a front view).
     */
    @Test
    fun noShoulderLiftLeavesEveryJointBitIdentical() {
        val random = Random(2026_10_31)
        val written = listOf(
            standing(),
            standing().copy(hipX = 42f, hipY = 62f, trunk = 90f, head = 90f, upperArmNear = 0f, forearmNear = 0f),
            standing().copy(hipX = 38f, hipY = 58f, trunk = 35f, thighNear = 75f, shinNear = 200f, thighFar = 75f, shinFar = 200f),
            standing().copy(upperArmNear = 95f, upperArmFar = -95f, frontView = true),
        )
        for ((index, pose) in (written + List(200) { randomPose(random) }).withIndex()) {
            val expected = bits(fixedSkeletonJoints(pose))
            assertEquals(expected, bits(FigureKinematics.solve(pose).joints), "pose $index by default")
            assertEquals(expected, bits(FigureKinematics.solve(pose.copy(shoulderLift = 0f)).joints), "pose $index at 0")
        }
    }

    /**
     * A lift moves the shoulder exactly that far up the trunk line, toward the neck. The hip, the
     * neck, the head and the legs stay put, and the arms keep their lengths and their angles: they
     * move up with the shoulder, hands and all.
     */
    @Test
    fun aShoulderLiftRaisesTheShoulderAlongTheTrunk() {
        val random = Random(31)
        val arm = listOf(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, FigureJoint.HAND_NEAR, FigureJoint.ELBOW_FAR, FigureJoint.HAND_FAR)
        for (lift in listOf(0.5f, 1.5f, FigureSkeleton.SHOULDER_LIFT_MAX)) {
            repeat(20) { index ->
                val pose = randomPose(random)
                val still = FigureKinematics.solve(pose).joints
                val lifted = FigureKinematics.solve(pose.copy(shoulderLift = lift)).joints
                val shift = FigureKinematics.direction(pose.trunk) * lift
                for (joint in arm) assertNear(still.getValue(joint) + shift, lifted.getValue(joint), "pose $index lift $lift: $joint")
                for (joint in FigureJoint.entries - arm.toSet()) {
                    assertEquals(still.getValue(joint), lifted.getValue(joint), "pose $index lift $lift: $joint stays put")
                }
                val hip = lifted.getValue(FigureJoint.HIP)
                val shoulder = lifted.getValue(FigureJoint.SHOULDER)
                assertEquals(FigureSkeleton.TRUNK + lift, distance(hip, shoulder), tolerance, "pose $index: the shoulder sits $lift higher on the trunk")
                for ((from, to, length) in listOf(
                    Triple(FigureJoint.SHOULDER, FigureJoint.ELBOW_NEAR, FigureSkeleton.UPPER_ARM),
                    Triple(FigureJoint.ELBOW_NEAR, FigureJoint.HAND_NEAR, FigureSkeleton.FOREARM),
                    Triple(FigureJoint.SHOULDER, FigureJoint.ELBOW_FAR, FigureSkeleton.UPPER_ARM),
                    Triple(FigureJoint.ELBOW_FAR, FigureJoint.HAND_FAR, FigureSkeleton.FOREARM),
                )) {
                    assertEquals(length, distance(lifted.getValue(from), lifted.getValue(to)), tolerance, "pose $index lift $lift: $from-$to")
                }
            }
        }
        // Standing, the lift is straight up: the shoulder and the hands rise, the head does not.
        val standing = FigureKinematics.solve(standing()).joints
        val shrug = FigureKinematics.solve(standing().copy(shoulderLift = 2f)).joints
        assertNear(standing.getValue(FigureJoint.SHOULDER) - Offset(0f, 2f), shrug.getValue(FigureJoint.SHOULDER), "standing shoulder")
        assertNear(standing.getValue(FigureJoint.HAND_NEAR) - Offset(0f, 2f), shrug.getValue(FigureJoint.HAND_NEAR), "standing hand")
        assertEquals(standing.getValue(FigureJoint.HEAD), shrug.getValue(FigureJoint.HEAD), "the head stays")
    }

    /**
     * The lift is held to `0..SHOULDER_LIFT_MAX`: no more than the neck, so the shoulder never passes
     * the base of the head, and never down into the trunk.
     */
    @Test
    fun theShoulderLiftIsClamped() {
        assertTrue(FigureSkeleton.SHOULDER_LIFT_MAX <= FigureSkeleton.NECK, "the lift stops at the base of the head")
        val pose = standing().copy(trunk = 20f, head = 20f)
        fun shoulder(lift: Float) = FigureKinematics.solve(pose.copy(shoulderLift = lift)).joints.getValue(FigureJoint.SHOULDER)
        assertEquals(shoulder(FigureSkeleton.SHOULDER_LIFT_MAX), shoulder(FigureSkeleton.SHOULDER_LIFT_MAX * 4f), "above the maximum")
        assertEquals(shoulder(0f), shoulder(-2f), "a negative lift")
        assertEquals(shoulder(0f), shoulder(Float.NaN), "not a number")
        val top = FigureKinematics.solve(pose.copy(shoulderLift = 100f)).joints
        assertEquals(
            FigureSkeleton.TRUNK + FigureSkeleton.SHOULDER_LIFT_MAX,
            distance(top.getValue(FigureJoint.HIP), top.getValue(FigureJoint.SHOULDER)),
            tolerance,
        )
        assertTrue(
            distance(top.getValue(FigureJoint.HIP), top.getValue(FigureJoint.SHOULDER)) <=
                distance(top.getValue(FigureJoint.HIP), top.getValue(FigureJoint.NECK)) + tolerance,
            "the lifted shoulder is still at or below the neck",
        )
    }
}
