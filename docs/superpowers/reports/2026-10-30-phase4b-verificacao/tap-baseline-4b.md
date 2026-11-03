# Tap baseline of the OLD routine screens (phase 4B, Task 15 step 1)

Measured on 2026-10-30 at `4736ec5`, before the new Train tab and routine editor were hosted, by the temporary
`RoutinesTapBaselineHostTest` (Robolectric, over `RoutinesScreen` and `RoutineEditor`, counting every `performClick`;
typed text is not counted). The test was deleted together with the old screens in the hosting commit.

| Scenario | Taps today | How |
|---|---|---|
| Build a new routine with 5 exercises, from the Train tab to the editor holding all 5 | 13 | New routine (1), Save (1), open the routine card (1), then 5 x (Add exercise, pick the movement) = 10 |
| Reorder: the 5th exercise to the 1st place | 4 | Only "Move up" exists, one place per tap |
| Add a planned set equal to the one above | 1 | "Add Set" (the new set copies the last; no value to edit in the fake) |
| Start the workout of a routine | 1 | "Start" on the card (no active workout) |

Notes: the picker was filtered by typing (not counted) because the Robolectric dialog shows only the first rows;
on a device a scroll (not a tap) would stand in for it. Tasks 17 and 18 compare the new screens against these numbers.
