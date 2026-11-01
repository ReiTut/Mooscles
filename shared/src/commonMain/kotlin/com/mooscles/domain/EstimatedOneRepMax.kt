package com.mooscles.domain

/**
 * Estimated one-rep max by the Epley formula, only for sets of 1 to [MAX_REPS] repetitions
 * (above that the formulas diverge and overestimate). Weights are in kg; rounding is for display only.
 */
object EstimatedOneRepMax {
    const val MAX_REPS = 10

    /** `weightKg * (1 + reps / 30)`; one rep is the load itself. Null outside the rules. */
    fun estimate(weightKg: Double, reps: Int): Double? {
        if (reps !in 1..MAX_REPS) return null
        if (!weightKg.isFinite() || weightKg <= 0.0) return null
        if (reps == 1) return weightKg
        return weightKg * (1 + reps / 30.0)
    }
}
