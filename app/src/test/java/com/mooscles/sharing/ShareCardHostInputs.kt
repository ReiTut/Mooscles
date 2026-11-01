package com.mooscles.sharing

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.screens.share.ShareCardPainter
import com.mooscles.screens.share.ShareCardStyles
import com.mooscles.screens.share.ShareCardTexts
import com.mooscles.screens.share.rememberShareCardStyles
import com.mooscles.screens.share.rememberShareCardTexts

/** What the share preview resolves in its composition: the real texts (English), fonts and a measurer. */
internal class ShareCardHostInputs(val texts: ShareCardTexts, val styles: ShareCardStyles, val measurer: TextMeasurer) {
    val painter: ShareCardPainter get() = ShareCardPainter(styles)
}

internal fun ComposeContentTestRule.captureShareCardInputs(): ShareCardHostInputs {
    var inputs: ShareCardHostInputs? = null
    setContent { inputs = ShareCardHostInputs(rememberShareCardTexts(), rememberShareCardStyles(), rememberTextMeasurer()) }
    waitForIdle()
    return checkNotNull(inputs)
}
