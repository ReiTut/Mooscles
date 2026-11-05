# Phase 5B — implementation and verification checkpoint

Updated 5 November 2026. Implementation is complete on the feature branch; performance acceptance and final-source iOS execution remain open. No Phase 6/7, merge, version change, tag or release is included.

## Source and preservation

Repository: [ReiTut/Mooscles](https://github.com/ReiTut/Mooscles). Branch: `codex/phase5b-history`.

| Commit | Change |
| --- | --- |
| `c9ba9b1a226d7cbc41acbca19dcba09ae9f6447e` | Starting current-release baseline, version 1.5.3 / Build 25. |
| `9a0740a521af131dac2f8683a5d569ac19f85fa7` | Shared History, overall progress, saved detail/editing and common share preview. |
| `e264f7186652d38eaaf0535dd790363026e6f098` | CI host-stall diagnostics and checkpoint critique. |
| `26a8889d53a37bff9736fc97b65d7b4001b3aa51` | Build-local exercise metadata reuse, parity/invalidation tests, foreground refresh coverage and expanded accessibility matrix. |
| `7fc1b2e3eacad1d6d667432efe6a7e84fe3e0a33` | Usable saved-editor viewport at narrow widths/large fonts; full heading remains in scrollable content. |

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
| Timing failure rerun | Passed unchanged loose allowance after cache fix: debug index median 177.560 ms, p95 361.000 ms, n=9; chart/query/key loose checks also passed. This is not the R8 budget measurement (`final-debug-index-timing.txt`). |
| Foreground calendar | Focused Android test passed: refreshes date/zone without rebuilding an unchanged index or recomputing an equal query. |
| Completed cache-revision CI | [42032537216](https://github.com/ReiTut/Mooscles/actions/runs/42032537216), source `26a8889`: all seven jobs succeeded. Host 1,337 /194 classes (app 569, shared 303, DS 127, screens 338); iOS shared 283 /27, DS 428 /70, screens 1,006 /86. Zero failures/errors/skips in all reported aggregates. |
| Final editor-source host CI | [42078692993](https://github.com/ReiTut/Mooscles/actions/runs/42078692993), source `7fc1b2e`: Linux host passed 1,337 tests /194 classes, zero failures/errors/skips (`ci-editor-fix-host.log`). |
| Final editor-source iOS CI | Initial attempt never acquired a hosted runner; canceled after 15 minutes. GitHub explicitly reported macOS arm64 capacity constraints (`ci-editor-fix-ios-runner-capacity.json`). Only that job was retried; the retry also failed to acquire a runner after 15 minutes (`ci-editor-fix-ios-retry-capacity.json`). No tests ran in either attempt. Final native execution is blocked by hosted-runner capacity, not reported green. |

Earlier iOS source run 41975113848 passed 283 shared /428 DS /1,003 screens cases. A same-source follow-up stalled at the screens test task for 52m41s; cause remains unresolved. The later complete cache-revision run passed. These are distinct events, not established production failures. The CI changes add test names/diagnostics and bounded tasks without raising assertions or lowering minima.

Current CI minima: host 1,337, shared iOS 283, DS iOS 428, screens iOS 1,006. The final full Android screens count is expected to be 1,012; that complete count has not been freshly run. Full earlier suites plus the necessary affected reruns are reported separately.

Final local source build: focused detail-model seven cases, common metadata, lint and clean debug/preview packaging passed. Debug lint has zero errors /120 advisory warnings. Design-token guard passed 178 files. No schema JSON changed.

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

The 5% scrolling target remains unmet: History 11.12–13.62%, unchanged Library control 10.22–13.61%, Overall Progress 19.00–22.16%. Supplemental Perfetto traces show substantial RenderThread work and graphics-handoff waits, plus some History layout/prefetch outliers. No hardware GPU tracks or allocation stacks establish the division between app drawing, emulator translation and host driver.

A final-source trace confirms one DefaultDispatch index build per kg/lb switch (six switches/six builds), but its in-app median 77.133 ms exceeds the 60 ms goal. The controlled standalone result must not be generalized to every app condition. Navigation-to-visible-frame and active-workout rendering comparisons remain unverified.

Conditions: Medium_Phone AVD, emulator-5560, Android 15/API35 x86_64, 1080×2400/60 Hz, WHPX, host OpenGL translation on AMD Radeon RX7800XT; Ryzen7 5700X3D/32GiB host. Original animations restored before release-like capture (1.0/1.0/default). Final test changes also restored animations; emulator Wi-Fi/data are on as originally recorded.

Current and earlier iOS captures remain separate in `ios-final-captures/` and `ios-captures/`. The latest completed native run produced 145 captures; selected calendar/detail/preview states were inspected. These are simulator/layout proof, not an actual iOS app, VoiceOver session or native Android PNG decoding. Final editor-fix Android captures are in `final-editor-captures/`. Actual native exported PNG samples were retained in `exported-pngs/` before cleaning app/build.

5A carryovers: foreground refresh, convention labels and stronger list restoration are covered. Dispatching-context cache assumptions are documented. Short long-range axis labels remain; table/readout/spoken dates retain years. Fresh pre-33 region changes, real iPhone region changes, physical TalkBack/VoiceOver, finger gestures/haptics, 120 Hz and S23 performance remain unverified.

## Verified final APKs

Original certificate SHA-256: `0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35`.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `D:\Programing\Mooscles\app\build\outputs\apk\debug\app-debug.apk` | 18,214,392 | `0d895ba6766e63d90c672523c5d3766b12f1539fa578188f340441d9934d49c9` |
| `D:\Programing\Mooscles\app\build\outputs\apk\preview\app-preview.apk` | 18,099,752 | `e282829f2dd278b496fe38f699895fc26207bec09a2808a8868fb9591a5c03b8` |

Debug: com.mooscles, 1.5.3 /25, minSdk26/target35. Preview: com.mooscles.preview, 1.5.3-preview /25, independent data, updater disabled. Both verify with the original signer. `final-apk-verification.json` retains the manifest/hash evidence.

Unchanged budgets pass: fonts compressed 645,546 bytes; icon upper bound 293,223; combined 938,769 below 1,048,576. APK growth −6,254,816 against phase base 24,469,208, limit +524,288. No budget increase or lint suppression. The unused Material icons library removal accounts for the large packaging reduction.

## Continuation and phone gate

Physical phone `4eb066eb` was listed online/authorized by ADB inventory only. No phone installation, settings access, interaction, test or storage operation occurred. A uniquely isolated optimized APK is prepared (not installed): com.mooscles.phase5b.phone20261105, visible label Mooscles Phase 5B Test; exact metadata in `prepared-phone-apk.json` and proposed procedure in `PHONE-CHECK-PLAN.md` under the ignored evidence root.

Before any phone-dependent check, explain the concrete build/actions/settings/protection and request explicit approval; then stop all agents, builds, tests and Git work until the answer. Connection is not approval. The emulator cannot separate its translation/host effects from the observed graphics pressure; a phone comparison could help, but cannot retrospectively make the emulator budget pass.

Remaining acceptance: final editor-source iOS execution when runner capacity permits, bounded investigation of the frame/unit-switch budget misses and unverified visible-frame/active-render timing. Keep the branch unmerged and version unchanged. Do not advance to Phase6/7 or publish a release.
