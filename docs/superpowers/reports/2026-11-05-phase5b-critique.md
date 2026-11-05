# Phase 5B — screen and compatibility review in progress

Reviewed on 5 November 2026, feature source `9a0740a`, Android 15/API 35 emulator
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
The clean APK passes font/icon/growth budgets and verifies with the original signing certificate.

## Still pending

Final full emulator suites/captures, iOS compile/simulator CI and its visual review, optimized
History/progress measurements, and the isolated offline on-top data comparison remain pending.
Phone-specific checks require the owner's explicit approval and a complete work stop while waiting.
