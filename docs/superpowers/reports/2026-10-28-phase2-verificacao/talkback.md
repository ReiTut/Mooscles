# TalkBack notes: Settings and the SetRow gallery page

Task 16, Step 3. Emulator `emulator-5554` (Android 15), TalkBack 15.0.0.639625893, build `4d33c32`, system language English, font scale 1.0.

## How this was read, and what it is not

TalkBack is installed on this image, and I turned it on with `settings put secure enabled_accessibility_services …TalkBackService` (and `accessibility_enabled 1`). I could not drive it from the Mac:

- Gestures injected with `adb shell input` do not go through TalkBack's touch exploration here. A single `input tap` on "Light" selected the Light theme directly instead of moving accessibility focus to it, and right swipes (`input swipe`, 100–120 ms) did not move the focus past the first item.
- The keyboard shortcut for the next item (`input keycombination KEYCODE_ALT_LEFT KEYCODE_DPAD_RIGHT`) did not move the focus either.
- TalkBack does not write what it speaks to logcat at its default log level (ERROR). Turning on verbose logging needs a privacy confirmation in TalkBack's developer settings. I backed out of it, because without a way to move the focus it would only have logged the window title. The log level is still ERROR.

So **this is not a TalkBack listening session.** The reading order below comes from the accessibility node tree (`uiautomator dump`, which lists the nodes Compose hands to TalkBack, in tree order), grouped the way TalkBack groups them: each clickable, checkable or focusable node is one stop and absorbs its descendants' text. Roles and custom actions come from the source code (the dump doesn't show `roleDescription`, headings or custom actions), and the custom actions are checked by `SetRowTest.exposesFiveCustomActionsThatCallBack`, which passed on this emulator in Step 5. A real pass with TalkBack (a person swiping through it, or a device where injected gestures reach TalkBack) is still needed before saying the reading order was verified with TalkBack.

TalkBack was turned off again (`enabled_accessibility_services null`, `accessibility_enabled 0`; `dumpsys accessibility` shows no enabled services and `touchExplorationEnabled=false`).

## Settings (en, dark, active workout, rest timer done)

Node-tree order, top to bottom, merged across six scroll positions (`set-1…6` dumps). The role in brackets comes from the source: `RadioRow` → `Role.RadioButton`, `SwitchRow` → `Role.Switch` (Compose exposes it as a View with the role description "Switch"), `SettingsRow` with a click → `Role.Button`, `SectionHeader` and `TopBar` title → `heading()`.

1. Appearance [heading]
2. System [radio button], not checked
3. Light [radio button], not checked
4. Dark [radio button], checked
5. Weight units [heading]
6. Kilograms · kg [radio button], not checked
7. Pounds · lb [radio button], checked
8. Records keep their original precision when you change units.
9. Automatic rest [heading]
10. Start after completing a set [switch], on
11. Completing another set restarts rest using that exercise’s duration.
12. 2:30, Default rest duration, min, Minutes, then seconds · maximum 5:00 [edit box]
13. Each exercise can use its own rest duration.
14. Your data [heading]
15. Save a copy of your workouts, routines and settings to a file, or restore one.
16. Export data [button]
17. Import data [button], disabled
18. Finish or discard the active workout to import
19. Backups contain your full workout history without encryption. Keep the file somewhere private.
20. App updates [heading]
21. Installed 1.4.5 · build 16 [button]
22. Automatic updates, Download available updates automatically [switch], on
23. Wi-Fi-only downloads, Wait for Wi-Fi before downloading an APK [switch], on
24. Include prereleases, Get preview and debug releases as well as stable releases [switch], off
25. Channel: stable. Drafts and prereleases are excluded.
26. Ready to check
27. Published updates come from ReiTut/Mooscles on GitHub.
28. Last checked
29. Not checked yet
30. Check for updates [button]
31. Checks run when you open the app and about every six hours in the background. …
32. Android may ask you to approve an installation. …
33. Component gallery [button] (only after the seven taps)
34. Just you and your progress [heading]
35. Your routines, workout history, and records stay on this device. … (one long node)
36. Open-source licenses [button]

After the content the tree has: Back [button], Settings [heading]; the old rest timer dock (Rest complete, 0:00 min, Rest timer progress, −0:15 disabled, Start rest timer, +0:15, Reset rest timer, Skip rest timer); the tabs Train, Workout, Library, History, Records.

What I'd compare against the spec ("a ordem de leitura segue a ordem visual"):

- In the node tree, the top bar (Back, "Settings") comes **after** the first screen of content, not before it. Compose usually reorders siblings by position for TalkBack, so it may still be read first; this is exactly the point a real TalkBack pass has to confirm.
- Rows read as single stops with their state, and the radio rows in each group are separate stops. The groups don't use `selectableGroup()`, so TalkBack won't say how many options a group has.
- "Installed 1.4.5 · build 16" is a button (it counts the seven taps). TalkBack will announce it as a button and offer "double-tap to activate", and a double tap does nothing visible.
- The default-rest field reads its value before its label ("2:30, Default rest duration, min, …").
- "Last checked" and "Not checked yet" are two stops for one fact.

## SetRow gallery page

Gallery, Set row page, English and Portuguese (Brasil) through the gallery's own language control. Each row is one merged node (`semantics(mergeDescendants = true)` with a `contentDescription`). The check, the set number and the previous cell are cleared (`clearAndSetSemantics {}`), so they are not separate stops.

| Row | en (as exposed) | pt-BR (as exposed) |
| --- | --- | --- |
| 1, warm-up, pending | Set 1 of 6, previous 55 kilograms, load 60 kilograms, 10 reps, not completed | Série 1 de 6, anterior 55 quilos, carga 60 quilos, 10 repetições, não concluída |
| 2, editing | Set 2 of 6, previous 77.5 kilograms, load 80 kilograms, 6 reps, not completed | Série 2 de 6, anterior 77,5 quilos, carga 80 quilos, 6 repetições, não concluída |
| 3, completed | Set 3 of 6, previous 80 kilograms, load 82.5 kilograms, 6 reps, completed | Série 3 de 6, anterior 80 quilos, carga 82,5 quilos, 6 repetições, concluída |
| 4, completed with record | Set 4 of 6, previous 80 kilograms, load 85 kilograms, 6 reps, completed, personal record | Série 4 de 6, anterior 80 quilos, carga 85 quilos, 6 repetições, concluída, recorde pessoal |
| 5, drop, no previous | Set 5 of 6, load 70 kilograms, 12 reps, not completed | Série 5 de 6, carga 70 quilos, 12 repetições, não concluída |
| 6, failure | Set 6 of 6, previous 65 kilograms, load 70 kilograms, 12 reps, not completed | Série 6 de 6, anterior 65 quilos, carga 70 quilos, 12 repetições, não concluída |

Page order: Back, "Set row" (heading), the gallery controls (Theme, Text size, Language, Motion chips), "Set rows in every state" (heading), rows 1–6 in visual order, then the app's tabs.

**Custom actions** (from `SetRow.kt:173–178`; the count and callbacks are covered by `SetRowTest`, green in Step 5): Complete set / Concluir série (Mark set as not done / Marcar série como não feita once completed), Edit load / Editar carga, Edit reps / Editar repetições, Change type / Mudar tipo, Remove set / Remover série. That is the spec's list of five.

**Against the spec** (spec, Acessibilidade: "Série 4 de 5, anterior 80 quilos por 6, carga 85 quilos, 6 repetições, não concluída"):

- Matches: one node per row, set n of total, load in spoken units, reps, completion status, "recorde pessoal", the five custom actions, both languages.
- **Previous is missing its reps.** The gallery reads "anterior 80 quilos", and the spec asks for "anterior 80 quilos por 6". `SetRow` reads `previousSpoken`, and the gallery's sample builds it from the load alone (`SetRowPage.kt`: `previousSpoken = spoken(load)`), so the gap is in the sample, unless the component should build the string itself.
- **The set type is not spoken.** Rows 1, 5 and 6 show W, D and F, and the description doesn't say warm-up, drop or failure.
- **The check is not a toggle of its own.** The spec says "O check é um alternável ('Concluir série 4', marcado ou não)". In the component the check is cleared from semantics, so completing a set with TalkBack only works through the custom actions menu. The row node itself has no click action.
