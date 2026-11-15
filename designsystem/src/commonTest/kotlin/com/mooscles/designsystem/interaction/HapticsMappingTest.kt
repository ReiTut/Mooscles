package com.mooscles.designsystem.interaction

import kotlin.test.Test
import kotlin.test.assertEquals

class HapticsMappingTest {
    private fun one(kind: AndroidHaptic) = listOf(HapticStep(kind, 0))

    @Test
    fun androidPlanMatchesTheSpecTable() {
        val expected = mapOf(
            HapticEvent.SetCompleted to one(AndroidHaptic.Confirm),
            HapticEvent.RestFinished to one(AndroidHaptic.Confirm),
            HapticEvent.StepperStep to one(AndroidHaptic.SegmentTick),
            HapticEvent.SetUnchecked to one(AndroidHaptic.SegmentFrequentTick),
            HapticEvent.Rejected to one(AndroidHaptic.Reject),
            HapticEvent.LongPress to one(AndroidHaptic.LongPress),
            HapticEvent.ToggleOn to one(AndroidHaptic.ToggleOn),
            HapticEvent.ToggleOff to one(AndroidHaptic.ToggleOff),
            HapticEvent.PersonalRecord to listOf(HapticStep(AndroidHaptic.Confirm, 0), HapticStep(AndroidHaptic.Confirm, 80)),
        )
        assertEquals(HapticEvent.entries.toSet(), expected.keys, "Every event must be covered")
        for ((event, plan) in expected) assertEquals(plan, androidHapticPlan(event), event.name)
    }

    @Test
    fun recordConfirmsTwiceEightyMsApart() {
        assertEquals(
            listOf(HapticStep(AndroidHaptic.Confirm, 0), HapticStep(AndroidHaptic.Confirm, 80)),
            androidHapticPlan(HapticEvent.PersonalRecord),
        )
    }
}
