# AIS Terminal

SSH / Telnet / Mosh / local-shell terminal for Android, built on ConnectBot with
extra features for AIS Glass operations. **Developed by DT.**

All additions are listed under **⋮ → AIS Tools** on the host list.

| Feature | Where |
|---|---|
| Macros | AIS Tools → Macros. In the console, use the ⚡ button |
| Barcode scanner | AIS Tools → Barcode scanner. In the console, use the camera button |
| Session logs | AIS Tools → Session logs (turn on "Record sessions") |
| Admin PIN / kiosk | AIS Tools → Admin lock & kiosk |
| Auto-login | Edit host → Automation (built into ConnectBot) |

## Building
Every push to GitHub runs `.github/workflows/build-apk.yml`. Download the APK from
**Actions → latest run → Artifacts → ais-terminal-debug-apk**.

Local build: `./gradlew :app:assembleOssDebug` (JDK 17 and the Android SDK are required).

## Code layout
New code lives in `app/src/main/java/org/connectbot/ais/`. Upstream files carry only small hooks:
`TerminalBridge.kt` (logging), `ConsoleScreen.kt` (buttons, scanner),
`HostListScreen.kt` and `NavGraph.kt` (admin gate, AIS Tools), `MainActivity.kt` (kiosk)
and `AndroidManifest.xml`. These small hooks keep upstream ConnectBot updates easy to merge.

## License
Apache License 2.0, the same as ConnectBot (see `LICENSE`). Original copyright notices are retained.
