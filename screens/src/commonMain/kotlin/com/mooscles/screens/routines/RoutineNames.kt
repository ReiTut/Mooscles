package com.mooscles.screens.routines

import com.mooscles.data.NameRule

/** Names the Train tab derives from a routine's. */
internal object RoutineNames {
    /** The stand-in for the name in a copy's template, which [copyName] swaps for the (cut) name. */
    const val NAME_TOKEN = "\u0000"

    /**
     * The name of a copy of [name]: [template] (the screen language's "%1$s (copy)" with [NAME_TOKEN] in place of the
     * name) around [name], which is cut first so the whole is at most [NameRule.MAX_LENGTH] characters and the suffix
     * is never lost. A cut never splits a surrogate pair, and does not leave a trailing space before the suffix.
     */
    fun copyName(name: String, template: String): String {
        val room = NameRule.MAX_LENGTH - (template.length - NAME_TOKEN.length)
        var cut = name.trim()
        if (cut.length > room) {
            var end = room.coerceAtLeast(0)
            if (end > 0 && cut[end - 1].isHighSurrogate()) end--
            cut = cut.substring(0, end).trimEnd()
        }
        return template.replace(NAME_TOKEN, cut)
    }
}
