package com.mooscles.screens.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.mooscles.designsystem.components.MButton
import com.mooscles.designsystem.components.MButtonSize
import com.mooscles.designsystem.components.MButtonVariant
import com.mooscles.designsystem.components.MIconButton
import com.mooscles.designsystem.components.MOverlayWindow
import com.mooscles.designsystem.components.MProgressBar
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.format.pluralCount
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.screens.resources.Res
import com.mooscles.screens.resources.share_cancel
import com.mooscles.screens.resources.share_choose_app
import com.mooscles.screens.resources.share_close
import com.mooscles.screens.resources.share_open_failed
import com.mooscles.screens.resources.share_other_apps
import com.mooscles.screens.resources.share_preview_failed
import com.mooscles.screens.resources.share_preview_hint
import com.mooscles.screens.resources.share_preview_image
import com.mooscles.screens.resources.share_preview_loading
import com.mooscles.screens.resources.share_preview_page
import com.mooscles.screens.resources.share_preview_page_failed
import com.mooscles.screens.resources.share_preview_pages
import com.mooscles.screens.resources.share_preview_title
import com.mooscles.screens.resources.share_retry
import com.mooscles.screens.resources.share_whatsapp
import com.mooscles.screens.resources.share_whatsapp_business
import com.mooscles.screens.resources.share_workout
import com.mooscles.screens.resources.summary_pr_count
import org.jetbrains.compose.resources.stringResource

/** The common preview has no file, platform package name or image decoder. The Android host owns those. */
@Immutable
sealed interface SharePreviewState {
    data object Loading : SharePreviewState
    data object Failed : SharePreviewState
    data class Ready(val pageCount: Int, val prCount: Int = 0, val requestKey: String = "") : SharePreviewState {
        init { require(pageCount > 0 && prCount >= 0) }
    }
}

enum class SharePreviewDestination { WhatsApp, WhatsAppBusiness, Other }

/** The Android chooser uses the same common/localized text as the preview. */
@Composable
fun shareWorkoutChooserTitle(): String = stringResource(Res.string.share_workout)

/** Opens one full-screen design-system overlay; Back and the explicit close action dismiss through the host. */
@Composable
fun SharePreviewSheet(
    state: SharePreviewState,
    availableDestinations: Set<SharePreviewDestination>,
    shareFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (SharePreviewDestination) -> Unit,
    page: @Composable (index: Int, description: String, onLoaded: () -> Unit, onFailed: () -> Unit) -> Unit,
) {
    MOverlayWindow(onDismissRequest = onDismiss) {
        SharePreviewContent(state, availableDestinations, shareFailed, onRetry, onDismiss, onShare, page)
    }
}

/** Shared UI, also mounted without another window by integration tests. Pages are the actual host-exported PNGs. */
@Composable
fun SharePreviewContent(
    state: SharePreviewState,
    availableDestinations: Set<SharePreviewDestination>,
    shareFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (SharePreviewDestination) -> Unit,
    page: @Composable (index: Int, description: String, onLoaded: () -> Unit, onFailed: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A retry/export/language change has a new state/request key. Old callbacks disappear with its lazy pages.
    var previewVisible by remember(state) { mutableStateOf(false) }
    var previewFailed by remember(state) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(MTheme.colors.bgBase)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(MTheme.space.s4)) {
        val footerLimit = maxHeight * MTheme.sizes.sharePreviewActionsMaxFraction
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MText(stringResource(Res.string.share_preview_title), MTheme.type.titleLg, Modifier.weight(1f))
                MIconButton(MIcons.X, stringResource(Res.string.share_close), onDismiss, Modifier.testTag("share-preview-close"))
            }
            when (state) {
                SharePreviewState.Loading -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        MProgressBar(null, Modifier.testTag("share-preview-loading"))
                        Spacer(Modifier.height(MTheme.space.s4))
                        MText(stringResource(Res.string.share_preview_loading), MTheme.type.bodyLg)
                    }
                    MButton(stringResource(Res.string.share_cancel), onDismiss, Modifier.fillMaxWidth(), MButtonVariant.Text)
                }
                SharePreviewState.Failed -> {
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        MText(stringResource(Res.string.share_preview_failed), MTheme.type.bodyLg, color = MTheme.colors.dangerText)
                        Spacer(Modifier.height(MTheme.space.s3))
                        MButton(stringResource(Res.string.share_retry), onRetry, Modifier.testTag("share-preview-retry"))
                    }
                    MButton(stringResource(Res.string.share_cancel), onDismiss, Modifier.fillMaxWidth(), MButtonVariant.Text)
                }
                is SharePreviewState.Ready -> {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("share-preview-pages"),
                        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
                        items(state.pageCount, key = { "${state.requestKey}-$it" }) { index ->
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
                                if (state.pageCount > 1) MText(stringResource(Res.string.share_preview_page, index + 1, state.pageCount),
                                    MTheme.type.caption, Modifier.testTag("share-preview-page-label-$index"))
                                val description = stringResource(Res.string.share_preview_image, index + 1, state.pageCount) + " · " +
                                    pluralCount(Res.plurals.summary_pr_count, state.prCount)
                                // After recreation or a new export, the list may restore a later page. Its decoded
                                // image is a valid visible preview too; requiring page zero would leave every share
                                // action disabled until the person manually scrolled back to the beginning.
                                page(index, description, { previewVisible = true }, { previewFailed = true })
                            }
                        }
                    }
                    SharePreviewActions(availableDestinations, shareFailed, previewFailed, previewVisible && !previewFailed,
                        state.pageCount, footerLimit, onRetry, onShare)
                }
            }
        }
    }
}

@Composable
private fun SharePreviewActions(
    destinations: Set<SharePreviewDestination>,
    shareFailed: Boolean,
    previewFailed: Boolean,
    enabled: Boolean,
    pageCount: Int,
    maxHeight: Dp,
    onRetry: () -> Unit,
    onShare: (SharePreviewDestination) -> Unit,
) {
    val preferred = when {
        SharePreviewDestination.WhatsApp in destinations -> SharePreviewDestination.WhatsApp
        SharePreviewDestination.WhatsAppBusiness in destinations -> SharePreviewDestination.WhatsAppBusiness
        else -> null
    }
    // Large-font actions remain reachable by scrolling, without squeezing away the preview or clipping buttons.
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).testTag("share-preview-actions"),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s3)) {
        // At large text sizes the wrapping page-count caption must not consume the image viewport.
        // It remains available with the destination instructions in this bounded, scrollable area.
        if (pageCount > 1) MText(pluralCount(Res.plurals.share_preview_pages, pageCount), MTheme.type.caption)
        if (previewFailed) {
            MText(stringResource(Res.string.share_preview_page_failed), MTheme.type.bodyMd, color = MTheme.colors.dangerText)
            MButton(stringResource(Res.string.share_retry), onRetry, Modifier.testTag("share-preview-retry"), MButtonVariant.Text)
        }
        if (shareFailed) MText(stringResource(Res.string.share_open_failed), MTheme.type.bodyMd, color = MTheme.colors.dangerText)
        MText(stringResource(Res.string.share_preview_hint), MTheme.type.bodyMd, color = MTheme.colors.textSecondary)
        if (preferred != null) MButton(stringResource(if (preferred == SharePreviewDestination.WhatsAppBusiness)
            Res.string.share_whatsapp_business else Res.string.share_whatsapp), { onShare(preferred) },
            Modifier.fillMaxWidth().testTag("share-whatsapp"), MButtonVariant.Primary, MButtonSize.L, MIcons.Share2, enabled = enabled)
        if (SharePreviewDestination.WhatsApp in destinations && SharePreviewDestination.WhatsAppBusiness in destinations) {
            MButton(stringResource(Res.string.share_whatsapp_business), { onShare(SharePreviewDestination.WhatsAppBusiness) },
                Modifier.fillMaxWidth().testTag("share-whatsapp-business"), MButtonVariant.Secondary, MButtonSize.L, enabled = enabled)
        }
        MButton(stringResource(if (preferred == null) Res.string.share_choose_app else Res.string.share_other_apps),
            { onShare(SharePreviewDestination.Other) }, Modifier.fillMaxWidth().testTag("share-other-apps"),
            MButtonVariant.Secondary, MButtonSize.L, enabled = enabled)
    }
}

/** The native decoder uses this while a lazy page is loading; failures are reported in the shared actions area. */
@Composable
fun SharePreviewPagePlaceholder(loading: Boolean) {
    Box(Modifier.fillMaxWidth().height(MTheme.sizes.sharePreviewPlaceholder), contentAlignment = Alignment.Center) {
        if (loading) MProgressBar(null)
    }
}
