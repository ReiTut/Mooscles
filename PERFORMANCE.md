# Workout responsiveness verification

## Version 1.4.6 optimizations measured on 24 October 2026

The version 1.4.6 changes retain UI-thread presentation while removing unnecessary
historical PR preparation. An active workout without eligible completed working
sets returns before historical baseline calculation. A presenter-owned historical
analysis shares an index, weight/repetition baselines, completed achievements and
performance histories. Actual historical row contents invalidate the analysis,
including edits, deletions, variations and chronology changes. Defensive list
copies prevent mutable caller data from silently changing the cache key. Rare
nontransitive four-ULP load comparisons retain the original ordering fallback.
No database schema, saved workout format, application ID or signing identity changed.

Set editors now save one immutable draft snapshot instead of sixteen separate
registry entries, with one acknowledgment/unit synchronization effect. TextField
appearance and structure remain intact. Blank/invalid drafts, delayed echoes,
completion flags and exact stored kilograms remain independent of rounded display
text. Fifteen focused regressions were added, including an independent frozen
v1.4.5 oracle for PR/record/history behavior. The complete host suite passes
**378 tests in 55 suites**, with zero failures/errors/skips; debug lint reports
**0 errors and 21 warnings**. Debug and instrumentation APK builds pass.
Three focused checks also passed on the physical phone: real saved-session input,
exact kg/lb values and Activity recreation; folder/repeated-discard navigation;
and real keyboard/focus/timer geometry. These functional checks used the isolated
debug measurement variant, separately from the optimized performance captures.
The user's installed app and all eight database tables plus both preference files
were preserved. Temporary keep-awake changes were restored.

Measurements used a real **Redmi Note 12 (Android 15, Adreno 610)** with its existing
font scale 1.1, native approximately 120 Hz display, and the R8-optimized,
nondebuggable, shell-profileable performance variant. A separate locally built
measurement package protected the user's installed application and data. The same
8-exercise/32-set routine and 200 finished synthetic workouts were used in ABBA
order, with first and warm Start per batch and identical repeated fast swipes.
ART compilation was `speed`. These are in-app Start interactions after Train was
ready, not cold-launch times. Temporary diagnostics live only in ignored source copies.

| Phone observation | Baseline | Optimized local source |
| --- | ---: | ---: |
| First Start to editable-row frame-commit proxy, median | 312.70 ms | 222.54 ms |
| Warm Start to the same proxy, median | 218.65 ms | 150.62 ms |
| Presentation median | 33.13 ms | 4.44 ms |
| PR preparation median | 25.62 ms | 0.068 ms |
| Initial editor composition, median | 29.52 ms | 15.25 ms |
| Initial editor layout, median | 53.57 ms | 41.13 ms |
| Pure-scroll app deadline misses | 84/3,097 (2.712%) | 84/3,086 (2.722%) |

These trials show about **29-31% shorter Start readiness** and lower initial editor
construction cost. They do **not** establish lower overall scrolling jank. Warm
editor composition was slightly slower, and the small descriptive scrolling CPU
reduction is not a reliable whole-screen smoothness improvement. No broader
rendering redesign was made. The startup endpoint is a rendering-readiness proxy,
not an exact human-perceived usability timestamp.

Limits: one phone, small synthetic sample, identical diagnostic observer overhead
in both builds. Every trace includes 19-39 dropped negative-timestamp events and
36 setup notices; one candidate first-start trace also has three systrace parse
failures. Dropped event types could not be attributed. Input anchors, completed
first-field frame-commit markers and scheduler data are present in all eight
captures. Six have matching first-editor surface-token links; B1-warm and B2-warm
do not. Their readiness proxies are measured, but compositor/display endpoints
remain unverified and the missing-link cause is unresolved. No `data_loss`
severity flags were observed. Expected app processing budget
(11.667 ms) differs from the approximately 8.353 ms display cadence. Exact results,
quality flags, device checks and raw traces are retained locally in the ignored
optimization evidence and report, outside release assets. These measurements were
completed before release preparation; the measured local build used versionName
1.4.5 / code 16. Version 1.4.6 packages the same runtime changes as Build 17.

The following sections are historical measurements of the earlier implementation.
## Earlier UI-thread preparation and preference filtering

On 23 October 2026, workout/card presentation was moved from
`withContext(Dispatchers.Default)` back into the flow collected by
`viewModelScope` on the main thread. Repository loading remains in the background.
`AppPreferences.flow` now applies `distinctUntilChanged()` after projecting the
shared DataStore into display preferences. Timer start, clear and reset writes
therefore do not emit unchanged settings or trigger another presentation.
Existing caches, timer isolation and lazy inputs remain intact.

Fourteen focused host tests passed: `AppPreferencesTest` (5),
`AppViewModelPerformanceHostTest` (5), and `WorkoutPresentationTest` (4).
After adding an explicit timer-clear assertion, only the five preference tests
were rerun, successfully. Debug lint passed with zero errors / 21 warnings.
Debug, instrumentation and R8-optimized performance APKs built. The opt-in
additive `ThreadComparisonFixtureTest` passed once on the emulator; this is setup
validation, not a passing full instrumented UI suite.

Both comparison APKs use `com.mooscles`, version 1.4.3 / code 14 and the existing
certificate. Tests targeted `emulator-5554` / `Medium_Phone`, Android 15 / API 35,
x86_64, 1080 x 2400 at 60 Hz. Each non-debuggable, profileable APK was speed
compiled and its installed hash verified. Six warm-process routine starts used
an ABBAAB order, the same eight-exercise/four-set prescriptions and identical
scroll gestures. Each test session was saved, so history increased during the
comparison. No storage was cleared and no app was uninstalled.

| Diagnostic | Before: background | After: UI thread + filtering |
| --- | ---: | ---: |
| Timer-reset presentation, same large active session | 1 worker pass, 23.87 ms | 0 passes |
| Presentations per routine Start, every trial | 3 worker passes | 1 main-thread pass |
| Start command median, three trials each | 143.47 ms | 165.82 ms |
| Initial 1.4 s app deadline misses | 20/115 (17.39%) | 15/121 (12.40%) |
| Initial maximum main frame, median | 51.19 ms | 61.72 ms |
| Pure-scroll app deadline misses | 68/1,045 (6.51%) | 81/1,048 (7.73%) |
| Pure-scroll frame pipeline p95, median | 40.90 ms | 43.69 ms |

**The redundant work is eliminated, but a consistent performance improvement
was not demonstrated.** Start timing and paired frame results were mixed.
After-change main-thread presentation spans lasted 26.70-40.50 ms. The Start
command ends at prepared-state acknowledgment, not the first usable frame.
Frame pipeline durations include rendering and waits. The two changes were
measured together; their individual performance effects cannot be separated.
These small emulator samples do not establish physical-phone performance.
All six traces had zero positive error or data-loss counters.

Database comparison found every original exercise, routine and previously
finished workout unchanged. The pre-existing synthetic active session was
finished and saved, and six comparison sessions were retained. SQLite integrity
passed. The updated optimized APK was left installed. Raw traces, installed
APKs, findings, data comparisons and `REPORT.md` are under
`app/build/outputs/thread-comparison/20261023/`, excluded from source control.
These implementation measurements used Build 14 before release preparation.
The same behavior is packaged as version 1.4.4 / Build 15 for distribution.

## Earlier emulator verification, before the local thread change

Android Studio's running `Medium_Phone` emulator was tested directly as
`emulator-5554`: Android 15 / API 35, x86_64, 1080 x 2400 at 60 Hz. Its SDK and
adb are the project's `.tooling/android-sdk`, confirmed against `local.properties`
and Android Studio's SDK configuration. The app was initially absent; all
workouts used for this run were synthetic. No app was uninstalled or storage
cleared.

The debug and optimized APKs built, 347 host tests passed, and debug lint had
zero errors / 21 warnings. `:app:connectedDebugAndroidTest`, restricted to
`WorkoutRepositoryTest` and `RestTimerPersistenceTest`, passed all 16 tests on
that serial with APK retention enabled. The full `UiFlowTest` was attempted but
did not complete: its initial database-versus-Compose readiness race was corrected
in the test, then subsequent attempts lost the automation connection and ended
with `UiAutomation.disconnect` / `DeadObjectException`. This is not a passing UI
suite, and the underlying connection failure was not established.

Measurements used the existing `performance` variant: R8 optimized, resource
shrunk, non-debuggable and shell-profileable, `com.mooscles` 1.4.3 / Build 14,
with the existing signing certificate. The installed APK was pulled back and
its hash matched the built APK. Tests exercised 80 exercise instances with
12 sets each, initially 200 finished sessions, and a subsequent saved test
session. Manual checks covered fresh routine startup, resume, editing, the rest
countdown, full-list traversal, finishing/saving and reopening. The edited
22 kg x 11 completed set survived reopening and was saved to history; the next
session opened with the routine's independent 20 kg x 8 prescription.

The final database passed `PRAGMA quick_check`. Comparing every original row in
all seven exercise/routine/workout tables against the final copy found zero
missing or changed rows. Test-created sessions were retained. The current
optimized APK was left installed and the fresh test workout open.

| Emulator observation | Verified result |
| --- | --- |
| Fresh Start command to prepared-state acknowledgment | 297.545 ms; not first usable-frame latency |
| Presentation during Start | Three spans on `DefaultDispatch` workers; none on main |
| Main frame immediately after Start acknowledgment | 95.679 ms wall time, including 76.758 ms running and 64.035 ms in UI measurement |
| Three fast-scroll diagnostic trials | 139 / 972 App Deadline Missed flags, 14.30% weighted |
| Fast-scroll frame pipeline p95 | 42.952-47.220 ms across those three trials |
| Timer scroll | 9 / 366 app deadline misses; no full presentation spans during countdown scrolling |
| Matched historical baseline | Became unresponsive before a usable workout; Android reported a 5002 ms focus-input timeout |

These results confirm that background preparation is followed by substantial
initial UI composition/measurement. They do not isolate which implementation
change caused the user's phone to stutter. Baseline scroll captures are excluded
because an ANR dialog was present; there is no valid before/after speed claim.
Frame pipeline time is not pure CPU time, and nested trace slices must not be
summed. Emulator prediction/pacing errors occurred extensively. Physical-phone
timings, exact tap-to-first-usable latency and repeated-cycle memory leak
behavior remain unverified.

Local evidence is under `app/build/outputs/device-validation/20261023/`, including
`REPORT.md`, `performance-trace-summary.json`, the Perfetto traces, instrumented
test logs, UI snapshots, signing checks and data-retention comparisons. Build
outputs and database copies are ignored by Git. Use these measurements as
emulator diagnostics; [Android's benchmark guidance](https://android.googlesource.com/platform/frameworks/support/+/7ebe543d88e892e4f0f5bd74e4bf7796138481b2/benchmark/docs/macrobenchmark.md)
explains why emulator numbers do not represent physical-device performance.

## Historical record: original verification before an emulator was available

The remaining source/build notes describe the original optimization work.
Their device-availability limits apply to that earlier stage; the current
comparison and current thread placement are documented above.

The baseline application source is commit `4c362999d3c33fdcb6cfd90ed86e16850e795d43`
(version 1.4.1 / Build 13). On 23 October 2026, before the optimization edits,
the authorized `adb devices -l` check returned no connected devices. Emulator
37.2.12 still reported acceleration check 6: the Android Emulator hypervisor
driver is not installed. Its preceding three software-rendering attempts had
terminated before boot with Windows exception `0xC0000005`. Repeating the same
failed setup would not provide a suitable measurement target.

**Before/after device latency, frame timing, allocations, memory growth, and
visible smoothness are unverified.** No timings below are invented. Host tests
verify behavior, not frames per second. The debug APK must not be used to infer
production scrolling performance.

| Metric | Before | After |
| --- | --- | --- |
| Routine Start to first editable, scrollable workout frame | Not measured: no device | Not measured: no device |
| Warm resume to usable workout | Not measured | Not measured |
| Fast scroll: median/p95 frame duration and missed deadlines | Not measured | Not measured |
| Fast scroll with running rest countdown | Not measured | Not measured |
| PSS and repeated start/resume growth | Not measured | Not measured |

## Baseline implementation evidence

These are verified source properties, not measured attribution of user-visible
lag. Inspect the referenced baseline commit to distinguish them from new code:

- `AppViewModel.presentation` called `present(...)` in a flow collected by
  `viewModelScope`, with no background dispatcher for that CPU work. Presentation
  constructed completed workout histories and records as well as the active
  session. Each workout mapped each exercise, scanning sets and history again.
  Individual writes invalidated the broad repository snapshot and presentation.
- `ActiveWorkoutScreen` already used an exercise-keyed `LazyColumn`. It did **not**
  compose all off-screen exercises upfront. Each visible exercise card did build
  all its set editors in a normal `Column`, which is unbounded for a single
  unusually large exercise.
- `ActiveWorkoutScreen` read an elapsed clock state at the screen level once a
  second. The timer was also included in the global `UiState`, so timer emissions
  reached app navigation/screen inputs. These scopes are broader than the clock
  labels that actually need updates.
- A command channel serialized Start taps and the repository reused an active
  session. That protects data from duplicates but did not provide immediate
  Start-specific progress or eliminate unnecessary queued Start work.

Trace the main thread, Room query executor, coroutine scheduling, composition,
layout, GC and frame deadlines on a device before calling any one of these the
dominant runtime bottleneck. Database suspend operations alone do not establish
that a query runs on the main thread.

## Original optimization changes

- The original optimization moved presentation to `Dispatchers.Default`; the
  current local change above restores it to the main thread.
  Finished-history/record presentation is reused across active-set
  edits; changes to completed data, catalog or units invalidate the appropriate
  cache. Unchanged active exercise/set UI objects retain their identities.
  Cache ownership stays within the sequential presentation flow. Session changes
  clear session-specific previous-performance descriptions.
- Start provides immediate in-flight state and rejects repeated queued Start
  requests. Session setup reads the routine, joined exercise information and
  planned sets in bulk, then writes the snapshot in a transaction. Commands that
  edit one row use focused lookups instead of loading the whole archive. An
  accepted retry clears a previous action error and waits for that saved session
  ID to reach presentation. Failures release the in-flight state, and a bounded
  presentation wait reports an actionable error without ending the command actor.
- The repository deliberately retains a coherent **seven-table atomic snapshot**
  for its broad data flow. It reuses equal immutable table lists after the read;
  it does not choose cached tables solely from invalidation names. This avoids
  mixing old/new table data if another transaction commits between the signal
  and snapshot. Background flow context handles snapshot list comparisons and
  deduplication. The timer has a separate narrow active-session-ID observer.
- The active list now lazily composes exercise headings, individual set rows and
  footers with unique exercise/set instance keys and content types. Off-screen
  set editors inside a large exercise are no longer all composed at once.
- A small elapsed metric owns its own clock, while countdown state is read only
  by the timer dock. Timer ticks do not enter the Activity's content state or
  workout route parameters. Saved row values remain the source of truth for
  reconstruction after lazy disposal, with pending text edits retained safely.

These source changes reduce known categories of work. Device measurement is
still required to quantify the reduction and determine whether either reported
symptom is resolved.

## Completed local checks

The final full host suite passed **347 tests in 49 suites**, with no failures,
errors or skips. Debug and optimized lint passed with zero errors (21 existing
debug warnings; 22 optimized warnings, including the expected API 29+ shell
profiling attribute warning). Debug, instrumentation and optimized APKs built.
Both current application APKs retain `com.mooscles`, version `1.4.3` / code `14` and the
existing signing certificate. The optimized manifest is non-debuggable and
profileable; R8 optimization and resource shrinking completed successfully.

Structural checks are not Android latency/frame measurements:

| Check | Baseline implementation | Optimized implementation |
| --- | --- | --- |
| Repository Start SELECTs for 3 exercise occurrences | 9, derived from source | 5, verified by Room query callback |
| Repository Start SELECTs for 40 occurrences | 83, derived from source | 5, verified by Room query callback |
| Raw set archive visits within each calculation | Repeated full-list filters inside exercise loops | One indexing traversal per public calculation, regression-tested |
| Editable inputs in one 120-set exercise | All 120 built by the card's Column | Only 1–8 reps fields composed in the host viewport, regression-tested |
| Active set edit | Rebuilds historical workout/record presentation | Reuses unchanged history, records, routine and unaffected row references |
| Countdown progress | Included in Activity screen state | Separate dock observation; no content-state emission, regression-tested |

New tests additionally cover saved order/duplicate instances, bodyweight,
independent targets, disk reopen/resume, concurrent snapshot consistency, PR
corrections/deletions, fresh settings, repeated Start/error recovery, lazy draft
disposal, cross-field edits and exact kg/lb behavior. The full existing workout
flow and rest-dialog checks also pass. Instrumentation was compiled, not run.
See [VERIFICATION.md](VERIFICATION.md) for absolute artifact paths, hashes and
the distinction between the local update and the published baseline binary.

## Comparable optimized builds

`:app:assemblePerformance` builds `app/build/outputs/apk/performance/app-performance.apk`.
This variant uses R8 optimization and resource shrinking, is non-debuggable,
and includes `<profileable android:shell="true"/>` only in its variant manifest.
It keeps package `com.mooscles`, version settings, and the existing distribution
debug signing configuration. The ordinary debug and release variants are
unchanged. Profileable capture requires an API 29+ device. Use a disposable test
device because this package shares application data with the normal app.

Prepare the baseline without changing the working tree:

```powershell
.\tools\prepare-performance-baseline.ps1 -OutputDirectory '.tooling\performance-baseline-v143'
```

The new `.tooling/performance-baseline-v143` contains archived application
source from the baseline commit. The script overlays only the current build
configuration and performance manifest, recording their SHA-256 values. It
refuses to overwrite an existing baseline. Build that source and the optimized
source with the same JDK, SDK, Gradle cache, optimizer settings, and signing key:

```powershell
$project = 'D:\Programing\Mooscles'
$env:JAVA_HOME = (Get-ChildItem "$project\.tooling" -Directory -Filter 'jdk-*' | Select-Object -First 1).FullName
$env:GRADLE_USER_HOME = "$project\.tooling\gradle-home"
Push-Location "$project\.tooling\performance-baseline-v143"
try { .\gradlew.bat :app:assemblePerformance --console=plain } finally { Pop-Location }
.\build.ps1 -Tasks ':app:assemblePerformance'
```

The initially built `.tooling/performance-baseline-project` APK belongs to the
Build 13 implementation task. Its source and verification record are preserved.
For Build 14 comparisons, regenerate the baseline in the new directory above
using the corresponding version and measurement configuration, then build it.
The later Build 14 thread-placement comparison is documented above and uses
two matching performance variants. An archived debug APK and a
current optimized APK are **not** a valid before/after pairing. Compare the two
matching `performance` APKs. Keep both APKs, R8
`mapping.txt` files, hashes, commit IDs, and fixture details with the traces.
The build helper does not publish the performance APK as a release.

The baseline optimized build completed successfully (52 tasks). Its verified APK
is `D:\Programing\Mooscles\.tooling\performance-baseline-project\app\build\outputs\apk\performance\app-performance.apk`
(2,007,400 bytes; SHA-256
`b9df649cdd6ce4b1b36046d7178d9a8052d18989360af51cc02eb0cdd5c4eb39`).
Package inspection confirms `com.mooscles`, version 1.4.1 / code 13, no debuggable
flag, and `profileable` with shell access. APK Signature Scheme v2 verification
passed with the existing distribution certificate SHA-256
`0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.
The baseline is ready for a later same-device comparison; it has not been run
on Android in this session.

## Deterministic test data

`WorkoutPerformanceFixtureTest` is an opt-in instrumentation setup helper,
not a timing test. It skips normal instrumentation runs and refuses a device
containing any non-fixture routine or workout. Never clear or uninstall a
person's installed app to make it pass. Use an otherwise unused test device.

| Fixture | Routine | Finished history | Coverage |
| --- | --- | --- | --- |
| `typical` | 8 exercise instances × 4 independent sets | 30 sessions × 8 exercises × 4 sets | Weighted/bodyweight, varied reps, warm-up history |
| `large` | 80 exercise instances × 12 independent sets | 200 sessions × 12 exercises × 4 sets | Repeated variations with unique instance IDs, larger saved history |

Both use fixed IDs, dates and targets, rest 120 seconds, kg, and automatic rest.
Fixture re-seeding deletes only these test routines/workouts after verifying
there are no non-fixture routines/workouts. It does not clear storage, delete
custom exercises, or use destructive database migration.

1. Build the current debug and instrumentation APKs. On the disposable device,
   install both signed APKs using `adb -s SERIAL install -r APK_PATH`.
2. Seed either fixture explicitly:

   ```powershell
   $adb = '.\.tooling\android-sdk\platform-tools\adb.exe'
   & $adb -s SERIAL shell am instrument -w -e class com.mooscles.WorkoutPerformanceFixtureTest -e performanceFixture typical com.mooscles.test/androidx.test.runner.AndroidJUnitRunner
   ```

3. Stop the app, then install the baseline `app-performance.apk` with `install -r`.
   Do not uninstall or clear storage. The package and certificate are the same.
   Confirm `versionCode`, certificate, and non-debuggable/profileable flags.
4. Measure baseline scenarios. Re-seed the exact same fixture through the same
   debug/test APKs before measuring the optimized build, then install the new
   `app-performance.apk` with `install -r`. This keeps the initial database
   deterministic. Do not change the fixture or build mode between captures.
5. Repeat the whole sequence for `large`. For repeats, discard only active
   **fixture** sessions through the app, or re-seed before a new series.

Disable network or turn off automatic updates on this test device in both
conditions so an unrelated update worker does not change the installed binary.
Keep device refresh rate, power mode, animation scale, thermal state, orientation,
keyboard, brightness, history, and compilation mode identical. Record device
model, Android version and system build. For controlled warm compilation run
the same `cmd package compile -m speed -f com.mooscles` before each series; do not
compare an uncompiled baseline with a compiled result.

## Same-device capture procedure

The helper records raw system traces, `gfxinfo` frame data, process memory,
device/display/thermal information and installed package details. It pulls the
installed single APK, records its SHA-256, and rejects an `-ApkPath` mismatch
before capturing so the before/after label cannot silently identify another
binary. It does not install, uninstall, clear storage, fabricate summary numbers,
or assert that a screen is usable. API 29+ Perfetto access is required. Capture
one isolated scenario at a time, first for baseline, then for the optimized APK:

```powershell
.\tools\capture-workout-performance.ps1 -Serial SERIAL -Stage before -Fixture typical -Scenario start -ApkPath 'BASELINE_APK'
.\tools\capture-workout-performance.ps1 -Serial SERIAL -Stage after -Fixture typical -Scenario start -ApkPath 'OPTIMIZED_APK'
```

Each capture writes a new directory under `app/build/outputs/performance`.
On a device that lacks a Perfetto category, inspect the capture logs and adjust
the **same** category set for both builds. Do not treat a failed or empty trace
as a result. Open `capture.perfetto-trace` in Android Studio's system trace viewer
or Perfetto UI; raw `gfxinfo` averages alone are not sufficient for Compose
scroll conclusions.

Use at least five paired runs per fixture/scenario, with the same swipe path
and duration. Analyze the measured interaction interval, excluding navigation
setup, idle capture time, fixture preparation and profiler attachment.

- **Start:** Begin with Train showing the fixture routine and no active session.
  Record the Start tap's input event. Scroll immediately and edit the first
  visible kg/reps field, checking the persisted value afterwards. Define usable
  latency from the tap to the first displayed workout frame that supports both
  interactions. Report the first-frame timestamp separately from the completed
  edit acknowledgement; a spinner or the first empty route is not usable.
  Use input dispatch/composition/frame tracks and, if necessary, an external
  synchronized video rather than treating `am start -W` as routine Start time.
  The optimized build exposes `Mooscles:startSession` (session command) and
  `Mooscles:present` (background presentation) trace spans. They help attribute
  work, but their end timestamps are not substitutes for the first usable frame.
- **Scroll:** Start the session outside the measured interval. Fling from first
  to last exercise and back at a repeatable speed. Report frame-duration median,
  p95/p99, missed deadlines/jank count and rate, and observed stutter for the
  active interval. Compare against the device's actual frame deadline, not a
  fixed 16.7 ms assumption. Inspect main-thread composition/layout, database
  work, allocations/GC and scheduler contention for long frames.
- **Timer scroll:** Complete one working set to start its 2:00 timer before the
  interval. Repeat the same flings while it counts down, plus pause/resume and
  ±0:15. Confirm only relevant timer/elapsed surfaces update; inspect unrelated
  card composition and compare missed frame deadlines against timer-idle runs.
- **Resume:** Edit and complete distinct sets, background the app, then force-stop
  and reopen without clearing data. Measure resume tap to a usable workout and
  check per-set values, duplicate exercise order, completion and timer restoration.
- **Cycles:** Repeat ten start/discard cycles and ten resume/background cycles on
  fixture data. Record PSS after every cycle at the same idle point and after a
  consistent idle interval. Compare steady-state trends, not just one peak.
  Verify routine targets stay separate from recorded performance. End each
  series with no active test workout before seeding the next series.

Memory PSS from the optimized target is comparable, but Java/Kotlin allocation
recording and heap dumps may require a separate debuggable diagnostic capture.
Those diagnose allocation sites; never substitute their frame times for the
optimized before/after comparison. If detailed automation is added later,
Android recommends a separate Macrobenchmark test module with
`FrameTimingMetric` and the same non-debuggable target.

## Functional checks beside profiling

Run host tests, lint, debug and optimized builds. Device tests should additionally
edit blank-bodyweight and weighted values; scroll each field away and back;
check fast consecutive edits followed by backgrounding or navigation; verify
sets remain independent, repeated exercise occurrences stay ordered, and
completion, PRs, duration, volume, discard and summaries match saved snapshots.
Try repeated Start taps and a missing/deleted routine error. Instrumentation
setup and host test success must be reported separately from device execution.
The initial Build 13 baseline, current Build 14 optimized APK and final
host/lint/build results are verified above. A matching Build 14 baseline must
be regenerated and built before a new paired comparison. No optimized APK was
profiled on Android here; this procedure is not evidence that profiling ran.

References: [Compose performance](https://developer.android.com/develop/ui/compose/performance),
[Compose best practices](https://developer.android.com/develop/ui/compose/performance/bestpractices),
[profileable builds](https://developer.android.com/studio/profile), and
[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview).
