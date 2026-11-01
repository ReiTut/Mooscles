package com.mooscles.designsystem.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Phase 4A: the strokes of the exercise figures and the body map. The figure values are fractions
 * of the figure's 100-unit box, so a figure reads the same at the 64 dp of a list and at the size
 * of the detail. [figureStroke] is the limbs (round caps: capsules); [figureTrunkStroke] the
 * trunk, half as heavy again, so a standing figure reads as a body and not a pole at 64 dp;
 * [figureHeadStroke] the head ring and the equipment; [figureGap] the band cleared on each side of
 * the head and of an arm in front, so they read over the trunk or a plate; [figureMargin] how far
 * inside the box a pose keeps its joints and equipment (at least half a [figureTrunkStroke], so no
 * stroke is cut). [bodyMapStroke] is the body map's outline, the 1 dp hairline of the spike. The
 * limb, head and margin values held through the pilot poses (Task 8); the trunk and the gap came
 * from the pilot sheet's critique (Task 9, on the emulator at 64 dp and at 360 dp). All pinned in
 * `IllustrationTokensTest`.
 */
@Immutable
data class MoosclesIllustration(
    val figureStroke: Float = 0.06f,
    val figureTrunkStroke: Float = 0.09f,
    val figureHeadStroke: Float = 0.04f,
    val figureGap: Float = 0.012f,
    val figureMargin: Float = 0.05f,
    val bodyMapStroke: Dp = 1.dp,
)

/**
 * Phase 4A: the theme colors of an exercise figure beyond the semantic tokens. [farLimb] is the
 * far arm and leg (spec "Esqueleto": the far side "num token mais apagado, escolhido no S1"): a
 * body tone, fainter than the near limbs (`text.secondary`) and apart from the equipment
 * ([equipment]), so a far leg on a bench does not read as part of the bench. Iron 57 in both
 * themes: lighter than the equipment in the light theme, darker in the dark one, and 3:1 on the
 * tile and the pages in both (`ComponentColorsTest`).
 *
 * G1 (2026-10-30): [equipment] is copper, an orange, so the equipment (barbell, plates, machines,
 * benches, cables) stands out from the grey body and the green muscle. Copper 47 in the light theme
 * and copper 64 in the dark one. Measured on the four page and tile backgrounds, the light value is
 * at least 4.09:1 and the dark value at least 5.8:1, and under deuteranopia simulation it keeps a
 * delta E of about 38 to 39 from the green highlight. Brass is out because it already means a
 * personal record, and ember because it means danger.
 */
@Immutable
data class MoosclesFigureColors(val farLimb: Color, val equipment: Color) {
    companion object {
        val Light = MoosclesFigureColors(farLimb = Primitives.ferro57, equipment = Primitives.cobre47)
        val Dark = MoosclesFigureColors(farLimb = Primitives.ferro57, equipment = Primitives.cobre64)
    }
}
