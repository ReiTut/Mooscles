package com.mooscles.designsystem.tokens

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class PrimitiveColorsTest {
    /** The spec's primitives table, pasted: name to sRGB hex. */
    private val table: List<Triple<String, Long, Color>> = listOf(
        Triple("branco", 0xFFFFFFFF, Primitives.branco),
        Triple("brasa.18", 0xFF1D0C0A, Primitives.brasa18),
        Triple("brasa.30", 0xFF491F1B, Primitives.brasa30),
        Triple("brasa.50", 0xFFB02B27, Primitives.brasa50),
        Triple("brasa.52", 0xFFBA2B28, Primitives.brasa52),
        Triple("brasa.68", 0xFFED695E, Primitives.brasa68),
        Triple("brasa.74", 0xFFF98478, Primitives.brasa74),
        Triple("brasa.94", 0xFFFFE4E0, Primitives.brasa94),
        Triple("cobre.47", 0xFFB4531A, Primitives.cobre47),
        Triple("cobre.64", 0xFFE8813E, Primitives.cobre64),
        Triple("ferro.16", 0xFF0A0E0C, Primitives.ferro16),
        Triple("ferro.19", 0xFF111513, Primitives.ferro19),
        Triple("ferro.21", 0xFF151917, Primitives.ferro21),
        Triple("ferro.23", 0xFF1A1E1C, Primitives.ferro23),
        Triple("ferro.25", 0xFF1E2321, Primitives.ferro25),
        Triple("ferro.27", 0xFF232825, Primitives.ferro27),
        Triple("ferro.30", 0xFF2A2F2D, Primitives.ferro30),
        Triple("ferro.35", 0xFF373C39, Primitives.ferro35),
        Triple("ferro.42", 0xFF494E4C, Primitives.ferro42),
        Triple("ferro.50", 0xFF5F6562, Primitives.ferro50),
        Triple("ferro.57", 0xFF737976, Primitives.ferro57),
        Triple("ferro.70", 0xFF9AA09D, Primitives.ferro70),
        Triple("ferro.80", 0xFFB9BFBC, Primitives.ferro80),
        Triple("ferro.89", 0xFFD6DCD9, Primitives.ferro89),
        Triple("ferro.92", 0xFFE2E5E4, Primitives.ferro92),
        Triple("ferro.93", 0xFFE5E9E7, Primitives.ferro93),
        Triple("ferro.95", 0xFFECEFEE, Primitives.ferro95),
        Triple("ferro.96", 0xFFEFF3F1, Primitives.ferro96),
        Triple("ferro.99", 0xFFF9FDFB, Primitives.ferro99),
        Triple("latao.22", 0xFF251803, Primitives.latao22),
        Triple("latao.31", 0xFF3F2C0B, Primitives.latao31),
        Triple("latao.40", 0xFF604102, Primitives.latao40),
        Triple("latao.50", 0xFF835A01, Primitives.latao50),
        Triple("latao.87", 0xFFFFCB77, Primitives.latao87),
        Triple("latao.88", 0xFFFFCF83, Primitives.latao88),
        Triple("latao.94", 0xFFFEE8C6, Primitives.latao94),
        Triple("verde.20", 0xFF031B10, Primitives.verde20),
        Triple("verde.30", 0xFF143525, Primitives.verde30),
        Triple("verde.44", 0xFF04623F, Primitives.verde44),
        Triple("verde.47", 0xFF016C46, Primitives.verde47),
        Triple("verde.50", 0xFF00764C, Primitives.verde50),
        Triple("verde.70", 0xFF2EB97F, Primitives.verde70),
        Triple("verde.76", 0xFF39CE8E, Primitives.verde76),
        Triple("verde.83", 0xFF68E3A8, Primitives.verde83),
        Triple("verde.93", 0xFFCFF2DE, Primitives.verde93),
    )

    @Test
    fun hexValuesMatchTheSpecTable() {
        assertEquals(45, table.size)
        for ((name, hex, actual) in table) assertEquals(Color(hex), actual, name)
    }
}
