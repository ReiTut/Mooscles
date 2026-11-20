# Rest dock: the exercise name on its own line

2026-11-19. Approved by the owner in chat on the same day (options 1 and 4 of the investigation below).

## Problem

v1.5.9 capped the docked rest timer's label at two lines with an ellipsis. Inline (the ±15 s and skip buttons beside the time, at about 400 dp and font scales below 1.5) the label had only about 100 dp beside the ring, so "Descanso · Supino reto com barra" and similar names were cut. `WorkoutAccessibilitySweepTest.restDocked` failed on it (pt-BR, 412 dp, font scales 1.0 and 1.3, both themes) since v1.5.9.

## Change

- **Layout** (`RestTimerDock`): the name is on its own line across the whole dock, still up to two lines with an ellipsis; below it, the ring and the time (the body, the one "Open rest timer" button), and inline the −15 s, +15 s and skip buttons beside them as before. Stacked layouts (narrow docks or font scale ≥ 1.5) keep the buttons under the body. A tap on the name also opens the expanded timer (touch only, so a screen reader still has one button); a screen reader reads the name as text, then the body, then the buttons. The body no longer spans the name's line, so its bounds never overlap the buttons. The inline/stacked decision and its measured width are unchanged.
- **Label** (`WorkoutTimerSurfaces`): the dock shows the exercise name alone, without "Rest ·" / "Descanso ·" (the ring and timer icon say it is a rest; a screen reader hears "Open rest timer, …" first). The expanded timer's title keeps "Rest · name · set n of m".

The dock gets about one short line taller inline. The workout screen already measures the dock and pads the list by its height, so nothing is covered.

## Not changed

The compact timer, the expanded timer, the workout bar, notifications, timer logic and data.

## Tests

`RestTimerDockTest.theNameHasItsOwnLineAcrossTheDock` (new), `ActiveWorkoutScreenTest` dock label expectations, and the `WorkoutAccessibilitySweepTest` sweep, which now passes.

## Fallback

Before installing this change on the owner's phone, the previous Preview APK and a patch of this change were kept on the owner's PC (not in the repository), so the phone and the source can return to the earlier dock exactly.
