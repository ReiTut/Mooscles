package com.mooscles.screens.format

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import com.mooscles.ui.RoutineTargetUi
import com.mooscles.ui.TargetSetUi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class RoutineTargetLineTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun resourcesInBothLanguages() {
        val nb = " "
        val mixed = RoutineTargetUi(listOf(TargetSetUi(10, 70.0), TargetSetUi(6, 80.0)), warmups = 1)
        val equal = RoutineTargetUi(List(3) { TargetSetUi(8, 80.0) }, warmups = 2)
        val cases = listOf(
            Triple("en", mixed, "Target 2 sets · 6–10 reps · 70–80${nb}kg +${nb}1${nb}warm-up"),
            Triple("en", equal, "Target 3×8 · 80${nb}kg +${nb}2${nb}warm-up"),
            Triple("pt-BR", mixed, "Alvo 2 séries · 6–10 reps · 70–80${nb}kg +${nb}1${nb}aquec."),
            Triple("pt-BR", equal, "Alvo 3×8 · 80${nb}kg +${nb}2${nb}aquec."),
        )
        for ((locale, target, expected) in cases) {
            runComposeUiTest {
                var text: String? = null
                var none: Any? = Unit
                withScreen(locale = locale) {
                    text = targetLine(target, pounds = false)?.text
                    none = targetLine(null, pounds = false)
                }
                waitForIdle()
                assertEquals(expected, text, locale)
                assertNull(none)
            }
            LocalAppLocale.restore()
        }
    }
}
