# AIS Terminal – Changelog

Version control: bump `aisVersionCode` and `aisVersionName` in `app/build.gradle.kts`
for every release, add an entry here, and tag the commit `vX.Y.Z`.

## 1.2.1 (build 4) – 2026-10-08
- Supports the AIS FG label QR format `@ITEM@SERIAL@QTY@LOT@SUBINV@MFG_DATE@BATCH`.
  It adds an `@` split option, and these are now the default field names for every menu.
  The empty leading field is named `_` and hidden.

## 1.2.0 (build 3) – 2026-10-07
- App renamed **AIS_Terminal**.
- **Transaction menus**: FGWH Receiving, Move to PDI, Move to Packing, Lotout, Org Transfer and Exit.
  - The menus appear on the home screen and on the console's ⊞ button.
  - After a menu is chosen, every QR scan is split into named fields (e.g. KEY,ITEM,LOT,QTY)
    and the master row is looked up by the key field. The menu template then types the values
    into the terminal fields: `{QR.ITEM}`, `{M.SUBINVENTORY|FGWH}`, `{TAB}`, `{ENTER}`…
  - Each menu has its own host, master table, start keys (to reach the EBS screen), exit keys,
    and auto-send or confirm-before-send. It can also block scans not found in the master.
  - A scan panel in the console shows the defaulted values, status and a sent counter.
  - Setup can be exported and imported as JSON, to copy it to other devices.
- **Master data**: import .xlsx or .csv files (row 1 = headers), choose the key column, and test a lookup.

## 1.1.0 (build 2) – 2026-10-07
- **New look**: AIS brand colours that follow the phone's light/dark setting, rounded shapes and bolder type.
  - Home screen: gradient header with live/host counts and role, host search, quick-action chips,
    host cards with protocol badges and status pills, a "New host" button and bottom navigation (Home / Macros / Logs / Tools).
  - AIS Tools: a tile dashboard. Settings pages are grouped into cards. The console top bar shows a live status dot.
- **QR / barcode default data**:
  - Scan templates: `{SCAN}`, split fields `{S1}` `{S2}`…, and defaults for missing fields `{S3|1}`.
  - Also `{DATE}` / `{TIME}`, plus every macro key (`{TAB}` `{ENTER}` `{F2}` `{DELAY:300}`).
  - Splits on `|` `,` `;` Tab, GS (GS1) or space.
  - Per-host templates override the default.
  - The live preview shows the exact keys that will be sent.
  - 1.0 prefix/suffix settings migrate automatically.

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
