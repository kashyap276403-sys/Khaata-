# auraX Guard

Free, offline anti-theft app for Android. No ads, no account, no internet.

## What it does (v0.2)
1. **Intruder photo** - wrong lock-screen PIN/password/pattern: front camera
   photo, stamped with date/time and reason, saved to the gallery
   (Pictures/auraXGuard). View the latest photo from the app.
2. **Snatch lock** - a hard sustained jerk locks the screen (a single bump is
   ignored). Sensitivity: Zyada / Madhyam / Kam.
3. **Siren** - loud alarm (even on silent) on snatch, charger pull-out, or 3
   wrong attempts. Stops when the owner unlocks, or after 25 seconds.
4. **Charger pull-out alarm** (optional).
5. **Pause** detection for 1 hour when at home.
6. **Always-on guard** with a setup checklist and the last 25 events.

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
