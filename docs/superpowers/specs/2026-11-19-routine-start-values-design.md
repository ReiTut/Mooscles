# Routine start values from Previous

2026-11-19. Approved by the owner in chat on the same day; this note records the decisions.

## Problem

Starting a routine filled each set's KG and REPS with the planned target (for example 100 kg × 10 and 100 kg × 9), while the Previous column showed what was actually done last time (120 kg × 7 and 100 kg × 9). The owner expects the fields to start from Previous, or from a record when it is higher.

## Behavior

When a routine starts, each planned set gets its starting KG and REPS from `RoutineStartValues.forPlannedSets()`:

- **Pairing** follows the Previous column (`SetSuggestions.pairPrevious`): warm-ups with warm-ups, working sets with working sets, then by order. Previous is the newest finished session started before this one, for the same exercise ID and load convention, using its valid completed sets.
- **Manual working sets** (superseded, see Revision) copy their paired Previous set. Their reps rise to the most ever done at that same load (owner's choice: "PR at that weight"), counting only valid completed working sets of the same exercise and load convention from finished workouts. Assisted sets compare only with assisted sets.
- **Manual warm-ups** copy their paired Previous warm-up exactly.
- **No Previous set** (for example the second set when only one was done last time): the set keeps its routine target (owner's choice).
- **Follow the record sets** (superseded, see Revision) keep their saved record, but still use their Previous slot, so the sets after them pair the same way as the column. A Follow the record set that has no record yet (0 reps) starts like a manual set.
- An assisted Previous set never becomes the load of a regular set, or the reverse; that set keeps its target.

Unchanged: routine targets (manual prescriptions are never rewritten), Follow the record updates after finishing, unplanned sets added during a workout (Build 32 record defaults), saved-workout editing, the Previous column, and the empty-field suggestions.

## Compatibility

No schema, DataStore, version or updater change. The repository adds two bulk read queries (`savedEntriesForRoutine`, `completedSetsForRoutine`) and writes the same `WorkoutSet` columns as before. Saved history is never modified.

## Tests

- `RoutineStartValuesTest` (`:shared` commonTest, 11 cases): the reported leg press and bench press, same-load reps records (including kg/lb round trips), other loads, warm-ups, assisted and unfinished sets, Follow the record pairing, bodyweight and assistance conventions, invalid Previous rows.
- `RoutineTargetPersistenceTest` (+3, Room on Robolectric): Previous from the newest earlier session and the record from an older quick workout, discarded workouts ignored, routine targets unchanged, sets without Previous, Follow the record sets, routines without history.

## Revision (2026-11-19, later the same day)

After testing the first version on the phone, the owner asked for the starting KG and REPS to "always be what is in the previous for all sets and exercises". This replaces two decisions above:

- **No record raise.** Working sets copy their Previous set exactly; reps no longer rise to the best at that load.
- **Follow the record sets copy Previous too.** Their saved record stays as the routine target (and still updates after finishing), but the workout starts from Previous. Without a Previous set they keep the record.

Unchanged: the pairing, sets without a Previous set keep their target, warm-ups copy Previous warm-ups, an assisted Previous set never becomes a regular load, and routine targets are never rewritten. `RoutineStartValues.forPlannedSets` no longer takes the history, and the repository reads only what the Previous column needs (the same two bulk queries).

## Revision 2 (2026-11-19): empty workouts and added sets

Testing on the phone showed the same gap in a workout started empty (the exercise added by hand): its two sets started at the record (Build 32). The owner asked for the same rule there. Now, in an active workout only:

- **Adding an exercise** (and replacing one): each of the two new sets copies the Previous working set at its place; a set without one falls back to the record, as in Build 32.
- **Adding a set** (any active workout, routine or empty): a new working set copies the Previous working set at its place (counting working sets only). Without one it keeps the old default: the last set's load and no reps, filled by the suggestion. A set whose assistance flag differs from its exercise keeps the old default.
- Saved-workout editing is unchanged: added exercises start blank and added sets take the last load.

Previous is found the same way as the column: the newest finished session of that exercise and load convention started before this workout, with a valid completed set. One new read query, `savedEntriesForExercise`.
