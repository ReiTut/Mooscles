package com.mooscles.designsystem.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
actual fun rememberPlatformHaptics(): MoosclesHaptics {
    val feedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    return remember(feedback, scope) {
        MoosclesHaptics { event ->
            val plan = androidHapticPlan(event)
            val first = plan.first()
            // The first step fires now, in the caller's frame; later steps wait in the scope.
            feedback.performHapticFeedback(first.kind.toType())
            if (plan.size > 1) {
                scope.launch {
                    for (step in plan.drop(1)) {
                        delay(step.delayBeforeMs)
                        feedback.performHapticFeedback(step.kind.toType())
                    }
                }
            }
        }
    }
}

private fun AndroidHaptic.toType(): HapticFeedbackType = when (this) {
    AndroidHaptic.Confirm -> HapticFeedbackType.Confirm
    AndroidHaptic.Reject -> HapticFeedbackType.Reject
    AndroidHaptic.SegmentTick -> HapticFeedbackType.SegmentTick
    AndroidHaptic.SegmentFrequentTick -> HapticFeedbackType.SegmentFrequentTick
    AndroidHaptic.LongPress -> HapticFeedbackType.LongPress
    AndroidHaptic.ToggleOn -> HapticFeedbackType.ToggleOn
    AndroidHaptic.ToggleOff -> HapticFeedbackType.ToggleOff
}
