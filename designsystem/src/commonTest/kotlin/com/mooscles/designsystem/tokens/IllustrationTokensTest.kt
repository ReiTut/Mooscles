package com.mooscles.designsystem.tokens

import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.illustration.FigureSkeleton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IllustrationTokensTest {
    /**
     * Phase 4A (spec D3 and "Onde as figuras vivem"): the figure renderer's values, final after the
     * 20 pilot poses (Task 8) and their sheet (Task 9). The limb and head strokes and the margin
     * kept their starting values; the skeleton and the equipment changed; the trunk stroke and the
     * gap were added.
     */
    @Test
    fun valuesArePinned() {
        val i = MoosclesIllustration()
        // Fractions of the figure's 100-unit box.
        assertEquals(0.06f, i.figureStroke)
        assertEquals(0.04f, i.figureHeadStroke)
        assertEquals(0.05f, i.figureMargin)
        // From the pilot sheet's critique (Task 9): a trunk half as heavy again as a limb, and the
        // band cleared on each side of the head and of an arm in front (about 2 px at 64 dp).
        assertEquals(0.09f, i.figureTrunkStroke)
        assertEquals(0.012f, i.figureGap)
        // The body map's outline: the 1 dp hairline the spike used (sizes.border).
        assertEquals(1.dp, i.bodyMapStroke)
        // The far limbs' tone (Task 8 fix round 1): iron 57 in both themes.
        assertEquals(Primitives.ferro57, MoosclesFigureColors.Light.farLimb)
        assertEquals(Primitives.ferro57, MoosclesFigureColors.Dark.farLimb)
        // The equipment is copper (G1, 2026-10-30): 47 in the light theme, 64 in the dark one.
        assertEquals(Primitives.cobre47, MoosclesFigureColors.Light.equipment)
        assertEquals(Primitives.cobre64, MoosclesFigureColors.Dark.equipment)
        // A pose that keeps its joints `figureMargin` inside the box keeps its whole stroke inside too.
        assertTrue(i.figureMargin >= i.figureStroke / 2, "the margin holds half a limb stroke")
        assertTrue(i.figureMargin >= i.figureTrunkStroke / 2, "and half the trunk's")
        // The bones of the 100-unit box, final after the pilot poses (Task 8). The spec's example
        // (trunk 30, thigh 21…) did not leave room for a standing overhead lockout with its plate, so
        // the body is about two thirds of the box tall, in human proportions (trunk 19, arm 12 + 13,
        // thigh and shin 16, foot 5.5, neck 3 and head radius 4.5).
        assertEquals(
            listOf(19f, 3f, 4.5f, 12f, 13f, 16f, 16f, 5.5f),
            listOf(
                FigureSkeleton.TRUNK, FigureSkeleton.NECK, FigureSkeleton.HEAD_RADIUS, FigureSkeleton.UPPER_ARM,
                FigureSkeleton.FOREARM, FigureSkeleton.THIGH, FigureSkeleton.SHIN, FigureSkeleton.FOOT,
            ),
        )
        // The shoulder lift's maximum (barbell lot, A1, 2026-10-31): the neck's length, about a sixth
        // of the trunk, so a shrug raises the arms without changing the proportions above.
        assertEquals(3f, FigureSkeleton.SHOULDER_LIFT_MAX)
        assertTrue(FigureSkeleton.SHOULDER_LIFT_MAX <= FigureSkeleton.NECK, "a shrugged shoulder stays below the head")
        // The equipment: plate, dumbbell, kettlebell, pulley, bench legs (a bench about knee high) and floor overhang.
        assertEquals(
            listOf(8f, 4f, 2.5f, 5f, 2.5f, 16f, 6f),
            listOf(
                FigureSkeleton.PLATE_RADIUS, FigureSkeleton.DUMBBELL_RADIUS, FigureSkeleton.KETTLEBELL_HANDLE,
                FigureSkeleton.KETTLEBELL_RADIUS, FigureSkeleton.PULLEY_RADIUS, FigureSkeleton.BENCH_LEG,
                FigureSkeleton.FLOOR_OVERHANG,
            ),
        )
    }

    /** At the 64 dp of a list thumbnail the figure's limbs must read heavier than the body map's hairline. */
    @Test
    fun strokeIsThickerThanTheBodyMapsAtSixtyFourDp() {
        val i = MoosclesIllustration()
        val thumbnail = MoosclesSizes().media
        assertEquals(64.dp, thumbnail)
        assertTrue(thumbnail * i.figureStroke > i.bodyMapStroke, "limb ${thumbnail * i.figureStroke} vs map ${i.bodyMapStroke}")
        assertTrue(thumbnail * i.figureHeadStroke > i.bodyMapStroke, "head ${thumbnail * i.figureHeadStroke} vs map ${i.bodyMapStroke}")
    }
}
