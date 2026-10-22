# Mooscles

A native Android workout journal built with Kotlin, Jetpack Compose and Material 3. Android 8.0 (API 26) or newer. Training data stays on the device and training works fully offline. Network requests only discover and download public GitHub app updates; no workout data is uploaded. No account, subscriptions or analytics.

## Download

The current release is **1.2.0**, version code **7**, with the same application ID and existing debug certificate.

[v1.2.0 debug prerelease](https://github.com/ReiTut/Mooscles/releases/tag/v1.2.0) · [Installable debug-signed APK](https://github.com/ReiTut/Mooscles/releases/download/v1.2.0/Mooscles-v1.2.0-debug.apk) · [Update metadata](https://github.com/ReiTut/Mooscles/releases/download/v1.2.0/update.json)

This is a debug-signed prerelease. It keeps application ID `com.mooscles`, uses version name `1.2.0` and version code `7`, and requires Android 8.0 or newer. APKs are release assets, not committed source files. Version 1.1.0 and later already include the updater: enable **Settings → App updates → Include prereleases** to discover this version, then check for updates. Stable-channel users remain on the existing v1.1.1 release. Older versions without the updater must install this APK once. Automatic downloads use unmetered Wi-Fi by default, and Android may require installation approval. The existing local debug signing key is used; private keys and local signing settings are excluded from Git. Keep that key outside the repository for future installable updates signed with the same certificate.

## Use

Create routines in **Train** and add movements from the searchable muscle/equipment-filtered library. Valid set-count, rep and load edits save automatically. Reps are optional and start blank when no target is saved; starting a routine copies its saved weight and rep targets into unfinished workout sets. Previously logged reps stay saved. Each exercise has an independent rest duration entered as minutes:seconds, such as **2:00 min**. The new default is **2:00**, including missing-value fallbacks; explicitly saved durations, including 1:30, are preserved. You can edit rest during a workout for subsequent rests; the timer's **−0:15 / +0:15** controls adjust rest already in progress. The default rest duration in Settings also saves automatically.

Start a routine or an empty session. Log decimal loads and reps, mark working/warm-up/assistance sets, complete or undo sets, and finish to view the full breakdown. Rounded, filled numeric fields retain their original sizes. Active sessions save every valid edit automatically and can be resumed after app/process restart. History supports search, set corrections, exercise changes, renaming and confirmed deletion. Custom exercises retain stable identities when renamed.

The offline catalog contains **277** established exercise variations across barbell, dumbbell, kettlebell, cable, bodyweight, Smith, selectorized and plate-loaded machines. Equipment, movement variation, and logging convention keep distinct records.

## Build

Version 1.1.0 adds **Settings → App updates** with foreground checks, approximately six-hour background checks, stable/prerelease channels, Wi-Fi download preferences, progress, retries, and Android installation approval. Installation waits for saved workouts and stopped rest timers. Install this updater-enabled APK once on older versions to enable future updates. See [UPDATES.md](UPDATES.md) for the required APK + `update.json` release assets and publishing procedure.

Toolchain: AGP 8.7.3, Gradle wrapper 8.14.5, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Room 2.7.2; Java bytecode 17, JDK 21 used for builds. Compile/target API 35, build-tools 35.0.0.

On the development workspace, the local JDK and SDK are installed under ignored `.tooling/`; ignored `local.properties` points to the SDK. After configuring your own JDK and SDK as described below, run:

```powershell
.\build.ps1
```

After Android Studio's daemon-toolchain migration, `gradle/gradle-daemon-jvm.properties` declares **Adoptium Java 21** for Gradle. Android Studio and command-line builds use these criteria; `build.ps1` also selects the bundled JDK 21 to launch the wrapper. Gradle 8.x cannot run with Java 25.

On a fresh checkout, install Adoptium JDK 21, Android SDK platform 35 and build-tools 35.0.0. Set `JAVA_HOME` and set `sdk.dir` in untracked `local.properties` to your SDK folder. The checked-in wrapper downloads Gradle. Build dependencies require network only during initial resolution.

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

The latter requires a booted emulator or authorized USB device. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Android Studio can open the project root directly. Test/lint reports are under `app/build/reports/`.

To create an installable APK in Android Studio, select the `debug` build variant, then **Build → Generate Bundle(s) / APK(s) → Generate APK(s)**. When it finishes, click **locate** in the notification. You can copy/share `app-debug.apk` and install it directly on an Android 8+ device. The debug APK is signed automatically. For a future production-signed distribution, use **Build → Generate Signed App Bundle or APK → APK** after resolving signing compatibility with existing installations; changing the key can prevent updates.

Version compatibility references: [AGP 8.7](https://developer.android.com/build/releases/agp-8-7-0-release-notes), [Kotlin Gradle compatibility](https://kotlinlang.org/docs/gradle-configure-project.html), [Compose compiler plugin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin), [Room releases](https://developer.android.com/jetpack/androidx/releases/room).

## Architecture and persistence

`ui/` contains Compose screens, navigation, presentation models and the UI action contract. `AppViewModel` maps repository snapshots to screen state, serializes commands, and catches save failures. `data/` contains Room entities, foreign-key relationships/cascade rules, a transactional repository, and the offline catalog. A single transaction reads each observable database snapshot. Workout finalization checks the existing status and saves once atomically; concurrent starts reuse the single active workout. `domain/` contains pure record, volume and unit calculations.

Workouts copy exercise metadata and planned sets into independent snapshots. Renaming exercises or editing/deleting routines never rewrites past workouts. Records are derived from completed history instead of cached, so corrections and deletions recalculate both current records and each session's PR total. Room schemas v1 and v2 are exported to `app/schemas/`. The additive v1 → v2 migration creates only PR synchronization checkpoints, preserving every existing routine and workout table. No destructive migration fallback is enabled.

Preferences and rest deadlines use DataStore. Timer state includes exercise, duration, deadline, paused remainder and a unique token. A new completed set restarts rest with that exercise's duration; editing an already completed set does not restart rest. Pause freezes the remainder; resume continues it; reset returns to the configured duration in a paused state. Rest includes background time and survives process restart. Completion is claimed once to avoid duplicate alerts. The in-app clock stays accurate when notification permission is denied. Android background notifications are used when permitted. On Android 12+, without special exact-alarm access the permission-free system alarm can arrive late under power saving; the in-app countdown and restored deadline remain correct. Wall-clock changes affect deadlines; reboot/force-stop cancels OS alarms and reopening the app restores the persisted timer.

Automatic cloud backups and device-transfer extraction are disabled, including databases and preferences. Uninstalling the app removes local data. There is currently no import/export feature.

## Weight-first PR policy

A weight PR is the highest **positive external weight in a valid completed working set**, compared against the historical maximum from **all earlier completed workouts** for the same stable variation ID and load convention. Repetitions and volume never override weight:

- Previous 100 kg × 10, new 105 kg × 3: weight PR.
- Equal 100 kg with more reps: no weight PR.
- Lower weight with higher volume: no weight PR.
- Several improving sets of one variation in one session: counted once, using the highest weight.
- An initial positive load is labeled **First record**, separate from records beaten.

Incomplete sets, discarded sessions, warm-ups, assistance-only values, and zero load are excluded from external-weight PRs. Active PR badges are provisional until the workout is saved. Unit changes use canonical kg. Comparisons ignore only machine-precision conversion noise (four double-precision units in the last place); unedited displayed weights keep their canonical precision.

Repetition PRs compare completed working sets at the same canonical external load. Blank weight is zero added load and qualifies for bodyweight repetition records. Assisted sets compare only with the same variation, assistance mode, and assistance amount. The first record at a load is labeled separately from a record beaten. Every exercise can display both Weight PR and Reps PR badges; “Exercises with PRs” counts each variation once across all first records and records beaten, even if it earns both types or improves several load-specific repetition records. Three first-record badges for bench press and pull-up therefore count as two exercises. Editing earlier history recalculates all affected records.

## Set entry and rest

The local **1.2.0 / version code 7** build adds persistent PR-based routine targets. On workout completion, the repository selects the highest eligible working-set weight, then the highest repetitions at that weight, always copying both values from one actual set. A heavier set qualifies even with fewer reps or lower volume. Repetition improvements at the current record load update reps; lower-weight repetition records cannot replace that load. Bodyweight records keep the weight target blank. Warm-ups, assistance, unfinished sets and active/discarded workouts do not establish these targets. Stable exercise IDs and load conventions keep variations separate; all loads stay in canonical kg with existing kg/lb conversion tolerance.

Targets are saved across all routines referencing that variation. Existing eligible history synchronizes on the first feature initialization; new or replaced routine exercises also use their existing records. Persistent consumed-record checkpoints keep manual edits intact through restarts and non-record workouts until a newer qualifying record is earned. The editor accepts saved target changes without writing during rendering. Set counts/order, rest prescriptions, active workouts and completed snapshots are preserved. The app stores one standard target per routine exercise rather than separate warm-up/back-off prescriptions.

New routine exercises and newly added or replaced workout exercises start with two sets. Explicit saved set counts and completed history are preserved. Routine workouts copy their saved target repetitions and weight; exercises without targets and newly added sets keep reps blank. Positive whole reps are required before completion. Blank workout weight stays blank and saves as zero external load; history describes it as Bodyweight without estimating body mass or adding it to volume.

All rest editors use a numeric `M:SS` mask with a permanent colon and a maximum of `5:00`. Typing `2`, `3`, `0` produces `2:30`; `2`, `0`, `3` produces `2:03`. Invalid seconds and over-limit input are rejected without replacing the last valid saved value. Timer adjustment buttons and persistence apply the same five-minute bound. Existing routine, preference, and active-workout rest settings above the maximum normalize to `5:00`; completed workout snapshots retain their original rest durations. The workout timer sits in a compact bottom dock outside the exercise list and above the app navigation, with separate keyboard inset handling.

## Load and volume conventions

Volume is **logged load × logged reps** for valid completed sets, including completed warm-ups, excluding assistance. No hidden multipliers are used. Total session weight moved is the sum of these logged external-load volumes; mixed conventions are labeled per exercise and are not a biomechanical estimate.

- **Total load:** total external resistance entered.
- **Per hand:** one dumbbell's load and reps; per-hand volume, never silently doubled.
- **One side:** total external load used on the working side and reps for that side; never silently doubled.
- **Added weight:** external load added to bodyweight. Leave weight blank for bodyweight; unknown bodyweight contributes no invented volume.
- **Assistance:** resistance offset supplied by a machine; excluded from external-weight PRs and weight moved.

Weights must be finite and nonnegative. Completed sets require positive whole reps. kg/lb preferences change display and entry units while persistent loads stay in kg. Elapsed workout duration spans start to finish, including rests and time outside the app.

## Verification

Unit tests cover strict weight priority, ties and lower-weight volume, historical rather than recent maxima, duplicate variation counting, first records, warm-up/assistance exclusion, unit conversion, per-hand/one-side/bodyweight volume and timer restoration. Robolectric tests exercise actual Room persistence, snapshots, concurrent start/finalization, corrections/deletions and reopening a database file. Instrumented tests cover database persistence, durable DataStore timer transitions and the main Compose flow; their execution requires a suitable device.

See [VERIFICATION.md](VERIFICATION.md) for the current build results and APK details. Device instrumentation requires a connected Android device or usable emulator; host tests cover persistence, input behavior, PR summaries, and the pinned timer layout.
