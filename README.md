# auraX Guard

Free, offline anti-theft app for Android. No ads, no account, no internet.

## What it does (v0.1)
1. **Intruder photo** - someone enters a wrong lock-screen PIN/password/pattern:
   the front camera takes a photo and saves it to the gallery
   (Pictures/auraXGuard).
2. **Snatch lock** - if the phone is jerked away hard, the screen locks at once.
   Sensitivity can be changed.

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
