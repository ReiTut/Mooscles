package com.mooscles.designsystem.format

import androidx.compose.runtime.Composable
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.ds_unit_kg_spoken
import com.mooscles.designsystem.resources.ds_unit_lb_spoken

/**
 * A weight read aloud with its unit in full: "1 quilo", "2 quilos", "1,5 quilos", "1 pound". [plain] is the value as
 * [MoosclesNumbers.plain] writes it, already in the unit of [pounds]. Only exactly 1 takes the singular: a decimal
 * stays plural in both languages ("1,5 quilos", "1.5 kilograms"), and 0 is plural, as [pluralCount] keeps it.
 */
@Composable
fun spokenWeight(plain: String, pounds: Boolean, locale: NumberLocale = currentNumberLocale()): String =
    pluralCount(
        if (pounds) Res.plurals.ds_unit_lb_spoken else Res.plurals.ds_unit_kg_spoken,
        if (plain == "1") 1 else 2,
        MoosclesNumbers.localize(plain, locale),
    )
