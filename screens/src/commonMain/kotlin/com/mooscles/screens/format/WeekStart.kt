package com.mooscles.screens.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.intl.Locale
import com.mooscles.designsystem.i18n.deviceRegion
import kotlinx.datetime.DayOfWeek

/**
 * First day of the week by region, from the CLDR `weekData/firstDay` table, in pure Kotlin
 * (no `java.time`). Monday is the CLDR default; the regions that differ are listed, plus every ISO region to tell an unknown one apart.
 */
object WeekStart {
    private val sunday = setOf(
        "AG", "AS", "BD", "BR", "BS", "BT", "BW", "BZ", "CA", "CO", "DM", "DO", "ET", "GT", "GU",
        "HK", "HN", "ID", "IL", "IN", "JM", "JP", "KE", "KH", "KR", "LA", "MH", "MM", "MO", "MT",
        "MX", "MZ", "NI", "NP", "PA", "PE", "PH", "PK", "PR", "PT", "PY", "SA", "SG", "SV", "TH",
        "TT", "TW", "UM", "US", "VE", "VI", "WS", "YE", "ZA", "ZW",
    )
    private val saturday = setOf(
        "AE", "AF", "BH", "DJ", "DZ", "EG", "IQ", "IR", "JO", "KW", "LY", "OM", "QA", "SD", "SY",
    )
    private val friday = setOf("MV")

    /** ISO 3166-1 regions: a region outside this set is unknown and falls back by language. */
    private val known = setOf(
        "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ", "BA", "BB", "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ",
        "BR", "BS", "BT", "BV", "BW", "BY", "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX", "CY", "CZ", "DE", "DJ", "DK", "DM",
        "DO", "DZ", "EC", "EE", "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK", "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS",
        "GT", "GU", "GW", "GY", "HK", "HM", "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR", "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN",
        "KP", "KR", "KW", "KY", "KZ", "LA", "LB", "LC", "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK", "ML", "MM", "MN", "MO", "MP", "MQ",
        "MR", "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE", "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG", "PH", "PK", "PL", "PM",
        "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC", "SD", "SE", "SG", "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS", "ST", "SV",
        "SX", "SY", "SZ", "TC", "TD", "TF", "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY", "UZ", "VA", "VC", "VE", "VG", "VI",
        "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW",
    )

    /**
     * [languageTag] such as `pt-BR`, `zh-Hant-TW` or `pt_BR`. A tag without a region takes
     * [deviceRegion] (the system's, spec D8: an app language chosen as plain `en` must not hide an
     * `US` device); without either, `pt` starts on Sunday and the rest on Monday.
     */
    fun of(languageTag: String, deviceRegion: String? = null): DayOfWeek {
        val parts = languageTag.replace('_', '-').split('-')
        val language = parts.firstOrNull()?.lowercase().orEmpty()
        // The region is the first 2-letter (or 3-digit) subtag after the language, before any extension.
        val region = parts.drop(1).takeWhile { it.length > 1 }
            .firstOrNull { (it.length == 2 && it.all(Char::isLetter)) }
            ?.uppercase()
            ?: deviceRegion?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()
        return when {
            region in sunday -> DayOfWeek.SUNDAY
            region in saturday -> DayOfWeek.SATURDAY
            region in friday -> DayOfWeek.FRIDAY
            region in known -> DayOfWeek.MONDAY
            language == "pt" -> DayOfWeek.SUNDAY
            else -> DayOfWeek.MONDAY
        }
    }
}

/** The first day of the week for the current locale, by the device's region when the app's language has none. */
@Composable
fun currentWeekStart(): DayOfWeek = WeekStart.of(Locale.current.toLanguageTag(), deviceRegion())
