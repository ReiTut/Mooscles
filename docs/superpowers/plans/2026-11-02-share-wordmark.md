# Shared workout wordmark plan

Implements only the [user-authorized wordmark design](../specs/2026-11-02-share-wordmark-design.md); Phase 5/5B is paused.

1. Inspect current source/instructions/handoff, sharing painter/export/preview and relevant tests; preserve the unrelated Gradle edit and backups. Create a local branch from current main.
2. Inspect the explicitly named source and prepare a faithful trimmed alpha resource; record geometry/hash and inspect compositing on the actual card background.
3. Load/decode the common asset once outside drawing. Inject it into the common painter and Android export caller, retaining header allocation, body geometry and pagination.
4. Update existing brand assertions to source-art/color/transparency checks, retain content/long-workout assertions and add a real Portuguese export case. Keep UI/timing helper constructors compiling and update the host CI test floor.
5. Run only affected host and device checks, common metadata compilation, lint and debug packaging. Inspect exported single/multi-page PNGs and preview/sharing behavior on a permitted emulator, without sending anything or clearing app data.
6. Record verified artifacts, file changes, test evidence and limitations; leave changes local. Do not change app version, push or publish.
