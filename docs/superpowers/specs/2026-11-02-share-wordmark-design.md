# Shared workout wordmark replacement

Scope explicitly authorized by the user's implementation request: replace only the workout-card wordmark with the supplied white/lime artwork. Phase 5/5B stays paused. No app version, launcher, data, signing, sharing destinations, export format, push or release changes.

The shared `ShareCardPainter` draws the header on every page; Android previews decode the same exported PNGs. Pass the real wordmark bitmap to this painter, prepared and process-cached outside composition/drawing. A shared resource keeps Android and future iOS rendering aligned without a platform-only logo implementation.

Use the explicitly named `4e3d7e56-87a9-4443-8d49-b1b8ae5505ef.png` as the source, not approximated typography. Remove only its presentation background, preserving interior colors, barbell details and antialiased edges. Preserve proportions and position within the existing header band. Retain the old header's measured allocation so titles/statistics and page breaks do not move; the old letters and plate paths are no longer drawn.

Keep the existing 1080px PNG width, per-page memory/export behavior, file names/retention, FileProvider, intents, localization, exact values and read-only saved snapshot handling.

Verification: affected renderer/export/preview/intent host tests, updated source-art assertions on every page, real PT-BR export, generated PNG inspection, shared metadata/device-test compilation, focused painter/text device tests when available, lint, token/size checks and debug build. No unrelated benchmarks or suites. Record unavailable iOS/physical-device checks and the 260×72 source-resolution limit.
