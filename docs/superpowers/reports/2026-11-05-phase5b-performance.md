# Phase 5B performance — measured CPU work and remaining frame checks

Date: 2026-11-05. Feature branch: `codex/phase5b-history`. Development baseline: `c9ba9b1a226d7cbc41acbca19dcba09ae9f6447e` (v1.5.3 / Build 25). Emulator and approved physical-phone results are separate below.

The cached implementation corresponds to source `26a8889`. The later `7fc1b2e` correction changes only the saved editor's large-text heading/layout and its assertions; it was not part of the ABBA/settled-scroll captures. The later final-source unit-switch trace below includes it. No saved-editor performance benefit is inferred from these History/index measurements.

## Current verdict

Caching repeated exercise metadata **within one history-index build** reduced the measured `ProgressIndex.build()` median from **79.2168 to 58.5348 ms (26.1%)**, with p95 from **98.3670 to 72.1725 ms**. This passes the specification's median ≤60 ms / p95 ≤120 ms emulator budget in this sample, with only about 1.5 ms of median headroom. The later in-app unit-switch trace measured **77.133 ms median**, exceeding the 60 ms goal under concurrent app work; this standalone pass is not a blanket in-app performance pass. The comparison is against the initial Phase 5B implementation, **not** against the older released app, which did not build the new history metadata.

All measured query categories pass the median ≤4 ms / p95 ≤8 ms budget. That is not a claim that every query is below 8 ms: overall-progress queries had **4/360 samples above 8 ms in each build**.

On the phone, the scoped body-map layer reduced Overall Progress jank from **7.24% to 1.39%** in the repeat comparison. History, Records and the Library control also met the 5% goal on the phone. The earlier host-GPU emulator results exceeded 5% in History, Overall Progress and Library. The emulator was measured before the layer change. Navigation-to-visible-frame timing and the active-workout rendering comparison remain unverified.

## Approved physical-phone comparison

Device `4eb066eb`, model 23021RAAEG, Android 15/API 35, Adreno 610, 1080 × 2400, density 440 dpi, font scale 1.1. Nominal 120 Hz with measured intended-vsync spacing around 8.353 ms. Portuguese, dark theme, pounds, no active timer or keyboard. The R8-optimized, profileable Build 25 used the separate package `com.mooscles.phase5b.phone20261105` with updates disabled and the same synthetic fixture of 208 workouts, 12,688 sets and 48 variations. The phone's production installation and device settings were preserved.

Initial warmup and scroll captures used `7fc1b2e`. History, Records and Library each used 20 down/up pairs at 1,200 px per 100 ms with 350 ms gaps. Overall Progress used 10 pairs at 1,000 px per 100 ms with the same gaps. The rates below use Android's modern gfxinfo aggregate. The different gestures make comparisons between screens descriptive.

| Screen | Captures | Janky / total frames | Capture range |
| --- | ---: | ---: | ---: |
| History | 3 | 94 / 7,356 | 1.14–1.35% |
| Records | 3 | 128 / 7,442 | 1.53–1.90% |
| Library control | 3 | 98 / 7,137 | 1.21–1.52% |

Overall Progress had repeated costly path drawing and long graphics handoffs. The isolated prototype put only the static muscle map in an offscreen layer at `HistoryProgress.kt` in the `muscle-map` lazy item. Repeating the same scroll with the original APK restored the slowdown. Reinstalling the prototype restored the improvement. The last three blocks used the same compilation, seed guard, fresh-process setup and visible-chart check. Initial A1 followed earlier CPU and scroll probes.

| Block | Janky / total frames | Rates by capture | Frame-time p95 by capture |
| --- | ---: | --- | --- |
| A1, original | 259 / 3,542 | 7.23%, 7.37%, 7.34% | — |
| B1, layer | 60 / 3,677 | 1.62%, 1.56%, 1.71% | 17, 18, 18 ms |
| A2, restored original | 256 / 3,536 | 7.31%, 7.21%, 7.20% | 27, 28, 27 ms |
| B2, restored layer | 51 / 3,675 | 1.55%, 1.31%, 1.30% | 18, 18, 18 ms |

A2 to B2 is a 5.85 percentage-point reduction (80.8% relative). These are repeated captures from one phone with correlated samples within each block. The candidate and original map captures retained the same regions, outlines, proportions and legend. Content and size changes still invalidate the layer.

In comparable approximately 12.6–12.7 second Perfetto captures, path-heavy RenderThread draws fell **428→13** and summed main-thread graphics-handoff wait fell **3,779.9→1,288.2 ms**. RenderThread scheduled CPU fell **6,157.4→4,043.4 ms**, while main-thread scheduled CPU rose from **1,546.9 to 1,690.1 ms** across more frames. The 13 remaining path-heavy draws averaged 17.3 ms wall time, so layer creation and item re-entry can still be expensive. A visible-map RGBA backing is roughly 2.8 MiB. Single snapshots showed 44,388→44,340 KiB Graphics PSS and cannot establish a memory gain. There are no hardware GPU counters, and the FrameTimeline parser lost some rows. The jank table therefore uses gfxinfo.

The phone CPU probe measured an index median of 88.966 ms and p95 of 103.137 ms (nine samples, candidate only). Records/history/calendar/overall query medians were 0.799/0.141/0.092/3.094 ms and p95s 3.078/0.188/0.172/4.795 ms (180 per query). The phone probe has no matched CPU baseline.

Local evidence under `D:\Programing\Mooscles\.tooling\phase5b-20261104\` includes `phone-scroll-analysis.md`, `phone-cpu-analysis.md`, `phone-map-baseline-a.log`, `phone-map-layer-b.log`, `phone-map-layer-b2.log`, `phone-overall-scroll.perfetto-trace`, `phone-map-layer-scroll.perfetto-trace` and both `phone-map-*.png` captures.

## Device, build and workload

- Emulator: `emulator-5560`, AVD `Medium_Phone`, Android 15 / API 35, `sdk_gphone64_x86_64`, 1080 × 2400, density 420 dpi, approximately 60 Hz. Emulator 37.2.12.0 uses WHPX and the host GPU through gfxstream; its startup log identifies an AMD Radeon RX 7800 XT. These are not phone measurements.
- Host: AMD Ryzen 7 5700X3D, 16 logical processors, approximately 32 GiB RAM. After host cleanup, the ABBA checkpoint recorded 6,991,440 KiB free RAM at `2026-11-05T12:36:25.7784192Z`. There is no synchronized host CPU-utilization or GC trace for each CPU sample; scheduling/thermal/host effects cannot be separated fully.
- Build: R8-optimized `performance`, resource shrinking enabled, non-debuggable, shell-profileable, original local signing identity. Only ignored source copies use `com.mooscles.phase5b.perf` with `UPDATER_ENABLED=false`; production `com.mooscles` and its data are separate. Both sides report versionName 1.5.3 / versionCode 25. The probe script speed-compiles the installed isolated package before each process run.
- Data: the existing `LargeHistoryFixture.variedExercises(48)` workload: **208 finished workouts, 12,688 sets, 48 exercise variations**, 48 exercise snapshots per workout with 61 sets distributed over them. The fixture has stable IDs and is retained across installs. Both sides report **3,600 record events and 1,845 current records**. This is a stress fixture rather than a typical six-exercise routine.
- Preferences: pounds and dark theme in the isolated fixture. CPU probes display a simple diagnostic Activity; no workout timer, keyboard, list drawing or recording is part of those timed method calls. Visual scrolling conditions are recorded separately below.
- Methods run on `Dispatchers.Default`. A temporary public bridge in the ignored source copy calls the real internal `WorkoutPresentation`; it does not replace the production calculations. Raw outputs are consumed and written to JSON. No broad benchmark framework was added to the project.

## Measured change and invalidation

`screens/src/commonMain/kotlin/com/mooscles/screens/history/HistoryModel.kt:53` creates a build-local `HashMap<ExerciseUi, HistoryExerciseMetadata>`. At line 70, repeated historical entries reuse the prepared bilingual search text, convention and anatomy. The metadata class begins at line 111. Before this change, these definitions were resolved repeatedly across the fixture's 9,984 exercise snapshots, even though only 48 definitions were involved.

The key is the complete immutable historical exercise snapshot, not only its ID. Different names, classifications, conventions or custom flags therefore remain distinct. The map lives only for this one `HistoryIndex.build`; it introduces no process-level cache or independent invalidation policy. `ProgressIndex.build` still owns the history metadata (`ProgressIndex.kt:136`), and the existing history/record identity cache remains authoritative.

The accompanying focused tests preserve the old whole-string search fold and verify that changed historical definitions do not leak between snapshots or builds (`HistoryModelTest.cachedNamePiecesExactlyMatchTheOriginalWholeStringFold` and `exerciseMetadataUsesTheWholeSnapshotAndDoesNotOutliveItsIndex`). Their focused build, lint and APK build completed in `metadata-cache-focused-build.log`; full phase-end validation is recorded in the main Phase 5B report.

## Controlled CPU comparison

The cache comparison used **ABBA order**, with each process performing nine index builds:

| Order | Raw result | First index call (ms) | Index median (ms) | Index p95 (ms) |
| --- | --- | ---: | ---: | ---: |
| A1, initial Phase 5B | `cache-before-a-20261105.json` | 98.7502 | 87.1119 | 98.3670 |
| B1, metadata cache | `cache-after-a-20261105.json` | 78.3886 | 58.5348 | 72.1725 |
| B2, metadata cache | `cache-after-b-20261105.json` | 67.1279 | 54.1037 | 63.4291 |
| A2, initial Phase 5B | `cache-before-b-20261105.json` | 66.9801 | 73.8732 | 78.7822 |

The corresponding file times are 12:36:12, 12:36:21, 12:36:29 and 12:36:38 UTC. These are four process runs with correlated repetitions within each run, not 36 independent users/devices. The first call in this table is the first **index call in that process after presentation preparation**, not application cold-start or first visible-frame time. It remains included in the pooled statistics.

Statistics use the existing probe convention: upper-middle median and p95 at `floor((n−1) × .95)` in sorted raw samples. Pooled results reproduce `cache-comparison-summary.json`:

| Metric | Samples per side | Before median / p95 / max (ms) | After median / p95 / max (ms) | Median difference |
| --- | ---: | ---: | ---: | ---: |
| Full index, including new history metadata | 18 | 79.2168 / 98.3670 / 98.7502 | 58.5348 / 72.1725 / 78.3886 | −20.6820 ms / **−26.1%** |
| Fresh presenter, full history preparation | 18 | 156.8797 / 463.1334 / 588.6543 | 136.2199 / 182.4226 / 203.3723 | −20.6598 ms; attribution unverified |
| Retained presenter, cache-hit call | 60 | 7.0046 / 15.7066 / 38.8668 | 6.2631 / 14.5989 / 17.4612 | −0.7415 ms; attribution unverified |

Excluding the first index call of each process, 16 samples per side have medians **79.2168 → 54.1037 ms**, with p95 **91.0662 → 64.1010 ms**. This sensitivity check supports the index result rather than relying on one first-use outlier.

**Presenter caveat:** `shared/.../WorkoutPresentation.kt` is byte-for-byte unchanged from the c9ba9b1 source copy (SHA-256 `be4bf336cd6dd5a4f8dc0709eff26a70c02e5c7d0ab5f120b92c477d48c96e7c`). History-index metadata is built after presentation, so the lower observed presenter times cannot be credited to this cache change. They are retained to show variability and the cost of the unchanged pipeline. No GC or scheduler evidence attributes the 588.7 ms outlier.

The candidate also measured `HistoryIndex.build()` alone after the other probes: 18 calls, median **15.0472 ms**, p95 **27.9095 ms**, maximum **29.9155 ms**. There is no directly comparable pre-cache metadata-only measurement; do not subtract this number from the earlier full-index timing or call it a separate measured speedup. Its additional consumed result also explains why the candidate diagnostic checksum differs from the initial Phase 5B checksum.

### Query distributions

Each category has **360 calls per side** (180 in each of two processes). These are direct **uncached** query-method costs over an existing index. Dispatch, Compose, navigation, layout and drawing are excluded. Production memoization was not disabled or redesigned.

| Query category | Before median / p95 / max (ms) | After median / p95 / max (ms) | Samples >8 ms, before → after |
| --- | ---: | ---: | ---: |
| Records/search and current/first filters | 0.4058 / 1.8332 / 5.7975 | 0.4752 / 2.3285 / 6.4372 | 0/360 → 0/360 |
| History search/PR filters | 0.1116 / 0.2338 / 5.2017 | 0.0747 / 0.2181 / 1.3140 | 0/360 → 0/360 |
| Calendar month queries | 0.0543 / 0.1530 / 6.0022 | 0.0506 / 0.1193 / 5.3637 | 0/360 → 0/360 |
| Overall progress, periods and week starts | 1.1343 / 2.1911 / 11.0514 | 1.1371 / 2.3735 / 11.8269 | 4/360 → 4/360 |

Records queries vary the prefixes of “supino” and current/first-record options. History queries use the same prefixes and the beaten-record filter. Calendar queries cycle 12 month values; overall queries cycle the five periods and seven week starts. This is not complete coverage of all routine/exercise/day-filter combinations or every keyboard event in the actual screen.

All four categories meet the specification's median/p95 targets in both builds. Records became slightly slower in this small comparison (+0.0694 ms median), and overall progress is essentially unchanged. Since query algorithms did not change in this optimization, these distributions support the budget check rather than separate causal query-speedup claims.

### Earlier released-source comparison

Before the targeted cache comparison, the cleaned c9ba9b1 source-copy runs had index medians **41.8858 and 38.3638 ms**. Initial Phase 5B runs had **93.5477 and 103.8950 ms**, identifying the added metadata as a budget problem. Those earlier runs are preserved as `clean-baseline-*` and `clean-candidate-*`; they are not pooled into the later ABBA sample. The final feature adds work that the release did not perform. Passing the absolute budget after the cache fix does not establish that the whole Phase 5B app is faster than the released version.

## Settled History, Library control and Overall Progress scrolling — budget failure

The final cached candidate was exercised after loading settled, with no active timer and no keyboard. Each capture traversed down 20 times and back up 20 times, using x=540, y=1900↔700 (1,200 px), 100 ms swipes and 0.35-second gaps. This corrects the initial helper's alternating-at-one-position pattern. Animation settings were window=1.0, transition=1.0 and animator=`null` (the recorded system default). No settings assumption is substituted for those captured values.

| Capture | Janky / rendered frames | Janky rate | p50 / p95 / p99 (ms) |
| --- | ---: | ---: | ---: |
| First traversal | 103 / 926 | **11.12%** | 25 / 44 / 53 |
| Warm traversal 1 | 107 / 919 | **11.64%** | 26 / 42 / 53 |
| Warm traversal 2 | 123 / 903 | **13.62%** | 25 / 44 / 53 |

The same cached build, emulator, settled loading state and traversal procedure were then used for the Library control and Overall Progress:

| Screen / capture | Janky / rendered frames | Janky rate | p50 / p95 / p99 (ms) |
| --- | ---: | ---: | ---: |
| Library control, first | 75 / 734 | **10.22%** | 25 / 44 / 53 |
| Library control, warm 1 | 86 / 707 | **12.16%** | 26 / 46 / 65 |
| Library control, warm 2 | 98 / 720 | **13.61%** | 26 / 46 / 57 |
| Overall Progress, first | 80 / 421 | **19.00%** | 28 / 44 / 48 |
| Overall Progress, warm 1 | 86 / 388 | **22.16%** | 28 / 48 / 69 |
| Overall Progress, warm 2 | 88 / 410 | **21.46%** | 28 / 46 / 48 |

All nine captures exceed 5%. History and Library ranges overlap; Overall Progress is consistently higher in this sample. These are sequential screen comparisons within the candidate, **not** an alternating released-baseline/candidate experiment. Their content, list lengths and number of frames generated by the same gestures differ; a short screen reaches its boundary sooner. The control therefore bounds attribution but cannot prove which source change caused a regression or how a physical phone behaves.

Every capture's `gfxinfo` GPU p90 is in the 4,950 ms bucket, which is not credible as a clean hardware-GPU measurement on this emulator. The supplemental traces below establish graphics-handoff waits and additional lazy-layout outliers, but contain no hardware GPU slices. They do not separate product drawing cost from emulator translation/host driver cost. No rendering repair or general stutter fix is claimed from these numbers.

Navigation to the first visible History list/record chart, the 300-point chart selection/path behavior, active-workout rendering versus the released baseline, and trace-based allocation/GC attribution have **not yet been closed by this report**. CPU method timing and a successful build cannot substitute for them.

## Final-source unit-switch trace

The isolated optimized package was rebuilt with the saved-editor correction and a clearly distinct
"Mooscles Phase 5B Test" label (no production configuration change). Its synthetic 208-workout dataset
was retained. Six alternating kilogram/pound selections, 1.2 seconds apart, produced **exactly six**
`Mooscles:progressIndex` sections, all on `DefaultDispatch`, with elapsed durations
**83.418, 77.133, 67.599, 65.644, 71.103 and 96.372 ms**. The final selection was pounds again.
This confirms one background index build per actual switch rather than duplicate builds in this capture.

These in-app wall times are under concurrent preference/presentation/UI work: upper-middle median
**77.133 ms**, p95 **83.418 ms** (six samples, the same percentile convention), maximum **96.372 ms**.
They exceed the 60 ms median index goal, even though the controlled standalone ABBA median is 58.535 ms.
Do not turn that standalone budget pass into a blanket in-app performance pass. This is not an
additional before/after optimization comparison and it does not separate CPU running from scheduling wait.

Evidence: `unit-switch.perfetto-trace`, `unit-switch-index.csv`, `unit-switch-parser.txt` and
`units-ui.xml` / `units-after.xml`. No production preferences or physical-phone state were accessed.
The measured ABBA APK was retained separately before this rebuild; the prepared final test APK is
`candidate-perf-source/app/build/outputs/apk/performance/app-performance.apk`, 3,546,528 bytes,
SHA-256 `65888860482f00b035fb6a11b0ee791f401216b6f82f7a2afc62afe95c4bde15`, package
`com.mooscles.phase5b.perf`, Build 25, non-debuggable/profileable/R8, updater disabled, original signer.
## Offline upgrade cross-check supporting the measurements

The separate isolated package `com.mooscles.phase5b.upgrade2` successfully upgraded the start-of-Phase-5A source `e6ada6a61d2174d51815bc412d124dcd0a22caf7` (**1.4.5 / Build 16**) to the Phase 5B candidate (**1.5.3 / Build 25**) on the same emulator. The upgrade inputs record the original matching signer; connectivity at installation showed **“Active default network: none”** and no current networks, despite airplane mode being 0. This is an offline isolated-install result, not a production-phone installation.

Both after-install and after-MainActivity comparisons passed all 14 checks: identical fixed-metadata backup export, byte-identical local preferences, schema 6, counts, exact active sets, three selected workout facts, records, active identity, timer identity/running state, preferences and Portuguese app locale. The fixture contained 208 finished workouts plus one active workout, 12,691 sets, one routine and three planned sets. Its completed active working set retained **22.1234567890123 kg × 6**; blank/unfinished and warm-up rows remained intact.

The authoritative timer token and deadline were unchanged; the after-main observation occurred 10,729 ms after the before observation, with 288,236 ms remaining. The export SHA-256 was `fadfcea380a22c69ccbbfeeb1e9abb5e2b674e9533e7e70635e03f4fbc1aba83` throughout. The 357-byte local-preference file retained MD5 `b5530b5a8ca4e79a05d05e45492f37f3` and SHA-256 `2a00fb0e28e59646240e3f1d12bc270fc51d563d5f698862fe6fd09736729221` before reads, after reads and after normal app initialization.

The first `.upgrade` attempt was **not** a successful upgrade: it returned “Existing isolated data must be preserved; setup will not overwrite it” after the initial in-Activity locale-setting approach. That data/package was retained. The corrected `.upgrade2` run set the locale externally before setup and used a distinct package, without uninstalling or clearing production data. See the main Phase 5B verification report for the offline UI captures and sharing checks; they do not establish frame performance.

## Local evidence and remaining work

Heavy logs, APKs, raw synthetic exports and captures are ignored local evidence, not source/release assets:

- Root: `D:\Programing\Mooscles\.tooling\phase5b-20261104\`.
- CPU aggregate: `cache-comparison-summary.json`; host checkpoint: `cache-comparison-host.json`.
- Raw ABBA results: `perf-setup\results\cache-before-a-20261105.json`, `cache-after-a-20261105.json`, `cache-after-b-20261105.json`, `cache-before-b-20261105.json` and the corresponding root `.log` files.
- Before-cache APK: `artifacts\phase5b-before-metadata-cache.apk`, SHA-256 `342751a99d00a5e3922a719025fa0d97742a650182016b2a2906d54cef2e4e25`.
- Cached candidate APK: `artifacts\phase5b-metadata-cache-measured.apk`, retained SHA-256 `1041c310efbe2b3d3915ff967789e358f6b3fbc52a8b62bd023d8b4eb098508e`.
- Scrolling: `perf-setup\results\history-first-gfxinfo.txt`, `history-warm-1-gfxinfo.txt`, `history-warm-2-gfxinfo.txt`, the three `library-control-*-gfxinfo.txt` and three `overall-*-gfxinfo.txt` files, and each capture's `*-conditions.json` with display/settings and before/after meminfo.
- Upgrade: `perf-setup\upgrade-results-20261105-095753\inputs.json` and `phase5b-upgrade\comparison.json`, before/after JSON, exports and preference copies; `offline-upgrade2-run.log`, `offline-upgrade2-connectivity.txt`.
- Failed protected setup: `perf-setup\upgrade-results-20261105-094318\phase5b-upgrade\before.json`.

Control/trace analysis is recorded here; the next performance work is a narrowly controlled rendering comparison motivated by those findings, plus the remaining navigation, graph and active-workout checks. Those checks are not silently treated as passed. Keep the UI symptom verdict separate from the measured metadata-CPU improvement. Physical-phone checks require the owner's explicit approval and complete stop while waiting; no phone result is inferred here. No iOS performance result or final phase-completion claim is made by this report.

## Supplemental Perfetto attribution — settled scrolling

Two optimized-candidate traces were analyzed with the local Perfetto trace processor: `history-scroll.perfetto-trace`
(13.118 s) and `overall-scroll.perfetto-trace` (13.076 s), both PID 5948, `com.mooscles.phase5b.perf`.
These are one diagnostic capture per screen, not a matched baseline/candidate experiment. Their FrameTimeline
denominators and classifications differ from the earlier `gfxinfo` captures and must not replace the scrolling
budget results. The app's actual positive-duration surface frames were counted; incomplete/zero-duration frames
were excluded. Numeric-token `Choreographer#doFrame` spans were counted without their nested resynchronization spans.

| Trace observation | History list | Overall Progress |
| --- | ---: | ---: |
| Actual app surface frames; App Deadline Missed | 453; 34 (**7.51%**) | 261; 69 (**26.44%**) |
| SurfaceFlinger deadline flags; buffer-stuffing flags | 24; 433 | 34; 225 |
| App pipeline p50 / p95 / p99 | 17.705 / 33.067 / 60.874 ms | 31.938 / 49.508 / 56.409 ms |
| Main doFrame count; p50 / p95 / maximum wall time | 480; 7.400 / 18.780 / 45.584 ms | 312; 12.598 / 26.714 / 39.400 ms |
| Main frame wall time summed; CPU scheduled inside those spans | 4,212.880; 1,180.854 ms | 3,976.791; 692.750 ms |
| `postAndWait` wall time inside main frames | 2,865.362 ms (**68.0%**) | 3,220.930 ms (**81.0%**) |
| Main frames over 16.667 ms; those spending >70% in `postAndWait` | 33; **25** | 129; **121** |
| RenderThread scheduled CPU; `Drawing` spans | 4,847.987 ms; 453 | 4,246.926 ms; 259 |

**Confirmed trace finding:** most slow main frames wait in the graphics handoff, while RenderThread is doing
substantial work. The worst Overall frame illustrates this directly: at trace +3.039 s, main doFrame token 1530155
lasts 39.400 ms with only 0.639 ms scheduled CPU and **38.792 ms in `postAndWait`**. Its surface-frame pipeline lasts
74.250 ms and carries App Deadline Missed/buffer-stuffing flags. Another example at +9.822 s spends 33.090 of
38.005 ms in the same wait. This does not support blaming historical calculations or broad recomposition for most
of this capture's stalls.

History also has real UI-layout outliers: token 1528030 at +8.044 s takes 32.204 ms, including 10.100 ms scheduled
main CPU, 24.388 ms of measure/layout spans and only 0.620 ms `postAndWait`; its pipeline reaches 70.566 ms with an
App Deadline Missed flag. An urgent lazy-prefetch span immediately before it lasts 26.632 ms (+8.017670 s).
Other History frames show text measurement, lazy precomposition and measurement costs as rows enter the list.
They are contributors, not an explanation of every slow frame. Measure/layout spans can nest; their wall-time
totals are not CPU totals and must not be added to CPU or other nested slice totals.

The graphics work includes many rectangle, text, rounded-rectangle and GL command-encoding operations. Overall
additionally records `PathStencilCoverOp` (354 spans, 353.966 ms summed). Its RenderThread CPU per drawing span is
approximately 16.4 ms versus History's 10.7 ms, a descriptive workload difference. The traces do **not** map those
operations to a particular chart or body-map composable. The relevant candidate boundaries for a later small
rendering experiment are `HistoryProgress.kt:79` (charts), `:84` (body map), and `HistoryScreen.kt:242` (cards).
Moving their already-background queries again is not a trace-supported repair.

Each trace contains one concurrent mark-compact GC (125.382 ms History; 108.541 ms Overall). Those entire concurrent
durations are **not** main-thread pauses. Main `FullSuspendCheck` spans total 7.198 / 6.337 ms, with maxima 4.655 /
4.838 ms; only one >16.667 ms main frame in each capture overlaps such a check. Heap-size counters range from
23,850–48,430 KB and 24,042–47,950 KB. This establishes allocation/GC activity, but no allocation-stack sampling
identifies its source and it does not explain the sustained graphics waits.

No index/presentation trace markers were observed during these settled scroll windows. The capture config has
gfx/view/input/dalvik/scheduler coverage, but no database category or allocation sampling, so absence of SQLite
slices is not proof that no database call happened. There are **zero hardware GPU slices**: CPU GL encoding,
buffer waits and SurfaceFlinger flags establish graphics-pipeline pressure, not a measured hardware-GPU bottleneck
or its division between app drawing, emulator translation and the host driver. Both traces have 36 ftrace setup
notices; History also reports one informational FrameTimeline parser error. No positive error/data-loss-severity
counter was reported. One History surface frame and two Overall surface frames lack exact numeric main-token
matches; the aggregate timeline and main-frame populations are therefore deliberately reported separately.

These emulator traces motivated the phone comparison above. The scoped layer is now applied at the measured History Progress call site. The earlier emulator result still describes its tested build and environment.

Reproducible local SQL/results: `scroll-trace-summary.sql`, `scroll-frame-attribution.sql`,
`history-trace-summary.csv`, `overall-trace-summary.csv`, `history-frame-attribution.csv` and
`overall-frame-attribution.csv`, all under the ignored evidence root. Parser stderr is retained alongside them.
