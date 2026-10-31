# Aalap — project guide for Claude

Android music player (Kotlin, Jetpack Compose, Media3/ExoPlayer). App id `com.music.aalap`,
current version in `app/build.gradle.kts` (`versionName`/`versionCode`). **Android only** —
no iOS port. Metadata comes from Spotify (web API via the user's `sp_dc` cookie); audio is
resolved per song from JioSaavn → YouTube (InnerTube) → Gaana, plus optional lossless
(SpotiFLAC/Tidal, Soulseek). Single-user per install; no backend of its own (optional
Firebase Firestore over REST for Blend sync).

Detailed docs (read only what the task needs):
- `docs/PROJECT_MAP.md` — where things live, per feature, with symbols
- `docs/ARCHITECTURE.md` — flows, state, storage, background, lifecycle
- `docs/PERFORMANCE_BASELINE.md` — known hot spots, risks, ranked optimisations
- `docs/CHANGELOG_TECHNICAL.md` — technical change log (update for significant changes)
- `docs/recommendation/README.md` — recommendation engine pipeline and weights
- `CHANGELOG.md` — user-facing release notes (one entry per version)

## Modules
| Module | What |
|---|---|
| `app` | Everything user-facing: UI, player, sources, recommendations, storage |
| `innertube` | Trimmed Metrolist YouTube Music client (Ktor, NewPipeExtractor, Rhino) |
| `spotify` | Pure-JVM Spotify web/GraphQL client, TOTP auth, SpotiFLAC, Canvas |

Package `com.metrolist.music.utils` inside `app` is ported YouTube stream decoding
(cipher WebView, PoToken WebView, SABR n-transform) — treat as vendored.

## Commands (verified 2026-10-31)
`local.properties` is not committed: set `ANDROID_HOME=$HOME/Library/Android/sdk` (or
create `local.properties` with `sdk.dir=`) or Gradle fails with "SDK location not found".
```
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :innertube:testDebugUnitTest :spotify:test
./gradlew :app:assembleRelease && scripts/sign-release.sh   # needs ~/.aalap-signing keys
```
Unit tests (JUnit4, `app/src/test`, ~85 + 6 + 2) cover the pure logic: recommendation
picking/fusion/vibe, song version matching, voice parsing, audio features, Soulseek/Tidal
matching. There are no meaningful instrumented/UI tests. Debug builds expose a
recommendation test endpoint on `127.0.0.1:8765` (`QueueEndpoint`).

## Architecture in one paragraph
`MyApplication` (Hilt) sets up YouTube session state, schedules `LibrarySyncWorker`
(hourly), and warms Home caches. `MainActivity` hosts Compose `App()` → `MyNavHost`
(string routes in `ui/navigation/Routes.kt`). Screens use Hilt ViewModels → `AppRepository`
→ `data/api/Api` (Flow<`Response<T>`>). Playback state lives in the singleton
`di/CurrentSongState` (Compose `MutableState`s); the singleton object `di/SongPlayer` owns
ExoPlayer, stream resolution, caching, preload, crossfade and offload.
`ui/notification/PlaybackService` (Media3 `MediaLibraryService`) owns the session,
notification, Android Auto browse tree, widgets, and **queue advancement** (track end →
next / recommendations), so playback continues with no UI. Persistence is
SharedPreferences (many small files, JSON strings) + one SQLite DB (`RecoDb`,
`aalap_reco.db`) for the recommendation engine + Media3 `SimpleCache` (256 MB) for audio.

## Conventions
- Each shipped change bumps the minor version and `versionCode` +1, with a plain-English
  entry at the top of `CHANGELOG.md` (newest first). Don't bump unless asked to release.
- Comments explain *why*, in plain non-technical English, often with the measured effect.
  Fully-qualified names inline instead of imports is common; match the file you're in.
- Singletons are Kotlin `object`s with their own `CoroutineScope(SupervisorJob() + IO)`;
  ExoPlayer is touched only on Main (`withContext(Dispatchers.Main)`).
- UI loops must be lifecycle-gated (`repeatOnLifecycle(STARTED)`), never run in background.
- Users never see backend names (Spotify-owned playlists show as "Aalap").
- R8 optimises but never renames/shrinks (`proguard-rules.pro`): code is found by name
  (NewPipe, Rhino, JSON models, JS bridges). Do not enable obfuscation/shrinking.
- No Firebase SDK (protobuf clash) — Firestore is REST (`FirestoreRest`).

## Performance requirements (core, not an afterthought)
Minimise CPU, RAM, battery. Before adding any work, ask: does it run in the background,
on every song, on every frame, or forever? Specifically:
- Nothing polls in the background; UI tickers ≤ 1 Hz and only while STARTED.
- Don't add work per song change (each was measured at 1–5 s CPU after 7.0.0).
- Keep audio offload possible in the background (speed 1×, no crossfade there).
- Bound every cache (several existing registries are not — see PERFORMANCE_BASELINE).
- Don't start WebViews (cipher/PoToken/Spotify) unless the path truly needs them.
- Measure on a device with a **release** build (`adb shell top`, `dumpsys batterystats`,
  `dumpsys meminfo com.music.aalap`) before claiming an improvement.

## Working on changes
1. Read this file, then `docs/PROJECT_MAP.md` for the area; open only relevant sources.
2. `SongPlayer.kt` (2.2k lines) and `PlaybackService.kt` are high-risk: playback with the
   screen off, gapless preload, crossfade, error-retry and foreground-service rules
   interact. Trace callers before editing; keep `isBetweenSongs()` semantics intact.
3. Prefer the smallest change; no new dependencies without justification (APK size).
4. Add/extend JUnit tests for pure logic; run the test command above and report results.
5. Update `docs/CHANGELOG_TECHNICAL.md` (and the relevant doc) for meaningful changes.
