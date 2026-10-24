package com.mooscles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.mooscles.ui.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class RoutineFoldersHostTest {
    @get:Rule val compose = createComposeRule()
    private val upper = RoutineUi("upper", "Upper day", folderId = "ul")
    private val full = RoutineUi("full", "Full body day", folderId = "fullbody")
    private val active = WorkoutUi("saved-workout", "Upper day", 1_000, sourceRoutineId = upper.id)
    private val state = mutableStateOf(UiState(loading = false, routines = listOf(upper, full), active = active,
        routineFolders = listOf(RoutineFolderUi("default", "My routines"), RoutineFolderUi("ul", "U/L"), RoutineFolderUi("fullbody", "FULLBODY")),
        preferences = PreferencesUi(selectedRoutineFolderId = "ul")))
    private val calls = mutableListOf<String>()
    private val actions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader,
        arrayOf(MoosclesActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "toString" -> "Folder test actions"
            "selectRoutineFolder" -> {
                calls += method.name
                state.value = state.value.copy(preferences = state.value.preferences.copy(selectedRoutineFolderId = args!![0] as String))
                null
            }
            "moveRoutineToFolder" -> {
                calls += method.name
                state.value = state.value.copy(routines = state.value.routines.map {
                    if (it.id == args!![0]) it.copy(folderId = args[1] as String) else it
                })
                null
            }
            else -> { calls += method.name; null }
        }
    } as MoosclesActions

    @Test fun switchingFoldersFiltersCardsWithoutChangingActiveRoutineAssociation() {
        show()
        scroll("routine-upper-start").assertTextEquals("Resume active")
        compose.onNodeWithTag("routine-full-start").assertDoesNotExist()
        choose("fullbody")
        compose.onNodeWithTag("selected-routine-folder", useUnmergedTree = true).assertTextEquals("FULLBODY")
        scroll("routine-full-start").assertTextEquals("Start")
        compose.onNodeWithTag("routine-upper-start").assertDoesNotExist()
        choose("ul")
        scroll("routine-upper-start").assertTextEquals("Resume active")
        compose.runOnIdle {
            assertEquals(active, state.value.active)
            assertEquals(listOf("selectRoutineFolder", "selectRoutineFolder"), calls)
        }
    }

    @Test fun movingAnActiveRoutinesCardUsesItsIdAndKeepsTheSession() {
        show()
        scroll("routine-upper-actions").performClick()
        compose.onNodeWithText("Move to folder").performClick()
        compose.onNodeWithTag("move-to-folder-fullbody").performClick()
        compose.onNodeWithTag("routine-upper-start").assertDoesNotExist()
        choose("fullbody")
        scroll("routine-upper-start").assertTextEquals("Resume active")
        compose.runOnIdle {
            assertEquals(active, state.value.active)
            assertEquals("fullbody", state.value.routines.first { it.id == upper.id }.folderId)
            assertEquals(listOf("moveRoutineToFolder", "selectRoutineFolder"), calls)
        }
    }

    private fun show() = compose.setContent { MaterialTheme { RoutinesScreen(state.value, actions, {}, {}) } }
    private fun scroll(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("routines-list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun choose(id: String) {
        scroll("routine-folder-selector").performClick()
        compose.onNodeWithTag("folder-choice-$id").performClick()
    }
}
