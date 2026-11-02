# Compatibility: what is verified and what isn't

Declared support: minSdk 26 (Android 8.0) to targetSdk 34 (compileSdk 36), phones, portrait.
Only what's listed under "Verified" has been run. Everything else is unverified, not claimed.

## Verified (2026-11-01)
| Device | Android | What was run |
|---|---|---|
| Samsung Galaxy S24 FE (SM-S721B), One UI | 17 (API 37), build CP2A.260605.016 | Cold start (~0.95–0.99 s, no crash), updates 7.3→7.4→7.5 keeping data, fresh install of the debug variant (notification permission denied, onboarding without any sign-in, Home, YouTube Music search, playback), playback/skip/crossfade/lock-screen/screen-off controls, output picker and Android's switcher, background-restriction row |
| Same phone, older build (bugreport 2026-10-22) | 16 | 3 ANRs in the liked-songs merge on the main thread (old build; that code now runs off the main thread) |

No emulator is installed on the build Mac and no other device was connected: Android 8–16,
Pixel, OnePlus, Xiaomi, OPPO/ColorOS, Vivo, Realme, tablets and low-RAM phones are untested.

## ColorOS / Android 17 start-up report
Not reproduced: no ColorOS device or crash log was available, and the same Android version on
Samsung starts fine. Since 7.4.0 a crash leaves a report (phone, Android/ROM build, app version,
stack trace) under Profile → Playback → "Aalap closed unexpectedly", shareable from the phone.
That report (or `adb logcat -b crash`) is what's needed to find the cause.

## Static checks (Android Lint, focused on compatibility)
- Fixed: two calls newer than minSdk (`PackageInfo.signingInfo` API 28 in `FirestoreRest`,
  ICU `Transliterator` API 29 in `SongPlayer`). Both were inside `runCatching`, so they failed
  quietly on Android 8–9 rather than crashing.
- `@RequiresApi(S)` on `MainActivity.onCreate`, `App`, `MyNavHost` and Search: lint with them
  removed finds no Android 12-only calls; they only hide warnings (left as they are).
- Full lint can't run: the Compose lint checks crash on Kotlin 2.2 metadata (tool mismatch).

## Known limitations
- Portrait-locked: on tablets/foldables the app is letterboxed (targetSdk 34).
- Background playback relies on the media foreground service; makers that restrict background
  apps (ColorOS, Xiaomi, Samsung "sleeping apps") may still stop it. The app shows a settings
  shortcut when Android reports Aalap as battery-optimised; it can't change that itself.
- Play from a headset/car after Android ended the process needs Android to still list Aalap
  as its media-button app; true process death couldn't be reproduced without root.
- Output picker lists only connected outputs (no Bluetooth permission is asked); a
  notification button that opens Aalap's picker isn't possible on Android 12+ (notification
  trampoline rules). Android 11+ media notifications include the system output switcher.
- Without Spotify: search returns songs only (no albums/artists/podcasts); mood stations,
  Spotify home rows, Canvas and Spotify lyrics are unavailable.

## OPPO CPH2797, ColorOS 17 (Android 17, API 37) — tested 2026-11-02
- **7.0.0 installed:** cold start 1.0 s, no crash (the earlier "doesn't start" report did not
  reproduce). Tapping play on the restored song did nothing for 60 s: the 7.0.0 bug where
  play() with an empty player did nothing (fixed in 7.1.0, `SongPlayer.play()` reloads). The
  YouTube lookup for it also failed ("Missing stream expire time": every client answered OK
  without streaming data); JioSaavn has the song and its link answers 200.
- **7.6.0 / 7.6.1 over it** (`adb install -r`, data kept): the same songs start in ~4 s and play
  continuously; AudioFlinger shows 0 underruns on Aalap's track. Music volume on the phone's
  speaker was 10/160, so it sounds very quiet; that's the phone's setting.
- **Mini player:** at 360 dp width (1080 px, density 480) the fixed 260 dp title area squeezed
  the play button to ~26 dp wide; fixed in 7.6.1 (title area takes the remaining width).
- USB installs on ColorOS need "Disable permission monitoring" on and "Verify apps over USB"
  off in Developer options, or `adb install` fails with INSTALL_FAILED_VERIFICATION_FAILURE.
