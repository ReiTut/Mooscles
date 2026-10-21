# Verification — 2026-10-21

Current local build: **Mooscles 1.1.0**, Android version code **5**, application ID `com.mooscles`. The existing Android Debug signing identity and Room schema v1 are retained. No destructive migration or data reset was introduced.

Final command: `./build.ps1`, using Gradle 8.14.5 and bundled Adoptium JDK 21.

| Check | Result |
| --- | --- |
| Host unit, persistence and Compose tests | **PASS — 158 tests; zero failures, errors or skips** |
| Android lint | **PASS — 0 errors; 19 dependency-version advisories** |
| Debug APK | **PASS** |
| Instrumented test APK | **PASS — compiled and packaged** |
| APK signature | **PASS — APK Signature Scheme v2; same existing certificate** |
| Generated release APK and update.json | **PASS — actual package/version/minSdk/size/SHA-256 agree** |
| APK freshness | **PASS — no production source/build inputs newer than final APK** |
| Public GitHub Releases API | **PASS — unauthenticated request succeeded** |
| Device upgrade / real installation | **Not run — no connected device; emulator hypervisor driver unavailable** |

## Updater behavior

- Official public GitHub Releases API for `ReiTut/Mooscles`, without embedded credentials or workout uploads. Stable releases are the default; prereleases require an explicit Settings preference. Drafts are always excluded.
- Foreground checks are throttled to one hour; manual checks are throttled to 30 seconds. Persistent GitHub rate-limit deadlines take precedence. ETag response caches survive restart; temporary failures use backoff.
- Unique WorkManager 2.10.5 periodic work checks about every six hours with a network constraint. Unique download work respects Wi-Fi-only preferences (unmetered Wi-Fi by default), persists progress, and supports cancellation/retry. Interrupted transfers restart safely into private temporary files and reverify the complete download.
- Update selection uses Android versionCode and strict update.json metadata. It selects only the named APK with matching asset size, then verifies downloaded SHA-256, package identity, version, minimum SDK and compatible signing certificates before staging it for installation.
- PackageInstaller stages, persists and commits a single session. Eligible Android self-updates request installation without user action; required approval is recorded and shown through Settings/notification. The app only opens source permission or system confirmation after a user gesture. Cancellation and failed sessions wait for user retry.
- New training activity and installation share a gate. Installation waits for an active workout to be saved and a running or paused rest timer to stop. An already expired timer restored by a background worker does not block an idle app. Approval becomes available again after training, including with automatic updates disabled.
- Startup and MY_PACKAGE_REPLACED reconcile the installed version and remove obsolete updater files/state. Recovery abandons orphaned uncommitted self-update sessions. Cleanup does not touch Room, training preferences, history, saved active workouts or timers.
- The Material 3 Settings section shows installed/available versions, last check, preferences/channel, notes, download progress, readiness, approval, success and failure states.

## Focused tests

The existing 92 workout, timer, Room, preferences and UI tests still pass. The updater adds 66 tests:

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
| **Total including existing tests** | **158** |

Upgrade preservation tests use real on-disk Room databases and DataStore files: saved routines with explicit set counts, completed history and derived PRs, active workouts, exercise settings, preferences and timer deadlines survive simulated installed-version changes and update cleanup. These tests do not substitute for an actual Android package replacement.

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/test-results/testDebugUnitTest/`
- `app/build/reports/lint-results-debug.html`

## Verified delivery assets

**APK:** `D:\Programing\Mooscles\app\build\outputs\release\Mooscles-v1.1.0-debug.apk`

**Metadata:** `D:\Programing\Mooscles\app\build\outputs\release\update.json`

- Package: `com.mooscles`; version `1.1.0`; version code `5`
- Size: **19344097 bytes**
- APK SHA-256: `25ee56ae9bbcf5fa15cc8923378bdef4febdd59c580196c7c3574b036fbee261`
- Existing debug certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`
- Android 8.0 / API 26 or newer; compile/target API 35
- The normal `app/build/outputs/apk/debug/app-debug.apk` has identical bytes.
- Adjacent `Mooscles-v1.1.0-debug.apk.sha256` supports manual verification.

APKs, generated metadata, build outputs, SDK paths, keystores, passwords and credentials remain excluded from source control.

## Release process and prerequisites

`build.ps1` generates the versioned APK, update.json and checksum after successful debug assembly. `tools/prepare-release.ps1` verifies the actual APK, existing signer and increasing version code; negative checks reject a wrong package, signer or reused version code. `tools/publish-release.ps1` requires a clean tested source commit and exact local/remote tag, reruns checks, prepares and verifies all uploaded assets, and defaults to a draft prerelease. Publishing requires its explicit `-Publish` option. The release workflow also independently downloads and verifies uploaded assets before publication.

The older v1.0.0 prerelease has an APK and checksum but no update.json; the updater safely skips such older releases. The v1.1.0 distribution includes the APK, generated update.json and checksum. Future releases also need both the exact versioned APK and its generated update.json. Debug distributions must remain prereleases, so users receiving them must enable Include prereleases. See [UPDATES.md](UPDATES.md).

An installed version without the updater must install this updater-enabled APK once. Future updates then use the selected channel and preferences. Android may require Allow from this source and installation confirmation. Six-hour scheduling is approximate, not immediate release delivery.

## Checks unavailable on this machine

The device inventory was empty, and the installed emulator reported **Android Emulator hypervisor driver is not installed on this machine**. A real APK replacement, Android source-permission screen, system confirmation, OS background timing, notifications and device rendering could not be exercised. Installation state transitions, persistence, signing policy, training deferral and scheduling are host-tested; physical upgrade validation remains required on a suitable device.

After connecting an authorized Android device or enabling emulator acceleration, run:

```powershell
./build.ps1 -Tasks ':app:connectedDebugAndroidTest'
```

Exercise a future higher-versionCode APK signed with the same key and containing valid update.json through a test GitHub Release/channel. Never uninstall the app, clear storage or change the signing key to simulate success.
