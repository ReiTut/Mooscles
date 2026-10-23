# Mooscles

A native Android workout journal built with Kotlin, Jetpack Compose and Material 3. Android 8.0 (API 26) or newer. Training data stays on the device and training works fully offline. App network requests only discover and download public GitHub updates. Optional sharing creates a workout image locally and opens the user's chosen destination; it never selects a recipient or sends automatically. No account, subscriptions or analytics.

## Workout responsiveness

Version 1.4.3 / Build 14 batches routine loading, prepares presentation off the main thread, reuses unchanged history and row data, and keeps timer ticks separate from workout content. Active workout set inputs are individually lazy, with persistent instance keys and saveable drafts so scrolling does not lose edits. Start shows progress and coalesces repeated taps. Database schema v4 and saved workout formats are unchanged.

`build.ps1 -Tasks ':app:assemblePerformance'` creates an R8-optimized, non-debuggable, profileable APK using the existing package and signing identity. [PERFORMANCE.md](PERFORMANCE.md) records the source evidence, device limitations, matching baseline build, fixtures, and capture procedure. Device startup latency, scrolling smoothness and memory changes remain unmeasured. The public distribution keeps the existing debug variant for continuity.

## Download

The current release is **1.4.3 / Build 14**, with workout loading and rendering improvements built on the v1.4.1 recovery functionality.

[v1.4.3 full release · Latest](https://github.com/ReiTut/Mooscles/releases/tag/v1.4.3) · [Installable debug-signed APK](https://github.com/ReiTut/Mooscles/releases/download/v1.4.3/Mooscles-v1.4.3-debug.apk) · [Update metadata](https://github.com/ReiTut/Mooscles/releases/download/v1.4.3/update.json)

This full release is marked Latest and retains the existing debug APK and signing identity for update compatibility. It keeps application ID `com.mooscles`, uses version name `1.4.3` and version code `14`, and requires Android 8.0 or newer. APKs are release assets, not committed source files. Previous tags and releases remain intact. Distributed updaters select the highest compatible metadata version code, so prior builds can discover Build 14 on the default stable channel. **Include prereleases** is not required. Automatic downloads respect saved preferences, and Android may require installation approval. Older versions without the updater can install this APK once over their compatible existing app. The existing local debug signing key is retained and excluded from Git. Full GitHub release status does not change the APK's build type or signing certificate.

The interface keeps the recovery build's exercise menus and workout behavior. The new background presentation cache follows saved values and invalidates on history corrections, routine changes and unit switches. Saved exercise order, independent targets, routines, completed history and active sessions remain readable through the unchanged Room schema v4 and migrations. Nullable timer ownership from Build 12 remains compatible, including its persistence and cleanup. The updater remains compatible with prior builds and future higher-build updates. Installed-version reporting shows the real **Build 14**.

## Use

Create routines in **Train** and add movements from the searchable muscle/equipment-filtered library. Each exercise has ordered, numbered **SET / KG (or LB) / REPS** rows with independent weight and repetition fields. Valid edits save automatically; **Add Set** adds a row, and each row's menu can remove it or select **Use PR defaults**. New exercises start with two sets. Reps are optional and start blank when no target is saved; starting a routine copies each corresponding saved row into an unfinished workout set. Previously saved set counts and values stay intact.

Open an exercise's three-dot menu → **Edit Rest Timer** to set its rest duration for that exercise within that routine. The saved time appears near the exercise heading, such as **2:00 min rest**. The default and missing-value fallback are **2:00**, and the maximum is **5:00**. Explicit saved custom durations within that limit, including 1:30, are preserved. The same exercise can have different rest durations in different routines, and future workouts use the relevant routine's saved duration. You can also edit rest during a workout for subsequent rests; the timer's **−0:15 / +0:15** controls adjust rest already in progress. The default rest duration in Settings also saves automatically.

During a workout, tap the **Rest M:SS min** label beside **Add set** to edit that exercise instance's next rest. The fixed-colon editor opens with its configured duration, even when the countdown is idle; **Save** applies the override and **Cancel** discards the draft. This leaves the routine's default and any existing countdown unchanged. The override persists with the workout and is used after the next completed set. The bottom timer's independent controls adjust the live interval, whose green progress bar shrinks from full to empty and freezes while paused.

Only a routine whose saved ID matches the active workout's source shows **Resume active**. Other cards retain **Start** and protect the existing session, offering to resume it instead of overwriting it. Renaming, reordering and duplicate names do not change the link. Free workouts and older unfinished sessions that have no stored source ID remain available through the general **Resume workout** action; the app does not guess a legacy association from a name.

Start a routine or an empty session. Log decimal loads and reps, mark working/warm-up/assistance sets, complete or undo sets, and finish to view the full breakdown. Rounded, filled numeric fields retain their original sizes. Active sessions save every valid edit automatically and can be resumed after app/process restart. History supports search, set corrections, exercise changes, renaming and confirmed deletion. Custom exercises retain stable identities when renamed.

The offline catalog contains **287** established exercise variations across barbell, dumbbell, kettlebell, cable, bodyweight, Smith, selectorized and plate-loaded machines, plus a distinctly identified trap-bar deadlift. **Seated Overhead Press (Machine)** uses the existing selectorized shoulder-press ID, preserving its history and records. Ten genuine additions include EZ-bar exercises, barbell Bulgarian split squat, one-arm dumbbell bench press, bench-supported cable fly variations, cable shoulder press, belt squat and trap-bar deadlift. Both the library and picker recognize common equivalent names, abbreviations and punctuation/word-order differences, including shoulder/overhead press, pec deck/chest fly, hex/trap bar and skull crushers. Catalog refreshes add missing entries to existing installations without resetting data or deleting custom exercises; machine, cable, barbell and dumbbell variations keep separate identities.

## Build

The recovery build uses **versionName 1.4.1 / versionCode 13**. Its debug APK and generated metadata include `build13` in their release filenames/tag, without modifying the original `v1.4.1` release. See [UPDATES.md](UPDATES.md) for the build-tag publication option and [VERIFICATION.md](VERIFICATION.md) for compatibility checks and device-test limits.

Original **1.4.1 / version code 11** introduced rest-timer progress, an inline workout rest editor for subsequent rests, and persistent routine-source association so only the originating routine shows **Resume active**. These features and their original behavior are restored, while saved data from Build 12 remains supported.

Version **1.4.0 / version code 10** adds persistent per-set routine targets, inherited/custom row modes, exercise-menu rest editing and the expanded searchable catalog. Existing targets migrate as custom prescriptions with their exact values and set counts; use a row's **Use PR defaults** action to enable inheritance explicitly. The stable distribution retains the existing debug variant and signing identity.

Version **1.3.1 / build 9** shows one rest-timer dock above the footer only while a saved workout session is active. Finishing or discarding a session clears its countdown, alarm, sound and notification; restarting restores unfinished sessions and removes orphaned timers. Saved exercise rest durations and completed workout data are preserved.

Version **1.3.0 / build 8** introduced the supplied green arm/dumbbell launcher artwork, including transparent/adaptive/legacy/themed resources and dark splash settings. See [artwork/README.md](artwork/README.md) for source preservation and regeneration.

Completed workout summaries and history details include **Share workout**. The preview renders a 1080px-wide PNG from the selected saved workout, with its historical workout number, localized date and selected weight units, every completed exercise, actual best-set pairs, individual PR badges and summary totals. Long names wrap; exceptionally long workouts become ordered image pages with statistics intact. No failure indicators are invented. The card uses the app's English language, rather than the Portuguese sample text.

Sharing supports WhatsApp, WhatsApp Business and the standard Android Sharesheet through private-cache FileProvider URIs and temporary read grants. The user chooses recipients and confirms sending in the destination. This needs no contacts or broad storage permissions. Exports remain available after the preview closes and old managed exports are cleaned after seven days; sharing refreshes the retention timestamp. Preview generation and file decoding run off the UI thread. No backend or workout database changes are required.

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

Workouts copy exercise metadata and planned sets into independent snapshots. Renaming exercises or editing/deleting routines never rewrites past workouts. Records are derived from completed history instead of cached, so corrections and deletions recalculate both current records and each session's PR total. Room schemas v1 through v4 are exported to `app/schemas/`. The additive v1 → v2 migration creates PR synchronization checkpoints. The additive v2 → v3 migration creates the ordered `routine_sets` table and copies each legacy exercise's shared target pair into every planned row, preserving its original values and set count. Migrated rows are explicitly custom prescriptions, so initialization does not infer PR inheritance or overwrite them. The additive v3 → v4 migration adds only a nullable workout source routine ID. Legacy sources remain unknown, and routine deletion cannot cascade into workout history. Existing routine/workout tables remain in place; no destructive migration fallback is enabled.

Preferences and rest deadlines use DataStore. Timer state includes exercise, duration, deadline, paused remainder and a unique token. A new completed set restarts rest with that exercise's duration; editing an already completed set does not restart rest. Pause freezes the remainder; resume continues it; reset returns to the configured duration in a paused state. Rest includes background time and survives process restart. Completion is claimed once to avoid duplicate alerts. The in-app clock stays accurate when notification permission is denied. Android background notifications are used when permitted. On Android 12+, without special exact-alarm access the permission-free system alarm can arrive late under power saving; the in-app countdown and restored deadline remain correct. Wall-clock changes affect deadlines; reboot/force-stop cancels OS alarms and reopening the app restores the persisted timer.

The timer also persists the adjusted interval's duration separately from its original reset duration. Progress and the displayed countdown use the same clock. Accepted +/- adjustments change the interval's total while preserving elapsed time; remaining time stays within 0:00–5:00 and progress within 0–1. Reset restores the original full interval; skip/completion empty the bar. Old timer stores fall back to their saved duration and bounded remaining time. Navigation and exercise-rest editing never reset the countdown or its progress.

Automatic cloud backups and device-transfer extraction are disabled, including databases and preferences. Uninstalling the app removes local data. Routine-code sharing and database import/export are not implemented; workout image sharing remains local and needs no hosting service.

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

Persistent PR-based routine targets now operate per planned row. On workout completion, the repository selects the highest eligible working-set weight, then the highest repetitions at that weight, always copying both values from one actual set. A heavier set qualifies even with fewer reps or lower volume. Repetition improvements at the current record load update reps; lower-weight repetition records cannot replace that load. Bodyweight records keep the weight target blank. Warm-ups, assistance, unfinished sets and active/discarded workouts do not establish these targets. Stable exercise IDs and load conventions keep variations separate; all loads stay in canonical kg with existing kg/lb conversion tolerance.

Each row persistently records whether it **inherits PR defaults** or is a **custom target**. New/default rows can inherit the variation's current eligible record pair. Editing either weight or reps makes only that row custom; later PRs update inherited rows across routines while leaving custom prescriptions unchanged, including manually configured warm-up/back-off targets. Select **Use PR defaults** in a row's menu to opt that row back into current and future PR defaults. Explicit blank weight is a saved bodyweight prescription, separate from the inheritance flag. Legacy targets migrate as custom rows with all values/counts preserved; users can opt them into PR defaults explicitly. Synchronization runs through the repository after workout completion or initialization, never during UI rendering. Changes affect future sessions without modifying an active workout or completed snapshots.

New routine exercises and newly added or replaced workout exercises start with two sets. Existing routines retain their ordered rows and saved set counts, including three or more sets. Routine duplication copies every row's targets and inheritance mode, plus each exercise's rest duration. Starting a routine initializes each workout set from its corresponding planned row. Decimal weights are supported; reps accept positive whole values or remain blank before completion. Newly added routine rows inherit eligible PR defaults when available and otherwise start blank. Blank workout weight saves as zero external load; history describes it as Bodyweight without estimating body mass or adding it to volume.

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
