package com.mooscles.screens

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.text.TextLayoutResult
import com.mooscles.designsystem.components.ExerciseAddSetTag
import com.mooscles.designsystem.components.SetRowCheckTag
import com.mooscles.designsystem.components.SetRowLoadTag
import com.mooscles.designsystem.components.SetRowRepsTag
import com.mooscles.designsystem.components.StepperDecreaseTag
import com.mooscles.designsystem.components.StepperIncreaseTag
import com.mooscles.designsystem.components.TimerCompactTag
import com.mooscles.designsystem.components.TimerDockTag
import com.mooscles.designsystem.components.TimerExpandedTag
import com.mooscles.designsystem.components.TimerMinusTag
import com.mooscles.designsystem.components.TimerPlusTag
import com.mooscles.designsystem.components.TimerSkipTag
import com.mooscles.designsystem.components.WorkoutBarSkipTag
import com.mooscles.screens.workout.ActiveWorkoutTags
import kotlin.math.ceil
import kotlin.test.assertTrue

// The accessibility checks of a screen state, shared by the end-to-end flow (ActiveWorkoutFlowTest)
// and the sweep (WorkoutAccessibilitySweepTest), plus the screen reader's order as Android computes it.

/** The smallest target of anything clickable (spec "Acessibilidade"). */
const val MIN_TARGET_DP = 48f

/** The smallest target of the workout's actions: check, stepper, ±15 s, skip, add set, add exercise, finish. */
const val WORKOUT_TARGET_DP = 56f

private const val TOLERANCE_DP = 0.5f

/** What a screen reader says for [node]: its content description and its text. */
fun spokenLabel(node: SemanticsNode): String {
    val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
    val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
    return "$description $text".trim()
}

/** [node] for a failure message: its tag, label, size and config. */
fun describe(node: SemanticsNode): String =
    "tag=${node.config.getOrNull(SemanticsProperties.TestTag)} label='${spokenLabel(node)}' size=${node.size} config=${node.config}"

/** Asserts that [node] is at least [widthDp] × [heightDp] (0 skips a side). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertAtLeast(node: SemanticsNode, widthDp: Float, heightDp: Float, where: String) {
    val px = density.density
    val width = node.size.width / px
    val height = node.size.height / px
    assertTrue(
        width + TOLERANCE_DP >= widthDp && height + TOLERANCE_DP >= heightDp,
        "$where: ${width}x$height dp, at least ${widthDp}x$heightDp expected: ${describe(node)}",
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.unmerged(matcher: SemanticsMatcher): List<SemanticsNode> =
    onAllNodes(matcher and isPlaced, useUnmergedTree = true).fetchSemanticsNodes()

/**
 * Every placed ([isPlaced]) clickable node (merged tree, every root, a 0-wide one too: a collapsed
 * button is a defect, not a node to skip) has a label and is at least 48 × 48 dp. Fails without any.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertClickablesLabeledAndSized(where: String) {
    val clickable = onAllNodes(hasClickAction() and isPlaced).fetchSemanticsNodes()
    assertTrue(clickable.isNotEmpty(), "$where: no clickable node")
    for (node in clickable) {
        assertTrue(spokenLabel(node).isNotBlank(), "$where: a clickable node has no label: ${describe(node)}")
        assertAtLeast(node, MIN_TARGET_DP, MIN_TARGET_DP, where)
    }
}

/**
 * The workout's actions that are there are 56 dp (spec "Acessibilidade"): the set checks and the
 * stepper buttons 56 × 56, the load and reps cells 56 tall, −15 s, +15 s and skip of the dock, the
 * compact timer, the expanded timer and the workout bar 56 × 56, and "Add set", "Add exercise" and
 * "Finish workout" 56 tall. The header's "Finish" is a 48 dp target by the spec, checked as any clickable.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertWorkoutTargets(where: String) {
    val square = hasTestTag(SetRowCheckTag) or hasTestTag(StepperIncreaseTag) or hasTestTag(StepperDecreaseTag) or
        hasTestTag(WorkoutBarSkipTag) or
        ((hasTestTag(TimerMinusTag) or hasTestTag(TimerPlusTag) or hasTestTag(TimerSkipTag)) and
            hasAnyAncestor(hasTestTag(TimerDockTag) or hasTestTag(TimerCompactTag) or hasTestTag(TimerExpandedTag)))
    for (node in unmerged(square)) assertAtLeast(node, WORKOUT_TARGET_DP, WORKOUT_TARGET_DP, where)
    val tall = hasTestTag(SetRowLoadTag) or hasTestTag(SetRowRepsTag) or hasTestTag(ExerciseAddSetTag) or
        hasTestTag(ActiveWorkoutTags.ADD_EXERCISE) or hasTestTag(ActiveWorkoutTags.FINISH_WORKOUT)
    for (node in unmerged(tall)) assertAtLeast(node, 0f, WORKOUT_TARGET_DP, where)
}

/** A text the screen shows (not an editable field's), inside something clickable or clickable itself. */
private val buttonText = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) and
    SemanticsMatcher.keyNotDefined(SemanticsProperties.EditableText) and
    (hasClickAction() or hasAnyAncestor(hasClickAction()))

/**
 * No text of a button (any text node, unmerged tree, that is clickable or inside something
 * clickable) is cut: a one-line text is shown whole ([assertShownWhole]); a wrapping one needs no
 * more lines than it may have and no more height than it has. Returns how many were measured.
 *
 * A wrapping text's real line count is not in its layout result (`GetTextLayoutResult` lays it out
 * again at the parent's width), so the lines it needs at its own width are bounded from below by
 * its max intrinsic width over that width (with 5% for the spaces a line break drops); the height
 * they need may exceed the text's by a quarter of a line (the platforms round it differently).
 *
 * [except] leaves out nodes whose text is a semantics stand-in, not a laid out text (one set by
 * `clearAndSetSemantics { text = … }` over a component that clears its own text): they have no
 * layout to measure, and the caller checks what is drawn there another way.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.assertButtonTextsWhole(where: String, except: SemanticsMatcher? = null): Int {
    val nodes = unmerged(if (except == null) buttonText else buttonText and !except)
    for (node in nodes) {
        val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
        if (text.isBlank()) continue
        val results = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertTrue(action != null && action(results) && results.isNotEmpty(), "$where: \"$text\" has no text layout result")
        assertTextNotCut(results.first(), "$where: button text")
    }
    return nodes.size
}

/** [assertButtonTextsWhole] for one text: see there. */
fun assertTextNotCut(layout: TextLayoutResult, where: String) {
    val input = layout.layoutInput
    if (!input.softWrap || input.maxLines == 1) {
        assertShownWhole(layout, where)
        return
    }
    val text = input.text.text
    val width = layout.size.width
    val needed = layout.multiParagraph.intrinsics.maxIntrinsicWidth
    assertTrue(width > 0, "$where \"$text\" is 0 px wide")
    val lines = maxOf(1, ceil(needed / (width * WRAP_SLACK)).toInt())
    assertTrue(lines <= input.maxLines, "$where \"$text\" needs at least $lines lines at $width px, may have ${input.maxLines}")
    val lineHeight = layout.multiParagraph.getLineBottom(0) - layout.multiParagraph.getLineTop(0)
    // A quarter of a line of slack: Skia on iOS rounds the laid out height down a pixel from the
    // line's (19 px for a 20 px line), while a line cut by a fixed height loses far more.
    assertTrue(
        lines * lineHeight <= layout.size.height + lineHeight * LINE_SLACK,
        "$where \"$text\" needs at least $lines lines of $lineHeight px at $width px, has ${layout.size.height} px",
    )
}

private const val WRAP_SLACK = 1.05f
private const val LINE_SLACK = 0.25f

// --- The screen reader's order ------------------------------------------------------------------

private fun isTraversalGroup(node: SemanticsNode): Boolean = node.config.getOrElse(SemanticsProperties.IsTraversalGroup) { false }

private fun traversalIndex(node: SemanticsNode): Float = node.config.getOrElse(SemanticsProperties.TraversalIndex) { 0f }

/** A node a screen reader stops on: it merges its children, says something, or can be acted on. */
private fun speaks(node: SemanticsNode): Boolean {
    val config = node.config
    if (SemanticsProperties.HideFromAccessibility in config) return false
    return config.isMergingSemanticsOfDescendants ||
        SemanticsProperties.ContentDescription in config || SemanticsProperties.Text in config ||
        SemanticsProperties.EditableText in config || SemanticsProperties.StateDescription in config ||
        SemanticsProperties.ToggleableState in config || SemanticsActions.OnClick in config
}

/**
 * The order a screen reader reads the main window in (merged tree, from the first root), as
 * Android's accessibility delegate computes it: the nodes of a traversal group
 * ([SemanticsProperties.IsTraversalGroup]) are sorted together, a nested group counting as one
 * node; they go in rows of vertically overlapping bounds, top to bottom, left to right within a
 * row, then by [SemanticsProperties.TraversalIndex]; each group then gives way to its own sorted
 * nodes (kept itself only when it speaks). The iOS simulator runs the same model: it checks the
 * semantics the screens give, not the platform's reader.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.screenReaderOrder(): List<SemanticsNode> {
    val root = onAllNodes(isRoot()).fetchSemanticsNodes().first()
    return sortedGroup(root.children)
}

private fun sortedGroup(nodes: List<SemanticsNode>): List<SemanticsNode> {
    val groups = HashMap<Int, List<SemanticsNode>>()
    val flat = ArrayList<SemanticsNode>()
    fun visit(node: SemanticsNode) {
        val group = isTraversalGroup(node)
        if (group || speaks(node)) flat += node
        if (group) groups[node.id] = sortedGroup(node.children) else node.children.forEach(::visit)
    }
    nodes.forEach(::visit)

    // Rows of vertically overlapping bounds; a row shrinks to the overlap of what it holds.
    class Row(var top: Float, var bottom: Float, val nodes: MutableList<SemanticsNode>)
    val rows = ArrayList<Row>()
    for (node in flat) {
        val bounds = node.boundsInRoot
        val row = rows.firstOrNull { row ->
            bounds.top < bounds.bottom && row.top < row.bottom && maxOf(bounds.top, row.top) < minOf(bounds.bottom, row.bottom)
        }
        if (row == null) {
            rows += Row(bounds.top, bounds.bottom, mutableListOf(node))
        } else {
            row.top = maxOf(row.top, bounds.top)
            row.bottom = minOf(row.bottom, bounds.bottom)
            row.nodes += node
        }
    }
    rows.sortWith(compareBy<Row>({ it.top }, { it.bottom }))
    val ordered = rows.flatMap { row ->
        row.nodes.sortedWith(compareBy({ it.boundsInRoot.left }, { it.boundsInRoot.top }, { it.boundsInRoot.bottom }, { it.boundsInRoot.right }))
    }.sortedBy(::traversalIndex).toMutableList()

    var i = 0
    while (i < ordered.size) {
        val children = groups[ordered[i].id]
        if (children == null) {
            i++
            continue
        }
        if (speaks(ordered[i])) i++ else ordered.removeAt(i)
        ordered.addAll(i, children)
        i += children.size
    }
    return ordered
}

/**
 * The nearest traversal group of [node] at or above it that is inside [container] (or is it), or
 * null when there is none before [container].
 */
fun traversalGroupWithin(node: SemanticsNode, container: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
        if (isTraversalGroup(current)) return current
        if (current.id == container.id) return null
        current = current.parent
    }
    return null
}
