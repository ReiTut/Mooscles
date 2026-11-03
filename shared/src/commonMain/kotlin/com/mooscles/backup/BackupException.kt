package com.mooscles.backup

sealed class BackupException(message: String) : Exception(message) {
    object NotABackup : BackupException("This file is not a Mooscles backup.") {
        private fun readResolve(): Any = NotABackup
    }

    class NewerVersion(val found: Int) :
        BackupException("This backup was made by a newer version of Mooscles. Update the app and try again.")

    /** [detail] is for logs and tests; the user-facing message stays generic. */
    class Corrupted(val detail: String) : BackupException("This backup file is damaged or incomplete.")

    class Invalid(val detail: String) : BackupException("This backup contains invalid data: $detail")
}
