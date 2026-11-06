package com.mooscles.screens.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.screens.LINEAR_TWO_X_FONT_SCALE
import com.mooscles.screens.screenRestoration
import com.mooscles.screens.withScreen
import com.mooscles.screens.flow.capture
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Common preview behavior; the Android host tests separately decode and show the actual exported PNG. */
@OptIn(ExperimentalTestApi::class)
class SharePreviewSheetTest {
    private val both = setOf(SharePreviewDestination.WhatsApp, SharePreviewDestination.WhatsAppBusiness)

    @AfterTest
    fun restoreLanguage() { LocalAppLocale.restore() }

    private fun ComposeUiTest.preview(
        state: () -> SharePreviewState,
        destinations: Set<SharePreviewDestination> = both,
        loaded: () -> Boolean = { true },
        failed: () -> Boolean = { false },
        locale: String = "en",
        onRetry: () -> Unit = {},
        onClose: () -> Unit = {},
        onShare: (SharePreviewDestination) -> Unit = {},
    ) {
        withScreen(locale = locale, reducedMotion = true, size = DpSize(412.dp, 800.dp)) {
            SharePreviewContent(state(), destinations, false, onRetry, onClose, onShare,
                page = { index, description, ready, failure -> FakePage(index, description, loaded(), failed(), ready, failure) })
        }
        waitForIdle()
    }

    @Test
    fun loadingCanBeCancelledWithoutOfferingAShareDestination() = runComposeUiTest {
        var closed = 0
        preview({ SharePreviewState.Loading }, onClose = { closed++ })
        onNodeWithTag("share-preview-loading").assertIsDisplayed()
        onNodeWithText("Creating your workout image…").assertIsDisplayed()
        onNodeWithText("Cancel").performClick()
        onNodeWithTag("share-other-apps").assertDoesNotExist()
        assertEquals(1, closed)
    }

    @Test
    fun generationFailureOffersRetryAndCloseWithoutSharing() = runComposeUiTest {
        var retries = 0
        var closed = 0
        preview({ SharePreviewState.Failed }, onRetry = { retries++ }, onClose = { closed++ })
        onNodeWithTag("share-preview-retry").performClick()
        onNodeWithTag("share-preview-close").performClick()
        onNodeWithTag("share-other-apps").assertDoesNotExist()
        assertEquals(1, retries)
        assertEquals(1, closed)
    }

    @Test
    fun sharingWaitsForFirstPageAndOnlyRunsAfterExplicitSelection() = runComposeUiTest {
        var loaded by mutableStateOf(false)
        val calls = mutableListOf<SharePreviewDestination>()
        preview({ SharePreviewState.Ready(1, 11) }, loaded = { loaded }, onShare = { calls += it })
        onNodeWithTag("share-whatsapp").assertIsNotEnabled()
        onNodeWithTag("share-other-apps").assertIsNotEnabled()
        assertEquals(emptyList(), calls)
        runOnIdle { loaded = true }
        waitForIdle()
        onNodeWithTag("share-whatsapp").assertIsEnabled().performClick()
        onNodeWithTag("share-whatsapp-business").performClick()
        onNodeWithTag("share-other-apps").performClick()
        assertEquals(listOf(SharePreviewDestination.WhatsApp, SharePreviewDestination.WhatsAppBusiness, SharePreviewDestination.Other), calls)
    }

    @Test
    fun businessOnlyIsThePreferredDestinationWithoutInventingASecondButton() = runComposeUiTest {
        val calls = mutableListOf<SharePreviewDestination>()
        preview({ SharePreviewState.Ready(1) }, setOf(SharePreviewDestination.WhatsAppBusiness), onShare = { calls += it })
        onNodeWithText("Share with WhatsApp Business").assertIsDisplayed()
        onNodeWithTag("share-whatsapp-business").assertDoesNotExist()
        onNodeWithTag("share-whatsapp").performClick()
        assertEquals(listOf(SharePreviewDestination.WhatsAppBusiness), calls)
    }

    @Test
    fun noWhatsAppOffersTheSystemChooserAfterThePreview() = runComposeUiTest {
        val calls = mutableListOf<SharePreviewDestination>()
        preview({ SharePreviewState.Ready(1) }, emptySet(), onShare = { calls += it })
        onNodeWithTag("share-whatsapp").assertDoesNotExist()
        onNodeWithText("Choose an app").performClick()
        assertEquals(listOf(SharePreviewDestination.Other), calls)
    }

    @Test
    fun everyPageRepeatsTheWorkoutWidePrCountAndKeepsItsPosition() = runComposeUiTest {
        preview({ SharePreviewState.Ready(3, 11) })
        onNodeWithText("3 images · all exercises are included in order").assertIsDisplayed()
        for (index in 0..2) {
            onNodeWithTag("share-preview-pages").performScrollToIndex(index)
            onNodeWithTag("share-preview-page-$index")
                .assertContentDescriptionEquals("Workout image ${index + 1} of 3 · 11 exercises with personal records")
            onNodeWithText("Page ${index + 1} of 3").assertIsDisplayed()
        }
    }

    @Test
    fun pageFailureDisablesSharingAndRetryWaitsForTheNewPage() = runComposeUiTest {
        var request by mutableStateOf(SharePreviewState.Ready(1, requestKey = "first"))
        var loaded by mutableStateOf(true)
        var failed by mutableStateOf(false)
        var retries = 0
        preview({ request }, loaded = { loaded }, failed = { failed }, onRetry = {
            retries++
            loaded = false
            failed = false
            request = SharePreviewState.Ready(1, requestKey = "second")
        })
        onNodeWithTag("share-other-apps").assertIsEnabled()
        runOnIdle { failed = true }
        waitForIdle()
        onNodeWithTag("share-other-apps").assertIsNotEnabled()
        onNodeWithTag("share-preview-retry").performClick()
        waitForIdle()
        assertEquals(1, retries)
        onNodeWithTag("share-other-apps").assertIsNotEnabled()
        runOnIdle { loaded = true }
        waitForIdle()
        onNodeWithTag("share-other-apps").assertIsEnabled()
    }

    @Test
    fun zeroCountsAndChooserTitlesAreLocalizedInBothLanguages() {
        for ((locale, description, chooser) in listOf(
            Triple("en", "Workout image 1 of 1 · 0 exercises with personal records", "Share workout"),
            Triple("pt-BR", "Imagem do treino 1 de 1 · 0 exercícios com recordes pessoais", "Compartilhar treino"),
        )) {
            runComposeUiTest {
                var title = ""
                withScreen(locale = locale, reducedMotion = true) {
                    title = shareWorkoutChooserTitle()
                    SharePreviewContent(SharePreviewState.Ready(1, 0), emptySet(), false, {}, {}, {},
                        page = { index, label, ready, failed -> FakePage(index, label, true, false, ready, failed) })
                }
                waitForIdle()
                assertEquals(chooser, title)
                onNodeWithTag("share-preview-page-0").assertContentDescriptionEquals(description)
            }
        }
    }

    @Test
    fun narrowLargeFontActionsRemainReachableInBothThemes() {
        for (dark in listOf(false, true)) for (width in listOf(320, 412)) {
            runComposeUiTest {
                withScreen(dark = dark, fontScale = LINEAR_TWO_X_FONT_SCALE, locale = "pt-BR", reducedMotion = true,
                    size = DpSize(width.dp, 800.dp)) {
                    SharePreviewContent(SharePreviewState.Ready(2, 11), both, true, {}, {}, {},
                        page = { index, label, ready, failed -> FakePage(index, label, true, false, ready, failed) })
                }
                waitForIdle()
                capture("phase5b-preview-${width}-${if (dark) "dark" else "light"}-2x")
                onNodeWithTag("share-preview-page-0").assertIsDisplayed()
                for (tag in listOf("share-whatsapp", "share-whatsapp-business", "share-other-apps")) {
                    onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertIsEnabled()
                }
                onNodeWithTag("share-preview-close").assertIsDisplayed()
            }
        }
    }

    @Test
    fun fullScreenSheetUsesItsOwnCloseAction() = runComposeUiTest {
        var closed = 0
        withScreen(locale = "en", reducedMotion = true) {
            SharePreviewSheet(SharePreviewState.Loading, emptySet(), false, {}, { closed++ }, {},
                page = { _, _, _, _ -> })
        }
        waitForIdle()
        onNodeWithTag("share-preview-close").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun restoredLaterPageEnablesSharingWithoutReturningToTheFirstPage() = runComposeUiTest {
        val restoration = screenRestoration()
        // Forced test locales replace the subtree during restoration; language is tested separately.
        withScreen(reducedMotion = true, restoration = restoration, size = DpSize(412.dp, 800.dp)) {
            SharePreviewContent(SharePreviewState.Ready(10, 11, "same-export"), both, false, {}, {}, {},
                page = { index, label, ready, failed -> FakePage(index, label, true, false, ready, failed) })
        }
        waitForIdle()
        onNodeWithTag("share-preview-pages").performScrollToIndex(9)
        waitForIdle()
        capture("phase5b-preview-last-page")
        onNodeWithTag("share-preview-page-9").assertIsDisplayed()
        restoration.emulateSaveAndRestore()
        waitForIdle()
        capture("phase5b-preview-last-page-restored")
        onNodeWithTag("share-preview-page-9").assertIsDisplayed()
        onNodeWithTag("share-other-apps").assertIsEnabled()
    }
}

@Composable
private fun FakePage(index: Int, description: String, loaded: Boolean, failed: Boolean, onLoaded: () -> Unit, onFailed: () -> Unit) {
    LaunchedEffect(index, loaded, failed) {
        if (failed) onFailed() else if (loaded) onLoaded()
    }
    Box(Modifier.fillMaxWidth().height(100.dp).testTag("share-preview-page-$index").semantics { contentDescription = description })
}
