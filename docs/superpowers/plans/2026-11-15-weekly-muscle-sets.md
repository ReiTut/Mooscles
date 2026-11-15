# Weekly muscle sets implementation

The [design](../specs/2026-11-15-weekly-muscle-sets-design.md) records the requested Weekly view.

1. Extend the overall Progress query with a one-week period and a week offset. Verify calendar boundaries, weighted set totals, empty weeks and saved history changes.
2. Add the localized period chip, week controls, muscle list and empty state. Reuse the longer periods' muscle rows and save the week in the existing Progress segment.
3. Verify week navigation and selection restoration, localization and existing periods on Android. Run the affected host tests, lint and design-token checks, then build and verify the debug APK.

## Verification

- The final Android host run passed 34 History model tests and seven resource-parity tests. The weekly cases cover regional and time-zone boundaries, 10- and 6-set weeks, warm-up and incomplete-set exclusion, fractional credits, Full Body and history refresh.
- All 20 History screen tests passed on the read-only Android 15 emulator, including the three new Weekly cases. The tests use synthetic saved workouts and exercise the Progress cache. They cover empty weeks, navigation and segment state, refreshed totals, Brazilian Portuguese formatting and button accessibility at 200% text size.
- The first emulator captures showed the new week navigation buttons breaking words at 200% text size on a 320 dp layout. The final captures show the buttons stacked at larger text sizes with readable labels. The existing Progresso segment label still wraps at that size. The final English and Portuguese captures and logs are under `.tooling/weekly-muscle-sets-20261114/`.
- `:app:lintDebug`, `:app:assembleDebug`, design tokens and the APK budget passed. The debug APK at `app/build/outputs/apk/debug/app-debug.apk` is 18,999,997 bytes, `com.mooscles` 1.5.4 / Build 26, and uses the existing signer. Its SHA-256 is `1bd707c5a2e6d575e7185563de8423bcebf983eaa87dc8045f1bfa92079c75b9`.
