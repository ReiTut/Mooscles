package com.mooscles.designsystem.format

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Formats [n] with the plural [res]. Zero stays plural ("0 séries"), as everywhere in the app:
 * CLDR puts 0 in `one` for Portuguese, so the quantity passed to the plural rule is 2.
 */
@Composable
fun pluralCount(res: PluralStringResource, n: Int): String =
    pluralStringResource(res, if (n == 0) 2 else n, n)

/**
 * Formats the plural [res] chosen by [n] with [formatArgs], for a plural whose counted word does
 * not follow its first argument ("%1$d of %2$d sets" is plural on the total). Zero stays plural,
 * as in the single-argument [pluralCount].
 */
@Composable
fun pluralCount(res: PluralStringResource, n: Int, vararg formatArgs: Any): String =
    pluralStringResource(res, if (n == 0) 2 else n, *formatArgs)
