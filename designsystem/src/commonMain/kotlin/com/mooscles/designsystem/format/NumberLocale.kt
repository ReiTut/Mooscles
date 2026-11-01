package com.mooscles.designsystem.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale

/** The two number styles the app has: English and Brazilian Portuguese. */
enum class NumberLocale {
    EN,
    PT_BR,
    ;

    companion object {
        /** Every tag that starts with `pt` is [PT_BR]; the app has no other languages. */
        fun from(languageTag: String): NumberLocale {
            val language = languageTag.substringBefore('-').substringBefore('_').lowercase()
            return if (language == "pt") PT_BR else EN
        }
    }
}

@Composable
fun currentNumberLocale(): NumberLocale = NumberLocale.from(Locale.current.toLanguageTag())
