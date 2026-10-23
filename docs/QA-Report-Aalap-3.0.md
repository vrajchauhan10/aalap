# Aalap 3.0 — QA report

**Build:** Aalap 3.0.0 (versionCode 30), release build, file `dist/Aalap-3.0.apk`
**Tested on:** Samsung (Android, One UI), signed in to Spotify and YouTube Music, 23 Oct 2026
**Tested by:** Claude, acting as senior QA engineer: every screen driven on the phone, background jobs and saved data inspected, failure cases checked, bugs fixed and re-tested.

---

## 1. Summary

| | |
|---|---|
| Areas tested | 16 (Home, Aaj ka Aalap, Quick picks, charts, moods, mixes, Search, Library, playlists, artist and album pages, player, mini player, Liked Songs, History/Downloads/Local files, Profile and Settings, background sync and taste) |
| Bugs found and fixed | **24** (2 data bugs, 6 behaviour bugs, 4 failure-case bugs, 3 accessibility, 9 display/text) |
| Open bugs | **0** known |
| Things not run on the phone (and why) | 5, listed in section 6 |
| Player close smoothness | worst frame **~30 ms** (was 150–450 ms before this round of work) |

Everything listed in section 3 as ✅ was done on the phone and checked by reading the screen, the playback state or the app's saved data.

---

## 2. How Aalap works (feature by feature)

### Home
- **Greeting** follows the time of day (Good morning / Late-night listening).
- **Aaj ka Aalap poster**: one of **50 handmade craft designs**, a new one at midnight (also while the app is open). The shuffle button picks another design for the rest of the day. Tapping the poster opens **today's 30 songs**: 8 trending, 7 top hits, 9 for your taste, 6 of your likes, rebuilt every day.
- **Quick picks**: trending songs mixed with picks for your taste; a new set every day **and** on every refresh (pull down, or double-tap the Home tab). Each row shows why it's there (Trending / Top hit / Your taste) and a gold heart if you liked it.
- **Mood stations**: 17 moods, each ordered for your taste; tap to play. They change every 6 hours.
- **Today's charts**: Top 50 India, Top 50 Global, Trending India (Spotify's *Trending Now India*), Trending Global (*Viral Hits*). Cached once a day.
- **Made for you**: Daily Mix 1–3 from your top artists, new every day.
- **Jump back in, Recently played, Your all-time favourites, Your artist radios, New from your artists, Popular albums and singles, More of what you like.**
- Home also rebuilds by itself when a new 6-hour window or a new day starts, even if left open.

### Search
- 10 **trending searches** (5 from YouTube Music, 5 from Spotify), refreshed daily; mood and explore tiles.
- Results: songs, artists, albums, each shown once, with **All / Songs / Artists / Albums** filters and taste tags.

### Library
- Tiles: **Liked Songs** (same count everywhere), Downloads, History, Local files.
- **Albums from artists you love**, rotating every day.
- Filters All / Playlists / Albums / Artists; list or grid (4×4 pages).
- **Create Aalap playlists**: add songs, rename, delete. Long-press a Spotify playlist to remove it from Aalap only (it stays on Spotify; the account menu brings it back).
- **Artists you follow** with suggestions from what you play.

### Player and mini player
- Play/pause, next/previous, shuffle, repeat (all/off), seek, queue (reorder), speed (0.5×–2×), sound/equalizer, devices, share card, lyrics (with karaoke and share lyric), sleep timer, alternative stream, add to playlist, go to album/artist, download.
- **Like**: tap to like (petals fall, button fills, gold *Liked* tag pops), tap again to unlike. **Double-tap the cover** to like with a heart. Likes go to Spotify too.
- Player and queue open as a layer over the page you came from, so pulling down / back / the down arrow slide it into the mini player smoothly.
- Mini player: like with petal rain, swipe to skip, small equalizer on the cover while playing.

### Liked Songs
- One list: Spotify + YouTube Music likes (each song once) + songs liked in Aalap. Search, and filters by Spotify / YouTube Music / your top artists. Pull down closes the page.

### Profile and Settings
- Liked / Artists / Plays counts; Connected services (Spotify, YouTube Music with *Sync now*, JioSaavn and Gaana as backup sources, Apple Music import).
- **Your taste**: artists you love, languages, plays and how much of each song you finish; updates by itself.
- Appearance: System / Light / Dark. Offline mode, auto-download liked songs, go offline automatically, audio quality, playback options, song videos, log out.

### What updates, and how often (verified)
| What | When | Seen on the phone |
|---|---|---|
| Library sync (Spotify likes and follows, YouTube Music likes, artists, history) | every hour, even when Aalap is closed | job scheduled; YouTube synced "3 minutes ago" |
| Your taste | after every play (rebuilt at most every 30 s), on every like/unlike, after each hourly sync | 2 Diljit Dosanjh plays → his weight went 0 → 0.31; profile rebuilt the same minute |
| Spotify top artists (taste signal) | daily | was never saved before (bug 1); now saved |
| Quick picks | daily and on every refresh | date stamp 23 Oct; refresh gave a new set |
| Aaj ka Aalap 30 songs | daily | 23 Oct, 8+7+9+6 |
| Daily Mixes, new releases, Library albums | daily | all stamped 23 Oct |
| Trending searches | daily, when Search opens | refreshed to 23 Oct on opening Search |
| Charts | daily | stamped today |
| Mood stations, Your Mix | every 6 h / 3 h | — |
| Poster design | midnight | — |

---

## 3. Test results

✅ = tested on the phone and working (after fixes where noted).

### Home
- ✅ All sections present and in order.
- ✅ Pull-to-refresh and double-tap Home both give new Quick picks (generation 30 → 31 → 32).
- ✅ Poster opens today's 30 (8 trending, 7 top, 9 taste, 6 liked); shuffle changes the design and remembers it after restart.
- ✅ Mood station plays (Romantic → *Nafrat*); equalizer shows on the mini player.
- ✅ Top 50 India / Global open. ⚠→✅ Trending India showed the wrong list (bug 2).
- ✅ Daily Mix plays. ⚠→✅ mini player flashed "paused" while loading (bug 3).
- ✅ Artist radio plays (Arijit Singh → *Apna Bana Le*). ⚠→✅ New releases showed compilations (bug 24).

### Search
- ✅ 10 trending searches refreshed today; typed search works; filters work; no duplicates; tapping plays.
- ⚠→✅ An artist with no name appeared (bug 4).

### Library and playlists
- ✅ Tiles, filters, grid/list, album rotation, followed artists and suggestions.
- ✅ Create "QA Test" → add a song → rename to "QA Done" → delete (confirmation shown). Your playlists are back to how they were.
- ✅ Long-press remove / restore Spotify playlist (tested earlier this session).
- ⚠→✅ "1 songs" (bugs 5, 7), unhelpful empty Playlists message (bug 6).

### Artist and album pages
- ✅ Artist page (verified badge, listeners, Following, popular songs, releases); album page plays.
- ⚠→✅ Likes there didn't reach Spotify (bug 8); white tick (bug 9); "Album : " blank (bug 10).

### Player
- ✅ Play, pause, next, previous (screen and system media controls agree), shuffle on/off, repeat on/off, queue, speed, sound/equalizer, sleep timer, add to playlist, alternative stream, share sheet (closed without sending), go to album, go to artist, lyrics + karaoke, close by pull/back/arrow.
- ✅ Close smoothness on the release build: worst frame 27–32 ms.
- ⚠→✅ Controls had no screen-reader labels (bug 11); empty "On Spotify" heading (bug 12).

### Liked Songs, History, Downloads, Local files
- ✅ Liked count 195 matches Library and Profile; filters and search work.
- ✅ History (minutes, top artists/songs), Downloads (1 downloaded song), Local files (import screen).
- ⚠→✅ History showed "ARIJIT SINGH" (bug 14); Downloads "1 songs" (bug 15); no message for an empty Liked list (bug 17).

### Profile, Settings, themes
- ✅ Connected services, Sync now, Your taste, Appearance.
- ✅ **Light theme** checked on Home, Search, Library, player (player stays dark on purpose); **Dark** restored.
- ⚠→✅ Plays 320 vs 327 (bug 13); switches unlabeled and labels not tappable (bug 20).

### Background, taste and sync
- ✅ Hourly sync scheduled; YouTube Music synced recently; play history recording (278 → 330 plays).
- ✅ Taste updates from use (Diljit Dosanjh test above).
- ⚠→✅ Spotify top-artists refresh never saved (bug 1); likes didn't refresh taste at once (bug 21).

### Offline
- ✅ Offline mode: Home shows "You're offline" with your downloads, **downloaded song plays**, "Go online" works. Offline mode is off again.
- ⚠→✅ False "offline" after a network switch (bug 19); auto-offline was off by default (bug 18).

---

## 4. Scenarios checked (what happens when things go wrong)

| Scenario | What Aalap does now | How checked |
|---|---|---|
| No internet / Offline mode | Switches to downloaded music, shows a clear banner, "Go online" button | on phone (Offline mode) + code (auto-offline now on by default) |
| Network switches (Wi-Fi ↔ mobile) | Re-checks after 2 s and 8 s and on every return to the app, so it doesn't get stuck "offline" | reproduced the stuck state, fixed, code |
| Spotify unreachable / login expired | Library still shows likes, downloads, history, Aalap playlists and artists, with "Couldn't reach Spotify · Try again" | code (bug 22) |
| Spotify rate limit (429) | Fails fast; taste uses your library instead; new releases retry later | seen in logs; fixed bug 1 |
| YouTube Music signed out | Liked shows Spotify + Aalap likes; YouTube sync skipped | code |
| Song can't stream | Skips to the next playable song | code |
| Phone call during playback | Music pauses (audio focus); unplugging headphones pauses | two real calls during testing + code |
| App left open past midnight | Poster, Quick picks, today's 30 and mixes roll over | code (bug 16) |
| New user with nothing liked | Liked page explains how to like songs | code (bug 17) |
| Empty playlists / albums filter | Friendly message with what to do | on phone (bug 6) |
| Log out | Saved login, live token, cached feeds and welcome step cleared; your plays, Aalap likes, downloads and playlists stay | code (bug 23); not pressed on your phone |
| Switching Spotify accounts | First sync replaces the old account's likes, follows and taste | code |
| Large system font | Poster grows instead of overlapping | code |
| Light / dark theme | Both readable; player always dark | on phone |

---

## 5. Bugs found and fixed

| # | Area | Bug | Fix |
|---|---|---|---|
| 1 | Taste | Daily Spotify top-artists refresh **never saved anything**: Spotify answers HTTP 429 | Falls back to your top artists from Spotify + YouTube Music likes and follows; saved daily |
| 2 | Charts | "Trending India" opened Spotify's weekly *Top Songs – India* (Spotify reused the old Viral 50 id); trending tags used it too | Now Spotify's *Trending Now India* (75 songs) for the chart, tags and Aaj ka Aalap |
| 3 | Mini player | Showed "paused" for 2–3 s whenever a new song loaded | The internal pause during a track change no longer reaches the button |
| 4 | Search | Nameless artist (and possibly songs/albums) in results | Entries without a name are skipped |
| 5 | Text | "1 songs" on Library tiles, Liked, Local files, Settings; "1 plays" in Wrapped/History | One helper writes "1 song / 2 songs" everywhere |
| 6 | Library | Empty Playlists said "Nothing matches that." | "No playlists yet. Tap + at the top to make one." (and an albums version) |
| 7 | Playlists | Header "1 songs · made in Aalap" | Correct count |
| 8 | Sync | Likes on **artist pages, album pages and the Liked list** never reached Spotify (and the hourly sync could undo them) | All three mirror to Spotify like the player does |
| 9 | Artist page | Liked tick white, saffron everywhere else | Saffron |
| 10 | Album page | Header "Album : " with nothing after | "Album · 2024 · 12 songs" (missing parts left out) |
| 11 | Accessibility | Player buttons (close, more, shuffle, previous, play/pause, next, repeat, devices, share) had no labels | Labelled for TalkBack |
| 12 | Saved in sheet | "On Spotify" heading with nothing under it | Hidden when you have no Spotify playlists to add to |
| 13 | Profile | Plays 320 at the top, 327 in Your taste | Both use the engine's count |
| 14 | History | Top artist shown as "ARIJIT SINGH" | Prefers the normal spelling |
| 15 | Downloads | "1 songs • available offline" | Fixed |
| 16 | Home | Left open past midnight, Quick picks / today's 30 stayed on yesterday | 5-minute check rebuilds daily sections when the window changes |
| 17 | Liked | New user saw a bare "0 songs" | Explains how to like songs |
| 18 | Offline | With no internet the app kept trying to stream (auto-offline off) | Go offline automatically is on by default |
| 19 | Offline | App got stuck showing "You're offline" while connected | Debounced detection + re-check on return |
| 20 | Accessibility | Settings / Sound / Profile switches unlabeled; tapping the words did nothing | Whole row is the switch, read as "Offline mode, switch, off" |
| 21 | Taste | Liking/unliking didn't refresh the taste straight away | Refreshes on every like and unlike |
| 22 | Library | If Spotify couldn't be reached, the whole Library vanished | Shows everything on the phone plus a "Try again" note |
| 23 | Log out | Two Log out buttons behaved differently; the live Spotify token stayed in memory | One complete log-out used by both |
| 24 | Home | "New from your artists" listed compilations/mixtapes ("Happy Vibes", "The Winter Playlist") | Compilations and label mixtapes left out |

---

## 6. Not run on the phone, and why

| Item | Why | Covered instead by |
|---|---|---|
| Pressing **Log out** and logging back in | It would sign you out; signing in needs your password, which I don't enter | Code review and fix (bug 23) |
| Airplane mode / turning Wi-Fi off | Changing phone system settings | In-app Offline mode on the phone + code (bugs 18, 19) |
| Downloading a song | Writes a file to your phone | Existing download plays offline |
| Apple Music import, sharing Wrapped | Need your files / would share content | — |
| Hourly sync firing while the app is closed | Takes up to an hour | Job confirmed scheduled; YouTube sync timestamp recent |

---

## 7. Known limits and recommendations

1. **Spotify rate limits (429).** Spotify throttles some requests (top artists, top tracks) for web-player tokens. Aalap now degrades gracefully, but a few Spotify-only rows can be missing for a while after heavy use.
2. **Habit score.** The "your habit" part of the recommendation score only counts once you've played the same song-after-song pair before, so it starts at 0 for new pairs and grows with use.
3. **Signing key.** The release build is signed with this computer's debug key (no `keystore.properties`). Fine for your phone; for sharing or the Play Store, create a release keystore. Updates must be signed with the same key.
4. **App size.** Minify is off (15 MB). Turning on R8 would shrink it, but needs keep rules tested first.
5. New releases cached today still include "Diwali Party Bangers"; the filter for it applies from tomorrow's rebuild.

---

## 8. Your phone after testing
Theme **Dark**, Offline mode **off**, shuffle and repeat **off**, music **paused**, test playlist **deleted**, likes unchanged. Two Diljit Dosanjh songs were played for the taste test, so they're in your history. The installed app is **Aalap 3.0.0 (release)**.
