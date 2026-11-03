package com.mooscles.designsystem.tokens

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TypographyTokensTest {
    private class Row(val key: String, val face: TypeFace, val weight: Int, val size: Float, val line: Float, val spacing: Float)

    /** The spec's typography table, pasted (plus the two variants its rows mention). */
    private val table = listOf(
        Row("num.display", TypeFace.Number, 600, 88f, 88f, -0.01f),
        Row("num.lg", TypeFace.Number, 600, 44f, 46f, 0f),
        Row("num.md", TypeFace.Number, 600, 28f, 32f, 0f),
        Row("num.dock", TypeFace.Number, 600, 32f, 34f, 0f),
        Row("num.sm", TypeFace.Number, 500, 20f, 24f, 0f),
        Row("num.set.index", TypeFace.Number, 500, 22f, 24f, 0f),
        Row("title.lg", TypeFace.Inter, 700, 24f, 30f, -0.01f),
        Row("title.md", TypeFace.Inter, 600, 18f, 24f, 0f),
        Row("title.sm", TypeFace.Inter, 600, 16f, 22f, 0f),
        Row("body.lg", TypeFace.Inter, 400, 16f, 24f, 0f),
        Row("body.md", TypeFace.Inter, 400, 14f, 20f, 0f),
        Row("label.lg", TypeFace.Inter, 600, 16f, 20f, 0f),
        Row("label.md", TypeFace.Inter, 600, 14f, 18f, 0f),
        Row("label.caps", TypeFace.Inter, 600, 12f, 16f, 0.06f),
        Row("caption", TypeFace.Inter, 400, 13f, 18f, 0f),
    )

    private val styles = buildMoosclesTypography(FontFamily.Default, FontFamily.Monospace).byKey()

    @Test
    fun matchesTheSpecTable() {
        assertEquals(table.map { it.key }.toSet(), TypeSpec.keys)
        assertEquals(table.map { it.key }.toSet(), styles.keys)
        for (row in table) {
            val spec = TypeSpec.getValue(row.key)
            assertEquals(row.face, spec.family, row.key)
            assertEquals(row.weight, spec.weight, row.key)
            assertEquals(row.size, spec.sizeSp, row.key)
            assertEquals(row.line, spec.lineHeightSp, row.key)
            assertEquals(row.spacing, spec.letterSpacingEm, row.key)
            val style = styles.getValue(row.key)
            assertEquals(FontWeight(row.weight), style.fontWeight, row.key)
            assertEquals(row.size.sp, style.fontSize, row.key)
            assertEquals(row.line.sp, style.lineHeight, row.key)
            assertEquals(row.spacing.em, style.letterSpacing, row.key)
            val expectedFamily = if (row.face == TypeFace.Number) FontFamily.Monospace else FontFamily.Default
            assertEquals(expectedFamily, style.fontFamily, row.key)
        }
        assertEquals(32f to 34f, TypeSpec.getValue("num.dock").let { it.sizeSp to it.lineHeightSp })
        assertEquals(22f to 24f, TypeSpec.getValue("num.set.index").let { it.sizeSp to it.lineHeightSp })
    }

    @Test
    fun nothingBelowTwelveSpOrWeightFourHundred() {
        for ((key, style) in styles) {
            assertTrue(style.fontSize.value >= 12f, "$key size")
            assertTrue(style.fontWeight!!.weight >= 400, "$key weight")
        }
    }

    @Test
    fun numberStylesAreTabular() {
        for ((key, style) in styles) {
            if (key.startsWith("num.")) assertEquals("tnum", style.fontFeatureSettings, key)
            else assertEquals(null, style.fontFeatureSettings, key)
        }
        assertEquals("tnum", styles.getValue("body.md").tabular().fontFeatureSettings)
    }
}
