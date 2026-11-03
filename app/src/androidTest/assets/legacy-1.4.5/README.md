# Fixtures written by v1.4.5

`mooscles.db` and `local_preferences.preferences_pb` were written on 2026-10-28 by Mooscles 1.4.5 (build 16), the debug APK built from `main` at `72e835f`, the commit the phase 1B branch started from. `LegacyInstallOpenTest` opens them with the current code. They contain no personal data: everything comes from the synthetic backup `docs/superpowers/reports/2026-10-27-phase1a-verificacao/export-before-upgrade.json`.

## How they were made

Only on the `emulator-5554` emulator (AVD `Mooscles_Pixel8_API35`, Android 15):

1. `git worktree add <scratchpad>/before 72e835f`, then `./gradlew :app:assembleDebug` in that worktree.
2. `svc wifi disable` and `svc data disable`, so 1.4.5 did not download the published 1.4.6 update during the run.
3. `adb uninstall com.mooscles`, install the 1.4.5 APK, grant `POST_NOTIFICATIONS`.
4. Settings → Import data → `export-before-upgrade.json` from Downloads → Import: "Imported 11 workouts, 4 routines and 1 custom exercise". The backup was exported during a workout, so the import also brings in the active workout "Upgrade routine 2" (from a routine), with two sets already completed. The import restored lb, the dark theme, automatic rest, a 2:30 default rest and the "Upgrade block" folder.
5. Because a workout was already active, no new one could be started. In that routine workout, set 3 of the high-bar back squat and set 1 of the conventional barbell deadlift were completed. The rest timer was running (deadlift, 5:00).
6. `adb shell am force-stop com.mooscles`, then `run-as com.mooscles sqlite3 databases/mooscles.db "PRAGMA wal_checkpoint(TRUNCATE)"`. After that the database had no `-wal` or `-shm` file left.
7. `adb exec-out run-as com.mooscles cat databases/mooscles.db` and `cat files/datastore/local_preferences.preferences_pb`. The MD5 of each copy matched the file on the emulator.

## Recorded values

From `sqlite3` on a copy of `mooscles.db`: `user_version` 5, `integrity_check` ok, `journal_mode` wal.

| Table | Rows |
| --- | --- |
| exercises | 292 |
| routine_folders | 2 |
| routines | 4 |
| routine_exercises | 8 |
| routine_sets | 24 |
| workouts | 11 |
| workout_exercises | 42 |
| workout_sets | 166 |
| routine_target_checkpoints | 4 |

The active workout is `9d397cf9-de65-4b86-9b76-8ae53e179638`.

From the `.preferences_pb` file: `pounds` true, `theme` dark, `auto_rest` true, `default_rest` 150, `selected_routine_folder` `e7ef98d9-773e-557f-a703-4aada6dcfda5`, `timer_token` 1793185961106, `timer_deadline` 1793186261106, `timer_duration` 300000, `timer_finished` false, `timer_workout` `9d397cf9-de65-4b86-9b76-8ae53e179638`, `timer_workout_exercise` `cfc8e2f3-8d96-484d-931f-eeedbe3962f2`. The emulator clock read 1793185972746 right after the force-stop.

Never regenerate these files with newer code: they only prove compatibility if 1.4.5 wrote them.
