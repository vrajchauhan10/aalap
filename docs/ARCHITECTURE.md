# Architecture

Verified against the code at 7.0.0 (build 68). Where something is inferred rather than
read, it says so.

## Stack
Kotlin 2.2, AGP 8.9.1, compileSdk 36 / targetSdk 34 / minSdk 26, JDK 17, Compose BOM
2024.06 (Material 3), Navigation-Compose 2.7.7, Hilt 2.57.1 (KSP), Coroutines 1.7.3,
Media3 1.5.1 (exoplayer, dash, hls, session, ui), WorkManager 2.9.1, Glide Compose
1.0.0-beta01, OkHttp 4.12 (app) + Ktor 3.4.2 (modules), kotlinx-serialization, AppFunctions
1.0.0-alpha08, ProfileInstaller (bundled `baseline-prof.txt`). Release: R8 optimise only
(`-dontobfuscate -dontshrink`), Log.v/d/i and Timber v/d/i stripped.

## Layers
```
Compose screens ──► Hilt ViewModels ──► AppRepository ──► Api ──► :spotify (Ktor)
      │                    │                                   └─► :innertube (YouTube)
      │                    └─► Recommender/QueueEngine ──► RecoDb (SQLite), sources
      ▼
CurrentSongState (singleton Compose state)  ◄──►  SongPlayer (object, ExoPlayer)
                                                      ▲
                     PlaybackService (MediaLibraryService, session, queue advance)
```
DI is light: Hilt provides `Api`, `AppRepository`, `CurrentSongState` and ViewModels;
most subsystems are Kotlin `object` singletons (`SongPlayer`, `Downloads`, `SmartPlay`,
`VibeStore`, sources) holding `applicationContext`. `AppModule` is empty.

## State management
- Playback/queue: `CurrentSongState` (`@Singleton`, Compose `MutableState`). Read by UI,
  `PlaybackService` (via `snapshotFlow`) and `SongPlayer` (`bindState`). Because Compose
  apply notifications only flow once a UI runs, `PlaybackService` registers a global
  snapshot write observer that calls `Snapshot.sendApplyNotifications()`.
- Screen data: ViewModels expose `StateFlow<Response<T>>` / `MutableState`.
- Cross-cutting UI signals: small objects with `MutableStateFlow`/state (`VoiceUi`,
  `VoicePlay.openPlayerRequest`, `LikeCelebration.events`, `BlendUi`, `ScreenState`).

## Core flows
**Play a song.** UI → `PlayerViewModel.playFromList`/`SmartPlay` → `CurrentSongState.updateQueue`
(+ registry seeding) → `SongPlayer.playSong(query)`: fade out, offline/download/web
checks, prefetch next, `finding = song` (wake lock), then `resolveStreamUrl`:
memory `streamCache` → downloaded file → optional lossless (SpotiFLAC 15 s, Soulseek)
→ race JioSaavn and Gaana, YouTube lazily (started only after a wait / when JioSaavn
misses; 1 s when YouTube recently failed) → `startStream` → ExoPlayer with
`CacheDataSource(SimpleCache 256 MB) ← ChunkedDataSource ← DefaultHttpDataSource`.
Failure → `streamFailed` (waits for network if offline-ish; stops after 5 consecutive
unavailable songs) → `onStreamFailed` → `PlaybackService.advance`.

**Song end.** ExoPlayer `STATE_ENDED` / gapless `onMediaItemTransition` → `SongPlayer`
callbacks → `PlaybackService.onTrackEnded` → repeat / `playIndex(next)` /
`continueIntoRecommendations()` (collects `Recommender.next` flow, appends to queue,
advances on first). `managePreload` queues the next item inside ExoPlayer ~30 s before the
end (no crossfade); crossfade uses a second ExoPlayer and `onPlayerSwapped` rebinds the session.

**Recommendations.** `QueueEngine.queueSongs` emits songs one by one; sources fetched in
parallel (800 ms budget for song 1, 4 s for later), cached 24 h in `source_cache`;
see `docs/recommendation/README.md`.

**Home.** `HomeViewModel.init` loads feed/artists/albums/discovery/unified mixes; process
cache in `Api.HomeCache` emits instantly then refreshes. A 5-minute loop checks for a new
3/6-hour window while the ViewModel lives.

## Storage
| Store | What | Notes |
|---|---|---|
| SharedPreferences (≈60 call sites, many files) | likes (`LikedSongs*`), follows, albums, settings, history, stats, Blend, YouTube session, reco settings, `vibe_store` | Large lists stored as JSON strings; whole value rewritten on change (inferred from wrappers). `UserVault` snapshots the per-user files to `files/users/<hash>.json` on logout. |
| SQLite `aalap_reco.db` (`RecoDb`, v3) | songs, source_cache, transitions, user_taste, play_history, song_tags, song_audio, feedback | Singleton helper; "safe off the main thread" per its doc. Schema mirror in `docs/recommendation/schema.sql`. |
| Media3 `SimpleCache` `cacheDir/media` | streamed audio | LRU 256 MB. |
| `cacheDir/slsk` | Soulseek FLACs | 1 GB cap. |
| `filesDir/downloads` | offline downloads | Unbounded (user-controlled). |
| In-memory | `SongPlayer` registries & stream caches, `Api.HomeCache`, `VibeStore` LruCaches, `ProfileCache` | Several `ConcurrentHashMap`s are unbounded (see performance doc). |

## Networking
OkHttp (app: Media3 HTTP, sources, Firestore REST) and Ktor+OkHttp (`:spotify`,
`:innertube`). No shared client config verified across modules. External services:
Spotify web/GraphQL (needs `sp_dc`), YouTube Music InnerTube + googlevideo, JioSaavn,
Gaana, SpotiFLAC community proxies (Tidal/Amazon), Soulseek P2P, LRCLIB (lyrics),
Last.fm (optional key), Firebase Auth/Firestore REST (optional, from `google-services.json`).

## Background execution
| Mechanism | When | Notes |
|---|---|---|
| `PlaybackService` (foreground `mediaPlayback`) | while playing **and between songs** (`isBetweenSongs`) | Stops on task removal only if idle. |
| ExoPlayer wake mode + `SongPlayer.holdAwake` partial wake lock | playing / finding next song | Added in 7.0.0. |
| Audio offload | app in background, speed 1× | Disables crossfade in background. |
| `StartupService` | background play request | `stopSelf` after ≤20 s or when playing. |
| `LibrarySyncWorker` | every 1 h, network required | Spotify likes/follows, taste, YouTube library. |
| `HeyAalapService` (foreground `microphone`) | only if "Hey Aalap" enabled, while app open or music plays | On-device `SpeechRecognizer`. |
| `OfflineState` network callback | process lifetime | Plus 2 s / 8 s re-checks. |
| `SongPlayer.startPositionWatch` | while a player exists | 1 Hz, 4 Hz in last 20 s. |
| `PlaybackService` widget ticker | service lifetime | Wakes every 500 ms; pushes only if playing, screen on, widget placed. |

## Lifecycle
- App visibility: `MyApplication` counts started activities → `SongPlayer.onAppVisible`
  (offload) and `ScreenState.onScreen`.
- `MainActivity.onDestroy` releases the player only if finishing and not playing.
- UI tickers (mini player, player, lyrics, downloads, Blend pull) use
  `repeatOnLifecycle(STARTED)`.
- Release/dispose: `PlaybackService.onDestroy` clears all `SongPlayer` callbacks, the
  snapshot observer, web player and session.

## Design decisions & constraints
- No renaming/shrinking in R8 (reflection & JS bridges by name).
- No Firebase SDK (protobuf conflict with `:innertube`).
- YouTube decoding is expensive (WebView + JS), so JioSaavn goes first (6.5.0) and
  YouTube runs only when needed (7.0.0).
- Queue advancement lives in the service, not the UI, so it works with the screen off.
- Single user per install; per-user data swapped by `UserVault`.
- Portrait only. Android only (iOS dropped).
