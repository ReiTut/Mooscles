# Language selection and launcher implementation plan

Implements only the [user-authorized language/icon scope](../specs/2026-11-01-language-launcher-design.md). Phase 5 remains paused.

1. Inspect current handoff, instructions, locale resources, editor ownership, launcher layers, SDK and available devices. Preserve the existing Gradle edit and earlier worktrees/backups.
2. Add Android locale controller, legacy metadata/context support and Android 13 language declaration. Wire Settings through a composition-local controller without adding a shared workout preference or dependency.
3. Preserve planned-set drafts when formatting locale changes. Compare decimal separators equivalently before reparsing displayed weights, retaining original exact kilograms for untouched values.
4. Localize existing Android legacy labels without changing their screens or behavior. Keep English keys/text compatible with existing tests and add parity coverage.
5. Convert the supplied source into launcher layers/density assets; inspect circle/squircle/rounded-square masks and preserve notification/preview behavior.
6. Run only affected locale, Settings, gallery, draft, string and icon checks. Resolve affected failures, run lint/debug build, verify package/version/signer. Exercise available emulator languages and masks; document remaining limits.
7. Write a concise verification report and leave the local branch ready for development. Do not change versions, push or publish.
