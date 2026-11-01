package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.SHOULDER
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Kettlebell exercises (spec D4). Poses drawn by hand, in the catalog's order; see [FigureFrames].
 * The kettlebell is the handle around the hand and the bell hanging below it; in the near hand it
 * is drawn over the arm, where the bell rests on the forearm in the rack and overhead. "Rack" is
 * the bell on the outside of the forearm at chest height, elbow tucked by the ribs; "hike" is the
 * bell swung back between the thighs, hinged with the shins near vertical.
 */
internal object KettlebellFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("kettlebell_squat_goblet") {
            // The kettlebell held by the horns against the chest, elbows down: standing, then squatting between the knees, chest up.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(172f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(20f)
                arms(190f, 68f)
                legs(92f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_squat_front_double") {
            // Both kettlebells racked on the forearms at chest height, elbows tucked: standing, then deep with the chest up.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(168f, 8f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(18f)
                arms(186f, 26f)
                legs(95f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_lunge_reverse_racked") {
            // One kettlebell racked on the near side, the free arm down: standing tall, then a step back, the back knee near the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                nearArm(168f, 8f); farArm(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                nearArm(168f, 8f); farArm(178f, 178f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_deadlift") {
            // The kettlebell on the floor under the mid-foot: hips back, flat back, hands on the handle; then standing tall.
            start {
                hip(32.4f, 64f); trunk(52f, 62f)
                arms(182f, 182f)
                legs(109.6f, 192.5f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(172f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_deadlift_romanian") {
            // Standing with the kettlebell at the thighs, arms long; then the hips back, the bell close to the legs, below the knees.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(172f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(65f, 70f)
                arms(199f, 199f)
                legs(160f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_deadlift_one_leg") {
            // On the near leg, the kettlebell in the opposite hand: standing, then hinged as the free leg rises behind.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                nearArm(178f, 178f); farArm(178f, 178f)
                nearLeg(180f, 180f, 90f); farLeg(195f, 215f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                kettlebell(HAND_FAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(78f, 84f)
                nearArm(180f, 180f); farArm(176f, 176f)
                nearLeg(172f, 186f, 90f); farLeg(258f, 258f, 170f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                kettlebell(HAND_FAR)
            }
        }
        figure("kettlebell_swing_two_hand") {
            // Hiked back between the legs, hinged with the shins near vertical; then the hips snap forward and the bell floats to chest height.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 76f)
                arms(214f, 214f)
                legs(150f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(94f, 94f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_swing_one_hand") {
            // One hand on the handle, the free arm loose: hiked back between the legs, then floated to chest height, shoulders square.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 76f)
                nearArm(214f, 214f); farArm(196f, 196f)
                legs(150f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(94f, 94f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_clean") {
            // Hiked back between the legs; then driven up close to the body into the rack, the bell on the forearm, elbow by the ribs.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 76f)
                nearArm(214f, 214f); farArm(196f, 196f)
                legs(150f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(168f, 8f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_snatch") {
            // Hiked back between the legs; then pulled up close to the body in one move and punched to lockout, biceps by the ear.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 76f)
                nearArm(214f, 214f); farArm(196f, 196f)
                legs(150f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f, 8f)
                nearArm(6f, 4f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_clean_press") {
            // Standing, the kettlebell hanging at arm's length (where each clean starts); then cleaned and pressed to lockout overhead.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(176f, 176f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f, 8f)
                nearArm(6f, 4f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_press") {
            // In the rack, forearm vertical under the bell; then pressed up and slightly out to lockout, without leaning back.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(168f, 8f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f, 8f)
                nearArm(8f, 6f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_push_press") {
            // In the rack with a short dip at the knees; then the legs drive the bell to lockout overhead.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(168f, 8f); farArm(176f, 176f)
                legs(162f, 198f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f, 8f)
                nearArm(6f, 4f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_floor_press") {
            // Lying on the floor, knees bent, one kettlebell over the shoulder: pressed up, then the upper arm down on the floor.
            start {
                hip(48f, 85f); trunk(-90f)
                nearArm(0f, 0f); farArm(90f, 90f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_FAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                hip(48f, 85f); trunk(-90f)
                nearArm(90f, -5f); farArm(90f, 90f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_NEAR, ELBOW_FAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_row") {
            // Staggered, the free hand braced on a bench, the back flat and nearly level: the arm hanging, then the bell rowed to the hip.
            start {
                hip(34f, 56f); trunk(72f, 80f)
                nearArm(180f, 180f); farArm(130.5f, 148.4f)
                nearLeg(169.4f, 214f, 90f); farLeg(146.3f, 168.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(60f, 74f, 22f)
                kettlebell(HAND_NEAR)
            }
            end {
                hip(34f, 56f); trunk(72f, 80f)
                nearArm(-65f, 185.3f); farArm(130.5f, 148.4f)
                nearLeg(169.4f, 214f, 90f); farLeg(146.3f, 168.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(60f, 74f, 22f)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_gorilla_row") {
            // Wide and hinged over two bells on the floor: both hands on the handles, then one bell rowed to the hip while the other stays down.
            start {
                hip(32.9f, 65f); trunk(48f, 60f)
                arms(180f, 180f)
                legs(106.5f, 195.3f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                hip(32.9f, 65f); trunk(48f, 60f)
                nearArm(-97.8f, 158.5f); farArm(180f, 180f)
                legs(106.5f, 195.3f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_high_pull") {
            // Hiked back between the legs; then the hips snap and the elbow pulls up and back, the hand near chest height.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(70f, 76f)
                nearArm(214f, 214f); farArm(196f, 196f)
                legs(150f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                nearArm(275f, 100f); farArm(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_halo") {
            // The kettlebell held by the horns in front of the chin; then circled close around the head to behind the neck, hips still.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(160f, 20f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(30f, 235f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_turkish_get_up") {
            // Lying with the kettlebell pressed up, the near knee bent; then half-kneeling with the arm still locked out, eyes on the bell.
            start {
                hip(54f, 85f); trunk(-90f, -84f)
                nearArm(0f, 0f); farArm(90f, 90f)
                nearLeg(40f, 140f, 90f); farLeg(90f, 90f, 0f)
                floor(85f, HIP, SHOULDER, ELBOW_FAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(0f, -15f)
                nearArm(6f, 4f); farArm(180f, 180f)
                nearLeg(90f, 180f, 90f); farLeg(180f, 270f, 270f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_windmill") {
            // From the front, the kettlebell locked out in the near hand: standing; then the hip pushed out and the free hand slid down the far leg.
            start {
                front(); hip(47.5f, 51.5f); trunk(0f)
                nearArm(14f, 12f); farArm(206f, 198f)
                nearLeg(165f, 172f, 160f); farLeg(195f, 188f, 200f)
                floor(88f, TOE_NEAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                front(); hip(53.5f, 54.5f); trunk(-52f, -40f)
                nearArm(0f, 0f); farArm(172.7f, 172.7f)
                nearLeg(151.4f, 207.1f, 160f); farLeg(218.8f, 188.4f, 200f)
                floor(88f, TOE_NEAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_curl") {
            // Held by the horns, arms long, elbows at the sides; then curled to chest height without swinging.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(172f, 38f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
        figure("kettlebell_triceps_overhead") {
            // Held by the horns overhead, arms straight; then lowered behind the head, elbows pointing up.
            start {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f)
                arms(-4f, 2f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f)
                arms(-6f, 210f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                kettlebell(HAND_NEAR)
            }
        }
    }
}
