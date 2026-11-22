# auraX Guard

Free, offline anti-theft app for Android. No ads, no account. Internet is used only if you turn on optional Telegram alerts.

## What it does (v0.4)
1. **Intruder photo** - wrong lock-screen PIN/password/pattern: front camera
   photo, stamped with date/time and reason, saved to the gallery
   (Pictures/auraXGuard). View the latest photo from the app.
2. **Snatch lock** - a hard sustained jerk locks the screen (a single bump is
   ignored). Sensitivity: Zyada / Madhyam / Kam.
3. **Siren** - loud alarm (even on silent) on snatch, charger pull-out, or 3
   wrong attempts. Stops when the owner unlocks, or after 25 seconds.
4. **Charger pull-out alarm** (optional).
5. **Pause** detection for 1 hour when at home.
6. **Stop the siren** - big red button in the app, a notification button that
   needs the phone unlocked, and automatic stop when the owner unlocks.
7. **Haath mat lagao mode** - phone on a table: any touch sets off the siren.
8. **Privacy** - app PIN (salted hash only), hide from screenshots/recents,
   keep intruder photos inside the app instead of the gallery, in-app photo
   viewer with delete, wipe-all button. Internet permission is used only for the optional Telegram alerts (BOT_TOKEN and CHAT_ID are typed in the app and stored encrypted on the phone, never in this repo).
9. **More privacy (v0.4)** - photos kept inside the app are AES-256 encrypted
   (Android Keystore); optional auto-delete after 7/30 days; app unlock time
   (1/5/15 min); fingerprint unlock; a photo is taken after 3 wrong App PINs;
   guard notification hidden on the lock screen.
10. **Siren torch + vibration**, **Quick Settings tile** for Haath mat lagao
    mode, and **share a photo** (only when you tap it, to the app you choose).
11. **Always-on guard** with a setup checklist and the last 25 events.

## How it works
- Uses Android *Device Admin* (`watch-login`, `force-lock`). Needed to be told
  about wrong attempts and to lock the screen. While admin is on, the app can't
  be uninstalled without switching admin off first.
- Photos stay on the phone. Nothing is uploaded.

## Honest limits (not yet tested on a real phone)
- Some phone makers or Android versions block camera use in the background,
  so the intruder photo may not work on every phone. The main screen shows the
  last event/error so a failure is visible - please report it.
- Phones with fingerprint/face unlock: only a wrong PIN/password/pattern counts
  as a wrong attempt on some models.
- Falling or running can trigger the snatch lock; use sensitivity "Kam".
- Battery-saver may stop the snatch service: set battery to "Unrestricted".

## Build
GitHub Actions builds a debug APK on every push (Actions -> latest run -> artifact).
