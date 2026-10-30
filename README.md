# auraX Khaata

Free, ad-free, subscription-free, offline-first digital udhaari / jama ledger for shopkeepers (Flutter, Android).

The app only **records** transactions. Cash / UPI / Other are labels; no payment is processed.

## Status (honest)

The code is written without a local Flutter SDK, so every change is first built and tested
by GitHub Actions. "Not yet built" means that phase has not had a green Actions run yet.
Nothing has been tested on a real phone by the developer yet.

| Area | State |
|---|---|
| Theme (emerald/ivory, light+dark), 5-tab navigation, Home dashboard | Written; CI build was green for the earlier phases |
| SQLite schema v2 (customers, transactions, audit log, tombstones) with v1 -> v2 migration | Written, migration test written |
| Customers: add, edit, archive, search, duplicate suggestions | Written; CI green for phase 3 |
| Ledger: integer paise, idempotency keys, atomic writes, reversal, integrity check | Written; CI green for phase 3 |
| Quick Entry tab (type, customer search, recent customers, repeat last) | Written, not yet built |
| History screen (entries with date filter/search/sort, activity log) | Written, not yet built |
| Trash screen (30-day retention, restore, permanent delete with tombstones) | Written, not yet built |
| Voice entry | Pending |
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
