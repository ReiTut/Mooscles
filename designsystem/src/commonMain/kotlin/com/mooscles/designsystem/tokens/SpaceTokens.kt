package com.mooscles.designsystem.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale (spec "Espaço"): `s4` is 16 dp. All multiples of 4. */
data class MoosclesSpace(
    val s1: Dp = 4.dp,
    val s2: Dp = 8.dp,
    val s3: Dp = 12.dp,
    val s4: Dp = 16.dp,
    val s5: Dp = 20.dp,
    val s6: Dp = 24.dp,
    val s8: Dp = 32.dp,
    val s10: Dp = 40.dp,
    val s12: Dp = 48.dp,
    val s16: Dp = 64.dp,
) {
    fun all(): List<Dp> = listOf(s1, s2, s3, s4, s5, s6, s8, s10, s12, s16)
}

/** Corner radii (spec "Raio"); `full` is the 50% pill. */
data class MoosclesRadius(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val full: Shape = RoundedCornerShape(percent = 50),
)

/** Touch targets: 48 dp everywhere, 56 dp for workout actions, 8 dp between targets. */
data class MoosclesTargets(
    val min: Dp = 48.dp,
    val workout: Dp = 56.dp,
    val gap: Dp = 8.dp,
)

/**
 * Measures the components draw with (plan decision 7 and rulings F15, F36). A control may
 * draw less than its target, never a smaller touch area.
 */
data class MoosclesSizes(
    val icon16: Dp = 16.dp,
    val icon20: Dp = 20.dp,
    val icon24: Dp = 24.dp,
    val icon28: Dp = 28.dp,
    val iconButton48: Dp = 48.dp,
    val iconButton56: Dp = 56.dp,
    val buttonL: Dp = 56.dp,
    val buttonM: Dp = 48.dp,
    val buttonS: Dp = 40.dp,
    val checkDisc: Dp = 44.dp,
    val numericFieldWidth: Dp = 72.dp,
    val numericFieldHeight: Dp = 48.dp,
    /** The text cursor of a field (foundation draws it 2 dp wide): room a numeric field keeps beside its number. */
    val textCursor: Dp = 2.dp,
    val stepperButtonWidth: Dp = 64.dp,
    val stepperButtonHeight: Dp = 56.dp,
    val dockRing: Dp = 48.dp,
    val dockRingStroke: Dp = 4.dp,
    /** Phase 3: the rest ring of the in-progress workout bar (spec D8, "o anel de 32 dp"). */
    val ringBar: Dp = 32.dp,
    val ringBarStroke: Dp = 3.dp,
    val expandedRing: Dp = 280.dp,
    val expandedRingStroke: Dp = 12.dp,
    val tickLength: Dp = 8.dp,
    val tickWidth: Dp = 2.dp,
    val tickCount: Int = 60,
    val timerDisc: Dp = 80.dp,
    val media: Dp = 64.dp,
    val sheetHandleWidth: Dp = 32.dp,
    val sheetHandleHeight: Dp = 4.dp,
    val sheetMaxFraction: Float = 0.9f,
    val rowMin: Dp = 56.dp,
    /** Phase 3: the lowest the tab bar gets (spec D7, "altura mínima de 64 dp"). */
    val barMin: Dp = 64.dp,
    val border: Dp = 1.dp,
    val borderError: Dp = 2.dp,
    /** Ring of a selected radio. The spec is silent: a controller choice, 2 dp like the focus ring. */
    val borderSelected: Dp = 2.dp,
    val focusRingWidth: Dp = 2.dp,
    val focusRingOffset: Dp = 2.dp,
    /** Share of the track the indeterminate progress bar fills while it moves (or rests). */
    val progressIndeterminateFraction: Float = 0.3f,
    val progressBarHeight: Dp = 8.dp,
    /** Lowest opacity of the skeleton pulse. */
    val skeletonMinAlpha: Float = 0.5f,
    /** Widest a dialog card gets; narrower screens keep `space.6` on each side instead. */
    val dialogMaxWidth: Dp = 360.dp,
    /** Phase 3: the widest the content of a screen gets, centered (spec "Tela do treino ativo", 600 dp). */
    val contentMaxWidth: Dp = 600.dp,
    /** Phase 4A: the media of a library row (spec "Aba Biblioteca", "mídia de 56 dp"). */
    val mediaList: Dp = 56.dp,
    /** Phase 4A: the widest the square media frame of the exercise detail gets (spec "até 360 dp"). */
    val mediaDetailMax: Dp = 360.dp,
    /** Phase 4A: the drawn height of a read-only chip (plan decision 12); it grows with the text. */
    val infoChipHeight: Dp = 32.dp,
    /** Phase 4B (spec D15): a drag held this close to an edge of the list scrolls it. */
    val reorderEdgeZone: Dp = 64.dp,
    /** Phase 4B: how far that scroll goes each frame. */
    val reorderScrollStep: Dp = 12.dp,
    /** Phase 4B (plan Task 11, 2026-10-30): each thumbnail of a routine card's strip, the same 56 dp as a library row. */
    val mediaStrip: Dp = mediaList,
    /** Phase 5A (spec D3): the height of a line or bar chart at a font scale of 1; it grows with the scale. */
    val chartHeight: Dp = 200.dp,
    /** Phase 5A (spec "Fonte grande"): the most a chart grows with the font scale, 1.5 times [chartHeight]. */
    val chartMaxScale: Float = 1.5f,
    /** Phase 5A (spec D3): the sparkline of the exercise detail's history card. */
    val sparklineHeight: Dp = 40.dp,
    /** Phase 5A: the line of a chart and of a sparkline, and the rings' stroke. */
    val chartStroke: Dp = 2.dp,
    /** Phase 5A: the radius of a chart point. */
    val chartPoint: Dp = 3.dp,
    /** Phase 5A: a line chart draws its points as dots while they are, on average, at least this many radii apart. */
    val chartDotSpacing: Float = 4f,
    /** Phase 5A: the radius of the ring around a record point. */
    val chartRecordRing: Dp = 6.dp,
    /** Phase 5A: the radius of the ring around the selected point or over the selected bar. */
    val chartSelectionRing: Dp = 8.dp,
    /** Phase 5A: the space between two bars. */
    val chartBarGap: Dp = 4.dp,
)
