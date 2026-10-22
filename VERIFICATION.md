# Verification — 2026-10-22

## Current release — 1.3.1 / build 9

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
