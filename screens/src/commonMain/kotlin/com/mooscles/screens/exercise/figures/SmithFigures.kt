package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Smith machine exercises (spec D4). Poses drawn by hand, in the catalog's order; see [FigureFrames].
 * The Smith bar is a barbell on vertical rails: each figure draws the rail with `rail` and holds
 * the bar on its x in both frames, so between them the bar only moves straight up or down (the
 * tests hold the drawing to it).
 */
internal object SmithFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("smith_bench_flat") {
            // Lying on the bench, set so the bar runs down onto the lower chest: locked out, then at the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(13.9f, 13.9f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                rail(33f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(123.1f, -18.2f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                rail(33f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_bench_incline") {
            // Back on a bench at about 35°, the bar running down onto the upper chest: locked out, then at the chest.
            start {
                hip(48f, 69f); trunk(-55f)
                arms(12.9f, 12.9f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                rail(38f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(48f, 69f); trunk(-55f)
                arms(150f, -1.9f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                rail(38f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_bench_decline") {
            // Head low, the shins under the roller, the bar lined up with the lower chest: locked out, then at the chest.
            start {
                hip(54f, 60f); trunk(-110f)
                arms(13.5f, 13.5f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                rail(42f, 6f, 88f)
                barbell(HAND_NEAR)
            }
            end {
                hip(54f, 60f); trunk(-110f)
                arms(121.3f, -19.8f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                rail(42f, 6f, 88f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_bench_close_grip") {
            // As the Smith bench press, with the elbows tucked: the bar comes down a little lower on the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(18.7f, 18.7f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                rail(35f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(128.2f, -6.3f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                rail(35f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_shoulder_press") {
            // Upright on the bench, the bar passing just in front of the face: at chin height, then pressed up.
            start {
                hip(42f, 69f); trunk(0f)
                arms(137.9f, -6.8f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                rail(48.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(15.1f, 15.1f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                rail(48.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_upright_row") {
            // Standing close to the bar, at the thighs; then lifted along the rails, elbows leading to shoulder height.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176.6f, 176.6f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(253.3f, 88f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_shrug") {
            // Standing tall, arms straight, the bar in front of the thighs; then the shoulders up toward the ears (the shoulder lift, barbell lot A1): the straight arms carry the bar straight up the rail.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176.6f, 176.6f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f); shoulderLift(SHRUG_LIFT)
                arms(176.6f, 176.6f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_bent_over_row") {
            // Hinged with a flat back, the arms hanging to the bar; then the bar pulled up the rails to the upper stomach.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(196.7f, 196.7f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(55f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(-78.2f, 159.4f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(55f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_squat") {
            // The bar on the upper back, the feet a little in front of it: standing, then down with the thighs about parallel.
            start {
                place(ANKLE_NEAR, 47f, 85f); trunk(0f)
                arms(242.8f, 48.1f)
                legs(176.4f, 176.4f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(44f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 47f, 85f); trunk(20f, 10f)
                arms(-97.2f, 68.1f)
                legs(92f, 203.3f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(44f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_front_squat") {
            // The bar on the front shoulders, elbows high, over the mid-foot: standing, then deep with the torso upright.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(73.9f, 248.9f)
                legs(177.5f, 177.5f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(44f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(15f)
                arms(88.9f, -96.1f)
                legs(95f, 218.5f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(44f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_split_squat") {
            // A long split stance, the front foot ahead of the bar: tall, then the back knee lowered straight down.
            start {
                hip(44.5f, 57.5f); trunk(0f)
                arms(242.8f, 48.1f)
                nearLeg(137.1f, 170.6f, 90f); farLeg(206.4f, 229.3f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                rail(43.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(44.5f, 69.5f); trunk(0f)
                arms(242.8f, 48.1f)
                nearLeg(88.9f, 189f, 90f); farLeg(192.7f, -79.7f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                rail(43.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_lunge_reverse") {
            // Standing under the bar on the upper back; then a step back, the back knee lowered toward the floor.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(0f)
                arms(242.8f, 48.1f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(49f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46.9f, 68.5f); trunk(8f)
                arms(250.8f, 56.1f)
                nearLeg(111f, 227.7f, 90f); farLeg(217.8f, -94f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                rail(49f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_deadlift_romanian") {
            // Standing with the bar against the thighs; then the hips back and the bar lowered along the rails to the knees.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176.6f, 176.6f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(65f, 70f)
                arms(205.6f, 205.6f)
                legs(160f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                rail(45.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
        figure("smith_hip_thrust") {
            // Upper back on the bench, the bar over the hips: hips low, then driven straight up until level with the knees.
            start {
                hip(49f, 78f); trunk(-61.7f, -60f)
                arms(162.2f, 62.9f)
                legs(56.7f, 170.6f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 20f)
                rail(49f, 6f, 90f)
                barbell(HIP)
            }
            end {
                hip(49f, 69f); trunk(-90f, -60f)
                arms(125.4f, 36.4f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 20f)
                rail(49f, 6f, 90f)
                barbell(HIP)
            }
        }
        figure("smith_calf_raise") {
            // The bar on the upper back, the balls of the feet on a step: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(242.8f, 48.1f)
                legs(182.2f, 182.2f, 60f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                rail(47.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
            end {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(242.8f, 48.1f)
                legs(180f, 180f, 140f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                rail(47.5f, 6f, 90f)
                barbell(HAND_NEAR)
            }
        }
    }
}
