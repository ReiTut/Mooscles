package com.mooscles.screens.workout

import com.mooscles.designsystem.format.NumberLocale
import com.mooscles.ui.MoosclesActions
import com.mooscles.ui.SetField
import com.mooscles.ui.SetSuggestion
import com.mooscles.ui.SetUi
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Language changes must not reinterpret rounded pound drafts as new weights. */
class SetRowEditorLocaleTest {
    private fun actions(writes: MutableList<Double>) = Proxy.newProxyInstance(
        MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java),
    ) { _, method, args ->
        if (method.name == "updateSet") writes += args!![2] as Double
        null
    } as MoosclesActions

    @Test
    fun editingRepsAfterEitherLanguageChangePreservesSavedExactKg() {
        for (before in NumberLocale.entries) {
            val after = if (before == NumberLocale.EN) NumberLocale.PT_BR else NumberLocale.EN
            val writes = mutableListOf<Double>()
            val actions = actions(writes)
            val exactKg = 22.123456789
            val set = SetUi("set", reps = 8, weightKg = exactKg)
            val screen = ActiveWorkoutScreenState()
            val original = SetRowEditor(set, true, SetSuggestion(), screen, actions, before)
            val text = original.savedText(SetField.LOAD)
            original.type(SetField.LOAD, text)

            val changed = SetRowEditor(set, true, SetSuggestion(), screen, actions, after)
            changed.type(SetField.REPS, "9")

            assertEquals(text, changed.shown(SetField.LOAD))
            assertEquals(exactKg, writes.single(), 0.0)
            assertEquals(exactKg, changed.snapshot().weightKg, 0.0)
        }
    }

    @Test
    fun editingRepsAfterEitherLanguageChangePreservesExactPendingKg() {
        for (before in NumberLocale.entries) {
            val after = if (before == NumberLocale.EN) NumberLocale.PT_BR else NumberLocale.EN
            val writes = mutableListOf<Double>()
            val actions = actions(writes)
            val exactPendingKg = 22.123456789
            val set = SetUi("set", reps = 8, weightKg = 17.0)
            val screen = ActiveWorkoutScreenState()
            val original = SetRowEditor(set, true, SetSuggestion(), screen, actions, before)
            val text = original.loadText(exactPendingKg)
            screen.type(set.id, SetField.LOAD, text)
            screen.sent(set.id, SetField.LOAD, exactPendingKg)

            val changed = SetRowEditor(set, true, SetSuggestion(), screen, actions, after)
            changed.type(SetField.REPS, "9")

            assertEquals(text, changed.shown(SetField.LOAD))
            assertEquals(exactPendingKg, writes.single(), 0.0)
            assertEquals(exactPendingKg, changed.snapshot().weightKg, 0.0)
        }
    }
}
