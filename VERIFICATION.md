# Verification — 2026-10-21

Project: [ReiTut/Mooscles](https://github.com/ReiTut/Mooscles)

Final full verification command: `./build.ps1` (Gradle 8.14.5, Adoptium JDK 21) for release **v1.0.0**, version name **1.0.0**, version code **3**. The APK and reports below reflect the tested source. This release is a debug prerelease and retains the existing application ID and debug signing configuration.

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | **PASS — 52 tests, zero failures/errors/skips** |
| `:app:lintDebug` | **PASS — 0 errors, 17 dependency-version update advisories** |
| `:app:assembleDebug` | **PASS** |
| `:app:assembleDebugAndroidTest` | **PASS — instrumented test APK compiled and packaged** |
| Debug APK signing | **PASS — APK Signature Scheme v2 verified** |
| APK/source freshness | **PASS — no application source/build inputs newer than the final APK** |
| Device instrumentation | **Not run — no usable emulator or connected device** |

The 52 host tests include 18 PR/volume/history calculation tests, 2 catalog integrity tests, 6 actual Room database persistence tests, 9 actual DataStore file persistence tests, 3 timer deadline tests, 2 timer-controller failure/recovery tests, 6 minute-format parsing/round-trip tests, 5 routine/workout input regression tests, and 1 Compose end-to-end test.

The end-to-end test uses the real MainActivity, ViewModel, Room, and DataStore at a compact 320 × 470 viewport. It verifies blank routine reps, automatically saves an 8-rep target and 2:00 rest, starts the routine with blank actual reps, changes workout rest to 1:45, logs a completed set, checks a running 105-second timer, finishes, sees the immediate summary, and opens history detail. It verifies that the routine still has 2:00 rest while the saved workout retains 1:45. Other regression checks cover clearing reps, invalid partial input, rapid rest changes, exact kg/lb preservation, new-set defaults, persisted active rest, and replacement exercises not inheriting an old weight draft.

Reports:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/test-results/testDebugUnitTest/`

## Verified APK

Local output: `app/build/outputs/apk/debug/app-debug.apk`

Release asset: [Mooscles-v1.0.0-debug.apk](https://github.com/ReiTut/Mooscles/releases/download/v1.0.0/Mooscles-v1.0.0-debug.apk)

- Size: **19,124,782 bytes**
- SHA-256: `81DDFF86CF4E9B6166E5AF0BB4462DA5997B17023B0AB55FBCD50A9231BE1C4F`
- Package: `com.mooscles`, version `1.0.0` / code `3`
- Existing Android Debug certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`
- Minimum Android API: 26; target/compile API: 35
- Manifest inspection confirms no Internet permission.

Instrumented test APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. It contains 17 instrumented tests for Room persistence, DataStore timer persistence, and the main UI flow. APKs, local toolchains, credentials, keystores and build outputs are excluded from source control.

## Device-test blocker and remaining limits

The installed `Medium_Phone_API_37.0` emulator failed accelerated startup with: **Android Emulator hypervisor driver is not installed on this machine**. A software CPU fallback was attempted but remained offline and was stopped. A final `adb devices -l` check showed no connected devices. No instrumented tests were skipped or disabled to hide this limitation; they compile successfully. To run them, connect an authorized Android 8+ device or make the Android emulator's acceleration driver available, then run `./gradlew.bat :app:connectedDebugAndroidTest` using the configured JDK/SDK (or `./build.ps1 -Tasks ':app:connectedDebugAndroidTest'`).

Robolectric verifies application behavior on the host; physical rendering, device rotation/process-kill behavior, power-management behavior and actual background notification delivery still need device testing. Persistence recreation and restoration logic are covered by host tests.

On Android 12+, background alerts use an inexact alarm when exact-alarm special access is unavailable, so power saving can delay an alert. Countdown/restoration calculations remain based on the persisted deadline. Reopening restores a timer after reboot or force-stop, but the app does not schedule boot-time alarms. Wall-clock changes affect deadlines. Data remains on this device; there is currently no export/import feature.

## Android Studio Gradle JDK fix

Android Studio had selected `jbr-25`, which cannot run Gradle 8.14.5. The initial fix used `#GRADLE_LOCAL_JAVA_HOME` and `.gradle/config.properties` to select the local JDK 21. After the user's daemon-toolchain migration, `gradle/gradle-daemon-jvm.properties` declares Adoptium Java 21. The complete final build passed with that migrated configuration. The APK passes signature verification and can be shared and installed normally.
