package com.mooscles.designsystem.components

import com.mooscles.designsystem.charts.ChartColors
import com.mooscles.designsystem.illustration.bodyMapPalette
import com.mooscles.designsystem.illustration.figurePalette
import com.mooscles.designsystem.tokens.Contrast
import com.mooscles.designsystem.tokens.MoosclesColors
import com.mooscles.designsystem.tokens.MoosclesElevation
import com.mooscles.designsystem.interaction.reorderLiftLook
import com.mooscles.designsystem.tokens.MoosclesFigureColors
import com.mooscles.designsystem.tokens.Primitives
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The token choices of the critique round, measured in both themes. */
class ComponentColorsTest {
    private val themes = listOf("light" to MoosclesColors.Light, "dark" to MoosclesColors.Dark)

    private fun atLeast(min: Double, fg: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color, what: String) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue(ratio >= min, "$what is $ratio:1, below $min:1")
    }

    @Test
    fun errorSurfaceIsLegibleAndNotGreen() {
        for ((name, colors) in themes) {
            val error = ErrorSurfaceColors.of(colors)
            assertEquals(colors.dangerSubtle, error.surface)
            atLeast(4.5, error.content, error.surface, "$name message and action")
            atLeast(3.0, error.icon, error.surface, "$name alert icon")
            atLeast(3.0, error.outline, error.surface, "$name action outline")
            // The banner's action is MButtonVariant.Secondary, drawn in exactly these two tokens.
            assertEquals(colors.textPrimary, error.content, name)
            assertEquals(colors.borderStrong, error.outline, name)
            for (green in listOf(colors.accentText, colors.accentSolid, colors.accentInverseText)) assertNotEquals(green, error.content, name)
        }
    }

    @Test
    fun errorToastLooksDifferentFromAnInfoToast() {
        for ((name, colors) in themes) {
            val info = toastColors(MToastKind.Info, colors)
            val error = toastColors(MToastKind.Error, colors)
            assertNull(info.icon, name)
            assertNotNull(error.icon, name)
            assertNotEquals(info.surface, error.surface, name)
            atLeast(4.5, info.content, info.surface, "$name info message")
            atLeast(4.5, info.action, info.surface, "$name info action")
            atLeast(4.5, error.content, error.surface, "$name error message")
            atLeast(4.5, error.action, error.surface, "$name error action")
            atLeast(3.0, error.icon!!, error.surface, "$name error icon")
        }
    }

    /**
     * Phase 4A: every color of an exercise figure is a 3:1 graphic on the thumbnail tile and on the
     * pages it sits on. The far limbs (Task 8 fix round 1) are a tone of their own, neither the
     * body's nor the equipment's, so a far leg never merges with a bench.
     */
    @Test
    fun figureColorsStandOutOnTheirBackgrounds() {
        for ((name, colors) in themes) {
            val palette = figurePalette(colors, if (name == "dark") MoosclesFigureColors.Dark else MoosclesFigureColors.Light)
            assertNotEquals(palette.body, palette.far, "$name far limbs are not the near body")
            assertNotEquals(palette.equipment, palette.far, "$name far limbs are not the equipment")
            for ((surface, bg) in listOf("inset" to colors.bgInset, "surface" to colors.bgSurface, "base" to colors.bgBase)) {
                atLeast(3.0, palette.body, bg, "$name figure on $surface")
                atLeast(3.0, palette.far, bg, "$name far limbs on $surface")
                atLeast(3.0, palette.equipment, bg, "$name equipment on $surface")
                atLeast(3.0, palette.highlight, bg, "$name highlight on $surface")
            }
        }
    }

    /**
     * G1 (2026-10-30): the equipment is copper, not `text.tertiary`, and an orange of its own against
     * the body, the far limbs and the highlight, 3:1 on every page and tile the figure is drawn on.
     */
    @Test
    fun figureEquipmentIsCopperAndStandsOut() {
        for ((name, colors) in themes) {
            val figure = if (name == "dark") MoosclesFigureColors.Dark else MoosclesFigureColors.Light
            val palette = figurePalette(colors, figure)
            assertEquals(figure.equipment, palette.equipment, "$name equipment comes from the figure colors")
            assertEquals(if (name == "dark") Primitives.cobre64 else Primitives.cobre47, palette.equipment, name)
            assertNotEquals(colors.textTertiary, palette.equipment, "$name equipment is no longer text.tertiary")
            assertNotEquals(palette.body, palette.equipment, name)
            assertNotEquals(palette.highlight, palette.equipment, name)
            for ((surface, bg) in listOf("inset" to colors.bgInset, "surface" to colors.bgSurface, "base" to colors.bgBase, "raised" to colors.bgRaised)) {
                atLeast(3.0, palette.equipment, bg, "$name copper equipment on $surface")
            }
        }
    }

    /**
     * Phase 4A (spec D6, WCAG 1.4.11): the primary and the secondary regions of the body map stand
     * out 3:1 against the neutral fill and against the pages the map sits on, in both themes. Primary
     * against secondary is not a 3:1 pair (no grey is 3:1 from both bg.inset and accent.solid in the
     * light theme, spike report): they differ in hue, and the legend and the text say which is which.
     */
    @Test
    fun bodyMapPrimaryAndSecondaryStandOut() {
        for ((name, colors) in themes) {
            val palette = bodyMapPalette(colors)
            assertEquals(colors.bgInset, palette.neutral, name)
            assertEquals(colors.accentSolid, palette.primary, name)
            assertEquals(colors.borderStrong, palette.secondary, name)
            assertEquals(colors.borderStrong, palette.outline, name)
            for ((region, color) in listOf("primary" to palette.primary, "secondary" to palette.secondary)) {
                atLeast(3.0, color, palette.neutral, "$name $region on the neutral fill")
                atLeast(3.0, color, colors.bgBase, "$name $region on bg.base")
                atLeast(3.0, color, colors.bgSurface, "$name $region on bg.surface")
            }
            // The outline of the silhouette against the page.
            atLeast(3.0, palette.outline, colors.bgBase, "$name outline on bg.base")
            atLeast(3.0, palette.outline, colors.bgSurface, "$name outline on bg.surface")
            assertNotEquals(palette.primary, palette.secondary, name)
        }
    }

    @Test
    fun skeletonStandsOutMoreThanTheWhiteItReplaced() {
        for ((name, colors) in themes) {
            for ((surface, bg) in listOf("base" to colors.bgBase, "surface" to colors.bgSurface, "raised" to colors.bgRaised)) {
                val now = Contrast.ratio(skeletonFill(colors), bg)
                atLeast(1.2, skeletonFill(colors), bg, "$name skeleton on $surface")
                if (bg != colors.bgRaised) assertTrue(now > Contrast.ratio(colors.bgRaised, bg), "$name skeleton on $surface is no better than bg.raised")
            }
        }
    }

    @Test
    fun pausedRingIsNotTheRunningGreenAndKeepsItsContrast() {
        for ((name, colors) in themes) {
            assertEquals(colors.timerProgress, ringBarColor(TimerPhase.Running, colors), name)
            assertEquals(colors.timerProgress, ringBarColor(TimerPhase.Done, colors), name)
            val paused = ringBarColor(TimerPhase.Paused, colors)
            assertNotEquals(colors.timerProgress, paused, name)
            atLeast(3.0, paused, colors.bgRaised, "$name paused arc on the dock")
            atLeast(3.0, paused, colors.bgBase, "$name paused arc on the page")
            atLeast(3.0, paused, colors.timerTrack, "$name paused arc on the track")
        }
    }

    @Test
    fun emptyValueIsAnEmDash() {
        assertEquals("—", EMPTY_VALUE)
    }

    /**
     * Phase 4B review I2: a lifted item shows `elevation.e3` in both themes. Light: the e3 shadow.
     * Dark (e3 has no shadow there): a `border.strong` outline, a 3:1 line on every surface an item
     * is drawn on or over.
     */
    @Test
    fun reorderLiftShowsInBothThemes() {
        val elevation = MoosclesElevation()
        val light = reorderLiftLook(MoosclesColors.Light, elevation, dark = false)
        assertEquals(elevation.e3.shadowLight, light.shadow)
        assertNull(light.outline)
        val colors = MoosclesColors.Dark
        val dark = reorderLiftLook(colors, elevation, dark = true)
        assertNull(dark.shadow)
        assertEquals(colors.borderStrong, dark.outline)
        for ((what, surface) in listOf("base" to colors.bgBase, "surface" to colors.bgSurface, "overlay" to colors.bgOverlay)) {
            atLeast(3.0, dark.outline!!, surface, "dark lift outline on $what")
        }
    }

    /**
     * Phase 5A (spec D3): what each part of a chart is drawn in. The selection ring is `accent.solid`,
     * already a 3:1 graphic on `bg.surface` and `bg.raised` (pairs 53 and 54 of `ContrastTokensTest`).
     */
    @Test
    fun chartColorsMapToTheirTokens() {
        for ((name, colors) in themes) {
            val chart = ChartColors.of(colors)
            assertEquals(colors.accentSolid, chart.selection, name)
            assertEquals(colors.chartLine, chart.line, name)
            assertEquals(colors.chartPr, chart.record, name)
            assertEquals(colors.chartGrid, chart.grid, name)
            assertEquals(colors.textSecondary, chart.label, name)
        }
    }

    /**
     * Phase 5A (spec D3, "chart.line sobre bg.raised se o card for elevado"): `chart.line` is the
     * `accent.solid` primitive in both themes, so pair 54 covers it on a raised card; the record ring
     * on a raised card is a new pair.
     */
    @Test
    fun chartLineOnRaised() {
        for ((name, colors) in themes) {
            assertEquals(colors.accentSolid, colors.chartLine, "$name: chart.line is accent.solid (pair 54)")
            atLeast(3.0, colors.chartPr, colors.bgRaised, "$name record ring on a raised card")
        }
    }
}
