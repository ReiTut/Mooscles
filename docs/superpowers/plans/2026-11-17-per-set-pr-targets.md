# Per-set PR targets implementation

See the [design](../specs/2026-11-17-per-set-pr-targets-design.md).

1. Add nullable workout set provenance and validate the additive migration with legacy, active, and completed data.
2. Save the planned set ID when starting a routine and carry it through edits, Undo, and backup export/import.
3. Reuse the weight-first ranking for each validated origin. Persist only changed inherited targets after finish, initialization, or import. Keep existing values when a set has no match.
4. Check real Room persistence, independent targets, restored and reordered rows, eligible performances, legacy backup compatibility, and the next workout's initial values. Build and lint the Android APK and run the focused emulator checks.
