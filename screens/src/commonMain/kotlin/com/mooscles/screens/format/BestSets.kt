package com.mooscles.screens.format

import kotlin.math.abs
import kotlin.math.ulp

/** Why an exercise with completed sets shows no best set. */
enum class NoBestSet { WARMUP_ONLY, ASSISTANCE_ONLY, NO_WORKING_SET }

/**
 * The one "best set" rule of the app, shared by the share card ([com.mooscles.screens.share.ShareCards])
 * and the workout summary ([com.mooscles.screens.summary.SummaryStats]), whatever type their sets have.
 *
 * Only a working set can be the best: not a warm-up, not an assisted set and not from an exercise
 * logged as assistance. Among those, the heaviest wins, and among equal loads (unit round-trip
 * noise included) the most repetitions; on a full tie, the first. Without a working set,
 * [whyNone] says which words to show instead.
 */
internal object BestSets {
    fun eligible(warmup: Boolean, assistance: Boolean, assistanceConvention: Boolean): Boolean =
        !warmup && !assistance && !assistanceConvention

    /** The best of [eligible] (sets already filtered by [eligible]), or null when it is empty. */
    fun <T> best(eligible: List<T>, weightKg: (T) -> Double, reps: (T) -> Int): T? {
        val highest = eligible.maxOfOrNull(weightKg) ?: return null
        return eligible.filter { sameLoad(weightKg(it), highest) }.maxByOrNull(reps)
    }

    /** Why [completed] (not empty, without a working set) has no best set. */
    fun <T> whyNone(
        completed: List<T>,
        warmup: (T) -> Boolean,
        assistance: (T) -> Boolean,
        assistanceConvention: Boolean,
    ): NoBestSet = when {
        completed.isEmpty() -> NoBestSet.NO_WORKING_SET
        completed.all(warmup) -> NoBestSet.WARMUP_ONLY
        assistanceConvention || completed.all(assistance) -> NoBestSet.ASSISTANCE_ONLY
        else -> NoBestSet.NO_WORKING_SET
    }

    /** Round-trip unit noise is equal load; both displayed values still come from one real set. */
    fun sameLoad(first: Double, second: Double): Boolean =
        abs(first - second) <= maxOf(first.ulp, second.ulp) * 4
}
