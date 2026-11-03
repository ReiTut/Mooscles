# What I saw on the emulator (input for the critique round)

Task 16, Steps 1–5, build `4d33c32`, emulator-5554 (Pixel 8 profile, 1080×2400, Android 15). Each item names the screenshot that shows it. These are observations only: nothing was changed in the product code. The priority is my first guess for the critique round, not a decision.

"App shell" means code that phase 2 did not rebuild (the old `NavigationBar`, the old timer dock, `MoosclesApp`), so it is probably out of scope for Task 16, but it sits on top of the new screens.

## Settings

1. **High: the old rest timer dock clips its own buttons at 200%.** "−0:15" and "+0:15" render as "−0:1" and "+0:1", and the exercise name truncates to "Conventional b…" (`settings-dark-pt-200*.png`; the crop is easiest to see in `settings-dark-pt-200-h.png`). App shell, but it covers the bottom of Settings during a workout.
2. **High: the tab labels break mid-word at 200%:** "Worko / ut", "Librar / y", "Histor / y", "Recor / ds" (`settings-dark-pt-200*.png`). App shell.
3. **High: very little content shows at 200% during a workout.** The top bar, the timer dock and the tab bar together leave about 45% of the screen height for content (`settings-dark-pt-200*.png`).
4. **Medium: the app is in mixed languages in pt-BR.** Settings is in Portuguese, but the tabs (Train, Workout, …) and the timer dock ("Rest complete", "Conventional barbell deadlift") stay in English (`settings-light-pt*.png`, `settings-dark-pt*.png`, `licenses.png`). Expected until the other screens move to resources, but it is what a pt-BR user sees.
5. **Medium: in light theme the tab bar doesn't match the page.** It has a lavender Material default surface under the green-grey Giz page (`settings-light-en*.png`, `settings-light-pt*.png`). App shell.
6. **Medium: the "App updates" block is crowded.** "Installed 1.4.5 · build 16" sits alone with a large gap, while "Last checked / Not checked yet" are two tight lines with no spacing, right above the button (`settings-dark-en-c.png`, `settings-light-en-c.png`). The two explanatory paragraphs under "Check for updates" run together with no gap between them (`settings-dark-en-d.png`).
7. **Medium: the license text keeps its hard line breaks.** The OFL is shown with the file's original newlines, so lines wrap raggedly ("…to stimulate worldwide / development…"), and the dashed rules wrap onto a second line as a lone "-" (`licenses.png`).
8. **Low, a11y: the hidden-gallery tap target is a button.** "Installed 1.4.5 · build 16" is exposed as a button (see `talkback.md`), so TalkBack offers "double-tap to activate" and nothing visible happens.
9. **Low, a11y: radio groups aren't grouped.** The Appearance and Weight units radio rows have no `selectableGroup()`, so a screen reader doesn't announce the group or its size (`talkback.md`).
10. **Low, a11y: reading order needs a real TalkBack check.** In the accessibility node tree the top bar (Back, "Settings") comes after the first screen of content (`talkback.md`). It may still be read first; it needs a real TalkBack pass.
11. **Low: the field reads its value before its name:** "2:30, Default rest duration, min, …" (`talkback.md`).
12. **Low: the disabled "Import data" button is very pale in light theme.** The reason it is disabled is in the line below, which is fine (`settings-light-en-b.png`).
13. **Low: the icon in "Check for updates" doesn't scale at 200%.** The text grows and the icon stays at 100%, so the button looks bottom-heavy (`settings-dark-pt-200-g.png`, `-h.png`).

## Gallery (shell and controls)

14. **High, Step 4: the gallery ignores the system "reduce motion".** With `animator_duration_scale 0`, Foundations still reads "reducedMotion: false" (`animator-0-foundations-motion.png`). `GalleryControls.reducedMotion` starts at `false` and overrides `rememberSystemReducedMotion()` (details in `animator-0.txt`).
15. **Medium, motion: one-frame flash at the start of the check animation.** The finished check is drawn at full size and full opacity for one frame, then jumps back to scale 0.92 (spring) or to transparent (fade) (`animator-check-spring-frames.png`, `animator-check-reduced-frames.png`).
16. **Medium: the gallery controls stay dark when the page is light.** With Theme set to Light, the top bar and control chips keep the app theme (dark) while the page under them is light, which makes the light pages hard to judge (`gallery-*-light.png`).
17. **Medium: the gallery controls take about 30% of the screen and don't scroll away.** With the tab bar, a page gets about 1,350 of 2,400 px, so most pages need 2–5 screenshots (`gallery-foundations-*.png`). There is also no divider or spacing between the controls and the page, so content scrolls into the controls with a hard edge (`gallery-foundations-dark-2.png`, `gallery-states-dark-2.png`).
18. **Low: the language chips run off the right edge** ("Português (Bras…") with no fade or other hint that the row scrolls (every gallery screenshot).
19. **Low: the page list rows look like plain text.** They have no chevron, divider or background (`gallery-index-dark.png`, `gallery-index-light.png`).
20. **Low: tapping the current tab from inside the gallery does nothing.** It doesn't pop to that tab's root (seen when tapping Workout from Gallery → Set row). App shell navigation (`restoreState`), not phase 2.

## Gallery pages

21. **High, a11y and spec: the set check is not its own toggle.** The spec says the check is a toggleable ("Concluir série 4", checked or not). In `SetRow`, the check is cleared from semantics and the row has no click, so with TalkBack a set can only be completed through the custom actions menu (`talkback.md`, `gallery-setrow-dark.png`).
22. **Medium, a11y: "previous" is read without its reps.** The row reads "previous 55 kilograms", where the spec has "anterior 80 quilos por 6". The gallery sample builds `previousSpoken` from the load only (`talkback.md`).
23. **Medium, a11y: the set type is not spoken.** W, D and F (warm-up, drop, failure) are visual only; the row description doesn't say the type (`gallery-setrow-dark.png`, `talkback.md`).
24. **Medium: at 130% the "previous" cell wraps mid-value**, "55 kg × / 10" and "77.5 kg × / 6", below the 1.5 threshold where the two-line layout starts (`gallery-setrow-130.png`).
25. **Medium: the error toast looks the same as an info toast.** "Could not save the file" has no error icon or color, but the spec says an error is shown with an icon and a message (`gallery-overlays-dark-errortoast.png`, `gallery-overlays-light-errortoast.png`).
26. **Medium: the error banner repeats itself and mixes colors.** It says "Error: Could not save. Try again." and then has a "Try again" action, and the action is green on a red surface (`gallery-states-dark.png`, `gallery-states-light.png`).
27. **Medium: the skeleton is nearly invisible in light theme.** The blocks are barely distinguishable from the page background (`gallery-states-light-2.png`).
28. **Medium: the numeric field error says "Enter a name"** under a reps field with 999. The sample reuses the text-field error (`gallery-workoutinputs-dark.png`).
29. **Medium: the stepper shows "20.0".** It uses `Double.toString()`, while loads elsewhere are "60" and "82.5"; in pt-BR this would read "20,0" (`gallery-workoutinputs-dark.png`).
30. **Medium: the PR toast covers the content at the top of the page,** including the section header and half of the first PR badge (`gallery-records-dark-toast.png`).
31. **Low: section headers have two different insets.** SectionHeader pages (Buttons, Chips, Fields, States, Set row …) indent the header about 16 dp more than the content under it, while Foundations and Icons headers align with the content (`gallery-buttons-dark.png` vs `gallery-icons-dark.png`).
32. **Low: the gallery samples are unlabeled.** The button and chip variants are shown with no names (eight "Continue" buttons, three rows of "Chest" chips), so you can't tell which is primary, tonal, ghost, or which chip kind is which (`gallery-buttons-*.png`, `gallery-chips-*.png`).
33. **Low: the icon-button row is misaligned.** The fourth "+" sits about 8 px lower than the others (`gallery-buttons-light-2.png`).
34. **Low: "Paused" looks like "Rest".** The paused dock differs from the running one only by its label; the ring and colors are identical (`gallery-timer-dark.png`, `gallery-timer-light.png`).
35. **Low: empty values use a hyphen.** The previous cell shows "-" where an en dash or "—" would read as "no value" (`gallery-setrow-*.png`, `gallery-exercisecard-*.png`).
36. **Low: the media tile is blank while loading.** The first exercise card shows an empty green square with no placeholder icon (`gallery-exercisecard-dark.png`, `gallery-exercisecard-light.png`).
37. **Low: the light-theme PR badge and e1 surface barely stand out.** The soft PR badge (pale peach on near-white) and the `e1` elevation sample are hard to tell from the background (`gallery-records-light.png`, `gallery-foundations-light-5.png`).
38. **Low: the Fields samples have different widths.** The text fields are about 40% wide while the rest-timer field is full width (`gallery-fields-dark.png`).

## Not problems (checked)

- Settings keeps the same non-default preferences after the upgrade, in both themes and both languages (`upgrade.md`).
- No clipping in Settings' own content at 200%: rows, switches, buttons and the field wrap correctly (`settings-dark-pt-200*.png`).
- The SetRow two-line layout at 200% works: numbers are not truncated, and the check stays on the load/reps line (`gallery-setrow-200*.png`).
- Contrast pairs on the Foundations page meet AA in both themes: text pairs ≥ 4.86, the lowest non-text pair is borderStrong / bgSurface at 3.79 (dark) (`gallery-foundations-*.png`).
- Dialogs and the sheet look right in both themes, with one solid action and the destructive action in red (`gallery-overlays-*-dialog.png`, `-destructive.png`, `-sheet.png`).
