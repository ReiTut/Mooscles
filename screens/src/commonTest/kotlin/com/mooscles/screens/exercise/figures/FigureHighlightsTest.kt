package com.mooscles.screens.exercise.figures

import com.mooscles.data.ExerciseAnatomy
import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.illustration.FigureSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The coarse muscle highlight of a figure (spec "Destaque do músculo na figura"; plan decisions 9 and Task 8's table). */
class FigureHighlightsTest {
    @Test
    fun everyRegionMapsAsTheTableSays() {
        val expected = mapOf(
            MuscleRegion.CHEST to FigureSegment.TORSO_FRONT,
            MuscleRegion.ABDOMINALS to FigureSegment.TORSO_FRONT,
            MuscleRegion.LATS to FigureSegment.TORSO_BACK,
            MuscleRegion.MIDDLE_BACK to FigureSegment.TORSO_BACK,
            MuscleRegion.LOWER_BACK to FigureSegment.TORSO_BACK,
            MuscleRegion.TRAPS to FigureSegment.TORSO_BACK,
            MuscleRegion.SHOULDERS to FigureSegment.SHOULDER,
            MuscleRegion.BICEPS to FigureSegment.UPPER_ARM_FRONT,
            MuscleRegion.TRICEPS to FigureSegment.UPPER_ARM_BACK,
            MuscleRegion.FOREARMS to FigureSegment.FOREARM,
            MuscleRegion.GLUTES to FigureSegment.HIP,
            MuscleRegion.ABDUCTORS to FigureSegment.HIP,
            MuscleRegion.QUADRICEPS to FigureSegment.THIGH_FRONT,
            MuscleRegion.ADDUCTORS to FigureSegment.THIGH_FRONT,
            MuscleRegion.HAMSTRINGS to FigureSegment.THIGH_BACK,
            MuscleRegion.CALVES to FigureSegment.SHIN_BACK,
            MuscleRegion.NECK to null,
        )
        assertEquals(MuscleRegion.entries.toSet(), expected.keys, "every region is in the table")
        for ((region, segment) in expected) {
            assertEquals(segment, FigureHighlights.segment(region), "$region")
            assertEquals(setOfNotNull(segment), FigureHighlights.forRegions(listOf(region)), "$region alone")
        }
        // Several primaries light several segments; two regions on one segment light it once.
        assertEquals(
            setOf(FigureSegment.TORSO_BACK, FigureSegment.HIP),
            FigureHighlights.forRegions(listOf(MuscleRegion.LATS, MuscleRegion.TRAPS, MuscleRegion.GLUTES, MuscleRegion.NECK)),
        )
        assertEquals(emptySet(), FigureHighlights.forRegions(emptyList()))
    }

    /** Only the primaries light up: the bench press's triceps and shoulders are the map's, not the figure's. */
    @Test
    fun benchPressHighlightsTheFrontOfTheTorso() {
        val anatomy = assertNotNull(ExerciseAnatomy.forExercise("catalog_barbell_bench_flat", "Chest"))
        assertEquals(setOf(FigureSegment.TORSO_FRONT), FigureHighlights.forRegions(anatomy.primary))
        // TRAPS is a secondary on rows (Task 2): the row lights the back once, from its primaries.
        val row = assertNotNull(ExerciseAnatomy.forExercise("catalog_barbell_row_overhand", "Back"))
        assertEquals(setOf(FigureSegment.TORSO_BACK), FigureHighlights.forRegions(row.primary))
    }
}
