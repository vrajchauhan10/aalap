# Technical changelog

Significant technical changes only (user-facing notes stay in `/CHANGELOG.md`). Newest first.
Template: date · change · files/modules · decisions · tests run (actual result) ·
performance implications · known limitations.

## 2026-11-01 (2) — One source of truth for progress; crossfade, lossless and interruption fixes
- **Root causes (device: S24 FE, Android 17; settings: Lossless on Wi-Fi + cellular,
  crossfade 1 s, Fast loading on):**
  - The screen read the player's position whatever song it held, so during a skip the new
    title briefly showed the old song's time; the restore point could be saved against the
    wrong song; pauses from the notification/headset/car didn't save it.
  - Media items took title/artist/cover from mutable "now playing" fields; the crossfade set
    them early and tagged the incoming player with the outgoing song's id.
  - Lossless quick start tried YouTube only: a song YouTube rejected waited for the whole
    lossless lookup (15 s measured) and was skipped if that missed, though JioSaavn/Gaana had it.
  - Crossfade looked up the next song only in its last second; an end during that lookup was
    ignored and, if the lookup failed, playback stopped.
  - Mid-blend interruptions (focus loss, becoming noisy, transient suppression) paused only
    the primary player; the incoming one kept playing and the UI kept "playing".
  - At the end of a crossfade the old player was stopped before the session moved to the new
    one: the session showed STOPPED for ~0.5 s (lock screen, notification, AVRCP).
- **Changes:** new `playback/PlaybackSync.kt` (pure: shown-song progress, resume point,
  save guard) + `SongPlayer.progress()` used by `PlayerScreen` (one state, 500 ms tick, once
  while paused), `MiniPlayer`, `LyricsScreen`; `saveLastPosition(ctx, playingQuery, pos)` and
  `SongPlayer.savePosition()` from `onPlayWhenReadyChanged(false)` + the periodic tick;
  `buildMediaItem` metadata from the queue entry of its query; crossfade item tagged with
  the next song; `endedWhileCrossfading`; `preResolveForBlend` (URL only, ~30 s ahead);
  `cancelCrossfade` on non-user pause or suppression; session rebound before the old player
  stops; quick start via `resolveStreamUrl(allowLossless = false)`; Fast-loading LoadControl
  `(30 s, 600 s, 0.5 s, 2.5 s)` with size over time (≈13 MB cap) instead of time over size.
- **Tests:** `:app:testDebugUnitTest` 96/96 (incl. `PlaybackSyncTest`, 11). Release build
  installed over the previous one on the S24 FE; checked with `dumpsys media_session`, UI dumps
  (uiautomator) and `dumpsys audio`:
  - restore shows the resume point before playing (1:21 / 4:00, was 0:00 / 0:00); system
    Play key resumes at 81.3 s in ~1.3 s;
  - 3 skips: new song shows 0:00–0:01 at once, title = session title, 0.7–1.2 s tap → playing
    (was 1.2–1.8 s);
  - crossfade hand-overs (Uff Teri Adaa → Tum Mile, Oh Girl → Bebo): UI 0:02 at the switch;
    after the rebind fix no STOPPED between the two PLAYING states (was ~0.5 s STOPPED);
  - Google Assistant interruption: audio paused, resumed by itself after dismissal;
  - AAC and FLAC streams both played (media.metrics).
- **Measured, no improvement:** PSS during playback 272–312 MB after vs 257–283 MB before
  (Java heap 40–76 vs 44–70 MB); the buffer cap is kept as a bound, not claimed as a saving.
- **Not verified:** Bluetooth / wired output (none connected), emulators (none installed),
  an interruption landing inside a blend, Gaana HLS, local files, background offload with AAC.
- **Remaining:** manual skips still show STOPPED / no metadata while the next stream is found
  (the player is emptied); the title switches when a crossfade ends (up to the crossfade
  length); YouTube's "weak match" rejection of exact matches outside the lossless quick start;
  Spotify 429s.

## 2026-11-01 — Playback state fixes (frozen progress, silent Play, resumption, late picks)
- **Found on device** (Galaxy S24 FE, Android 17, 7.0.0 release): after an automatic song
  change the player showed 0:00/0:00 while audio played, then jumped to the real position
  on pause/play ("starts at 30–40 s"); after leaving the app while paused, Play showed
  "playing" + the equalizer animation but stayed silent (also via the notification intent).
- **Changes:**
  - `ui/screens/PlayerScreen.kt`, `ui/components/AppComponents.kt` (MiniPlayer): progress
    `LaunchedEffect` also keyed on `songId` (its `remember(songId)` targets were replaced
    per song while the old loop kept writing the old ones).
  - `di/SongPlayer.play()`: with no media item (fresh launch, or player released by
    `MainActivity.onDestroy`) it replays the current queue song, resuming from the saved
    position when it matches; no-op while a song is being found.
  - `PlaybackService.LibraryCallback.onPlaybackResumption`: restores the last session and
    starts it via SongPlayer; returns a failed future so Media3 doesn't load URI-less items.
  - `HomeViewModel.refresh`: quick picks finishing after the 6 s spinner limit are applied;
    `onResumeRefresh` retries empty daily picks.
- **Decisions:** smallest fixes; UI still sets "playing" optimistically, made safe because
  `play()` now always starts something when a current song exists.
- **Tests:** `:app:assembleRelease` + `:app:testDebugUnitTest` → 85/85 passed. Installed over
  7.0.0 on the S24 FE (same debug-key signature, data kept) and checked against
  `dumpsys media_session`: system Play key on a cold start now resumes (was a no-op; reaches
  `onPlaybackResumption`); progress moves after an automatic song change (0:22 → 0:29);
  exit-while-paused → reopen → Play gives audio in ~1.7 s; notification intent → Play
  gives audio in ~1.6 s. Late-picks change not exercised on device.
- **Performance:** no new work in steady state; the progress loop restarts once per song.
- **Known limitations:** resuming a cold song can take ~15 s: YouTube rejected "Ghafoor" as a
  "weak match" despite title/artist/duration/album all 1.00 (`isAcceptableMatch` gates,
  ~9 s lost) after JioSaavn missed its 2.5 s window; Spotify 429s at launch not handled yet; background/offload,
  crossfade and lossless-swap behaviour not yet tested on device; ColorOS launch failure
  still needs a crash log.

## 2026-10-31 — Project onboarding docs (no code change)
- **Change:** Added `CLAUDE.md`, `docs/PROJECT_MAP.md`, `docs/ARCHITECTURE.md`,
  `docs/PERFORMANCE_BASELINE.md`, this file. Application code untouched.
- **Baseline:** app at 7.0.0 (build 68), commit `80be873`.
- **Tests run:** `ANDROID_HOME=~/Library/Android/sdk ./gradlew :app:testDebugUnitTest
  :innertube:testDebugUnitTest :spotify:test` → BUILD SUCCESSFUL. `:innertube` 6/6 and
  `:spotify` 2/2 passed in this run; `:app` was UP-TO-DATE (Gradle reused the
  2026-10-27 results for unchanged inputs: 85 tests, 0 failures). Without `ANDROID_HOME`
  or `local.properties` the build fails with "SDK location not found".
- **Performance:** none (docs only). Baseline recorded in `PERFORMANCE_BASELINE.md`;
  no new measurements taken.
- **Known gaps:** README says SDK 34 while `compileSdk` is 36; no instrumented tests;
  untracked `dumpstate-2026-10-22-00-09-19.zip` (35 MB) in the repo root.

## Prior history (summary from `CHANGELOG.md`, not re-verified)
- **7.0.0 (2026-10-27):** foreground service kept between songs (`onUpdateNotification`,
  `isBetweenSongs`), ExoPlayer wake mode + transition wake lock, wait-for-network instead
  of skipping, play on after Recents swipe; YouTube decoding only when JioSaavn misses; no
  start-up YouTube warm-up.
- **6.6.0:** R8 optimise (no rename/shrink), debug logs stripped, baseline profile,
  ExoPlayer 1.5.1 dynamic scheduling, AAC from YouTube (offload-friendly), mini player 1 Hz.
- **6.5.0:** Your Mix infinite loop fixed, lifecycle-gated tickers, background audio
  offload, hidden Spotify WebView only when enabled, source order JioSaavn → YouTube → Gaana.
