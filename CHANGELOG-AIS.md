# AIS Terminal – Changelog

Version control: bump `aisVersionCode` and `aisVersionName` in `app/build.gradle.kts`
for every release, add an entry here, and tag the commit `vX.Y.Z`.

## 1.0.0 (build 1) – 2026-10-07
Developed by DT. Forked from ConnectBot 1.11.0 (Apache 2.0).

- New app identity: package `com.aisglass.terminal`, name "AIS Terminal". It installs
  side by side with ConnectBot.
- **Barcode scanner input**
  - Keyboard-wedge scanners type directly into the console.
  - Broadcast/intent receiver with presets for Zebra DataWedge, Honeywell, Urovo, Newland, iData, Sunmi and Chainway.
  - Camera scanning (ZXing) from a console toolbar button.
  - Configurable prefix and suffix (Enter / Tab / CR+LF / none), trim, vibrate, and a live test panel.
- **Macros**: one-tap sequences with tokens ({ENTER} {TAB} {F1}…{F12} {CTRL+C} {DELAY:ms} …), an optional per-host filter, reordering, and JSON import/export.
  ConnectBot's per-host *Automation* (wait-for-text / send) remains available for auto-login.
- **Admin lock / kiosk**: PIN-protected admin mode (PBKDF2-hashed, auto-relock, lockout after 5 wrong tries).
  Operators can only connect to saved hosts, run macros and scan. Optional kiosk screen pinning and keep-screen-on.
- **Session logging & export**: plain-text transcripts with escape codes stripped and optional timestamps.
  Optional CSV log of keys sent. Retention days, an in-app viewer, per-log share, and ZIP export with `index.csv`.
- GitHub Actions workflow builds the APK on every push.
