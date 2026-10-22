# GitHub updates

Mooscles discovers public releases from `ReiTut/Mooscles` using the official GitHub Releases API. Updates use Android `versionCode`; names and tags are display information. Drafts are always excluded. Stable releases are the default channel, including the official v1.1.1 release. Version 1.1.0 already checks this channel automatically by default; **Include prereleases** is only needed for releases explicitly marked as prereleases.

Automatic updates and Wi-Fi-only downloads are enabled initially. The app checks on foreground entry with throttling and uses unique network-constrained background work about every six hours. Android battery, network, and scheduling conditions may delay a background check. GitHub API limits and temporary failures also delay checks. The Settings section provides manual checks, progress, cancellation, retries, and installation approval.

Version **1.3.0 / build 8** is an official stable-channel release using the existing debug APK/signing identity. Updater-enabled versions 1.1.0, 1.1.1 and 1.2.0 discover it without enabling prereleases. The earlier v1.2.0 entry remains a prerelease; no previous release or tag is replaced. The owner explicitly confirmed that future releases should be official, and the publication helper now follows that default independently of build type.

Downloads contain only APK release assets. No workout data is uploaded. Offline routines, workouts, history, PRs, and the rest timer continue to work. Installation waits while a workout or rest timer is active. Android may require **Allow from this source** and system installation approval; the app cannot promise silent installation on every Android version or device.

A phone running a version without this updater must install the first updater-enabled APK once. Future discovery only works for releases that contain both the APK and the generated `update.json`. An older release without metadata is skipped.

## Build and prepare the required assets

Keep `applicationId = "com.mooscles"`. Increase `versionCode` for each distributed update, even if its version name or tag does not change. Preserve the existing signing key. Do not uninstall the app, clear app storage, use destructive Room migrations, or generate another key to work around an update failure. A different signing key cannot update existing installations unless Android accepts a deliberately configured compatible signing lineage.

The existing project uses Android's debug signing configuration. Retain the existing local debug keystore securely; a fresh debug keystore from another machine will not match installed copies. The release preparation script checks the existing public certificate fingerprint:

```text
0cd093b2696ea51b9a948e85311478cd6fad200de8aef775fbedfc301e22bb35
```

This fingerprint is public verification information. Keystores, passwords, tokens, and local SDK paths must remain outside version control. Moving future distributions to production signing requires a signing compatibility decision first.

Run the project's build and checks:

```powershell
.\build.ps1
```

The build prepares release assets after a successful debug APK build. To prepare another already-built APK explicitly:

```powershell
.\tools\prepare-release.ps1 `
    -ApkPath .\app\build\outputs\apk\debug\app-debug.apk `
    -OutputDirectory .\app\build\outputs\release `
    -PreviousVersionCode 4
```

Use the last distributed version code for `PreviousVersionCode`. The script reads the application ID, version code, version name, minimum SDK, and debug status from the actual APK with Android SDK Build Tools. It verifies the APK signature and trusted certificate, copies the APK under a versioned filename, calculates its size and SHA-256, and writes UTF-8 JSON without a BOM. Generated assets stay in ignored build directories.

For the first updater build, the required pair is:

- `app/build/outputs/release/Mooscles-v1.1.0-debug.apk`
- `app/build/outputs/release/update.json`

The adjacent `.sha256` file is optional for manual verification. Upload the generated pair together, without renaming the APK after generation. Always upload the generated `update.json`, whose values come from the verified APK:

| Key | APK value |
| --- | --- |
| `applicationId` | Package identity (`com.mooscles`) |
| `versionCode` | Integer Android version code |
| `versionName` | Displayed Android version name |
| `minSdk` | Minimum supported Android API level |
| `apkFileName` | Exact uploaded APK asset filename |
| `apkSize` | File size in bytes |
| `sha256` | SHA-256 of the complete APK, as lowercase hexadecimal |

The updater checks package identity, supported SDK, version code, file size, SHA-256, and APK signing compatibility before installation.

## Publish a future release

Source commits do not include APKs or credentials. Preserve remote history and never force-push a published tag. The guarded publication helper uses your existing GitHub CLI authentication, requires clean committed source and an existing local/remote tag pointing to the exact current commit, reruns the project's tests/lint/build, and generates metadata from that APK. It reads successful test and lint reports before uploading anything. It never creates commits, changes remotes, or pushes source or tags.

Commit and push the intended source, then create and push a new version tag pointing to that commit. Put reviewed release notes in an ignored file such as `.tooling/release-notes-v1.3.0.md`. For version 1.3.0, following distributed build 7:

```powershell
git tag v1.3.0 HEAD
git push origin v1.3.0
.\tools\publish-release.ps1 -Tag v1.3.0 `
    -PreviousVersionCode 7 -NotesFile .\.tooling\release-notes-v1.3.0.md
```

By default this creates a **draft official release** with the versioned debug APK, `update.json`, and `.sha256` attached. It checks asset names, sizes, the selected channel and available GitHub digests, then downloads all three assets to an ignored verification directory and compares their SHA-256 hashes. A failure leaves the draft unpublished. The tag must match the APK's version name exactly and the APK's version code must exceed `PreviousVersionCode`.

After reviewing the draft, explicitly publish it by running the same helper with `-Publish`:

```powershell
.\tools\publish-release.ps1 -Tag v1.3.0 `
    -PreviousVersionCode 7 -NotesFile .\.tooling\release-notes-v1.3.0.md -Publish
```

It can resume an existing draft only when its notes, channel and verified assets match the tested local build. It does not replace an existing draft's assets or notes, and refuses to overwrite a published release. The explicit `-Publish` option verifies assets first, publishes the official release, marks it Latest, checks the published asset list, and returns repository/release/download links. The optional `-Prerelease` switch is reserved for an explicitly requested channel override and must be supplied consistently when resuming that draft. Build type remains checked independently: this workflow distributes the existing debug APK with the trusted signing identity. Android may still require source permission or installation approval.

Replace the tag, last distributed version code, and notes filename for each later version. The local preparation script never tags, pushes, publishes, changes signing configuration, or creates keys. Adding the updater and these helper scripts does not modify existing GitHub releases or publish a new release by itself.

Keep release assets public. The app includes no shared GitHub token. If source code becomes private, keep public update assets in a public release-only repository and explicitly configure the updater to use that repository before distributing it. Private assets need a separate authentication/distribution design; embedding an owner's token in the APK is not supported.

The upstream API documentation is [GitHub REST releases](https://docs.github.com/en/rest/releases/releases).
