# Workout-wide PR statistic

This is a localized follow-up authorized by the owner's detailed request. Phase 5/5B remains paused, including planning. Existing Phase 5A behavior is preserved.

- Add PRs beside Sets in the completion summary, saved detail, actual exported share cards and their preview. The reference workout has 12 completed sets and 11 qualifying exercise variations.
- Reuse `WorkoutUi.prExerciseCount` and `WorkoutSummary.prExerciseCount`. Both include first and beaten records, deduplicate exercise ID plus convention, and exclude records belonging to other workouts. Qualification and storage do not change.
- The share header reads Duration, Sets, PRs, Volume on every page. Use existing fonts/tokens and natural-width columns. Reflow wide statistics instead of shrinking them excessively; keep Sets and PRs adjacent whenever they fit. The summary retains its existing statistics, adding PRs without removing Exercises.
- The legacy detail already has the statistic. Reuse it, use the shorter PRs caption and a localized spoken description, and stack at large font sizes.
- Expose numeric and localized display counts in the share data so the PNG preview can announce the total without parsing pixels or localized text. Preview continues to display the exact exported PNGs.
- Preserve dimensions, wordmark, record lists, best-set highlighting, ordering, precision, sharing permissions and pagination algorithm. A wider header may require more vertical space, accounted for before body pagination.
- No migration, preference, signing, version, release or unrelated optimization changes.

Acceptance checks cover zero/first/multiple/deduplicated/independent variations; agreement between domain, presentation and cards; all-page repeated count; English and Portuguese; narrow/large-font layouts; actual PNG drawing; preview accessibility and valid share intents.
