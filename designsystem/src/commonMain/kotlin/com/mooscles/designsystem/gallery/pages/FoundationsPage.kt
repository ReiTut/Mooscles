package com.mooscles.designsystem.gallery.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_sample_text
import com.mooscles.designsystem.resources.gallery_section_colors
import com.mooscles.designsystem.resources.gallery_section_elevation
import com.mooscles.designsystem.resources.gallery_section_motion
import com.mooscles.designsystem.resources.gallery_section_radius
import com.mooscles.designsystem.resources.gallery_section_space
import com.mooscles.designsystem.resources.gallery_section_type
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.tokens.Contrast
import com.mooscles.designsystem.tokens.ElevationSpec
import com.mooscles.designsystem.tokens.tabular
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource

/** Colors with the measured ratio of each pair, type, space, radius, elevation and motion. */
@Composable
fun FoundationsPage() {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(MTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(MTheme.space.s6),
    ) {
        Section(stringResource(Res.string.gallery_section_colors)) { Colors() }
        Section(stringResource(Res.string.gallery_section_type)) { Typography() }
        Section(stringResource(Res.string.gallery_section_space)) { Spaces() }
        Section(stringResource(Res.string.gallery_section_radius)) { Radii() }
        Section(stringResource(Res.string.gallery_section_elevation)) { Elevations() }
        Section(stringResource(Res.string.gallery_section_motion)) { Motion() }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        PageHeader(title)
        content()
    }
}

@Composable
private fun Caption(text: String) {
    BasicText(text, style = MTheme.type.caption.copy(color = MTheme.colors.textSecondary).tabular())
}

private class ColorPair(val name: String, val fg: Color, val bg: Color)

@Composable
private fun Colors() {
    val c = MTheme.colors
    val pairs = listOf(
        ColorPair("textPrimary / bgBase", c.textPrimary, c.bgBase),
        ColorPair("textSecondary / bgSurface", c.textSecondary, c.bgSurface),
        ColorPair("textTertiary / bgInset", c.textTertiary, c.bgInset),
        ColorPair("textInverse / bgInverse", c.textInverse, c.bgInverse),
        ColorPair("accentText / bgSurface", c.accentText, c.bgSurface),
        ColorPair("accentOn / accentSolid", c.accentOn, c.accentSolid),
        ColorPair("accentText / accentSubtle", c.accentText, c.accentSubtle),
        ColorPair("prOn / prSolid", c.prOn, c.prSolid),
        ColorPair("prText / prSubtle", c.prText, c.prSubtle),
        ColorPair("dangerOn / dangerSolid", c.dangerOn, c.dangerSolid),
        ColorPair("dangerText / dangerSubtle", c.dangerText, c.dangerSubtle),
        ColorPair("borderStrong / bgSurface", c.borderStrong, c.bgSurface),
        ColorPair("focusRing / bgSurface", c.focusRing, c.bgSurface),
        ColorPair("chartLine / bgSurface", c.chartLine, c.bgSurface),
    )
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        for (pair in pairs) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(pair.bg, RoundedCornerShape(MTheme.radius.sm))
                    .border(MTheme.sizes.border, c.borderSubtle, RoundedCornerShape(MTheme.radius.sm))
                    .padding(MTheme.space.s3),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BasicText(pair.name, style = MTheme.type.labelMd.copy(color = pair.fg))
                BasicText(
                    ratioText(Contrast.ratio(pair.fg, pair.bg)),
                    style = MTheme.type.labelMd.copy(color = pair.fg).tabular(),
                )
            }
        }
    }
}

/** Two decimals without locale formatting: the gallery is a developer tool. */
private fun ratioText(ratio: Double): String {
    val hundredths = (ratio * 100).roundToInt()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
}

@Composable
private fun Typography() {
    val sample = stringResource(Res.string.gallery_sample_text)
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        for ((key, style) in MTheme.type.byKey()) {
            Column {
                Caption(key)
                BasicText(sample, style = style.copy(color = MTheme.colors.textPrimary))
            }
        }
    }
}

@Composable
private fun Spaces() {
    val space = MTheme.space
    val names = listOf("s1", "s2", "s3", "s4", "s5", "s6", "s8", "s10", "s12", "s16")
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        for ((name, value) in names.zip(space.all())) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Box(Modifier.width(MTheme.sizes.media)) { Caption("$name ${value.value.roundToInt()}") }
                Box(Modifier.width(value).height(MTheme.space.s2).background(MTheme.colors.accentSolid))
            }
        }
    }
}

@Composable
private fun Radii() {
    val radius = MTheme.radius
    val items: List<Pair<String, androidx.compose.ui.graphics.Shape>> = listOf(
        "xs" to RoundedCornerShape(radius.xs),
        "sm" to RoundedCornerShape(radius.sm),
        "md" to RoundedCornerShape(radius.md),
        "lg" to RoundedCornerShape(radius.lg),
        "xl" to RoundedCornerShape(radius.xl),
        "full" to radius.full,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2)) {
        for ((name, shape) in items) {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(MTheme.sizes.buttonM)
                        .background(MTheme.colors.accentSubtle, shape)
                        .border(MTheme.sizes.border, MTheme.colors.borderStrong, shape),
                )
                Caption(name)
            }
        }
    }
}

@Composable
private fun Elevations() {
    val e = MTheme.elevation
    Row(horizontalArrangement = Arrangement.spacedBy(MTheme.space.s4)) {
        // A card at e1 also has a border.subtle outline, so the sample has it too: on a light
        // page the soft shadow alone barely separates it from the background.
        ElevationBox("e1", e.e1, MTheme.colors.bgSurface, outline = MTheme.colors.borderSubtle)
        ElevationBox("e2", e.e2, MTheme.colors.bgRaised)
        ElevationBox("e3", e.e3, MTheme.colors.bgOverlay)
    }
}

@Composable
private fun ElevationBox(name: String, spec: ElevationSpec, surface: Color, outline: Color? = null) {
    val shape = RoundedCornerShape(MTheme.radius.md)
    val hairline = spec.hairlineDark
    var modifier = Modifier.size(MTheme.sizes.media)
    modifier = if (MTheme.isDark) modifier else modifier.shadow(spec.shadowLight, shape, spotColor = MTheme.colors.shadow)
    modifier = modifier.background(surface, shape)
    val edge = if (MTheme.isDark) hairline ?: outline else outline
    if (edge != null) modifier = modifier.border(MTheme.sizes.border, edge, shape)
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Box(modifier)
        Caption(name)
    }
}

@Composable
private fun Motion() {
    val m = MTheme.motion
    val durations = listOf(
        "instant" to m.instant, "press" to m.press, "fast" to m.fast,
        "base" to m.base, "slow" to m.slow, "slowExit" to m.slowExit,
    )
    Column(verticalArrangement = Arrangement.spacedBy(MTheme.space.s1)) {
        for ((name, ms) in durations) Caption("$name: $ms ms")
        Caption("springSet: ${m.springSet.dampingRatio} / ${m.springSet.stiffness.roundToInt()}")
        Caption("springPr: ${m.springPr.dampingRatio} / ${m.springPr.stiffness.roundToInt()}")
        Caption("reducedMotion: ${MTheme.reducedMotion}")
    }
}
