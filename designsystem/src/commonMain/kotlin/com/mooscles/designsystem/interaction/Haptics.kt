package com.mooscles.designsystem.interaction

import androidx.compose.runtime.Composable

/** The moments the app gives haptic feedback for (spec "Háptico"). */
enum class HapticEvent {
    SetCompleted, PersonalRecord, RestFinished, StepperStep, SetUnchecked,
    Rejected, LongPress, ToggleOn, ToggleOff,
}

/** The single entry point for haptics. Platform implementations: [rememberPlatformHaptics]. */
fun interface MoosclesHaptics {
    fun perform(event: HapticEvent)
}

/** Does nothing: for previews and tests that do not care about haptics. */
object NoHaptics : MoosclesHaptics {
    override fun perform(event: HapticEvent) = Unit
}

/** Records every event, for tests. */
class RecordingHaptics : MoosclesHaptics {
    private val recorded = mutableListOf<HapticEvent>()
    val events: List<HapticEvent> get() = recorded

    override fun perform(event: HapticEvent) {
        recorded += event
    }
}

/**
 * The platform haptics. Android: `LocalHapticFeedback` with the [androidHapticPlan] steps.
 * iOS: the UIKit feedback generators, chosen by [iosHapticFor].
 */
@Composable
expect fun rememberPlatformHaptics(): MoosclesHaptics
