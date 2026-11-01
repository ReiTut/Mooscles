package com.mooscles.designsystem

import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.ceil
import kotlin.test.assertTrue

/**
 * Asserts that a one-line text is shown whole: on one line, as wide as it needs and as tall as
 * its line, so nothing of it is cut, clipped or ellipsized.
 *
 * `hasVisualOverflow` cannot tell this for an `MText`. Its `GetTextLayoutResult` (the String
 * `BasicText` node) lays the paragraph out again at the parent's full max width while `size`
 * stays the real one, so `didOverflowWidth` (`size.width < multiParagraph.width`) is true for any
 * text narrower than its parent: on the emulator "Rest", 77 px in a 723 px column, "overflows".
 * The width the text needs is its max intrinsic width, which does not depend on that relayout.
 */
fun assertShownWhole(layout: TextLayoutResult, where: String = "") {
    val text = layout.layoutInput.text.text
    val needed = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth).toInt()
    assertTrue(
        layout.lineCount == 1 && !layout.multiParagraph.didExceedMaxLines,
        "$where \"$text\" is not on one line: lines=${layout.lineCount}, " +
            "exceededMaxLines=${layout.multiParagraph.didExceedMaxLines}, maxLines=${layout.layoutInput.maxLines}, " +
            "softWrap=${layout.layoutInput.softWrap}, maxWidth=${layout.layoutInput.constraints.maxWidth}, " +
            "size=${layout.size}, needed=$needed, paragraphWidth=${layout.multiParagraph.width}",
    )
    assertTrue(needed <= layout.size.width, "$where \"$text\" needs $needed px, has ${layout.size.width}")
    assertTrue(layout.multiParagraph.height <= layout.size.height + 0.5f, "$where \"$text\" is ${layout.multiParagraph.height} px tall, has ${layout.size.height}")
}
