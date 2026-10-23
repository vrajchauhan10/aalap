# Aalap changelog

Every change to the app raises the version: each change bumps the minor number
(3.1 → 3.2 → 3.3 …) and the build number (`versionCode`) goes up by one, so phones
always see it as an update. Newest first.

## 3.6.0 — 23 Oct 2026 (build 36)
- **Share Aalap opens instantly.** The app file is now prepared as soon as Profile
  opens (once per version), so tapping Share Aalap shows the share sheet in under a
  second; before, it copied the 15 MB file first and showed nothing for a few
  seconds. While preparing, the row shows a spinner and "Getting the app ready…", and
  a message appears if sharing fails.

 — 23 Oct 2026 (build 35)
- **Share Aalap.** In Profile (Share section) and Library's account menu: sends the
  app installed on your phone as "Aalap-3.5.0.apk" through WhatsApp, Drive, Quick Share
  or any app. The other person taps the file to install it.

## 3.4.0 — 23 Oct 2026 (build 34, shipped inside 3.5.0)
- **Your library comes back when you sign in again.** Logging out saves your library
  on the phone under your email and clears it, so the next person starts fresh. Signing
  back in with the same email restores everything: likes (including songs liked only in
  Aalap), Aalap playlists, listening history, recently played, followed artists, removed
  playlists and your taste. Listening stats were already kept per email. Downloads stay
  on the phone for everyone. Before, logging out kept the old account's email, so the
  next person to sign in took over its listening history.

 — 23 Oct 2026 (build 33)
- **Tapping the notification no longer crashes the app.** Opening Aalap from the
  notification (or a widget) crashed in the opening animation, because Android shows
  that splash without an icon; now it opens straight back where you were.
- **New widgets, plain black.** Large widget: the cover on the left; song, progress
  (tap to seek) and like · previous · play · next on the right; gold heart when liked.
  Small 2×2 widget: cover and song on top, then previous · play · next (previous is new).
  The craft-pattern backgrounds are gone. Widgets placed before keep their old size
  until resized.

## 3.2.0 — 23 Oct 2026 (build 32)
- **History shows real listening time.** A listening clock counts only while music is
  actually playing (paused, loading and stopped time don't count) and saves it with
  each song every 15 seconds. History's minutes, top artists and top songs use it.
  Plays from before this version keep the old estimate.
- **Removed "Show hidden playlists"** from Library's account menu. Spotify playlists
  removed from Aalap stay on Spotify and stay out of Aalap.

## 3.1.0 — 23 Oct 2026 (build 31, shipped inside 3.2.0)
- **Your name, not an account ID.** Spotify accounts without a display name showed
  their raw ID ("31rzydmapjxv3a3…") in the Home greeting; now the name you entered in
  Aalap is used. Profile and "Connected as" handle a missing name too.
- **Aaj ka Aalap always has 30 songs** when enough exist: with little listening history
  it tops up from the remaining taste, trending, top and liked songs (was 24).

## 3.0.0 — 23 Oct 2026 (build 30)
Full QA pass; 24 bugs fixed. Details in `docs/QA-Report-Aalap-3.0.pdf`. Highlights:
- Daily Spotify taste refresh works again (falls back to your library on HTTP 429).
- Trending India chart uses Spotify's *Trending Now India*.
- Likes from artist pages, album pages and the Liked list reach Spotify.
- Mini player no longer flashes "paused" while a song loads.
- Library stays usable when Spotify can't be reached; offline detection fixed and
  automatic offline on by default.
- Home rolls over to the new day while left open; new releases skip compilations.
- One complete log-out; accessibility labels on player controls and settings switches.
- 50 handmade Aaj ka Aalap poster designs with a shuffle button; smooth player close;
  marigold like animations; real-time recommendation trace (debug builds).
