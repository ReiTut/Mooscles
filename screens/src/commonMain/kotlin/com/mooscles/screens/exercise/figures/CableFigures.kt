package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Cable exercises. Poses drawn by hand, in the catalog's order; see [FigureFrames]. The cable runs
 * from the pulley, where the exercise sets it (high, at the chest or shoulder, low), to the hand or
 * the ankle strap, and the pulley stays put between the frames. A pulley set beside the body is
 * drawn behind or in front of it, where the cable pulls from. When a cue has the free hand hold the
 * machine, its upright is a tall narrow box with the pulley at its foot. A movement in the frontal
 * plane (the lateral raise, the external rotation, the high curl, the cross-body extension, the hip
 * abduction and adduction, the woodchops) is drawn from the front, like the pilot's lateral raise.
 */
internal object CableFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("cable_chest_fly_mid") {
            // Pulleys at shoulder height behind, one foot forward: arms open back to a stretch, then the hands together in front.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(8f)
                arms(265f, 275f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 34f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 50f, 85f); trunk(8f)
                arms(88f, 94f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 34f, HAND_NEAR)
            }
        }
        figure("cable_chest_fly_high_low") {
            // Pulleys high behind, leaning in: arms up and back, then swept down and together in front of the hips.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(12f)
                arms(-55f, -45f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(15f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 50f, 85f); trunk(12f)
                arms(155f, 165f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(15f, 8f, HAND_NEAR)
            }
        }
        figure("cable_chest_fly_low_high") {
            // Pulleys low behind: arms down and back, then swept up until the hands meet in front of the upper chest.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(5f)
                arms(210f, 200f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 82f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 50f, 85f); trunk(5f)
                arms(80f, 70f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 82f, HAND_NEAR)
            }
        }
        figure("cable_chest_fly_one_arm") {
            // Side-on to a pulley at shoulder height, the free hand on the hip: the arm open to a stretch, then swept across the chest.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(0f)
                nearArm(265f, 275f); farArm(215f, 150f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 34f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 50f, 85f); trunk(0f)
                nearArm(88f, 94f); farArm(215f, 150f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 34f, HAND_NEAR)
            }
        }
        figure("cable_chest_fly_flat_bench") {
            // On a flat bench between low pulleys: the handles over the chest, then the arms opened wide and low, elbows soft.
            start {
                hip(52f, 69f); trunk(-90f)
                arms(-5f, 10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(20f, 74f, 38f)
                cable(15f, 86f, HAND_NEAR)
            }
            end {
                hip(52f, 69f); trunk(-90f)
                arms(130f, 112f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(20f, 74f, 38f)
                cable(15f, 86f, HAND_NEAR)
            }
        }
        figure("cable_chest_fly_incline_bench") {
            // On an incline bench between low pulleys: the handles over the upper chest, then lowered in a wide arc.
            start {
                hip(48f, 69f); trunk(-55f)
                arms(-5f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                cable(16f, 86f, HAND_NEAR)
            }
            end {
                hip(48f, 69f); trunk(-55f)
                arms(160f, 140f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                cable(16f, 86f, HAND_NEAR)
            }
        }
        figure("cable_chest_press") {
            // Pulleys at chest height behind, one foot forward: the handles by the chest, then pressed straight out.
            start {
                place(ANKLE_NEAR, 50f, 85f); trunk(8f)
                arms(240f, 80f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 38f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 50f, 85f); trunk(8f)
                arms(90f, 90f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 38f, HAND_NEAR)
            }
        }
        figure("cable_shoulder_press") {
            // Seated against the back pad between low pulleys: the handles at shoulder height, then pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(150f, -5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                cable(28f, 86f, HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                cable(28f, 86f, HAND_NEAR)
            }
        }
        figure("cable_row_close_neutral") {
            // Seated, feet on the platform: arms reaching forward, then the handle to the stomach.
            start {
                hip(34f, 69f); trunk(12f)
                arms(85f, 92f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
            end {
                hip(34f, 69f); trunk(-5f)
                arms(200f, 85f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
        }
        figure("cable_row_wide_overhand") {
            // A long bar, wide overhand grip: arms reaching forward, then the bar to the lower chest with the elbows high and out.
            start {
                hip(34f, 69f); trunk(12f)
                arms(85f, 92f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
            end {
                hip(34f, 69f); trunk(-5f)
                arms(250f, 80f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
        }
        figure("cable_row_underhand") {
            // A straight bar, underhand grip: arms reaching forward, then the bar to the belly button, elbows close.
            start {
                hip(34f, 69f); trunk(10f)
                arms(87f, 94f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 60f, HAND_NEAR)
            }
            end {
                hip(34f, 69f); trunk(-8f)
                arms(190f, 95f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 60f, HAND_NEAR)
            }
        }
        figure("cable_row_one_arm") {
            // One handle, the free hand on the thigh: the arm reaching forward, then the handle to the side, elbow close.
            start {
                hip(34f, 69f); trunk(12f)
                nearArm(85f, 92f); farArm(160f, 140f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
            end {
                hip(34f, 69f); trunk(-5f)
                nearArm(210f, 85f); farArm(170f, 140f)
                legs(80f, 105f, 0f)
                groundLine(90f)
                bench(24f, 74f, 20f)
                platform(ANKLE_NEAR, 14f, 0f)
                cable(84f, 58f, HAND_NEAR)
            }
        }
        figure("cable_lat_pulldown_wide") {
            // Seated, thighs under the pad: arms up on the bar, then the bar to the upper chest.
            start {
                hip(40f, 69f); trunk(-10f, 0f)
                arms(10f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(42f, 8f, HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(-12f, 0f)
                arms(205f, 50f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(42f, 8f, HAND_NEAR)
            }
        }
        figure("cable_lat_pulldown_underhand") {
            // Underhand at shoulder width: arms straight up, then the bar to the upper chest with the elbows close to the body.
            start {
                hip(40f, 69f); trunk(-10f, 0f)
                arms(4f, 4f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(40f, 8f, HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(-12f, 0f)
                arms(192f, 30f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(40f, 8f, HAND_NEAR)
            }
        }
        figure("cable_lat_pulldown_neutral") {
            // The V-handle, leaning back a little more: arms straight up, then the handle to the upper chest, elbows driven down.
            start {
                hip(40f, 69f); trunk(-12f, -2f)
                arms(3f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(38f, 8f, HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(-16f, -4f)
                arms(200f, 35f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(38f, 8f, HAND_NEAR)
            }
        }
        figure("cable_lat_pulldown_one_arm") {
            // One handle, the free hand on the thigh: the arm up to a full stretch, then the elbow pulled down toward the hip.
            start {
                hip(40f, 69f); trunk(-8f, 0f)
                nearArm(8f, 8f); farArm(160f, 125f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(44f, 8f, HAND_NEAR)
            }
            end {
                hip(40f, 69f); trunk(-8f, 0f)
                nearArm(195f, 25f); farArm(160f, 125f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                cable(44f, 8f, HAND_NEAR)
            }
        }
        figure("cable_straight_arm_pulldown_bar") {
            // Facing a high pulley, hinged a little: arms nearly straight at head height, then swept down to the thighs.
            start {
                place(ANKLE_NEAR, 38f, 85f); trunk(25f)
                arms(55f, 65f)
                legs(172f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(80f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 38f, 85f); trunk(25f)
                arms(168f, 172f)
                legs(172f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(80f, 8f, HAND_NEAR)
            }
        }
        figure("cable_straight_arm_pulldown_rope") {
            // The rope from a high pulley: arms about level with the head, then pulled down past the thighs, the ends spread.
            start {
                place(ANKLE_NEAR, 38f, 85f); trunk(25f)
                arms(60f, 68f)
                legs(172f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(80f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 38f, 85f); trunk(25f)
                arms(184f, 188f)
                legs(172f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(80f, 8f, HAND_NEAR)
            }
        }
        figure("cable_face_pull") {
            // The rope at head height in front, standing tall: arms reaching, then the hands beside the ears, elbows high and back.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(82f, 88f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 28f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(285f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 28f, HAND_NEAR)
            }
        }
        figure("cable_reverse_fly") {
            // Pulleys at shoulder height in front, the cables crossed: arms forward, then opened back in line with the shoulders.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(90f, 95f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(86f, 34f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(268f, 262f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(86f, 34f, HAND_NEAR)
            }
        }
        figure("cable_lateral_raise") {
            // From the front, side-on to a low pulley, the free hand on the machine: the arm down, then raised out to shoulder height.
            start {
                front(); place(TOE_NEAR, 57f, 85f); trunk(0f)
                nearArm(165f, 175f); farArm(-118f, -100f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(20f, 26f, 4f, 64f)
                cable(28f, 84f, HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 57f, 85f); trunk(0f)
                nearArm(88f, 95f); farArm(-118f, -100f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(20f, 26f, 4f, 64f)
                cable(28f, 84f, HAND_NEAR)
            }
        }
        figure("cable_front_raise") {
            // Back to a low pulley, the cable between the legs: the hand by the thigh, then raised forward to shoulder height.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                nearArm(172f, 176f); farArm(182f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                nearArm(88f, 98f); farArm(182f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
        }
        figure("cable_upright_row") {
            // Facing a low pulley with a straight bar: the bar at the thighs, then lifted close to the body, elbows leading.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(70f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(285f, 115f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(70f, 84f, HAND_NEAR)
            }
        }
        figure("cable_external_rotation") {
            // From the front, the pulley at elbow height on the other side: the forearm across the belly, then rotated out, elbow at the side.
            start {
                front(); place(TOE_NEAR, 55f, 85f); trunk(0f)
                nearArm(165f, 255f); farArm(198f, 190f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 46f, HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 55f, 85f); trunk(0f)
                nearArm(165f, 85f); farArm(198f, 190f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 46f, HAND_NEAR)
            }
        }
        figure("cable_curl_straight_bar") {
            // Close to a low pulley, a straight bar underhand: arms long, then curled with the elbows at the sides.
            start {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                arms(178f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                arms(172f, 40f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
        }
        figure("cable_curl_rope") {
            // The rope from a low pulley, palms facing: arms long, then curled toward the shoulders, elbows at the sides.
            start {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                arms(178f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                arms(172f, 32f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
        }
        figure("cable_curl_one_arm") {
            // One handle from a low pulley, the other arm at the side: the arm long, then curled all the way up.
            start {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                nearArm(178f, 176f); farArm(185f, 182f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 42f, 85f); trunk(0f)
                nearArm(172f, 30f); farArm(185f, 182f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(68f, 84f, HAND_NEAR)
            }
        }
        figure("cable_curl_behind_body") {
            // Back to a low pulley, a step forward: the arm pulled a little behind the body, then curled with the elbow kept back.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(4f)
                nearArm(200f, 195f); farArm(178f, 176f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(4f)
                nearArm(196f, 35f); farArm(178f, 176f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
        }
        figure("cable_curl_high") {
            // From the front, between two high pulleys: arms out at shoulder height, then only the forearms curled toward the ears.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(90f, 90f); farArm(-90f, -90f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(86f, 24f, HAND_NEAR)
                cable(14f, 24f, HAND_FAR)
            }
            end {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(90f, -15f); farArm(-90f, 15f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(86f, 24f, HAND_NEAR)
                cable(14f, 24f, HAND_FAR)
            }
        }
        figure("cable_curl_preacher") {
            // On the preacher bench before a low pulley: the arm almost straight on the pad, then the forearm close to vertical.
            start {
                hip(30f, 69f); trunk(20f)
                arms(118f, 150f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                cable(66f, 86f, HAND_NEAR)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(118f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                cable(66f, 86f, HAND_NEAR)
            }
        }
        figure("cable_triceps_pushdown_rope") {
            // Leaning forward a little, elbows at the sides: forearms level, then the arms straight down.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(12f)
                arms(175f, 90f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(12f)
                arms(175f, 175f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
        figure("cable_triceps_pushdown_bar") {
            // A straight bar overhand, elbows pinned: forearms level, then the bar pushed down until the arms are straight.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                arms(176f, 92f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                arms(176f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
        figure("cable_triceps_pushdown_reverse") {
            // A bar underhand, elbows pinned and wrists firm: forearms level, then pushed down until the arms are straight.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                arms(176f, 92f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                arms(176f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
        figure("cable_triceps_overhead_rope") {
            // Back to the pulley, one foot forward, leaning in: the rope behind the head, then the elbows straightened forward and up.
            start {
                place(ANKLE_NEAR, 56f, 85f); trunk(20f)
                arms(20f, 245f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 40f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 56f, 85f); trunk(20f)
                arms(40f, 50f)
                nearLeg(165f, 180f, 90f); farLeg(195f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 40f, HAND_NEAR)
            }
        }
        figure("cable_triceps_pushdown_one_arm") {
            // One handle from a high pulley, the other arm at the side: the forearm level, then pushed down until the arm is straight.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                nearArm(176f, 92f); farArm(186f, 182f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(8f)
                nearArm(176f, 174f); farArm(186f, 182f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
        figure("cable_triceps_cross_body") {
            // From the front, the far hand on a high pulley across the body: the hand by the other shoulder, then the arm straight out to the side.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(112f, -75f); farArm(200f, 190f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 10f, HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(112f, 112f); farArm(200f, 190f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 10f, HAND_NEAR)
            }
        }
        figure("cable_pull_through") {
            // Back to a low pulley, the rope between the legs: standing tall, then the hips pushed back with a flat back.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(0f)
                arms(178f, 182f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(68f, 72f)
                arms(192f, 198f)
                legs(158f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
        }
        figure("cable_glute_kickback") {
            // The strap on the near ankle, facing a low pulley, the hands on the frame: the leg under the body, then kicked back.
            start {
                place(ANKLE_FAR, 44f, 85f); trunk(30f)
                arms(72f, 80f)
                nearLeg(186f, 200f, 95f); farLeg(176f, 184f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(76f, 22f, 4f, 68f)
                cable(72f, 84f, ANKLE_NEAR)
            }
            end {
                place(ANKLE_FAR, 44f, 85f); trunk(30f)
                arms(72f, 80f)
                nearLeg(222f, 236f, 150f); farLeg(176f, 184f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(76f, 22f, 4f, 68f)
                cable(72f, 84f, ANKLE_NEAR)
            }
        }
        figure("cable_hip_abduction") {
            // From the front, the strap on the ankle away from the low pulley, a hand on the machine: the leg in, then lifted out to the side.
            start {
                front(); place(TOE_FAR, 50f, 85f); trunk(0f)
                nearArm(160f, 170f); farArm(-118f, -100f)
                nearLeg(184f, 184f, 160f); farLeg(180f, 180f, 200f)
                floor(85f, TOE_FAR)
                box(20f, 26f, 4f, 64f)
                cable(28f, 84f, ANKLE_NEAR)
            }
            end {
                front(); place(TOE_FAR, 50f, 85f); trunk(0f)
                nearArm(160f, 170f); farArm(-118f, -100f)
                nearLeg(142f, 142f, 120f); farLeg(180f, 180f, 200f)
                floor(85f, TOE_FAR)
                box(20f, 26f, 4f, 64f)
                cable(28f, 84f, ANKLE_NEAR)
            }
        }
        figure("cable_hip_adduction") {
            // From the front, the strap on the ankle nearest the low pulley: the leg pulled out to the side, then brought in across the standing leg.
            start {
                front(); place(TOE_FAR, 44f, 85f); trunk(0f)
                nearArm(160f, 170f); farArm(200f, 190f)
                nearLeg(145f, 145f, 125f); farLeg(180f, 180f, 200f)
                floor(85f, TOE_FAR)
                cable(86f, 84f, ANKLE_NEAR)
            }
            end {
                front(); place(TOE_FAR, 44f, 85f); trunk(0f)
                nearArm(160f, 170f); farArm(200f, 190f)
                nearLeg(196f, 196f, 200f); farLeg(180f, 180f, 200f)
                floor(85f, TOE_FAR)
                cable(86f, 84f, ANKLE_NEAR)
            }
        }
        figure("cable_leg_curl") {
            // The cuff on the near ankle, facing a low pulley, hands on the frame: the leg straight, then the heel curled toward the glutes.
            start {
                place(ANKLE_FAR, 44f, 85f); trunk(10f)
                arms(78f, 84f)
                nearLeg(182f, 190f, 85f); farLeg(178f, 182f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(70f, 22f, 4f, 68f)
                cable(66f, 84f, ANKLE_NEAR)
            }
            end {
                place(ANKLE_FAR, 44f, 85f); trunk(10f)
                arms(78f, 84f)
                nearLeg(182f, 315f, 30f); farLeg(178f, 182f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(70f, 22f, 4f, 68f)
                cable(66f, 84f, ANKLE_NEAR)
            }
        }
        figure("cable_crunch_kneeling") {
            // Kneeling before a high pulley, the rope beside the head: tall from the knees, then the ribs curled toward the hips.
            start {
                place(KNEE_NEAR, 46f, 85f); trunk(15f)
                arms(111f, -33f)
                legs(180f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
            end {
                place(KNEE_NEAR, 46f, 85f); trunk(95f, 125f)
                arms(171f, 22f)
                legs(180f, 270f, 270f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                cable(62f, 8f, HAND_NEAR)
            }
        }
        figure("cable_crunch_standing") {
            // Back to a high pulley, knees soft, the rope beside the head: standing tall, then the torso curled down, hips still.
            start {
                place(ANKLE_NEAR, 48f, 85f); trunk(0f)
                arms(111f, -33f)
                legs(176f, 184f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(16f, 8f, HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 48f, 85f); trunk(40f, 80f)
                arms(150f, 20f)
                legs(176f, 184f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(16f, 8f, HAND_NEAR)
            }
        }
        figure("cable_woodchop_high_low") {
            // From the front, side-on to a high pulley, feet wide: both hands up toward it, then pulled across the body to the other knee.
            start {
                front(); place(TOE_NEAR, 60f, 85f); trunk(-4f)
                arms(-48f, -45f)
                nearLeg(160f, 166f, 150f); farLeg(200f, 194f, 210f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 10f, HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 60f, 85f); trunk(8f)
                arms(150f, 158f)
                nearLeg(160f, 166f, 150f); farLeg(200f, 194f, 210f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 10f, HAND_NEAR)
            }
        }
        figure("cable_woodchop_low_high") {
            // From the front, side-on to a low pulley, knees bent: both hands by the near knee, then pulled across to above the other shoulder.
            start {
                front(); place(TOE_NEAR, 60f, 85f); trunk(-10f)
                arms(218f, 212f)
                nearLeg(156f, 172f, 150f); farLeg(206f, 188f, 210f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 60f, 85f); trunk(6f)
                arms(38f, 32f)
                nearLeg(156f, 172f, 150f); farLeg(206f, 188f, 210f)
                floor(85f, TOE_NEAR, TOE_FAR)
                cable(14f, 84f, HAND_NEAR)
            }
        }
    }
}
