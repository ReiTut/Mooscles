# Routine Start latency: measured changes and limits

Completed locally on 3 November 2026, on `codex/workout-startup-latency`, based on
`651bca47a321d5a67185a0358f0a667db895c4a7`. No commit, push, release or version change.
Phase 5/5B remains paused; existing Phase 5A code is unchanged.

## Outcome

Two small changes are retained:

1. Skip both active-workout historical PR calculations until a valid completed,
   non-warmup set exists. Runtime traces confirm less background presentation CPU.
2. Remove only the workout route's forward entry fade. Recorded images confirm that
   the editor is opaque in its first detected visible frame instead of overlapping
   Train while fading in. Back and other route transitions remain inherited.

**The full Start transition is not consistently faster in the trace-only comparison.**
Initial layout remains expensive, and first scrolling still has occasional graphics
stalls. These changes do not constitute a general stutter fix. No list rewrite,
thread-placement change, database shortcut or deferred-history pipeline was added.

## Checkout and implementation verified

The current KMP implementation differs from earlier staged-start branches. It has
`WorkoutSkeleton` placeholders, not the historical literal “Starting workout…” /
“Loading your routine in the background.” overlay. The current skeleton is preserved;
this task does not silently import an older implementation or claim to restore that text.

Relevant code, with line numbers at the end of this investigation:

- `shared/src/commonMain/kotlin/com/mooscles/MoosclesViewModel.kt:214`: successful
  compare-and-set protects Start; the serialized command acquires the training gate,
  checks active state, clears the timer only for a new session, reads preferences,
  starts/resumes the repository session and waits for its matching active UI model.
- `shared/src/commonMain/kotlin/com/mooscles/MoosclesViewModel.kt:62`: presentation
  already runs on a sequential background dispatcher with conflation. It was not
  running on the UI thread in this checkout.
- `shared/src/commonMain/kotlin/com/mooscles/data/WorkoutRepository.kt:572`:
  `startRoutine` creates the session/exercise/set snapshot transactionally. The
  repository's atomic full-table snapshot guarantee remains intact.
- `shared/src/commonMain/kotlin/com/mooscles/WorkoutPresentation.kt:191`: previously
  called both PR APIs even when a fresh workout had no eligible completed set. The
  new guard reuses the existing set grouping. It is the repetition-PR eligibility
  rule, which includes every weight-PR candidate as well as bodyweight/assistance.
  There is no new persistent cache or invalidation policy.
- `app/src/main/java/com/mooscles/ui/MoosclesApp.kt:369`: only `workout` forward entry
  uses `EnterTransition.None`; explicit `popEnterTransition = null` preserves Back.
  Cached Navigation 2.10.2 bytecode confirmed its default forward fade uses 700 ms.
- `screens/src/commonMain/kotlin/com/mooscles/screens/workout/ActiveWorkoutScreen.kt:199`:
  the editor already uses a granular lazy list. Off-screen exercise/set rows remain
  lazy; they are not all composed to satisfy the essential-data-ready milestone.
- `screens/src/commonMain/kotlin/com/mooscles/screens/workout/ActiveWorkoutRoute.kt:132`:
  loading/starting state selects the current skeleton. State propagation is async;
  two optimized recordings showed Train → skeleton → editor with no generic empty
  workout flash. This observation is not a universal scheduling guarantee.

## Controlled setup and reproduction

Physical Redmi Note 12 (23021RAAEG), authorized serial `4eb066eb`, Android 15/API 35,
1080×2400, active 120 Hz, Adreno 610. Project SDK/adb: `.tooling/android-sdk`.
No emulator was used for performance conclusions.

All performance builds were R8-optimized, resource-shrunk, nondebuggable/profileable
`performance` variants, ART speed-compiled, with the original signing certificate.
Only the isolated package `com.mooscles.startuplatency` was installed/updated. Its
updater was disabled. Production `com.mooscles` was never replaced, uninstalled,
force-stopped or cleared. Debug measurements are not used as performance evidence.

Synthetic opt-in routine: 8 exercises × 4 sets = 32 planned sets; 200 finished
workouts with the same 8×4 structure = 6,400 archived sets. This is a deliberately
history-heavy diagnostic fixture, not the user's own routine. Keyboard closed,
timer stopped, kg, English test-app locale, unchanged system language/density.

Procedure: open Train, accept one Start using a stationary 80 ms touch, capture
12 seconds of Perfetto, inspect the resulting 8-exercise/32-set model, and discard
only the synthetic session. First-use means first Start after force-stopping only
the isolated process; a warm Start follows discard in that same process. T0 is the
exact successful CAS after touch-up, not adb invocation or application cold start.

Primary comparison: three first/warm pairs per build, six Starts per build. The
earlier video comparison has another three pairs each for baseline and guard-only;
these are supporting evidence and are not pooled with trace-only results. Separate
optical clips and three first-scroll runs per final arm were captured. Host builds
and heavy offline analysis did not run during performance captures.

## Primary Start timeline

Milliseconds, median [range] for three samples of each kind. Small descriptive
samples, without significance or meaningful tail-percentile claims.

| Milestone | Baseline first | Final first | Baseline warm | Final warm |
|---|---:|---:|---:|---:|
| Skeleton frame-commit proxy | 40.70 | 52.98 | 38.18 | 49.51 |
| Complete essential model observed (8 exercises / 32 sets) | 207.47 [193.88–245.57] | 164.52 [159.84–187.45] | 163.19 [150.54–194.17] | 182.55 [179.95–193.82] |
| Editor frame-commit proxy | 316.11 [311.69–395.62] | 328.29 [299.01–328.68] | 238.98 [228.54–268.47] | 262.84 [261.05–273.02] |
| Presentation wall time | 40.64 [31.66–48.62] | 9.75 [5.98–18.67] | 33.07 [22.20–34.83] | 17.47 [13.49–24.72] |
| Presentation scheduled CPU | 34.84 | 9.75 | 31.51 | 16.96 |
| Largest initial owner layout | 71.85 | 71.99 | 43.22 | 47.47 |

The older recorded guard-only experiment independently measured presentation wall
medians near 29 → 6 ms. Both experiments support the intended reduction in work,
not a reliable end-to-end speedup. Largest primary first-800-ms main doFrame medians
were 93.08 → 97.29 ms first and 61.72 → 69.44 ms warm.

The asynchronous start-session span includes transactions, snapshot delivery and
waiting for UI state: medians 196.29 → 150.19 ms first, 155.04 → 170.64 ms warm.
It is not a pure database-I/O measurement. Composition and layout are nested and
their durations must not be added as independent categories. No matching GC pause
was recorded during the initial 800 ms; this does not prove zero allocation.

### Optical visibility and editing milestones

Real H.264 frames were decoded on Android and saved as PNGs. Route-local color
signatures inherited actual navigation opacity; current letterbox geometry was
checked against the images. Manual inspection of every recorded early frame in
the two final clips shows skeleton followed directly by an opaque editor, with
no Train/editor overlap or generic empty-workout flash.

| Clip | First visible editor → near-opaque editor |
|---|---:|
| Baseline first | 146.5 ms |
| Baseline warm | 226.0 ms |
| Guard-only first (old fade retained) | 98.6 ms |
| Final first | Same decoded frame (0 ms extra) |
| Final warm | Same decoded frame (0 ms extra) |

These support removing visual fade delay. Video PTS and exact CAS use different
epochs; no independently synchronized absolute Start→optical-visibility value is
claimed. A draw/frame commit alone is not optical proof. Two clips bound observed
behavior, not all possible schedules or devices.

Actual keyboard editing was accepted on the phone and checked independently of
frame markers, but the earliest possible interactive instant was not measured.
Do not reinterpret post-load UI automation latency as that instant. Complete
essential data was observed as 8/32 before the editor draw; traversing all eight
exercises encountered no second loading gate. Current presentation supplies previous
performance with the model; there is no separately staged secondary-complete event.
The global progress-index warmup remains independent and was not timed as part of
editor readiness. A fresh workout correctly has no live PR candidates.

## First-scroll comparison

Three downward then three upward 120 ms swipes, starting nominally 500 ms after
the Start command returns. All six traces contain six actual target-main Down/Up
pairs; comparisons use accepted+500…3000 ms, including fling settling. UI inspection
confirmed a return to the first exercise and untouched 0/32 completion; the separate
draft traversal reached the final exercise and returned with its draft intact.

| Scoped observation | Baseline (3 runs) | Final (3 runs) |
|---|---:|---:|
| Observed app deadline misses / surface frames | 18 / 655 (2.75%) | 17 / 620 (2.74%) |
| Median of run main doFrame p95 | 7.73 ms | 8.94 ms |
| Median of run surface p95 | 22.53 ms | 23.28 ms |
| Largest main frame across runs | 20.82 ms | 57.41 ms |
| Presentation calls during window | 0 | 0 |

No first-scroll improvement is established. One final run directly correlates a
43.02 ms RenderThread shader compilation (30.42 ms scheduled CPU) with a 54.61 ms
main drawing wait (only 0.046 ms CPU). Another wait overlaps further compilation.
The other five runs' largest shader compilation was 1.50–1.87 ms. This identifies
graphics preparation in that outlier, not PR work shifted into scrolling. Blocked
arm order and shared/vendor shader caches prevent attributing the outlier to the
code change. No caches or app storage were cleared to manufacture cold conditions.

## Focused validation

Freshly passed 70 relevant host tests, no failures/errors/skips:

- `WorkoutPresentationTest`: 33 (including two new eligibility/check/uncheck regressions).
- ViewModel performance: 5; presentation-thread: 3; command-loop: 3.
- `ShellNavigationHostTest`: 23; `DiscardNavigationHostTest`: 3, rerun after no-fade change.

Coverage includes duplicate Start, durable edits/reopen/resume, timer isolation,
failed Start retry, cancellation/continued commands, off-main latest-state delivery,
bodyweight/assistance and invalid/warmup-set eligibility. New tests compare against
unchanged domain calculations. CI host/shared-iOS minimum counts increased by two.

`shared:compileCommonMainKotlinMetadata`, debug assembly and Android lint passed.
Lint: 0 errors, 21 warnings. No iOS execution was possible on Windows; no push was
made to invoke CI. No unrelated full suite or broad benchmark infrastructure was run.
Five distinct offline video-decoder cases passed; two were repeated to save all early
frames. These are diagnostic decoding checks, not five app end-to-end UI tests.

Phone checks used the final optimized test app for keyboard editing, off-screen draft
preservation, set completion, timer decrement across background/reopen, process death
and Resume, and discard returning to Train. Repeated capture cycles also exercised
Start/discard repeatedly. Failure and cancellation remain covered by focused host
tests rather than destructive phone fault injection. Delayed secondary enrichment
was not artificially injected; this checkout has no separate staged enrichment gate.

One device test used an over-precision draft (22.12345). It stays visible but is invalid under
the existing two-decimal parser; completion retains the last valid 22.12. This rule
predates these changes. A separate matching-debug saved-database check verifies a
valid completed 22.25 kg × 11 pair exactly. Numeric editing code was not changed.

## Preservation and deliverable

Production read-only before/after snapshots have identical complete, canonically
ordered contents in all nine workout/catalog/routine tables, schema 6, and identical
local preference hashes. Updater preferences changed between snapshots; no command
wrote to production preferences or invoked its updater. Do not claim every preference
file was byte-identical. All existing PC backups remain untouched.

The temporary global keep-awake setting was restored to its saved value (15). Only
the newly created diagnostic package was stopped and disabled to hide its duplicate
launcher icon, retaining its data. Production Mooscles was not disabled or stopped.

Application identity, signing setup, schemas/migrations, numeric storage, timer
format and version remain unchanged. The unrelated `gradle.properties` edit has its
original SHA-256 `EB0301005B9C4F30C1F4924D25F68B9C2BC6631F2C1312E3CC27373CEA8C9ACB`.
Diagnostic hooks/fixtures/assets/traces are only in ignored `.tooling` source copies;
none are in the production source or delivery APK.

Fresh debug APK:
`D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk`

- Package: `com.mooscles`; versionName `1.5.2`, versionCode `23`.
- Verified original signer SHA-256:
  `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.
- Size: 24,963,674 bytes. SHA-256:
  `7a1300bbc47018064c37fc2366f19a67460d1ec55bc172c52fbf0a81be9f0a08`.
- Incremental packaging initially exceeded the APK budget. Clean app packaging
  resolved the documented inflation; the final font/icon/growth budget passes.

Local evidence root:
`D:\Programing\Mooscles\.tooling\startup-latency-20261102`

See `FINAL-TRACE-REPORT.md`, `natural-analysis/`, `scroll-analysis/`, `captures/`,
`decoded/`, `final-video-decode-summary.txt`, `focused-test-summary.json`,
`navigation-tests.json`, `functional-summary.txt`, `persisted-valid-set.json`,
`production-table-comparison.json`, `final-debug-apk.json`, and build/lint logs.

## Remaining limits and continuation

FrameTimeline is sparse around initial rendering. All 18 primary/scroll traces have
20–37 negative-timestamp drops; two baseline scroll traces have 60/92 systrace parse
failures. Anchors were recovered/in bounds, but frame counts are incomplete observed
counts, not complete dropped-frame rates. Main frame, surface frame and GPU histogram
figures are not interchangeable. Video recording can itself affect graphics waits,
which is why trace-only measurements are primary. End-of-capture PSS showed no evident
regression, but is not peak memory/allocation evidence. Results from one synthetic,
history-heavy routine on one phone do not establish effects on all user routines.

The smallest confirmed repairs are retained; no speculative list rewrite is included.
Next performance work, if requested, should narrowly isolate initial editor layout/
text construction and repeat first-scroll graphics measurements with alternating
arm order and shader-cache limitations recorded. Earlier usable editor timing still
needs independently synchronized optical/input measurements before claiming a full
Start-latency improvement.
