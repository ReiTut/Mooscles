package com.mooscles.screens.exercise.figures

import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.ANKLE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.ELBOW_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HAND_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.HIP
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.KNEE_NEAR
import com.mooscles.designsystem.illustration.FigureJoint.SHOULDER
import com.mooscles.designsystem.illustration.FigureJoint.TOE_FAR
import com.mooscles.designsystem.illustration.FigureJoint.TOE_NEAR

/**
 * Barbell exercises, the EZ bar included, and the trap-bar deadlift (spec D4: it comes with the
 * barbell lot). Poses drawn by hand, in the catalog's order; see [FigureFrames]. A landmine's bar is
 * drawn with `cable`: a line from its pivot on the floor (the ring) to the hands.
 */
internal object BarbellFigures {
    val frames: Map<String, FigureFrames> = figureTable {
        figure("barbell_bench_flat") {
            // Lying on the bench, head to the left; the bar over the shoulders, then on the lower chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(125f, -20f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_bench_incline") {
            // Back on a bench set at about 35°: the bar locked out over the shoulders, then at the upper chest.
            start {
                hip(48f, 69f); trunk(-55f)
                arms(0f, 0f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                barbell(HAND_NEAR)
            }
            end {
                hip(48f, 69f); trunk(-55f)
                arms(150f, -10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 26f)
                seat(45f, 72f, 28f, -55f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_bench_decline") {
            // Head low on a decline bench, the shins hooked under the roller: lockout, then the bar at the lower chest.
            start {
                hip(54f, 60f); trunk(-110f)
                arms(0f, 0f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                barbell(HAND_NEAR)
            }
            end {
                hip(54f, 60f); trunk(-110f)
                arms(105f, -15f)
                legs(60f, 150f, 90f)
                seat(55.7f, 64.7f, 30f, -110f)
                pad(ANKLE_NEAR, 60f, 5.5f, 3f)
                groundLine(88f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_floor_press") {
            // Lying on the floor, knees bent: the bar pressed up, then the upper arms down on the floor.
            start {
                hip(48f, 85f); trunk(-90f)
                arms(0f, 0f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                hip(48f, 85f); trunk(-90f)
                arms(90f, -5f)
                legs(40f, 140f, 90f)
                floor(85f, HIP, SHOULDER, ELBOW_NEAR, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_bench_close_grip") {
            // As the bench press, with the elbows tucked: they travel further toward the hips.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(115f, -10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_bench_spoto") {
            // As the bench press, but the bar stops in the air just above the chest.
            start {
                hip(46f, 69f); trunk(-90f)
                arms(0f, 0f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 69f); trunk(-90f)
                arms(70f, -25f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_row_overhand") {
            // Hinged with a flat back, arms hanging; then the bar pulled to the upper stomach.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(195f, 195f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(62f, 70f)
                arms(285f, 164f)
                legs(150f, 185f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_row_underhand") {
            // A higher hinge than the overhand row, palms up: arms hanging, then the bar to the belly button.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(50f, 60f)
                arms(185f, 185f)
                legs(155f, 192f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(50f, 60f)
                arms(245f, 145f)
                legs(155f, 192f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_row_pendlay") {
            // The back near parallel, the bar resting on the floor; then pulled fast to the lower chest.
            start {
                place(ANKLE_NEAR, 52f, 85f); trunk(72f, 75f)
                arms(180f, 180f)
                legs(135.2f, 169.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 52f, 85f); trunk(72f, 75f)
                arms(290f, 170f)
                legs(135.2f, 169.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_row_seal") {
            // Face down on a high bench, arms hanging past its end; then the bar pulled up to the bench.
            start {
                hip(46f, 50f); trunk(90f)
                arms(180f, 180f)
                legs(-90f, -90f, 180f)
                box(24f, 55f, 36f, 33f)
                groundLine(88f)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 50f); trunk(90f)
                arms(300f, 165f)
                legs(-90f, -90f, 180f)
                box(24f, 55f, 36f, 33f)
                groundLine(88f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_landmine_row_two_arm") {
            // Straddling the bar, its end fixed on the floor behind: arms hanging, then the handle to the chest.
            start {
                place(ANKLE_NEAR, 56f, 85f); trunk(52f, 62f)
                arms(180f, 180f)
                legs(155f, 195f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 56f, 85f); trunk(52f, 62f)
                arms(260f, 150f)
                legs(155f, 195f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_landmine_row_one_arm") {
            // Staggered, the free hand on the front knee: the arm hanging, then the bar's end pulled to the hip.
            start {
                hip(48f, 58f); trunk(58f, 66f)
                nearArm(180f, 180f); farArm(155.6f, 215.1f)
                nearLeg(181.4f, 226.5f, 90f); farLeg(138.1f, 160.6f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
            end {
                hip(48f, 58f); trunk(58f, 66f)
                nearArm(245f, 150f); farArm(155.6f, 215.1f)
                nearLeg(181.4f, 226.5f, 90f); farLeg(138.1f, 160.6f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_meadows_row") {
            // Side-on to the bar's end, the free forearm on the front knee: a full stretch, then the elbow high past the back.
            start {
                hip(46f, 59f); trunk(68f, 74f)
                nearArm(185f, 185f); farArm(147.5f, 242.6f)
                nearLeg(178.3f, 231.3f, 90f); farLeg(131f, 165.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
            end {
                hip(46f, 59f); trunk(68f, 74f)
                nearArm(300f, 170f); farArm(147.5f, 242.6f)
                nearLeg(178.3f, 231.3f, 90f); farLeg(131f, 165.8f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(18f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_overhead_press") {
            // The bar on the front of the shoulders, then locked out overhead.
            start {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f)
                arms(150f, -10f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f, 10f)
                arms(5f, 5f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_seated_press") {
            // Upright against the back pad: the bar at the upper chest, then pressed overhead.
            start {
                hip(42f, 69f); trunk(0f)
                arms(150f, -5f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                barbell(HAND_NEAR)
            }
            end {
                hip(42f, 69f); trunk(0f)
                arms(5f, 3f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(37f, 74f, 22f)
                bench(37f, 74f, 26f, 0f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_push_press") {
            // The bar on the front shoulders in a short knee dip; then the legs drive it to lockout.
            start {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f)
                arms(150f, -10f)
                legs(162f, 198f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f, 10f)
                arms(5f, 5f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_landmine_press") {
            // Facing the bar's fixed end, staggered: the end at the shoulder, then pressed up and forward.
            start {
                hip(38f, 57f); trunk(10f)
                nearArm(155f, -5f); farArm(175f, 175f)
                nearLeg(138.6f, 182f, 90f); farLeg(221f, 185.4f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
            end {
                hip(38f, 57f); trunk(14f)
                nearArm(45f, 45f); farArm(175f, 175f)
                nearLeg(138.6f, 182f, 90f); farLeg(221f, 185.4f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_upright_row") {
            // Standing, the bar at the thighs; then lifted close to the body, elbows leading to shoulder height.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(285f, 115f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_shrug") {
            // Standing tall, arms straight, then the shoulders up toward the ears (the shoulder lift, finding A1
            // of the lot report): the straight arms and the bar rise with them.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f); shoulderLift(SHRUG_LIFT)
                arms(176f, 176f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_back_squat_high_bar") {
            // Standing with the bar on the upper back, then at the bottom with the thighs about parallel.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(245f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(35f, 20f)
                arms(280f, 85f)
                legs(85f, 205f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_back_squat_low_bar") {
            // The bar lower on the back: standing, then at depth with more forward lean than the high bar.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(8f)
                arms(255f, 70f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(48f, 35f)
                arms(295f, 110f)
                legs(80f, 198f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_front_squat") {
            // The bar on the front shoulders, elbows high: standing, then deep with the torso upright.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(75f, 250f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(18f)
                arms(90f, 268f)
                legs(95f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_zercher_squat") {
            // The bar in the crook of the elbows, hands together: standing, then squatting between the knees.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(160f, 45f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(ELBOW_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(20f)
                arms(180f, 65f)
                legs(90f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(ELBOW_NEAR)
            }
        }
        figure("barbell_overhead_squat") {
            // The bar locked out overhead: standing, then squatting with the bar kept over the mid-foot.
            start {
                place(ANKLE_NEAR, 46f, 90f); trunk(0f)
                arms(-4f, -4f)
                legs(180f, 180f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 90f); trunk(25f, 15f)
                arms(-10f, -10f)
                legs(85f, 210f, 90f)
                floor(90f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_landmine_squat") {
            // Holding the bar's end at the chest, its other end fixed on the floor ahead: standing, then squatting.
            start {
                place(ANKLE_NEAR, 38f, 85f); trunk(6f)
                arms(165f, 45f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 38f, 85f); trunk(22f)
                arms(175f, 60f)
                legs(90f, 210f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                cable(84f, 86f, HAND_NEAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_split_squat") {
            // A long split stance, the bar on the back: tall, then the back knee dropped toward the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(133.5f, 178.6f, 90f); farLeg(217.4f, 223.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_squat_bulgarian") {
            // The back foot on a bench, the bar on the back: tall, then the front thigh about parallel.
            start {
                place(ANKLE_NEAR, 60f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(180f, 180f, 90f); farLeg(215.4f, 259.3f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 60f, 85f); trunk(12f)
                arms(257f, 62f)
                nearLeg(90f, 200f, 90f); farLeg(210.8f, -23.2f, -90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR)
                bench(18f, 74f, 20f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_reverse_lunge") {
            // Standing tall with the bar on the back; then a step back, the back knee lowered toward the floor.
            start {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(245f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_walking_lunge") {
            // Mid-stride on the back leg with the front leg swinging through; then the lunge, back knee near the floor.
            start {
                place(ANKLE_FAR, 40f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(145f, 185f, 100f); farLeg(180f, 180f, 90f)
                floor(85f, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 58f, 85f); trunk(0f)
                arms(245f, 50f)
                nearLeg(90.4f, 187.2f, 90f); farLeg(190.8f, 278.9f, 120f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_conventional") {
            // The bar on the floor over the mid-foot, hips above the knees; then standing tall.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(56f, 65f)
                arms(180f, 180f)
                legs(114.6f, 192f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(168f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_sumo") {
            // A wide stance, hips close to the bar, chest up and shins near vertical; then standing tall.
            start {
                place(ANKLE_NEAR, 40f, 85f); trunk(40f, 55f)
                arms(176f, 176f)
                legs(105.3f, 208.1f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 40f, 85f); trunk(0f)
                arms(168f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_romanian") {
            // Standing with the bar at the thighs, then hips back and the bar below the knees.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(65f, 70f)
                arms(199f, 199f)
                legs(160f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_deadlift_stiff_leg") {
            // Standing with the bar at the thighs; then a deep hinge with the knees almost straight.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(80f, 85f)
                arms(185f, 185f)
                legs(170f, 182f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_good_morning") {
            // The bar on the upper back: standing, then hips back and the chest lowered with a flat back.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(245f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(78f, 85f)
                arms(323f, 128f)
                legs(168f, 186f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_hip_thrust") {
            // Upper back on the bench, the bar across the hips: hips low, then level with the knees.
            start {
                place(ANKLE_NEAR, 66f, 85f); trunk(-62f, -60f)
                arms(75f, 140f)
                legs(57f, 162f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                barbell(HIP)
            }
            end {
                place(ANKLE_NEAR, 66f, 85f); trunk(-90f, -60f)
                arms(70f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(14f, 74f, 18f)
                barbell(HIP)
            }
        }
        figure("barbell_glute_bridge") {
            // Shoulders and feet on the floor, the bar across the hips: hips down, then lifted.
            start {
                place(SHOULDER, 28f, 85f); trunk(-105.3f, -90f)
                arms(37.3f, 101.2f)
                legs(47.2f, 172.9f, 90f)
                floor(85f, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HIP)
            }
            end {
                place(SHOULDER, 28f, 85f); trunk(-140f, -90f)
                arms(7.2f, 64.2f)
                legs(86.5f, 166.2f, 90f)
                floor(85f, SHOULDER, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HIP)
            }
        }
        figure("barbell_calf_raise_standing") {
            // The bar on the back, the balls of the feet on a step: heels down, then high on the toes.
            start {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(245f, 50f)
                legs(180f, 180f, 60f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                barbell(HAND_NEAR)
            }
            end {
                place(TOE_NEAR, 52f, 78f); trunk(0f)
                arms(245f, 50f)
                legs(180f, 180f, 140f)
                box(49f, 83f, 16f, 7f)
                groundLine(90f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_curl") {
            // Standing, the bar at the thighs, palms up; then curled with the elbows at the sides.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 40f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_curl_reverse") {
            // As the curl with an overhand grip: the bar at the thighs, then curled, wrists straight.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 50f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_curl_preacher") {
            // Seated, the upper arms on the slanted pad: arms almost straight, then the forearms nearly vertical.
            start {
                hip(30f, 69f); trunk(20f)
                arms(118f, 150f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                barbell(HAND_NEAR)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(118f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36.5f, 58.5f, 14f, 118f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_curl_drag") {
            // The bar against the thighs; then dragged up the body to the lower chest, elbows going back.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(178f, 178f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(205f, 60f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("ez_bar_curl") {
            // Standing, the EZ bar at the thighs on its angled grips; then curled without leaning back.
            start {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(175f, 172f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 46f, 85f); trunk(0f)
                arms(170f, 35f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("ez_bar_curl_preacher") {
            // As the barbell preacher curl, with the EZ bar's inner grips.
            start {
                hip(30f, 69f); trunk(20f)
                arms(125f, 155f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36f, 59f, 14f, 125f)
                barbell(HAND_NEAR)
            }
            end {
                hip(30f, 69f); trunk(20f)
                arms(125f, 10f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(18f, 74f, 14f)
                seat(36f, 59f, 14f, 125f)
                barbell(HAND_NEAR)
            }
        }
        figure("ez_bar_triceps_lying") {
            // Lying on the bench, the bar over the shoulders; then lowered to the forehead, upper arms still.
            start {
                hip(51f, 69f); trunk(-90f)
                arms(-10f, -10f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(51f, 69f); trunk(-90f)
                arms(-14f, -135f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_triceps_lying") {
            // As the EZ-bar version with a straight bar and a narrow grip.
            start {
                hip(51f, 69f); trunk(-90f)
                arms(-8f, -8f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                barbell(HAND_NEAR)
            }
            end {
                hip(51f, 69f); trunk(-90f)
                arms(-12f, -130f)
                legs(95f, 204f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(19f, 74f, 38f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_wrist_curl") {
            // Seated, forearms on the thighs, palms up: the wrists bent back, then curled up.
            start {
                hip(36f, 69f); trunk(30f)
                arms(185f, 112f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                barbell(HAND_NEAR)
            }
            end {
                hip(36f, 69f); trunk(30f)
                arms(185f, 68f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_wrist_curl_reverse") {
            // Seated, forearms on the thighs, palms down: the wrists bent down, then the backs of the hands up.
            start {
                hip(36f, 69f); trunk(30f)
                arms(185f, 110f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                barbell(HAND_NEAR)
            }
            end {
                hip(36f, 69f); trunk(30f)
                arms(185f, 72f)
                legs(90f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                bench(22f, 74f, 16f)
                barbell(HAND_NEAR)
            }
        }
        figure("barbell_rollout") {
            // Kneeling, hands on the bar under the shoulders; then rolled forward, the body long and braced.
            start {
                place(KNEE_NEAR, 34f, 85f); trunk(40f, 50f)
                arms(160f, 160f)
                legs(180f, -90f, -90f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(KNEE_NEAR, 34f, 85f); trunk(78f, 85f)
                arms(118.6f, 118.6f)
                legs(200f, -90f, -90f)
                floor(85f, KNEE_NEAR, ANKLE_NEAR, TOE_NEAR, KNEE_FAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
        figure("trap_bar_deadlift") {
            // Inside the trap bar, handles beside the mid-foot: hips back and down, chest up; then standing tall.
            start {
                place(ANKLE_NEAR, 44f, 85f); trunk(48f, 58f)
                arms(180f, 180f)
                legs(106.8f, 191.6f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
            end {
                place(ANKLE_NEAR, 44f, 85f); trunk(0f)
                arms(180f, 180f)
                legs(180f, 180f, 90f)
                floor(85f, ANKLE_NEAR, TOE_NEAR, ANKLE_FAR, TOE_FAR)
                barbell(HAND_NEAR)
            }
        }
    }
}
