package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_FAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.SHOULDER
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Dumbbell exercises. Poses drawn by hand, in the catalog's order; see [FigureFrames]. Seen from
 * the side, the two dumbbells of a pair sit on top of each other, so only the near one shows. A
 * movement in the frontal plane (the lateral raises, the lateral lunge, the side bend) is drawn
 * from the front, like the pilot's lateral raise.
 */
internal object DumbbellFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("dumbbell_bench_flat") {
            // Lying on the bench, the dumbbells pressed up, then lowered beside the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(125f, -20f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_bench_one_arm") {
            // One dumbbell, the free hand holding the bench's side: pressed up, then lowered beside the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                nearArm(0f, 0f); farArm(110f, 150f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                nearArm(125f, -20f); farArm(110f, 150f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_bench_incline") {
            // Back on a bench set at about 35°: the dumbbells over the shoulders, then at the sides of the upper chest.
            start {
                hip(48f, 69f); trunk(-55f)
                arms(0f, 0f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(48f, 69f); trunk(-55f)
                arms(150f, -10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_bench_decline") {
            // Head low, the shins hooked under the roller: the dumbbells over the lower chest, then at its sides.
            start {
                hip(54f, 60f); trunk(-110f)
                arms(0f, 0f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(54f, 60f); trunk(-110f)
                arms(105f, -15f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_bench_neutral") {
            // Palms facing in, elbows close: pressed up, then lowered with the elbows travelling toward the hips.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(115f, -10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_floor_press") {
            // Lying on the floor, knees bent: pressed up, then the upper arms down on the floor.
            start {
                hip(48f, 85f); trunk(-90f)
                arms(0f, 0f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(48f, 85f); trunk(-90f)
                arms(90f, -5f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_NEAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_floor_press_one_arm") {
            // One dumbbell over the shoulder, the free arm along the floor: pressed up, then the upper arm down.
            start {
                hip(48f, 85f); trunk(-90f)
                nearArm(0f, 0f); farArm(90f, 90f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_FAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(48f, 85f); trunk(-90f)
                nearArm(90f, -5f); farArm(90f, 90f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_NEAR, ELBOW_FAR, HAND_FAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_fly_flat") {
            // The dumbbells over the chest, elbows softly bent; then the arms opened wide and low, the bend kept.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(-5f, 10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(138f, 122f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_fly_incline") {
            // On a bench at about 30°: the dumbbells over the chest, then lowered in a wide arc beside the bench.
            start {
                hip(48f, 69f); trunk(-55f)
                arms(-5f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(48f, 69f); trunk(-55f)
                arms(168f, 150f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_pullover") {
            // One dumbbell in both hands over the chest, elbows soft; then lowered behind the head to a stretch.
            start {
                hip(60f, 69f); trunk(-90f)
                arms(-5f, 5f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(60f, 69f); trunk(-90f)
                arms(-100f, -90f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(28f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_row_one_arm") {
            // One knee and one hand on the bench, the back flat: the arm hanging, then the dumbbell pulled to the hip.
            start {
                hip(36f, 53.2f); trunk(76f, 82f)
                nearArm(180f, 180f); farArm(150f, 160f)
                nearLeg(172f, 186f, 90f); farLeg(182f, -90f, -120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(14f, 74f, 46f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(36f, 53.2f); trunk(76f, 82f)
                nearArm(292f, 175f); farArm(150f, 160f)
                nearLeg(172f, 186f, 90f); farLeg(182f, -90f, -120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(14f, 74f, 46f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_row_chest_supported") {
            // Chest down on an incline bench, on the toes: arms hanging, then rowed up, the chest kept on the pad.
            start {
                hip(42f, 60f); trunk(52f, 62f)
                arms(176f, 176f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 60f); trunk(52f, 62f)
                arms(262f, 178f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_row_bent_over") {
            // Hinged with a flat back, the dumbbells below the shoulders; then both rowed toward the hips.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(185f, 185f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(288f, 172f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_row_renegade") {
            // A high plank on two dumbbells: both arms straight, then one dumbbell rowed to the hip, hips square.
            start {
                place(HAND_FAR, 70f, 81f); trunk(62f)
                arms(180f, 180f)
                legs(242f, 242f, 155f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(HAND_FAR, 70f, 81f); trunk(62f)
                nearArm(290f, 178f); farArm(180f, 180f)
                legs(242f, 242f, 155f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_shrug") {
            // Standing tall, the dumbbells at the sides, then the shoulders up toward the ears (the shoulder lift, as the
            // barbell shrug): the straight arms and the dumbbells rise with them.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f); shoulderLift(SHRUG_LIFT)
                arms(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_press_seated") {
            // Upright on the bench, the dumbbells at shoulder height, then pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(150f, -5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_press_standing") {
            // Standing, the dumbbells at shoulder height, forearms vertical; then pressed straight up without leaning back.
            start {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f)
                arms(150f, -5f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f, 8f)
                arms(5f, 3f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_press_arnold") {
            // Seated, the dumbbells in front of the shoulders with the elbows forward; then turned out and pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(130f, 0f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_raise_lateral") {
            // Seen from the front (a side view cannot show arms rising sideways):
            // both arms and both deltoids, feet as short stubs toward the viewer, toes on the floor.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(155f, 165f); farArm(205f, 195f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
            end {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(85f, 95f); farArm(-85f, -95f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
        }
        figure("dumbbell_raise_lateral_seated") {
            // From the front, sitting on the end of a bench (the box), knees apart: arms down, then out to shoulder height.
            start {
                front(); place(TOE_NEAR, 64f, 85f); trunk(0f)
                nearArm(150f, 160f); farArm(210f, 200f)
                nearLeg(130f, 178f, 160f); farLeg(230f, 182f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(43f, 66f, 14f, 24f)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
            end {
                front(); place(TOE_NEAR, 64f, 85f); trunk(0f)
                nearArm(85f, 95f); farArm(-85f, -95f)
                nearLeg(130f, 178f, 160f); farLeg(230f, 182f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                box(43f, 66f, 14f, 24f)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
        }
        figure("dumbbell_raise_front") {
            // Standing, the dumbbells in front of the thighs; then raised forward to shoulder height, elbows soft.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(170f, 166f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(88f, 98f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_fly_reverse") {
            // Hinged near parallel, arms hanging with soft elbows; then swung up and back to the height of the back.
            // Seen from the side the arms open toward the viewer, so the frame B shows them swept back (lot report).
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(74f, 80f)
                arms(182f, 172f)
                legs(155f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(74f, 80f)
                arms(258f, 248f)
                legs(155f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_fly_reverse_incline") {
            // Chest down on the incline bench: arms hanging, then swept up and back in line with the body.
            start {
                hip(42f, 60f); trunk(52f, 62f)
                arms(178f, 170f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 60f); trunk(52f, 62f)
                arms(248f, 238f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_upright_row") {
            // Standing, the dumbbells at the thighs; then lifted close to the body, elbows leading to shoulder height.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(285f, 115f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_squat_goblet") {
            // One dumbbell held upright against the chest: standing, then squatting between the knees, chest up.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176f, 35f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(20f)
                arms(192f, 55f)
                legs(92f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_squat_front") {
            // The dumbbells on the front of the shoulders, elbows up: standing, then deep with the torso upright.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(75f, 250f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(18f)
                arms(90f, 268f)
                legs(95f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_squat_bulgarian") {
            // The back foot on a bench; only the front foot is on the floor. Tall, then the front thigh parallel.
            start {
                place(ANKLE_NEAR, 60f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(180f, 180f, 90f); farLeg(215.4f, 259.3f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 60f, 85f); trunk(12f)
                arms(185f, 185f)
                nearLeg(90f, 200f, 90f); farLeg(210.8f, -23.2f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_split_squat") {
            // A long split stance, the dumbbells at the sides: tall, then the back knee dropped toward the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(133.5f, 178.6f, 90f); farLeg(217.4f, 223.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_lunge_reverse") {
            // Standing tall, the dumbbells at the sides; then a step back, the back knee lowered toward the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_lunge_forward") {
            // Standing tall over the back foot; then a step forward into the lunge, both knees at about 90°.
            start {
                place(ANKLE_NEAR, 36f, 85f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 64f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_lunge_walking") {
            // Mid-stride on the back leg with the front leg swinging through; then the lunge, back knee near the floor.
            start {
                place(ANKLE_FAR, 40f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(145f, 185f, 100f); farLeg(180f, 180f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(180f, 180f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_lunge_lateral") {
            // From the front: feet together; then a wide step to the side, sitting over the bent leg, the other straight.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(158f, 168f); farArm(202f, 192f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
            end {
                front(); place(TOE_NEAR, 66f, 85f); trunk(6f)
                nearArm(162f, 172f); farArm(206f, 196f)
                nearLeg(125f, 175f, 160f); farLeg(218.3f, 218.3f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR); dumbbell(HAND_FAR)
            }
        }
        figure("dumbbell_step_up") {
            // One whole foot on a knee-high box; then standing on it, driven up through that foot.
            start {
                hip(44f, 57f); trunk(8f)
                arms(182f, 182f)
                nearLeg(79.2f, 179f, 90f); farLeg(178f, 221.4f, 90f)
                box(52f, 75f, 22f, 15f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 70f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 90f)
                box(52f, 75f, 22f, 15f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_deadlift_romanian") {
            // Standing, the dumbbells at the thighs; then the hips back, the dumbbells sliding down the legs.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(65f, 70f)
                arms(196f, 196f)
                legs(160f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_deadlift_romanian_one_leg") {
            // On the near leg, the dumbbell in the opposite hand: standing, then hinged as the free leg rises behind.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                nearArm(178f, 178f); farArm(178f, 178f)
                nearLeg(180f, 180f, 90f); farLeg(195f, 215f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                dumbbell(HAND_FAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(82f, 86f)
                nearArm(180f, 180f); farArm(180f, 180f)
                nearLeg(172f, 186f, 90f); farLeg(262f, 262f, 170f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                dumbbell(HAND_FAR)
            }
        }
        figure("dumbbell_deadlift_sumo") {
            // A wide stance, one dumbbell hanging between the legs by its end: hips down, chest up; then standing tall.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(40f, 55f)
                arms(176f, 176f)
                legs(105.3f, 208.1f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(176f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_hip_thrust") {
            // Upper back on the bench, the dumbbell held on the hips with both hands (drawn at the hand, so the trunk does not hide it): hips low, then level with the knees.
            start {
                place(ANKLE_NEAR, 66f, 85f); trunk(-62f, -60f)
                arms(75f, 140f)
                legs(57f, 162f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 66f, 85f); trunk(-90f, -60f)
                arms(70f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_calf_raise_standing") {
            // The balls of the feet on a step, the dumbbells at the sides: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 60f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 140f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_calf_raise_one_leg") {
            // On one foot on the step, the dumbbell in that hand, the other hand on a post: heel down, then up.
            start {
                place(TOE_NEAR, 46f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                nearLeg(180f, 180f, 60f); farLeg(178f, 220f, 150f)
                box(43f, 83f, 16f, 7f)
                box(64f, 26f, 4f, 64f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(TOE_NEAR, 46f, 78f); trunk(0f)
                nearArm(180f, 180f); farArm(135f, 100f)
                nearLeg(180f, 180f, 140f); farLeg(178f, 220f, 150f)
                box(43f, 83f, 16f, 7f)
                box(64f, 26f, 4f, 64f)
                groundLine(90f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_calf_raise_seated") {
            // Seated, the balls of the feet on a step, the dumbbells held on the knees: heels down, then lifted.
            start {
                hip(40f, 69f); trunk(15f)
                arms(190.5f, 80f)
                legs(76.2f, 166.6f, 60f)
                bench(20f, 74f, 22f)
                box(56f, 83f, 14f, 7f)
                groundLine(90f)
                dumbbell(KNEE_NEAR)
            }
            end {
                hip(40f, 69f); trunk(15f)
                arms(191.4f, 54.7f)
                legs(55.3f, 155.5f, 130f)
                bench(20f, 74f, 22f)
                box(56f, 83f, 14f, 7f)
                groundLine(90f)
                dumbbell(KNEE_NEAR)
            }
        }
        figure("dumbbell_curl_standing") {
            // Standing, the dumbbells at the sides, then curled with the elbows still.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 40f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_hammer") {
            // As the curl with the palms facing in the whole rep: arms down, then curled, elbows fixed at the sides.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(176f, 174f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(172f, 45f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_incline") {
            // Sitting back on an incline bench, arms hanging behind the body's line; then curled, the elbows kept back.
            start {
                hip(50f, 69f); trunk(-40f)
                arms(180f, 180f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(26f, 74f, 26f)
                seat(46f, 73f, 26f, -40f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(50f, 69f); trunk(-40f)
                arms(180f, 30f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(26f, 74f, 26f)
                seat(46f, 73f, 26f, -40f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_concentration") {
            // Seated and leaning in, the upper arm braced on the inner thigh: the arm straight, then curled to the shoulder.
            start {
                hip(36f, 69f); trunk(42f, 55f)
                nearArm(180f, 174f); farArm(150f, 120f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(20f, 74f, 22f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(36f, 69f); trunk(42f, 55f)
                nearArm(180f, 30f); farArm(150f, 120f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(20f, 74f, 22f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_preacher") {
            // Seated, the upper arm on the slanted pad: the elbow nearly straight, then the forearm close to vertical.
            start {
                hip(30f, 69f); trunk(20f)
                arms(118f, 150f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(118f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_spider") {
            // Chest down on the incline bench, the upper arms vertical and still: arms straight, then curled.
            start {
                hip(42f, 60f); trunk(52f, 62f)
                arms(178f, 178f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 60f); trunk(52f, 62f)
                arms(178f, 30f)
                legs(232f, 232f, 160f)
                floor(85f, TOE_NEAR, TOE_FAR)
                bench(30f, 74f, 26f)
                seat(44f, 67f, 17f, 52f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_reverse") {
            // Palms facing back, wrists straight: arms down, then curled with the elbows at the sides.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_curl_zottman") {
            // Curled with the palms up, turned over at the top and lowered palms down: the arms down, then curled.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 35f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_triceps_overhead_two_hands") {
            // One dumbbell overhead in both hands, elbows forward by the head; then lowered behind the head.
            start {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f)
                arms(-4f, 2f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 88f); trunk(0f)
                arms(-6f, -150f)
                legs(180f, 180f, 90f)
                floor(88f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_triceps_overhead_one_arm") {
            // Seated, one arm straight overhead, the upper arm by the ear; then the dumbbell lowered behind the head.
            start {
                hip(42f, 69f); trunk(0f)
                nearArm(-4f, 2f); farArm(160f, 120f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(30f, 74f, 22f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                nearArm(-6f, -150f); farArm(160f, 120f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(30f, 74f, 22f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_triceps_lying") {
            // Lying on the bench, the dumbbells over the shoulders; then lowered beside the head, upper arms still.
            start {
                hip(51f, 69f); trunk(-90f)
                arms(-10f, -10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(51f, 69f); trunk(-90f)
                arms(-14f, -135f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_triceps_kickback") {
            // Hinged, one hand on the bench, the upper arm back along the body: the forearm hanging, then the arm straight.
            start {
                place(ANKLE_NEAR, 34f, 85f); trunk(68f, 76f)
                nearArm(262f, 180f); farArm(145f, 140f)
                legs(160f, 188f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(52f, 74f, 30f)
                dumbbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 34f, 85f); trunk(68f, 76f)
                nearArm(262f, 262f); farArm(145f, 140f)
                legs(160f, 188f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(52f, 74f, 30f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_wrist_curl") {
            // Seated, the forearm on the thigh, palm up: the wrist bent back, then curled up.
            start {
                hip(36f, 69f); trunk(30f)
                arms(185f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(36f, 69f); trunk(30f)
                arms(185f, 68f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_wrist_curl_reverse") {
            // Seated, the forearm on the thigh, palm down: the wrist bent down, then the back of the hand lifted.
            start {
                hip(36f, 69f); trunk(30f)
                arms(185f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(36f, 69f); trunk(30f)
                arms(185f, 72f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_side_bend") {
            // From the front, one dumbbell at the near side, the free hand on the hip: standing tall, then bent sideways toward it, down the leg.
            start {
                front(); place(TOE_NEAR, 53f, 85f); trunk(0f)
                nearArm(160f, 172f); farArm(215f, 150f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                front(); place(TOE_NEAR, 53f, 85f); trunk(36f, 26f)
                nearArm(178f, 180f); farArm(251f, 186f)
                nearLeg(170f, 176f, 160f); farLeg(190f, 184f, 200f)
                floor(85f, TOE_NEAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
        figure("dumbbell_crunch") {
            // On the back, knees bent, the dumbbell held on the chest; then the ribs curled toward the hips, chin tucked.
            start {
                hip(48f, 85f); trunk(-90f)
                arms(89f, -40f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
            end {
                hip(48f, 85f); trunk(-58f, -40f)
                arms(127f, -8f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                dumbbell(HAND_NEAR)
            }
        }
    }
}
