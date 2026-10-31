# auraX Khaata

Free, ad-free, subscription-free, offline-first digital udhaari / jama ledger for shopkeepers (Flutter, Android).

The app only **records** transactions. Cash / UPI / Other are labels; no payment is processed.

## Status (honest)

The code is written without a local Flutter SDK, so every change is first built and tested
by GitHub Actions. "Not yet built" means that phase has not had a green Actions run yet.
Nothing has been tested on a real phone by the developer yet.

| Area | State |
|---|---|
| Theme (emerald/ivory, light+dark), accent colour, font size, glass effect, Simple/Pro mode | Written; settings saved on phone |
| SQLite schema v3 with tested migrations (v1 -> v2 -> v3) | CI green |
| Customers: add, edit, archive/unarchive, search, duplicate suggestions, due date | CI green |
| Ledger: integer paise, idempotency keys, atomic writes, reversal, integrity check | CI green |
| Quick Entry, History (entries + activity), Trash (30-day retention, tombstones) | CI green |
| Reports (period, customer statements), CSV and PDF export | CI green |
| In-app reminders (due/overdue, snooze, dismiss) | CI green; no push notifications yet |
| Data: full JSON export, full ledger CSV, erase all data, ledger check, privacy page | Written, see CI status of latest commit |
| Voice entry | Pending |
| Language switch (Hindi/English), push notifications | Pending |
| Signed release APK + GitHub Releases | Pending |
| Google Sign-In, Drive encrypted backup (planned last) | Pending |

## Balance rule (single source of truth)

`balance = SUM(udhaari) - SUM(jama)` over non-deleted rows of the `transactions` table.
Positive = customer owes the shop. Nothing else stores a balance. Corrections are new
reversal entries; history is never edited.

## Build (GitHub Actions)

Actions tab > **Build debug APK** > Run workflow. The first run generates the `android/`
folder (applicationId `com.aurax.khaata`). Commit that folder afterwards so the
applicationId stays stable. The debug APK is attached as a workflow artifact.

## Known limits

- PDF export uses the built-in font: Hindi/non-Latin characters show as `?` and money is
  written as `Rs`. CSV keeps full Unicode. A bundled Unicode font is planned.
- Udhaari entries carry no Cash/UPI label (no money moves); the label applies to Jama only.

## Rules

- Money is stored as integer paise, never float/double.
- Never commit keystores or passwords (see `.gitignore`).

## Voice entry (Bolkar likhein) - v0.6.0

Quick Entry -> "Bolkar likhein". Hindi/English sentence (spoken or typed) ->
preview -> aap confirm karein -> tabhi save. Kuch bhi apne aap save nahi hota.

- Parser pure Dart hai aur tests me check hota hai (test/voice_test.dart).
- Awaaz Android ki speech service samajhti hai (aam taur par Google, internet
  use kar sakti hai). App awaaz save nahi karta. Mic ke bina type kar sakte hain.
- Status: CI tests ke liye likhe gaye hain; asli phone par mic abhi test nahi hua.
