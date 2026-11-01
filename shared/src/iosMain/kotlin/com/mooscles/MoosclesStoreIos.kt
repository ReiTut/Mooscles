package com.mooscles

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * The directory that holds the iOS app's database and preferences: `Library/Application Support`
 * in the app's sandbox, created if missing.
 *
 * Application Support, not Documents: Documents is for files the person owns and can be shown in
 * the Files app, while this is app-managed data, like Android's private `databases/` and `files/`.
 * Both are kept by iOS (unlike Caches and tmp) and included in device backups.
 */
fun moosclesDataDirectory(): String {
    val url = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        .first() as NSURL
    val path = requireNotNull(url.path) { "Application Support has no file path." }
    FileSystem.SYSTEM.createDirectories(path.toPath())
    return path
}

/**
 * Opens `<directory>/datastore/local_preferences.preferences_pb`, the layout Android uses under
 * `files/` (`files/datastore/local_preferences.preferences_pb`), holding the display preferences
 * and the timer's `timer_*` keys. Pass the store to [AppPreferences] and `RestTimerPersistence`.
 *
 * DataStore allows one active instance per file: create it once per process and keep it. Cancel
 * [scope] to close it before another instance opens the same file.
 */
fun createMoosclesStore(
    directory: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope) {
    "$directory/datastore/${MoosclesStore.FILE_NAME}".toPath()
}
