package com.mooscles

import com.mooscles.backup.BackupPreferences
import com.mooscles.ui.PreferencesUi

fun PreferencesUi.toBackup() = BackupPreferences(pounds, theme, autoRest, defaultRestSeconds, selectedRoutineFolderId)
