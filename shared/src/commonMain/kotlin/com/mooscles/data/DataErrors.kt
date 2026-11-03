package com.mooscles.data

/** Why a data rule refused a change. Screens translate it into their own copy; nothing stores it. */
enum class DataError {
    WORKOUT_NOT_FOUND,
    WORKOUT_EXERCISE_NOT_FOUND,
    SET_NOT_FOUND,
    EXERCISE_NOT_FOUND,
    ROUTINE_NOT_FOUND,
    FOLDER_NOT_FOUND,
    SET_LIMIT,
    INVALID_VALUE,
    NO_COMPLETED_SET,
    WORKOUT_NOT_ACTIVE,
    RESTORE_UNAVAILABLE,
}

/**
 * A refused change, with the same message as before. It stays an [IllegalArgumentException], the type
 * `require` threw, so every caller that catches that type (the backup import, for one) behaves as it did.
 */
class DataRuleException(val error: DataError, message: String) : IllegalArgumentException(message)

/** `require`, with the reason typed. */
internal inline fun dataRequire(value: Boolean, error: DataError, lazyMessage: () -> String) {
    if (!value) throw DataRuleException(error, lazyMessage())
}

/** `requireNotNull`, with the reason typed. */
internal inline fun <T : Any> dataRequireNotNull(value: T?, error: DataError, lazyMessage: () -> String): T =
    value ?: throw DataRuleException(error, lazyMessage())
