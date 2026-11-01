package com.mooscles.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.text.TextLayoutResult
import kotlin.math.ceil
import kotlin.test.assertTrue

// A copy of the design system's TextFit.kt (KMP has no test fixtures shared across modules),
// plus assertNumbersWhole.

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

// Units and the separators a number text may hold: "82.5 kg × 8", "1:02:18", "−15 s", "1 h 12 min".
private val UNIT_WORDS = Regex("\\b(kg|lbs?|min|h|s)\\b")
private val NUMBER_CHARS = Regex("[0-9\\s\\u00A0\\u202F.,:×+\\-−–/]+")

/**
 * The node and every layout above it are placed: what the screen shows. A lazy list keeps some
 * items composed but unplaced (out of its viewport, kept for reuse); their layout is not redone
 * when the font scale changes, so measuring them would measure a stale layout.
 */
val isPlaced: SemanticsMatcher = SemanticsMatcher("is placed") { node ->
    var info: androidx.compose.ui.layout.LayoutInfo? = node.layoutInfo
    var placed = true
    while (info != null && placed) {
        placed = info.isPlaced
        info = info.parentInfo
    }
    placed
}

/** True when [text] holds a digit and nothing but digits, units, `×`, `:` and separators. */
internal fun isNumberText(text: String): Boolean =
    text.any { it.isDigit() } && NUMBER_CHARS.matches(UNIT_WORDS.replace(text, " "))

/**
 * Applies [assertShownWhole] to every text node (unmerged tree) whose whole text is a number,
 * with or without a unit, such as "82.5", "82.5 kg × 8", "1:02:18" or "1 h 12 min", that is
 * placed ([isPlaced]). Fails when
 * fewer than [atLeast] such nodes exist, so a screen that lost its numbers cannot pass in the void.
 */
fun SemanticsNodeInteractionsProvider.assertNumbersWhole(atLeast: Int = 1) {
    val numbers = SemanticsMatcher("text is only a number") { node ->
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        text != null && isNumberText(text)
    }
    val nodes = onAllNodes(numbers and isPlaced, useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(nodes.size >= atLeast, "${nodes.size} number texts found, at least $atLeast expected")
    for (node in nodes) {
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "\"$text\" has no text layout result")
        assertShownWhole(results.first(), "number text")
    }
}
