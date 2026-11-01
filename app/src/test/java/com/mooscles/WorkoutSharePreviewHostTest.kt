package com.mooscles

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.mooscles.sharing.WorkoutShareIntent
import com.mooscles.ui.*
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class WorkoutSharePreviewHostTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewShowsTheGeneratedImageBeforeExplicitDestinationSelection() {
        val calls = mutableListOf<String?>()
        show(WorkoutSharePreviewState.Ready(listOf(image())), setOf(WorkoutShareIntent.WHATSAPP, WorkoutShareIntent.WHATSAPP_BUSINESS), onShare = { calls += it })
        awaitImage()
        assertEquals(emptyList<String?>(), calls)
        compose.onNodeWithTag("share-whatsapp").assertIsEnabled().performClick()
        compose.onNodeWithTag("share-whatsapp-business").assertIsEnabled().performClick()
        compose.onNodeWithTag("share-other-apps").performClick()
        compose.runOnIdle { assertEquals(listOf(WorkoutShareIntent.WHATSAPP, WorkoutShareIntent.WHATSAPP_BUSINESS, null), calls) }
    }

    @Test fun noWhatsappStillOffersTheAndroidSharesheetAfterPreview() {
        val calls = mutableListOf<String?>()
        show(WorkoutSharePreviewState.Ready(listOf(image())), onShare = { calls += it })
        awaitImage()
        compose.onNodeWithText("Choose an app").assertIsDisplayed().performClick()
        compose.onNodeWithTag("share-whatsapp").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf<String?>(null), calls) }
    }

    @Test fun loadingCanBeCancelledAndNeverOffersADestination() {
        var closed = 0
        show(WorkoutSharePreviewState.Loading, onDismiss = { closed++ })
        compose.onNodeWithTag("share-preview-loading").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("share-other-apps").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closed) }
    }

    @Test fun generationFailureOffersRetryAndCloseWithoutSharing() {
        var retried = 0
        var closed = 0
        show(WorkoutSharePreviewState.Failed, onRetry = { retried++ }, onDismiss = { closed++ })
        compose.onNodeWithTag("share-preview-retry").performClick()
        compose.onNodeWithTag("share-preview-close").performClick()
        compose.onNodeWithTag("share-other-apps").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, retried); assertEquals(1, closed) }
    }

    @Test fun completedHistoryDetailsExposeShareWorkout() {
        val workout = WorkoutUi("saved-workout", "Strength", startedAt = 1_000, endedAt = 61_000)
        compose.setContent { MaterialTheme { Box(Modifier.size(430.dp, 900.dp)) {
            WorkoutDetailScreen(workout, UiState(loading = false), noActions, onDeleted = {}, onExerciseHistory = {})
        } } }
        compose.onNodeWithTag("workout-details").performScrollToNode(hasTestTag("share-workout"))
        compose.onNodeWithTag("share-workout").assertIsDisplayed()
    }

    private fun show(state: WorkoutSharePreviewState, packages: Set<String> = emptySet(), onRetry: () -> Unit = {}, onDismiss: () -> Unit = {}, onShare: (String?) -> Unit = {}) {
        compose.setContent { MaterialTheme { Box(Modifier.size(430.dp, 900.dp)) {
            WorkoutSharePreviewContent(state, packages, false, onRetry, onDismiss, onShare)
        } } }
    }

    private fun awaitImage() {
        compose.waitUntil(timeoutMillis = 10_000) { compose.onAllNodesWithTag("share-preview-page-0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("share-preview-page-0").assertIsDisplayed()
        compose.waitForIdle()
    }

    private fun image(): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "workout-shares/preview-${UUID.randomUUID()}/workout.png")
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private val noActions = Proxy.newProxyInstance(MoosclesActions::class.java.classLoader, arrayOf(MoosclesActions::class.java)) { _, _, _ -> null } as MoosclesActions
}
