package com.mooscles.backup

import android.content.Context
import java.io.IOException

/** [BackupDocumentStore] over the documents of the system picker ([BackupDocuments]). */
class AndroidBackupDocuments(private val context: Context) : BackupDocumentStore {
    override suspend fun write(uri: String, text: String) { BackupDocuments.write(context, uri, text) }
    override suspend fun discard(uri: String) { BackupDocuments.discard(context, uri) }
    override suspend fun read(uri: String): String = BackupDocuments.read(context, uri)
    override fun isFileAccessFailure(failure: Exception): Boolean = failure is IOException || failure is SecurityException
}
