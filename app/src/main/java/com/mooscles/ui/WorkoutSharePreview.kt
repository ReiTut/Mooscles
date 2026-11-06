package com.mooscles.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import com.mooscles.MoosclesApplication
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.screens.share.ShareCardDensity
import com.mooscles.screens.share.ShareCardPainter
import com.mooscles.screens.share.ShareCards
import com.mooscles.screens.share.rememberShareCardStyles
import com.mooscles.screens.share.rememberShareCardTexts
import com.mooscles.screens.share.loadShareWordmark
import com.mooscles.screens.share.SharePreviewContent
import com.mooscles.screens.share.SharePreviewDestination
import com.mooscles.screens.share.SharePreviewPagePlaceholder
import com.mooscles.screens.share.SharePreviewSheet
import com.mooscles.screens.share.SharePreviewState
import com.mooscles.screens.share.shareWorkoutChooserTitle
import com.mooscles.sharing.WorkoutCardRenderer
import com.mooscles.sharing.WorkoutShareIntent
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

internal sealed interface WorkoutSharePreviewState {
    data object Loading : WorkoutSharePreviewState
    data object Failed : WorkoutSharePreviewState
    data class Ready(val files: List<File>, val prCount: Int = 0) : WorkoutSharePreviewState
}

/**
 * Reads a fresh saved snapshot once per request; rendering never writes workout data. The card is
 * the common one (`ShareCardPainter`, in `:screens`): its texts and fonts are resolved
 * here, in the composition, and the drawing and the PNG run on a background dispatcher. The pages
 * shown are those very PNGs, decoded, so the preview is the bitmap that is shared.
 */
@Composable
fun WorkoutSharePreview(workoutId: String, pounds: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var attempt by remember(workoutId, pounds) { mutableIntStateOf(0) }
    var shareFailed by remember(workoutId, pounds, attempt) { mutableStateOf(false) }
    var available by remember(context) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(context) {
        available = withContext(Dispatchers.IO) { WorkoutShareIntent.availableWhatsAppPackages(context) }
    }
    var state by remember(workoutId, pounds, attempt) { mutableStateOf<WorkoutSharePreviewState>(WorkoutSharePreviewState.Loading) }
    val texts = rememberShareCardTexts()
    val styles = rememberShareCardStyles()
    val fonts = LocalFontFamilyResolver.current
    val locale = currentNumberLocale()
    // The texts, fonts and number style are keys too: a language change redraws the card in it.
    LaunchedEffect(workoutId, pounds, attempt, texts, styles, fonts, locale) {
        state = WorkoutSharePreviewState.Loading
        shareFailed = false
        state = try {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as MoosclesApplication
                val card = ShareCards.from(app.repository.snapshot(), workoutId, pounds, locale,
                    TimeZone.currentSystemDefault(), texts)
                // A measurer of its own: a retry can start while a cancelled export still draws.
                val measurer = TextMeasurer(fonts, ShareCardDensity, LayoutDirection.Ltr)
                val files = WorkoutCardRenderer().export(context.applicationContext, card, ShareCardPainter(styles, loadShareWordmark()), measurer)
                check(files.isNotEmpty())
                WorkoutSharePreviewState.Ready(files, card.prCount)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WorkoutSharePreviewState.Failed
        }
    }
    val chooserTitle = shareWorkoutChooserTitle()
    val ready = state as? WorkoutSharePreviewState.Ready
    SharePreviewSheet(state.shared(), available.destinations(), shareFailed,
        onRetry = { attempt++ }, onDismiss = onDismiss,
        onShare = { destination ->
            shareFailed = try {
                !WorkoutShareIntent.launch(context, ready?.files.orEmpty(), chooserTitle, destination.packageName())
            } catch (_: Exception) {
                true
            }
        },
        page = { index, description, onLoaded, onFailed ->
            ready?.files?.getOrNull(index)?.let { WorkoutSharePage(it, index, description, onLoaded, onFailed) }
        })
}

@Composable
internal fun WorkoutSharePreviewContent(
    state: WorkoutSharePreviewState,
    availablePackages: Set<String>,
    shareFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onShare: (String?) -> Unit,
) {
    val ready = state as? WorkoutSharePreviewState.Ready
    SharePreviewContent(state.shared(), availablePackages.destinations(), shareFailed, onRetry, onDismiss,
        onShare = { onShare(it.packageName()) },
        page = { index, description, onLoaded, onFailed ->
            ready?.files?.getOrNull(index)?.let { WorkoutSharePage(it, index, description, onLoaded, onFailed) }
        })
}

private fun WorkoutSharePreviewState.shared(): SharePreviewState = when (this) {
    WorkoutSharePreviewState.Loading -> SharePreviewState.Loading
    WorkoutSharePreviewState.Failed -> SharePreviewState.Failed
    is WorkoutSharePreviewState.Ready -> SharePreviewState.Ready(files.size, prCount, files.firstOrNull()?.absolutePath.orEmpty())
}

private fun Set<String>.destinations(): Set<SharePreviewDestination> = buildSet {
    if (WorkoutShareIntent.WHATSAPP in this@destinations) add(SharePreviewDestination.WhatsApp)
    if (WorkoutShareIntent.WHATSAPP_BUSINESS in this@destinations) add(SharePreviewDestination.WhatsAppBusiness)
}

private fun SharePreviewDestination.packageName(): String? = when (this) {
    SharePreviewDestination.WhatsApp -> WorkoutShareIntent.WHATSAPP
    SharePreviewDestination.WhatsAppBusiness -> WorkoutShareIntent.WHATSAPP_BUSINESS
    SharePreviewDestination.Other -> null
}

/** Only visible pages are decoded, keeping very long workouts from retaining every bitmap. */
@Composable
private fun WorkoutSharePage(file: File, index: Int, description: String, onLoaded: () -> Unit, onFailed: () -> Unit) {
    var preview by remember(file) { mutableStateOf<Result<Bitmap>?>(null) }
    LaunchedEffect(file) {
        preview = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                check(bounds.outWidth > 0 && bounds.outHeight > 0)
                var sample = 1
                while (bounds.outWidth / sample > 800 || bounds.outHeight / sample > 4096) sample *= 2
                requireNotNull(BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample }))
            }
        }
    }
    LaunchedEffect(preview) {
        preview?.let { if (it.isSuccess) onLoaded() else onFailed() }
    }
    val bitmap = preview?.getOrNull()
    if (bitmap == null) {
        SharePreviewPagePlaceholder(loading = preview == null)
    } else {
        Image(bitmap.asImageBitmap(), description,
            Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).testTag("share-preview-page-$index"),
            contentScale = ContentScale.Fit)
    }
}
