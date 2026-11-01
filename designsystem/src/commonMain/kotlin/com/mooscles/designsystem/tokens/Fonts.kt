package com.mooscles.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.barlow_condensed_bold
import com.mooscles.designsystem.resources.barlow_condensed_medium
import com.mooscles.designsystem.resources.barlow_condensed_semibold
import com.mooscles.designsystem.resources.inter_variable
import org.jetbrains.compose.resources.Font

/** The Inter weights the type scale uses, each set on the variable font's `wght` axis. */
private val InterWeights = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

/**
 * Inter (variable, `opsz` and `wght` axes) at 400, 500, 600 and 700. One file serves every
 * weight: each entry sets `wght` explicitly, and `opsz` stays at the file default (14).
 */
@Composable
fun interFamily(): FontFamily = FontFamily(
    InterWeights.map { weight ->
        Font(
            Res.font.inter_variable,
            weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)

/**
 * Barlow Condensed 500, 600 and 700 (static files), for numbers. Numeric styles add
 * `fontFeatureSettings = "tnum"`: without it the digits are proportional.
 */
@Composable
fun numberFamily(): FontFamily = FontFamily(
    Font(Res.font.barlow_condensed_medium, FontWeight.Medium),
    Font(Res.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(Res.font.barlow_condensed_bold, FontWeight.Bold),
)
