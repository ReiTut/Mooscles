package com.mooscles.designsystem.interaction

import androidx.compose.runtime.Composable
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

/** Calls the UIKit feedback generators directly, so it does not depend on what `LocalHapticFeedback` covers on iOS. */
private object IosHaptics : MoosclesHaptics {
    override fun perform(event: HapticEvent) {
        when (iosHapticFor(event)) {
            IosHaptic.ImpactMedium -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
            IosHaptic.ImpactLight -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            IosHaptic.NotificationSuccess -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            IosHaptic.NotificationWarning -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
            IosHaptic.NotificationError -> notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            IosHaptic.Selection -> UISelectionFeedbackGenerator().apply {
                prepare()
                selectionChanged()
            }
        }
    }

    private fun impact(style: UIImpactFeedbackStyle) {
        UIImpactFeedbackGenerator(style = style).apply {
            prepare()
            impactOccurred()
        }
    }

    private fun notify(type: UINotificationFeedbackType) {
        UINotificationFeedbackGenerator().apply {
            prepare()
            notificationOccurred(type)
        }
    }
}

@Composable
actual fun rememberPlatformHaptics(): MoosclesHaptics = IosHaptics
