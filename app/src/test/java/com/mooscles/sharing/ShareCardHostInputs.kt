package com.mooscles.sharing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.ImageBitmap
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.screens.share.ShareCardPainter
import com.mooscles.screens.share.ShareCardStyles
import com.mooscles.screens.share.ShareCardTexts
import com.mooscles.screens.share.rememberShareCardStyles
import com.mooscles.screens.share.rememberShareCardTexts
import com.mooscles.screens.share.loadShareWordmark
import kotlinx.coroutines.runBlocking

/** What the share preview resolves in its composition: the real texts (English), fonts and a measurer. */
internal class ShareCardHostInputs(val texts: ShareCardTexts, val styles: ShareCardStyles, val measurer: TextMeasurer,
    val wordmark: ImageBitmap) {
    val painter: ShareCardPainter get() = ShareCardPainter(styles, wordmark)
}

internal fun ComposeContentTestRule.captureShareCardInputs(language: String? = null): ShareCardHostInputs {
    val wordmark = runBlocking { loadShareWordmark() }
    var inputs: ShareCardHostInputs? = null
    setContent {
        ProvideAppLocale(language) {
            inputs = ShareCardHostInputs(rememberShareCardTexts(), rememberShareCardStyles(), rememberTextMeasurer(), wordmark)
        }
    }
    waitForIdle()
    return checkNotNull(inputs)
}
