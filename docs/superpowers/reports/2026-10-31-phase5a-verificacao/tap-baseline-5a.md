# Tap baseline of the OLD records screens (phase 5A, Task 13 step 1)

Measured on 2026-10-31 at `2cbca45`, before the new Records tab and exercise progress were hosted, by the temporary
`RecordsTapBaselineHostTest` (Robolectric, over the real navigation graph with `RecordsScreen` and
`ExerciseHistoryScreen`, counting every `performClick`; typed text is not counted). The test is deleted together with
the old screens in Task 13.

| Scenario | Taps today | How |
|---|---|---|
| See the current record of an exercise (from the Train tab) | 2 | Records tab (1), tap the search field to focus it (1); the name is typed (not counted) and the card shows the record |
| From the record to the exercise history | 2 | Records tab (1), the record card (1), which opens "Movement history" |

Notes: the list is sorted by name, so the first card needs no scroll; with the whole catalog's records the target
would sit further down and a scroll (not a tap) would stand in for the search. Task 13 compares the new screens
against these numbers.
