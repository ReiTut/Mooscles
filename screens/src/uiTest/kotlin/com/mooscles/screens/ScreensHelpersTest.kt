package com.mooscles.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.workout.RecompositionProbe
import com.mooscles.ui.RoutineSetUi
import com.mooscles.ui.SetUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The test helpers the screen tests lean on fail when they should, so those tests cannot pass in the void. */
@OptIn(ExperimentalTestApi::class)
class ScreensHelpersTest {
    @Test
    fun recordingActionsWritesMethodAndArguments() {
        val actions = RecordingActions()
        actions.updateSet("s2", 8, 82.5, completed = true, warmup = false, assistance = false)
        actions.skipTimer()
        actions.startWorkout(null)
        actions.restoreSet("e1", 2, SetUi("s3", reps = 5))
        assertEquals(
            listOf(
                "updateSet:s2,8,82.5,true,false,false",
                "skipTimer:",
                "startWorkout:null",
                "restoreSet:e1,2,${SetUi("s3", reps = 5)}",
            ),
            actions.calls,
        )
    }

    @Test
    fun recordingActionsJoinsListsAndKeepsTheRestoredRoutineSet() {
        val actions = RecordingActions()
        actions.addRoutineExercises("r1", listOf("bench", "row"))
        actions.reorderRoutineFolders(listOf("default", "f1"))
        actions.createRoutine("Push", "f1", "r2")
        val removed = RoutineSetUi("s1", reps = 10, weightKg = 40.0, inherited = false, warmup = true)
        actions.restoreRoutineSet("e1", 0, removed)
        assertEquals(
            listOf(
                "addRoutineExercises:r1,bench|row",
                "reorderRoutineFolders:default|f1",
                "createRoutine:Push,f1,r2",
                "restoreRoutineSet:e1,0,RoutineSetUi(id=s1, reps=10, weightKg=40.0, inherited=false, warmup=true)",
            ),
            actions.calls,
        )
    }

    @Test
    fun numberTextsAreTheOnlyOnesMeasured() {
        for (text in listOf("82.5", "82,5 kg", "82.5 kg × 8", "1:02:18", "−15 s", "1 h 12 min", "185 lb")) {
            assertTrue(isNumberText(text), text)
        }
        for (text in listOf("Rest", "3 sets", "Set 1", "kg", "", "Bench 80")) assertFalse(isNumberText(text), text)
    }

    @Test
    fun assertNumbersWholeFailsForACutNumber() = runComposeUiTest {
        withScreen {
            Box(Modifier.width(20.dp)) { MText("187.5", MTheme.type.numDock, softWrap = false, maxLines = 1) }
        }
        waitForIdle()
        assertFails { assertNumbersWhole() }
    }

    /** A wrapping button text cut by a fixed height fails the button text check; a whole one passes. */
    @Test
    fun assertButtonTextsWholeFailsForAClippedButtonText() = runComposeUiTest {
        var clipped by mutableStateOf(true)
        withScreen {
            Box(Modifier.width(200.dp).then(if (clipped) Modifier.height(8.dp) else Modifier).clickable {}) {
                MText("Adicionar série", MTheme.type.labelLg)
            }
        }
        waitForIdle()
        assertFails { assertButtonTextsWhole("clipped") }
        clipped = false
        waitForIdle()
        assertTrue(assertButtonTextsWhole("whole") >= 1)
    }

    @Test
    fun assertNumbersWholePassesForWholeNumbersAndFailsWithoutAny() = runComposeUiTest {
        val keys = mutableListOf<String>()
        withScreen(probe = { keys += it }) {
            Column {
                RecompositionProbe("column")
                MText("82.5 kg × 8", MTheme.type.bodyMd)
                MText("Rest", MTheme.type.bodyMd)
            }
        }
        waitForIdle()
        assertNumbersWhole(atLeast = 1)
        assertFails { assertNumbersWhole(atLeast = 2) }
        assertTrue("column" in keys, "the probe was not called: $keys")
    }
}
