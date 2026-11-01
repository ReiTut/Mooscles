package com.mooscles.domain

/** Fixed digit slots keep the separator and the two seconds digits present during every edit. */
object RestDuration {
    const val DEFAULT_SECONDS = 120
    const val MAX_SECONDS = 300
    private val inputPattern = Regex("[0-5]:[0-5][0-9]")
    private val digitPositions = listOf(0, 2, 3)

    /** History may contain longer rests from earlier versions; only new input is bounded. */
    fun format(seconds: Int): String {
        require(seconds >= 0) { "Rest duration cannot be negative" }
        return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
    }

    fun parse(text: String): Int? {
        if (!inputPattern.matches(text)) return null
        return ((text[0] - '0') * 60 + (text[2] - '0') * 10 + (text[3] - '0'))
            .takeIf { it <= MAX_SECONDS }
    }

    data class EditorValue(val text: String, val selectionStart: Int, val selectionEnd: Int)

    fun editor(seconds: Int) = EditorValue(format(seconds.coerceIn(0, MAX_SECONDS)), 0, 4)

    /** Invalid edits retain the last valid duration; a selected value starts a fresh duration. */
    fun edit(previous: EditorValue, proposed: EditorValue): EditorValue {
        if (proposed.text == previous.text) {
            // Replacing a selected zero with zero still advances to the next seconds slot.
            val slot = digitPositions.indexOf(previous.selectionStart)
            if (slot in 0..1 && previous.selectionEnd == previous.selectionStart + 1 &&
                proposed.selectionStart == previous.selectionEnd && proposed.selectionEnd == proposed.selectionStart) {
                return selectNext(previous.text, slot + 1)
            }
            return previous.copy(selectionStart = proposed.selectionStart.coerceIn(0, 4),
                selectionEnd = proposed.selectionEnd.coerceIn(0, 4))
        }

        var start = minOf(previous.selectionStart, previous.selectionEnd)
        var end = maxOf(previous.selectionStart, previous.selectionEnd)
        val insertedLength = proposed.text.length - (previous.text.length - (end - start))
        val followsSelection = insertedLength >= 0 &&
            proposed.text.startsWith(previous.text.take(start)) &&
            proposed.text.endsWith(previous.text.drop(end))
        val inserted: String
        if (followsSelection) {
            inserted = proposed.text.substring(start, start + insertedLength)
        } else {
            // Backspace/delete from a collapsed caret, or an IME replacing the whole value.
            start = previous.text.commonPrefixWith(proposed.text).length
            val suffix = previous.text.drop(start).commonSuffixWith(proposed.text.drop(start)).length
            end = previous.text.length - suffix
            inserted = proposed.text.substring(start, proposed.text.length - suffix)
        }

        if (':' in inserted) {
            return if (parse(proposed.text) != null) EditorValue(proposed.text, 4, 4) else previous
        }
        if (inserted.any { it !in '0'..'9' }) return previous
        val characters = previous.text.toCharArray()
        if (inserted.isEmpty()) {
            if (':' in proposed.text && end - start > 1 && previous.selectionStart == previous.selectionEnd)
                return previous // A truncated whole-value paste is not a valid duration.
            var removed = digitPositions.filter { it in start until end }
            // Backspacing over the separator clears the preceding minute digit.
            if (removed.isEmpty() && start == 1 && end == 2) removed = listOf(0)
            removed.forEach { characters[it] = '0' }
            val caret = removed.firstOrNull() ?: start.coerceIn(0, 4)
            return EditorValue(characters.concatToString(), caret, caret)
        }

        val firstSlot = when (start) { 0 -> 0; 1, 2 -> 1; 3 -> 2; else -> return previous }
        if (inserted.length > 3 - firstSlot) return previous
        if (start == 0 && end == 4) digitPositions.forEach { characters[it] = '0' }
        else digitPositions.filter { it in start until end }.forEach { characters[it] = '0' }
        inserted.forEachIndexed { index, character -> characters[digitPositions[firstSlot + index]] = character }
        val text = characters.concatToString()
        return if (parse(text) != null) selectNext(text, firstSlot + inserted.length) else previous
    }

    private fun selectNext(text: String, slot: Int): EditorValue =
        if (slot < digitPositions.size) EditorValue(text, digitPositions[slot], digitPositions[slot] + 1)
        else EditorValue(text, 4, 4)
}
