package com.mooscles.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.interaction.mFocusRing
import com.mooscles.designsystem.interaction.mPressScale
import com.mooscles.designsystem.theme.MTheme

const val MTabBarTag = "tab-bar"
const val TabPillTag = "tab-pill"

/** The tag of a tab's icon, by the icon's name ("DumbbellFilled"), so tests can tell which variant is drawn. */
internal fun tabIconTag(iconName: String): String = "tab-icon-$iconName"

/** The tag of the tab with [key]: `tab-<key>`. The app's keys are its routes ("routines", "library", ...). */
fun tabTag(key: String): String = "tab-$key"

/** Above this font scale only the selected tab shows its label (spec D7). */
private const val LABEL_ONLY_SELECTED_FONT_SCALE = 1.5f

/** One destination of the tab bar. [selectedIcon] is the filled variant shown while it is selected. */
data class MTab(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The bottom tab bar (spec D7): `bg.raised` with `elevation.2`, at least `sizes.barMin` tall, each
 * tab a target of 48 dp or more. The selected tab shows [MTab.selectedIcon] in `accent.text` on an
 * `accent.subtle` pill, so the state is told by shape as well as color; the others are in
 * `text.secondary`. A label never wraps: when one of them does not fit its column (measured) or the
 * font scale is above 1.5, only the selected tab shows its label, and it gets the width it needs
 * (the others keep 48 dp at least). Every tab is named by its label either way.
 *
 * Semantics: the row is a selectable group and a one-row collection of [tabs]; each tab is a
 * `Role.Tab` with its selected state and position ("Train, tab, 1 of 4"). Tapping the selected tab
 * calls [onReselect] (back to the root of that tab), any other calls [onSelect]. No haptic.
 */
@Composable
fun MTabBar(
    tabs: List<MTab>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    onReselect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MTheme.type.labelMd
    val tabPadding = MTheme.space.s1
    val minTarget = MTheme.targets.min
    val labelWidths = tabs.map { tab ->
        measurer.measure(tab.label, labelStyle, softWrap = false, maxLines = 1, layoutDirection = direction, density = density).size.width
    }
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }
    val count = tabs.size

    BoxWithConstraints(modifier.testTag(MTabBarTag).fixedBarSurface()) {
        val bounded = constraints.hasBoundedWidth && count > 0
        val padPx = with(density) { tabPadding.roundToPx() } * 2
        val columnPx = if (bounded) constraints.maxWidth / count else Int.MAX_VALUE
        val labelsFit = labelWidths.all { it + padPx <= columnPx }
        val compact = density.fontScale > LABEL_ONLY_SELECTED_FONT_SCALE || !labelsFit
        // Compact: the selected tab is as wide as its label needs (at least an even share), and
        // every other tab keeps a 48 dp target.
        val selectedWidth = if (compact && bounded && selectedIndex >= 0) {
            val othersPx = with(density) { minTarget.roundToPx() } * (count - 1)
            val wanted = maxOf(labelWidths[selectedIndex] + padPx, columnPx)
            with(density) { wanted.coerceAtMost(constraints.maxWidth - othersPx).coerceAtLeast(0).toDp() }
        } else {
            null
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = MTheme.sizes.barMin)
                .height(IntrinsicSize.Min)
                .selectableGroup()
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = count) },
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val width = if (selected && selectedWidth != null) Modifier.width(selectedWidth) else Modifier.weight(1f)
                TabItem(
                    tab = tab,
                    index = index,
                    selected = selected,
                    showLabel = !compact || selected,
                    alignTop = compact,
                    enabled = enabled,
                    onClick = { if (selected) onReselect(tab.key) else onSelect(tab.key) },
                    modifier = width,
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: MTab,
    index: Int,
    selected: Boolean,
    showLabel: Boolean,
    alignTop: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = MTheme.colors
    val source = remember { MutableInteractionSource() }
    val content = when {
        !enabled -> colors.textDisabled
        selected -> colors.accentText
        else -> colors.textSecondary
    }
    val pillShape = MTheme.radius.full
    val gap = MTheme.space.s1
    Column(
        modifier
            .fillMaxHeight()
            .testTag(tabTag(tab.key))
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            // The label names the tab whether it is drawn or not; the drawn text is not read twice.
            .clearAndSetSemantics {
                contentDescription = tab.label
                collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1)
            }
            .padding(MTheme.space.s1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap, if (alignTop) Alignment.Top else Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .mPressScale(source, enabled)
                .mFocusRing(pillShape, source)
                .then(if (selected) Modifier.testTag(TabPillTag).background(colors.accentSubtle, pillShape) else Modifier)
                .padding(horizontal = MTheme.space.s5, vertical = MTheme.space.s1),
            contentAlignment = Alignment.Center,
        ) {
            val icon = if (selected) tab.selectedIcon else tab.icon
            MIcon(icon, contentDescription = null, modifier = Modifier.testTag(tabIconTag(icon.name)), size = MTheme.sizes.icon24, tint = content)
        }
        if (showLabel) {
            MText(tab.label, MTheme.type.labelMd, color = content, softWrap = false, maxLines = 1)
        }
    }
}

/**
 * The surface of a fixed bar (`elevation.2`): `bg.raised` in [shape] with, in light, a shadow of
 * `elevation.2` and, in dark, its hairline along the top edge (the surface tone does the rest).
 */
@Composable
internal fun Modifier.fixedBarSurface(shape: Shape = RectangleShape): Modifier {
    val colors = MTheme.colors
    val spec = MTheme.elevation.e2
    val dark = MTheme.isDark
    val edge = MTheme.sizes.border
    val hairline = spec.hairlineDark ?: Color.Transparent
    val shadowed = if (dark) this else shadow(spec.shadowLight, shape, clip = false, spotColor = colors.shadow)
    val surface = shadowed.background(colors.bgRaised, shape)
    if (!dark || hairline == Color.Transparent) return surface
    return surface.drawWithContent {
        drawContent()
        val stroke = edge.toPx()
        val outline = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
        clipPath(outline) {
            drawLine(hairline, Offset(0f, stroke / 2f), Offset(size.width, stroke / 2f), stroke)
        }
    }
}

/** The top-rounded shape of a bar that sits on the tab bar (`radius.lg`). */
@Composable
internal fun topRoundedShape(): Shape = RoundedCornerShape(topStart = MTheme.radius.lg, topEnd = MTheme.radius.lg)
