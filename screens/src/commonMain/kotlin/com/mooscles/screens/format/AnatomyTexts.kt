package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import com.mooscles.data.Anatomy
import com.mooscles.data.MuscleRegion
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.anatomy_primary
import com.mooscles.screens.resources.anatomy_primary_secondary
import com.mooscles.screens.resources.anatomy_region_abdominals
import com.mooscles.screens.resources.anatomy_region_abductors
import com.mooscles.screens.resources.anatomy_region_adductors
import com.mooscles.screens.resources.anatomy_region_biceps
import com.mooscles.screens.resources.anatomy_region_calves
import com.mooscles.screens.resources.anatomy_region_chest
import com.mooscles.screens.resources.anatomy_region_forearms
import com.mooscles.screens.resources.anatomy_region_glutes
import com.mooscles.screens.resources.anatomy_region_hamstrings
import com.mooscles.screens.resources.anatomy_region_lats
import com.mooscles.screens.resources.anatomy_region_lower_back
import com.mooscles.screens.resources.anatomy_region_middle_back
import com.mooscles.screens.resources.anatomy_region_neck
import com.mooscles.screens.resources.anatomy_region_quadriceps
import com.mooscles.screens.resources.anatomy_region_shoulders
import com.mooscles.screens.resources.anatomy_region_traps
import com.mooscles.screens.resources.anatomy_region_triceps
import com.mooscles.screens.resources.catalog_muscle_full_body
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The name of [region] in lowercase, as it sits inside a sentence: "quadriceps", "posteriores da coxa". */
@Composable
fun regionName(region: MuscleRegion): String = stringResource(region.text())

/**
 * The text equivalent of the body map (spec D21), always shown under it: "Primary: quadriceps.
 * Secondary: glutes, hamstrings.", only the first sentence without secondary regions, and "Full
 * body" (`catalog_muscle_full_body`, pre-flight F30) for [anatomy] `null` or without a primary
 * region, so it is never a sentence with nothing in it.
 */
@Composable
fun anatomySentence(anatomy: Anatomy?): String {
    if (anatomy == null || anatomy.primary.isEmpty()) return stringResource(Res.string.catalog_muscle_full_body)
    val primary = regionList(anatomy.primary)
    if (anatomy.secondary.isEmpty()) return stringResource(Res.string.anatomy_primary, primary)
    return stringResource(Res.string.anatomy_primary_secondary, primary, regionList(anatomy.secondary))
}

/** [regions] in their order, joined by ", " (the same in both languages). */
@Composable
private fun regionList(regions: List<MuscleRegion>): String {
    // A loop, not joinToString's lambda: stringResource has to be called from the composable itself.
    val names = ArrayList<String>(regions.size)
    for (region in regions) names += regionName(region)
    return names.joinToString(", ")
}

private fun MuscleRegion.text(): StringResource = when (this) {
    MuscleRegion.ABDOMINALS -> Res.string.anatomy_region_abdominals
    MuscleRegion.ABDUCTORS -> Res.string.anatomy_region_abductors
    MuscleRegion.ADDUCTORS -> Res.string.anatomy_region_adductors
    MuscleRegion.BICEPS -> Res.string.anatomy_region_biceps
    MuscleRegion.CALVES -> Res.string.anatomy_region_calves
    MuscleRegion.CHEST -> Res.string.anatomy_region_chest
    MuscleRegion.FOREARMS -> Res.string.anatomy_region_forearms
    MuscleRegion.GLUTES -> Res.string.anatomy_region_glutes
    MuscleRegion.HAMSTRINGS -> Res.string.anatomy_region_hamstrings
    MuscleRegion.LATS -> Res.string.anatomy_region_lats
    MuscleRegion.LOWER_BACK -> Res.string.anatomy_region_lower_back
    MuscleRegion.MIDDLE_BACK -> Res.string.anatomy_region_middle_back
    MuscleRegion.NECK -> Res.string.anatomy_region_neck
    MuscleRegion.QUADRICEPS -> Res.string.anatomy_region_quadriceps
    MuscleRegion.SHOULDERS -> Res.string.anatomy_region_shoulders
    MuscleRegion.TRAPS -> Res.string.anatomy_region_traps
    MuscleRegion.TRICEPS -> Res.string.anatomy_region_triceps
}
