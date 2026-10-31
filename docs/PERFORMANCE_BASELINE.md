# Performance baseline

Status as of 7.0.0 (build 68), from code reading on 2026-10-31. **No new measurements were
taken during onboarding.** Figures below marked "changelog" are the author's own on-device
release-build measurements recorded in `CHANGELOG.md`; they have not been re-verified.

## Existing measurements (changelog)
| Version | Scenario | Result |
|---|---|---|
| 6.5.0 | music playing, background | 0–8% of one core (was ~30%, spikes 100–150%) |
| 6.5.0 | audio offload playback itself | ~0.4% |
| 6.6.0 | 1 min music, app open / background | 19–24% / 2–3% of one core; cold open 1 s |
| 7.0.0 | per song change | 1–5 s CPU (was 25 s+) |
| 7.0.0 | 1 min music, app open / background | 14% / 2–6% of one core |

How they were taken is not recorded in the repo. To reproduce: release build, `adb shell
top -d 60 -p $(pidof com.music.aalap)`, `adb shell dumpsys meminfo com.music.aalap`,
`adb shell dumpsys batterystats --charged com.music.aalap`, Android Studio profiler for
allocations/recomposition.

## Already addressed (don't regress)
Lifecycle-gated UI tickers; mini player 1 Hz; ExoPlayer dynamic scheduling; offload in
background; hidden Spotify WebView only when enabled; YouTube decoding only on JioSaavn
miss; no start-up YouTube warm-up; debug logs stripped in release; baseline profile.

## CPU-intensive operations
- **YouTube stream resolution** (`YTPlayerUtils`, `CipherWebView`, `PoTokenWebView`,
  NewPipe/Rhino): WebView + JS execution per resolution. Now a fallback path.
- **YouTube candidate scoring** (`SongPlayer.resolveVideoCandidates`, `ytmusicTransferScore`,
  regex/bigram/transliteration per candidate): moderate, per YouTube resolution.
- **Recommendation chaining** (`QueueEngine`): network fan-out + scoring per pick, runs at
  queue end and on taps via `SmartPlay`.
- **AudioFeatureTap**: autocorrelation over ~40 s of PCM per song on the audio thread
  (needs profiling; presumably idle under audio offload, where ExoPlayer bypasses
  audio processors — not verified).
- **Crossfade**: two decoders + filter processor during the fade (foreground only).

## Memory retention risks
1. **Unbounded registries in `SongPlayer`**: `streamCache`, `sourceCache`,
   `qualityCache`, `trackIdRegistry`, `alternativeKeyRegistry`, `explicitRegistry`,
   `durationRegistry`, `metadataRegistry` (+ `LyricsApi.registerTracks`) grow for the
   process lifetime. `CurrentSongState.updateQueue` re-registers the *whole* queue on every
   call — and `continueIntoRecommendations` calls it once per appended song — so a long
   session does O(n²) map writes and retains every song ever queued. Low per-entry size;
   matters on multi-hour sessions. *Needs heap measurement.*
2. `PlaybackService.trackById` / `queueByTrackId` (Android Auto browse) never pruned.
3. Long-lived WebViews (cipher, PoToken) once created — size unknown, *needs profiling*.
4. `VibeStore` LruCaches (8000 + 8000 + 4000 entries) — bounded but potentially several MB.
5. `Api.HomeCache` holds full feeds for the process lifetime (bounded by feed size).

## Rendering / recomposition
- `PlayerScreen` progress loop: **300 ms** while on screen (mini player is 1 s). Each
  tick writes state → recomposition of the progress area. Candidate to 500–1000 ms or
  derive from a single ticker.
- `LyricsScreen` 250 ms ticker (needed for line sync; could compute next-line delay).
- `DownloadsScreen` 400 ms poll of `Downloads.downloadingSnapshot()` while on screen;
  `Downloads.onDownloadsChanged` exists and could drive it instead.
- `SyncedVideo` (PlayerScreen) 700 ms sync loop while a video shows.
- `PetalShower` is always composed at the root; idle cost is a suspended collector
  (fine) — animation only on like.
- Infinite transitions in `Loading`, `WelcomeScreen`, `BlendScreen`, `VoiceOverlay`,
  `HeyAalap` overlay: check they leave composition when not visible.
- Large composables (`HomeScreen` 1.2k, `PlayerScreen` 1.6k lines): recomposition scope
  not audited — *needs Layout Inspector recomposition counts*.

## Network / database overhead
- Hourly `LibrarySyncWorker` re-reads full Spotify liked songs (all pages) and followed
  artists, plus YouTube library, even when unchanged.
- App start: three Home cache warm-ups + YouTube library sync (after 4 s) + visitorData.
- `BlendSync.pull` every 60 s while a Blend screen is on screen.
- Many SharedPreferences files with JSON blobs: full rewrite per edit; first read of each
  file on the main thread is possible (not audited).
- `RecoDb`: indexes exist for lookups; write batching not audited.

## Background tasks, timers, listeners
| Item | Frequency | Assessment |
|---|---|---|
| `PlaybackService` widget ticker | wakes every **500 ms for the service lifetime**, even paused/screen off | Cheap per wake but a perpetual timer; should run only while playing + interactive + widget placed (event-driven start/stop). **Top candidate.** |
| `SongPlayer.startPositionWatch` | 1 Hz (4 Hz last 20 s) while player exists; hops to Main several times per tick | Needed for preload/crossfade/position save; could stop when paused. |
| `HomeViewModel` new-window check | every 5 min while VM alive (incl. background, VM survives in back stack) | Small; could be lifecycle-gated or replaced by on-resume check. |
| `OfflineState` network callback | process lifetime | Appropriate. |
| `LibrarySyncWorker` | 1 h, network-constrained | Could be 6–12 h or conditional; trade-off with freshness. |
| Wake locks (`holdAwake`, ExoPlayer wake mode) | while playing / between songs | Required for reliability (7.0.0 fix); verify always released. |

## Ranked optimisation candidates
| # | Change | Expected impact | Risk |
|---|---|---|---|
| 1 | Widget ticker: start only while playing & interactive & widget placed; stop otherwise | Removes a perpetual 2 Hz wake-up; battery while paused | Low |
| 2 | `startPositionWatch`: suspend while paused / idle | Fewer wake-ups while paused with service alive | Low–Med (preload/crossfade timing) |
| 3 | `updateQueue` append path: register only new songs; cap/clear registries (e.g. keep current queue + LRU) | Removes O(n²) work and unbounded growth in long sessions | Med (lossless/YouTube matching relies on registries) |
| 4 | PlayerScreen progress 300 ms → 500–1000 ms | Fewer recompositions with player open | Low |
| 5 | DownloadsScreen poll → `onDownloadsChanged` event | Removes 2.5 Hz polling on that screen | Low |
| 6 | Library sync interval / conditional sync | Less background network & CPU hourly | Low–Med (freshness) |
| 7 | Audit infinite transitions & large-screen recomposition | Unknown — needs profiling first | Low |
| 8 | Measure AudioFeatureTap cost; skip when features already stored | Unknown | Low |

Each item needs a before/after measurement on a release build before it is claimed.
