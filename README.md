# auraX Khaata

Free, ad-free, subscription-free, offline-first digital udhaari / jama ledger for shopkeepers (Flutter, Android).

The app only **records** transactions. Cash / UPI / Other are labels; no payment is processed.

## Status (honest)

Nothing below has been compiled or run yet: the code was written without a Flutter SDK.
The first GitHub Actions run is the first real build and test run.

| Area | State |
|---|---|
| Theme (emerald/ivory, light+dark), 5-tab navigation, Home dashboard | Written, untested |
| SQLite schema v1 (customers, transactions, audit log) | Written, untested |
| Customers: add, edit, archive, soft delete/restore, search, duplicate suggestions | Written, untested (no Trash screen yet) |
| Ledger: integer paise, idempotency keys, atomic writes, reversal, integrity check | Written, tests written, not yet run |
| Entry sheet (Udhaari/Jama, quick amounts, Cash/UPI/Other label) per customer | Written, untested |
| Global Quick Entry tab, voice entry, History screen, Trash screen | Pending |
| Reports, CSV/PDF, reminders, settings/customization | Pending |
| Google Sign-In, Drive encrypted backup | Pending |
| Signed release APK + GitHub Releases | Pending |

## Balance rule (single source of truth)

`balance = SUM(udhaari) - SUM(jama)` over non-deleted rows of the `transactions` table.
Positive = customer owes the shop. Nothing else stores a balance. Corrections are new
reversal entries; history is never edited.

## Build (GitHub Actions)

Actions tab > **Build debug APK** > Run workflow. The first run generates the `android/`
folder (applicationId `com.aurax.khaata`). Commit that folder afterwards so the
applicationId stays stable. The debug APK is attached as a workflow artifact.

## Rules

- Money is stored as integer paise, never float/double.
- Never commit keystores or passwords (see `.gitignore`).
