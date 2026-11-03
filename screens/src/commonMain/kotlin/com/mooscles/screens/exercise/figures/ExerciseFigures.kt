package com.mooscles.screens.exercise.figures

import com.mooscles.data.ExerciseCatalog
import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.illustration.FigureSegment

/**
 * The exercise figures (spec D3, D4): the seven family tables joined, keyed by the catalog's
 * `stableId`. The pilot has 20; the lots after the G1 gate drew the rest, family by family, until
 * every catalog exercise but [NO_FIGURE] had one. The catalog's "other" equipment holds only the
 * trap-bar deadlift, which comes with the barbell lot (spec D4), so there is no family of its own.
 */
internal object ExerciseFigures {
    /** The 20 pilot exercises of spec D4, in its order. */
    val PILOT: List<String> = listOf(
        "barbell_bench_flat", "barbell_back_squat_high_bar", "barbell_deadlift_conventional",
        "barbell_overhead_press", "barbell_row_overhand", "barbell_deadlift_romanian", "barbell_hip_thrust",
        "dumbbell_bench_flat", "dumbbell_press_seated", "dumbbell_raise_lateral", "dumbbell_curl_standing",
        "dumbbell_squat_bulgarian", "cable_lat_pulldown_wide", "cable_row_close_neutral",
        "cable_triceps_pushdown_rope", "bodyweight_pull_up", "bodyweight_push_up", "plate_leg_press_45",
        "selectorized_leg_extension", "selectorized_leg_curl_lying",
    )

    /**
     * Catalog exercises left without a figure on purpose, which keep the equipment icon. The family
     * checks of the lots and the check of the whole catalog skip them.
     * - `cable_pallof_press`: the side view cannot show an anti-rotation pull; the equipment icon is clearer.
     * - `selectorized_torso_rotation`: the torso turns about its own axis, which neither the side nor
     *   the front view shows; both frames would be the same seated figure.
     */
    val NO_FIGURE: Set<String> = setOf("cable_pallof_press", "selectorized_torso_rotation")

    /** Each family's table, for the size checks: a family stays well below the JVM's method size limit. */
    val families: Map<String, Map<String, FigureFrames>> by lazy {
        mapOf(
            "barbell" to BarbellFigures.frames,
            "dumbbell" to DumbbellFigures.frames,
            "cable" to CableFigures.frames,
            "bodyweight" to BodyweightFigures.frames,
            "machine" to MachineFigures.frames,
            "kettlebell" to KettlebellFigures.frames,
            "smith" to SmithFigures.frames,
        )
    }

    private val all: Map<String, FigureFrames> by lazy {
        val joined = LinkedHashMap<String, FigureFrames>()
        for ((family, table) in families) {
            for ((id, frames) in table) {
                check(joined.put(id, frames) == null) { "$id has a figure in two families (the second is $family)" }
            }
        }
        joined
    }

    val stableIds: Set<String> get() = all.keys

    /** The figure of the exercise whose id is [exerciseId] (`catalog_…`); null for a custom exercise or one still without a figure. */
    fun forExercise(exerciseId: String): FigureFrames? =
        if (exerciseId.startsWith(ExerciseCatalog.ID_PREFIX)) all[exerciseId.removePrefix(ExerciseCatalog.ID_PREFIX)] else null
}

/**
 * Which parts of the figure light up for an exercise (plan decision 9): its primary regions only,
 * coarse, by segment; the secondaries are the body map's. The neck has no segment of its own.
 */
internal object FigureHighlights {
    private val table: Map<MuscleRegion, FigureSegment?> = mapOf(
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

    /** The segment of [region], or null when it has none (the neck). */
    fun segment(region: MuscleRegion): FigureSegment? = table.getValue(region)

    fun forRegions(primary: List<MuscleRegion>): Set<FigureSegment> = primary.mapNotNullTo(LinkedHashSet()) { segment(it) }
}
