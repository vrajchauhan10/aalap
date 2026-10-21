# Aalap recommendation engine

When a song plays, Aalap builds the next 5 songs by chaining what people around the
world play next. Each pick becomes the seed for the next one.

Example: Daru Badnaam → (what plays after it) → Backbone → (what plays after Backbone) → …

## Folder structure

```
app/src/main/java/com/music/spotui/data/recommendation/
├── Recommender.kt              entry point used by every screen
└── engine/
    ├── Models.kt               Source (with RRF weight), Candidate, Song, Scored
    ├── SongMatcher.kt          RESOLVE: ISRC / ids / fuzzy title+artist matching
    ├── RecoDb.kt               SQLite: songs, source_cache, transitions, user_taste, play_history
    ├── Fusion.kt               FUSE: Reciprocal Rank Fusion
    ├── TasteProfile.kt         taste + habit scoring, finalScore formula
    ├── Personalizer.kt         PERSONALIZE: builds the taste from usage
    ├── Diversifier.kt          DIVERSIFY: no artist twice, no recent/skipped, top-5 sampling
    ├── QueueEngine.kt          build_queue: fetch → fuse → personalise → diversify → chain
    ├── QueueEndpoint.kt        GET /queue (debug builds)
    ├── SongBridge.kt           app song ⇄ canonical song
    └── sources/
        ├── NextSongSource.kt   adapter interface: getNextSongs(seed) → ranked list
        ├── YouTubeMusicSource.kt   1. YouTube Music "Up next" (primary)
        ├── SpotifyRadioSource.kt   2. Spotify song radio
        ├── JioSaavnRadioSource.kt  3. JioSaavn song radio (Indian/regional)
        └── LastFmSource.kt         3b. Last.fm track.getSimilar (needs a key)
app/src/test/java/com/music/spotui/data/recommendation/FusionTest.kt
docs/recommendation/schema.sql
```

## Pipeline

1. **Resolve.** The seed becomes a canonical id, found by ISRC first, then by Spotify, YouTube or JioSaavn id, then by fuzzy title and artist. The match ignores suffixes like "(Official Video)", "Lyrical", "Remix" and "From …", and folds transliteration spellings together (Badnaam = Badnam).
2. **Fetch.** All sources run in parallel. Results are cached per (seed, source) for 24 hours, and a failing source is skipped.
3. **Normalise.** Every result is mapped to a canonical id and deduplicated.
4. **Fuse.** `global = Σ weight / (60 + rank) + bonus`, using these weights:
   - YouTube 1.0
   - Spotify 1.0
   - JioSaavn 0.7
   - Last.fm 0.7

   The bonus is +0.3 when two sources picked the song and +0.6 when three or more did. Songs found by one source are kept.
5. **Personalise.**
   - `final = 0.6·global + 0.2·taste + 0.2·habit`.
   - `global` is divided by the best score in the candidate set (0..1).
   - A new listener (fewer than 3 plays) gets `global` only.
   - Only the top 40 fused candidates are reranked, plus your own habits after the seed. This way taste can reorder songs the world plays next, but can't pull in a song it rarely does.
6. **Diversify.**
   - The same artist never plays twice in a row.
   - Excluded: the last 30 plays, anything played in the last 3 hours, songs skipped in the last 14 days, and anything already in the queue.
   - The pick is sampled from the top 5, weighted by score.
7. **Chain.** The pick becomes the next seed. Song 1 is returned as soon as it's picked. While it's appended, the next seed's sources are already loading.

**Fallbacks** when no source answers: your own transitions after the seed, then the seed artist's top tracks.

### Taste (updates itself from how you use the app)

| Signal | How |
|---|---|
| Plays | per artist, 60-day half-life; finished plays count +1, skips −0.6 |
| Skips | a play under 30 s (or under half the song) is a skip; frequently skipped artists get a penalty |
| Replays | songs you finish again and again |
| Likes | liked songs lift their artists (+1.5) |
| Follows | followed artists (+2) |
| Apple Music import | imported library artists |
| Language | the languages you finish songs in (from JioSaavn data) |
| Time of day | artists you play more at this time of day (night / morning / afternoon / evening) |

**Habit** is how often you played B within 30 minutes after A. It goes down again if you skipped B.

## Assumptions

- **No server.** Aalap is an app, so the engine and its database run on the phone (SQLite). Every table has `user_id`, so it can move to a server without changing the logic.
- **REST endpoint.** In debug builds, `GET /queue` is served inside the phone:

  ```bash
  adb forward tcp:8765 tcp:8765
  curl "http://localhost:8765/queue?seed_song_id=q:daru%20badnaam%20kamal%20kahlon&length=5"
  ```

  `seed_song_id` can be a canonical id, `spotify:<track id>`, or `q:<search text>`.
- **Spotify.** The public Spotify Recommendations API is closed to new apps (since Nov 2024), so it is not used. The Spotify source uses what Spotify's own web player queues after a song (its track radio). If that is empty, it uses Spotify's "recommended tracks", then an artist radio.
- **Third source.** JioSaavn song radio is always on. Last.fm `track.getSimilar` turns on when `lastfmApiKey=…` is set in `~/.gradle/gradle.properties`.
- **Timeouts.**
  - Song 1 waits 800 ms per source. If nothing answered, it waits up to 2.5 s more before falling back, since the seed is already playing.
  - Songs 2–5 are built in the background and wait up to 4 s per source.
  - Late answers still fill the cache.
- **"Remix" is ignored when matching**, as specified. A remix and its original count as one song.

## Test

```bash
./gradlew :app:testDebugUnitTest --tests 'com.music.spotui.data.recommendation.FusionTest'
```

The test covers fusion scores and order for the Daru Badnam example, single-source songs being kept, duplicate ranks, the song matcher, the finalScore weights, and the diversity rules.
