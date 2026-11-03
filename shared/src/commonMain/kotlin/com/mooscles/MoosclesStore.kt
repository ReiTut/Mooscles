package com.mooscles

/**
 * The one preferences file installed apps already have: display preferences and the rest timer's
 * `timer_*` keys, at `files/datastore/local_preferences.preferences_pb` on Android. Never rename it.
 */
object MoosclesStore {
    const val NAME = "local_preferences"
    const val FILE_NAME = "$NAME.preferences_pb"
}
