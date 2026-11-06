# Phase 5B — implementation and verification checkpoint

Updated 5 November 2026. Phase 5B is implemented on the feature branch. The final checks are Android-only by request, with open performance criteria documented below.

## Source and preservation

Repository: [ReiTut/Mooscles](https://github.com/ReiTut/Mooscles). Branch: `codex/phase5b-history`.

| Commit | Change |
| --- | --- |
| `c9ba9b1a226d7cbc41acbca19dcba09ae9f6447e` | Starting current-release baseline, version 1.5.3 / Build 25. |
| `9a0740a521af131dac2f8683a5d569ac19f85fa7` | Shared History, overall progress, saved detail/editing and common share preview. |
| `e264f7186652d38eaaf0535dd790363026e6f098` | CI host-stall diagnostics and checkpoint critique. |
| `26a8889d53a37bff9736fc97b65d7b4001b3aa51` | Build-local exercise metadata reuse, parity/invalidation tests, foreground refresh coverage and expanded accessibility matrix. |
| `7fc1b2e3eacad1d6d667432efe6a7e84fe3e0a33` | Usable saved-editor viewport at narrow widths/large fonts; full heading remains in scrollable content. |
| Local follow-up after `b4d23ed` | Reuse the static muscle-map drawing in an offscreen layer at the History Progress call site. |

Heavy evidence remains local and ignored: `D:\Programing\Mooscles\.tooling\phase5b-20261104\`.

The unrelated `gradle.properties` edit is excluded from feature commits and remains SHA-256 `EB0301005B9C4F30C1F4924D25F68B9C2BC6631F2C1312E3CC27373CEA8C9ACB`. The permanent PC-only backup `D:\Mooscles-Backups\20261104-003300-before-v1.5.3-full` is untouched and has not been uploaded. Production app data and the original signer remain intact.

## Implemented behavior

- Shared History Workouts/Progress segments: monthly calendar, direct single-workout-day navigation, multiple-workout-day filtering, bilingual search, routine/exercise/beaten-record/day filters and grouped cards.
- Overall period totals, frequency/volume charts, alternative tables, weighted muscle distribution and five most-performed exercises. Saveable segment, filters, month, period, table and scroll state.
- Shared saved-workout reading/editing retains every prior editing action, precise rest seconds with Save/Cancel, exact drafts, confirmations and the inclusive PR count beside Sets. Historical completion cannot start active-workout rest.
- Common full-screen sharing preview; Android still owns snapshots, PNG encoding, decoding/cache, FileProvider and share intents. Existing artwork, export dimensions, pagination and workout-wide PR totals remain unchanged.
- Calendar design tokens/semantics/gallery, English and Portuguese resources, foreground date/time-zone refresh and localized record conventions. Legacy coverage was ported before obsolete screens/components and the unused Material icons dependency were removed.

Regression fixes cover delete retry/late acknowledgment, unrelated-error interference, per-set failure scoping, History scroll restoration, Progress segment restoration and preview image space at 320 dp/large text.

The expanded Android matrix exposed a real editor bug: at Portuguese/320 dp/2.01 font scale, the title wrapped almost character by character between Back, Done and the menu, consuming the editor viewport (`a11y-retry-progress.png`). `7fc1b2e` follows the existing `TopBar(null)` contract: actions stay in the bar and the complete editing heading moves into scrolling content. Reading mode is unchanged. A direct minimum 56 dp editor-viewport assertion prevents another misleading scroll timeout; no timeout, font or target-size requirement was relaxed.

## Tests and CI

Results are tied to the source/checkpoint stated below. Earlier failures are retained rather than reported as a single green full run.

| Check | Actual result |
| --- | --- |
| Initial full local host checkpoint | 1,335 cases: app 569, shared 303, design system 127, screens 336. The latter three passed. Five Phase-5B setup/legacy expectation issues were corrected; all 28 affected app cases passed on rerun. |
| Windows baseline failures | Remaining 19 app failures reproduced unchanged in the released `c9ba9b1` source: 15 API-26 photo/native-image cases and four JVM DataStore file-replacement cases. No tests were skipped/weakened. Linux CI passes them. |
| Full Android screens checkpoint | 1,009 cases / one failure, 1,171.42 seconds. All 1,008 functional/UI cases passed; only the debuggable index-timing allowance failed (270.840 ms median >240 ms). |
| Full Android design system | 428 passed, no failures, 486.986 seconds. Calendar S2 separately passed seven UI cases with 16 captures. |
| Metadata-cache host checks | 80 passed, including search-fold parity and full-snapshot/index invalidation. Both newly added regression methods also passed on Android: two cases, 0.451 seconds (`cache-regressions-android.log`). |
| Post-cache affected Android run | 31 cases: 30 passed, one saved-editor accessibility method timed out. The follow-up screenshot established the viewport bug, rather than a performance fix being inferred from a build. |
| Final editor correction | All 28 saved-detail cases plus the saved-editor/preview matrix passed: 29 methods, 194.013 seconds (`editor-header-fix-ui.log`). That method covers all 32 language/theme/width/font looks. Light/dark Portuguese 320 dp/2.01 captures were inspected. |
| Final muscle-map correction | 20 Android methods passed in 119.648 seconds on the Android 15 Medium_Phone emulator. This covers History restoration and end-to-end flows, Progress period switching and the chart accessibility matrix (`integrated-map-layer-android-ui.log`). |
| Timing failure rerun | Passed unchanged loose allowance after cache fix: debug index median 177.560 ms, p95 361.000 ms, n=9; chart/query/key loose checks also passed. This is not the R8 budget measurement (`final-debug-index-timing.txt`). |
| Foreground calendar | Focused Android test passed: refreshes date/zone without rebuilding an unchanged index or recomputing an equal query. |
| Completed cache-revision CI | [42032537216](https://github.com/ReiTut/Mooscles/actions/runs/42032537216), source `26a8889`: all seven jobs succeeded. Host 1,337 /194 classes (app 569, shared 303, DS 127, screens 338); iOS shared 283 /27, DS 428 /70, screens 1,006 /86. Zero failures/errors/skips in all reported aggregates. |
| Final editor-source host CI | [42078692993](https://github.com/ReiTut/Mooscles/actions/runs/42078692993), source `7fc1b2e`: Linux host passed 1,337 tests /194 classes, zero failures/errors/skips (`ci-editor-fix-host.log`). |
| Final editor-source iOS CI | Initial attempt never acquired a hosted runner; canceled after 15 minutes. GitHub explicitly reported macOS arm64 capacity constraints (`ci-editor-fix-ios-runner-capacity.json`). Only that job was retried; the retry also failed to acquire a runner after 15 minutes (`ci-editor-fix-ios-retry-capacity.json`). No tests ran in either attempt. Final native execution is blocked by hosted-runner capacity, not reported green. |

Earlier iOS source run 41975113848 passed 283 shared /428 DS /1,003 screens cases. A same-source follow-up stalled at the screens test task for 52m41s and its cause remains unresolved. The later complete cache-revision run passed. These results apply to their recorded revisions. The owner's final verification scope excludes iOS-specific checks, so the local muscle-map change was tested on Android and kept local to avoid starting the iOS CI job.

Current CI minima: host 1,337, shared iOS 283, DS iOS 428, screens iOS 1,006. The final full Android screens count is expected to be 1,012; that complete count has not been freshly run. Full earlier suites plus the necessary affected reruns are reported separately.

Earlier focused detail-model verification passed seven cases. Common metadata and Android instrumentation packaging passed after the map change. Clean debug/preview packaging and lint then passed in 2m27s (`final-android-clean-package.log`). Lint reports zero errors and 120 advisory warnings. The design-token guard passed 178 files. No schema JSON changed.

## Offline upgrade and data compatibility

On owned emulator `emulator-5560`, isolated package `com.mooscles.phase5b.upgrade2` upgraded start-of-Phase-5A source `e6ada6a61d2174d51815bc412d124dcd0a22caf7` (1.4.5 /Build 16) to Phase 5B (1.5.3 /Build 25), using the original matching signer. Connectivity showed no active default network before installation; emulator Wi-Fi/data were restored afterwards.

Both after-install and after-MainActivity observations passed all 14 named checks:

- 209 workouts (208 finished plus one active), 12,691 sets, one routine/three planned sets; schema remains 6.
- Historical PR-exercise total 1,800 and current-record count 1,845 unchanged. Three selected workout facts remain 61 sets/10,220 kg, 61/35,770 kg and 61/19,162.5 kg.
- Exact active weight 22.1234567890123 kg, blank unfinished set, completed working/warm-up flags, identities and ordering retained.
- Timer token 1793883479308, deadline 1793883779308 and workout/exercise ownership unchanged; timer still running before its deadline at both observations. This does not independently prove actual alarm delivery.
- Portuguese app locale, pounds, dark theme, automatic rest, explicit 185-second default and selected folder retained.
- Raw preferences identical: 357 bytes, MD5 `b5530b5a8ca4e79a05d05e45492f37f3`, SHA-256 `2a00fb0e28e59646240e3f1d12bc270fc51d563d5f698862fe6fd09736729221`.
- Actual BackupCodec export identical using fixed probe metadata/export timestamp: SHA-256 `fadfcea380a22c69ccbbfeeb1e9abb5e2b674e9533e7e70635e03f4fbc1aba83`.

Evidence: `perf-setup\upgrade-results-20261105-095753\phase5b-upgrade\comparison.json` and before/after JSON, encoded exports and raw preferences. Parent `inputs.json` records actual APKs/signers. The initial setup attempt was protected by its existing-data guard; its separate synthetic package was not cleared or overwritten.

Actual offline Portuguese/pounds History, Progress, saved detail/menu and two-page PNG preview rendered (`offline-*.png`/XML). Nothing was sent. Native renderer/intent tests separately prove PNG encoding and sharing permissions; preview layout tests alone are not encoding proof.

Source review found unchanged schema/data, preferences, backup, manifest, updater, timer implementation and application identity/version contracts. Only existing set-save/delete subject IDs were added to existing command-error bookkeeping. No network code or runtime dependency was added.

## Performance and visual limitations

See [performance report](2026-11-05-phase5b-performance.md) and [screen critique](2026-11-05-phase5b-critique.md). The controlled R8 cache comparison reduced index median 79.2168→58.5348 ms (26.1%), p95 98.3670→72.1725 ms. This is versus initial Phase 5B, not a general startup/scroll improvement or a claim that the released app is slower.

On the approved Android 15 phone, the isolated R8/profileable Build 25 ran against 208 synthetic workouts and 12,688 sets. History (1.14–1.35%), Records (1.53–1.90%) and the Library control (1.21–1.52%) met 5%. Overall Progress dropped from 256/3,536 janky frames (7.24%) to 51/3,675 (1.39%) in the repeated baseline-to-layer comparison. The map's path-heavy draws fell from 428 to 13 in the sampled traces. Both map screenshots retained their content and proportions. Layer allocation and lazy-item re-entry still cause occasional expensive draws. See the performance report for all four blocks, test conditions and limitations.

Before the layer, the host-GPU emulator measured History at 11.12–13.62%, Library at 10.22–13.61% and Overall Progress at 19.00–22.16%. These measurements exceeded 5% and were retained as earlier evidence. Final-layer emulator frame timing was outside the affected functional rerun. Hardware GPU timing remains unmeasured.

A final-source trace confirms one DefaultDispatch index build per kg/lb switch (six switches/six builds), but its in-app median 77.133 ms exceeds the 60 ms goal. The controlled standalone result must not be generalized to every app condition. Navigation-to-visible-frame and active-workout rendering comparisons remain unverified.

Conditions: Medium_Phone AVD, emulator-5560, Android 15/API35 x86_64, 1080×2400/60 Hz, WHPX, host OpenGL translation on AMD Radeon RX7800XT; Ryzen7 5700X3D/32GiB host. Original animations restored before release-like capture (1.0/1.0/default). Final test changes also restored animations; emulator Wi-Fi/data are on as originally recorded.

Current and earlier iOS captures remain separate in `ios-final-captures/` and `ios-captures/`. The latest completed native run produced 145 captures; selected calendar/detail/preview states were inspected. These are simulator/layout proof, not an actual iOS app, VoiceOver session or native Android PNG decoding. Final editor-fix Android captures are in `final-editor-captures/`. Actual native exported PNG samples were retained in `exported-pngs/` before cleaning app/build.

5A carryovers include foreground refresh, convention labels and list restoration. Dispatching-context cache assumptions are documented. Long-range axis labels remain short and table/readout/spoken dates retain years. Fresh pre-33 region changes, real iPhone region changes, physical TalkBack/VoiceOver, finger gestures/haptics and S23-specific performance remain unverified. The tested phone's nominal 120 Hz results are recorded separately.

## Verified final APKs

Original certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk` | 18,214,392 | `8b4bac453104dc1201e7c4a7ba04828e83bfb409fe060ccb0f1806f098ecaf10` |
| `D:\Programing\Mooscles\app\build\outputs\apk\preview\app-preview.apk` | 18,099,752 | `ac41a8b2bba37969546198032e6b5e8cf631eae1d651dfced9dff965c7d0497b` |

Debug is `com.mooscles` 1.5.3 / Build 25, minSdk 26 and target 35. Preview is `com.mooscles.preview` 1.5.3-preview / Build 25 with independent data and updates disabled. Both verify with the original signer. `final-map-layer-apk-verification.json` retains the manifest and hash evidence.

The debug APK is 6,254,816 bytes below the 24,469,208-byte phase base and 6,779,104 bytes below the maximum allowed size. The combined fonts and icon upper bound is 938,769 bytes, leaving 109,807 bytes of headroom. Compressed fonts use 645,546 bytes and compiled icon classes account for an upper bound of 293,223 bytes. The budget and lint settings are unchanged. Removing the unused Material icons library accounts for the large packaging reduction.

## Continuation and phone gate

The owner approved the focused physical-phone comparison on serial `4eb066eb`. Only `com.mooscles.phase5b.phone20261105` was installed and profiled. The production app remained 1.5.2 / Build 23 with the same data directory, and the animation and stay-awake settings were unchanged. The isolated app was stopped and remains installed with its synthetic data. Raw metadata and test procedure are in `phone-before.json`, `phone-after.json`, `final-phone-preservation.json` and `PHONE-CHECK-PLAN.md`.

Any future phone-dependent check requires a concrete procedure, explicit approval, and a complete stop of all work while waiting. The approved comparison is complete.

The continuation point is the live unit-switch budget and unmeasured visible-frame and active-render timing. Keep the branch unmerged and version unchanged. Phase 6/7 and release work remain outside this checkpoint.
