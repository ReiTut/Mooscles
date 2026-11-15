package com.mooscles.designsystem.theme

import androidx.compose.runtime.Composable

/**
 * Whether Android's animator duration scale is zero. Updates when the setting changes.
 */
@Composable
expect fun rememberSystemReducedMotion(): Boolean

/** The Android rule: an animator duration scale of exactly 0 means "remove animations". */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f
