package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.SHOULDER
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Machine exercises, selectorized and plate-loaded. Poses drawn by hand, in the catalog's order;
 * see [FigureFrames]. A machine is drawn from its contact points, as in the pilot: the seat and the
 * back or chest pad (a seat, a bench on end), the rollers (pads that follow the limb they push),
 * the foot platform, and a handle as a short line across the hand. No weight stack and no lever
 * arms, which would read as cables; the exceptions are the T-bar row, the leverage squat and the
 * plate-loaded standing calf raise, whose lever runs from a pivot on the floor like the barbell
 * lot's landmine, and the belt squat's chain. The selectorized standing calf raise shows the
 * machine's column, a tall narrow box, with the shoulder pad against it.
 * The hip abduction and adduction are drawn from the front, like the pilot's lateral raise, and so
 * is the machine lateral raise. The torso rotation has no figure ([ExerciseFigures.NO_FIGURE]).
 */
internal object MachineFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("selectorized_chest_press") {
            // Seated, back on the pad, the handles at mid-chest: elbows back, then arms extended forward.
            start {
                hip(42f, 69f); trunk(0f)
                arms(215f, 75f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 165f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(93f, 93f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 3f)
            }
        }
        figure("selectorized_chest_press_incline") {
            // The back pad reclined, the handles at the upper chest: elbows back, then pressed up and forward.
            start {
                hip(42f, 69f); trunk(-20f)
                arms(200f, 60f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.5f, 72f, 26f, -20f)
                platform(HAND_NEAR, 10f, 150f)
            }
            end {
                hip(42f, 69f); trunk(-20f)
                arms(55f, 55f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.5f, 72f, 26f, -20f)
                platform(HAND_NEAR, 10f, 145f)
            }
        }
        figure("selectorized_pec_fly") {
            // Seated, the handles at shoulder height, elbows softly bent: arms open back in line with the body, then together in front.
            start {
                hip(42f, 69f); trunk(0f)
                arms(266f, 280f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 10f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(84f, 98f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 8f)
            }
        }
        figure("selectorized_shoulder_press") {
            // Seated, back on the pad: the handles at shoulder height, then pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(140f, 5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 95f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 93f)
            }
        }
        figure("selectorized_lateral_raise") {
            // From the front, seated, the pads on the outside of the upper arms: arms down, then raised out to shoulder height.
            start {
                front(); hip(50f, 53.6f); trunk(0f)
                nearArm(148f, 165f); farArm(212f, 195f)
                nearLeg(130f, 178f, 160f); farLeg(230f, 182f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(43f, 58.6f, 14f, 31.4f)
                pad(ELBOW_NEAR, 70f, 5f, 3f); pad(ELBOW_FAR, -70f, 5f, 3f)
            }
            end {
                front(); hip(50f, 53.6f); trunk(0f)
                nearArm(85f, 110f); farArm(-85f, -110f)
                nearLeg(130f, 178f, 160f); farLeg(230f, 182f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(43f, 58.6f, 14f, 31.4f)
                pad(ELBOW_NEAR, -5f, 5f, 3f); pad(ELBOW_FAR, 5f, 5f, 3f)
            }
        }
        figure("selectorized_reverse_fly") {
            // Seated facing the pad, the chest against it: arms forward at shoulder height, then opened back in line with the shoulders.
            start {
                hip(38f, 69f); trunk(4f)
                arms(90f, 95f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(26f, 74f, 22f)
                bench(44.5f, 70f, 18f, 4f)
                platform(HAND_NEAR, 10f, 5f)
            }
            end {
                hip(38f, 69f); trunk(4f)
                arms(268f, 262f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(26f, 74f, 22f)
                bench(44.5f, 70f, 18f, 4f)
                platform(HAND_NEAR, 10f, 172f)
            }
        }
        figure("selectorized_lat_pulldown") {
            // Seated, thighs under the pad: arms up on the handles, then the elbows driven down to the sides.
            start {
                hip(40f, 69f); trunk(-10f, 0f)
                arms(10f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 100f)
            }
            end {
                hip(40f, 69f); trunk(-12f, 0f)
                arms(210f, 55f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 145f)
            }
        }
        figure("selectorized_seated_row") {
            // Seated, the chest on the pad, the handles at mid-chest: arms extended, then the elbows pulled back past the torso.
            start {
                hip(36f, 69f); trunk(5f)
                arms(95f, 95f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 22f)
                bench(43f, 70f, 18f, 5f)
                platform(HAND_NEAR, 10f, 5f)
            }
            end {
                hip(36f, 69f); trunk(2f)
                arms(238f, 95f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 22f)
                bench(43f, 70f, 18f, 5f)
                platform(HAND_NEAR, 10f, 5f)
            }
        }
        figure("selectorized_high_row") {
            // Seated, the chest on the pad: arms reaching up and forward to the handles, then pulled down and back, elbows first.
            start {
                hip(36f, 69f); trunk(5f)
                arms(35f, 35f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 22f)
                bench(43f, 70f, 16f, 5f)
                platform(HAND_NEAR, 10f, 125f)
            }
            end {
                hip(36f, 69f); trunk(2f)
                arms(215f, 80f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 22f)
                bench(43f, 70f, 16f, 5f)
                platform(HAND_NEAR, 10f, 170f)
            }
        }
        figure("selectorized_pullover") {
            // Seated, back on the pad: arms overhead, elbows bent, then swept down in an arc until the bar reaches the stomach.
            start {
                hip(42f, 69f); trunk(0f)
                arms(-15f, -50f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 40f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(165f, 80f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 170f)
            }
        }
        figure("selectorized_assisted_pull_up") {
            // Kneeling on the assist pad, hands on the bar: a full hang, then the chin over the bar.
            start {
                place(HAND_NEAR, 48f, 16f); trunk(-6f, -25f)
                arms(8f, 8f)
                legs(175f, 265f, 265f)
                bar(40f, 16f, 16f)
                pad(KNEE_NEAR, 200f, 5f, 3f)
            }
            end {
                place(HAND_NEAR, 48f, 16f); trunk(-8f, 5f)
                arms(137.3f, -9.4f)
                legs(170f, 262f, 262f)
                bar(40f, 16f, 16f)
                pad(KNEE_NEAR, 200f, 5f, 3f)
            }
        }
        figure("selectorized_assisted_dip") {
            // Kneeling on the assist pad, hands on the bars: arms locked, torso upright, then lowered until the upper arms are about level.
            start {
                place(HAND_NEAR, 52f, 46f); trunk(2f)
                arms(180f, 180f)
                legs(175f, 265f, 265f)
                bar(28f, 46f, 46f)
                pad(KNEE_NEAR, 200f, 5f, 3f)
            }
            end {
                place(HAND_NEAR, 52f, 46f); trunk(6f)
                arms(-94.5f, 152.7f)
                legs(172f, 262f, 262f)
                bar(28f, 46f, 46f)
                pad(KNEE_NEAR, 200f, 5f, 3f)
            }
        }
        figure("selectorized_biceps_curl") {
            // Seated, the upper arms on the slanted pad, elbows at the pivot: arms nearly straight, then curled up.
            start {
                hip(30f, 69f); trunk(20f)
                arms(118f, 150f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(36.5f, 58.5f, 14f, 118f)
                platform(HAND_NEAR, 10f, 60f)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(118f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(36.5f, 58.5f, 14f, 118f)
                platform(HAND_NEAR, 10f, 100f)
            }
        }
        figure("selectorized_triceps_extension") {
            // Seated, the backs of the upper arms on the pad, elbows at the pivot: hands up by the shoulders, then pushed out straight.
            start {
                hip(30f, 69f); trunk(10f)
                arms(95f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(33.9f, 55.3f, 12f, 95f)
                platform(HAND_NEAR, 10f, 100f)
            }
            end {
                hip(30f, 69f); trunk(10f)
                arms(95f, 95f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(33.9f, 55.3f, 12f, 95f)
                platform(HAND_NEAR, 10f, 5f)
            }
        }
        figure("selectorized_triceps_press") {
            // Seated tall, the handles at the sides: elbows bent about 90° and back, then pushed down until the arms are straight.
            start {
                hip(42f, 69f); trunk(0f)
                arms(235f, 145f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 55f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(160f, 165f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 75f)
            }
        }
        figure("selectorized_leg_press") {
            // Seated against the reclined pad, feet on the platform: knees bent about 90°, then the legs pushed almost straight.
            start {
                hip(30f, 66f); trunk(-30f)
                arms(165f, 110f)
                legs(40f, 130f, 0f)
                seat(22f, 71f, 16f)
                bench(25.7f, 68.5f, 24f, -30f)
                platform(ANKLE_NEAR, 16f, 0f)
                groundLine(87f)
            }
            end {
                hip(30f, 66f); trunk(-30f)
                arms(165f, 110f)
                legs(80f, 100f, 0f)
                seat(22f, 71f, 16f)
                bench(25.7f, 68.5f, 24f, -30f)
                platform(ANKLE_NEAR, 16f, 0f)
                groundLine(87f)
            }
        }
        figure("selectorized_leg_extension") {
            // Seated, the pad on the lower shins: knees bent, then the legs straight.
            start {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 180f, 100f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(ANKLE_NEAR, 70f, 5.5f, 3f)
            }
            end {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 90f, 30f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(ANKLE_NEAR, -20f, 5.5f, 3f)
            }
        }
        figure("selectorized_leg_curl_seated") {
            // Seated, the thigh pad snug above the knees, the leg pad above the heels: legs almost straight, then the heels curled down and back.
            start {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 95f, 10f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(KNEE_NEAR, -20f, 6f, 3f)
                pad(ANKLE_NEAR, 185f, 5.5f, 3f)
            }
            end {
                hip(40f, 60f); trunk(0f)
                arms(180f, 150f)
                legs(90f, 200f, 110f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, 0f)
                pad(KNEE_NEAR, -20f, 6f, 3f)
                pad(ANKLE_NEAR, 290f, 5.5f, 3f)
            }
        }
        figure("selectorized_leg_curl_lying") {
            // Face down on the bench, the pad above the heels: legs straight, then curled.
            start {
                hip(46f, 60f); trunk(90f)
                arms(100f, 150f)
                legs(-90f, -90f, 180f)
                bench(32f, 65f, 44f)
                pad(ANKLE_NEAR, 20f, 5.5f, 3f)
            }
            end {
                hip(46f, 60f); trunk(90f)
                arms(100f, 150f)
                legs(-90f, 20f, -60f)
                bench(32f, 65f, 44f)
                pad(ANKLE_NEAR, 130f, 5.5f, 3f)
            }
        }
        figure("selectorized_leg_curl_standing") {
            // Standing on one leg, the other thigh against the pad, forearms on the machine: the leg straight, then the heel curled toward the glute.
            start {
                hip(42f, 47f); trunk(15f)
                arms(120f, 90f)
                nearLeg(175f, 195f, 150f); farLeg(180f, 180f, 90f)
                box(32f, 84f, 16f, 6f)
                box(62f, 39.7f, 6f, 50.3f)
                groundLine(90f)
                pad(KNEE_NEAR, 70f, 5.5f, 3f)
                pad(ANKLE_NEAR, 275f, 5.5f, 3f)
            }
            end {
                hip(42f, 47f); trunk(15f)
                arms(120f, 90f)
                nearLeg(175f, 300f, 250f); farLeg(180f, 180f, 90f)
                box(32f, 84f, 16f, 6f)
                box(62f, 39.7f, 6f, 50.3f)
                groundLine(90f)
                pad(KNEE_NEAR, 70f, 5.5f, 3f)
                pad(ANKLE_NEAR, 30f, 5.5f, 3f)
            }
        }
        figure("selectorized_hip_abduction") {
            // From the front, seated, the pads on the outside of the knees: knees close, then pushed apart.
            start {
                front(); hip(50f, 52f); trunk(0f)
                nearArm(150f, 175f); farArm(210f, 185f)
                nearLeg(140f, 178f, 160f); farLeg(220f, 182f, 200f)
                box(43f, 57f, 14f, 33f)
                groundLine(90f)
                pad(KNEE_NEAR, 90f, 5.5f, 3f); pad(KNEE_FAR, 270f, 5.5f, 3f)
            }
            end {
                front(); hip(50f, 52f); trunk(0f)
                nearArm(150f, 175f); farArm(210f, 185f)
                nearLeg(110f, 170f, 160f); farLeg(250f, 190f, 200f)
                box(43f, 57f, 14f, 33f)
                groundLine(90f)
                pad(KNEE_NEAR, 90f, 5.5f, 3f); pad(KNEE_FAR, 270f, 5.5f, 3f)
            }
        }
        figure("selectorized_hip_adduction") {
            // From the front, seated, the pads on the inside of the knees: knees wide, then squeezed together.
            start {
                front(); hip(50f, 52f); trunk(0f)
                nearArm(150f, 175f); farArm(210f, 185f)
                nearLeg(110f, 170f, 160f); farLeg(250f, 190f, 200f)
                box(43f, 57f, 14f, 33f)
                groundLine(90f)
                pad(KNEE_NEAR, 270f, 5.5f, 3f); pad(KNEE_FAR, 90f, 5.5f, 3f)
            }
            end {
                front(); hip(50f, 52f); trunk(0f)
                nearArm(150f, 175f); farArm(210f, 185f)
                nearLeg(140f, 178f, 160f); farLeg(220f, 182f, 200f)
                box(43f, 57f, 14f, 33f)
                groundLine(90f)
                pad(KNEE_NEAR, 270f, 5.5f, 3f); pad(KNEE_FAR, 90f, 5.5f, 3f)
            }
        }
        figure("selectorized_glute_kickback") {
            // Leaning on the forearm pad, standing on one leg, the pad behind the other knee: thigh forward, then driven back in line with the torso.
            start {
                hip(40f, 53f); trunk(55f)
                arms(140f, 90f)
                nearLeg(145f, 190f, 100f); farLeg(180f, 180f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(62f, 56.3f, 14f, 33.7f)
                pad(KNEE_NEAR, 235f, 5.5f, 3f)
            }
            end {
                hip(40f, 53f); trunk(55f)
                arms(140f, 90f)
                nearLeg(235f, 235f, 190f); farLeg(180f, 180f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                box(62f, 56.3f, 14f, 33.7f)
                pad(KNEE_NEAR, 325f, 5.5f, 3f)
            }
        }
        figure("selectorized_calf_raise_standing") {
            // Under the shoulder pads that ride up the machine's column, the balls of the feet on the step, knees straight: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 50f, 78f); trunk(0f)
                arms(165f, 25f)
                legs(180f, 180f, 60f)
                box(47f, 83f, 16f, 7f)
                box(29f, 18f, 4f, 72f)
                groundLine(90f)
                pad(SHOULDER, -80f, 7f, 3.5f)
            }
            end {
                place(TOE_NEAR, 50f, 78f); trunk(0f)
                arms(165f, 25f)
                legs(180f, 180f, 140f)
                box(47f, 83f, 16f, 7f)
                box(29f, 18f, 4f, 72f)
                groundLine(90f)
                pad(SHOULDER, -80f, 7f, 3.5f)
            }
        }
        figure("selectorized_calf_raise_seated") {
            // Seated against the reclined pad, legs almost straight, the balls of the feet on the platform: ankles flexed, then pushed onto the toes.
            start {
                hip(32f, 62f); trunk(-35f)
                arms(165f, 110f)
                legs(80f, 95f, -10f)
                seat(24f, 67f, 16f)
                bench(27.3f, 64.6f, 22f, -35f)
                platform(TOE_NEAR, 10f, -10f)
                groundLine(83f)
            }
            end {
                hip(32f, 62f); trunk(-35f)
                arms(165f, 110f)
                legs(80f, 95f, 40f)
                seat(24f, 67f, 16f)
                bench(27.3f, 64.6f, 22f, -35f)
                platform(TOE_NEAR, 10f, 40f)
                groundLine(83f)
            }
        }
        figure("selectorized_abdominal_crunch") {
            // Seated, the pads at the upper chest: tall against the back pad, then the ribs curled down toward the hips.
            start {
                hip(42f, 69f); trunk(-3f)
                arms(155f, 25f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                pad(SHOULDER, 125f, 6f, 3f)
            }
            end {
                hip(42f, 69f); trunk(40f, 60f)
                arms(198f, 68f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                pad(SHOULDER, 168f, 6f, 3f)
            }
        }
        figure("selectorized_back_extension") {
            // Seated, feet planted, the pad across the shoulder blades: leaning forward, then pushed back to upright.
            start {
                hip(40f, 69f); trunk(45f)
                arms(195f, 65f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(SHOULDER, 285f, 6.5f, 3f)
            }
            end {
                hip(40f, 69f); trunk(0f)
                arms(150f, 20f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(SHOULDER, 240f, 6.5f, 3f)
            }
        }
        figure("plate_chest_press") {
            // Seated, back on the pad, the handles at mid-chest: elbows back, then pressed forward on the levers' converging path.
            start {
                hip(42f, 69f); trunk(0f)
                arms(215f, 80f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 170f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(97f, 97f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 7f)
            }
        }
        figure("plate_chest_press_incline") {
            // The back pad reclined, the handles at the upper chest: elbows back, then pressed up and forward.
            start {
                hip(42f, 69f); trunk(-22f)
                arms(200f, 55f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.4f, 72f, 26f, -22f)
                platform(HAND_NEAR, 10f, 145f)
            }
            end {
                hip(42f, 69f); trunk(-22f)
                arms(48f, 48f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.4f, 72f, 26f, -22f)
                platform(HAND_NEAR, 10f, 138f)
            }
        }
        figure("plate_chest_press_decline") {
            // Seated, back on the pad, the handles at the lower chest: elbows back, then pressed forward and down.
            start {
                hip(42f, 69f); trunk(-8f)
                arms(225f, 105f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.2f, 73.5f, 26f, -8f)
                platform(HAND_NEAR, 10f, 15f)
            }
            end {
                hip(42f, 69f); trunk(-8f)
                arms(122f, 122f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.2f, 73.5f, 26f, -8f)
                platform(HAND_NEAR, 10f, 32f)
            }
        }
        figure("plate_chest_press_one_arm") {
            // Seated, back on the pad, one handle at mid-chest, the other hand holding on: the near arm pressed forward alone.
            start {
                hip(42f, 69f); trunk(0f)
                nearArm(215f, 80f); farArm(215f, 80f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 170f)
            }
            end {
                hip(42f, 69f); trunk(0f)
                nearArm(95f, 95f); farArm(215f, 80f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 5f)
            }
        }
        figure("plate_shoulder_press") {
            // Seated, back on the slightly reclined pad: the handles at shoulder height, then pressed up and a little forward.
            start {
                hip(42f, 69f); trunk(-8f)
                arms(140f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.2f, 73.5f, 26f, -8f)
                platform(HAND_NEAR, 10f, 100f)
            }
            end {
                hip(42f, 69f); trunk(-8f)
                arms(12f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37.2f, 73.5f, 26f, -8f)
                platform(HAND_NEAR, 10f, 100f)
            }
        }
        figure("plate_row_chest_supported") {
            // Seated, the chest on the slanted pad: arms reaching forward and down, then the elbows pulled back past the torso.
            start {
                hip(34f, 69f); trunk(25f)
                arms(112f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 18f)
                seat(40.2f, 67.5f, 16f, 25f)
                platform(HAND_NEAR, 10f, 22f)
            }
            end {
                hip(34f, 69f); trunk(25f)
                arms(232f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 18f)
                seat(40.2f, 67.5f, 16f, 25f)
                platform(HAND_NEAR, 10f, 22f)
            }
        }
        figure("plate_row_high") {
            // Seated, thighs under the pad: arms reaching up and forward, then the handles pulled down and back to the chest.
            start {
                hip(40f, 69f); trunk(-5f, 0f)
                arms(28f, 28f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 118f)
            }
            end {
                hip(40f, 69f); trunk(-5f, 0f)
                arms(215f, 75f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 165f)
            }
        }
        figure("plate_row_low") {
            // Seated, the chest on the pad, feet braced: arms reaching forward and low, then the handles pulled to the waist.
            start {
                hip(34f, 69f); trunk(5f)
                arms(105f, 105f)
                legs(80f, 110f, 20f)
                seat(22f, 74f, 22f)
                bench(41f, 70f, 16f, 5f)
                platform(ANKLE_NEAR, 12f, 20f)
                groundLine(90f)
                platform(HAND_NEAR, 10f, 15f)
            }
            end {
                hip(34f, 69f); trunk(2f)
                arms(215f, 100f)
                legs(80f, 110f, 20f)
                seat(22f, 74f, 22f)
                bench(41f, 70f, 16f, 5f)
                platform(ANKLE_NEAR, 12f, 20f)
                groundLine(90f)
                platform(HAND_NEAR, 10f, 10f)
            }
        }
        figure("plate_row_one_arm") {
            // The chest on the slanted pad, the free hand on its handle: one arm reaching forward and down, then rowed back.
            start {
                hip(34f, 69f); trunk(25f)
                nearArm(112f, 112f); farArm(112f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 18f)
                seat(40.2f, 67.5f, 16f, 25f)
                platform(HAND_NEAR, 10f, 22f)
            }
            end {
                hip(34f, 69f); trunk(25f)
                nearArm(232f, 112f); farArm(112f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(24f, 74f, 18f)
                seat(40.2f, 67.5f, 16f, 25f)
                platform(HAND_NEAR, 10f, 22f)
            }
        }
        figure("plate_lat_pulldown") {
            // Seated, thighs under the pad: arms up on the handles, then the elbows driven down to the sides.
            start {
                hip(40f, 69f); trunk(-10f, 0f)
                arms(14f, 14f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 104f)
            }
            end {
                hip(40f, 69f); trunk(-12f, 0f)
                arms(210f, 50f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(28f, 74f, 22f)
                pad(KNEE_NEAR, -10f, 7f, 3f)
                platform(HAND_NEAR, 10f, 140f)
            }
        }
        figure("plate_t_bar_row_supported") {
            // Standing on the foot plates, the chest on the slanted pad: arms hanging to the handles, then pulled toward the chest.
            start {
                place(ANKLE_NEAR, 36f, 85f); trunk(55f, 70f)
                arms(180f, 180f)
                legs(160f, 190f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(40f, 56.8f, 14f, 55f)
                cable(80f, 87f, HAND_NEAR)
                platform(HAND_NEAR, 10f, 90f)
            }
            end {
                place(ANKLE_NEAR, 36f, 85f); trunk(55f, 70f)
                arms(235f, 170f)
                legs(160f, 190f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(40f, 56.8f, 14f, 55f)
                cable(80f, 87f, HAND_NEAR)
                platform(HAND_NEAR, 10f, 80f)
            }
        }
        figure("plate_hack_squat") {
            // Back on the sled, shoulders under the pads, feet on the slanted platform: standing tall, then lowered along the rails.
            start {
                place(ANKLE_NEAR, 66f, 82f); trunk(-35f)
                arms(160f, 140f)
                legs(138.1f, 160.6f, 75f)
                bench(53.9f, 69.4f, 33f, -35f)
                pad(SHOULDER, 55f, 5f, 3f)
                platform(ANKLE_NEAR, 16f, 75f)
                groundLine(90f)
            }
            end {
                place(ANKLE_NEAR, 66f, 82f); trunk(-35f)
                arms(160f, 140f)
                legs(95.7f, 209.7f, 75f)
                bench(53.9f, 69.4f, 33f, -35f)
                pad(SHOULDER, 55f, 5f, 3f)
                platform(ANKLE_NEAR, 16f, 75f)
                groundLine(90f)
            }
        }
        figure("plate_pendulum_squat") {
            // Back on the pad, shoulders under the pads, more upright than the hack squat: standing, then swung down as deep as control allows.
            start {
                place(ANKLE_NEAR, 64f, 82f); trunk(-20f)
                arms(160f, 140f)
                legs(139.8f, 170.4f, 80f)
                bench(51.4f, 69.8f, 34f, -20f)
                pad(SHOULDER, 70f, 5f, 3f)
                platform(ANKLE_NEAR, 16f, 80f)
                groundLine(90f)
            }
            end {
                place(ANKLE_NEAR, 64f, 82f); trunk(-20f)
                arms(160f, 140f)
                legs(90.4f, 210.4f, 80f)
                bench(51.4f, 69.8f, 34f, -20f)
                pad(SHOULDER, 70f, 5f, 3f)
                platform(ANKLE_NEAR, 16f, 80f)
                groundLine(90f)
            }
        }
        figure("plate_belt_squat") {
            // The belt around the hips, chained to the lever below, hands on the rail: standing, then sitting down between the heels, torso upright.
            start {
                hip(48f, 48f); trunk(0f)
                arms(141.7f, 125.7f)
                legs(180f, 180f, 90f)
                floor(80f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bar(61f, 46f, 10f)
                box(70f, 46f, 3f, 39f)
                cable(45f, 89f, HIP)
            }
            end {
                hip(42f, 66f); trunk(8f)
                arms(119.5f, 57.1f)
                legs(95.2f, 218.4f, 90f)
                floor(80f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bar(61f, 46f, 10f)
                box(70f, 46f, 3f, 39f)
                cable(45f, 89f, HIP)
            }
        }
        figure("plate_leverage_squat") {
            // Shoulders under the lever's pads, feet a little in front: standing, then squatted down, knees over the toes.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(-5f)
                arms(160f, 140f)
                legs(159.7f, 178.4f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, SHOULDER)
                pad(SHOULDER, 70f, 5f, 3f)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(15f)
                arms(180f, 160f)
                legs(95.3f, 194.2f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, SHOULDER)
                pad(SHOULDER, 90f, 5f, 3f)
            }
        }
        figure("plate_leg_press_45") {
            // Reclined against the back pad, feet on the sled: legs almost straight, then the knees bent.
            start {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(150f, 120f)
                legs(21.2f, 35.1f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                groundLine(86.3f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
            end {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(150f, 120f)
                legs(-21.5f, 65f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                groundLine(86.3f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
        }
        figure("plate_leg_press_horizontal") {
            // Seated against the reclined pad, feet on the upright platform: knees deeply bent, then the legs pushed almost straight.
            start {
                hip(34f, 66f); trunk(-40f)
                arms(165f, 110f)
                legs(32f, 132f, 0f)
                seat(26f, 71f, 16f)
                bench(30.8f, 69.8f, 24f, -40f)
                platform(ANKLE_NEAR, 16f, 0f)
                groundLine(87f)
            }
            end {
                hip(34f, 66f); trunk(-40f)
                arms(165f, 110f)
                legs(82f, 98f, 0f)
                seat(26f, 71f, 16f)
                bench(30.8f, 69.8f, 24f, -40f)
                platform(ANKLE_NEAR, 16f, 0f)
                groundLine(87f)
            }
        }
        figure("plate_leg_press_one_leg") {
            // Reclined on the 45° press, one foot in the middle of the sled, the other resting bent: that leg almost straight, then bent.
            start {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(150f, 120f)
                nearLeg(21.2f, 35.1f, -45f); farLeg(-21.5f, 65f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                groundLine(86.3f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
            end {
                hip(40f, 72f); trunk(-55f, -40f)
                arms(150f, 120f)
                nearLeg(-21.5f, 65f, -45f); farLeg(-21.5f, 65f, -45f)
                seat(39.6f, 77.8f, 26f, -55f)
                groundLine(86.3f)
                platform(ANKLE_NEAR, 16f, -45f)
            }
        }
        figure("plate_leg_extension") {
            // Seated against the slightly reclined pad, the roller on the lower shins: knees bent, then the legs straight.
            start {
                hip(40f, 60f); trunk(-8f)
                arms(175f, 145f)
                legs(90f, 180f, 100f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, -8f)
                pad(ANKLE_NEAR, 70f, 5.5f, 3f)
            }
            end {
                hip(40f, 60f); trunk(-8f)
                arms(175f, 145f)
                legs(90f, 90f, 30f)
                seat(30f, 65f, 24f)
                bench(35f, 65f, 24f, -8f)
                pad(ANKLE_NEAR, -20f, 5.5f, 3f)
            }
        }
        figure("plate_leg_curl") {
            // Face down on the angled pad, hips held down, the roller above the heels: legs almost straight, then curled.
            start {
                hip(48f, 56f); trunk(110f)
                arms(170f, 150f)
                legs(-80f, -85f, 180f)
                seat(46.3f, 60.7f, 20f, 110f)
                seat(46.3f, 60.7f, 16f, -80f)
                pad(ANKLE_NEAR, 5f, 5.5f, 3f)
                platform(HAND_NEAR, 10f, 60f)
            }
            end {
                hip(48f, 56f); trunk(110f)
                arms(170f, 150f)
                legs(-80f, 30f, -50f)
                seat(46.3f, 60.7f, 20f, 110f)
                seat(46.3f, 60.7f, 16f, -80f)
                pad(ANKLE_NEAR, 120f, 5.5f, 3f)
                platform(HAND_NEAR, 10f, 60f)
            }
        }
        figure("plate_hip_thrust") {
            // Upper back on the backrest, the lever's pad across the hips: hips low, then driven up level with the shoulders and knees.
            start {
                place(ANKLE_NEAR, 66f, 85f); trunk(-62f, -60f)
                arms(75f, 140f)
                legs(57f, 162f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                pad(HIP, 0f, 6f, 3.5f)
            }
            end {
                place(ANKLE_NEAR, 66f, 85f); trunk(-90f, -60f)
                arms(70f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                pad(HIP, 0f, 6f, 3.5f)
            }
        }
        figure("plate_calf_raise_seated") {
            // Seated, the pad on the lower thighs, the balls of the feet on the step: heels down, then lifted as high as they go.
            start {
                hip(36f, 62f); trunk(0f)
                arms(150f, 120f)
                legs(96f, 180f, 60f)
                box(22f, 67f, 18f, 23f)
                box(53f, 81.9f, 14f, 8.1f)
                groundLine(90f)
                pad(KNEE_NEAR, -15f, 6f, 3f)
            }
            end {
                hip(36f, 62f); trunk(0f)
                arms(150f, 120f)
                legs(76.2f, 180f, 120f)
                box(22f, 67f, 18f, 23f)
                box(53f, 81.9f, 14f, 8.1f)
                groundLine(90f)
                pad(KNEE_NEAR, -15f, 6f, 3f)
            }
        }
        figure("plate_calf_raise_standing") {
            // Under the pads of the lever, pivoted on the floor behind, the balls of the feet on the step, knees straight: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 50f, 78f); trunk(0f)
                arms(165f, 25f)
                legs(180f, 180f, 60f)
                box(47f, 83f, 16f, 7f)
                groundLine(90f)
                cable(20f, 86f, SHOULDER)
                pad(SHOULDER, -80f, 7f, 3.5f)
            }
            end {
                place(TOE_NEAR, 50f, 78f); trunk(0f)
                arms(165f, 25f)
                legs(180f, 180f, 140f)
                box(47f, 83f, 16f, 7f)
                groundLine(90f)
                cable(20f, 86f, SHOULDER)
                pad(SHOULDER, -80f, 7f, 3.5f)
            }
        }
        figure("plate_biceps_curl") {
            // Seated, the upper arms on the slanted pad, elbows at the pivot: arms nearly straight, then curled up.
            start {
                hip(30f, 69f); trunk(20f)
                arms(122f, 152f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(36f, 59f, 14f, 122f)
                platform(HAND_NEAR, 10f, 62f)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(122f, 15f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(20f, 74f, 18f)
                seat(36f, 59f, 14f, 122f)
                platform(HAND_NEAR, 10f, 105f)
            }
        }
        figure("plate_triceps_dip") {
            // Seated tall, the handles at the sides: elbows bent about 90° and back, then pushed down until the arms are straight.
            start {
                hip(42f, 69f); trunk(4f)
                arms(232f, 142f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 52f)
            }
            end {
                hip(42f, 69f); trunk(4f)
                arms(160f, 165f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                seat(35f, 74f, 20f)
                bench(37f, 74f, 26f, 0f)
                platform(HAND_NEAR, 10f, 75f)
            }
        }
    }
}
