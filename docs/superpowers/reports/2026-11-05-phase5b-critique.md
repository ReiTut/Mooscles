# Phase 5B — screen and compatibility review in progress

Reviewed on 5 November 2026, feature source `9a0740a` and metadata-cache follow-up `26a8889`, Android 15/API 35 emulator
`emulator-5560` with host GPU. This is a checkpoint review, not phase completion.
Large local evidence stays in ignored `.tooling/phase5b-20261104/`.

## Findings addressed

- At 320 dp and 200% text the share preview's wrapping global page-count caption consumed
  the remaining image area. It now scrolls with destination instructions; per-page labels stay
  with their images. Four width/theme cases pass, including explicit access to each destination.
  Exports retain their original artwork, contents, dimensions and pagination.
- History scroll restoration was being overridden on first composition and could be clamped
  by a temporary one-item loading list. Initial loading now stays outside the lazy list; only
  later filter results request the top. A regression reproduced the old offset loss and now passes.
- Progress state was lost when its segment left composition. Its saveable state provider now
  retains period, chart-table view and list position. The round-trip regression now passes.
- Saved-detail actions follow the exercise list. The normal-font history card uses a left
  day/weekday column; at large text sizes its date moves above the content to preserve width.
- Deletion waiting and per-set failure recovery were reviewed and tested separately from
  visual behavior. A timeout permits retry while late removal still returns to History.
  Unrelated failures no longer clear another operation's pending state.

## Inspected artifacts and tradeoffs

- Calendar captures: `calendar-captures/files/calendar-*-320-*.png` and 412 dp variants.
  The 200% six-week grid keeps every number within its cell and marked targets at least 40 dp.
  Month headings wrap vertically rather than truncate. This makes the calendar taller at large
  text sizes; collapse and ordinary month-navigation buttons remain available.
- Preview captures: `captures/flow-captures/phase5b-preview-*-2x.png`. The test page slots are
  synthetic, so these prove layout and action reachability rather than native PNG decoding.
  Decoding and intent ownership are covered separately by the native host integration tests.
- Actual freshly exported PNGs: `app/build/outputs/share-previews/short-pt-BR-1.png` and
  `long-workout-3.png` were inspected. The supplied M/barbell wordmark, Portuguese number
  precision, PR statistic, final-page header, exercise order and page number remain visible,
  without overlap. Renderer tests cover all pages and pixel/geometry assertions.
  Copies retained before the final clean build are in ignored `exported-pngs/` under the evidence root.
- Actual optimized-app offline captures (`offline-history.png`, `offline-progress.png`,
  `offline-detail.png`, `offline-preview.png`) show Portuguese/pounds with 208 completed workouts
  and an independently preserved active workout. Calendar, full workout labels, period totals,
  saved set values and the real two-page PNG preview render without overlapping controls.
  The preview image viewport is scrollable; it does not imply that the entire long card fits at once.
- New accessibility coverage checks spoken labels, whole numbers, ordinary 48 dp targets,
  the calendar's approved 40 dp exception, charts and their data-table alternatives. These
  checks passed at EN/default and PT/320 dp/200% in both themes. They do not prove actual
  TalkBack/VoiceOver speech or real-device gestures/haptics.

## Compatibility review

The independent source review found unchanged schema 6, preferences/backup/updater contracts,
application identity and version. Native timer implementation and saved data are untouched.
Saved editing remains scoped to historical IDs and uses the existing exact-value/draft machinery;
automatic rest still requires an ACTIVE workout. No new network code or runtime dependency was added.
The unused Material icons library was removed after its last production callers disappeared.
Earlier clean APKs passed font/icon/growth budgets and verified with the original signing certificate;
the source-matching final artifact is recorded in the verification report.

## Verification status and limits

Full emulator design-system coverage passed 428 cases; the 1,009-case screens run passed every
functional case but failed its debuggable index timing check. The focused post-cache timing rerun
passes, and the expanded saved-editor matrix passes after its viewport correction. Earlier iOS compilation/simulator tests and narrow
calendar/detail/preview captures passed; final-source CI is recorded separately in the verification report.

The isolated offline on-top comparison passed both 14-check stages. The optimized index cache has
a measured standalone CPU benefit. Earlier emulator traces show graphics-handoff waits and some layout outliers.
The approved physical-phone comparison isolated the muscle map as a contributor to Overall Progress scrolling.
The scoped layer passed the phone's scrolling target. The pre-layer emulator result remains above target.

Both physical-phone map captures show the full map, highlights, outlines and legend at matching proportions.
The captured scroll positions differ by 4 px, so this is visual review rather than a pixel-equality assertion.
The test used ADB gestures on a nominal 120 Hz Android phone. Physical TalkBack/VoiceOver, finger gestures,
haptics and S23-specific performance remain unverified. Future phone-dependent tests require explicit approval.
Final verification is Android-only.

## Saved-editor correction, final Android rerun

The expanded matrix exposed a zero-height editor at Portuguese / 320 dp / 2.01 font scale:
the editing title wrapped almost one character per line between Back, Done and the menu.
`7fc1b2e` follows the existing design-system long-heading pattern: actions remain in the bar,
and the full editing heading moves into scrolling content. Reading mode is unchanged.
The 28 detail regressions and all 32 looks of the saved-editor/preview case passed (29 methods,
194.013 seconds). A direct 56 dp minimum viewport assertion now diagnoses this failure promptly.

Both `final-editor-captures/phase5b-saved-editor-pt-320-2x-light.png` and its dark variant were
inspected. Back/Done/menu, exact intermediate `22,` text, repetitions focus and stepper controls
remain usable. These are deliberately scrolled editing captures; content beyond the viewport
remains reachable by scrolling. They do not prove real TalkBack speech or physical keyboard behavior.
