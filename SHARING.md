# Completed workout images

Open a finished-workout summary or a completed workout in History, then choose **Share workout**. The app builds a fresh PNG from that saved workout and shows it in a preview. Choose WhatsApp, WhatsApp Business when available, or another Android sharing app. Recipient selection and sending happen in that destination; Mooscles never selects a recipient or sends automatically.

The card includes the workout's chronological number among saved completed sessions, name, localized date/time, every exercise with valid completed sets, completed-set counts, the best eligible actual weight/repetition pair, independent PR badges, and the existing summary's duration, logged external-load volume and distinct exercise PR count. First records contribute to the same count as the app's summary. Names stay intact and wrap; no failure flags are fabricated.

Positive working loads use weight-first selection and then repetitions at that load. Blank/zero external load is displayed as **Bodyweight × reps**, without adding invented body mass to volume. Warm-ups and assistance-only rows remain visible but are labeled honestly when no eligible unassisted working set exists. Duplicate rows only receive a badge when their actual completed sets qualify for the record.

Images are 1080 pixels wide and measured to fit their content. The renderer continues exceptionally long workouts across ordered pages of at most 4096 pixels high, retaining all rows/text and the statistics. The preview decodes visible pages at a sampled size; full-resolution PNGs are shared. Single-page exports use `ACTION_SEND`; multipage exports use `ACTION_SEND_MULTIPLE`, preserving page order.

PNG files live only in app-controlled `cache/workout-shares/export-…/` directories. A private FileProvider exposes only that cache subtree through `content://` URIs with temporary read grants and ClipData for each selected image. No contacts or broad storage permissions are requested. Closing the preview or returning from another app does not delete exports. Old managed export directories are cleaned after seven days; preparing an existing export for sharing refreshes its retention timestamps.

No backend, credentials, repository writes or workout-data mutations are involved. Missing/restricted WhatsApp destinations fall back to the system Sharesheet, and failure to open any sharing app leaves the preview available with an error message. Deleted/unavailable workouts and generation errors offer retry/close.

Host tests exercise card calculations, native Canvas output, long-name and long-workout pagination, real Room-to-PNG export with unchanged snapshots, FileProvider scope/content reading, grants, multipage intents, destination fallback and preview controls. On Windows, a test-only shadow normalizes path separators for AndroidX FileProvider's POSIX boundary predicate; production FileProvider code and its scoped XML remain unchanged.

Native-rendered review samples are generated under `app/build/outputs/share-previews/`. `representative-kg.png` comes from saved sample workout data through the same repository/model/renderer path as the app. Other samples stress long names, lb units, multiple PRs and pagination. These are test fixtures, not a user's private workout history.

No device is connected in this workspace, so actual WhatsApp/Business recipient UI, device Sharesheet behavior and physical launcher/splash display must be checked on an Android device. The exported PNGs and launcher-mask/splash resource previews are visually inspected locally.
