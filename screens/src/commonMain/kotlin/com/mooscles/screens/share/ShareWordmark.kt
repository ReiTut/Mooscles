package com.mooscles.screens.share

import androidx.compose.ui.graphics.ImageBitmap
import com.mooscles.screens.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.concurrent.Volatile

/** The supplied artwork, decoded once per process, outside composition and drawing. */
@Volatile private var wordmark: ImageBitmap? = null
private val wordmarkMutex = Mutex()

@OptIn(ExperimentalResourceApi::class)
suspend fun loadShareWordmark(): ImageBitmap = wordmark ?: withContext(Dispatchers.Default) {
    wordmarkMutex.withLock {
        wordmark ?: Res.readBytes("files/share_wordmark.png").decodeToImageBitmap().also { wordmark = it }
    }
}
