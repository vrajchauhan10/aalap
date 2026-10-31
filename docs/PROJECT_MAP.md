# Project map

Paths are relative to `app/src/main/java/com/music/aalap/` unless they start with a module
name. Line counts are approximate (as of 7.0.0) to flag the big files.

## Entry points
| File | Role |
|---|---|
| `MyApplication.kt` | `@HiltAndroidApp`. Tracks app visibility (`isInForeground`, calls `SongPlayer.onAppVisible`), inits `OfflineState`, `CipherDeobfuscator`, YouTube locale/cookie/visitorData, schedules `LibrarySyncWorker`, warms `Api` Home/albums/artists caches, provides AppFunctions config. Debug: starts `QueueEndpoint`. |
| `MainActivity.kt` | Splash, notification permission, `MediaController` bootstrap of `PlaybackService`, `setContent { AalapTheme { App(); BlendHost; VoiceOverlay } }`, intent handling (`MEDIA_PLAY_FROM_SEARCH`, `aalap://play?q=`, `ACTION_SEND` Blend codes, open-player). Optionally attaches `SpotifyWebPlayer`. |
| `App.kt` | Root Scaffold: `MainBottomNavigation` + `MyNavHost` + global `PetalShower`. |
| `ui/notification/PlaybackService.kt` (720) | Media3 `MediaLibraryService`: session, custom notification buttons (like, shuffle), Android Auto browse (`LibraryCallback`, `childrenOf`), widgets push, **track end → next / `continueIntoRecommendations`**, `onTaskRemoved`, foreground between songs (`onUpdateNotification`). |
| `ui/notification/StartupService.kt` | Short foreground service (≤20 s) while a background play request finds its song. |
| `ai/AalapAppFunctions.kt` | Android 16 AppFunctions (play, pause, next, like, now playing) for Gemini. |
| `ui/widget/AalapWidgets.kt` | `AalapWidgetLarge` / `AalapWidgetCompact` RemoteViews widgets. |
| `ui/notification/VoiceTileService.kt`, `di/AskAalap.kt` (`AskAalapActivity`, `AalapKeyService` accessibility), `di/HeyAalap.kt` (`HeyAalapService`) | Voice entry points. |

## Playback
| File | Key symbols |
|---|---|
| `di/SongPlayer.kt` (2.2k) | `object SongPlayer`: `playSong`, `resolveStreamUrl` (source race), `quickStartThenUpgrade`/`seamlessSwap` (lossless upgrade), `prefetch`, `managePreload` (gapless next item ~30 s before end), `triggerCrossfade`/`performCrossfade`, `startPositionWatch` (1 Hz, 4 Hz near end), `applyOffload`, `holdAwake` (transition wake lock), `midPlayErrorListener` (retry/skip), `mediaCache` (256 MB `SimpleCache`), `resolveYtPlayback`/`resolveVideoCandidates` (YouTube matching & scoring), registries (`trackIdRegistry`, `metadataRegistry`, …), callbacks `onTrackEnded`/`onStreamFailed`/`onPlayerCreated`/`onPlayerSwapped`. |
| `di/CurrentSongState.kt` | `@Singleton` Compose state: `queue`, `songId`, `songIndex`, `title/singer/coverUri`, `playingState`, `shuffle`, `repeatMode` (`RepeatMode`), `likeState`; `updateQueue` (also seeds SongPlayer registries & `LyricsApi`), `nextAfterEnd`. |
| `ui/viewmodel/PlayerViewModel.kt` | UI-side queue ops: `playFromList`, `playAll`, `playNext`, `addToQueue`, `playNextSongs`, `playWithRadio`, `continueIntoRadio`, canvas loading, feedback. |
| `di/SmartPlay.kt` | Tap-in-list behaviour (song + fitting recommendations interleaved with list). |
| `di/SongVideo.kt`, `ui/screens/PlayerScreen.kt` (`SyncedVideo` ~l.1560) | Music video / Spotify Canvas synced to playback. |
| `playback/ChunkedDataSource.kt` | 2 MiB ranged reads for googlevideo. |
| `ui/components/BeatBars.kt`, `CoverEqualizer` in `AppComponents.kt` | Cover bars: move only while `SongPlayer.audible`, glide per beat of the stored tempo. |
| `playback/PlaybackSync.kt` | Pure rules for what the screen shows: position/length only for the song in the player, resume point, save guard. Used via `SongPlayer.progress()` by PlayerScreen, MiniPlayer, Lyrics. Tests: `app/src/test/.../playback/PlaybackSyncTest.kt`. |
| `audio/AudioEffects.kt` | Equalizer / bass boost / loudness on a shared audio session id. |
| `audio/CrossfadeFilterAudioProcessor.kt`, `audio/BiquadFilter.kt` | DJ-style crossfade filters. |
| `audio/AudioFeatureTap.kt`, `audio/AudioFeatures.kt` | In-pipeline tempo/energy measurement (~40 s per song) → `VibeStore.saveAudio`. |
| `di/Downloads.kt` | Offline downloads to `filesDir/downloads`, progress map. |
| `di/SpotifyWebPlayer.kt`, `ui/notification/WebMediaPlayer.kt` | Experimental hidden-WebView Spotify playback (off unless enabled + logged in). |

## Audio sources (resolution order: JioSaavn → YouTube → Gaana; lossless optional)
| File | Notes |
|---|---|
| `saavn/JioSaavnSource.kt` | Search + DES-decrypted AAC URLs; strict title/artist/length match. |
| `gaana/GaanaSource.kt` | HLS fallback, ISRC or fuzzy match. |
| `com/metrolist/music/utils/YTPlayerUtils.kt`, `.../cipher/*`, `.../potoken/*`, `.../sabr/*` | YouTube stream URL: player JS fetch, signature/n deobfuscation in a WebView, PoToken WebView. Heavy; only run when JioSaavn misses (7.0.0). |
| `innertube/` module | `YouTube.kt` (search, browse, next, player), `models/*`, `pages/*`, `NewPipe.kt`. |
| `lossless/LosslessSource.kt`, `LosslessRegistry.kt`, `SpotiflacGated.kt` | Tidal/Amazon FLAC via SpotiFLAC community proxies. |
| `soulseek/*` | P2P FLAC fetch into `cacheDir/slsk` (1 GB cap), lookahead for next song. |
| `data/preferences/MusicSourcePref.kt`, `AlternativeStreamPref.kt` | Source toggles / per-song overrides. |

## Data layer
| File | Notes |
|---|---|
| `data/api/Api.kt` (870) | Spotify → app models (`SongsModel`, `AlbumsModel`, …). All public calls return `Flow<Response<T>>`. Process cache `Api.HomeCache` (home/albums/artists/library), `CanvasCache`. `fetchAllPages`. |
| `data/api/Response.kt` | `Response.Loading/Success/Error`. |
| `ui/repository/AppRepository.kt` | Thin Hilt wrapper over `Api` + `Recommender`. |
| `spotify/` module `Spotify.kt` (2.1k) | REST + GraphQL (persisted-query hashes `SpotifyHashProvider`), `SpotifyAuth` (TOTP token), `SpotifyCanvas`, `SpotiFlac`. |
| `data/api/SpotifySession.kt`, `SpotifyTokenProvider.kt`, `SpotifySync.kt`, `SpotifyTaste.kt` | `sp_dc` cookie, token minting, library sync, top-artist taste. |
| `data/api/YouTubeLibrary.kt` | YT Music likes/subscriptions/history → unified library (hourly). |
| `data/api/LibrarySyncWorker.kt` | WorkManager periodic (1 h, network required). |
| `data/api/LyricsApi.kt`, `LyricTranslate.kt` | Spotify color-lyrics, LRCLIB fallback, translation. |
| `data/api/Discover.kt` (`Mood`), `TrendingPlaylists.kt`, `BrowseTileImages.kt`, `ArtistPicker.kt`, `ProfileCache.kt`, `AccountActions.kt` | Home/Search content helpers. |
| `data/entity/*` | Plain models (`SongsModel` has `url` = play query, `id` = stable hash). |
| `data/preferences/*` | SharedPreferences wrappers: likes, follows, settings, history, stats (`ListeningStats`, `ListenClock`), offline (`OfflineState`), playback restore, sound, sources. |
| `data/local/*` | `UserVault` (per-email library snapshot in `files/users/`), `AalapPlaylists`, `LocalImport` (SAF), `AppleMusicImport`, `HiddenPlaylists`. |

## Recommendations (`data/recommendation/`) — see `docs/recommendation/README.md`
`Recommender` (entry) → `engine/QueueEngine` (resolve → fetch → fuse → score → pick →
chain) using `engine/sources/*`, `Fusion`, `QueuePicker`, `Personalizer`, `TasteProfile`,
`Diversifier`, `Vibe`/`VibeStore` (LruCaches 8000/8000/4000), `SongMatcher`, `RecoDb`
(SQLite). Home: `HomeMixes`, `AalapRadio` (`moodRadio`, `forMood`; Gym Freaks = `Mood.GYM` ranked by `GymFit`). Blend: `Blend`, `BlendScore`, `BlendSync`
(Firestore REST via `FirestoreRest`, pull every 60 s while a Blend is on screen).

## UI (`ui/`)
- Navigation: `navigation/MyNavHost.kt` (430), `Routes.kt`, `MainBottomNavigation.kt`
  (mini player + bottom bar; progress ticker in `components/AppComponents.kt` ~l.105),
  `SheetLayer.kt`.
- Screens (`screens/`): Home (`HomeScreen` 1.2k, `HomeUnified`, `TodayScreen`), Player
  (`PlayerScreen` 1.6k), Lyrics, Queue, Search (+`SearchExplore`), Library, Liked, Album,
  Artist, Playlist, Category, Mood, Show (podcasts), History/Wrapped, Downloads, LocalFiles,
  Settings/Sound/ProfileSections, logins (Spotify, YouTube, SpotiFLAC verify), Welcome.
- ViewModels (`viewmodel/`): Home, Player, Search (150 ms debounce), Library, Liked,
  Album, Artist, Playlist, Category, Lyrics, Show.
- Components: poster art (`PosterDesigns*.kt`, `CraftArt`, `PosterRotation`), Blend UI,
  sheets, `LikeAnimations` (`PetalShower`), `VoiceOverlay`.
- Theme: `theme/` (`AalapTheme`, `ScreenState.onScreen`). Share cards: `share/`.

## Where common changes land
| Change | Files |
|---|---|
| New screen | `ui/screens/X.kt`, `ui/viewmodel/XViewModel.kt`, `Routes.kt`, `MyNavHost.kt` |
| New Spotify data | `spotify/.../Spotify.kt` (+ model), `data/api/Api.kt`, `AppRepository.kt` |
| Playback behaviour | `di/SongPlayer.kt`, `PlaybackService.kt`, `CurrentSongState.kt`, `PlayerViewModel.kt` |
| New audio source | new `xyz/XyzSource.kt`, `SongPlayer.resolveStreamUrl`, `MusicSourcePref.kt`, Settings |
| Recommendation tuning | `engine/QueuePicker.kt`, `Fusion.kt`, `TasteProfile.kt`; tests in `app/src/test/.../recommendation` |
| A setting | `data/preferences/SettingsPref.kt` (or topic pref file), `SettingsScreen`/`ProfileSections`, add file to `UserVault.FILES` if per-user |
| Notification / widget | `PlaybackService.customLayout`, `pushWidgets`, `ui/widget/AalapWidgets.kt`, `res/layout/widget_*.xml` |
| Voice | `di/VoicePlay.kt` (parse/plan), `VoiceAsk`, `HeyAalap`, `VoiceListener`, `VoiceUi` |
| Release | `app/build.gradle.kts` version, `CHANGELOG.md`, `scripts/sign-release.sh` |
| Providers / sources | read `docs/PROVIDERS.md` first (what is and isn't an official integration) |
