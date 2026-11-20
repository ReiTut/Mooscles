# Plan: routine start values from Previous

Spec: [2026-11-19-routine-start-values-design.md](../specs/2026-11-19-routine-start-values-design.md). Branch `fix/routine-start-prefill` from `main` at `fad04f1` (v1.5.9 / Build 32).

1. Write `RoutineStartValuesTest` in `shared/src/commonTest` for the reported leg press and bench press, same-load reps records, warm-ups, Follow the record, bodyweight, assistance and invalid rows.
2. Add the pure `domain/RoutineStartValues.kt`, pairing like `SetSuggestions.pairPrevious`.
3. Add two bulk reads to `WorkoutDao` (`savedEntriesForRoutine`, `completedSetsForRoutine`) so the start keeps a fixed number of reads for any routine size (`WorkoutLoadingDataTest`).
4. Use them in `WorkoutRepository.startRoutine()`; add three Room tests to `RoutineTargetPersistenceTest`.
5. Update the tests whose expectations encoded the old start-at-target behavior (`RoutineSetPersistenceTest`, `RollbackV142DataCompatibilityTest`, `WorkoutLoadingDataTest`), keeping what each protects.
6. Raise `MIN_HOST_TESTS` with the count in the comment.
7. Verify: the four host suites, lint and `assembleDebug`; the routine device tests on the Android 15 emulator; `compat-reviewer`; `/code-review`.
8. Install a Preview build on the owner's phone for manual testing. No release until the owner asks.

Revision (2026-11-19, see the spec's revision note): drop the record raise and make Follow the record sets copy Previous. Rewrite `RoutineStartValuesTest` (still 11 cases) and the three Room tests in `RoutineTargetPersistenceTest`; update `BackupRepositoryTest.inheritedTargetsFollowOnlyTheExactImportedPlannedSetRecord` and `RollbackV142DataCompatibilityTest` start expectations; repeat the same verification and reinstall the Preview build.

Revision 2 (2026-11-19): extend to empty workouts and added sets (`insertInitialWorkoutSets`, `addSet`) through `RoutineStartValues.previousWorking`; +2 unit tests, +2 Room tests, `quickWorkoutSetsCopyPreviousAndFallBackToTheWeightedBodyweightRecord` updated from the record-only expectation; `MIN_HOST_TESTS` 1373; same verification and Preview reinstall.
