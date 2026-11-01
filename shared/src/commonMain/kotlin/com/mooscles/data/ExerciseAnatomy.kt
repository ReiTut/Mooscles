package com.mooscles.data

/** The regions an exercise works: the main movers first, then the regions that assist or stabilize. */
data class Anatomy(val primary: List<MuscleRegion>, val secondary: List<MuscleRegion>)

/**
 * Muscle regions for every catalog exercise, written for Mooscles from general exercise knowledge
 * (not copied from a dataset), plus the fallback that custom exercises get from their muscle group.
 */
object ExerciseAnatomy {
    /** Keyed by catalog stable id, without the `catalog_` prefix. */
    val catalog: Map<String, Anatomy> = anatomyTable

    /** The regions an exercise of each muscle group may list first as its primary. */
    val allowedPrimary: Map<MuscleGroup, Set<MuscleRegion>> = mapOf(
        MuscleGroup.CHEST to setOf(MuscleRegion.CHEST),
        MuscleGroup.BACK to setOf(MuscleRegion.LATS, MuscleRegion.MIDDLE_BACK, MuscleRegion.LOWER_BACK, MuscleRegion.TRAPS),
        MuscleGroup.SHOULDERS to setOf(MuscleRegion.SHOULDERS),
        MuscleGroup.BICEPS to setOf(MuscleRegion.BICEPS),
        MuscleGroup.TRICEPS to setOf(MuscleRegion.TRICEPS),
        MuscleGroup.QUADRICEPS to setOf(MuscleRegion.QUADRICEPS, MuscleRegion.ADDUCTORS),
        MuscleGroup.HAMSTRINGS to setOf(MuscleRegion.HAMSTRINGS),
        MuscleGroup.GLUTES to setOf(MuscleRegion.GLUTES, MuscleRegion.ABDUCTORS),
        MuscleGroup.CALVES to setOf(MuscleRegion.CALVES),
        MuscleGroup.CORE to setOf(MuscleRegion.ABDOMINALS, MuscleRegion.LOWER_BACK),
        MuscleGroup.FOREARMS to setOf(MuscleRegion.FOREARMS),
        MuscleGroup.FULL_BODY to MuscleRegion.entries.toSet()
    )

    private val groupRegion: Map<MuscleGroup, MuscleRegion> = mapOf(
        MuscleGroup.CHEST to MuscleRegion.CHEST,
        MuscleGroup.BACK to MuscleRegion.LATS,
        MuscleGroup.SHOULDERS to MuscleRegion.SHOULDERS,
        MuscleGroup.BICEPS to MuscleRegion.BICEPS,
        MuscleGroup.TRICEPS to MuscleRegion.TRICEPS,
        MuscleGroup.QUADRICEPS to MuscleRegion.QUADRICEPS,
        MuscleGroup.HAMSTRINGS to MuscleRegion.HAMSTRINGS,
        MuscleGroup.GLUTES to MuscleRegion.GLUTES,
        MuscleGroup.CALVES to MuscleRegion.CALVES,
        MuscleGroup.CORE to MuscleRegion.ABDOMINALS,
        MuscleGroup.FOREARMS to MuscleRegion.FOREARMS
    )

    /** A custom exercise highlights its group's region as the only primary; full body highlights none. */
    fun forMuscleGroup(group: MuscleGroup): Anatomy? =
        groupRegion[group]?.let { Anatomy(listOf(it), emptyList()) }

    /**
     * The catalog entry when [id] is a catalog id the table knows; otherwise the region of the
     * muscle group whose label (or enum name) is [muscleLabel], or null for full body and unknown labels.
     */
    fun forExercise(id: String, muscleLabel: String): Anatomy? {
        if (id.startsWith(ExerciseCatalog.ID_PREFIX)) {
            catalog[id.removePrefix(ExerciseCatalog.ID_PREFIX)]?.let { return it }
        }
        val group = MuscleGroup.fromLabelOrName(muscleLabel) ?: return null
        return forMuscleGroup(group)
    }
}
