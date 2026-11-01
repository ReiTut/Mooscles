package com.mooscles.screens.exercise

import com.mooscles.data.MuscleRegion
import com.mooscles.designsystem.illustration.BodyRegion

/**
 * The body map's region of this anatomy region (plan decision 8): the design system does not know
 * `:shared`, so the two enums share their 17 names, and the screens map one to the other by name.
 */
internal fun MuscleRegion.toBodyRegion(): BodyRegion = BodyRegion.valueOf(name)
