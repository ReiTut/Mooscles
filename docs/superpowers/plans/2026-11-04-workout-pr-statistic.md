# Workout PR statistic implementation

Scope is the owner's requested statistic only. [Design](../specs/2026-11-04-workout-pr-statistic-design.md). Phase 5/5B stays paused.

1. Inspect the current branch, instructions, latest handoff and domain/presentation/share count semantics. Preserve the unrelated local Gradle edit and existing data/backups.
2. Reuse the existing count in the summary and saved-detail statistic. Add localized labels and spoken counts, keeping Sets and PRs together with responsive layouts.
3. Carry the domain count into share data and preview accessibility; draw a fourth header statistic on every PNG page. Measure header reflow before body pagination.
4. Extend only affected count, rendering, layout and preview tests. Update CI minimums for added tests, without running or pushing CI.
5. Run focused host checks, relevant Android screen tests on an authorized device, lint/token checks and a debug build. Inspect representative native PNG exports and verify APK identity/signature/version.
6. Document results and remaining limitations. Leave changes local and uncommitted, with no version or release changes.
