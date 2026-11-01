package com.mooscles.designsystem.charts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Test seam (spec D3, "o `Path` só é refeito quando dados ou tamanho mudam"): called each time a
 * chart rebuilds its drawing cache, with `"line"` ([MLineChart]), `"bar"` ([MBarChart]) or
 * `"spark"` ([MSparkline]). Null, the default, costs nothing. Called in the draw phase, on the UI thread.
 */
internal val LocalChartPathBuilds = staticCompositionLocalOf<((String) -> Unit)?> { null }

internal const val CHART_BUILD_LINE = "line"
internal const val CHART_BUILD_BAR = "bar"
internal const val CHART_BUILD_SPARK = "spark"
