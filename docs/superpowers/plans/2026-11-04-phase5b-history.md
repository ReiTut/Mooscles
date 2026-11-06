# Phase 5B — history, overall progress, saved-workout editing and share preview

Date: 2026-11-04. Source baseline: `c9ba9b1` (`origin/main`, v1.5.3 / Build 25). Branch: `codex/phase5b-history`.

The owner's attached instruction explicitly resumes Phase 5B, including planning and implementation, superseding the dated pause notices for this phase only. The approved [Phase 5 specification](../specs/2026-10-31-phase5-progress-history-design.md) remains the scope. No Phase 6/7 work, version change, merge, tag or release is authorized. Feature-branch CI is authorized.

## Preservation and updated decisions

- Preserve the unrelated `gradle.properties` edit (starting SHA-256 `EB0301005B9C4F30C1F4924D25F68B9C2BC6631F2C1312E3CC27373CEA8C9ACB`). Exclude it from commits.
- Preserve the owner's permanent PC-only backup outside the project unchanged. It is source/APK recovery, not a phone-data snapshot.
- The October specification predates the language setting, supplied launcher/share artwork, startup fast path and Build 25 PR statistic. Keep all of them. The detail's PR total remains `WorkoutUi.prExerciseCount` (distinct exercise/convention, first records included). The explicit history “With PR” filter retains the spec's narrower beaten-record meaning.
- Retain schema 6, repository writes, preferences/backup/updater contracts, application identity and original signer. Screen selections are saveable UI state, not new preferences.
- Reuse one `ProgressIndexCache`; all history queries run through background memoized queries. No history calculations during composition/drawing.
- Physical-phone testing requires a concrete approval request, followed by a complete stop of all coding, builds, Git, tests and delegated work until approval. ADB inventory alone is permitted. Host/emulator checks may proceed without phone access. Never uninstall or clear the production app.
- Recalculate the actual current APK budget; the old 239 KB headroom is historical.

## Tasks and acceptance

1. [x] Inspect local and fetched remote state, instructions, approved spec and 5A evidence; identify no existing 5B plan/branch/implementation. Baseline only dirty file is the unrelated Gradle edit. No connected devices at inventory.
2. [x] Implement S2 `MonthCalendar`: pure geometry/semantics, 320/412 widths, four/six-week months, both themes, large fonts, accessible day buttons and non-focusable empty days. Add focused tests and named tokens; prove S2 before integrating the History tab. Use the documented visual-only/day-filter fallback if required.
3. [x] Add immutable history metadata to the existing index and pure memoized queries: bilingual AND search, OR routine selection, exercise/beaten-record/day filters, month groups/calendar marks, period buckets/totals, muscle weighting and most-performed exercises. Add deterministic common tests for date/time-zone/bucket/filter/domain parity edges.
4. [x] Build shared History Workouts/Progress screens on those results, preserving saveable selection/filter/month/list/data-table state, catalog localization, data tables, empty/error/loading states and semantic labels.
5. [x] Build shared saved-workout detail and editing using existing actions. Test each old capability, rest editing/cancellation, exact drafts/conversions, confirm destructive edits, and prove historical completion never starts a timer. Preserve inclusive PR statistics.
6. [x] Move only sharing-preview UI to shared screens. Keep Android snapshot/PNG/decode/intent ownership, stable tags and all existing export behavior. Port preview states/destination coverage.
7. [x] Integrate routes and own placeholders; port legacy behavior tests before removing genuinely unused old screens/components. Record old/new tap budgets and ensure navigation/focus/state restoration parity.
8. [x] Verify 5A carryovers against current code: foreground date refresh, list-top assertion, cache-start dispatch assumption and localized record convention label are covered. Long-range short axis labels and fresh pre-33/real-iOS region changes remain explicitly documented limitations; no extra redesign is bundled.
9. [ ] Run focused tests during each change, resource parity/tokens/metadata/lint/debug packaging, and final required device suites once. Validate offline/compatible upgrade on isolated emulator data, restoration, shared history flow, performance in optimized build and clean APK budgets. Physical checks trigger the owner's stop-and-approval gate.
10. [x] Review full feature diff, update CI minima by actual added/ported tests, record prior feature-branch CI, complete the visual critique and compatibility audit, and report final debug and preview APKs. Final checks are Android-only at the owner's request. Keep the final fix local to avoid triggering iOS CI.

## Evidence

Keep heavy captures/traces and command logs in ignored `.tooling/phase5b-20261104/`. A phase verification report will link their absolute local paths and distinguish fresh, reused, pending and unverified results. Do not claim phone/iOS execution from host tests or code inspection.

## Final implementation checkpoint

- Source through `7fc1b2e` includes the narrow/large-font saved-editor fix: keep Back/Done/menu in `TopBar(null)` and the full editing heading in scrolling content. A 56 dp viewport regression assertion prevents another timeout from a zero-height lazy editor; no assertions or timeout budgets were relaxed.
- Clean debug and preview builds, common metadata, lint and APK budgets pass. Version remains 1.5.3 / 25 and the original signer is verified. Exact artifacts are in the verification report.
- The isolated offline upgrade passed both 14-check stages, including identical real exports and raw preferences, schema 6, exact active sets and a running timer deadline.
- Full device suites and latest-source CI evidence are recorded separately from focused retries. No blanket green claim is made for the first Windows/device attempts.
- The approved phone test identified repeated muscle-map path drawing. The integrated offscreen layer brought Overall Progress jank from 7.24% to 1.39% on that phone. Its layer can still incur heavier draws on lazy-item re-entry. The earlier emulator result exceeded 5% and predates the layer.
- Task 9 retains open performance criteria. The live unit-switch median remains above 60 ms, and navigation-to-visible-content and active-workout rendering comparisons remain unverified. The [verification report](../reports/2026-11-05-phase5b-verificacao.md) separates full earlier suites from the final affected Android checks.
