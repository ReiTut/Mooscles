package com.mooscles.designsystem.components

/** What a [NumericField] edits: a load (decimal) or a repetition count (integer). */
enum class NumericKind { Load, Reps }

/**
 * Pure parsing of what the user types in a numeric field. Rounding stays in the domain: this
 * only decides whether the text is a number and within bounds. Both `,` and `.` are accepted as
 * the decimal separator; a sign, a space, a thousands separator and empty text are refused.
 */
object NumericInput {
    private val decimal = Regex("[0-9]+([.,][0-9]+)?")
    private val integer = Regex("[0-9]+")
    private val typingLoad = Regex("[0-9]*([.,][0-9]*)?")
    private val typingReps = Regex("[0-9]*")
    private const val MAX_TYPED_LENGTH = 8

    /** The value of [text], or null when it is not a decimal with at most [maxDecimals] digits, or is above [max]. */
    fun parseDecimal(text: String, maxDecimals: Int = 2, max: Double): Double? {
        if (!decimal.matches(text)) return null
        val separator = text.indexOfFirst { it == '.' || it == ',' }
        if (separator >= 0 && text.length - separator - 1 > maxDecimals) return null
        val value = text.replace(',', '.').toDoubleOrNull() ?: return null
        return if (value <= max) value else null
    }

    /** The value of [text], or null when it is not a whole number or is above [max]. */
    fun parseInteger(text: String, max: Int): Int? {
        if (!integer.matches(text)) return null
        val value = text.toIntOrNull() ?: return null
        return if (value <= max) value else null
    }

    /** Whether [text] can be a step on the way to a valid number of [kind] (the empty text and `82,` can). */
    fun isTypable(text: String, kind: NumericKind): Boolean =
        text.length <= MAX_TYPED_LENGTH && when (kind) {
            NumericKind.Load -> typingLoad.matches(text)
            NumericKind.Reps -> typingReps.matches(text)
        }
}
