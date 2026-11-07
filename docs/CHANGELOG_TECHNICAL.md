# Technical changelog

Significant technical changes only (user-facing notes stay in `/CHANGELOG.md`). Newest first.
Template: date · change · files/modules · decisions · tests run (actual result) ·
performance implications · known limitations.

## 2026-11-07 — 8.3.0 (87) YouTube backup, search rank, playlist share, queue, downloads, motion, security
- Backup: `YouTube.playlistSongs` (VL + public chart playlists) and `YouTube.artistByName`
  (search with artist filter → browse). `Api.youTubeChart` maps the four Spotify chart ids to
  YT Top 100 India/Global; used when the catalog token or the playlist call fails.
  `Api.youTubeArtist` builds an `ArtistOverviewModel` (top songs + credited search hits, 16).
  `youTubeSongSearch` also returns the YT artist. Probed from JVM: charts 100/99 songs.
- `SearchRank` (tested): exact/starting title first, version words (cover, remix, lofi…)
  down unless in the query, stable on Spotify's order (searchDesktop has no playcount).
- `PlaylistShare` (tested): gzip+base64 code `AALAP-PLAYLIST:`; size limits; MainActivity
  ACTION_SEND imports into `AalapPlaylists`.
- Queue screen rewritten: theme colours (was hard-coded #121212), NowPlayingCard, numbered
  rows, time left, Shuffle/Clear, `animateItemPlacement`, lifted drag row.
- `Downloads.batch` + `BatchDownloadBar`; `downloads.wifi_only` (checked against
  `isActiveNetworkMetered`; refuses with a message, no background waiting).
- `CappedMap` (tested) for SongPlayer registries, PlaybackService maps, LyricsApi;
  `CurrentSongState.updateQueue` registers only new songs (was O(n²)).
- `Motion`: `pressSquish`, `kickOn`; tab kick, play/pause AnimatedContent with spin,
  mini player title roll. Interaction-only; nothing runs idle.
- Security: `AppUpdate.verify` also requires `OFFICIAL_SIGNER`; `genuine()` + Home warning;
  docs/SECURITY.md. Version chip always opens the update row.
- Hum/sing search not added: no public recognition API; paid ones need an in-app secret.

## 2026-11-06 — 8.2.0 (86) search artist line, artist share text
- `SearchSongRow` and recent song rows: `shortArtists(max = 1)` + ellipsis (two tags left
  "… & 2" with "more" wrapped out of sight on the S24).
- `ShareMessage.forArtist`: name + YouTube Music search link; the artist ⋯ Share uses it
  instead of an open.spotify.com address (backend names stay hidden).

## 2026-11-05 (2) — 8.1.0 (85) artist page and search artist card
- `Api.topUpTo16`: overview's 10 top tracks + a track search for the artist (credited only,
  title-deduped) to 16. `ArtistScreen` Popular is a `HorizontalPager` of 4-row pages, short
  last page padded to keep height; rows like Quick picks with `shortCount` plays (tested).
- About: `cleanBio` (HtmlCompat) + `splitLinks` (URLs → named buttons), 5 lines + Read more.
  Artist ⋯ = DropdownMenu (Artist radio, Share). Releases "Show all" encodes the name.
- Search: `artistTop` is the matched artist only (no overview fetch); card opens the artist
  page and records it in recents; that artist isn't repeated in the list.
- `ui/components/shortArtists` (tested) on album/playlist/search/liked/downloads/history/
  today/mood/queue/home rows, album header, player and mini player. Album row taps now
  raise `openPlayerRequest` (they called `SongPlayer.playSong` directly).
- `PlayerViewModel.goToArtist` gets a catalog token first (works without Spotify login).
- Verified on the S24 (debug copy, no login): search card → artist, recents, 16 songs over 4
  pages, Fans also like, Appears on → album, ⋯ menu, About buttons.

## 2026-11-05 — 8.0.0 (84) catalog without Spotify login, sign-in fixes, search artist top songs
- `SpotifyTokenProvider.ensureCatalogToken`: the user's token when `sp_dc` is set, else the
  signed-out web-player token (`SpotifyAuth.fetchAnonymousToken`, tracked by `anonymous`).
  Used in `Api` for public reads only (search, playlists/charts, artists, albums, shows,
  candidates, radio); library/likes/follows/account still use `ensureToken`, which never
  accepts the anonymous token. Probed from JVM first: anonymous token loaded "Top 50 -
  Global" and artist search.
- Spotify login: `fixSpotifyLoginLayout` used `WebView.height` (screen px) as CSS px, making
  the page ~2.6 screens tall; now `window.innerHeight` as min-height. Native bottom bar
  toggles `SIGNUP_URL`/`LOGIN_URL` in the same WebView (sign-up sets `sp_dc`). Google OAuth
  (`/o/oauth2`, `/signin/oauth`, `/gsi/`) from the Spotify flow is stopped
  (`setUpForSignIn(onGoogleButton)`) with a dialog explaining the emailed-code login; no UA
  spoofing. Welcome shrinks its header while the IME is visible.
- Search: `SearchViewModel.artistTop` (exact/near-exact artist name match, top 10 from
  `getArtistOverview`), `ArtistTopSongs` card plays via `playAll`.
- Song taps open the player: `PlayerViewModel.openPlayer()` from `playFromList`, `playAll`,
  `playWithRadio`, `playSongAt`, and `SearchViewModel.startRadioFromSong`; MyNavHost ignores
  it on Player and Queue (swipes, queue taps).
- Downloads: `Downloads.unseen` (prefs `downloads_unseen`, read once at start), dot on the
  Home chip and Library tile, cleared by opening Downloads. Player menu polls progress every
  300 ms only while open and downloading; `DownloadBar` in both menus.
- Player menu: dismiss while on Sleep timer / Alternative stream returns to the menu
  (`sheetState.show()`), plus a back arrow.
- Home `UpdateBanner` from `AppUpdate.knownRelease` (cached, no network), re-read when
  `launchOffer` changes.

## 2026-11-03 (6) — 7.14.0 (83) scrolling song menus, Quick picks menu, new-account Library
- `PlayerOptionsSheet` and `SongOptionsSheet` columns get `verticalScroll`; `PlayerMenuRow`
  vertical padding 14 → 11 dp. On the S24 the bottom rows were cut off before.
- `QuickPicksSection` takes `navController` and opens `SongOptionsSheet` from a ⋮ button.
  Verified on the S24 (release build): menu opens with all rows reachable.
- `ArtistPicker.albumsFromFavourites` falls back to `trending(…, 8)` artists when there are
  no plays or follows (empty results still aren't cached). Library All shows the artists
  section (Add artists) even when empty, and trending playlists while there are no own
  entries. Not verified with a real new account. Tests: unit tests pass.

## 2026-11-03 (5) — 7.13.0 (82) update offered on opening; reminder check always fresh
- `UpdatePrompt` (activity root) → `AppUpdate.checkAtLaunch`: once per process, only when
  onboarded; fresh unless asked < 1 h ago; `launchOffer` → shared `UpdateDialog`. After Later
  the same version waits 24 h (`offered`/`offered_at`, `shouldRemind(every=)` tested). Verified
  on the S24 with a 7.11.9 test build (GitHub latest 7.12.0): the dialog showed on opening, and
  after Later + reopen it didn't show; the Home chip read "v7.11.9 · Update". Then 7.13.0 was
  installed over it with `adb install -r` (same versionCode 82).
- `remindIfDue` used `check(app)`, which returns the remembered answer for 6 h. On the S24
  (7.11.0, answer from 13:18 saying 7.11.0 was latest), a forced sync job at 13:29 found
  "up to date" and then set `asked_at`, so 7.12.0, published at 13:28, would only have been
  reminded a day later. Now it uses `check(app, force = true)`; the 24 h `asked_at` gate still
  limits it to one GitHub request a day. Ships with the next release; 7.11.0 and 7.12.0 phones
  get the reminder up to ~30 h after a release instead of ~24 h.

## 2026-11-03 (4) — 7.12.0 (81) sheet bottom gap
- 7.11.0 on the S24 FE: the sheet window was at the top (`frame=[0,0][1080,2205]`) but still
  2205 px tall. Cause, from Compose UI 1.6.8 bytecode: `DialogLayout.internalOnMeasure` measures
  with `screenHeightDp` (screen minus bars), and `internalOnLayout` calls
  `window.setLayout(measuredW, measuredH)` on every layout, so the window can't be made taller
  from outside without fighting it. A Compose BOM upgrade would fix it, but it's too big for
  this. Instead, `SheetCover.shown` (true once the sheet has finished opening, false as soon
  as it starts closing) paints `ImmersiveBackground`, the player's bottom gradient colour,
  over the activity, so the 135 px under the window matches.

## 2026-11-03 (3) — 7.11.0 (80) update reminders
- `AppUpdate.remindIfDue` runs at the end of `LibrarySyncWorker` (existing hourly, network-
  constrained job: no new wake-ups). It asks GitHub at most once per 24 h (`asked_at`), and
  `shouldRemind` (tested) notifies the first time a version is seen, then every 3 days. The
  "updates" channel is IMPORTANCE_DEFAULT. A tap sends `ACTION_OPEN_UPDATE` → `openRequest` →
  MyNavHost opens Settings → `AppUpdateRow` shows the Update now dialog. Starting a download
  cancels the notification.
- Home chip: `knownNewer` reads the remembered answer only (no network) → "v7.x · Update",
  and a tap opens Profile.
- Sheet window (player/queue): Compose's dialog window was 1080x2205 of 2340 px on the S24
  FE (`dumpsys window`), kept clear of the system bars, so 7.10.0 hid the status icons but
  left a dark band above and the page below showing. API 30+: `fitInsetsTypes = 0`, gravity
  TOP; content already pads (`statusBarsIgnoringVisibility`, `navigationBarsPadding`). API <30
  unchanged (not testable here).
- Tests 167/167. Not yet seen on a phone: the notification itself needs a newer published
  release than the installed one.

## 2026-11-03 (2) — 7.10.0 (79) full-screen player, update prompt
- **Player/queue full screen:** `SheetLayer` sets `setDecorFitsSystemWindows(false)` on the sheet
  dialog window and hides `statusBars` while open (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`),
  showing them again as it closes; the activity window is untouched, so other pages keep the
  status bar. Player/Queue pad with `statusBarsIgnoringVisibility`, so the layout doesn't jump
  under the camera when the bar is hidden or swiped in.
- **Update row:** tap = forced check, then a toast ("up to date" / "couldn't check") or an
  `AlertDialog` "Aalap X is available · Update now / Later"; no prompt while downloading.

## 2026-11-03 — 7.9.0 (78) full screen, font fit, in-app updates
- **Grey strip at the top:** `Theme.Aalap` (post-splash theme, parent Theme.Material.Light)
  didn't set `statusBarColor`, so the platform default `#757575` was drawn by the DecorView's
  `statusBarBackground` (measured 117,117,117 over a 16,11,28 page; Samsung Settings showed
  none). Fixed in `values/themes.xml` + new `values-v29` (transparent bars, no contrast
  enforcement), plus `enableEdgeToEdge(SystemBarStyle.auto(TRANSPARENT, TRANSPARENT))`.
  Verified on the S24 FE: status bar pixel = page colour on Home, Search, Library.
- **Font fit:** `FontFit` clamps the phone's font scale to 0.85–1.3 via `LocalDensity` in
  `AalapTheme` (tested). Not verified at extreme scales on a phone (changing the system font
  size is the user's setting). Fixed-width rows → `weight(1f)` (`LikedSongsAlbum`).
- **In-app updates (`AppUpdate`):** Profile reads GitHub `releases/latest` (≤ every 6 h, only
  while Profile is open; 404 = no release = up to date). Only `https://github.com/<repo>/releases/…`
  links and an asset named `Aalap-<version>.apk` are accepted (tested). Tap → download to
  `cacheDir/update` → `verify`: same package, higher versionCode, identical signing-cert
  SHA-256 set → FileProvider → Android installer (`REQUEST_INSTALL_PACKAGES`; first time
  opens "Install unknown apps"). Publish with `scripts/publish-release.sh` (GITHUB_TOKEN).
  Download/verify/install not exercised end to end yet: it needs a newer published release.
- Version chip on Home made smaller (9 sp). Tests 158/158. Idle (paused, background, S24 FE):
  10 ticks / 60 s = 0.17% CPU.

## 2026-11-02 (2) — 7.8.0 (77) YouTube-only songs, failover speed, Search re-tap, version chip
- **Root cause (OPPO CPH2797, mobile data):** Shadow Fight soundtracks (YouTube only; JioSaavn
  and Gaana correctly had no match) never played. Trail on the phone: ANDROID_MUSIC
  LOGIN_REQUIRED "confirm you're not a bot" was treated as an age limit, which dropped the
  signature timestamp and NewPipe extraction for every fallback client; ANDROID_VR links were
  403; IOS/IOS_RECENT/ANDROID answered OK with signatureCipher links → `no-link`. Then 15 s
  lossless + 12 s loose JioSaavn ran in sequence (22–27 s of "loading").
- **Fixes:** `YouTubeAnswers.isBotCheck` (bot check ≠ age limit; tested); `ClientMemory`
  skips fallback clients refused (LOGIN_REQUIRED / HTTP 403) in the last 15 min, never the last
  client (tested); link check `callTimeout` 1.5 s (timeout = accept, as IO errors already
  did); last resorts run in parallel within 10 s.
- **Always-on trail:** `adb logcat -s AalapSource` prints per song each client with ms since
  start and the result, plus "playing from <source> after N ms" (Log.w survives R8).
- **Measured on the OPPO (release build, real data):** before: song never started. After:
  first song ~8 s (cold: player JS 2.5 s, iOS decode ~3.8 s), next songs 4.1–4.6 s; link check
  3.0 s → 0.5–0.85 s; VR skipped after the first refusal. Idle: paused in background 4 ticks /
  60 s ≈ 0.07% CPU (no new background work; everything added runs only during a song lookup).
- **UI:** Search tab re-tap / double tap → `SearchTabEvents.focus` → search field focused with
  keyboard (verified `mInputShown=true`); Home header shows `vVERSION_NAME` after the name.
- Tests 152/152. Not verified: other networks (the client that works can differ per IP),
  Wi-Fi, S24 FE with 7.8.0.

## 2026-11-02 — 7.6.1 (75) mini player width; 7.7.0 (76) one-tap sharing
- 7.6.1: `MiniPlayer` title row `width(260.dp)` → `weight(1f)`; at 360 dp wide (OPPO ColorOS,
  density 480) the play button was squeezed to ~26 dp. Verified on the OPPO: 144×144 px.
- 7.7.0: shares ran in the menu's `rememberCoroutineScope`; the profile sheet's "Share Aalap"
  called `onDismiss()` right away, so the first tap's 14 MB APK copy was cancelled when the
  sheet left (second tap found the cached copy). New `ui/share/ShareTask` (app-lifetime scope,
  one share at a time, `busy` flow, "Getting … ready" toast after 400 ms) used by
  `ShareApp.start` and `shareSongCard` (scope parameter removed); the player's share icon shows
  a spinner while busy; the profile sheet pre-copies the APK. Verified on the S24 FE after the
  update (stale copy): one tap → share sheet in 0.7 s; double tap on the player's share →
  one share sheet in 0.9 s.

## 2026-11-01 (6) — 7.6.0 (74) continue card, smart shuffle, skip memory, weekly Wrapped, per-output sound, backup
- **Continue card:** `HomeScreen.ContinueCard` shows while `SongPlayer.pendingResumeMs(query)`
  exists and `PlaybackSync.worthContinuing` (≥15 s in, not in the last 10 s). Verified: played
  to 0:18, paused, force-stopped, relaunched → "Continue listening · From 0:18"; tap resumed at
  18.4 s and the card went away. With a 0:00 resume point it correctly stays hidden.
- **Skip memory:** `engine/SkipMemory.tiredOf` replaces the 14-day `recentSkips` rule: two
  skips under 10 s within 90 days keep a song out for 90 days; finishing it later forgives
  earlier skips (behaviour change: a skip followed by a full play no longer excludes the song).
- **Smart shuffle:** `SmartShuffle.order` / `smartShuffled(context, songs)` (history: last 500
  plays) for Liked and playlist shuffle via `CurrentSongState.startShuffled(songs, order)`.
  Album shuffle unchanged. Not exercised on the phone (unit tests only).
- **Weekly Wrapped:** `ListeningPeriod` (Mon–Sun weeks, months); Wrapped chips This/Last week,
  This/Last month, comparison with the previous period; opens on last week when this week has
  <5 finished plays. `HomeScreen.LastWeekCard` on Mon/Tue only (not seen yet: tested on a Sunday).
  Verified on phone: week of 26 Oct, 439 min, 148 plays, "+330 minutes".
- **Per-output sound:** `SoundPref` keys `key@KIND:name` per output (phone = plain keys, so
  existing settings are the phone's; a new output starts from the phone's and copies them on
  first change). `AudioEffects.refreshOutput` on `AudioDeviceCallback` (no polling) and
  `SongPlayer.setOutput`. Verified only "Settings for This phone"; switching untested (no
  Bluetooth device connected).
- **Backup:** `data/local/Backup` = `UserVault.dump` of `UserVault.FILES + sound_prefs` +
  `RecoDb.exportRows` (all tables but `source_cache`; play ids dropped). Restore replaces the
  library prefs, adds DB rows (plays de-duplicated by user/song/time) and adopts the backup's
  user id into the current one. Verified: 6.4 MB file, 516 plays, 11,375 songs, no cookies;
  restore into the debug variant brought back 215 likes. The adopt-into-another-email path
  compiled but was not exercised (the test install ended up with the same email).
- Tests: `:app:testDebugUnitTest` 146/146 (16 new: SkipMemory, SmartShuffle,
  ListeningPeriod, worthContinuing, sound keys). Installed 73 → 74 with `adb install -r`; data kept.
- Performance: no background work added; Home cards read local prefs once per visit.

## 2026-11-01 (5) — 7.4.0 (72) compatibility/sign-in/output; 7.5.0 (73) timers; release process
- **Startup (ColorOS/Android 17):** not reproduced (no device/log). Same Android 17 on Samsung:
  cold start 0.92–0.99 s, no crash. Added `CrashReport` (installed first in
  `MyApplication.onCreate`) + Settings row to share it. See `docs/COMPATIBILITY.md`.
- **Lint (focused):** fixed API 28/29 calls in `FirestoreRest.signingSha1` and
  `SongPlayer.anyLatinTransliterator`; full lint crashes (Compose lint vs Kotlin 2.2 metadata).
- **Sign-in optional:** `ConnectServicesScreen` Start always enabled ("Start without signing
  in"), Spotify "Optional"; `MyNavHost` start route shows it only until passed once.
  `Api.searchEverything` → `youTubeSongSearch` (InnerTube FILTER_SONG → RecoDb → SongBridge)
  without Spotify or when Spotify search fails. Verified on a fresh debug install with no
  account: onboarding, Home (empty-state picks), search "kesariya", playback of Kesariya.
- **Output picker:** `audio/OutputDevices` (pure rules, tested) + `SongPlayer.setOutput`
  (`ExoPlayer.setPreferredAudioDevice`, applied to new players) + `OutputPicker` dialog with
  `AudioDeviceCallback` live list, Bluetooth settings, API 34 `MediaRouter2.showSystemOutputSwitcher`.
  Verified: dialog lists "This phone · Playing", system switcher opens. Not verified: Bluetooth,
  wired (none connected).
- **Debug variant:** `applicationIdSuffix ".debug"` so a fresh install can be tested beside the
  real app.
- **7.5.0 timers:** `SongPlayer.startPositionWatch` waits on `playerPlaying` while not playing;
  `PlaybackService` widget ticker waits on `playingState` and checks widgets every 10 s.
  Measured, paused in background (60 s, /proc ticks): before 1.0% (1 run), after 0.5% (2 runs).
  Regression checks after: pause/play/next/previous on the lock screen OK; automatic song
  changes kept happening with the screen off; crossfade vs cut after the change not verified.
- **Measured, not changed:** playing with the screen off, Lossless: 14–16% of a core steady,
  ~21% in a minute with a song change; ExoPlayer playback thread dominates (FLAC decoded on the
  CPU, no offload for FLAC on this phone). No same-settings baseline, so no claim either way.
  PSS while playing 176 MB (one sample).
- **Release process:** `scripts/release.sh` (tests, id, signer SHA-256 vs
  `scripts/release-signer.sha256`, versionCode > dist/, changelog entry, `--install` with
  `adb install -r`); `docs/RELEASING.md`. Verified: 7.3.0→7.4.0 and 7.4.0→7.5.0 updates kept
  first-install date, Liked 215 / Artists 84 / Plays 510, Lossless and crossfade 1 s; guards
  refused an unraised versionCode and a wrong signer.
- **Tests:** 130/130 (`OutputDevicesTest` 5 new).

## 2026-11-01 (4) — 7.3.0 (71): Gym Freaks radio; smooth cover bars measured
- **Decision:** keep current sources (owner, 2026-11-01); Phase 5 stays the assessment in
  `docs/PROVIDERS.md`. Gym Freaks uses the same sources as every other mood station.
- **Gym Freaks:** `Mood.GYM` ("gym workout" Spotify category), `Vibe.forMoodName("GYM")` =
  Energetic 0.8 + Party 0.2, `Craft.DHOKRA`, voice words (gym, gym freaks, lifting…; "workout"
  stays Energetic), morning/afternoon ordering, `AalapRadio.forMood` → `GymFit.order` on Home,
  Search (`MoodScreen`) and voice. `GymFit`: energy < 0.55 or < 95 bpm (unless ≥ 0.75 energy)
  left out unless fewer than 15 remain; unknown songs after known fits.
- **Device:** Search → Moods → Gym Freaks page listed workout songs; `aalap://play?q=gym`
  started the station in ~2 s ("Playing from Gym Freaks"), skips gave Highway to Hell, On My
  Way, Thunderstruck, Ignite. Cold start: Spotify 429s 4 → 1 (remaining: top artists, single
  request). Quick picks shown on Home.
- **Cover bars cost (settled 60 s, Home open, playing, `gfxinfo` + `/proc/<pid>/stat`):**
  still 3.7 fps / 14–19% of a core; 60 fps glide 63 fps / 67–69%; 25 fps glide 28 fps /
  49–53% (shipped). Paused: 0 frames / ~5%. RenderThread dominates (~15 ms CPU a frame, GPU
  3 ms): each frame repaints the whole window.
- **Owner chose brief motion:** glide `BeatBars.GLIDE_MS` (8 s, `glideBeats`) after each start,
  resume, song change or return to the screen, then ease to `HOLD` and stop. Device: in-app Play
  → ~30 fps for ~7 s, then ~1 fps; settled holding while playing 10 frames/10 s, 17% of a core
  (vs 49–53% continuous). A mid-song player swap (lossless upgrade) also restarts the glide.
- **Tests:** 125/125 (`GymFitTest` 8, 2 voice cases, `BeatBarsTest` +2).

## 2026-11-01 (3) — 7.1.0 / 7.2.0: notification + background, cover animation, picks, liked merge
- **Versions:** 7.1.0 (69) = playback fixes of (1)/(2) + Phase 4; 7.2.0 (70) = Phase 6.
  `CHANGELOG.md` has both entries; the build carries 7.2.0.
- **Phase 4 changes:**
  - `SongPlayer.audible` (Compose state from `onIsPlayingChanged`, incl. blend's incoming
    player, set on promotion, cleared on release): "sound is really coming out".
  - `CoverEqualizer` (`AppComponents.kt`): animates only while `audible` and STARTED; four
    `Animatable`s glide to new heights each beat, read in the Canvas draw lambda (draw-only
    invalidation). Tempo from `VibeStore.tempoOf` (stored `song_audio.bpm`, no new analysis);
    unmeasured songs use a neutral 600 ms pace. Pure rules in `ui/components/BeatBars.kt`.
  - Manifest: `androidx.media3.session.MediaButtonReceiver` (Play after the process is gone).
  - `PlaybackService`: recent root (`params.isRecent` → `NODE_RECENT` = last song) for
    Android's media-resumption controls.
  - Settings → Playback: `BackgroundPlaybackRow`, shown only when battery-optimised or
    background-restricted; opens app details settings (no special permission).
- **Phase 6 changes:**
  - `Api.HomeCache`: per-feed mutex, 60 s freshness, 20 s backoff after a failure, cleared by
    pull-to-refresh; `PlayerViewModel` no longer fetches unused Spotify top tracks.
  - `PicksStatus` (LOADING/READY/EMPTY/FAILED) + `retryQuickPicks`; `QuickPicksSection`
    shows a line and "Try again" instead of disappearing.
  - `YouTubeLibrary.mergeLiked`: also requires `SongVersion.same` (different takes kept).
- **Tests:** 115/115 (`BeatBarsTest` 6, `PicksStatusTest` 5, `LikedMergeTest` 8 new). The
  liked-merge test found the take bug before the fix.
- **Device (S24 FE, Android 17):** cover bars move while playing, overlay hidden and frames
  identical when paused from the system key; screen off (Dozing): playback continued, and
  pause / play / next / previous via the system media keys all worked; system now records
  `MediaButtonReceiver` as Aalap's receiver; cold start Spotify "Rate limited" errors 4 → 2
  before the backoff (after it: not measured, phone locked).
- **Not verified:** smooth-animation CPU cost; Home/picks on screen after the last build;
  true process death (no root; force-stop blocks media buttons); Android's resumption card;
  battery saver / Doze forcing (not changed: system settings); ColorOS (no device); Bluetooth
  and wired output (none connected).

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
