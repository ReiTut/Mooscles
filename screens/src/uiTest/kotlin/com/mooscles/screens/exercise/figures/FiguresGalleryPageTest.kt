package com.mooscles.screens.exercise.figures

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.v2.runComposeUiTest
import com.mooscles.designsystem.components.ExerciseMediaFigureTag
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.withScreen
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The gallery's "Figures" page (plan decision 5): every pose, both frames, by its shown name. */
@OptIn(ExperimentalTestApi::class)
class FiguresGalleryPageTest {
    @AfterTest
    fun restoreSystemLanguage() {
        LocalAppLocale.restore()
    }

    @Test
    fun showsEveryPoseTwice() = runComposeUiTest {
        val page = figuresGalleryPage()
        withScreen(locale = "pt-BR") { page.content() }
        waitForIdle()
        // A lazy list: bring each exercise in, then find its two frames.
        val list = onNodeWithTag(FIGURES_GALLERY_LIST_TAG)
        for (id in ExerciseFigures.stableIds) {
            list.performScrollToKey(id)
            waitForIdle()
            onNodeWithTag(figureFrameTag(id, start = true)).assertExists()
            onNodeWithTag(figureFrameTag(id, start = false)).assertExists()
        }
        // Every frame tag on screen belongs to a figure, two per exercise (counted on the first screen).
        val frames = SemanticsMatcher("a figure frame") { node ->
            SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag].startsWith("figure-")
        }
        list.performScrollToIndex(0)
        waitForIdle()
        val shown = onAllNodes(frames).fetchSemanticsNodes().size
        assertTrue(shown >= 2 && shown % 2 == 0, "$shown frames on the first screen")
        assertEquals(shown / 2, onAllNodesWithTag(ExerciseMediaFigureTag).fetchSemanticsNodes().size, "one thumbnail per exercise shown")
        // The names are the shown ones, in the gallery's language.
        onNodeWithText("Supino reto com barra").assertExists()
        list.performScrollToKey("bodyweight_pull_up")
        waitForIdle()
        onNodeWithText("Barra fixa pronada").assertExists()
    }
}
