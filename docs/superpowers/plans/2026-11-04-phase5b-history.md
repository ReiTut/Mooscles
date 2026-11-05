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
2. [ ] Implement S2 `MonthCalendar`: pure geometry/semantics, 320/412 widths, four/six-week months, both themes, large fonts, accessible day buttons and non-focusable empty days. Add focused tests and named tokens; prove S2 before integrating the History tab. Use the documented visual-only/day-filter fallback if required.
3. [ ] Add immutable history metadata to the existing index and pure memoized queries: bilingual AND search, OR routine selection, exercise/beaten-record/day filters, month groups/calendar marks, period buckets/totals, muscle weighting and most-performed exercises. Add deterministic common tests for date/time-zone/bucket/filter/domain parity edges.
4. [ ] Build shared History Workouts/Progress screens on those results, preserving saveable selection/filter/month/list/data-table state, catalog localization, data tables, empty/error/loading states and semantic labels.
5. [ ] Build shared saved-workout detail and editing using existing actions. Test each old capability, rest editing/cancellation, exact drafts/conversions, confirm destructive edits, and prove historical completion never starts a timer. Preserve inclusive PR statistics.
6. [ ] Move only sharing-preview UI to shared screens. Keep Android snapshot/PNG/decode/intent ownership, stable tags and all existing export behavior. Port preview states/destination coverage.
7. [ ] Integrate routes and own placeholders; port legacy behavior tests before removing genuinely unused old screens/components. Record old/new tap budgets and ensure navigation/focus/state restoration parity.
8. [ ] Verify 5A carryovers against current code: foreground date refresh, list-top assertion, cache-start dispatch assumption, record convention label, long-range year labels and region behavior; handle relevant unresolved items with focused tests.
9. [ ] Run focused tests during each change, resource parity/tokens/metadata/lint/debug packaging, and final required device suites once. Validate offline/compatible upgrade on isolated emulator data, restoration, shared history flow, performance in optimized build and clean APK budgets. Physical checks trigger the owner's stop-and-approval gate.
10. [ ] Review full feature diff, update CI minima by actual added/ported tests, run feature-branch CI (including iOS), visual critique and compatibility audit. Report actual evidence/limitations and final debug/preview APK paths; keep feature branch unmerged and version unchanged.

## Evidence

Keep heavy captures/traces and command logs in ignored `.tooling/phase5b-20261104/`. A phase verification report will link their absolute local paths and distinguish fresh, reused, pending and unverified results. Do not claim phone/iOS execution from host tests or code inspection.
