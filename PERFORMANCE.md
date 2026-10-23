# Workout responsiveness verification

## Status and limits

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

## Changes under verification

- Presentation now runs on `Dispatchers.Default`, outside composition and the
  main thread. Finished-history/record presentation is reused across active-set
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
using the current version and measurement configuration, then build it. No
Build 14 device comparison has been performed. An archived debug APK and a
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
