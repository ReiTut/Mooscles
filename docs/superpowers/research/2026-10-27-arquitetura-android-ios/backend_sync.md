# Backend and data-sync options for an offline-first workout journal (Android + iOS), as of Oct 2026

Context assumed: Room/SQLite, ~9 relational tables, string UUID PKs, additive migrations, no account/no analytics, small team in Brazil. Research date: 2026-10-27. Pricing pages fetched on that date; most vendor pricing pages carry no "last updated" date.

## 1. No-backend options (file export/import, OS backups) and their limits

### Takeaway
A no-backend app can cover backup/restore and even Android<->iOS migration in 2026: a user-initiated export file (JSON or SQLite) through the share sheet is the only fully portable path, and new OS-level cross-platform transfer hooks now exist on both sides (Android 16 QPR2 `cross-platform-transfer`, Apple AppMigrationKit). Android Auto Backup is capped at 25 MB, keeps only the latest snapshot, and runs about once a night. It works for same-platform restore but gives no multi-device sync.

### Cited Findings
- Android Auto Backup: "Every app can allocate up to 25 MB of backup data per app user". It is stored in a private Google Drive folder and doesn't count toward the user's Drive quota. Over 25 MB the system calls `onQuotaExceeded()` and stops cloud backup until data shrinks — [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- Auto Backup conditions: the user has backup enabled, at least 24 h have passed since the last backup, the device is idle, and Wi-Fi is on unless mobile data is allowed. "In practice... roughly every night, but a device might never back up". Apps in the foreground are skipped by default. "Only the most recent backup is stored." — [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- Databases under `getDatabasePath()` are included by default. Backups are end-to-end encrypted on Android 9+ when the device has a screen lock. `disableIfNoEncryptionCapabilities` (Android 12+) can turn off unencrypted cloud backup — [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- Restore happens on install (Play Store, the setup wizard, or adb), after the APK is installed and before first launch. Device-to-device transfer is configured separately on Android 12+ — [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- **New:** "Starting from Android 16 QPR2 (API level 36.1) you can configure Auto Backup for data transfers to and from non-Android devices". `<cross-platform-transfer platform="ios">` takes `bundleId`, `teamId` and `contentVersion`. `BackupAgent` exposes `FLAG_CROSS_PLATFORM_TRANSFER_IOS` — [Android Developers: Auto Backup](https://developer.android.com/identity/data/autobackup)
- **New (iOS side):** Apple's AppMigrationKit is a framework for moving third-party app data between iPhone and Android. Developers implement an app extension that imports, exports, or both. "AppMigrationKit only supports migration to and from non-Apple platforms". It was in beta with iOS/iPadOS 26.1, reported Oct 22, 2025 — [Android Central](https://www.androidcentral.com/apps-software/android-os/this-new-apple-framework-will-make-moving-app-data-from-ios-to-android-seamless); [MacMagazine (pt-BR), 2025-10-22](https://macmagazine.com.br/post/2025/10/22/novo-framework-permitira-migrar-dados-de-apps-do-ios-pro-android-e-vice-versa/); [MobileSyrup, 2025-10-22](https://mobilesyrup.com/2025/10/22/apple-framework-app-data-migration-android/)
- Comparable apps: one comparison says Strong has "solid CSV export" and Hevy offers "basic export". This is a competitor's blog (Setgraph), so it is low-reliability — [Setgraph, 2026](https://setgraph.app/articles/hevy-vs-strong-app-comparison-2026)

### Inferences
- With the 25 MB cap, a Room DB of workout logs (small numeric rows) fits for years for typical users. A heavy user with many years of sets plus WAL files could approach it. Excluding `-wal`/`-shm` files and checkpointing before backup is prudent.
- Cross-platform migration with no backend can be layered: (a) a manual export file, which works everywhere today and also satisfies the LGPD portability right (Art. 18 V); (b) OS-level hooks (Android 16 QPR2 + AppMigrationKit) as a polished path, which only work during the OS's phone-switch flow and only on recent OS versions.
- A canonical, versioned export schema (for example JSON with `schemaVersion`, UUIDs preserved, and soft-delete flags) is the foundation for every later option: file backup, cloud-drive backup, OS transfer, and server sync.
- OS backups don't sync: they are snapshot-only, keep the latest copy, and restore only on install. Multi-device use (phone + tablet) needs something else.

### Gaps
- iCloud device backup and iCloud Drive behavior for app containers (size limits, `isExcludedFromBackup`, restore semantics) was not fetched this session. Based on prior knowledge, which was not verified here: an iOS device backup includes the app's Documents/Library unless excluded, and restores only to iOS.
- AppMigrationKit's GA status and final API in iOS 26.x, and whether Android 16 QPR2's iOS transfer is shipped broadly and pairs with AppMigrationKit, were not confirmed with Apple docs this session. Sources are Oct 2025 news coverage.
- No official Strong/Hevy help-page details on export formats were found.

## 2. User-owned cloud storage (iCloud Drive/CloudKit, Google Drive appData, Dropbox)

### Takeaway
The Google Drive `appDataFolder` (REST API, scope `drive.appdata`) is the only user-owned storage reachable from both Android and iOS with a hidden, app-private folder, so it suits a cross-platform "backup snapshot" without your own server. CloudKit and iCloud Drive are Apple-only in practice. Dropbox (not researched in depth) is cross-platform but visible to the user. None of these gives row-level sync. They are file-snapshot stores.

### Cited Findings
- Drive appData requires the "non-sensitive" scope `https://www.googleapis.com/auth/drive.appdata`. "This folder is only accessible by your app", and it can't be accessed through the Drive UI. It is created automatically on first write — [Google Drive API: Store application-specific data](https://developers.google.com/workspace/drive/api/guides/appdata)
- "The application data folder is deleted when a user uninstalls your app from their My Drive". Users can delete it manually. Files there can't be shared, trashed or moved. "Only the application that created the data in the appDataFolder can access it" — [Google Drive API appdata guide](https://developers.google.com/workspace/drive/api/guides/appdata)
- The appData folder is separate from the Drive backup folder that Android device backups use — [Google Drive API appdata guide](https://developers.google.com/workspace/drive/api/guides/appdata)

### Inferences
- A non-sensitive scope implies a lighter Google OAuth verification burden than full Drive scopes. That is an inference from the "non-sensitive" label, not from the verification policy itself.
- The "same app" restriction most likely means the same Google Cloud OAuth project/client. Android and iOS OAuth clients under the same Cloud project should both reach the folder, which is how cross-platform appData backup apps usually work. Verify this before committing.
- Pattern: serialize the DB to a JSON or SQLite file, encrypt it client-side with a user passphrase if desired, then upload it on a schedule. Restore means download + import. Two devices writing alternately would need merge logic (see section 5). Treat it as backup, not sync.
- Using the user's own Drive keeps the "no account (of ours)" promise. The developer never holds the data, which lightens LGPD exposure (see section 6). Users still have to sign in with Google, including on iOS.

### Gaps
- Whether appData files count toward the user's Drive quota was not stated on the fetched page.
- CloudKit (private database, free quota tied to the user's iCloud, no Android SDK; CloudKit JS/Web Services exist) and Dropbox API limits were not fetched this session. Treat them as unverified.

## 3. Managed backends with offline sync: state, SDKs, deprecations

### Takeaway
For a relational SQLite app that wants real multi-device sync, PowerSync (Postgres/Supabase, MongoDB, MySQL or SQL Server as source; client-side SQLite; official Kotlin, Swift, Flutter and React Native SDKs; self-hostable Open Edition) maps most directly onto the current Room schema. **Realm/Atlas Device Sync is dead: it reached EOL on Sept 30, 2025.** ElectricSQL is now read-path-only sync with TypeScript/Elixir clients. Turso Sync is promising but still beta-ish on conflict handling. Firestore and Couchbase are document models that would require remodeling.

### Cited Findings
**PowerSync**
- Client SDKs: React Native, JavaScript Web, Dart/Flutter, Kotlin, Swift, .NET, Node.js, Tauri, Capacitor, Rust — [PowerSync pricing page](https://powersync.com/pricing.md) (fetched 2026-10-27, page undated)
- Source DBs (self-hosted Open Edition): Postgres, MongoDB, MySQL, SQL Server, Azure DocumentDB (alpha). The Open Edition is "free, source-available self-hosted" — [PowerSync pricing](https://powersync.com/pricing.md)
- MongoDB partnered with PowerSync as a migration path from Device Sync. This is from secondary commentary — [Realm is Deprecated. What now?](https://loopcafe.substack.com/p/realm-is-deprecated-what-now)

**Realm / Atlas Device Sync (DEPRECATED)**
- Atlas Device SDKs were deprecated in September 2024, with EOL and removal on September 30, 2025. Apps had to migrate or remove Sync by that date — [MongoDB docs (Realm)](https://mongodb.com/docs/realm); [MongoDB forum: Device Sync and Edge Server are Deprecated](https://mongodb.com/community/forums/t/device-sync-and-edge-server-are-deprecated/296035)
- The local Realm DB continues as open source, but a forum reply says SDK versions 20.x+ don't support cloud sync — [MongoDB forum](https://www.mongodb.com/community/forums/t/update-to-end-of-life-and-deprecation-notice/297168?page=2); [realm-js discussion #6884](https://github.com/realm/realm-js/discussions/6884)

**ElectricSQL (now at electric.ax)**
- electric-sql.com redirects (301) to electric.ax. "Electric does not do write-path sync". Writes go through your own API, using optimistic state or through-the-database sync with PGlite. Documented clients are TypeScript and Elixir. Expo is listed as a platform integration. TanStack DB is a sync primitive — [Electric docs: Writes](https://electric.ax/docs/guides/writes)

**Turso / libSQL**
- Embedded Replicas are "fully supported in production, but for new projects that need sync, we recommend Turso Sync". Replicas read locally, and writes go to the cloud primary by default — [Turso docs: Embedded replicas](https://docs.turso.tech/features/embedded-replicas)
- The Turso Sync / offline-writes public beta offers bidirectional sync and conflict *detection*. "Conflict resolution is not yet implemented" as of the beta post. It supports Expo/React Native — [Turso blog: offline sync public beta](https://turso.tech/blog/turso-offline-sync-public-beta); [Turso: Introducing Offline Writes](https://turso.tech/blog/introducing-offline-writes-for-turso). The posts are undated in the results and may be superseded. Turso's [local-first page](https://turso.tech/local-first) suggests configurable sync strategies, which conflicts with the beta note.

**Firebase Firestore**
- Free quota (Standard edition): 50,000 document reads, 20,000 writes and 20,000 deletes per day. It applies to the (default) database only, one free database per project — [Firestore pricing](https://cloud.google.com/firestore/pricing); [Firestore Standard edition overview](https://firebase.google.cn/docs/firestore/standard-edition)
- With offline persistence on, a listener disconnected for more than 30 minutes is billed as a new query on reconnect. This wording is from the Enterprise edition page — [Firestore Enterprise pricing](https://cloud.google.com/firestore/enterprise/pricing?authuser=0)

**Couchbase Lite + Capella App Services**
- The Capella free tier includes Mobile App Services on one 8 GB node, with no credit card. Paid tiers start at $0.15/hr per node (Basic), $0.35/hr (Developer Pro) and $0.49/hr (Enterprise) — [Couchbase pricing](https://www.couchbase.com/?p=1193); [Couchbase blog: free tier](https://www.couchbase.com/blog/free-tier-capella-dev-available/)

### Inferences
- PowerSync fits best because its client is SQLite with a relational schema. The Kotlin SDK is Kotlin Multiplatform-oriented, and the Swift SDK covers native iOS. Room itself is not the PowerSync client DB, though. Adoption would mean either replacing Room's DAO layer with PowerSync's SQLite (or a Room/SQLDelight integration if one exists) or writing an adapter. That is a significant change.
- Firestore and Couchbase Lite are document stores. Mapping 9 relational tables means either denormalizing (workout → exercises → sets as nested documents) or keeping many collections and handling joins client-side. They work offline-first but impose their own local store in place of Room.
- ElectricSQL is a poor fit for native Kotlin/Swift apps today: there is no native client, and writes are left to you.
- Turso Sync is the closest "SQLite file that syncs" story, but its beta-grade conflict handling and the unclear state of native Kotlin/Swift SDKs make it risky for a small team in 2026.
- Convex: not researched this session. Based on prior knowledge (unverified), it is a reactive cloud DB with TypeScript functions and React/React Native clients, not designed as offline-first local SQLite. It is likely unsuitable.
- CRDT options (Automerge, Yjs, cr-sqlite/vlcn, Ditto): not researched this session. Based on prior knowledge (unverified), cr-sqlite's maintenance slowed. Ditto is commercial, with Kotlin/Swift/Flutter SDKs. For a single-user journal, per-row LWW is usually sufficient, and CRDTs are overkill.

### Gaps
- Official current SDK lists for Supabase (supabase-kt is community-maintained; supabase-swift is official; Flutter and JS are official), Firebase KMP (GitLive community SDK) and Couchbase Lite (official Kotlin/Android, Swift, C, .NET, Java; community Flutter) were not verified this session.
- No official Electric Cloud pricing was fetched.
- Couchbase App Services' free-tier quotas for sync are not specified.

## 4. Pricing at small scale, operational burden, lock-in, data residency

### Takeaway
At hobby/early scale, PowerSync Free + Supabase Free costs $0 but **both pause or deactivate after about a week of inactivity**, so production needs PowerSync Pro ($49/mo) + Supabase Pro ($25/mo), roughly $74/mo before usage. A file-based backup to the user's Drive costs the developer $0 and has no ops burden. Firestore's free daily quotas are generous for a single-user journal but lock you into a document model.

### Cited Findings
- PowerSync Free: $0, 2 GB synced/month, 500 MB hosted, 50 peak concurrent clients, 2 instances. Deactivated after "1 week of inactivity" per the table, or "2 weeks" per the caveats, which is inconsistent on the page itself — [PowerSync pricing](https://powersync.com/pricing.md); billing docs say 7 days without deploys or connections → deprovisioned — [PowerSync usage & billing](https://docs.powersync.com/resources/usage-and-billing)
- PowerSync Pro: from $49/mo with 30 GB synced, 10 GB hosted and 1,000 peak concurrent clients. Overage is $1/GB synced, $1/GB hosted and $30 per 1,000 extra clients (max 3,000 by default). Team: from $599/mo (SOC 2/HIPAA availability). Pro invoices over $100 are reviewed with the owner before charging — [PowerSync pricing](https://powersync.com/pricing.md); [PowerSync billing FAQ](https://docs.powersync.com/resources/usage-and-billing/usage-and-billing-faq)
- Supabase Free: $0, 50,000 MAU, 500 MB DB, 1 GB file storage, 2 projects. It pauses after a week without traffic and needs a manual restore. Pro: from $25/mo with 100,000 MAU, no pausing and a $10 compute credit (Micro); extra MAU at $0.00325. Team: $599/mo. These figures come from third-party reviews, not the official page — [Jetadmin Supabase pricing 2026](https://www.jetadmin.io/blog/supabase-pricing-2026-guide-to-plans-limits-and-real-world-costs/); [Makerkit Supabase pricing 2026](https://makerkit.dev/blog/saas/supabase-pricing); [Jetadmin review, 2026-09-30](https://www.jetadmin.io/blog/supabase-review/)
- Firestore free: 50k reads, 20k writes and 20k deletes per day, one free DB per project — [Firestore pricing](https://cloud.google.com/firestore/pricing)
- Couchbase Capella: free tier with one node; paid from $0.15/node-hour — [Couchbase pricing](https://www.couchbase.com/?p=1193)

### Inferences
- Ops burden, lowest to highest: export file (none) < user's Drive/iCloud (OAuth plus file format) < Firestore (managed, but security rules plus a document remodel) < Supabase + PowerSync Cloud (schema, RLS, sync rules, two vendors) < self-hosted PowerSync Open Edition + Postgres on the owner's own VPS (full ops, but no per-MAU cost and full residency control).
- Lock-in: PowerSync over Postgres is the most portable, because the data stays in standard Postgres and the Open Edition can be self-hosted. Firestore and Couchbase have high lock-in, since their proprietary data models and SDKs replace Room.
- Data residency: Supabase and the major clouds offer a São Paulo region (AWS sa-east-1, GCP southamerica-east1). This is prior knowledge, not verified this session. Hosting in Brazil avoids LGPD international-transfer formalities (Art. 33) for the primary store, though subprocessors may still transfer data.
- At about 1k–10k users, a workout journal syncs small amounts of data (KB per session), so PowerSync Pro's included 30 GB is far above need. The fixed monthly minimum dominates the cost.

### Gaps
- The official Supabase pricing page was not fetched; the figures come from consistent third-party sources dated 2026.
- Firebase Auth's MAU pricing and Identity Platform tiers were not verified.
- PowerSync Cloud region options (EU/US/other; is there a Brazil region?) were not verified.

## 5. Conflict resolution for this data model

### Takeaway
For a single-user, multi-device workout journal with UUID PKs, the standard and sufficient approach is per-row (or per-field) last-write-wins on `updated_at` (ideally a hybrid logical clock), soft deletes/tombstones, and append-only treatment of completed workout sets. UUID PKs already remove insert-collision risk. Turso Sync doesn't yet resolve conflicts automatically, and PowerSync leaves write conflict policy to your backend API.

### Cited Findings
- Electric pushes writes to the app: online writes via your API, optimistic state, or through-the-database sync — [Electric docs: Writes](https://electric.ax/docs/guides/writes)
- The Turso Sync beta offers conflict detection, and conflict resolution is not yet implemented — [Turso offline sync public beta](https://turso.tech/blog/turso-offline-sync-public-beta)
- Turso's own guide on building local-first apps covers offline-first sync patterns — [Turso: Building Local-First Apps guide](https://turso.tech/blog/building-local-first-apps-the-complete-guide-to-offline-first-database-sync)

### Inferences
- Recommended model (an engineering inference, not vendor-sourced):
  - Add `updated_at` (HLC or server-assigned version), `deleted_at`/`is_deleted` (tombstone) and optionally `device_id` to every syncable table. This is additive and compatible with the existing additive-migration policy.
  - **Templates** (exercises, routine folders, routines, routine exercises, routine sets): LGPD-safe per-row LWW. Ordering fields (`position`) can conflict; use fractional indexing or LWW on the whole ordered list.
  - **History** (workouts, workout exercises, workout sets): once a workout is finished, treat it as effectively append-only, with edits as LWW per row. Two devices rarely edit the same past set at the same time.
  - **Checkpoints** (in-progress workout state): device-local. Don't sync them, or sync with LWW and a "this workout is active on another device" guard.
  - Tombstones must be kept long enough (or forever, since rows are tiny) so a device offline for months doesn't resurrect deleted rows.
- With PowerSync, conflicts are resolved in the developer's backend upload handler, for example a Supabase RPC that applies LWW by comparing `updated_at`. This is the vendor's documented pattern from prior knowledge, not re-verified here.
- With file-based backup (Drive/export), "restore" can be a full replace (simplest) or a merge by UUID with LWW on `updated_at`. Merge lets users combine two devices' histories during migration.

### Gaps
- No primary source was fetched this session on Ink & Switch's local-first essay or on PowerSync's conflict docs. The recommendations above rest on standard practice.

## 6. Privacy/legal: LGPD and store disclosures

### Takeaway
Workout logs (exercises, loads, reps, body-related checkpoints) are plausibly "dado referente à saúde", which makes them **dado pessoal sensível** under LGPD Art. 5 II. Once data leaves the device to a server the developer controls, the developer becomes a controller subject to Art. 11 (specific, prominent consent per purpose), Art. 46 (security), Art. 48 (incident notification) and Art. 33 (international transfer rules). Keeping data on-device or in the user's own Drive/iCloud largely avoids "collection" for the App Store label and minimizes LGPD controller duties.

### Cited Findings
- LGPD Art. 5 II defines sensitive personal data to include "dado referente à saúde ou à vida sexual, dado genético ou biométrico, quando vinculado a uma pessoa natural" — [Lei 13.709/2018 (Planalto)](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm); summarized in [Confidata: wearables, apps de saúde e LGPD](https://confidata.com.br/blog/wearables-apps-saude-lgpd-dados-pessoais)
- Art. 11 I: sensitive data may be processed "quando o titular ou seu responsável legal consentir, de forma específica e destacada, para finalidades específicas" — [Lei 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- Art. 18 V gives a portability right ("portabilidade dos dados a outro fornecedor de serviço ou produto, mediante requisição expressa"). Art. 41 requires appointing an encarregado. Art. 46 requires security measures. Art. 48 requires notifying the ANPD and the data subject of relevant incidents. Art. 33 lists the hypotheses under which international transfer is permitted — [Lei 13.709/2018](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm)
- No ANPD guidance specific to fitness apps was found. Commentators apply the general rule: data revealing health conditions is sensitive, and consent (Art. 11 I) is the common legal basis for personal-use apps. A blog says the ANPD 2025–2026 regulatory agenda prioritizes health and biometric data, but that was not confirmed with ANPD — [Confidata](https://confidata.com.br/blog/wearables-apps-saude-lgpd-dados-pessoais)
- Idec's position is that all health-area data should be treated as sensitive — [Idec: dados sensíveis na saúde](https://idec.org.br/dicas-e-direitos/dados-sensiveis-pela-lgpd-como-eles-sao-usados-na-area-da-saude)
- Apple App Privacy: "Collect" means "transmitting data off the device in a way that allows you and/or your third-party partners to access it for a period longer than what is necessary to service the transmitted request in real time". "Data that is processed only on device is not 'collected' and does not need to be disclosed". The data types include **Health** and **Fitness** ("Fitness and exercise data") — [Apple: App privacy details](https://developer.apple.com/app-store/app-privacy-details/)
- Apple's optional-disclosure criteria require data to be collected only "in infrequent cases that are not part of your app's primary functionality", so ongoing sync wouldn't qualify — [Apple: App privacy details](https://developer.apple.com/app-store/app-privacy-details/)

### Inferences
- **Store labels:** today, "Data Not Collected" on Apple and "no data collected" on Play are accurate. Adding a developer-run sync backend means declaring Fitness (and possibly Health) data plus identifiers/email (if accounts) on both stores. Under Apple's definition, a backup to the user's own iCloud/Drive that the developer can't access arguably isn't "collected" by the developer. This is an inference from the definition: "allows you... to access it".
- **End-to-end encryption** (client-side key derived from a user passphrase) before upload to any server would reduce breach impact (Art. 46/48). Apple's page doesn't grant an explicit E2EE exemption, so whether E2EE data counts as "collected" is unclear (gap).
- **LGPD posture by option:**
  - Export file / OS backup / user's Drive: the developer is not processing the data on its own servers, so controller duties are minimal. A privacy policy is still required by the stores.
  - Own backend (Supabase/PowerSync/Firebase): the developer is controller of sensitive data and needs explicit, granular opt-in consent for sync, a privacy policy, an encarregado (Art. 41; see the ANPD small-agent gap below), an incident response plan, deletion/portability endpoints, and a DPA with processors. US-hosted processors trigger Art. 33.
- Keep "sync" strictly opt-in and preserve a fully functional no-account mode. That honors the "no account" brand promise and makes consent meaningful.

### Gaps
- ANPD Resolução CD/ANPD nº 2/2022 (small-scale processing agents, possible encarregado exemption, and whether processing sensitive data counts as "alto risco" and removes that exemption) and Resolução CD/ANPD nº 19/2024 (international transfer standard contractual clauses) are known from prior knowledge but were **not verified this session**. Check them at gov.br/anpd.
- Google Play Data safety's "Health and fitness" category definitions were not fetched.
- There is no authoritative classification of strength-training logs (as opposed to heart rate or medical data) as health data. This is a legal interpretation, and the conservative reading treats them as sensitive.

## 7. What comparable fitness apps do (Strong, Hevy, Liftosaur, Boostcamp)

### Takeaway
The market leaders are cloud-account apps with offline logging. Hevy is account-first. Strong offers "Cloud Sync" backup via account. StrongLifts backs up only while signed in. Liftosaur runs a full AWS backend (Lambda/DynamoDB/S3). Little primary documentation is public, and no surveyed competitor offers account-free cross-device sync, which is a potential differentiator for a privacy-first app.

### Cited Findings
- Strong's App Store listing: "automatically back up your data with Cloud Sync" — [App Store: Strong](https://apps.apple.com/za/app/id464254577)
- StrongLifts: "Workouts logged while signed in are backed up to our servers. Workouts logged while signed out are not backed up... They're saved locally on your device". Signing in on a new device restores workouts and purchases, and data in Google Fit/Apple Health isn't pulled back — [StrongLifts support: Backup (Android)](https://support.stronglifts.com/article/27-backup); [Recover workouts](https://support.stronglifts.com/article/141-recover-workouts)
- Hevy and Strong both allow offline logging and sync on reconnect. Backup/export options vary by tier. This is a competitor's blog, so it is low reliability — [Setgraph: Hevy vs Strong 2026](https://setgraph.app/articles/hevy-vs-strong-app-comparison-2026)
- Liftosaur is open source. Its backend runs on AWS (Lambdas, DynamoDB, S3, deployed via CDK) and is tied to the liftosaur.com domain. Local runs give an offline-like experience — [GitHub: astashov/liftosaur](https://github.com/astashov/liftosaur)

### Inferences
- The industry pattern is "local DB + optional/required account → vendor cloud". Hevy also uses its account for social features.
- A privacy-first alternative pattern: no account by default; manual export/import plus an OS backup; an optional "backup to your Google Drive / iCloud" option; and later, an optional account with E2EE sync if multi-device or sharing demand appears.
- Sharing routines doesn't need sync. A routine can be exported as a file or deep link (JSON payload or a short-lived link) without accounts. Only public/social sharing really needs a backend.

### Gaps
- No primary docs found for Hevy's account/sync architecture, Boostcamp's sync model, or whether Strong requires an account for Cloud Sync. These rest on store listings and third-party claims only.

---

### Summary recommendation matrix (inference, for the report writer)

| Need | Minimal option | Backend needed? |
|---|---|---|
| Backup/restore same platform | Android Auto Backup (≤25 MB, nightly) + iCloud device backup | No |
| Android↔iOS migration | Versioned JSON/SQLite export via share sheet; later Android 16 QPR2 cross-platform-transfer + AppMigrationKit | No |
| Cross-platform cloud backup | Google Drive appDataFolder via REST on both OSes (user's own account) | No (user's storage) |
| Multi-device live sync | PowerSync (+ Supabase Postgres, or self-hosted Open Edition) with LWW + tombstones | Yes |
| Accounts / social sharing of routines | Supabase Auth + Postgres; routine sharing via file/deep link first | Yes (only for public/social) |
| Avoid | Realm/Atlas Device Sync (EOL 2025-09-30); ElectricSQL for native apps (no native client, read-only sync); Turso Sync until conflict resolution is GA | — |
