package com.mooscles.designsystem.illustration

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * A solved pose: every joint's position in the 100 × 100 box, and the [bounds] that cover the
 * joints, the head circle and the equipment (stroke centerlines; the stroke width is the theme's).
 */
@Immutable
data class FigureGeometry(val joints: Map<FigureJoint, Offset>, val bounds: Rect)

/**
 * Forward kinematics: from the hip outward, each joint is its parent plus the bone length of
 * [FigureSkeleton] in the bone's absolute direction (plan decision 6). The neck starts at the top of
 * the trunk; the arms start at the shoulder, which a pose's `shoulderLift` raises further up the
 * trunk line (see [FigurePose]).
 */
object FigureKinematics {
    fun solve(pose: FigurePose): FigureGeometry {
        val joints = joints(pose)
        var bounds = bodyBounds(joints)
        for (shape in equipment(pose, joints)) bounds = bounds.including(shape.bounds())
        return FigureGeometry(joints, bounds)
    }

    /**
     * Every piece of equipment of [pose] as shapes, in the order of its props. A floor runs under
     * everything else, so it is measured last, against the body and the other equipment.
     */
    internal fun equipment(pose: FigurePose, joints: Map<FigureJoint, Offset>): List<PropShape> = layer(pose, joints) { true }

    /** The equipment drawn behind the body: all of it but what [inFrontOfTheArm] keeps for the front. */
    internal fun equipmentBehind(pose: FigurePose, joints: Map<FigureJoint, Offset>): List<PropShape> =
        layer(pose, joints) { !inFrontOfTheArm(it, pose.frontView) }

    /** The equipment drawn last, over the near arm (see [inFrontOfTheArm]). */
    internal fun equipmentInFront(pose: FigurePose, joints: Map<FigureJoint, Offset>): List<PropShape> =
        layer(pose, joints) { inFrontOfTheArm(it, pose.frontView) }

    /**
     * A kettlebell in a hand the viewer sees in front (the near hand; from the front, either hand):
     * its bell rests on the outside of the forearm in the rack and overhead, toward the viewer, and
     * the arm's gap would otherwise erase it whenever the forearm points up.
     */
    internal fun inFrontOfTheArm(prop: FigureProp, frontView: Boolean): Boolean =
        prop is FigureProp.Kettlebell &&
            (prop.at == FigureJoint.HAND_NEAR || (frontView && prop.at == FigureJoint.HAND_FAR))

    private inline fun layer(pose: FigurePose, joints: Map<FigureJoint, Offset>, keep: (FigureProp) -> Boolean): List<PropShape> {
        val shapes = pose.props.map { propShapes(it, joints) }
        var rest = bodyBounds(joints)
        for (list in shapes) for (shape in list) rest = rest.including(shape.bounds())
        return pose.props.flatMapIndexed { index, prop ->
            when {
                !keep(prop) -> emptyList()
                prop is FigureProp.Floor -> floorShapes(prop, rest)
                else -> shapes[index]
            }
        }
    }

    /** The joints and the head circle. */
    private fun bodyBounds(joints: Map<FigureJoint, Offset>): Rect {
        val hip = joints.getValue(FigureJoint.HIP)
        var bounds = Rect(hip, hip)
        for (point in joints.values) bounds = bounds.including(point)
        return bounds.including(Rect(joints.getValue(FigureJoint.HEAD), FigureSkeleton.HEAD_RADIUS))
    }

    /** The unit vector of an absolute pose angle: 0 is up, positive turns clockwise (y grows downward). */
    internal fun direction(degrees: Float): Offset {
        val radians = degrees * (PI / 180.0)
        return Offset(sin(radians).toFloat(), -cos(radians).toFloat())
    }

    /** The pose's shoulder lift, clamped to `0..`[FigureSkeleton.SHOULDER_LIFT_MAX] (a NaN is no lift). */
    internal fun shoulderLift(pose: FigurePose): Float {
        val lift = pose.shoulderLift
        return if (lift > 0f) min(lift, FigureSkeleton.SHOULDER_LIFT_MAX) else 0f
    }

    private fun joints(pose: FigurePose): Map<FigureJoint, Offset> {
        fun bone(from: Offset, angle: Float, length: Float) = from + direction(angle) * length
        val hip = Offset(pose.hipX, pose.hipY)
        val trunkTop = bone(hip, pose.trunk, FigureSkeleton.TRUNK)
        // Without a lift the shoulder is the top of the trunk itself, bit for bit.
        val lift = shoulderLift(pose)
        val shoulder = if (lift > 0f) bone(trunkTop, pose.trunk, lift) else trunkTop
        val neck = bone(trunkTop, pose.head, FigureSkeleton.NECK)
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

    /**
     * The outline of a piece of equipment, in box units: what [solve] measures and the renderer
     * draws. A hand-held weight's first shape is the one around the hand. A floor needs the rest of
     * the figure's width, so it has no shapes here (see [floorShapes]).
     */
    internal fun propShapes(prop: FigureProp, joints: Map<FigureJoint, Offset>): List<PropShape> = when (prop) {
        is FigureProp.Barbell -> listOf(PropShape.Circle(joints.getValue(prop.at), FigureSkeleton.PLATE_RADIUS))
        is FigureProp.Dumbbell -> listOf(PropShape.Circle(joints.getValue(prop.at), FigureSkeleton.DUMBBELL_RADIUS))
        is FigureProp.Kettlebell -> {
            val hand = joints.getValue(prop.at)
            val bell = hand + Offset(0f, FigureSkeleton.KETTLEBELL_HANDLE + FigureSkeleton.KETTLEBELL_RADIUS)
            listOf(PropShape.Circle(hand, FigureSkeleton.KETTLEBELL_HANDLE), PropShape.Disc(bell, FigureSkeleton.KETTLEBELL_RADIUS))
        }
        is FigureProp.Bench -> {
            val start = Offset(prop.x, prop.y)
            val end = start + direction(prop.angle) * prop.length
            val leg = Offset(0f, FigureSkeleton.BENCH_LEG)
            listOf(PropShape.Line(start, end), PropShape.Line(start, start + leg), PropShape.Line(end, end + leg))
        }
        is FigureProp.Cable -> {
            val anchor = Offset(prop.anchorX, prop.anchorY)
            listOf(PropShape.Line(anchor, joints.getValue(prop.to)), PropShape.Circle(anchor, FigureSkeleton.PULLEY_RADIUS))
        }
        is FigureProp.Seat -> {
            val start = Offset(prop.x, prop.y)
            val end = start + direction(prop.angle) * prop.length
            val middle = (start + end) / 2f
            listOf(PropShape.Line(start, end), PropShape.Line(middle, middle + Offset(0f, FigureSkeleton.BENCH_LEG)))
        }
        is FigureProp.Pad -> listOf(PropShape.Circle(Offset(prop.x, prop.y), prop.radius))
        is FigureProp.Bar -> listOf(PropShape.Line(Offset(prop.x, prop.y), Offset(prop.x + prop.length, prop.y)))
        is FigureProp.Box -> listOf(PropShape.Box(Rect(prop.x, prop.y, prop.x + prop.width, prop.y + prop.height)))
        is FigureProp.Platform -> {
            val center = joints.getValue(prop.at)
            val half = direction(prop.angle) * (prop.length / 2f)
            listOf(PropShape.Line(center - half, center + half))
        }
        is FigureProp.Rail -> listOf(PropShape.Line(Offset(prop.x, prop.top), Offset(prop.x, prop.bottom)))
        is FigureProp.Floor -> emptyList()
    }

    /** A floor line at its height, [FigureSkeleton.FLOOR_OVERHANG] past the [body]'s sides. */
    internal fun floorShapes(floor: FigureProp.Floor, body: Rect): List<PropShape> = listOf(
        PropShape.Line(
            Offset(body.left - FigureSkeleton.FLOOR_OVERHANG, floor.y),
            Offset(body.right + FigureSkeleton.FLOOR_OVERHANG, floor.y),
        ),
    )
}

/** A primitive of the equipment drawing, in box units. */
internal sealed interface PropShape {
    fun bounds(): Rect

    /** A stroked segment with round ends. */
    data class Line(val start: Offset, val end: Offset) : PropShape {
        override fun bounds() = Rect(
            min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y),
        )
    }

    /** A stroked ring (a plate, a pad, a pulley). */
    data class Circle(val center: Offset, val radius: Float) : PropShape {
        override fun bounds() = Rect(center, radius)
    }

    /** A filled disc (a kettlebell's bell). */
    data class Disc(val center: Offset, val radius: Float) : PropShape {
        override fun bounds() = Rect(center, radius)
    }

    /** A stroked rectangle. */
    data class Box(val rect: Rect) : PropShape {
        override fun bounds() = rect
    }
}

private fun Rect.including(point: Offset) = Rect(
    min(left, point.x), min(top, point.y), max(right, point.x), max(bottom, point.y),
)

private fun Rect.including(other: Rect) = Rect(
    min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom),
)
