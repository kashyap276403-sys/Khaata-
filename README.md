# auraX Khaata

Free, ad-free, subscription-free, offline-first digital udhaari / jama ledger for shopkeepers (Flutter, Android).

The app only **records** transactions. Cash / UPI / Other are labels; no payment is processed.

## Status (honest)

| Area | State |
|---|---|
| Flutter project, theme (emerald/ivory, light+dark), 5-tab navigation | Implemented, **not yet built or tested** |
| Home dashboard UI (zero placeholders) | Implemented, not yet tested |
| Money formatting helper (integer paise) | Implemented, unit tests written, not yet run |
| SQLite ledger, customers, Quick Entry, voice, history, Trash | Pending |
| Reports, CSV/PDF, reminders | Pending |
| Google Sign-In, Drive encrypted backup | Pending |
| Signed release APK + GitHub Releases | Pending |

## Build (GitHub Actions)

Actions tab > **Build debug APK** > Run workflow. The first run generates the `android/`
folder with `flutter create` (applicationId `com.aurax.khaata`). Commit that folder afterwards
so the applicationId stays stable. The debug APK is attached as a workflow artifact.

## Rules

- Money is stored as integer paise, never float/double.
- Never commit keystores or passwords (see `.gitignore`).
