package com.mooscles.screens.exercise.figures

import androidx.compose.ui.geometry.Offset
import com.mooscles.designsystem.illustration.FigureJoint
import com.mooscles.designsystem.illustration.FigureKinematics
import com.mooscles.designsystem.illustration.FigurePose
import com.mooscles.designsystem.illustration.FigureProp
import com.mooscles.designsystem.illustration.FigureSkeleton
import com.mooscles.designsystem.tokens.MoosclesIllustration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An exercise's two frames (spec D3, D5): [start] where a repetition begins, [end] where it turns
 * around, the frame lists and thumbnails show. [startOnFloor] and [endOnFloor] are the joints that
 * rest on the pose's `floorY` in each frame, declared per pose (both feet flat when standing, the
 * toes and hands in a push-up, the front foot only in a split squat); the tests hold the drawing to
 * them.
 */
internal data class FigureFrames(
    val start: FigurePose,
    val end: FigurePose,
    val startOnFloor: Set<FigureJoint> = emptySet(),
    val endOnFloor: Set<FigureJoint> = emptySet(),
)

/**
 * The table of one family, written by hand: `figure("barbell_bench_flat") { start { … }; end { … } }`,
 * keyed by the catalog's `stableId` (the id without `catalog_`). Inline all the way down, so a table
 * compiles to straight calls in one initializer, without a class per lambda (spec, size warning).
 */
internal inline fun figureTable(block: FigureTable.() -> Unit): Map<String, FigureFrames> =
    FigureTable().apply(block).entries

internal class FigureTable {
    val entries = LinkedHashMap<String, FigureFrames>()

    inline fun figure(stableId: String, block: FigureBuilder.() -> Unit) {
        add(stableId, FigureBuilder().apply(block))
    }

    fun add(stableId: String, builder: FigureBuilder) {
        require(stableId !in entries) { "$stableId has two figures" }
        entries[stableId] = builder.frames()
    }
}

internal class FigureBuilder {
    var start: PoseBuilder? = null
    var end: PoseBuilder? = null

    inline fun start(block: PoseBuilder.() -> Unit) {
        start = PoseBuilder().apply(block)
    }

    inline fun end(block: PoseBuilder.() -> Unit) {
        end = PoseBuilder().apply(block)
    }

    fun frames(): FigureFrames {
        val a = checkNotNull(start) { "no start frame" }
        val b = checkNotNull(end) { "no end frame" }
        return FigureFrames(a.pose(), b.pose(), a.onFloor, b.onFloor)
    }
}

/**
 * One frame, in the pose convention (plan decision 6): the 100 × 100 box with y down, absolute
 * angles in degrees, 0 up, positive clockwise, the figure facing right. Unset angles are 0.
 */
internal class PoseBuilder {
    private var hipX = 0f
    private var hipY = 0f
    private var placed: FigureJoint? = null
    private var trunk = 0f
    private var head = 0f
    private var upperArmNear = 0f
    private var forearmNear = 0f
    private var upperArmFar = 0f
    private var forearmFar = 0f
    private var thighNear = 0f
    private var shinNear = 0f
    private var footNear = 0f
    private var thighFar = 0f
    private var shinFar = 0f
    private var footFar = 0f
    private var floorY: Float? = null
    private var frontView = false
    private var shoulderLift = 0f
    private val props = ArrayList<PropSpec>()
    var onFloor: Set<FigureJoint> = emptySet()
        private set

    /**
     * Draws the figure from the front (only for a movement in the frontal plane, such as a lateral
     * raise): near is then the figure's left side, on the right of the image.
     */
    fun front() {
        frontView = true
    }

    /** Puts the hip at ([x], [y]). */
    fun hip(x: Float, y: Float) {
        hipX = x
        hipY = y
        placed = null
    }

    /** Puts [joint] at ([x], [y]) and the rest of the body where the angles take it (a planted foot, a hand on a bar). */
    fun place(joint: FigureJoint, x: Float, y: Float) {
        hipX = x
        hipY = y
        placed = joint
    }

    /** The trunk, from the hip to the shoulders, and the head, from the shoulders (in line with the trunk by default). */
    fun trunk(angle: Float, head: Float = angle) {
        trunk = angle
        this.head = head
    }

    /**
     * Raises the shoulders [lift] box units up the trunk, toward the neck (a shrug), at most
     * `FigureSkeleton.SHOULDER_LIFT_MAX`. The arms start that much higher; nothing else moves.
     */
    fun shoulderLift(lift: Float) {
        shoulderLift = lift
    }

    fun nearArm(upper: Float, forearm: Float) {
        upperArmNear = upper
        forearmNear = forearm
    }

    fun farArm(upper: Float, forearm: Float) {
        upperArmFar = upper
        forearmFar = forearm
    }

    /** Both arms the same: the far one hides behind the near one, as seen from the side. */
    fun arms(upper: Float, forearm: Float) {
        nearArm(upper, forearm)
        farArm(upper, forearm)
    }

    fun nearLeg(thigh: Float, shin: Float, foot: Float) {
        thighNear = thigh
        shinNear = shin
        footNear = foot
    }

    fun farLeg(thigh: Float, shin: Float, foot: Float) {
        thighFar = thigh
        shinFar = shin
        footFar = foot
    }

    /** Both legs the same, the far one hidden behind the near one. */
    fun legs(thigh: Float, shin: Float, foot: Float) {
        nearLeg(thigh, shin, foot)
        farLeg(thigh, shin, foot)
    }

    /**
     * The floor: [joints] rest on it at height [y] (their centerlines; the pose tests check them),
     * and its line is drawn just under them, where the strokes touch.
     */
    fun floor(y: Float, vararg joints: FigureJoint) {
        floorY = y
        onFloor = joints.toSet()
        props += Fixed(FigureProp.Floor(y + CONTACT_GAP))
    }

    /** A floor line at [y] that nothing in the pose stands on (it grounds a machine). */
    fun groundLine(y: Float) {
        props += Fixed(FigureProp.Floor(y))
    }

    fun barbell(at: FigureJoint) {
        props += Fixed(FigureProp.Barbell(at))
    }

    fun dumbbell(at: FigureJoint) {
        props += Fixed(FigureProp.Dumbbell(at))
    }

    fun kettlebell(at: FigureJoint) {
        props += Fixed(FigureProp.Kettlebell(at))
    }

    /** A bench pad whose line runs from ([x], [y]) for [length] at [angle] (90 is flat). */
    fun bench(x: Float, y: Float, length: Float, angle: Float = FLAT) {
        props += Fixed(FigureProp.Bench(x, y, length, angle))
    }

    fun cable(anchorX: Float, anchorY: Float, to: FigureJoint) {
        props += Fixed(FigureProp.Cable(anchorX, anchorY, to))
    }

    fun seat(x: Float, y: Float, length: Float, angle: Float = FLAT) {
        props += Fixed(FigureProp.Seat(x, y, length, angle))
    }

    /** A round pad [distance] from [joint] in the direction [angle], so it follows the limb between frames. */
    fun pad(joint: FigureJoint, angle: Float, distance: Float, radius: Float) {
        props += PadAt(joint, angle, distance, radius)
    }

    fun bar(x: Float, y: Float, length: Float) {
        props += Fixed(FigureProp.Bar(x, y, length))
    }

    fun box(x: Float, y: Float, width: Float, height: Float) {
        props += Fixed(FigureProp.Box(x, y, width, height))
    }

    /** A Smith machine's rail at [x], from [top] down to [bottom]; the bar held on it must sit at [x]. */
    fun rail(x: Float, top: Float, bottom: Float) {
        props += Fixed(FigureProp.Rail(x, top, bottom))
    }

    fun platform(at: FigureJoint, length: Float, angle: Float) {
        props += Fixed(FigureProp.Platform(at, length, angle))
    }

    fun pose(): FigurePose {
        var pose = FigurePose(
            hipX, hipY, trunk, head, upperArmNear, forearmNear, upperArmFar, forearmFar,
            thighNear, shinNear, footNear, thighFar, shinFar, footFar, floorY, frontView = frontView, shoulderLift = shoulderLift,
        )
        val joint = placed
        if (joint != null) {
            val at = FigureKinematics.solve(pose).joints.getValue(joint)
            pose = pose.copy(hipX = hipX + (hipX - at.x), hipY = hipY + (hipY - at.y))
        }
        val joints = FigureKinematics.solve(pose).joints
        return pose.copy(
            props = props.map { prop ->
                when (prop) {
                    is PadAt -> {
                        val center = joints.getValue(prop.joint) + direction(prop.angle) * prop.distance
                        FigureProp.Pad(center.x, center.y, prop.radius)
                    }
                    is Fixed -> prop.prop
                }
            },
        )
    }

    /** A prop as written: given outright, or a pad placed from a joint once the pose is solved. */
    private sealed interface PropSpec

    private class Fixed(val prop: FigureProp) : PropSpec

    private class PadAt(val joint: FigureJoint, val angle: Float, val distance: Float, val radius: Float) : PropSpec

    private companion object {
        const val FLAT = 90f
    }
}

/**
 * How far below a joint's centerline a surface it rests on is drawn: half a limb stroke plus half
 * an equipment stroke, in box units, so the two strokes just touch. It reads the default
 * [MoosclesIllustration], not the theme's: both themes use the same strokes, and a pose is data
 * built outside any composition.
 */
internal val CONTACT_GAP: Float = MoosclesIllustration().let { (it.figureStroke + it.figureHeadStroke) / 2f * BOX }

/** The side of the pose box, in pose units. */
internal const val BOX = 100f

/**
 * The B frame of the three shrugs: the shoulders all the way up the trunk, to the base of the head
 * (`FigureSkeleton.SHOULDER_LIFT_MAX`), the most the skeleton allows, so the lift reads at 64 dp.
 */
internal const val SHRUG_LIFT = FigureSkeleton.SHOULDER_LIFT_MAX

/** The unit vector of a pose angle: 0 up, positive clockwise, y down (the renderer's convention). */
private fun direction(degrees: Float): Offset {
    val radians = degrees * PI.toFloat() / 180f
    return Offset(sin(radians), -cos(radians))
}
