package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.SHOULDER
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Bodyweight exercises, including those on a bar, the parallel bars, a bench, a box or a wall.
 * Poses drawn by hand, in the catalog's order; see [FigureFrames]. A fixed bar seen from the side
 * is a short line at the hands (a pull-up bar, the inverted-row bar); the parallel bars run along
 * the body, so they are a long line. A wall or a post is a tall narrow box. Lying on the back, the
 * head is on the left (the figure's front faces up); face down, it is on the right. The lateral
 * lunge is drawn from the front, like the pilot's lateral raise.
 */
internal object BodyweightFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("bodyweight_push_up") {
            // On the hands and toes, body in one line: arms straight, then the chest just above the floor.
            start {
                place(HAND_NEAR, 70f, 85f); trunk(66.9f)
                arms(180f, 180f)
                legs(246.9f, 246.9f, 155f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 25.4f, 85f); trunk(84.1f)
                arms(-91.2f, 138.6f)
                legs(264.1f, 264.1f, 150f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_push_up_incline") {
            // Hands on a bench, body in one line on the toes: arms straight, then the chest lowered to the bench's edge.
            start {
                place(TOE_NEAR, 28f, 85f); trunk(45.7f)
                arms(172f, 172f)
                legs(225.7f, 225.7f, 159f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(62f, 74f, 22f)
            }
            end {
                place(TOE_NEAR, 28f, 85f); trunk(67.6f)
                arms(-69.9f, 153.4f)
                legs(247.6f, 247.6f, 137.6f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(62f, 74f, 22f)
            }
        }
        figure("bodyweight_push_up_decline") {
            // Feet on a bench behind, hands on the floor: arms straight, then the chest lowered toward the floor, body in one line.
            start {
                place(TOE_NEAR, 27f, 69f); trunk(81.1f)
                arms(180f, 180f)
                legs(261.1f, 261.1f, 101.7f)
                floor(85f, HAND_NEAR, HAND_FAR)
                bench(12f, 74f, 20f)
            }
            end {
                place(TOE_NEAR, 27f, 69f); trunk(103.6f)
                arms(-84.7f, 144.7f)
                legs(283.6f, 283.6f, 178.6f)
                floor(85f, HAND_NEAR, HAND_FAR)
                bench(12f, 74f, 20f)
            }
        }
        figure("bodyweight_push_up_wide") {
            // Hands wide on the floor, body in one line: arms straight, then the chest lowered between the hands.
            start {
                place(TOE_NEAR, 25f, 85f); trunk(67.1f)
                arms(178f, 178f)
                legs(247.1f, 247.1f, 160.2f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 25f, 85f); trunk(85.7f)
                arms(-91.4f, 129.1f)
                legs(265.7f, 265.7f, 147.7f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_push_up_diamond") {
            // Hands together under the chest: arms straight, then lowered with the elbows close to the body.
            start {
                place(TOE_NEAR, 25f, 85f); trunk(66.5f)
                arms(190f, 190f)
                legs(246.5f, 246.5f, 141.4f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 25f, 85f); trunk(85.3f)
                arms(-71.5f, 170.3f)
                legs(265.3f, 265.3f, 151.3f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_push_up_archer") {
            // Hands very wide: arms straight, then lowered toward the near hand while the far arm stays straight.
            start {
                place(TOE_NEAR, 18f, 85f); trunk(66.6f)
                arms(180f, 180f)
                legs(246.6f, 246.6f, 149.4f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 18f, 85f); trunk(84.1f)
                nearArm(-88.8f, 142f); farArm(113.6f, 113.6f)
                legs(264.1f, 264.1f, 150.1f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_push_up_pike") {
            // Hips high in an upside-down V, head between the arms: arms straight, then the top of the head lowered toward the floor.
            start {
                hip(40.8f, 49.5f); trunk(135f, 153f)
                arms(152f, 152f)
                legs(-168.4f, -168.4f, 139.2f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
            end {
                hip(56.2f, 55.2f); trunk(145f, 165f)
                arms(-117.2f, 132.3f)
                legs(-140.6f, -140.6f, 157.2f)
                floor(85f, HAND_NEAR, HAND_FAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_push_up_handstand") {
            // Upside down against the wall, heels on it: arms locked, then the head lowered toward the floor just ahead of the hands.
            start {
                hip(48.7f, 41f); trunk(182f, 180f)
                arms(180f, 180f)
                legs(11.4f, 11.4f, -90f)
                floor(85f, HAND_NEAR, HAND_FAR)
                box(60f, 8f, 4f, 82f)
            }
            end {
                hip(47.6f, 53.4f); trunk(186f, 196f)
                arms(-128.1f, 114.1f)
                legs(13.5f, 13.5f, -90f)
                floor(85f, HAND_NEAR, HAND_FAR)
                box(60f, 8f, 4f, 82f)
            }
        }
        figure("bodyweight_dip_chest") {
            // On the parallel bars, arms locked, torso leaning forward; then lowered until the shoulders are a little below the elbows.
            start {
                place(HAND_NEAR, 52f, 50f); trunk(22f, 17f)
                arms(180f, 180f)
                legs(170f, 220f, 165f)
                bar(28f, 50f, 46f)
            }
            end {
                place(HAND_NEAR, 52f, 50f); trunk(40f, 30f)
                arms(-84.5f, 145.1f)
                legs(165f, 222f, 165f)
                bar(28f, 50f, 46f)
            }
        }
        figure("bodyweight_dip_triceps") {
            // On the parallel bars, arms locked, torso upright; then lowered until the upper arms are about level, elbows back.
            start {
                place(HAND_NEAR, 52f, 50f); trunk(2f)
                arms(180f, 180f)
                legs(175f, 215f, 165f)
                bar(28f, 50f, 46f)
            }
            end {
                place(HAND_NEAR, 52f, 50f); trunk(6f)
                arms(-94.5f, 152.7f)
                legs(172f, 215f, 165f)
                bar(28f, 50f, 46f)
            }
        }
        figure("bodyweight_bench_dip") {
            // Hands on the edge of a bench behind, feet on the floor: arms straight, then lowered until the upper arms are about level.
            start {
                hip(43.6f, 63.9f); trunk(4f)
                arms(196f, 196f)
                legs(130.8f, 130.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 22f)
            }
            end {
                hip(46.4f, 74.8f); trunk(8f)
                arms(-90.2f, 175.6f)
                legs(73.6f, 156.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 22f)
            }
        }
        figure("bodyweight_pull_up") {
            // Hands on the bar: a dead hang, then the chin over the bar.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(8f, 8f)
                legs(172f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-8f, 5f)
                arms(137.3f, -9.4f)
                legs(165f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
        }
        figure("bodyweight_pull_up_wide") {
            // Hands wide on the bar: a dead hang, then the chest driven up toward the bar, elbows down and back.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(8f, 8f)
                legs(172f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-18f, -8f)
                arms(105.4f, -38.4f)
                legs(165f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
        }
        figure("bodyweight_pull_up_neutral") {
            // On the parallel handles, palms facing: a dead hang, then the chin over the handles, elbows close in front.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(8f, 8f)
                legs(172f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-5f, 5f)
                arms(130.8f, -18.3f)
                legs(165f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
        }
        figure("bodyweight_chin_up") {
            // Hands under the bar, palms facing you: a dead hang, then the chest toward the bar, chin over it.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(8f, 8f)
                legs(172f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-12f, 0f)
                arms(120.7f, -26.6f)
                legs(165f, 215f, 160f)
                bar(40f, 16f, 16f)
            }
        }
        figure("bodyweight_row_inverted") {
            // Under a waist-high bar, heels on the floor, body in one line: arms straight, then the chest pulled to the bar.
            start {
                place(ANKLE_NEAR, 82f, 85f); trunk(-70.1f, -75.1f)
                arms(51.1f, 51.1f)
                legs(109.9f, 109.9f, 19.9f)
                floor(85f, ANKLE_NEAR, ANKLE_FAR)
                bar(45.5f, 52f, 16f)
            }
            end {
                place(ANKLE_NEAR, 82f, 85f); trunk(-48.1f, -40.1f)
                arms(169.9f, 34.4f)
                legs(131.9f, 131.9f, 41.9f)
                floor(85f, ANKLE_NEAR, ANKLE_FAR)
                bar(45.5f, 52f, 16f)
            }
        }
        figure("bodyweight_row_inverted_underhand") {
            // Under a waist-high bar, palms up, body in one line: arms straight, then the bar pulled to the lower chest, elbows in.
            start {
                place(ANKLE_NEAR, 82f, 85f); trunk(-67.4f, -72.4f)
                arms(57.5f, 57.5f)
                legs(112.6f, 112.6f, 22.6f)
                floor(85f, ANKLE_NEAR, ANKLE_FAR)
                bar(48f, 52f, 16f)
            }
            end {
                place(ANKLE_NEAR, 82f, 85f); trunk(-45.8f, -37.8f)
                arms(172.9f, 44.2f)
                legs(134.2f, 134.2f, 44.2f)
                floor(85f, ANKLE_NEAR, ANKLE_FAR)
                bar(48f, 52f, 16f)
            }
        }
        figure("bodyweight_squat") {
            // Standing, feet flat; then sitting down between the knees, arms reaching forward for balance, heels down.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(24f, 14f)
                arms(88f, 88f)
                legs(92f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_split_squat") {
            // A long split stance, hands on the hips: tall, then the back knee dropped toward the floor, torso upright.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                nearLeg(133.5f, 178.6f, 90f); farLeg(217.4f, 223.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_bulgarian_split_squat") {
            // The back foot on a bench, hands on the hips: tall, then the front thigh about parallel.
            start {
                place(ANKLE_NEAR, 60f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                nearLeg(180f, 180f, 90f); farLeg(215.4f, 259.3f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
            }
            end {
                place(ANKLE_NEAR, 60f, 85f); trunk(12f)
                arms(-123.4f, 145.5f)
                nearLeg(90f, 200f, 90f); farLeg(210.8f, -23.2f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
            }
        }
        figure("bodyweight_lunge_reverse") {
            // Standing tall, hands on the hips; then a step back, the back knee lowered toward the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_lunge_forward") {
            // Standing tall over the back foot, hands on the hips; then a long step forward into the lunge.
            start {
                place(ANKLE_NEAR, 36f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(ANKLE_NEAR, 64f, 85f); trunk(0f)
                arms(-135.4f, 133.5f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_lunge_lateral") {
            // From the front, hands together in front of the chest: feet together, then a wide step to the side, sitting over the bent leg.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(105f, -116.9f); farArm(-105f, 116.9f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
            }
            end {
                front(); place(TOE_NEAR, 66f, 85f); trunk(6f)
                nearArm(111f, -110.9f); farArm(-99f, 122.9f)
                nearLeg(125f, 175f, 160f); farLeg(218.3f, 218.3f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
            }
        }
        figure("bodyweight_step_up") {
            // One whole foot on a knee-high box; then standing on it, driven up through that foot.
            start {
                hip(44f, 57f); trunk(8f)
                nearArm(194f, 186f); farArm(166f, 172f)
                nearLeg(79.2f, 179f, 90f); farLeg(178f, 221.4f, 90f)
                box(52f, 75f, 22f, 15f)
                groundLine(90f)
            }
            end {
                place(ANKLE_NEAR, 58f, 70f); trunk(0f)
                nearArm(182f, 180f); farArm(178f, 180f)
                legs(180f, 180f, 90f)
                box(52f, 75f, 22f, 15f)
                groundLine(90f)
            }
        }
        figure("bodyweight_pistol_squat") {
            // On one leg, the other held out in front: standing, then sitting all the way down, arms forward, standing heel down.
            start {
                place(ANKLE_NEAR, 42f, 85f); trunk(4f)
                arms(150f, 140f)
                nearLeg(180f, 180f, 90f); farLeg(152f, 152f, 62f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
            }
            end {
                place(ANKLE_NEAR, 42f, 85f); trunk(38f, 30f)
                arms(84f, 86f)
                nearLeg(97.8f, -146.4f, 90f); farLeg(86f, 86f, 0f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
            }
        }
        figure("bodyweight_squat_jump") {
            // A partial squat, arms swung back; then in the air, the body straight and the arms driven up.
            start {
                place(ANKLE_NEAR, 46f, 89f); trunk(36f, 24f)
                arms(256f, 246f)
                legs(118f, 208f, 90f)
                floor(89f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(TOE_NEAR, 47f, 82.5f); trunk(2f)
                arms(36f, 30f)
                legs(182f, 180f, 165f)
                groundLine(94f)
            }
        }
        figure("bodyweight_reverse_nordic") {
            // Kneeling, one line from the knees to the head: upright, then leaning back with the hips straight.
            start {
                place(KNEE_NEAR, 52f, 85f); trunk(0f)
                arms(165f, 35f)
                legs(180f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(KNEE_NEAR, 52f, 85f); trunk(-44f, -36f)
                arms(121f, -9f)
                legs(136f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_glute_bridge") {
            // On the back, knees bent, feet flat, arms on the floor: hips down, then lifted until shoulders, hips and knees line up.
            start {
                place(SHOULDER, 28f, 85f); trunk(-90f)
                arms(90f, 90f)
                legs(24f, 156f, 90f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(SHOULDER, 28f, 85f); trunk(-117.2f, -90f)
                arms(90f, 90f)
                legs(62.8f, 176.9f, 90f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_glute_bridge_one_leg") {
            // On the back, one foot flat, the other leg raised: hips down, then lifted on the planted foot, the raised leg in line.
            start {
                place(SHOULDER, 28f, 85f); trunk(-90f)
                arms(90f, 90f)
                nearLeg(24f, 156f, 90f); farLeg(40f, 40f, -10f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR)
            }
            end {
                place(SHOULDER, 28f, 85f); trunk(-117.2f, -90f)
                arms(90f, 90f)
                nearLeg(62.8f, 176.9f, 90f); farLeg(62.8f, 62.8f, 12.8f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR)
            }
        }
        figure("bodyweight_hip_thrust") {
            // Upper back on the bench, hands on the hips: hips low, then driven up level with the knees.
            start {
                place(ANKLE_NEAR, 66f, 85f); trunk(-62f, -60f)
                arms(162.6f, 71.5f)
                legs(57f, 162f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
            }
            end {
                place(ANKLE_NEAR, 66f, 85f); trunk(-90f, -60f)
                arms(134.6f, 43.5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
            }
        }
        figure("bodyweight_nordic_curl") {
            // Kneeling, ankles held under a pad: upright, then lowered forward in one line from the knees, hands ready.
            start {
                place(KNEE_NEAR, 38f, 85f); trunk(0f)
                arms(172f, 40f)
                legs(180f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                pad(ANKLE_NEAR, 0f, 6f, 3f)
            }
            end {
                place(KNEE_NEAR, 38f, 85f); trunk(58f, 66f)
                arms(150f, 120f)
                legs(238f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                pad(ANKLE_NEAR, 0f, 6f, 3f)
            }
        }
        figure("bodyweight_hamstring_walkout") {
            // A glute bridge on the heels: knees bent, then the heels walked out until the legs are nearly straight, hips still up.
            start {
                place(SHOULDER, 24f, 85f); trunk(-140f, -90f)
                arms(90f, 90f)
                legs(86.5f, 166.2f, -10f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, ANKLE_FAR)
            }
            end {
                place(SHOULDER, 24f, 85f); trunk(-122f, -90f)
                arms(90f, 90f)
                legs(86.3f, 133.9f, 33.9f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR, ANKLE_NEAR, ANKLE_FAR)
            }
        }
        figure("bodyweight_calf_raise") {
            // The balls of both feet on a step, a hand on a post: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                legs(180f, 180f, 60f)
                box(49f, 83f, 16f, 7f)
                box(70f, 26f, 4f, 64f)
                groundLine(90f)
            }
            end {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                legs(180f, 180f, 140f)
                box(49f, 83f, 16f, 7f)
                box(70f, 26f, 4f, 64f)
                groundLine(90f)
            }
        }
        figure("bodyweight_calf_raise_one_leg") {
            // On one foot on the step, the other leg bent behind, a hand on a post: heel down, then up.
            start {
                place(TOE_NEAR, 46f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                nearLeg(180f, 180f, 60f); farLeg(178f, 220f, 150f)
                box(43f, 83f, 16f, 7f)
                box(64f, 26f, 4f, 64f)
                groundLine(90f)
            }
            end {
                place(TOE_NEAR, 46f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                nearLeg(180f, 180f, 140f); farLeg(178f, 220f, 150f)
                box(43f, 83f, 16f, 7f)
                box(64f, 26f, 4f, 64f)
                groundLine(90f)
            }
        }
        figure("bodyweight_crunch") {
            // On the back, knees bent, hands by the head: flat, then the ribs curled toward the hips, shoulder blades up.
            start {
                hip(50f, 85f); trunk(-90f)
                arms(-6.3f, -156.6f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                hip(50f, 85f); trunk(-60f, -42f)
                arms(41.7f, -108.6f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_reverse_crunch") {
            // On the back, hands by the sides, knees bent and lifted: then the hips curled off the floor, knees toward the chest.
            start {
                place(SHOULDER, 28f, 85f); trunk(-90f)
                arms(90f, 90f)
                legs(0f, 90f, 60f)
                floor(85f, SHOULDER, HIP, HAND_NEAR, HAND_FAR)
            }
            end {
                place(SHOULDER, 28f, 85f); trunk(-114f, -90f)
                arms(90f, 90f)
                legs(-35f, 55f, 30f)
                floor(85f, SHOULDER, HAND_NEAR, HAND_FAR)
            }
        }
        figure("bodyweight_sit_up") {
            // On the back, knees bent, feet flat, hands by the head: lying, then curled all the way up to upright.
            start {
                hip(54f, 85f); trunk(-90f)
                arms(-6.3f, -156.6f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                hip(54f, 85f); trunk(-4f, 12f)
                arms(95.7f, -54.6f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_bicycle_crunch") {
            // On the back, shoulders up, hands by the head: one knee in and the other leg long and low, then the legs switched.
            start {
                hip(48f, 85f); trunk(-66f, -46f)
                arms(37.7f, -112.6f)
                nearLeg(-18f, 72f, 40f); farLeg(80f, 80f, 20f)
                floor(85f, HIP)
            }
            end {
                hip(48f, 85f); trunk(-66f, -46f)
                arms(37.7f, -112.6f)
                nearLeg(80f, 80f, 20f); farLeg(-18f, 72f, 40f)
                floor(85f, HIP)
            }
        }
        figure("bodyweight_hanging_knee_raise") {
            // Hanging from the bar, shoulders down: legs straight, then the knees raised toward the chest, hips curled.
            start {
                place(HAND_NEAR, 46f, 10f); trunk(-3f, -8f)
                arms(3f, 3f)
                legs(178f, 180f, 165f)
                bar(38f, 10f, 16f)
            }
            end {
                place(HAND_NEAR, 46f, 10f); trunk(-14f, -6f)
                arms(-2f, -2f)
                legs(52f, 172f, 140f)
                bar(38f, 10f, 16f)
            }
        }
        figure("bodyweight_hanging_leg_raise") {
            // Hanging from the bar, legs together: straight down, then raised straight to hip height, hips curled.
            start {
                place(HAND_NEAR, 42f, 10f); trunk(-3f, -8f)
                arms(3f, 3f)
                legs(178f, 180f, 165f)
                bar(34f, 10f, 16f)
            }
            end {
                place(HAND_NEAR, 42f, 10f); trunk(-16f, -6f)
                arms(-2f, -2f)
                legs(84f, 84f, 40f)
                bar(34f, 10f, 16f)
            }
        }
        figure("bodyweight_captains_chair_knee_raise") {
            // Forearms on the pads, back against the backrest, legs hanging: then the knees raised toward the chest.
            start {
                hip(44f, 48f); trunk(0f)
                arms(180f, 90f)
                legs(178f, 180f, 162f)
                box(33f, 19f, 4f, 74f)
                bar(40f, 46f, 20f)
                groundLine(93f)
            }
            end {
                hip(44f, 48f); trunk(-3f)
                arms(180f, 90f)
                legs(68f, 176f, 150f)
                box(33f, 19f, 4f, 74f)
                bar(40f, 46f, 20f)
                groundLine(93f)
            }
        }
        figure("bodyweight_lying_leg_raise") {
            // On the back, hands by the sides, legs straight: just off the floor, then raised until they point up.
            start {
                place(SHOULDER, 24f, 85f); trunk(-90f)
                arms(90f, 90f)
                legs(81f, 81f, 20f)
                floor(85f, SHOULDER, HIP, HAND_NEAR, HAND_FAR)
            }
            end {
                place(SHOULDER, 24f, 85f); trunk(-90f)
                arms(90f, 90f)
                legs(0f, 0f, -50f)
                floor(85f, SHOULDER, HIP, HAND_NEAR, HAND_FAR)
            }
        }
        figure("bodyweight_dead_bug") {
            // On the back, arms up, knees bent over the hips: then one arm reaching back overhead and the opposite leg long and low.
            start {
                place(SHOULDER, 34f, 85f); trunk(-90f)
                arms(0f, 0f)
                legs(0f, 90f, 40f)
                floor(85f, SHOULDER, HIP)
            }
            end {
                place(SHOULDER, 34f, 85f); trunk(-90f)
                nearArm(-62f, -62f); farArm(0f, 0f)
                nearLeg(0f, 90f, 40f); farLeg(80f, 80f, 20f)
                floor(85f, SHOULDER, HIP)
            }
        }
        figure("bodyweight_bird_dog") {
            // On hands and knees, back flat: then one arm reached forward and the opposite leg back, both in line with the torso.
            start {
                place(KNEE_NEAR, 46f, 85f); trunk(72f, 82f)
                arms(151f, 151f)
                legs(180f, 270f, 270f)
                floor(85f, HAND_NEAR, HAND_FAR, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
            }
            end {
                place(KNEE_FAR, 46f, 85f); trunk(72f, 82f)
                nearArm(151f, 151f); farArm(80f, 80f)
                nearLeg(-98f, -98f, 172f); farLeg(180f, 270f, 270f)
                floor(85f, HAND_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
            }
        }
        figure("bodyweight_back_extension") {
            // On the 45° bench, the pad under the top of the thighs, ankles locked: torso lowered with a flat back, then raised in line with the legs.
            start {
                hip(52f, 48f); trunk(142f, 150f)
                arms(314f, 154f)
                legs(225f, 225f, 135f)
                seat(56.6f, 51.2f, 12f, 225f)
                pad(ANKLE_NEAR, 315f, 6f, 3f)
                platform(ANKLE_NEAR, 10f, 135f)
            }
            end {
                hip(52f, 48f); trunk(45f, 52f)
                arms(217f, 57f)
                legs(225f, 225f, 135f)
                seat(56.6f, 51.2f, 12f, 225f)
                pad(ANKLE_NEAR, 315f, 6f, 3f)
                platform(ANKLE_NEAR, 10f, 135f)
            }
        }
        figure("bodyweight_glute_ham_raise") {
            // Knees just behind the pad, toes against the plate: body level from knees to head, then curled up to upright.
            start {
                place(KNEE_NEAR, 40f, 66f); trunk(90f, 96f)
                arms(255f, 125f)
                legs(-90f, -90f, 180f)
                bench(20f, 71f, 20f)
                pad(KNEE_NEAR, 150f, 6f, 3f)
                pad(ANKLE_NEAR, 0f, 6f, 3f)
                platform(TOE_NEAR, 9f, 0f)
                groundLine(87f)
            }
            end {
                place(KNEE_NEAR, 40f, 66f); trunk(0f, 8f)
                arms(165f, 35f)
                legs(180f, -90f, 180f)
                bench(20f, 71f, 20f)
                pad(KNEE_NEAR, 150f, 6f, 3f)
                pad(ANKLE_NEAR, 0f, 6f, 3f)
                platform(TOE_NEAR, 9f, 0f)
                groundLine(87f)
            }
        }
    }
}
