package com.mooscles.designsystem.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A 24 x 24 icon; add its strokes with [strokePath]. */
internal fun mIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

/** The path of every outline icon: a black, unfilled stroke of 2 with round caps and joins. */
internal fun ImageVector.Builder.strokePath(pathBuilder: PathBuilder.() -> Unit): ImageVector.Builder =
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathBuilder,
    )

/**
 * A solid black area with no stroke, drawn at the size the stroked outline covers. Only the
 * `*Filled` variants (the selected tab icons) have these; [evenOdd] cuts holes, such as the
 * clock hands of `HistoryFilled`.
 */
internal fun ImageVector.Builder.fillPath(evenOdd: Boolean = false, pathBuilder: PathBuilder.() -> Unit): ImageVector.Builder =
    path(
        fill = SolidColor(Color.Black),
        stroke = null,
        strokeLineWidth = 0f,
        pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        pathBuilder = pathBuilder,
    )
