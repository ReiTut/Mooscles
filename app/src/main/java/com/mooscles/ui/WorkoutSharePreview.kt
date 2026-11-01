package com.mooscles.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mooscles.MoosclesApplication
import com.mooscles.R
import com.mooscles.designsystem.format.currentNumberLocale
import com.mooscles.screens.share.ShareCardDensity
import com.mooscles.screens.share.ShareCardPainter
import com.mooscles.screens.share.ShareCards
import com.mooscles.screens.share.rememberShareCardStyles
import com.mooscles.screens.share.rememberShareCardTexts
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
    data class Ready(val files: List<File>) : WorkoutSharePreviewState
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
        state = try {
            withContext(Dispatchers.IO) {
                val app = context.applicationContext as MoosclesApplication
                val card = ShareCards.from(app.repository.snapshot(), workoutId, pounds, locale,
                    TimeZone.currentSystemDefault(), texts)
                // A measurer of its own: a retry can start while a cancelled export still draws.
                val measurer = TextMeasurer(fonts, ShareCardDensity, LayoutDirection.Ltr)
                val files = WorkoutCardRenderer().export(context.applicationContext, card, ShareCardPainter(styles), measurer)
                check(files.isNotEmpty())
                WorkoutSharePreviewState.Ready(files)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            WorkoutSharePreviewState.Failed
        }
    }
    val chooserTitle = stringResource(R.string.share_workout)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        WorkoutSharePreviewContent(state, available, shareFailed,
            onRetry = { attempt++ }, onDismiss = onDismiss,
            onShare = { packageName ->
                val files = (state as? WorkoutSharePreviewState.Ready)?.files.orEmpty()
                shareFailed = try {
                    !WorkoutShareIntent.launch(context, files, chooserTitle, packageName)
                } catch (_: Exception) {
                    true
                }
            })
    }
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
    var previewVisible by remember(state) { mutableStateOf(false) }
    var previewFailed by remember(state) { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.share_preview_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("share-preview-close")) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.share_close))
                }
            }
            when (state) {
                WorkoutSharePreviewState.Loading -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(Modifier.testTag("share-preview-loading"))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.share_preview_loading))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_cancel)) }
                }
                WorkoutSharePreviewState.Failed -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.share_preview_failed), color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRetry, modifier = Modifier.testTag("share-preview-retry")) { Text(stringResource(R.string.share_retry)) }
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_cancel)) }
                }
                is WorkoutSharePreviewState.Ready -> {
                    if (state.files.size > 1) Text(pluralStringResource(R.plurals.share_preview_pages, state.files.size, state.files.size), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("share-preview-pages"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(state.files, key = { _, file -> file.absolutePath }) { index, file ->
                            WorkoutSharePage(file, index, state.files.size, onLoaded = { if (index == 0) previewVisible = true }, onFailed = { previewFailed = true })
                        }
                    }
                    if (previewFailed) {
                        Text(stringResource(R.string.share_preview_page_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, modifier = Modifier.testTag("share-preview-retry")) { Text(stringResource(R.string.share_retry)) }
                    }
                    if (shareFailed) Text(stringResource(R.string.share_open_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.share_preview_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val preferred = WorkoutShareIntent.preferredWhatsApp(availablePackages)
                    if (preferred != null) Button(onClick = { onShare(preferred) }, enabled = previewVisible && !previewFailed, modifier = Modifier.fillMaxWidth().testTag("share-whatsapp")) {
                        Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (preferred == WorkoutShareIntent.WHATSAPP_BUSINESS) R.string.share_whatsapp_business else R.string.share_whatsapp))
                    }
                    if (WorkoutShareIntent.WHATSAPP in availablePackages && WorkoutShareIntent.WHATSAPP_BUSINESS in availablePackages) {
                        OutlinedButton(onClick = { onShare(WorkoutShareIntent.WHATSAPP_BUSINESS) }, enabled = previewVisible && !previewFailed, modifier = Modifier.fillMaxWidth().testTag("share-whatsapp-business")) {
                            Text(stringResource(R.string.share_whatsapp_business))
                        }
                    }
                    OutlinedButton(onClick = { onShare(null) }, enabled = previewVisible && !previewFailed, modifier = Modifier.fillMaxWidth().testTag("share-other-apps")) {
                        Text(stringResource(if (preferred == null) R.string.share_choose_app else R.string.share_other_apps))
                    }
                }
            }
        }
    }
}

/** Only visible pages are decoded, keeping very long workouts from retaining every bitmap. */
@Composable
private fun WorkoutSharePage(file: File, index: Int, count: Int, onLoaded: () -> Unit, onFailed: () -> Unit) {
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
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            if (preview == null) CircularProgressIndicator()
        }
    } else {
        Image(bitmap.asImageBitmap(), stringResource(R.string.share_preview_image, index + 1, count),
            Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height).testTag("share-preview-page-$index"),
            contentScale = ContentScale.Fit)
    }
}
