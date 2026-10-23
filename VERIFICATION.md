# Verification — 2026-10-23

## Release 1.4.3 / Build 14

The responsiveness update is distributed with versionName **1.4.3**, versionCode **14**, application ID **com.mooscles**, minimum API 26 and target API 35. Published release metadata was inventoried before preparation: the highest existing build was 13; v1.4.2 was already used, making v1.4.3 the next unused patch. The current release keeps the established debug distribution variant and trusted certificate. The optimized performance variant remains a local measurement target.

The versioned source passed `testDebugUnitTest`, `lintDebug`, `lintPerformance`, `assembleDebug`, `assembleDebugAndroidTest` and `assemblePerformance`. Results: **347 tests in 49 suites; zero failures/errors/skips**; debug lint **0 errors/21 warnings**; performance lint **0 errors/22 warnings**, including the documented API 29+ profiling attribute warning. Room schema v4/migrations, saved session formats, preference keys, application ID, signing identity, and updater source remain compatible.

Verified distribution APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.4.3-debug.apk` — **19,664,935 bytes**; SHA-256 **30aaee3083b362ead531158104778daa0f978b6903efe23bced615276c69077f**. The ordinary `app/build/outputs/apk/debug/app-debug.apk` contains identical bytes. Existing certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`; APK Signature Scheme v2 verifies successfully. `update.json` uses the unchanged seven-field schema and the exact APK filename, package, version, size and checksum; the adjacent `.sha256` agrees.

Local optimized test APK: `D:\Programing\Mooscles\app\build\outputs\apk\performance\app-performance.apk`. It is non-debuggable, R8-optimized, resource-shrunk, and profileable by shell on API 29+. No Android device is connected and the available emulator cannot boot; device profiling, installation, installed UI behavior and device-level data retention remain unverified. Host storage-transition tests do not substitute for device installation. Android may require source permission/installation confirmation.

Before publication, the frozen updater classes shipped by earlier versions passed **12 discovery cases**, one simulated future-update case and four identity-policy checks with the final Build 14 metadata. Builds 5, 11, 12 and 13 select Build 14 on the stable channel; Builds 14 and 15 reject it. The updater source is identical across those prior builds and the current update.

Publication uses the existing guarded helper: commit the intended source, normally push `main` and the unique `v1.4.3` tag, rerun checks on that exact clean commit, create a draft with APK/metadata/checksum, verify uploaded bytes, then explicitly publish with `draft=false`, `prerelease=false` and Latest. Public asset downloads, the latest-release endpoint and live old-updater discovery are verified after publication. Previous tags/releases and unrelated local IDE settings are preserved. Local evidence is recorded under `.tooling/release-v143-*`; no APKs, keys, credentials or machine-specific configuration enter the source commit.

## Responsiveness implementation validation — initial Build 13 (historical)

Baseline application source: `4c362999d3c33fdcb6cfd90ed86e16850e795d43`. The local update preserves application ID `com.mooscles`, versionName `1.4.1`, versionCode `13`, Room schema v4, saved preference keys, and the existing signing identity. The published recovery record below describes the earlier binary; these local optimization changes have their own rebuilt artifacts.

Routine start now uses five bulk SELECTs for both three and forty exercise occurrences, verified with real Room query callbacks. The former implementation required `3 + 2N` SELECTs (nine and eighty-three respectively, derived from its source). Each independent set and duplicate occurrence still receives its own saved identity and target pair inside the original transaction. Commands read focused rows and durable preferences, avoiding both full-archive command lookups and settings delayed by background UI projection. Broad observation retains a coherent seven-table snapshot; list comparison/deduplication and presentation preparation run off the main thread. Calculation-local indexes replace repeated archive filtering. History/record caches invalidate by actual saved content, units and catalog changes, while unchanged cards/sets retain their object identities.

The active screen is lazy per heading, individual set and footer. Saveable drafts retain blanks, invalid input, exact kg and optimistic completion through disposal, unit switches and delayed save acknowledgments. Timer progress is collected only by the dock; elapsed time has an isolated metric. Decorative segments use a background rather than a touch-intercepting Surface; the existing rest-dialog pointer tests and complete Activity workout flow pass unchanged in their assertions. Start coalesces repeated requests, reports loading/errors, and waits for the matching persisted session.

| Final check | Result |
| --- | --- |
| Full unit, Room, DataStore and Compose host suite | **PASS — 347 tests, 49 suites; zero failures/errors/skips** |
| Debug lint | **PASS — 0 errors, 21 existing warnings** |
| Optimized performance lint | **PASS — 0 errors, 22 warnings**; additional warning notes shell profiling requires API 29+ while normal app minimum remains API 26 |
| Debug, instrumented-test and optimized APK builds | **PASS** |
| Debug/optimized package and certificate | **PASS — com.mooscles; 1.4.1 / code 13; existing certificate; APK v2 signatures** |
| Optimized target flags | **PASS — R8 optimization/resource shrinking; non-debuggable; profileable by shell** |
| Comparable historical optimized APK | **PASS — baseline application source unchanged; identical measurement configuration** |
| Device startup, scroll/timer frame timing, PSS and repeated-cycle performance | **UNVERIFIED — no connected device; available emulator cannot boot** |
| Instrumentation execution and installed optimized behavior | **UNVERIFIED — instrumentation APK compiled only** |

Verified artifacts:

- Debug: `D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk` — 20,074,692 bytes; SHA-256 `02298110c2cebc932de920e3f5faf98339744076c7d936bd12fe5fdb1b6900cc`.
- Optimized: `D:\Programing\Mooscles\app\build\outputs\apk\performance\app-performance.apk` — 2,023,784 bytes; SHA-256 `310746fe0537bf311f8247cd7f12d485b17cf72b58983fff07beda5fd5ec4b57`.
- Instrumentation: `D:\Programing\Mooscles\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk` — 1,290,433 bytes; compiled opt-in fixture setup included.
- Historical optimized baseline: `D:\Programing\Mooscles\.tooling\performance-baseline-project\app\build\outputs\apk\performance\app-performance.apk` — 2,007,400 bytes; SHA-256 `b9df649cdd6ce4b1b36046d7178d9a8052d18989360af51cc02eb0cdd5c4eb39`.

Both application APKs retain certificate SHA-256 `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`. The build helper also produced matching local debug update metadata. Existing build-output filenames are reusable slots; historical hashes below identify earlier binaries rather than current slot contents. The unrelated pre-existing `gradle.properties` edit remains byte-for-byte unchanged.

No device performance conclusions are drawn from host tests, APK size or build success. [PERFORMANCE.md](PERFORMANCE.md) records all unavailable before/after metrics, typical/large deterministic fixtures, and matching optimized build/capture steps. Broad snapshots still include archived sets; runtime traces are needed to decide whether further data partitioning is warranted. Evidence: `.tooling/performance-full-tests.log`, `.tooling/performance-build-lint-checks.log`, `.tooling/performance-final-artifacts.json`, `.tooling/performance-final-tests.json` and the Gradle reports under `app/build/reports`.

## Published recovery release — 1.4.1 / build 13 (historical verification)

Restored functionality comes from original `v1.4.1` / Build 11, commit **87152e11925a221f828896c5dc082d09c10c6494**. This is a new forward commit after distributed v1.4.2 (`f5aa3f2096cb33b665ca815e3aaf65549f897a38`), never a reset or replacement of either release. The UI screens and repository match the historical source. Build 12's prepared-presentation cache, drag ordering window, saveable-draft additions and discard-to-Train navigation are removed.

The actual APK is **versionName 1.4.1 / versionCode 13**, with unique tag **v1.4.1-build13** and filename **Mooscles-v1.4.1-build13-debug.apk**. The maximum previously distributed code is 12 (all nine public releases inventoried; v1.0.0's code 3 verified from source and its APK). No connected test installation was present. Actual installed-version reporting reads the package and displays Build 13 rather than Build 11.

Compatibility differences from the original snapshot are limited to the newer build identity and preserved nullable timer exercise-instance ownership. The three timer classes, the optional `TimerUi` field and auto-rest's instance-ID argument keep Build 12's saved owner readable through timer adjustments, reset, reopening and cleanup. The timer's internal clear wrapper retains the same visible behavior. The publisher adds optional build-number tags and filenames, so the original v1.4.1 tag/assets remain intact.

Database entities, migrations, schema v4, core preferences and updater implementation are identical between original v1.4.1 and v1.4.2. Schema Git blob: `aa9f6dc6dc595072bf99034210e19555e937a660`; Room identity: `fa80dd71df6c40254d9d1b07ebd3f1a6`. No database downgrade, destructive migration, storage clearing or uninstall is used. Tests create a real database from the immutable distributed v1.4.2 schema and reopen it with the restored repository, preserving reordered duplicate instances, independent targets, precise weights, routines, history, PRs, checkpoints and partially completed sessions. Additional real-file tests read literal v1.4.2 training/updater/timer formats, including adjusted and paused intervals. Existing v1/v2/v3 → v4 migration checks remain in the full suite.

Actual public v1.4.1 and v1.4.2 APK disassembly was compared for eleven updater classes. Their discovery, metadata, identity validation, scheduling and installation logic match. The shipped updater selects the highest eligible **metadata versionCode**, independently of semantic versionName or tag ordering. Its frozen Build 12 classes accept this lower display version with code 13, reject mismatched code/name/certificate, and discover a simulated future Build 14. The original updater remains in the restored application; no discovery fix exists only in the new APK.

Final build command: `build.ps1` with bundled JDK 21 / Gradle 8.14.5.

| Check | Result |
| --- | --- |
| Full restored host unit, persistence, Compose and native graphics suite | **PASS — 325 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 existing warnings** |
| Debug APK and instrumented-test APK | **PASS — compiled and packaged** |
| Actual APK identity | **com.mooscles; versionName 1.4.1; versionCode 13; minimum API 26; target API 35** |
| Signing identity | **PASS — existing Android Debug certificate; APK Signature Scheme v2** |
| Versioned APK and updater metadata/checksum | **PASS — actual package/version, filename, size and SHA-256 agree** |
| Build 12 → restored saved-data transition | **PASS — real host SQLite/DataStore files; no destructive migration** |
| Captured installed-updater validation | **PASS — ten discovery scenarios, one future-update scenario and four identity checks** |
| Actual Android upgrade and restored installed UI | **Not verified — no connected device; isolated emulator could not boot** |

An actual installation was attempted as a verification setup, not claimed as completed: official Emulator 37.2.12 and Android 26 x86_64 were installed only inside ignored `.tooling`, with an isolated AVD. All three driver-free headless configurations crashed before guest boot (`0xC0000005` / exit -1073741819). The hypervisor check reports the Android Emulator hypervisor driver missing. No APK was installed, no app storage was cleared, and no host driver/system setting changed. Therefore actual Build 12 → 13 installation, installed UI behavior and instrumented execution remain unverified. Host storage-transition and discovery validation do not substitute for a device installation claim.

Verified local APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.4.1-build13-debug.apk` — **19,632,163 bytes**. SHA-256: `9cae0fb07af6e7e2849469eb01a0a010cc0a0c3524a2884555a82b29fe711eab`. Trusted certificate SHA-256 remains `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`. Normal and versioned debug APKs have identical bytes. APKs, metadata, keystores and local configuration remain excluded from source.

Distribution: [v1.4.1-build13 full GitHub Release](https://github.com/ReiTut/Mooscles/releases/tag/v1.4.1-build13), explicitly `draft=false`, `prerelease=false` and Latest. The existing helper validates the exact clean tagged commit, verifies downloaded draft assets before publication, and uses `-UseBuildTag` for this unique recovery identity. Public verification checks Latest, downloaded APK/metadata/checksum and captured Build 12 stable-channel discovery. Existing download preferences and Android installation approval still apply; ongoing training delays installation.

## Prior release — 1.4.2 / build 12

The workout presentation worker prepares every exercise, ordered set, target, rest value, previous-performance summary and record badge before publishing an interactive new or restored session. Database snapshots remain transactional; expensive presentation uses `Dispatchers.Default`. Indexed history/set lookups replace repeated whole-table scans. Bounded caches reuse unchanged workout/card/set objects; timer/updater copies and the isolated elapsed clock do not rebuild the exercise list. Lazy rendering keeps composition bounded, with saveable input drafts scoped to persistent exercise/set IDs.

After a successful discard, the command waits for durable session deletion, timer clearing and refreshed no-active presentation before emitting navigation success. Train becomes selected, live/saved Workout destinations are cleared, and Back or app restoration cannot reopen the deleted session. Failed database deletion or timer clearing shows an error without success navigation. Discarded provisional records do not enter completed history or update routine targets. Alerts and notifications are cancelled.

Both routine and active-workout screens have **Add Exercise / Reorder Exercises** controls. The separate compact ordering window keeps the full editor mounted behind it, supports drag handles with held-edge autoscroll, accessible moves, Save Changes and Cancel/Back. Reordering validates exact instance membership and updates positions atomically. Duplicate catalog movements keep independent sets, targets, rest settings and progress. Routine target editing retains its existing autosave behavior; only confirming the ordering window saves its order. Active reordering changes only the session, with no changes to routine/history snapshots or countdown state. A nullable DataStore exercise-instance owner supplements the timer's workout ID; legacy timers keep a null owner rather than guessing by name. Room schema v4 is unchanged.

Final command: `build.ps1`, using bundled JDK 21 and Gradle 8.14.5.

| Check | Result |
| --- | --- |
| Complete host unit, persistence, Compose and native graphics suite | **PASS — 351 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 existing warnings** |
| Debug APK and instrumented-test APK | **PASS — compiled and packaged** |
| Actual APK identity | **com.mooscles; version 1.4.2; version code 12; minimum API 26; target API 35** |
| Signing identity | **PASS — existing Android Debug certificate retained; APK Signature Scheme v2** |
| Versioned APK, update.json and checksum | **PASS — package/version, filename, size and SHA-256 agree; normal debug APK has identical bytes** |
| Ordering visual review | **PASS — native 360dp and 430dp captures; duplicate/long names, handles and Save/Cancel remain readable** |
| Real Android device, IME, frame timing and instrumented execution | **Not run — no connected device and no local emulator binary available** |

Forty focused tests cover complete worker preparation and restoration, exact compatibility with the previous PR/history presentation (including randomized/tied/near-equal load cases), cache identity and invalidation, repeated fast scrolling and input restoration, discard navigation and storage failures, atomic ordering/rollback, duplicates, future routine order, reopened session order and persistent timer ownership. Actual ViewModel tests confirm that preparation runs on a DefaultDispatcher worker, and that auto-rest retains the exact repeated exercise instance through reorder and ViewModel recreation.

Host CPU benchmark: **30 historical sessions, 48 exercises per session, 5,952 sets**. The final run measured **8,482.5 ms** for the previous preparation and **51.6 ms** for indexed preparation. **120 cached snapshots took 94.4 ms total and built zero extra cards**. See `app/build/reports/workout-preparation-benchmark.txt`. This is a synthetic host CPU comparison, not device startup or frame-rate timing.

The 40-exercise/160-set Compose test performs **24 fast swipes and six end-to-end jumps**, verifies bounded composition and offscreen inputs, then forces actual saved-state disposal/restoration and confirms an unpersisted invalid numeric draft survives that restoration and saved reorder. The final run's functional scrolling loop took **2,834 ms of host wall time**; this is not an Android frame/jank measurement. See `app/build/outputs/workout-performance/scroll-host.txt`. The held-edge ordering test moves beyond twelve positions from the original viewport while holding the pointer stationary, then confirms all thirty distinct instance IDs remain in the saved order.

Native screenshots under `app/build/outputs/workout-order-previews/` (`order-360dp.png`, `order-430dp.png`) were inspected. Existing native workout/dock previews also pass. No device scrolling, keyboard or installation claim is made from these host checks.

Verified APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.4.2-debug.apk` — **19,697,699 bytes**. SHA-256: `739a637f5489b1b2a88878b920f6c06ad0d0229e6ab29899c95f0d15784db931`. Signing certificate SHA-256 remains `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`. Build outputs, metadata, local configuration and signing material remain ignored.

Distribution: [v1.4.2 full GitHub Release](https://github.com/ReiTut/Mooscles/releases/tag/v1.4.2), with the existing debug variant, update.json and checksum as assets. The publication helper verifies the exact clean tagged source, checks downloaded draft assets, then explicitly publishes with `draft=false`, `prerelease=false` and Latest status. Stable updater discovery uses version code 12; downloads respect saved preferences and Android may require installation approval. Public verification checks the latest-release endpoint, downloaded asset bytes, package identity, signing certificate and metadata.

## Prior release — 1.4.1 / build 11

The floating rest timer now shows a green determinate progress bar. Countdown and progress use the same clock. DataStore persists the adjusted interval basis separately from the original reset duration: accepted +/- changes preserve elapsed time, pause freezes the fraction, resume continues it, reset restores the original full interval, and completion/skip empty the bar. Remaining time stays within 0:00–5:00 and progress within 0–1. Tests cover custom durations, fractional seconds, capped/negative adjustments, legacy stores, real file reopening, background time, controller recreation and session cleanup.

The **Rest M:SS min** action beside **Add set** opens a focused fixed-colon Save/Cancel editor for that workout-exercise instance's configured duration. It never reads the bottom timer's remainder or issues countdown commands. Saving updates the inline label and persists the next-rest override; cancelling discards the draft. Other exercise instances and routine defaults remain unchanged. The dialog is owned outside the lazy list so scrolling, card disposal or a smaller keyboard-sized viewport cannot destroy its draft; removing/replacing the selected exercise safely closes it.

Routine-based workouts now persist their stable source routine ID. Only that routine card shows **Resume active**; duplicate names, renames and reordering cannot move the association. Other Start actions protect the active session and offer an explicit resume. Free workouts and legacy sessions with unknown sources use the general Resume workout action. Room v4 adds only nullable `workouts.sourceRoutineId`, with no cascade or guessed backfill. Real v1/v2/v3 → v4 migrations preserve existing plans, targets, history and active snapshots.

Final command: `build.ps1` using bundled JDK 21 and Gradle 8.14.5.

| Check | Result |
| --- | --- |
| Full host unit, persistence, Compose and native graphics suite | **PASS — 311 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 existing warnings** |
| Debug APK and instrumented-test APK | **PASS — compiled and packaged** |
| APK identity | **com.mooscles; version 1.4.1; version code 11; minimum API 26; target API 35** |
| APK signing compatibility | **PASS — same existing Android Debug certificate; APK Signature Scheme v2** |
| Versioned APK, update.json and checksum | **PASS — actual version, filename, size and SHA-256 agree; normal APK has identical bytes** |
| Native layout review | **PASS — inline label/progress, long names, scrolling and constrained 550dp app viewport; dock and footer remain outside exercise rows** |
| Physical keyboard, device installation and instrumented execution | **Not run — no connected device; emulator hypervisor driver unavailable** |

The 33 additional tests cover the new timer state/persistence/controller behavior, workout-source migration and reopening, independent exercise-rest overrides, stable-ID card actions, dialog interactions, lazy-card disposal and native layout. The real Activity → Room → workout → rest → history test changes the next rest during a running interval, asserts the live state is unchanged, completes another set to verify the new duration, and verifies final timer cleanup plus the unchanged routine default. ViewModel recreation preserves source ID, elapsed start time, sets, rest override and countdown.

Native graphics previews are under `app/build/outputs/rest-timer-previews/`: `inline-label-and-progress.png`, `long-exercise-name-and-timer.png`, and `rest-label-constrained-viewport.png`. These were inspected. Focus, numeric input, fixed-colon advancement, validation, cancellation, saving and dialog survival are exercised by legacy-graphics Compose tests. Native focused-dialog capture is unavailable because that graphics mode does not become idle in this host. The constrained viewport simulates keyboard space and does not display a real IME.

Verified APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.4.1-debug.apk` — **20,032,522 bytes**. SHA-256: `63aae24da5be85a99cf1e99c8668f154f0d4350e0861a5dc68079d09a248b70a`. Signing certificate SHA-256 remains `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`. Generated metadata/checksum and build outputs remain ignored.

Distribution: [v1.4.1 full GitHub Release](https://github.com/ReiTut/Mooscles/releases/tag/v1.4.1), with the existing debug APK, update.json and checksum as assets. The publication helper rebuilds/checks the exact tagged commit, verifies downloaded draft assets, then explicitly publishes with `draft=false`, `prerelease=false` and Latest status. Stable discovery uses version code 11; automatic download/install respects saved preferences and Android approval. Publication verification checks the latest-release endpoint and independently downloads the public assets.

## Prior release — 1.4.0 / build 10

Routine targets now persist as ordered individual set rows. New exercises have two inherited rows; editing either value makes the whole row custom. Row menus can restore PR defaults or remove a set. Future eligible records update inherited rows using both values from one actual completed working set. Custom prescriptions, including explicit blank bodyweight, remain unchanged. Historical record corrections/deletions refresh inherited defaults on initialization/finalization without stale checkpoint suppression.

Room schema v3 adds only `routine_sets`. Real on-disk v1 → v3 and v2 → v3 tests verify that every legacy target pair and set count is copied exactly into custom rows, retaining rest durations and independent completed/active snapshots. Reopening, duplication and future workout initialization preserve each corresponding row and inheritance mode. Routine edits never rewrite an active workout or completed performance.

Exercise menus include **Edit Rest Timer**, with the existing fixed-colon editor, two-digit seconds and 0:00–5:00 validation. Rest is saved per routine exercise and displayed near its heading. Missing/new values use 120 seconds, and saved custom durations within the existing limit are preserved. Adjustment buttons reset the input mask to the adjusted value, including after an invalid typed draft.

The catalog contains 287 distinct entries. The existing selectorized shoulder-press ID is renamed to **Seated Overhead Press (Machine)**. Ten genuine missing variations and shared alias search are available to existing installations without resetting custom exercises, load conventions, records or snapshots.

Final command: `build.ps1 -Tasks @(':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest')` with the bundled JDK 21 and Gradle 8.14.5.

| Check | Result |
| --- | --- |
| Full host unit, persistence, native graphics and Compose suite | **PASS — 278 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 existing warnings** |
| Debug APK and instrumented-test APK | **PASS — compiled and packaged** |
| Actual APK identity | **com.mooscles; version 1.4.0; version code 10; minimum API 26** |
| APK signing compatibility | **PASS — existing certificate, APK Signature Scheme v2** |
| Release copy, generated metadata and checksum | **PASS — identical APK bytes and matching size/SHA-256** |
| Native visual inspection | **PASS — independent example rows, multiple exercises, wrapped long names, focused fields and accessible Add Set in a verified 360dp viewport** |
| Physical device installation and real keyboard | **Not run — no connected device; emulator hypervisor driver unavailable** |

The 13 focused editor tests include independent input, autosave ordering, exact kg behind rounded lb, explicit bodyweight/custom behavior, PR-default restoration, replacement, add/remove limits, rest controls and constrained scrolling. A native graphics test renders four previews under `app/build/outputs/routine-editor-previews/`. The constrained viewport simulates space above a keyboard; it does not display a real Android IME. The full Activity → Room → workout → rest timer → history test and updated instrumented test source use the new per-set editor. Instrumented tests were compiled but could not be executed without a device.

Verified APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.4.0-debug.apk` — **19,599,399 bytes**. SHA-256: `2f5162ad32d193194708266fa970ebec07541b95999e1aab0e1a02a9e70b41db`. The normal debug APK has identical bytes. `update.json` and `Mooscles-v1.4.0-debug.apk.sha256` were generated from that APK. Signing certificate SHA-256 remains `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`. Outputs/signing material remain ignored.

Distribution: [v1.4.0 full GitHub Release](https://github.com/ReiTut/Mooscles/releases/tag/v1.4.0), with the existing debug APK, update.json and checksum as assets. The existing publication helper rebuilds/checks the exact tagged commit, downloads and verifies draft assets, then explicitly publishes with `draft=false`, `prerelease=false` and Latest status. Stable discovery uses the existing updater and version code 10; automatic download/install remains subject to saved preferences and Android approval. Publication verification also checks the latest-release endpoint and downloads the public assets independently.

## Prior release — 1.3.1 / build 9

The timer uses the persisted active workout to control visibility and lifecycle. One dock remains above the footer across routes and scrolling. Finishing/discarding a session, orphaned startup state and stale receivers clear countdowns and cancel alarms, sounds and notifications. Unfinished sessions retain their timer; saved rest durations, workout data and Room schema v2 are unchanged.

| Check | Result |
| --- | --- |
| Host unit, persistence, native graphics and Compose tests | **PASS — 246 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 existing warnings** |
| Debug APK and instrumented-test APK | **PASS — compiled and packaged** |
| Actual APK identity | **com.mooscles; version 1.3.1; version code 9; minimum API 26** |
| Signature | **PASS — existing certificate retained, APK Signature Scheme v2** |
| Routine-code/hosting components | **Absent from source and final APK** |
| Physical device installation | **Not run — no connected device; emulator hypervisor driver unavailable** |

Ten focused timer tests were added: three on-disk persistence cases, four controller lifecycle/alarm/notification cases and three Compose visibility/placement cases. The full workout flow also verifies durable cleanup after completion. Its test resets AndroidX's process-wide ViewModel factory before Activity creation and after teardown, observes the Activity's actual application/database and verifies it matches the ViewModel. This prevents another Robolectric application's cached factory from leaking between tests; production code is unchanged by this test-isolation fix.

The full test suite passed through `build.ps1 -Tasks @(':app:testDebugUnitTest')`; lint and both APKs passed through `build.ps1 -Tasks @(':app:lintDebug', ':app:assembleDebug', ':app:assembleDebugAndroidTest')`. Existing workout-image sharing remains packaged. New routine-code and hosting work was removed as requested.

Verified APK: `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.3.1-debug.apk` — **19,566,627 bytes**. SHA-256: `37a70601f4b88932ffe4e3bb0fc3bca24a30a915bf592e69791104b2294e4f26`. The normal debug APK has identical bytes. Generated update.json and checksum files match the APK; outputs and credentials remain ignored. The existing signing certificate SHA-256 is `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.

Distribution uses a full GitHub Release marked Latest, with the existing debug variant and signing identity: [v1.3.1](https://github.com/ReiTut/Mooscles/releases/tag/v1.3.1). APK, update.json and checksum are attached as release assets. The publication helper verifies draft assets before publishing explicitly with `draft=false`, `prerelease=false` and Latest status. The sections below document the prior 1.3.0 baseline.

## Prior 1.3.0 baseline

Release build: **Mooscles 1.3.0**, Android version code **8**, application ID `com.mooscles`. The existing Android Debug signing identity and Room schema v2 are retained. Icon replacement and workout sharing do not change saved workout tables or preferences. The official stable distribution uses the verified debug APK, generated update.json and SHA-256 checksum; release channel and build type are independent.

Final command: `./build.ps1`, using Gradle 8.14.5 and bundled Adoptium JDK 21.

| Check | Result |
| --- | --- |
| Host unit, persistence, native graphics and Compose tests | **PASS — 236 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 21 warnings (19 dependency advisories and 2 compatibility/resource advisories)** |
| Debug APK | **PASS** |
| Instrumented test APK | **PASS — compiled and packaged** |
| APK signature | **PASS — APK Signature Scheme v2; same existing certificate** |
| Generated release APK and update.json | **PASS — actual package/version/minSdk/size/SHA-256 agree** |
| APK freshness | **PASS — no production source/build inputs newer than final APK** |
| Real on-disk Room v1 → v2 migration | **PASS — old plans, history and active snapshots preserved; existing PRs backfilled** |
| Device upgrade / real installation | **Not run — no connected device; emulator hypervisor driver unavailable** |

## v1.3.0 launcher icon and workout sharing

The supplied JPG is copied unchanged into `artwork/`. The final transparent foreground retains the actual source RGB pixels, including dumbbell highlights, while removing the edge-connected neutral checkerboard. Circular, rounded-square and monochrome mask previews plus a dark splash layout were inspected. Actual APK resource inspection confirms an adaptive launcher XML with background, foreground and monochrome layers, the correct manifest icon/roundIcon references, and five-density legacy PNGs. Adaptive XML remains in `mipmap-anydpi-v26` alongside legacy PNGs so the APK selects it correctly; the resulting ObsoleteSdkInt warning is retained. The other compatibility warning concerns the inlined API-29 LineBreaker constant used by the API-23 StaticLayout builder, not a runtime call to a newer API.

Finished summaries and history details expose Share workout. A saved snapshot feeds the same existing duration/volume/PR calculations into a 1080px native Canvas export. The preview precedes every explicit destination action. WhatsApp, Business, fallback chooser, PNG MIME type, single/multiple streams, scoped FileProvider URIs, temporary read grants, cache retention renewal and error/retry/close behavior are host-tested. No contacts or storage permissions were added, and the APK retains its existing package/signing identity.

Thirty-seven focused sharing tests were added: 19 card data/best-set/bodyweight/PR tests, 5 native renderer/pagination/cache tests, 6 intent/provider/retention tests, 6 preview/action tests, and 1 real Room-to-native-PNG integration test. All prior 199 tests continue to pass. The integration sample derives workout #2, two bench sets, three bodyweight sets, 22 kg × 6, Bodyweight × 12, 44:00, 272 kg and two PR exercises from actual saved sample history; export leaves the saved snapshot unchanged.

Visual review covered `representative-kg.png`, short/multiple-PR cards, wrapped long names in pounds, and all three pages of a 48-exercise workout, including the final statistics. Files are under `app/build/outputs/share-previews/`; icon previews are under `app/build/outputs/icon-previews/`. These are synthetic test data, not private user workouts. Physical launcher/splash display, Android recipient selection and WhatsApp/Business behavior were not tested on a device. See [SHARING.md](SHARING.md) and [artwork/README.md](artwork/README.md).

## v1.2.0 routine targets and rest defaults

Completed eligible working-set records now persist their actual weight/repetition pair to every routine referencing the same exercise ID and load convention. Highest eligible weight takes priority; the best repetitions at that load come from the same real set. Same-weight repetition improvements advance the target; lower-weight repetitions do not replace the record load. Bodyweight repetition records save a null weight target, displayed blank. Warm-ups, assistance, unfinished sets, active workouts and discarded workouts are excluded.

The repository synchronizes existing history on feature initialization and new records atomically with workout completion. Durable consumed-record checkpoints preserve later manual target edits until a newer qualifying PR. New/replaced routine references inherit eligible records. Saved target weight and reps appear in the routine editor, ViewModel presentation and future unfinished workout sets. Existing routine set counts/order/rest durations, active workouts and completed snapshots remain unchanged. Routine plans have one standard working-set prescription per exercise, without separate per-set warm-up/back-off prescriptions.

The default rest is 120 seconds / **2:00** across repository creation paths, preferences, UI models, timer initialization and missing-value fallbacks. Explicit saved durations including 90 and 135 seconds remain unchanged. The fixed colon, two seconds digits and 300-second maximum remain intact. Empty plans and newly added sets retain blank reps; routine workouts now initialize reps from saved targets.

Thirty-six focused tests were added: 13 selector/policy tests, 12 repository persistence tests, one real schema migration test, four editor tests, one actual Room → ViewModel → future workout test, and five rest-default/preservation tests. Existing UI-flow expectations were updated for saved rep targets. Final verification executed the full 199-test suite, lint, debug assembly and instrumented-test APK assembly successfully.

## v1.1.1 PR-counter correction

“Exercises with PRs” includes every distinct exercise variation with either first records or records beaten. Multiple Weight PR/Reps PR badges and multiple load-specific records for one exercise still count once. The first-record labels and separate beaten-record categories remain intact.

The screenshot regression reproduces three first-record badges (bench Weight PR, bench Reps PR, bodyweight wide-grip pull-up Reps PR), five completed sets and 560 kg total volume. The domain summary, displayed Material 3 metric and real Room-to-ViewModel saved/reopened result all show **2 exercises with PRs**. Empty records show zero; mixed first/beaten records and duplicate badges preserve distinct exercise counts.

Existing saved workouts recalculate their corrected totals when reopened. No Room migration, history rewrite or reset is needed. Five new focused tests cover this correction; all earlier updater and workout tests still pass.

## Updater behavior

- Official public GitHub Releases API for `ReiTut/Mooscles`, without embedded credentials or workout uploads. Stable releases are the default in v1.1.0; prereleases require an explicit Settings preference. Drafts are always excluded. Promoting v1.1.1 to stable makes its existing version-code-6 metadata eligible for version-code-5 v1.1.0 installations without enabling prereleases or replacing the updater.
- Foreground checks are throttled to one hour; manual checks are throttled to 30 seconds. Persistent GitHub rate-limit deadlines take precedence. ETag response caches survive restart; temporary failures use backoff.
- Unique WorkManager 2.10.5 periodic work checks about every six hours with a network constraint. Unique download work respects Wi-Fi-only preferences (unmetered Wi-Fi by default), persists progress, and supports cancellation/retry. Interrupted transfers restart safely into private temporary files and reverify the complete download.
- Update selection uses Android versionCode and strict update.json metadata. It selects only the named APK with matching asset size, then verifies downloaded SHA-256, package identity, version, minimum SDK and compatible signing certificates before staging it for installation.
- PackageInstaller stages, persists and commits a single session. Eligible Android self-updates request installation without user action; required approval is recorded and shown through Settings/notification. The app only opens source permission or system confirmation after a user gesture. Cancellation and failed sessions wait for user retry.
- New training activity and installation share a gate. Installation waits for an active workout to be saved and a running or paused rest timer to stop. An already expired timer restored by a background worker does not block an idle app. Approval becomes available again after training, including with automatic updates disabled.
- Startup and MY_PACKAGE_REPLACED reconcile the installed version and remove obsolete updater files/state. Recovery abandons orphaned uncommitted self-update sessions. Cleanup does not touch Room, training preferences, history, saved active workouts or timers.
- The Material 3 Settings section shows installed/available versions, last check, preferences/channel, notes, download progress, readiness, approval, success and failure states.

## Focused tests

The earlier 97 workout/timer/persistence/UI tests and 66 updater tests still pass. The new target/default feature adds 36 tests:

| Suite | Tests |
| --- | ---: |
| Releases, metadata, versionCode, channels, ETag, HTTP failures and rate limits | 15 |
| APK contents, identity and signing compatibility | 10 |
| Android installation status policy | 3 |
| System approval validation and session lifecycle | 5 |
| Actual manager upgrade cleanup/data preservation/permission/callback/recovery | 6 |
| Actual DataStore persistence, check serialization, retry state and deferral | 18 |
| Actual WorkManager uniqueness, six-hour interval, network constraints and cancellation | 3 |
| Material 3 update interface actions and retry states | 4 |
| Running, paused and expired rest installation guards | 2 |
| **New updater tests** | **66** |
| PR routine synchronization, migration, editor and rest-default tests | 36 |
| Launcher/share feature tests | 37 |
| **Total including existing tests** | **236** |

Upgrade preservation tests use real on-disk Room databases and DataStore files: saved routines with explicit set counts, completed history and derived PRs, active workouts, exercise settings, preferences and timer deadlines survive simulated installed-version changes and update cleanup. These tests do not substitute for an actual Android package replacement.

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/`
- `app/build/reports/lint-results-debug.html`

## Verified delivery assets

**APK:** `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.3.0-debug.apk`

**Metadata:** `D:\Programing\Mooscles\app\build\outputs\release\update.json`

- Package: `com.mooscles`; version `1.3.0`; version code `8`
- Size: **19987998 bytes**
- APK SHA-256: `183dbda9c3dfe81a31a7699175b1177fa1a8763b25da91e19db781251a95cbfc`
- Existing debug certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`
- Android 8.0 / API 26 or newer; compile/target API 35
- The normal `app/build/outputs/apk/debug/app-debug.apk` has identical bytes.
- Adjacent `Mooscles-v1.3.0-debug.apk.sha256` supports manual verification.

APKs, generated metadata, build outputs, SDK paths, keystores, passwords and credentials remain excluded from source control.

## Release process and prerequisites

`build.ps1` generates the versioned APK, update.json and checksum after successful debug assembly. `tools/prepare-release.ps1` verifies the actual APK, existing signer and increasing version code; negative checks reject a wrong package, signer or reused version code. `tools/publish-release.ps1` requires a clean tested source commit and exact local/remote tag, reruns checks, prepares and verifies all uploaded assets, and defaults to a draft official release as explicitly requested by the owner. Publishing requires its explicit `-Publish` option and marks the official release Latest. The release workflow also independently downloads and verifies uploaded assets before publication. APK preparation reports its debug build type separately from the distribution channel.

The older v1.0.0 prerelease has an APK and checksum but no update.json; the updater safely skips such older releases. The v1.1.1 distribution includes the APK, generated update.json and checksum. Future releases also need both the exact versioned APK and its generated update.json. The owner explicitly authorized promoting v1.1.1 to the official stable/latest GitHub release while retaining these exact assets and their debug signing identity for compatibility. Stable releases are eligible on v1.1.0's default update channel without enabling Include prereleases. GitHub release promotion is separate from the tested source tag and does not rebuild the APK, move the tag or establish production signing. See [UPDATES.md](UPDATES.md).

Promotion verification confirmed `draft=false`, `prerelease=false`, and v1.1.1 as the repository's latest release. The exact existing APK and update metadata were downloaded and independently checked for size, SHA-256, package/version and signing identity. The local build/check workflow passed again with unchanged outputs; the 163-test reports still have zero failures, errors or skips, and lint has zero errors. The v1.1.1 source tag still points to tested commit `a01ea38cb4a423726d98a1ab1fceb8f47997a55a`.

A live unauthenticated check using the actual compiled ReleaseClient (identical to v1.1.0's source) selected stable v1.1.1/code 6 for installed v1.1.0/code 5 with `includePrereleases=false`. Installed codes 6 and 7 were offered no update or downgrade. All three checks returned no diagnostics. This verifies public release discovery and compatibility metadata, not a physical-device installation. Automatic updates still respect saved preferences, user cancellations, Wi-Fi requirements and Android installation approval.

An installed version without the updater must install this updater-enabled APK once. Future updates then use the selected channel and preferences. Android may require Allow from this source and installation confirmation. Six-hour scheduling is approximate, not immediate release delivery.

## Checks unavailable on this machine

The device inventory was empty, and the installed emulator reported **Android Emulator hypervisor driver is not installed on this machine**. A real APK replacement, Android source-permission screen, system confirmation, OS background timing, notifications and device rendering could not be exercised. Installation state transitions, persistence, signing policy, training deferral and scheduling are host-tested; physical upgrade validation remains required on a suitable device.

After connecting an authorized Android device or enabling emulator acceleration, run:

```powershell
./build.ps1 -Tasks ':app:connectedDebugAndroidTest'
```

Exercise a future higher-versionCode APK signed with the same key and containing valid update.json through a test GitHub Release/channel. Never uninstall the app, clear storage or change the signing key to simulate success.
