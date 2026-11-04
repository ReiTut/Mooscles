# Changes from v1.5.1 / Build 22 to v1.5.3-beta.1 / Build 24

Verified against the published releases and tagged source on **3 November 2026**.
This document includes the Build 22 starting point, then explains what Builds 23
and 24 added. It does not describe a new release or change any published APK.

| Version | Build | Channel | Main change |
|---|---:|---|---|
| [1.5.1](https://github.com/ReiTut/Mooscles/releases/tag/v1.5.1) | 22 | Full stable release | App-language selection and new launcher icon |
| [1.5.2](https://github.com/ReiTut/Mooscles/releases/tag/v1.5.2) | 23 | Full stable release; current Latest | New wordmark on shared-workout cards |
| [1.5.3-beta.1](https://github.com/ReiTut/Mooscles/releases/tag/v1.5.3-beta.1) | 24 | Optional prerelease; not Latest | Less unnecessary PR preparation and removal of the forward workout-entry fade |

## Starting point: v1.5.1 / Build 22

These features were introduced in Build 22 and remain available in Builds 23–24:

- **Settings → Language:** System default, English and Português (Brasil).
  System default follows Android; explicit choices persist across restarts and
  synchronize with Android 13+ per-app language settings.
- Existing legacy history, workout-detail, dialog, picker and share-preview labels
  follow the selected language. User-written routine and custom exercise names
  remain unchanged. Some updater/backup diagnostic messages still remain English.
- Language changes retain active-workout drafts, exact saved/pending weights,
  focus, navigation and rest-timer continuity.
- A new **white M / green barbell launcher icon**, including adaptive, round,
  themed and density-specific assets. Notification icons were preserved.
- Host-test portability fixes for Windows, without changing saved workout data.

These are the baseline for this comparison, not features first added in Build 24.

## Build 22 → Build 23: shared-workout wordmark

Build 23 replaces the complete logo at the top of shared workout cards with the
supplied **white/lime Mooscles wordmark and barbell details**.

- The workout preview and actual exported PNGs use the same artwork.
- Every page of a multi-page workout receives the replacement logo.
- Placement preserves the artwork's proportions and existing header allocation.
- The common/KMP renderer loads and caches the artwork outside drawing, avoiding
  repeated decoding for each rendered page.
- Workout names, dates, duration, sets, volume, PRs, translations, exercise order,
  numeric values, pagination, export dimensions and sharing destinations retain
  their existing behavior.

This changes the **share-card wordmark**, not the launcher icon introduced in
Build 22. The supplied source image is only **260×72 pixels**, so enlargement can
look slightly soft. No general startup or scrolling improvement is claimed.

## Build 23 → Build 24: routine-startup preparation and entry visibility

### Avoid historical PR work before an eligible set exists

Previously, a newly started workout could run both historical weight-PR and
repetition-PR calculations even though none of its working sets were completed.
Build 24 first checks whether any valid, completed, non-warmup working set exists.
If there is no candidate, those two calculations are skipped.

The record-eligibility rules themselves are unchanged. Completing a qualifying
set still enables normal PR calculation, including bodyweight and assisted
exercises. No new historical cache or cache-invalidation policy was introduced.
Two presenter regression tests and their corresponding CI test-count adjustments
were added.

### Remove the forward workout-entry fade

The workout route now enters without fading over Train. This removes the
translucent overlap that could make the workout appear late or unclear.
Back navigation and unrelated route transitions retain their existing behavior.

The current loading UI still uses **workout skeleton placeholders**. Build 24 does
not restore the historical literal “Starting workout…” text overlay, add an
artificial loading duration, or redesign the exercise list.

### What the measurements establish

The implementation investigation used an optimized/profileable build on a Redmi
Note 12 running Android 15, with 8 exercises, 32 planned sets and 200 historical
workouts. The primary comparison had six Starts per implementation: three
first-process Starts and three subsequent Starts.

| Measured median | Baseline implementation | Updated implementation |
|---|---:|---:|
| First-start presentation CPU | 34.8 ms | 9.8 ms |
| Warm-start presentation CPU | 31.5 ms | 17.0 ms |
| First-start editor frame-commit proxy | 316 ms | 328 ms |
| Warm-start editor frame-commit proxy | 239 ms | 263 ms |

The changes reduce PR preparation work and improve visual opacity. They **do not
establish a consistent reduction in overall Start-to-usable-workout time or better
first-scroll performance**. Initial layout and occasional graphics/shader stalls
remain. A frame commit is not proof of the earliest accepted input.

The baseline was the Build 23/current-main implementation. These are reused
implementation-investigation measurements, not a direct benchmark of the distributed
Build 22 APK against the distributed Build 24 APK. Both isolated
diagnostic arms retained the Build 23 version labels; the published beta was built
fresh with its actual versionName and versionCode.

## Compatibility and update behavior across Builds 22–24

- Application ID remains **`com.mooscles`**.
- The minimum Android version remains **Android 8.0 / API 26**.
- Room stays at **schema 6**. These updates introduce no database migration,
  destructive reset or saved workout/session-format change.
- Routine identities, targets, workout history, PRs, preference keys, numeric storage
  and timer formats are preserved.
- The distributed APK remains the existing **debug build**, signed with the same
  original distribution certificate. A GitHub stable/prerelease label does not
  change the APK build type or signing identity.
- Android build numbers increase **22 → 23 → 24**. The unchanged updater selects
  compatible updates by metadata **versionCode**, not semantic version or tag order.
- **v1.5.2 / Build 23 remains stable Latest.** Enable **Include prereleases** in
  Settings to discover v1.5.3-beta.1 / Build 24. Stable-only users are not automatically
  offered the beta.
- Download settings still apply. Updater-managed installation waits until no workout/rest timer is
  active, and Android may require system approval. Discovery is not proof of installation.

Phase 5/5B remains paused, including planning. Existing Phase 5A functionality is
retained. No account, backend or analytics feature was introduced by these updates.

## Verification recorded for these updates

| Update | Relevant evidence |
|---|---|
| Build 22 | 70 focused language/icon checks and Android 15 emulator observations were reused for release preparation; 25 updater/signing checks, clean build, lint and APK budgets were run freshly. |
| Build 23 | 22 host checks, 6 emulator checks and actual preview/export/chooser observations; fresh release packaging, lint, resource/budget and original-signer checks. |
| Build 24 | 70 focused startup/state checks and optimized-phone observations were reused; 25 updater/signing tests, clean debug build, lint and APK budgets passed freshly. Android host CI and four Linux/Windows release-script checks passed. Public APK/metadata/checksum downloads matched, and all four live stable/prerelease discovery cases passed. |

These test counts refer to different update-specific sets; they must not be added
as one unique regression-suite count. At beta publication, iOS CI was queued and
was not reported as verified. The final version-only beta APK was not installed
over the user's production app as part of publication. No uninstall or storage
clearing was used. Performance and installation limitations remain as documented.

## Source and detailed records

- [Build 22 release notes](https://github.com/ReiTut/Mooscles/blob/v1.5.1/docs/superpowers/releases/v1.5.1.md)
- [Build 23 release notes](https://github.com/ReiTut/Mooscles/blob/v1.5.2/docs/superpowers/releases/v1.5.2.md)
- [Build 24 release notes](https://github.com/ReiTut/Mooscles/blob/v1.5.3-beta.1/docs/superpowers/releases/v1.5.3-beta.1.md)
- [Build 22 → Build 23 source comparison](https://github.com/ReiTut/Mooscles/compare/v1.5.1...v1.5.2)
- [Build 23 → Build 24 source comparison](https://github.com/ReiTut/Mooscles/compare/v1.5.2...v1.5.3-beta.1)
- [Language and launcher verification](https://github.com/ReiTut/Mooscles/blob/v1.5.1/docs/superpowers/reports/2026-11-01-language-launcher-verification.md)
- [Shared-wordmark verification](https://github.com/ReiTut/Mooscles/blob/v1.5.2/docs/superpowers/reports/2026-11-02-share-wordmark-verification.md)
- [Routine-startup investigation and limits](https://github.com/ReiTut/Mooscles/blob/v1.5.3-beta.1/docs/superpowers/reports/2026-11-03-routine-startup-latency.md)
