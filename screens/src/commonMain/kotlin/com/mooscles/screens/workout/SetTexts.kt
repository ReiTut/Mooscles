package com.mooscles.screens.workout

import androidx.compose.runtime.Composable
import com.mooscles.designsystem.components.spokenLoadByReps
import com.mooscles.designsystem.format.MoosclesNumbers
import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.designsystem.format.spokenWeight
import com.mooscles.domain.WeightUnits
import com.mooscles.screens.resources.best_set_bodyweight
import com.mooscles.ui.PreviousSetUi
import org.jetbrains.compose.resources.stringResource
import com.mooscles.screens.resources.Res as ScreensRes

// The texts of a set: loads in the chosen unit, with the language's decimal separator. The value
// stays in exact kg everywhere else; these only show it.

/** Whether a saved load is shown blank: 0 is no load, and the set row's field shows nothing. */
internal fun blankLoad(weightKg: Double): Boolean = weightKg == 0.0

/** Whether saved reps are shown blank: 0 is none yet, and the set row's field shows nothing. */
internal fun blankReps(reps: Int): Boolean = reps == 0

/** The unit symbol, the same in every language. */
internal fun unitSymbol(pounds: Boolean): String = if (pounds) "lb" else "kg"

/**
 * A best set as the workout summary and the exercise detail show it: "87,5 kg × 6" in the unit of
 * [pounds], or "Peso corporal × 12" for a set without a load.
 */
@Composable
internal fun bestSetText(weightKg: Double, reps: Int, pounds: Boolean, locale: NumberLocale = currentNumberLocale()): String =
    if (weightKg == 0.0) {
        stringResource(ScreensRes.string.best_set_bodyweight, reps)
    } else {
        MoosclesNumbers.setText(MoosclesNumbers.plain(WeightUnits.fromKg(weightKg, pounds)), unitSymbol(pounds), reps, locale)
    }

/** [weightKg] as shown in a field, in the unit of [pounds], without the unit: "82,5", "80". */
@Composable
internal fun loadText(weightKg: Double, pounds: Boolean): String =
    MoosclesNumbers.localize(MoosclesNumbers.plain(WeightUnits.fromKg(weightKg, pounds)), currentNumberLocale())

/** [weightKg] as read aloud: "82,5 quilos", "80 pounds". */
@Composable
internal fun loadSpoken(weightKg: Double, pounds: Boolean): String =
    spokenWeight(MoosclesNumbers.plain(WeightUnits.fromKg(weightKg, pounds)), pounds)

/**
 * The previous session's set as the "previous" column shows it: "80 × 6", without the unit, as on
 * the board "Treino ativo" (the column title carries "kg" or "lb", and the reading keeps it: see
 * [previousSpoken]). Without the unit the row fits one line on a 412 dp phone at 1.0 for the usual
 * values ("82,5 × 10"), so the column titles show. A set without a pair passes null to the row,
 * which draws the design system's "—" and leaves "previous" out of its reading.
 */
@Composable
internal fun previousText(previous: PreviousSetUi, pounds: Boolean): String = "${loadText(previous.weightKg, pounds)} × ${previous.reps}"

/** The previous session's set as read aloud: "80 quilos por 6". */
@Composable
internal fun previousSpoken(previous: PreviousSetUi, pounds: Boolean): String =
    spokenLoadByReps(loadSpoken(previous.weightKg, pounds), previous.reps)
