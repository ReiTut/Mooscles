package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.routines_target_prefix
import com.mooscles.screens.resources.routines_target_reps
import com.mooscles.screens.resources.routines_target_sets
import com.mooscles.screens.resources.routines_target_warmups
import com.mooscles.screens.workout.unitSymbol
import com.mooscles.ui.RoutineTargetUi
import org.jetbrains.compose.resources.stringResource

/** The words of the target line: [prefix] "Target", [sets] "3 sets", [reps] "6–10 reps", [warmups] "+ 2 warm-up" and the load's [unit]. */
internal class TargetTexts(
    val prefix: String,
    val sets: (Int) -> String,
    val reps: (String) -> String,
    val warmups: (Int) -> String,
    val unit: String,
)

/** A piece of the target line; [number] marks the digits (and the signs inside a number), drawn in the number face. */
internal data class TargetPart(val text: String, val number: Boolean)

/**
 * The target line of a routine entry (spec D16): "Target 3×8 · 80 kg", "Target 3×8 · 70–80 kg",
 * "Target 3 sets · 6–10 reps · 70–80 kg", with " + 2 warm-up" after it (kept whole on one line). Only working sets count; a set
 * with 0 reps or no load is blank and stays out of the ranges, and a blank part is left out of the line.
 */
internal object RoutineTargetFormat {
    private const val NO_BREAK_SPACE = "\u00A0"
    private val digits = Regex("[0-9]+(?:[.,×–][0-9]+)*")

    fun parts(target: RoutineTargetUi, pounds: Boolean, locale: NumberLocale, texts: TargetTexts): List<TargetPart>? {
        val working = target.working
        if (working.isEmpty() && target.warmups <= 0) return null
        val body = mutableListOf<String>()
        if (working.isNotEmpty()) {
            val reps = working.map { it.reps }.filter { it > 0 }
            val loads = working.mapNotNull { it.weightKg }.filter { it != 0.0 }
            val sameReps = reps.size == working.size && reps.distinct().size == 1
            body += if (sameReps) "${working.size}×${reps.first()}" else texts.sets(working.size)
            if (!sameReps && reps.isNotEmpty()) body += texts.reps(range(reps.min().toString(), reps.max().toString()))
            if (loads.isNotEmpty()) {
                fun shown(kg: Double) = MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(kg, pounds)), locale)
                body += range(shown(loads.min()), shown(loads.max())) + NO_BREAK_SPACE + texts.unit
            }
        }
        val line = buildList {
            add(texts.prefix)
            if (body.isNotEmpty()) add(body.joinToString(" · "))
            // "+ 2 warm-up" is one piece: a narrow line breaks before the "+", never between the count and its word.
            if (target.warmups > 0) add(texts.warmups(target.warmups).replace(" ", NO_BREAK_SPACE))
        }.joinToString(" ")
        return split(line)
    }

    private fun range(low: String, high: String) = if (low == high) low else "$low–$high"

    private fun split(line: String): List<TargetPart> {
        val out = mutableListOf<TargetPart>()
        var at = 0
        for (match in digits.findAll(line)) {
            if (match.range.first > at) out += TargetPart(line.substring(at, match.range.first), false)
            out += TargetPart(match.value, true)
            at = match.range.last + 1
        }
        if (at < line.length) out += TargetPart(line.substring(at), false)
        return out
    }
}

private const val REPS_MARK = "{reps}"

/**
 * The target line of [target] for the card header, in the unit of [pounds]: the words in `caption`,
 * the numbers in `num.sm`. `null` when the entry has no target.
 */
@Composable
internal fun targetLine(target: RoutineTargetUi?, pounds: Boolean): AnnotatedString? {
    if (target == null) return null
    val locale = currentNumberLocale()
    val prefix = stringResource(Res.string.routines_target_prefix)
    val sets = pluralCount(Res.plurals.routines_target_sets, target.working.size)
    val repsTemplate = stringResource(Res.string.routines_target_reps, REPS_MARK)
    val warmups = pluralCount(Res.plurals.routines_target_warmups, target.warmups)
    val unit = unitSymbol(pounds)
    val words = MTheme.type.caption
    val numbers = MTheme.type.numSm
    return remember(target, pounds, locale, prefix, sets, repsTemplate, warmups, words, numbers) {
        val texts = TargetTexts(prefix, { sets }, { repsTemplate.replace(REPS_MARK, it) }, { warmups }, unit)
        RoutineTargetFormat.parts(target, pounds, locale, texts)?.let { parts ->
            val wordStyle = SpanStyle(fontFamily = words.fontFamily, fontSize = words.fontSize, fontWeight = words.fontWeight, letterSpacing = words.letterSpacing)
            val numberStyle = SpanStyle(fontFamily = numbers.fontFamily, fontSize = numbers.fontSize, fontWeight = numbers.fontWeight, letterSpacing = numbers.letterSpacing, fontFeatureSettings = numbers.fontFeatureSettings)
            // Every line takes the numbers' leading, so a wrapped line of words sits as far below as one with digits.
            buildAnnotatedString {
                withStyle(ParagraphStyle(lineHeight = numbers.lineHeight)) {
                    for (part in parts) withStyle(if (part.number) numberStyle else wordStyle) { append(part.text) }
                }
            }
        }
    }
}
