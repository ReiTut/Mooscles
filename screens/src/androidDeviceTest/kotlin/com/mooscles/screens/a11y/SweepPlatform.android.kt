package com.mooscles.screens.a11y

import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE

/**
 * The sweep includes 2.01 for linearly scaled text. Android's nonlinear scaling through 2x
 * can draw large text smaller and hide clipping.
 */
internal val SWEEP_FONT_SCALES: List<Float> = listOf(1f, 1.3f, 2f, LINEAR_TWO_X_FONT_SCALE)
