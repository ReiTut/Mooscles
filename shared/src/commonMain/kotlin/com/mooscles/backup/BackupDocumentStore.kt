package com.mooscles.backup

/** Documents the user picked for a backup, addressed by the platform's URI string. */
interface BackupDocumentStore {
    suspend fun write(uri: String, text: String)
    /** Best effort after a failed export: removes the empty or partial document the picker created. */
    suspend fun discard(uri: String)
    suspend fun read(uri: String): String
    /** True for file-access failures (the document could not be opened, read or written), not for data errors. */
    fun isFileAccessFailure(failure: Exception): Boolean
}
