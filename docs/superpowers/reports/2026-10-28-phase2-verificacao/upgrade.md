# Upgrade in place: main (post-1B) to feat/phase2-design-system

Task 16, Step 1. Run on 2026-10-28 on `emulator-5554` (AVD `Mooscles_Pixel8_API35`, Android 15), following the recipe in `app/src/androidTest/assets/legacy-1.4.5/README.md`.

**Result: passed.** The preferences file was byte-identical across the upgrade, Settings showed the same non-default values, the active workout resumed and the rest timer kept its deadline and finished on it.

## APKs

| | Commit | versionCode / versionName | Built in |
| --- | --- | --- | --- |
| Before | `origin/main` at `4f698a8` (post-1B) | 16 / 1.4.5 | a detached worktree in the scratchpad (`./gradlew :app:assembleDebug`, removed afterwards) |
| After | `feat/phase2-design-system` at `4d33c32` | 16 / 1.4.5 | the main checkout (`./gradlew :app:assembleDebug`) |

Both are signed by the local Mac debug key, which is not the release key; the upgrade proves data compatibility between the two builds, not that a phone running the published APK can update to this one. The `versionCode` is the same, so `adb install -r` replaced the APK as a same-version reinstall over the existing data (no uninstall, no `pm clear`).

## Steps

1. `svc wifi disable` and `svc data disable` before the first launch (they stay off).
2. `adb uninstall com.mooscles`, install the before APK, grant `POST_NOTIFICATIONS`.
3. Settings, Import data, `export-before-upgrade.json` from Downloads, Import: "Imported 11 workouts, 4 routines and 1 custom exercise". The import brought the active workout "Upgrade routine 2" with two sets done, lb, dark theme, automatic rest, 2:30 default rest and the "Upgrade block" folder.
4. In that workout, completed set 3 of the high-bar back squat and set 1 of the conventional barbell deadlift: 4 sets done, rest timer running (deadlift, 5:00).
5. Read `files/datastore/local_preferences.preferences_pb` with `run-as` and decoded it.
6. With the app still running and the timer counting, `adb install -r` the after APK, then launched it.

## Preferences (decoded `local_preferences.preferences_pb`)

The file read before the upgrade and the one read after the first launch of the new build have the same MD5 (`f9706d640bb84efba0ec12847390edcd`).

| Key | Before | After |
| --- | --- | --- |
| `pounds` | true | true |
| `theme` | dark | dark |
| `auto_rest` | true | true |
| `default_rest` | 150 | 150 |
| `selected_routine_folder` | `e7ef98d9-773e-557f-a703-4aada6dcfda5` | same |
| `timer_deadline` | 1793213122906 (15:45:22.906) | same |
| `timer_duration` / `timer_interval_duration` | 300000 / 300000 | same |
| `timer_token` | 1793212822906 | same |
| `timer_paused` / `timer_finished` | 0 / false | same |
| `timer_name` | Conventional barbell deadlift | same |
| `timer_workout` | `9d397cf9-de65-4b86-9b76-8ae53e179638` | same |
| `timer_workout_exercise` | `cfc8e2f3-8d96-484d-931f-eeedbe3962f2` | same |

## What the new build showed

- **Settings** (`upgrade-settings-after.png`, then `settings-dark-en*.png`): Dark selected, Pounds · lb selected, "Start after completing a set" on, default rest field `2:30` min. Installed 1.4.5 · build 16. Import data disabled with "Finish or discard the active workout to import".
- **Train**: folder chip "Upgrade block"; "Pick up where you left off: Upgrade routine 2, 4 sets logged", Resume workout.
- **Workout** (`upgrade-workout-after.png`): "Upgrade routine 2", 4 sets done, 4337.6 lb moved; deadlift set 1 shows "Undo set completion".
- **Timer**: the dock read 4:28 at 1793212853668 ms, launched 3.5 s after the install; the deadline minus that clock is 4:29, so the deadline carried over. `dumpsys alarm` showed the `RestAlarmReceiver` RTC_WAKEUP alarm with `origWhen 1793213122906` before and after the install. At 15:45:27 the store had `timer_finished = true`, `timer_deadline = 0`, and the dock read "Rest complete 0:00".

## Not verified

- No rest-complete notification was left in the shade. The app was in the foreground when the timer finished, and I did not check whether it posts the notification then. This is not something the upgrade changes.
- The release-key upgrade path (published APK to this build) can't be tested on this Mac (see above).
- After the checks, the active workout was discarded so the gallery screenshots would not have the old timer dock over them. The theme was changed during the captures and set back to Dark at the end.
