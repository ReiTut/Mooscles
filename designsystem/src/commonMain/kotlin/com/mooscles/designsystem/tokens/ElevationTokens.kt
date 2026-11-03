package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One elevation level. Light: a shadow of [shadowLight]. Dark: no shadow, the surface tone
 * does the work, plus a [hairlineDark] top edge when it is not null.
 */
data class ElevationSpec(val shadowLight: Dp, val hairlineDark: Color?)

/** Elevation 1 (card), 2 (dock, fixed bars) and 3 (sheet, dialog, toast). Level 0 is `bgBase`. */
data class MoosclesElevation(
    val e1: ElevationSpec = ElevationSpec(1.dp, null),
    val e2: ElevationSpec = ElevationSpec(6.dp, Color(0x0FFFFFFF)),
    val e3: ElevationSpec = ElevationSpec(12.dp, null),
)
