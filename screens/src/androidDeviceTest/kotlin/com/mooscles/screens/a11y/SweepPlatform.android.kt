package com.mooscles.screens.a11y

import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE

// The emulator's side of WorkoutAccessibilitySweepTest: the same declarations as SweepPlatform.ios.kt (not expect/actual).

/**
 * The font scales the sweep runs at: 1.0, 1.3 and 2.0, and on Android 2.01 too, where text grows
 * linearly as on iOS (below 2x Android scales large text by less: how the phase 2 "187.5" cut got past the emulator).
 */
internal val SWEEP_FONT_SCALES: List<Float> = listOf(1f, 1.3f, 2f, LINEAR_TWO_X_FONT_SCALE)
