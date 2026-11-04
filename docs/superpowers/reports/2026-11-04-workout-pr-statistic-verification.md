# Workout PR statistic verification

Implemented locally on `codex/workout-pr-stat`, based on `b560f169e6aa7f9989222f3928ca4cb0402527d8`. The owner authorized this localized statistic change. Phase 5/5B remains paused, including planning. Existing Phase 5A behavior is unchanged.

[Design](../specs/2026-11-04-workout-pr-statistic-design.md) · [Plan](../plans/2026-11-04-workout-pr-statistic.md)

## Result and counting

- Completion summary shows PRs immediately beside Sets at normal font sizes, and immediately after Sets in the accessible single-column layout. Duration spans the first row; Volume and Exercises remain present.
- Saved workout detail reuses its existing PR statistic, with a shorter PRs caption and localized spoken description. It does not add a duplicate statistic. Large-font layouts stack the existing metrics.
- Share headers read Duration, Sets, PRs, Volume, repeating the workout-wide count on every PNG page. Natural-width columns preserve the existing typography. Wide content reflows with Sets and PRs together, or stacks for extreme counts.
- `ShareCards.from` uses `WorkoutSummary.prExerciseCount`; the summary/detail use `WorkoutUi.prExerciseCount`. These existing rules count distinct exercise ID plus convention, include first records, and merge multiple record types/sets for one variation. Zero remains visible. Qualification and historical calculations are unchanged.
- Share data carries the numeric count and its localized text. The real PNG preview announces the numeric total in its image description without parsing text or pixels. The preview still displays the actual exported files.
- English and Portuguese labels/descriptions are provided. Initial tests caught Portuguese singular wording for zero; Android plural selection now explicitly follows the app's zero-is-plural rule. The Portuguese Android plural includes the required `many` form. The obsolete, unused legacy caption was removed.

No database, preferences, numeric precision, wordmark, best-set highlighting, export dimensions, FileProvider, sharing destinations, timer or active-workout behavior changed. The existing pagination algorithm measures the new header before allocating body space; the representative long workout still has three pages.

## Affected files

Production:

- `screens/src/commonMain/kotlin/com/mooscles/screens/summary/WorkoutSummaryScreen.kt`
- `screens/src/commonMain/kotlin/com/mooscles/screens/share/ShareCardData.kt`
- `screens/src/commonMain/kotlin/com/mooscles/screens/share/ShareCardPainter.kt`
- `screens/src/commonMain/composeResources/values/strings_summary.xml`
- `screens/src/commonMain/composeResources/values-pt-rBR/strings_summary.xml`
- `screens/src/commonMain/composeResources/values/strings_share.xml`
- `screens/src/commonMain/composeResources/values-pt-rBR/strings_share.xml`
- `app/src/main/java/com/mooscles/ui/HistoryScreens.kt`
- `app/src/main/java/com/mooscles/ui/WorkoutSharePreview.kt`
- `app/src/main/res/values/legacy_strings.xml`
- `app/src/main/res/values-pt-rBR/legacy_strings.xml`

Focused coverage:

- `shared/src/commonTest/kotlin/com/mooscles/WorkoutPresentationTest.kt`
- `screens/src/commonTest/kotlin/com/mooscles/screens/share/ShareCardDataTest.kt`
- `screens/src/uiTest/kotlin/com/mooscles/screens/summary/WorkoutSummaryScreenTest.kt`
- `screens/src/uiTest/kotlin/com/mooscles/screens/share/ShareCardPainterTest.kt`
- `screens/src/uiTest/kotlin/com/mooscles/screens/share/ShareCardTextsTest.kt`
- `screens/src/uiTest/kotlin/com/mooscles/screens/share/ShareCardFixtures.kt`
- `app/src/test/java/com/mooscles/HistoryRecordsHostTest.kt`
- `app/src/test/java/com/mooscles/WorkoutSharePreviewHostTest.kt`
- `app/src/test/java/com/mooscles/sharing/WorkoutCardRendererTest.kt`
- `app/src/test/java/com/mooscles/sharing/WorkoutShareExportIntegrationTest.kt`

`.github/workflows/ci.yml` updates the minimums for seven added host tests, one shared iOS test and seven screens iOS tests. The local capture harness is not included in those counts. Handoff, design, plan and this report document the follow-up. The pre-existing `gradle.properties` edit remains unrelated and byte-identical to the starting state.

## Focused host checks

101 distinct selected host tests passed, zero failures/errors/skips:

| Class | Tests |
| --- | ---: |
| WorkoutPresentationTest | 34 |
| ShareCardDataTest | 28 |
| Screens StringsParityTest | 7 |
| HistoryRecordsHostTest | 7 |
| LegacyStringsHostTest | 2 |
| WorkoutSharePreviewHostTest | 6 |
| WorkoutCardRendererTest | 10 |
| WorkoutShareExportIntegrationTest | 1 |
| WorkoutShareIntentTest | 6 |

Coverage includes zero/first/multiple records, duplicate types/entries, separate variations and conventions, domain/presentation agreement in kg/lb, the 12-set/11-PR example in both languages, narrow/large-font metrics, repeated all-page totals and drawn PNG pixels, long names/pagination, real export files, preview descriptions and share permissions/intents.

After the final resource cleanup, the four affected parity/Portuguese-zero checks were rerun and passed during clean packaging. The retained complete run and the resource rerun are separate evidence sets. No unrelated host suite or performance benchmark was run; clean compilation naturally compiles the complete test source set.

Other checks passed:

- `:screens:compileCommonMainKotlinMetadata`.
- `:app:lintDebug`, zero errors and 21 existing warnings. Two warnings introduced during development were fixed, without adding suppressions.
- Design-token check, 169 files clean.
- Clean `:app:assembleDebug` and APK/font/icon budgets. Incremental packaging initially reported an inflated 25,750,661-byte APK; clean packaging produced the final artifact below, within every existing budget.
- APK package/version/signature inspection, same certificate as the locally retained distributed v1.5.2 APK.
- `git diff --check` and preservation checks.

## Device and visual checks

The authorized Redmi Note 12 (`4eb066eb`, model `23021RAAEG`, Android 15/API 35) was online. Its system denied the isolated test package's background activity launch (`Abort background activity starts from 10366`). The first UI test could not proceed; that run was stopped. This is not a passing phone UI result. No security settings or production-app data were changed.

Fallback used the existing Medium_Phone AVD, actual serial `emulator-5560`, Android 15/API 35, `sdk_gphone64_x86_64`, host graphics. It ran read-only with snapshot loading/saving disabled. Only the affected test APK was initially installed. The already-built APK was run with `am instrument` and the same four-class filter used by `:screens:connectedAndroidDeviceTest`.

All 24 checks passed in 131.252 seconds:

- WorkoutSummaryScreenTest, 13.
- ShareCardPainterTest, 7.
- ShareCardTextsTest, 2.
- Temporary visual-capture harness, 2.

Captures cover the synthetic 12-set/11-PR summary in English and Portuguese at 320dp and normal/2x fonts, plus every page of a three-page card in both languages. Multi-page capture fixtures preserve their fixed exercise/body strings; resource/model tests and the separate Portuguese native export verify production text formatting. Numeric values, adjacency, fallback layout, actual drawn ink, dimensions and page count were checked. The temporary Android capture source was removed after its outputs were copied; its retained source lives only in ignored evidence.

The final debug APK was then installed in place in the read-only emulator's disposable app copy, without uninstalling or clearing storage. Real saved-workout detail showed Sets 1 beside PRs 0. The actual exported-image preview showed Duration, Sets, PRs 0, Volume, and its accessibility description included zero personal-record exercises. The Android share chooser opened with an image thumbnail and was cancelled without selecting a recipient. History totals remained 208 workouts / 9,608 completed sets / 72 exercise PRs. The emulator was closed without saving its temporary state.

## Local evidence and samples

Evidence root is `D:\Programing\Mooscles\.tooling\pr-stat-20261103` (ignored). Exact commands/output are in `host-build.log`, `final-host-build.log`, `clean-package.log`, `common-metadata.log`, `device-checks.log` (blocked phone run), and `emulator-tests.log` (24 passing checks). `host-summary.json` and retained XMLs enumerate the host results. `phone-launch-restriction.log`, UI XMLs, signer/manifest files and `apk.json` preserve supporting observations.

Useful actual exported PNGs:

- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\rendered\eleven-prs-1.png`
- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\rendered\short-pt-BR-1.png`
- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\rendered\zero-prs-1.png`
- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\rendered\long-workout-1.png` through `long-workout-3.png`.
- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\device-rendered\share-pt-BR-1.png` through `share-pt-BR-3.png`.
- `D:\Programing\Mooscles\.tooling\pr-stat-20261103\device-rendered\app-export-zero-prs.png` (the real app's cache PNG).

Visual UI evidence:

- `device-rendered/summary-en-normal.png`, `summary-pt-BR-normal.png`, `summary-en-large.png`, `summary-pt-BR-large.png`.
- `device-rendered/app-preview-zero-prs.png` and `device-rendered/share-chooser.png`.

## Verified debug APK

`D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk`

- Application ID `com.mooscles`.
- Actual versionName `1.5.3-beta.1`, versionCode 24, unchanged.
- Debug build, minSdk 26, targetSdk 35.
- 24,964,070 bytes; growth over the repository baseline 494,862 bytes, below the 524,288-byte budget.
- SHA-256 `28b4d9e4ec61074d8a9dd15ed8f6f5ececb215483caea0cc515aa4b3998b3ac7`.
- Single signing identity SHA-256 `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`, matching the retained distributed v1.5.2 APK.

## Preservation and limits

All changes remain local and uncommitted on `codex/workout-pr-stat`; no push, tag, release or version change. The unrelated Gradle file retains SHA-256 `EB0301005B9C4F30C1F4924D25F68B9C2BC6631F2C1312E3CC27373CEA8C9ACB`. Schemas, signing configuration and existing PC backups were not modified. The phone's production app remains version 1.5.2 / Build 23, with its original last-update timestamp `2026-11-02 17:31:28`.

Physical-phone UI automation was blocked, so visual runtime conclusions are emulator-based. No iOS execution or broad accessibility/performance audit was performed. WhatsApp recipient UI was not exercised; its intent selection and file grants passed existing focused host coverage. No content was sent to another person.
