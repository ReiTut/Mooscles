package com.mooscles.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.mooscles.designsystem.components.LocalOverlayWindowSetup
import com.mooscles.designsystem.theme.MoosclesDesignTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B50), onPrimary = Color.White,
    primaryContainer = Color(0xFFBEF3D8), onPrimaryContainer = Color(0xFF003828),
    secondary = Color(0xFF50645B), secondaryContainer = Color(0xFFD2E8DA),
    tertiary = Color(0xFF895200), tertiaryContainer = Color(0xFFFFDDB1),
    background = Color(0xFFF7F8F2), onBackground = Color(0xFF18201B),
    surface = Color(0xFFF7F8F2), onSurface = Color(0xFF18201B),
    surfaceVariant = Color(0xFFE0E7DF), onSurfaceVariant = Color(0xFF414B44),
    outline = Color(0xFF717B73), outlineVariant = Color(0xFFC1CCC2),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF83DBB0), onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513C), onPrimaryContainer = Color(0xFFBEF3D8),
    secondary = Color(0xFFB7CCBE), secondaryContainer = Color(0xFF384D41),
    tertiary = Color(0xFFFFBA66), tertiaryContainer = Color(0xFF683D00),
    background = Color(0xFF101713), onBackground = Color(0xFFE0E6DE),
    surface = Color(0xFF101713), onSurface = Color(0xFFE0E6DE),
    surfaceVariant = Color(0xFF303C34), onSurfaceVariant = Color(0xFFBFCBC0),
    outline = Color(0xFF89958B), outlineVariant = Color(0xFF414D44),
)
private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.7).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 23.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)

/**
 * An Activity whose system bars follow the app's resolved theme rather than the system's night
 * mode. [MoosclesTheme] reports the theme after every composition; see MainActivity.
 */
fun interface AppThemeBars { fun appThemeResolved(dark: Boolean) }

@Composable
fun MoosclesTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        (activity as? AppThemeBars)?.appThemeResolved(dark)
    }
    // The Material theme stays exactly as before for the existing screens; the design system
    // tokens (MTheme) are provided inside it for the new components.
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography) {
        MoosclesDesignTheme(dark = dark) {
            // An overlay's window (dialog, sheet, the exercise form) takes its bars' appearance from the system, not the app.
            val overlayBars: @Composable () -> Unit = { OverlayWindowBars(dark) }
            CompositionLocalProvider(LocalOverlayWindowSetup provides overlayBars) { content() }
        }
    }
}

/**
 * Gives the window of the overlay this runs in the status and navigation bars of the app's theme [dark]
 * (dark icons on a light app), as the Activity does for its own window. Nothing happens outside a dialog window.
 */
@Composable
private fun OverlayWindowBars(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
