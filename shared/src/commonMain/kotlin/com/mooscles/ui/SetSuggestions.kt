package com.mooscles.ui

import com.mooscles.data.LoadConvention

enum class SetField { LOAD, REPS }

/** A suggested value per field; null means no suggestion for that field. */
data class SetSuggestion(val weightKg: Double? = null, val reps: Int? = null) {
    companion object {
        val None = SetSuggestion()
    }
}

data class EffectiveSet(val weightKg: Double, val reps: Int)

/** Pure rules for the values shown in empty fields of pending sets (spec D10). */
object SetSuggestions {
    private val commonLoad = setOf(LoadConvention.TOTAL_LOAD.label, LoadConvention.PER_HAND.label, LoadConvention.ONE_SIDE.label)

    /** 0 kg is empty only for the regular load conventions; Added weight and Assistance treat 0 as a real value. */
    fun loadIsEmpty(weightKg: Double, convention: String): Boolean = weightKg == 0.0 && convention in commonLoad

    /** Pairs by kind (warm-up vs working, assisted counts as working), then by order. [previous] holds only the completed
     * sets of the earlier session, in order. Unpaired sets get null. */
    fun pairPrevious(sets: List<SetUi>, previous: List<PreviousSetUi>): List<PreviousSetUi?> {
        val warm = previous.filter { it.warmup }
        val work = previous.filter { !it.warmup }
        var w = 0
        var k = 0
        return sets.map { if (it.warmup) warm.getOrNull(w++) else work.getOrNull(k++) }
    }

    fun forExercise(
        sets: List<SetUi>,
        previous: List<PreviousSetUi>,
        convention: String,
        cleared: Set<Pair<String, SetField>>,
    ): List<SetSuggestion> {
        val paired = pairPrevious(sets, previous)
        var lastWarm: SetUi? = null
        var lastWork: SetUi? = null
        return sets.mapIndexed { i, set ->
            val above = if (set.warmup) lastWarm else lastWork
            if (set.completed) {
                if (set.warmup) lastWarm = set else lastWork = set
                return@mapIndexed SetSuggestion.None
            }
            val prev = paired[i]
            val weight = if (loadIsEmpty(set.weightKg, convention) && (set.id to SetField.LOAD) !in cleared) {
                above?.weightKg?.takeUnless { loadIsEmpty(it, convention) } ?: prev?.weightKg?.takeUnless { loadIsEmpty(it, convention) }
            } else null
            val reps = if (set.reps == 0 && (set.id to SetField.REPS) !in cleared) {
                above?.reps?.takeIf { it > 0 } ?: prev?.reps?.takeIf { it > 0 }
            } else null
            SetSuggestion(weight, reps)
        }
    }

    fun effective(set: SetUi, suggestion: SetSuggestion): EffectiveSet = EffectiveSet(
        weightKg = suggestion.weightKg ?: set.weightKg,
        reps = suggestion.reps ?: set.reps,
    )
}
