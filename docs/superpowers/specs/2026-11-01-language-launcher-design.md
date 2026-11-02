# Language selection and launcher icon

Scope approved directly by the user's implementation request on 1 November 2026. This is independent of Phase 5; no Phase 5/5B planning, history redesign, calendar or progress feature is authorized by this change.

- Add a Settings language group with System default, English and Português (Brasil). System default remains the initial behavior, and no snapshot of the device language is persisted as an explicit choice.
- Android 13+ uses `LocaleManager.applicationLocales` as the sole source of truth. Declare the two supported languages in `locales_config.xml`. Existing legacy metadata migrates once on an OS upgrade; framework selections win.
- Android 8–12 uses isolated Android-only locale metadata, outside workout DataStore/backups. Apply it before UI creation and update Activity/Application resource configurations in place. Clearing it reads the actual system configuration.
- Handle locale/layout-direction configuration changes without rebuilding navigation/editor owners. Existing resource-based UI and catalog names follow the selection; user content and numeric model values never change. Preserve partial drafts and exact pending kilograms across separator changes.
- Translate existing legacy Android labels as a localization-only change, without redesigning those screens or changing their commands. Existing domain/updater messages not yet modeled as localized resources must be reported separately.
- Package the supplied white-M/green-barbell image as launcher assets. Remove presentation framing, preserve proportions and adaptive safe bounds, include themed/round/density assets, and preserve the preview identifier. Notification icons remain unchanged.
- Preserve app ID, version 1.5.0/code 21, signing identity, schema, all saved data, existing backups and unrelated local edits. No remote writes or release.

Platform references: [Android per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages), [runtime configuration handling](https://developer.android.com/guide/topics/resources/runtime-changes). Android documents that locale setters normally recreate Activities unless the app handles the configuration change; this app handles it to protect in-flight editor state.

Verification is focused: language ownership/persistence/system behavior, Settings resource updates/navigation, draft and exact-weight regression checks, string parity, icon resource/mask checks, lint and debug build. Use an isolated/read-only emulator when available; never uninstall or clear existing data.
