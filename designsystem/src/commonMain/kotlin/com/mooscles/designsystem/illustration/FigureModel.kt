package com.mooscles.designsystem.illustration

import androidx.compose.runtime.Immutable

/**
 * The joints of an exercise figure, seen from the side and facing right. "Near" is the side
 * closer to the viewer, drawn in front; "far" is behind it, drawn fainter. [HEAD] is the center
 * of the head circle, [NECK] the base of the head.
 */
enum class FigureJoint {
    HIP, SHOULDER, NECK, HEAD,
    ELBOW_NEAR, HAND_NEAR, ELBOW_FAR, HAND_FAR,
    KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR,
}

/**
 * The parts of the figure a muscle highlight can color (coarse on purpose: the fine detail is the
 * body map's). Front and back are sides of a segment: the front of the trunk is the chest, the
 * front of a thigh is the quadriceps, the back of the shin is the calf. Limb highlights go on the
 * near limbs, the ones drawn in front.
 */
enum class FigureSegment {
    TORSO_FRONT, TORSO_BACK, SHOULDER, UPPER_ARM_FRONT, UPPER_ARM_BACK, FOREARM,
    HIP, THIGH_FRONT, THIGH_BACK, SHIN_BACK,
}

/**
 * One frame of an exercise: where the hip is in the 100 × 100 box (y grows downward) and the angle
 * of every bone, plus the equipment. Angles are absolute in the image plane, in degrees: 0 points
 * up, positive turns clockwise, so 90 points forward (to the right, where the figure faces) and 180
 * straight down (plan decision 6). The bones keep the lengths of [FigureSkeleton], so the figure
 * never changes size between frames. A non-null [floorY] says the feet rest on a floor at that
 * height (checked by the pose tests; draw the line itself with [FigureProp.Floor]).
 *
 * [frontView] draws the figure seen from the front, for a movement in the frontal plane that a side
 * view cannot show (a lateral raise): "near" is then the figure's left side, on the right of the
 * image, and "far" its right side; both are drawn as body and both take the limb highlights.
 *
 * [shoulderLift] raises the shoulders (a shrug): the shoulder joint, where the arms start, slides up
 * the trunk line toward the neck by that many box units, clamped to `0..`[FigureSkeleton.SHOULDER_LIFT_MAX].
 * The hip, the top of the trunk, the neck, the head and the bone lengths stay as they are: the
 * proportions of [FigureSkeleton] do not change, the arms only start higher. 0, the default, is the
 * shoulder at the top of the trunk.
 */
@Immutable
data class FigurePose(
    val hipX: Float,
    val hipY: Float,
    val trunk: Float,
    val head: Float,
    val upperArmNear: Float,
    val forearmNear: Float,
    val upperArmFar: Float,
    val forearmFar: Float,
    val thighNear: Float,
    val shinNear: Float,
    val footNear: Float,
    val thighFar: Float,
    val shinFar: Float,
    val footFar: Float,
    val floorY: Float? = null,
    val props: List<FigureProp> = emptyList(),
    val frontView: Boolean = false,
    val shoulderLift: Float = 0f,
)

/**
 * The fixed body of every figure, in units of the 100 × 100 box, and the sizes of the equipment.
 * Unitless pose data, not measures: the drawing scales the box to the component.
 */
object FigureSkeleton {
    const val TRUNK = 19f
    const val NECK = 3f
    const val HEAD_RADIUS = 4.5f
    const val UPPER_ARM = 12f
    const val FOREARM = 13f
    const val THIGH = 16f
    const val SHIN = 16f
    const val FOOT = 5.5f

    /**
     * The most a pose's `shoulderLift` raises the shoulder up the trunk: the neck's length (about a
     * sixth of the trunk), so a shrugged shoulder reaches the base of the head and never passes it.
     */
    const val SHOULDER_LIFT_MAX = 3f

    /** A barbell seen from the side: its plate, facing the viewer, centered on the hand. */
    const val PLATE_RADIUS = 8f

    /** A dumbbell seen from the side: one small plate around the hand. */
    const val DUMBBELL_RADIUS = 4f

    /** The kettlebell's handle around the hand, and the bell hanging below it. */
    const val KETTLEBELL_HANDLE = 2.5f
    const val KETTLEBELL_RADIUS = 5f

    /** The pulley at a cable's anchor. */
    const val PULLEY_RADIUS = 2.5f

    /** The legs of a bench, and the post of a seat, straight down from the pad. */
    const val BENCH_LEG = 16f

    /** How far a floor line reaches past the rest of the figure on each side. */
    const val FLOOR_OVERHANG = 6f
}

/**
 * Equipment drawn with a pose, in the box's units and the pose's angle convention (an [angle] of 90
 * runs forward, flat). Hand-held weights follow the joint they are [at].
 */
@Immutable
sealed interface FigureProp {
    /** A barbell held at [at]: the plate faces the viewer, so it is a disc around the hand. */
    data class Barbell(val at: FigureJoint) : FigureProp

    /** A dumbbell held at [at]: a small disc around the hand. */
    data class Dumbbell(val at: FigureJoint) : FigureProp

    /**
     * A kettlebell held at [at]: the handle around the hand, the bell hanging below. In the near
     * hand it is drawn over the arm, where its bell rests on the outside of the forearm (rack,
     * overhead), so the arm's gap does not erase it.
     */
    data class Kettlebell(val at: FigureJoint) : FigureProp

    /** A bench pad from ([x], [y]) for [length] at [angle] (90 is flat; tilt it for incline or decline), legs down from both ends. */
    data class Bench(val x: Float, val y: Float, val length: Float, val angle: Float) : FigureProp

    /** A cable from the pulley at ([anchorX], [anchorY]) to the joint it is pulled [to]. */
    data class Cable(val anchorX: Float, val anchorY: Float, val to: FigureJoint) : FigureProp

    /** A machine seat or back pad from ([x], [y]) for [length] at [angle], on one post down from its middle. */
    data class Seat(val x: Float, val y: Float, val length: Float, val angle: Float) : FigureProp

    /** A round machine pad (a roller) centered at ([x], [y]). */
    data class Pad(val x: Float, val y: Float, val radius: Float) : FigureProp

    /** A fixed bar (pull-up bar, parallel bars) from ([x], [y]) forward for [length]. */
    data class Bar(val x: Float, val y: Float, val length: Float) : FigureProp

    /** A box (plyo box, step) with its top-left corner at ([x], [y]). */
    data class Box(val x: Float, val y: Float, val width: Float, val height: Float) : FigureProp

    /** A foot platform (leg press) centered on the joint it is [at], [length] long at [angle]. */
    data class Platform(val at: FigureJoint, val length: Float, val angle: Float) : FigureProp

    /**
     * A Smith machine's guide rail, seen from the side: a vertical line at [x] from [top] down to
     * [bottom]. The bar slides along it, so a Smith figure holds its [Barbell] on the rail's x in
     * both frames; the rail itself stays put.
     */
    data class Rail(val x: Float, val top: Float, val bottom: Float) : FigureProp

    /** A floor line at height [y], under the whole figure and its equipment. */
    data class Floor(val y: Float) : FigureProp
}
