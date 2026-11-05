# Phase 5B — verification in progress

Baseline: `c9ba9b1` (`origin/main`, v1.5.3 / Build 25). Working branch: `codex/phase5b-history`. The owner's continuation explicitly resumes 5B only. This report does **not** mark the phase complete.

## Implemented locally

- Monthly calendar and pure geometry in `:designsystem`, including accessibility, a visual-only fallback and the gallery sample.
- Immutable history metadata built as part of the existing `ProgressIndex`, cached background filter/calendar/overall-progress queries and focused domain tests.
- Shared History Workouts/Progress UI with calendar, search/filters, period statistics, charts/tables, muscle map and frequent exercises.
- Shared saved-workout detail/editor, preserving Build 25's inclusive PR statistic and all old editing capabilities, including precise rest seconds with Save/Cancel.
- Shared sharing-preview UI; Android retains snapshot reading, original PNG export/decoding, cache and intents.
- Native navigation/back handling, localized errors, foreground-date refresh, record convention labels and legacy screen/test ports.
- Removed obsolete Material icons dependency only after its last production callers were removed. No new runtime dependency.

## Fresh evidence so far

- `HistoryModelTest`: 27 host tests passed.
- `WorkoutDetailModelTest`: 7 host tests passed.
- Calendar common tests passed; calendar Android S2: **7 UI tests passed** (`emulator-5560`, Android 15/API 35, x86_64, host GPU), including four/six-week calendars, 320/412 widths, both themes and large fonts. The first runner attempt failed before executing tests due to an `@AfterTest` return type; corrected without weakening assertions.
- First integrated host run executed 59 tests with seven failures. These included old detail/snackbar expectations, scrolling to an uncomposed lazy item, exact-label assertions, an invalid-PNG test needing the native decoder, and a test incorrectly treating derived previous-set text as persisted active-workout data. The latter now compares the exact stored workout/exercise/set rows and timer token/deadline. Corrections are being rerun.
- A focused regression test reproduced missing subject failure counters after two rejected saved-set edits. Set saves and saved-workout deletion now attach their existing IDs to the existing command error bookkeeping. The command-loop cancellation behavior, payloads and repository writes are unchanged. The corrected integrated runs passed 64 focused host cases (63 in build 5, the corrected system-Back case in build 6), plus metadata/lint/debug packaging.
- The first affected screen matrix ran **78 tests: 69 passed, 9 failed**. Follow-up corrected test assumptions about punctuation folding, merged text-field semantics, destructive-dialog tags, off-screen calendar clicks and the documented forced-locale restoration-test limitation. Preview image space at 320 dp/200% was an actual layout problem; moving its wrapping page-count caption into the bounded actions area fixed all four theme/width cases. Preview and saved-editor restoration now pass the focused retry. No stored numeric/locale or export behavior was changed to satisfy tests.
- New regressions reproduced three saved-detail state bugs: a delete retry blocked after timeout; unrelated errors releasing deletion protection; and one failed set discarding another set's pending completion. The fixes passed those three cases and late-delete acknowledgment. Another new assertion incorrectly assumed optimistic completion is saved through recreation; the app intentionally reloads completion from storage, and the test was replaced with pending-value/draft preservation instead. That revised test awaits its rerun.
- Two additional tests reproduced History's nonzero scroll reset after recreation and Progress segment state loss on a round trip. The source fixes keep initial skeletons outside lazy lists, retain segment state, and reset scroll only for a later filter result; post-fix verification is pending.
- Three focused accessibility cases passed both themes in EN/default and PT/320 dp/200% configurations, covering calendar target exceptions, labels, whole numbers/text, chart summaries/data tables, detail PRs and preview controls. These are semantics/layout checks, not TalkBack or VoiceOver proof.
- Calendar S2 rerun passed all seven cases and retained 16 PNGs for manual review. Narrow 200% dates fit their cells and the four/six-week layouts are visible without overlap. Captures are under `D:\Programing\Mooscles\.tooling\phase5b-20261104\calendar-captures\files\`.

## Source compatibility review

The independent review found no change in schema/data, preferences, backup, manifest, updater, timer implementation, application ID or version. Historical set completion still starts automatic rest only for an `ACTIVE` workout. Saved detail selects `UiState.history` by ID and reuses the existing exact-value/draft editor with its own `saved:<id>` namespace and no active suggestions or PR reactions. Precise rest writes only on Save. Android keeps PNG export/decode/FileProvider/intent ownership; supplied artwork and PR totals are unchanged. This source review does not replace final APK signing, offline upgrade or device-input evidence.

Command logs and heavy evidence: `D:\Programing\Mooscles\.tooling\phase5b-20261104\`.

## Pending before completion

- All affected UI tests and end-to-end history flow; final Android device suites once.
- Corrected host navigation/rest/editor/preview/timer/error tests, resource parity, tokens, common metadata, lint, clean debug build and actual APK budgets.
- iOS compile/simulator evidence through feature-branch CI; Windows host checks do not prove iOS execution.
- Visual critique, optimized performance/query/scroll measurements and isolated offline upgrade/data compatibility.
- Final actual test counts/CI minimum accounting, source review and any fixes revealed by verification.

## Phase checkpoint — local host and artifacts

The full host checkpoint ran **1,335 cases**: Android app 569, shared 303, design system 127,
screens 336. The latter three suites passed. The Android suite initially had 24 failures:
five Phase-5B-related test setup/legacy expectations were corrected and all **28 affected host
cases passed** on rerun. The remaining **19 failures reproduce unchanged in the previous-release
`c9ba9b1` source copy** (40 focused baseline cases): 15 Android-26 photo-store/native image cases
and four plain-JVM Android DataStore file-replacement cases on Windows. They are retained and
are not skipped or weakened. Linux CI remains required; this is not a fully green local host suite.
Logs: `phase-host-build-1.log`, `affected-host-3.log`, `baseline-host-portability.log` under the evidence root.

Fresh clean debug and preview builds, Android test APK compilation and common metadata passed.
Debug lint has **0 errors / 120 warnings** (99 unused legacy resources, 10 UseKtx, 7 PluralsCandidate
and four other advisory warnings); no lint suppression was added. Design-token check passed for
178 files. The clean debug APK is **18,214,392 bytes**; the old Material icons library is no longer
packaged after its callers were removed. APK budget passes: growth -6,254,816 bytes against the
24,469,208-byte phase base; compressed fonts 645,546 bytes; fonts plus icon upper bound 938,769 bytes.
No budget increased.

Verified debug identity: `com.mooscles`, **1.5.3 / versionCode 25**, minimum SDK 26; SHA-256
`1973ae32c974ab86fea776b8e6eca64daf80c4466c634ae23bd0c6bff8480260`.
Its certificate SHA-256 is the original
`0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.
Preview is `com.mooscles.preview`, with its own data and updater disabled. Its current APK SHA-256 is
`a7a8f1dd9f87d87113dfd23d4542ae81014c8739072e3c58d0867d77f8a53c3d`.
Paths: `D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk` and
`D:\Programing\Mooscles\app\build\outputs\apk\preview\app-preview.apk`.

Real native-Canvas exports regenerated by the passing renderer tests are in
`D:\Programing\Mooscles\app\build\outputs\share-previews\`, including `short-pt-BR-1.png`,
`eleven-prs-1.png` and all three `long-workout-*.png` pages. Preview UI tests do not themselves
prove Android encoding; the renderer/intent integration tests provide that separate evidence.

## Preservation and phone approval

The unrelated `gradle.properties` file remains at SHA-256 `EB0301005B9C4F30C1F4924D25F68B9C2BC6631F2C1312E3CC27373CEA8C9ACB`. The permanent backup `D:\Mooscles-Backups\20261104-003300-before-v1.5.3-full` is untouched and stays local. Version remains 1.5.3 / 25; no merge, tag or release has occurred.

Physical phone `4eb066eb` was listed online/authorized by read-only `adb devices -l`; no phone tests, installs, storage operations or settings changes were performed. Phone-dependent testing requires a concrete approval request followed by a complete stop of all work until explicit approval. Connection is not approval. Emulator checks use an explicit emulator serial and isolated test packages. Production app data/signing/schema/preferences/backup/updater contracts are preserved.
