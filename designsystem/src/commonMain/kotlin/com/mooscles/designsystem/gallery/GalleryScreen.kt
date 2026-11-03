package com.mooscles.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.mooscles.designsystem.components.MChip
import com.mooscles.designsystem.components.MChipKind
import com.mooscles.designsystem.components.MText
import com.mooscles.designsystem.components.SettingsRow
import com.mooscles.designsystem.components.TopBar
import com.mooscles.designsystem.i18n.LocalAppLocale
import com.mooscles.designsystem.i18n.ProvideAppLocale
import com.mooscles.designsystem.icons.MIcon
import com.mooscles.designsystem.icons.MIcons
import com.mooscles.designsystem.resources.Res
import com.mooscles.designsystem.resources.gallery_controls_hide
import com.mooscles.designsystem.resources.gallery_controls_language
import com.mooscles.designsystem.resources.gallery_controls_motion
import com.mooscles.designsystem.resources.gallery_controls_scale
import com.mooscles.designsystem.resources.gallery_controls_show
import com.mooscles.designsystem.resources.gallery_controls_theme
import com.mooscles.designsystem.resources.gallery_language_en
import com.mooscles.designsystem.resources.gallery_language_pt_br
import com.mooscles.designsystem.resources.gallery_language_system
import com.mooscles.designsystem.resources.gallery_motion_full
import com.mooscles.designsystem.resources.gallery_motion_reduced
import com.mooscles.designsystem.resources.gallery_motion_system
import com.mooscles.designsystem.resources.gallery_scale_100
import com.mooscles.designsystem.resources.gallery_scale_130
import com.mooscles.designsystem.resources.gallery_scale_200
import com.mooscles.designsystem.resources.gallery_theme_dark
import com.mooscles.designsystem.resources.gallery_theme_light
import com.mooscles.designsystem.resources.gallery_title
import com.mooscles.designsystem.theme.MTheme
import com.mooscles.designsystem.theme.MoosclesDesignTheme
import com.mooscles.designsystem.theme.rememberSystemReducedMotion
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The text scales the gallery offers, with their labels. */
enum class GalleryFontScale(val scale: Float, val label: StringResource) {
    Normal(1f, Res.string.gallery_scale_100),
    Large(1.3f, Res.string.gallery_scale_130),
    Largest(2f, Res.string.gallery_scale_200),
}

/** The languages the gallery offers. [tag] null is the system language. */
enum class GalleryLanguage(val tag: String?, val label: StringResource) {
    System(null, Res.string.gallery_language_system),
    English("en", Res.string.gallery_language_en),
    Portuguese("pt-BR", Res.string.gallery_language_pt_br),
}

/**
 * The motion choices of the gallery. [System] follows the system setting (Android: animator
 * duration scale 0; iOS: Reduce Motion) and is the starting choice; the other two force it.
 */
enum class GalleryMotion(val reduced: Boolean?, val label: StringResource) {
    System(null, Res.string.gallery_motion_system),
    Reduced(true, Res.string.gallery_motion_reduced),
    Full(false, Res.string.gallery_motion_full),
}

/** Test tags of the gallery screen and its controls. */
object GalleryTags {
    const val SCREEN = "gallery-screen"
    const val CONTROLS_TOGGLE = "gallery-controls-toggle"

    fun theme(dark: Boolean): String = if (dark) "gallery-theme-dark" else "gallery-theme-light"

    fun scale(scale: GalleryFontScale): String = "gallery-scale-${scale.name}"

    fun language(language: GalleryLanguage): String = "gallery-language-${language.name}"

    fun motion(motion: GalleryMotion): String = "gallery-motion-${motion.name}"
}

/**
 * The top controls of the gallery. They apply to the open page (and the page list) and live
 * in memory only: nothing is saved, and they start over each time the gallery opens. Motion
 * starts by following the system. [expanded] false folds the controls into one row, so a
 * page gets almost the whole screen.
 */
@Stable
class GalleryControls(dark: Boolean) {
    var dark by mutableStateOf(dark)
    var fontScale by mutableStateOf(GalleryFontScale.Normal)
    var motion by mutableStateOf(GalleryMotion.System)
    var language by mutableStateOf(GalleryLanguage.System)
    var expanded by mutableStateOf(true)
}

/**
 * The open page and the controls. A host may hoist it to route the system back to [back]. At most
 * one page is open: a design system page ([open]) or a host's extra page ([openExtra], its key).
 */
@Stable
class GalleryState(initialPage: GalleryPage?, dark: Boolean) {
    var open by mutableStateOf(initialPage)
    var openExtra by mutableStateOf<String?>(null)
    val controls = GalleryControls(dark)

    /**
     * Closes the open page. On the page list it leaves instead: the system language comes back
     * first, while the gallery is still composed, and only then [leave] runs, so the screen that
     * comes next composes in the system language.
     */
    fun back(leave: () -> Unit) {
        if (open != null || openExtra != null) {
            open = null
            openExtra = null
            return
        }
        restoreLanguage()
        leave()
    }

    /** Puts the system language back. Hosts call it when they navigate away by another path. */
    fun restoreLanguage() {
        controls.language = GalleryLanguage.System
        LocalAppLocale.restore()
    }
}

@Composable
fun rememberGalleryState(initialPage: GalleryPage? = null): GalleryState {
    val dark = MTheme.isDark
    return remember { GalleryState(initialPage, dark) }
}

/**
 * The gallery: the top bar, the controls (theme, text size, motion and language) and either
 * the page list or the open page. The whole screen, top bar and controls included, uses the
 * chosen theme and motion; only the page below the controls gets the chosen text size. Back
 * closes the page, then leaves through [onBack]. [extraPages] are the host's own pages, listed
 * after the design system's.
 */
@Composable
fun GalleryScreen(
    onBack: () -> Unit,
    initialPage: GalleryPage? = null,
    state: GalleryState = rememberGalleryState(initialPage),
    extraPages: List<GalleryExtraPage> = emptyList(),
) {
    val controls = state.controls
    val haptics = MTheme.haptics
    ProvideAppLocale(controls.language.tag) {
        val reducedMotion = controls.motion.reduced ?: rememberSystemReducedMotion()
        MoosclesDesignTheme(dark = controls.dark, reducedMotion = reducedMotion, haptics = haptics) {
            Column(Modifier.fillMaxSize().background(MTheme.colors.bgBase).testTag(GalleryTags.SCREEN)) {
                val page = state.open
                // A key the host no longer passes shows the list; back still clears it.
                val extra = if (page == null) state.openExtra?.let { key -> extraPages.firstOrNull { it.key == key } } else null
                val title = when {
                    page != null -> stringResource(page.title)
                    extra != null -> extra.title()
                    else -> stringResource(Res.string.gallery_title)
                }
                TopBar(title, onBack = { state.back(onBack) })
                Controls(controls)
                Box(Modifier.fillMaxWidth().height(MTheme.sizes.border).background(MTheme.colors.borderSubtle))
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, controls.fontScale.scale)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            page != null -> page.Content()
                            extra != null -> Box(Modifier.testTag(galleryExtraPageTag(extra.key))) { extra.content() }
                            else -> PageList(
                                extraPages,
                                onOpen = {
                                    state.openExtra = null
                                    state.open = it
                                },
                                onOpenExtra = {
                                    state.open = null
                                    state.openExtra = it
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Controls(controls: GalleryControls) {
    Column(Modifier.fillMaxWidth()) {
        if (controls.expanded) {
            ChoiceRow(stringResource(Res.string.gallery_controls_theme)) {
                for (dark in listOf(false, true)) {
                    MChip(
                        stringResource(if (dark) Res.string.gallery_theme_dark else Res.string.gallery_theme_light),
                        selected = controls.dark == dark,
                        onClick = { controls.dark = dark },
                        modifier = Modifier.testTag(GalleryTags.theme(dark)),
                        kind = MChipKind.Segmented,
                    )
                }
            }
            ChoiceRow(stringResource(Res.string.gallery_controls_scale)) {
                for (scale in GalleryFontScale.entries) {
                    MChip(
                        stringResource(scale.label),
                        selected = controls.fontScale == scale,
                        onClick = { controls.fontScale = scale },
                        modifier = Modifier.testTag(GalleryTags.scale(scale)),
                        kind = MChipKind.Segmented,
                    )
                }
            }
            ChoiceRow(stringResource(Res.string.gallery_controls_language)) {
                for (language in GalleryLanguage.entries) {
                    MChip(
                        stringResource(language.label),
                        selected = controls.language == language,
                        onClick = { controls.language = language },
                        modifier = Modifier.testTag(GalleryTags.language(language)),
                        kind = MChipKind.Segmented,
                    )
                }
            }
            ChoiceRow(stringResource(Res.string.gallery_controls_motion)) {
                for (motion in GalleryMotion.entries) {
                    MChip(
                        stringResource(motion.label),
                        selected = controls.motion == motion,
                        onClick = { controls.motion = motion },
                        modifier = Modifier.testTag(GalleryTags.motion(motion)),
                        kind = MChipKind.Segmented,
                    )
                }
            }
        }
        SettingsRow(
            stringResource(if (controls.expanded) Res.string.gallery_controls_hide else Res.string.gallery_controls_show),
            Modifier.testTag(GalleryTags.CONTROLS_TOGGLE),
            onClick = { controls.expanded = !controls.expanded },
            trailing = {
                MIcon(
                    if (controls.expanded) MIcons.ChevronUp else MIcons.ChevronDown,
                    contentDescription = null,
                    size = MTheme.sizes.icon20,
                    tint = MTheme.colors.textTertiary,
                )
            },
        )
    }
}

/**
 * A label and its choices on one line, which scrolls sideways when the text is large. One line
 * per control keeps the page below as tall as possible. While more choices are hidden past an
 * edge, that edge fades into the background, which shows that the row scrolls.
 */
@Composable
private fun ChoiceRow(label: String, choices: @Composable RowScope.() -> Unit) {
    val scroll = rememberScrollState()
    val fade = MTheme.space.s8
    val edge = MTheme.colors.bgBase
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val width = fade.toPx().coerceAtMost(size.width / 2)
                val clear = edge.copy(alpha = 0f)
                // Forward is toward the end edge: the right one, or the left one right to left.
                if (scroll.canScrollForward) fadeEdge(atRight = !rtl, width, clear, edge)
                if (scroll.canScrollBackward) fadeEdge(atRight = rtl, width, clear, edge)
            }
            .horizontalScroll(scroll)
            .padding(horizontal = MTheme.space.s4),
        horizontalArrangement = Arrangement.spacedBy(MTheme.space.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MText(label, MTheme.type.labelMd, color = MTheme.colors.textSecondary)
        choices()
    }
}

/** Covers [width] px at one side with a gradient from [clear] to [edge], toward the outside. */
private fun DrawScope.fadeEdge(atRight: Boolean, width: Float, clear: Color, edge: Color) {
    val left = if (atRight) size.width - width else 0f
    val colors = if (atRight) listOf(clear, edge) else listOf(edge, clear)
    drawRect(
        Brush.horizontalGradient(colors, startX = left, endX = left + width),
        topLeft = Offset(left, 0f),
        size = Size(width, size.height),
    )
}

/** One row per page, each a settings row with a chevron, at least 56 dp tall; the [extraPages] last. */
@Composable
private fun PageList(extraPages: List<GalleryExtraPage>, onOpen: (GalleryPage) -> Unit, onOpenExtra: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = MTheme.space.s2)) {
        for (entry in GalleryPage.entries) {
            SettingsRow(stringResource(entry.title), Modifier.testTag(galleryEntryTag(entry)), onClick = { onOpen(entry) })
        }
        for (extra in extraPages) {
            SettingsRow(extra.title(), Modifier.testTag(galleryExtraEntryTag(extra.key)), onClick = { onOpenExtra(extra.key) })
        }
    }
}
