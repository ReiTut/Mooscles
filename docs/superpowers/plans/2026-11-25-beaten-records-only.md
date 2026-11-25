# Plan: only beaten records, reps at the heaviest weight

Spec: [2026-11-25-beaten-records-only-design.md](../specs/2026-11-25-beaten-records-only-design.md). Branch `feat/no-first-records` from `main` at `9f2df99` (v1.5.10 / Build 33).

1. `WorkoutCalculations`: weight PRs need a previous heaviest; reps PRs need a previous value at the heaviest load of their group (lowest for assistance), counting the workout itself; `records()` and `repsRecords()` keep only beaten current records. Remove `firstRecord`, `firstRecords`, `firstRepsRecords` and `firstRecordExerciseCount`.
2. `WorkoutPresentation` and `UiContract`: drop the first-record plumbing (`RecordUi.firstRecord`, `WorkoutExerciseUi.firstRecord`, `WorkoutUi.firstRecordExerciseCount`); `prExerciseCount` counts beaten records.
3. `:screens`: remove the Records chip, badge and empty state, the summary and saved-workout "First records" sections, the history first count, the spoken "First record", and first records on share cards. Delete the unused strings in both languages; reword the empty title.
4. Tests: new domain tests from the spec; update tests that expected first records; adjust `MIN_HOST_TESTS`.
5. Verify: four host suites, lint, builds, design tokens; emulator device tests; phone logic tests; `compat-reviewer`; code review. Install a Preview on the owner's phone. No release until the owner asks.

Revision (2026-11-25, see the spec's revision): count an exercise's first session as a PR (weight PR, or a reps PR when there is no load record to give), keep later reps PRs at the heaviest load, keep per-set marks for beaten records only; update the domain, presentation, texts and tests, and reinstall the Preview.
