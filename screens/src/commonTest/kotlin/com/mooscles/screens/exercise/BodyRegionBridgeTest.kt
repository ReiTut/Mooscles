package com.mooscles.screens.exercise

import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.illustration.BodyRegion
import kotlin.test.Test
import kotlin.test.assertEquals

/** The anatomy table's regions reach the design system's body map by name (plan decision 8). */
class BodyRegionBridgeTest {
    @Test
    fun everyMuscleRegionHasTheBodyRegionOfTheSameName() {
        for (region in MuscleRegion.entries) {
            assertEquals(region.name, region.toBodyRegion().name)
        }
        // And nothing on the map that no anatomy entry can light.
        assertEquals(BodyRegion.entries.map { it.name }, MuscleRegion.entries.map { it.toBodyRegion().name })
    }
}
