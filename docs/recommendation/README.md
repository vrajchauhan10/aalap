# Aalap recommendation engine

When a song plays, Aalap builds what comes next one song at a time. Each pick asks
six things, then the queue's mix rules have their say:

1. **The world:** what YouTube Music, Spotify and JioSaavn play right after the current song.
2. **The session:** what they play after the song you chose and the last few, together,
   so a long queue doesn't drift away from where you started.
3. **The fit:** does it share the session's feel (energetic, party, romantic, sad,
   chill, devotional, focus, happy), tempo, language and style (lofi, rap, sufi…)?
4. **Your taste:** artists you actually finish, the feels you finish (vs skip), your
   languages, what you play at this time of day. A like is one small signal among these.
5. **Your habits:** what you yourself play after this song.
6. **Trends:** today's charts.

The mix rules: about a third of songs you already know (more if you often skip new
songs, fewer if you don't), liked songs never more than about a third, never the same
artist twice in a row (and rarely within six songs), and nothing you played lately or skipped.

Example: Millionaire (Yo Yo Honey Singh) used to be followed by ten romantic ballads
(your taste and likes pulled each pick, and each pick seeded the next). Now the
session keeps it to high-energy Punjabi/Hindi rap and party songs.

## Folder structure

```
app/src/main/java/com/music/aalap/data/recommendation/
├── Recommender.kt              entry point used by every screen
├── PlayContext.kt              the song you chose and the list you chose it from
└── engine/
    ├── Models.kt               Source (with RRF weight), Candidate, Song, Scored
    ├── SongMatcher.kt          RESOLVE: ISRC / ids / fuzzy title+artist matching
    ├── RecoDb.kt               SQLite: songs, source_cache, transitions, user_taste, play_history, song_tags
    ├── Fusion.kt               FUSE: Reciprocal Rank Fusion
    ├── Vibe.kt                 a song's feel, energy and style (pure)
    ├── VibeStore.kt            works out feels from titles, mood stations, artists and "up next" lists; trending
    ├── QueuePicker.kt          the session, the score and the pick rules (pure)
    ├── TasteProfile.kt         long-term taste scoring
    ├── Personalizer.kt         PERSONALIZE: builds the taste from usage
    ├── Diversifier.kt          weighted sampling, artist spreading
    ├── QueueEngine.kt          build_queue: fetch → fuse → session → score → pick → chain
    ├── QueueEndpoint.kt        GET /queue, /vibe, /learn (debug builds)
    ├── SongBridge.kt           app song ⇄ canonical song
    └── sources/                YouTube Music, Spotify radio, JioSaavn radio, Last.fm
app/src/main/java/com/music/aalap/di/SmartPlay.kt   tapping a song in a list
app/src/test/java/com/music/aalap/data/recommendation/QueueScenarioTest.kt
app/src/test/java/com/music/aalap/data/recommendation/FusionTest.kt
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
5. **Score** ([QueuePicker]): for an established listener

   `final = 0.28·world + 0.14·session + 0.26·fit + 0.16·taste + 0.10·habit + 0.06·trending`

   and for a new listener (fewer than 3 plays) `0.40·world + 0.18·session + 0.34·fit + 0.08·trending`.
   - **fit** = 0.7 × feel match (feel similarity sharpened, tempo, same main feel, style) + 0.3 × language match.
   - A song clearly off the session's feel (feel match under 0.45) is multiplied by 0.6.
   - Artist skipped in this session ×0.25; artist already in the last 6 songs ×0.7 (twice: ×0.35).
   - Mix: +0.06 for a known song while under the known-songs target, +0.04 for a new one once over it;
     liked songs ×0.45 once they're over 35% of the picks; from the list you tapped in, +0.1 while under 35%.
   - Candidates are the top 40 the world plays next, your habits, and, only when they fit the session
     (feel match ≥ 0.62), your 40 favourite songs, 25 trending songs and 30 songs of the list you tapped in.
6. **Pick.** Never the same artist twice in a row; nothing played in the last 3 hours, among the
   last 30 plays, or skipped in the last 14 days; one of the top 6, sampled by score².
7. **Chain.** The pick becomes the next seed. Song 1 is returned as soon as it's picked. While it's appended, the next seed's sources are already loading.

**Fallbacks** when no source answers: your own transitions after the seed, then the seed artist's top tracks.

### 5.0 additions

| Part | How |
|---|---|
| Audio analysis | `audio/AudioFeatureTap` listens to ~40 s of each song as it plays (passing audio through unchanged) and `AudioFeatures` measures its tempo (onset autocorrelation, 60–180 bpm, preferring ~120 when half/double time fit equally) and energy (loudness, beat strength, tempo). Saved per song in `song_audio`; an artist's typical measured energy fills in for their unmeasured songs. |
| Listening length | A play counts `0.35 + 0.65 × share heard`; a skip −0.6, a skip in the first 10 s −0.9 (`TasteProfile.playWeight`). Feels you finish are weighted the same way. |
| Smooth flow | Energy change over 0.18 from the song before: ×(1 − 1.6 × excess, at most −45%); tempo off by over 20% (half/double time allowed): ×0.85; within 6%: +0.03. |
| Feedback | `Feedback`: blocked songs/artists never play; "less like this" ×0.2 for the song, ×0.55 per time for its artist, and its feel −40%; "more like this" ×1.3 song, +15% per time for its artist, +20% for its feel. Acts at once: skips/removes and redoes the queued picks; the session is nudged toward (+1) or away (−1) for two hours. |
| Discovery dial | Familiar 75% known · Mostly familiar 55% · Balanced (learned) · Explore 20% · Surprise me 8% known and 30% surprises. The pull grows with the distance from the middle. |
| Language mix | Off / Some / Lots per language; languages turned off ×0.35; a language behind its share +0.08 + 0.4 × gap; one well over ×0.8. Titles without a language label get one from their words (`LanguageGuess`). |
| Re-discover | Songs finished 3+ times but not played for 45+ days join the candidates when they fit; +0.1 up to 10% of the queue, ×0.5 beyond. |
| Surprise | New to you, by an artist you don't usually play, and fits the session (≥ 0.65): +0.12 (more on Surprise me) up to the dial's share. |
| Blend | `Blend`: a taste code (top artists, feels, languages, 25 favourites; gzip + base64) to share; from a friend's code, a playlist of your songs that fit them, theirs that fit you, and new songs after artists you share. |

### The session

The session is the song you chose (counts most, 1.2), the songs you played in the
last stretch of listening (finished +0.8, fading by 15% per song; skipped −0.9, which
pushes the session away from that feel and that artist), and the engine's own picks
(0.25 each, so they can't talk the session into a new direction). What you usually
play at this time of day only matters while the session says little.

If you skip a song the engine queued, the queued songs still to come that are too much
like it (same artist, or the same feel and tempo) are dropped, and new ones are picked.

### A song's feel (no audio analysis needed)

| Evidence | How |
|---|---|
| Title and album words | bhajan, aarti → devotional; remix, party, daaru → party; dard, bewafa, judaai → sad; lofi, slowed, unplugged → chill; workout, drill, rap → energetic; ishq, pyaar → romantic; instrumental, piano → focus (Hindi, Punjabi, Urdu, Gujarati and English spellings) |
| Mood stations | the songs of the Energetic, Party, Sad, Devotional… stations are labelled with that feel (whenever a station is built, and every 3 days in the background; about 1,000 songs) |
| The artist | the artist's usual feel and language, from their other songs |
| What plays next to it | the feel of the songs the world plays right after it |

Feels: Energetic (energy 0.95), Party (0.9), Happy (0.7), Romantic (0.45), Devotional (0.35),
Chill (0.3), Sad (0.2), Focus (0.2). Styles act like genres: lofi, remix, acoustic, rap,
sufi, ghazal, classical, instrumental, devotional, folk, retro.

### Taste (updates itself from how you use the app)

| Signal | How |
|---|---|
| Plays | per artist, 60-day half-life; finished plays count +1, skips −0.6 |
| Skips | a play under 30 s (or under half the song) is a skip; frequently skipped artists get a penalty |
| Replays | songs you finish again and again |
| Feels | which feels you finish vs skip (finish rate per feel) |
| Likes | +0.5 per liked song to its artist, at most +3 per artist (was +1.5 with no limit); 6% of the taste score |
| Follows | followed artists (+2) |
| Apple Music import | imported library artists |
| Language | the languages you finish songs in |
| Time of day | artists and feels you play more at this time of day (night / morning / afternoon / evening) |
| New vs known | how often you skip songs the first time you hear them vs songs you know sets the known-songs target (20–60%) |

### Starting from a list

Tap a song in Liked Songs, a playlist, Downloads or an artist's songs: that song plays,
then recommendations that fit it, with songs from the list mixed in at random (about a
third, only ones that fit). The list's Play and Shuffle buttons play the whole list.
Albums play in order. Offline, a tap plays the list from that song.

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
./gradlew :app:testDebugUnitTest --tests 'com.music.aalap.data.recommendation.FusionTest'
```

The test covers fusion scores and order for the Daru Badnam example, single-source songs being kept, duplicate ranks, the song matcher, the finalScore weights, and the diversity rules.

```bash
./gradlew :app:testDebugUnitTest --tests 'com.music.aalap.data.recommendation.QueueScenarioTest'
```

The scenario test simulates 15 genres and a listener whose likes lean to romantic
Bollywood, runs the old and the new engine many times, and writes the comparison to
`app/build/reports/reco-scenarios.md` (same feel, tempo gap, language, liked share,
artist repeats; tapping in Liked Songs; skipping; a new listener; the known/new/trending mix).
