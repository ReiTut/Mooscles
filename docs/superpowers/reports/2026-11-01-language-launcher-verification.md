# Language selection and launcher icon — verification

Implemented locally on `codex/language-launcher-icon`, based on the recovered v1.5.0 checkout and local Windows test-portability commit `b1a96ef`. This task does not start or advance Phase 5/5B. Existing Phase 5A features remain in place. No app version change, push, tag or publication occurred.

Scope: [design](../specs/2026-11-01-language-launcher-design.md), [plan](../plans/2026-11-01-language-launcher.md). These record the scope the user explicitly authorized in the implementation request, not additional Phase 5 work.

## Changes

- Settings now offers System default, English and Português (Brasil), using the current design-system radio rows and translated labels.
- `AppLanguage.kt` uses Android 13+ `LocaleManager` as the sole locale authority, including external Android Settings changes. Android 8–12 uses separate Android-only preference metadata and localized contexts; workout DataStore and backup keys are unchanged. Legacy selection transfers once on an OS upgrade without overriding an existing framework choice.
- MainActivity handles locale/layout-direction changes in place. The controller publishes a configuration copy to Compose, including the manual pre-33 path, without keying/replacing the remembered navigation/editor tree. Application resources also follow the pre-33 choice for non-Compose consumers.
- Existing Compose resources and catalog-name presentation follow the selection. Existing Android history, detail, picker, dialog and share-preview labels were translated without redesigning these screens. User-written names are kept as entered.
- Routine draft ownership no longer resets on formatting-locale changes. Saved/pending weight comparisons accept equivalent comma/dot separators before parsing rounded pound text, preserving exact kilograms during later rep edits.
- Both languages remain in app bundles for offline switching; no dependency was added.
- Launcher foreground, monochrome, adaptive/round/density assets use the supplied white M and lime barbell. The preview keeps its identifying amber marker. Rest notification icons are unchanged.

The explicitly named source `C:\Users\nicho\AppData\Local\Temp\codex-clipboard-ac02917e-486a-4d0e-a562-87b9ede2f64f.png` was used. The newly attached PNG looks identical but differs in decoded pixels; generated assets were therefore regenerated from the explicitly named source. Source SHA-256: `e9e469867b2f2ad429e63566282296a3620451a4ea6768305cf44bd441c7a8ac`.

## Focused host verification

70 unique affected tests pass, with zero failures/errors in the effective final result:

| Area | Tests |
| --- | ---: |
| AppLanguageHostTest | 6 |
| AppLanguageUiHostTest | 1 |
| LegacyStringsHostTest | 2 |
| SettingsScreenHostTest | 14 |
| SettingsTextsHostTest | 6 |
| GalleryLocaleRestoreHostTest | 3 |
| LauncherIconHostTest | 1 |
| HistoryRecordsHostTest | 5 |
| InlineRestEditorHostTest | 2 |
| WorkoutSharePreviewHostTest | 5 |
| PlannedSetDraftsTest | 18 |
| SetRowEditorLocaleTest | 2 |
| Design-system StringsParityTest | 5 |

Passing results from earlier focused runs in this task were retained, and only affected checks repeated after corrections. Evidence, initial failures and final XMLs are in `.tooling/language-icon-20261101/`; `effective-test-results.json` identifies each final result. These are fresh checks from this task, not reused CI claims. No unrelated full test suite or performance benchmark ran.

The checks cover framework ownership/external changes, one-time OS-upgrade transfer, legacy preference persistence/new contexts, System default following device changes rather than the app-modified default locale, EN/PT resource updates, unsupported French fallback to English, Settings back-stack retention, partial drafts, exact saved/pending pound conversions in both language directions, held-stepper writes, translations/arguments and icon layer loading.

Initial verification exposed a real pre-33 issue: updating Android resources manually did not notify Compose. Publishing the configuration copy fixed it while retaining the UI tree. Integration also corrected Android apostrophe escaping. The simulated system-locale UI test initially recreated its generic Activity; its fixture now declares the same locale/layout-direction handling as MainActivity. All affected checks passed after these corrections.

Other checks:

- Debug build: successful, final clean build (`clean-build.log`).
- Android lint: successful, zero errors and 21 warnings. Existing toolchain/API/KTX warnings remain; new informational locale-attribute/KTX/plural-candidate warnings are also retained. No baseline or suppression was added.
- Design tokens: clean, 168 files checked.
- APK size: passed after clean packaging. Incremental packaging initially exceeded the size budget, a known repository issue; the clean build is 479,029 bytes over the 24,469,208-byte base, below the 524,288-byte limit. Font/icon budgets also pass.
- `git diff --check`: passed. Schema, workout preferences, timer and updater implementation files have no diff.
- CI minimums increased by the 14 added host tests and 3 added common/iOS tests. CI was not pushed or run.

## Emulator observations

SDK: `D:\Programing\Mooscles\.tooling\android-sdk` from local.properties. Authorized serial: `emulator-5560`, Android 15 / API 35, `sdk_gphone64_x86_64`, existing Medium_Phone AVD with host graphics. No phone was connected.

The emulator was launched read-only with snapshots disabled, preserving its saved userdata. Existing app version was 1.5.0/code 21. An ignored local data archive was captured before testing; the APK installed successfully in place with `adb install -r`. No uninstall, downgrade, storage clearing or signing substitution was used.

Observed and captured:

- Initial System default on the English device displayed English. Selecting Portuguese immediately displayed `Configurações`, `Idioma`, Portuguese navigation/accessibility labels and translated catalog exercise names.
- Framework locale query returned `[pt-BR]`. External framework selection of English updated the app. Clearing the in-app selection returned an empty override `[]` and English System default UI.
- Existing custom routine names remained unchanged; representative small routine `Flow test 1792778303098` had one exercise/two sets. The previously stored large routine (80 exercises/960 sets) remained visible; no scrolling benchmark was performed.
- In the small active workout, after completing a set and opening the second load input, draft `22.` remained focused before and after external EN → PT change. Rest countdown was `1:58` before and `1:55` after, while workout elapsed time moved `0:25` → `0:27`; there was no timer reset or loss of completion state.
- Force-stop/relaunch retained the Portuguese framework selection and the active workout, with the correct routine showing Resume workout. This verifies selection/session persistence; it does not promise restoration of arbitrary unsaved drafts after force-stop.
- The final clean APK also installed in place successfully. Launcher rendering was visually inspected: complete M/barbell, no clipped edges or doubled presentation frame. Generated circle/squircle/rounded-square previews were inspected, and the artwork radius is 30.51 dp inside the adaptive 33 dp safe circle.

Runtime XMLs/screenshots, launcher mask previews and metadata are kept in the ignored evidence folder. Tests changed only the read-only emulator overlay, not the saved emulator profile or a physical phone.

## Artifact and compatibility

Verified absolute path:

`D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk`

- Variant: debug.
- Application ID: `com.mooscles`.
- versionName: `1.5.0`; actual versionCode: `21`, unchanged.
- minSdk 26, targetSdk 35.
- Size: 24,948,237 bytes.
- SHA-256: `c793c7d228a5ae75879d7f5d317b38efff0ecf4e78c66ecc78836fc41c6f712d`.
- Signing certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`, matching the existing distribution identity; signature verified after clean packaging.

The original unstaged `gradle.properties` parallel-sync setting remains untouched. Existing PC backups, signing files and other worktrees were not modified. Implementation changes are left local and uncommitted for review.

## Limits

- Older Android live switching was tested with Robolectric API 28, not an API 26–32 device. Android 15 visual checks ran on an emulator, not a physical phone.
- TalkBack speech, OEM launcher masks and themed-icon appearance on physical launchers were not exercised; resource semantics and generated masks were checked.
- iOS execution was unavailable on this Windows PC. No claim is made that a complete iOS app exists or that its new common tests ran here.
- Existing domain/updater/backup diagnostic messages that arrive as ready-made English text are still English. This task localizes the resource-based UI and existing legacy labels, and does not redesign those message contracts or implement Phase 5/6 features.
