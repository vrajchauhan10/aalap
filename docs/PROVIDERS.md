# Music providers: what Aalap uses today and what an official integration allows

Assessment of 2026-11-01, from the code (7.0.x). "Official" means a documented public API or
SDK used under the provider's developer terms. This is an engineering assessment, not legal
advice; each provider's current terms decide.

## What the code does today

| Provider | Used for | How (verified in code) | Official? |
|---|---|---|---|
| Spotify | Catalogue, home feed, library, likes, lyrics, Canvas, recommendations | Web-player `sp_dc` cookie → web token, private GraphQL persisted queries (`spotify/Spotify.kt`, `SpotifyAuth` TOTP) | No: not the Web API with OAuth |
| Spotify (web player) | Optional full playback | Hidden WebView of open.spotify.com (`di/SpotifyWebPlayer.kt`), off by default | No |
| YouTube / YouTube Music | Search, matching, audio streams, library sync | InnerTube private API, player-JS signature/n deobfuscation, PoToken WebView (`innertube/`, `com/metrolist/...`) | No: stream extraction |
| JioSaavn | Audio (first choice) | Private web API, DES-decrypted CDN URLs (`saavn/JioSaavnSource.kt`) | No public API |
| Gaana | Audio (last fallback) | Private API with app key, HLS (`gaana/GaanaSource.kt`) | No public API |
| SpotiFLAC (Tidal / Amazon) | Lossless audio | Community proxy servers (`lossless/*`) | No |
| Soulseek | Lossless audio | P2P downloads (`soulseek/*`) | No (unlicensed sharing) |
| Last.fm | Similar tracks (optional key) | Public API (`sources/LastFmSource.kt`) | Yes (metadata only) |
| Apple Music | Taste import | Library export file (`data/local/AppleMusicImport.kt`) | User's own data |
| Local files / downloads | Playback | Storage Access Framework, app files | Yes (user's own files) |

So no streaming source in the app today is an official full-track playback integration.
Only local files are clearly within terms for playback; Last.fm and the Apple Music import
are fine for metadata/taste.

## What official integrations would allow

| Provider | Official route | Full tracks in Aalap's own player? | Needs |
|---|---|---|---|
| Spotify | Web API (OAuth: metadata, library, likes, playback *control* of Spotify Connect devices); Android SDK "App Remote" (controls the installed Spotify app) | No: Spotify plays in its own app; on-demand needs Premium | Developer app + client id, user OAuth, Spotify app installed; extended quota approval for public release |
| YouTube | YouTube Data API v3 (search, metadata, playlists, likes via OAuth); official embedded player | No audio-only or background playback; video in the official player, on screen | API key / OAuth client, daily quota |
| YouTube Music | No public API | — | — |
| Apple Music | MusicKit for Android | Yes, inside the MusicKit player, for subscribers | Apple developer membership, MusicKit key, user subscription |
| JioSaavn, Gaana | No public developer APIs found | — | A licensing/partner agreement |
| Last.fm | Public API | No (metadata only) | API key |

## Consequences for Aalap
- A provider switch "within the rules" cannot keep one player that plays every provider's
  tracks: Spotify and YouTube playback happen in their own apps/players. The common core
  would be the queue, library and recommendations; playback would be per provider.
- Metadata access (Spotify Web API, YouTube Data API, Last.fm) does not grant permission to
  play full tracks from another source.
- Moving to official integrations means removing or disabling the current private-API
  sources, which is most of today's playback. That's a product decision for the owner.
