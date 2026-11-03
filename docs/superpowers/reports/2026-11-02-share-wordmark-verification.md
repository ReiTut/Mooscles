# Shared workout wordmark — verification

Implemented locally on `codex/share-wordmark`, based on current main (`b2f27f6`, whose application tree matches the v1.5.1 source). The user authorized only this wordmark change. Phase 5/5B remains paused. No version, launcher, signing, schema, push or release changes were made.

[Design](../specs/2026-11-02-share-wordmark-design.md) · [Plan](../plans/2026-11-02-share-wordmark.md)

## Implementation and affected files

- `screens/src/commonMain/composeResources/files/share_wordmark.png`: faithful trimmed alpha artwork from the explicitly named second image, not replacement typography.
- `screens/src/commonMain/kotlin/com/mooscles/screens/share/ShareWordmark.kt`: process cache with a mutex; reads and decodes the resource on `Dispatchers.Default`, outside composition/drawing. Failed loads remain retryable.
- `screens/src/commonMain/kotlin/com/mooscles/screens/share/ShareCardPainter.kt`: injects/draws the real image on every page. Removes the old drawn letters/plate paths. Retains the former header's measured allocation, title/statistics coordinates, body layout and pagination; bitmap bounds preserve aspect ratio within that band.
- `app/src/main/java/com/mooscles/ui/WorkoutSharePreview.kt`: passes the loaded artwork into the existing renderer. Preview still decodes the actual exported PNG files.
- Tests/helpers: `app/src/test/java/com/mooscles/sharing/ShareCardHostInputs.kt`, `WorkoutCardRendererTest.kt`, `WorkoutShareExportIntegrationTest.kt`; `screens/src/uiTest/kotlin/com/mooscles/screens/share/ShareCardFixtures.kt`, `ShareCardPainterTest.kt`; `screens/src/androidDeviceTest/kotlin/com/mooscles/screens/share/ShareCardTimingTest.kt` (constructor compatibility only; timing test was not run).
- `.github/workflows/ci.yml`: host minimum 1278 → 1279 for the one new Portuguese export test. No new iOS UI test method was added.
- This design, plan and report document the change. The pre-existing `gradle.properties` edit remains unrelated and untouched.

`WorkoutCardRenderer` and `WorkoutShareIntent` production implementations are unchanged, including per-page PNG generation, 1080px width, filenames, retention, FileProvider and destination handling. The workout data model, units/precision, launcher resources and app version/signing configuration have no diff.

## Asset provenance

Explicit source: `C:\Users\nicho\AppData\Local\Temp\codex-clipboard-4e3d7e56-87a9-4443-8d49-b1b8ae5505ef.png`, 260×72 pixels.

Source SHA-256: `a326ad71429bc20d7883bf390ce618bb02e0301a727072d8a950a012d0469993`.

The newly attached `fca37fc4…png` has the same visual wordmark but differs in decoded pixels, so the explicitly named source was used. Output is 251×49 with 2px transparent padding, preserving 3,946 bright interior source pixels exactly. Its presentation gradient was removed and antialiased boundaries unmatted; the dark counters remain transparent. No letters, barbell shapes or colors were replaced with an approximate font/icon.

Asset SHA-256: `6d1ff31487f6ee637cedeb79e08930bb826b8cd817819e95630e50c21be72a42`.

The composited result was inspected against actual card graphite `#111513`. There is no rectangular presentation border. Source resolution limits sharpness when enlarged; faithful raster interpolation cannot create additional original detail.

## Focused checks

All 22 affected host tests passed:

| Class | Tests |
| --- | ---: |
| WorkoutCardRendererTest | 10 |
| WorkoutShareExportIntegrationTest | 1 |
| WorkoutShareIntentTest | 6 |
| WorkoutSharePreviewHostTest | 5 |

Existing content/pagination tests were retained, including the three-page long-workout fixture, unusually long exercise names, empty cards, cancellation cleanup and export retention. Brand assertions now compare the real source artwork's stable interior pixels and transparent gaps against the actual drawn rectangle on every page, requiring white letters and lime details; the old green plates cannot pass them. The new Portuguese case resolves real resources and exports preserved names/values, including `1.234,567 kg`.

Six focused device tests passed on the emulator, zero failures/errors/skips:

- `ShareCardPainterTest`: 4 (1080px pages, unchanged long-workout page count, dark background under either app theme, actual new wordmark on every page).
- `ShareCardTextsTest`: 2 (English and Portuguese card text/data).

Commands used the project JDK 21, SDK from local.properties and Gradle wrapper, with only the relevant classes selected. The existing timing test was compiled for API compatibility but was not executed. No unrelated suite, performance benchmark or CI push ran.

Other checks passed:

- `:screens:compileCommonMainKotlinMetadata`.
- Clean `:app:assembleDebug`.
- Android lint: 0 errors, 21 warnings (existing/informational warnings retained; no baseline/suppression added).
- Design-token check: 169 files clean.
- APK/font/icon budgets: APK growth 494,470 bytes over the repository base, below 524,288; other budgets pass.
- Signature, package and actual version inspection; `git diff --check`.
- Read-only source review found no blocker in cache/loading, constructor propagation, image geometry, state/data compatibility or sharing flow.

## Visual and runtime evidence

Evidence folder: `D:\Programing\Mooscles\.tooling\share-wordmark-20261102`.

Actual native-Canvas exported PNGs were inspected, including short kg, multiple PRs, long names/pound values, all-page branding in a three-page workout and Portuguese text. Useful samples:

- `rendered/short-kg-1.png`
- `rendered/short-pt-BR-1.png`
- `rendered/long-names-lb-1.png`
- `rendered/long-workout-1.png`, `long-workout-2.png`, `long-workout-3.png`
- `rendered/emulator-actual-1.png` (copied from the real app's exported cache file).

Runtime device: authorized `emulator-5560`, existing Medium_Phone AVD, Android 15 / API 35, `sdk_gphone64_x86_64`, host graphics. No physical phone was connected. It was started read-only with snapshot loading/saving disabled, preserving the original userdata profile.

The signed APK installed successfully in place over the emulator's v1.5.0/code21 using `adb install -r`. No Mooscles uninstall, downgrade or storage clear occurred. The real app's history contained 208 workouts, 9,608 completed sets and 72 exercise PRs. Opening an existing `Thread comparison` session and exporting its share card showed the new logo in the actual English preview and PNG. Portuguese preview was also opened and visually inspected, including translated exercise/date/statistic labels and the unchanged custom workout name.

The real share action opened Android's `Sharing image` chooser and displayed its image thumbnail. It was cancelled without selecting an app/recipient or sending anything. History totals remained 208 / 9,608 / 72 after sharing. Device XMLs and `preview-runtime.png`, `preview-runtime-pt.png` preserve the observations.

## Debug APK

Verified absolute path:

`D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk`

- Application ID `com.mooscles`.
- versionName **1.5.1**, actual versionCode **22**, unchanged.
- minSdk 26; targetSdk 35; debug build.
- Size **24,963,678 bytes**.
- SHA-256 `bd96b684a0a18e0ad17e1c67bd0ac4df37792dad13bb170d368ff4e517eeaec1`.
- Signing certificate SHA-256 `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`, the original distribution identity.

All implementation changes remain local and uncommitted. Existing PC backups, signing files and other worktrees were not modified.

## Limits

- The supplied wordmark is only 260×72; enlargement is somewhat soft. The current preview also uses its existing image-downsampling behavior; exported PNGs retain their 1080px width.
- No physical-phone or iOS execution was performed. Common metadata and Android device-test compilation passed, but this is not an iOS runtime claim.
- WhatsApp is not installed on this emulator, so its actual recipient UI was not opened. Existing WhatsApp selection/intents and grants are covered by focused host checks; generic chooser read access/thumbnail was observed on-device.
