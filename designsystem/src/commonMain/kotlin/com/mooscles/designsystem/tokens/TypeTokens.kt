package com.mooscles.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The two faces of the type scale: Inter for text, Barlow Condensed for numbers. */
enum class TypeFace { Inter, Number }

/** One row of the spec's typography table. */
data class TypeSpecRow(
    val family: TypeFace,
    val weight: Int,
    val sizeSp: Float,
    val lineHeightSp: Float,
    val letterSpacingEm: Float,
    val tabular: Boolean,
)

/**
 * The pure typography table (spec "Tipografia"), keyed by the spec's token names. The styles
 * of [MoosclesTypography] are built from it. `num.dock` and `num.set.index` are the two
 * variants the `num.md` and `num.sm` rows mention (timer in the dock 32/34; set number 22/24).
 */
val TypeSpec: Map<String, TypeSpecRow> = mapOf(
    "num.display" to TypeSpecRow(TypeFace.Number, 600, 88f, 88f, -0.01f, true),
    "num.lg" to TypeSpecRow(TypeFace.Number, 600, 44f, 46f, 0f, true),
    "num.md" to TypeSpecRow(TypeFace.Number, 600, 28f, 32f, 0f, true),
    "num.dock" to TypeSpecRow(TypeFace.Number, 600, 32f, 34f, 0f, true),
    "num.sm" to TypeSpecRow(TypeFace.Number, 500, 20f, 24f, 0f, true),
    "num.set.index" to TypeSpecRow(TypeFace.Number, 500, 22f, 24f, 0f, true),
    "title.lg" to TypeSpecRow(TypeFace.Inter, 700, 24f, 30f, -0.01f, false),
    "title.md" to TypeSpecRow(TypeFace.Inter, 600, 18f, 24f, 0f, false),
    "title.sm" to TypeSpecRow(TypeFace.Inter, 600, 16f, 22f, 0f, false),
    "body.lg" to TypeSpecRow(TypeFace.Inter, 400, 16f, 24f, 0f, false),
    "body.md" to TypeSpecRow(TypeFace.Inter, 400, 14f, 20f, 0f, false),
    "label.lg" to TypeSpecRow(TypeFace.Inter, 600, 16f, 20f, 0f, false),
    "label.md" to TypeSpecRow(TypeFace.Inter, 600, 14f, 18f, 0f, false),
    "label.caps" to TypeSpecRow(TypeFace.Inter, 600, 12f, 16f, 0.06f, false),
    "caption" to TypeSpecRow(TypeFace.Inter, 400, 13f, 18f, 0f, false),
)

private const val TNUM = "tnum"

/** Tabular figures on an Inter style (dates, durations, counts). Number styles already have it. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TNUM)

/** The Giz & Ferro type scale. Every `num*` style is tabular. */
data class MoosclesTypography(
    val numDisplay: TextStyle,
    val numLg: TextStyle,
    val numMd: TextStyle,
    val numDock: TextStyle,
    val numSm: TextStyle,
    val numSetIndex: TextStyle,
    val titleLg: TextStyle,
    val titleMd: TextStyle,
    val titleSm: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val labelCaps: TextStyle,
    val caption: TextStyle,
) {
    /** The styles keyed like [TypeSpec], for tests and the gallery. */
    fun byKey(): Map<String, TextStyle> = mapOf(
        "num.display" to numDisplay, "num.lg" to numLg, "num.md" to numMd, "num.dock" to numDock,
        "num.sm" to numSm, "num.set.index" to numSetIndex, "title.lg" to titleLg,
        "title.md" to titleMd, "title.sm" to titleSm, "body.lg" to bodyLg, "body.md" to bodyMd,
        "label.lg" to labelLg, "label.md" to labelMd, "label.caps" to labelCaps, "caption" to caption,
    )
}

/** Builds the scale from [TypeSpec] with the given families (pure: no resources are read). */
fun buildMoosclesTypography(inter: FontFamily, number: FontFamily): MoosclesTypography {
    fun style(key: String): TextStyle {
        val row = TypeSpec.getValue(key)
        return TextStyle(
            fontFamily = if (row.family == TypeFace.Number) number else inter,
            fontWeight = FontWeight(row.weight),
            fontSize = row.sizeSp.sp,
            lineHeight = row.lineHeightSp.sp,
            letterSpacing = row.letterSpacingEm.em,
            fontFeatureSettings = if (row.tabular) TNUM else null,
        )
    }
    return MoosclesTypography(
        numDisplay = style("num.display"), numLg = style("num.lg"), numMd = style("num.md"),
        numDock = style("num.dock"), numSm = style("num.sm"), numSetIndex = style("num.set.index"),
        titleLg = style("title.lg"), titleMd = style("title.md"), titleSm = style("title.sm"),
        bodyLg = style("body.lg"), bodyMd = style("body.md"), labelLg = style("label.lg"),
        labelMd = style("label.md"), labelCaps = style("label.caps"), caption = style("caption"),
    )
}

@Composable
fun rememberMoosclesTypography(): MoosclesTypography {
    val inter = interFamily()
    val number = numberFamily()
    return remember(inter, number) { buildMoosclesTypography(inter, number) }
}
