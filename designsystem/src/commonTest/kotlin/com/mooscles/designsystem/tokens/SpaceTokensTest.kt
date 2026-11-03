package com.mooscles.designsystem.tokens

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpaceTokensTest {
    @Test
    fun spaceScaleMatchesTheSpecAndIsMultipleOfFour() {
        val space = MoosclesSpace()
        // Spec "Espaço": space.1 to space.16, in dp.
        assertEquals(listOf(4, 8, 12, 16, 20, 24, 32, 40, 48, 64).map { it.dp }, space.all())
        for (value in space.all()) assertEquals(0f, value.value % 4f, "$value")
    }

    @Test
    fun radiusMatchesTheSpec() {
        val radius = MoosclesRadius()
        assertEquals(listOf(4.dp, 8.dp, 12.dp, 20.dp, 28.dp), listOf(radius.xs, radius.sm, radius.md, radius.lg, radius.xl))
    }

    @Test
    fun targetsAreFortyEightFiftySixAndEight() {
        val targets = MoosclesTargets()
        assertEquals(48.dp, targets.min)
        assertEquals(56.dp, targets.workout)
        assertEquals(8.dp, targets.gap)
    }

    @Test
    fun drawnControlsFitTheirTargets() {
        val sizes = MoosclesSizes()
        val targets = MoosclesTargets()
        assertEquals(44.dp, sizes.checkDisc)
        assertTrue(sizes.checkDisc <= targets.workout)
        assertEquals(40.dp, sizes.buttonS)
        assertTrue(sizes.buttonS <= targets.min)
        assertTrue(sizes.buttonM >= targets.min)
        assertTrue(sizes.buttonL >= targets.min)
        assertTrue(sizes.iconButton48 >= targets.min)
        assertTrue(sizes.iconButton56 >= targets.workout)
        assertTrue(sizes.stepperButtonHeight >= targets.workout)
        assertTrue(sizes.numericFieldHeight >= targets.min)
        assertTrue(sizes.rowMin >= targets.workout)
        // Phase 3: a tab (the bar's full height) is a 48 dp target at least; the bar's ring fits its 56 dp row.
        assertTrue(sizes.barMin >= targets.min)
        assertTrue(sizes.ringBar < targets.workout && sizes.ringBarStroke < sizes.dockRingStroke)
    }
}
