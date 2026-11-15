package com.mooscles.designsystem.interaction

import com.mooscles.designsystem.tokens.MoosclesTiming

/** The Android feedback types the plan uses; each maps to the `HapticFeedbackType` of the same name. */
enum class AndroidHaptic { Confirm, Reject, SegmentTick, SegmentFrequentTick, LongPress, ToggleOn, ToggleOff }

/** One haptic to fire, [delayBeforeMs] after the previous step finished. */
data class HapticStep(val kind: AndroidHaptic, val delayBeforeMs: Long)

/** The Android column of the spec's haptics table. */
fun androidHapticPlan(event: HapticEvent): List<HapticStep> = when (event) {
    HapticEvent.SetCompleted, HapticEvent.RestFinished -> single(AndroidHaptic.Confirm)
    HapticEvent.PersonalRecord -> listOf(
        HapticStep(AndroidHaptic.Confirm, 0),
        HapticStep(AndroidHaptic.Confirm, MoosclesTiming().hapticDoubleGap),
    )
    HapticEvent.StepperStep -> single(AndroidHaptic.SegmentTick)
    HapticEvent.SetUnchecked -> single(AndroidHaptic.SegmentFrequentTick)
    HapticEvent.Rejected -> single(AndroidHaptic.Reject)
    HapticEvent.LongPress -> single(AndroidHaptic.LongPress)
    HapticEvent.ToggleOn -> single(AndroidHaptic.ToggleOn)
    HapticEvent.ToggleOff -> single(AndroidHaptic.ToggleOff)
}

private fun single(kind: AndroidHaptic) = listOf(HapticStep(kind, 0))
