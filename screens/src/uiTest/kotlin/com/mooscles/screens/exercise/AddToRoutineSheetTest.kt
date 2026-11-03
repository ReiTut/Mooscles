package com.mooscles.screens.exercise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import com.mooscles.data.RoutineFolder
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.RecordingActions
import com.mooscles.screens.withScreen
import com.mooscles.ui.RoutineFolderUi
import com.mooscles.ui.RoutineUi
import com.mooscles.ui.UiState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** "Add to routine" of the exercise detail, in pt-BR. */
@OptIn(ExperimentalTestApi::class)
class AddToRoutineSheetTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    private val exerciseId = "catalog_barbell_bench_flat"
    private val home = RoutineFolderUi(RoutineFolder.DEFAULT_ID, RoutineFolder.DEFAULT_NAME)
    private val hypertrophy = RoutineFolderUi("f2", "Hipertrofia")
    private val empty = RoutineFolderUi("f3", "Vazia")

    private class Outcome {
        val added = mutableListOf<Pair<String, String>>()
        var dismissed = 0
    }

    private fun ComposeUiTest.show(state: UiState, actions: RecordingActions): Outcome {
        val outcome = Outcome()
        withScreen(locale = "pt-BR") {
            var open by remember { mutableStateOf(true) }
            if (open) {
                AddToRoutineSheet(
                    exerciseId = exerciseId,
                    state = state,
                    actions = actions,
                    onAdded = { id, name ->
                        outcome.added += id to name
                        open = false
                    },
                    onDismiss = {
                        outcome.dismissed++
                        open = false
                    },
                )
            }
        }
        waitForIdle()
        return outcome
    }

    private fun ComposeUiTest.top(tag: String): Dp = onNodeWithTag(tag).getUnclippedBoundsInRoot().top

    @Test
    fun routinesAreGroupedByFolderWithTheDefaultTranslated() = runComposeUiTest {
        val routines = listOf(
            RoutineUi("r1", "Treino A", folderId = home.id),
            RoutineUi("r2", "Treino B", folderId = hypertrophy.id),
            RoutineUi("r3", "Treino C", folderId = home.id),
            // A folder no longer listed: its routine goes with the default folder.
            RoutineUi("r4", "Treino D", folderId = "gone"),
        )
        show(UiState(loading = false, routines = routines, routineFolders = listOf(home, hypertrophy, empty)), RecordingActions())
        onNodeWithTag(AddToRoutineSheetTags.folder(home.id)).assertTextEquals("Minhas rotinas")
        onNodeWithTag(AddToRoutineSheetTags.folder(hypertrophy.id)).assertTextEquals("Hipertrofia")
        // A folder without routines is left out.
        onNodeWithTag(AddToRoutineSheetTags.folder(empty.id)).assertDoesNotExist()
        val order = listOf(
            AddToRoutineSheetTags.folder(home.id), AddToRoutineSheetTags.routine("r1"), AddToRoutineSheetTags.routine("r3"),
            AddToRoutineSheetTags.routine("r4"), AddToRoutineSheetTags.folder(hypertrophy.id), AddToRoutineSheetTags.routine("r2"),
        )
        val tops = order.map { top(it) }
        assertEquals(tops.sorted(), tops, "tops of $order: $tops")
        onNode(hasText("Treino A") and hasAnyAncestor(hasTestTag(AddToRoutineSheetTags.ROOT)), useUnmergedTree = true).assertIsDisplayed()
        onNode(hasText("Adicionar à rotina") and hasAnyAncestor(hasTestTag(AddToRoutineSheetTags.ROOT)), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aTapAddsOnceAndCloses() = runComposeUiTest {
        val actions = RecordingActions()
        val outcome = show(
            UiState(loading = false, routines = listOf(RoutineUi("r1", "Treino A"), RoutineUi("r2", "Treino B")), routineFolders = listOf(home)),
            actions,
        )
        // Two quick taps, in one gesture stream: the sheet has no frame to leave in between.
        onNodeWithTag(AddToRoutineSheetTags.routine("r1")).performTouchInput {
            click()
            advanceEventTime(100)
            click()
        }
        waitForIdle()
        assertEquals(listOf("addRoutineExercise:r1,$exerciseId"), actions.calls.filter { it.startsWith("addRoutineExercise:") })
        assertEquals(listOf("r1" to "Treino A"), outcome.added)
        onNodeWithTag(AddToRoutineSheetTags.ROOT).assertDoesNotExist()
        assertTrue(actions.calls.none { it.startsWith("addWorkoutExercise:") })
    }

    @Test
    fun withoutRoutinesSaysWhereToCreateOne() = runComposeUiTest {
        val actions = RecordingActions()
        show(UiState(loading = false, routines = emptyList(), routineFolders = listOf(home)), actions)
        onNodeWithTag(AddToRoutineSheetTags.EMPTY).assertTextEquals("Você ainda não tem rotinas. Crie uma na aba Treinar.")
        onNodeWithTag(AddToRoutineSheetTags.folder(home.id)).assertDoesNotExist()
        assertEquals(emptyList(), actions.calls)
    }
}
