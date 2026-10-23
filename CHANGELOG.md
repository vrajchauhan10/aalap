# Aalap changelog

Every change to the app raises the version: each change bumps the minor number
(3.1 → 3.2 → 3.3 …) and the build number (`versionCode`) goes up by one, so phones
always see it as an update. Newest first.

## 3.15.0 — 24 Oct 2026 (build 45)
- **"crafted by V" at the end of more pages:** the music player (bottom-right, under
  the share and queue buttons), the "Connect your music" sign-in page (under Start
  listening), Library and Liked Songs. Already on Home, the welcome screen, Profile and
  the opening screen.

 — 24 Oct 2026 (build 44)
- **Signature restyled.** "crafted by" in Great Vibes and the "V" in Parisienne (both
  free Google fonts, bundled in the app), lighter, and in the bottom-right corner on the
  welcome screen, Profile and Home. On the opening screen Android only allows it centred
  under the logo, so it stays there in the same fonts.

 — 24 Oct 2026 (build 43)
- **Wrapped and History now match.** Wrapped counted every song you started as if you
  listened to all of it (skips too, ~3.5 min for unknown lengths), while History counts
  the real listening time. Both now use one shared calculation: real listening time, and
  skipped songs (under 30 s) don't count as plays or in the top songs and artists.

 — 24 Oct 2026 (build 42)
- **Your signature.** A light, handwritten "crafted by V" on the opening screen (under
  the logo), the welcome screen, the end of Home, and in Profile under the version.

## 3.11.0 — 24 Oct 2026 (shipped inside 3.12.0)
- **Profile photo.** Tap your photo in Profile (or its pencil badge) to pick one from your
  gallery; tap again to change or remove it. Saved inside Aalap for your account (it comes
  back when you sign in again) and shown in Profile, on Home and in Library.

## 3.10.0 — 24 Oct 2026 (shipped inside 3.12.0)
- **Sign-in screens are stable on every phone.**
  - Spotify / YouTube Music cards: the "Required" / "Recommended" tag is on its own line,
    so it no longer splits in two on narrow phones or with large text.
  - "Start listening" stays fixed at the bottom of the screen instead of far below.
  - Google sign-in (inside Spotify and YouTube Music): pages that open a new window
    ("Continue with Google", "Forgot password?", "Create account") now open; password
    recovery and sign-up open in the phone's browser, where codes and SMS work.
  - Google's pages use their normal phone layout (they were zoomed out, with Next far
    below), and sign-in pages move above the keyboard.

 — 23 Oct 2026 (build 39)
- **Play from the widget works when Aalap is in the background.** The phone blocks
  background apps from the internet (battery saver, background limits), so the song
  couldn't be found and you got "Couldn't find a playable stream". Now pressing play,
  next or previous on the widget, notification or headset briefly runs a small
  "Starting your music…" foreground helper, which lifts the block; the widget also stays
  connected until the music actually starts (it used to disconnect after 1.5 s and the
  player got shut down mid-start).
- **Large widget is 4×2 like other widgets** (it asked for too much height, so Samsung
  placed it 4×3) and the cover is always square. Widgets already placed keep their
  size until resized.
- Losing and regaining background internet no longer marks Aalap as offline.

 — 23 Oct 2026 (build 38)
- **Widgets: proper spacing and wording.**
  - Large: the song, artist, progress and controls sit together as one centred block
    next to the cover, with even gaps (no big empty space under the artist); larger
    text and buttons.
  - Small 2×2: cover top-left, song and artist on one line each, controls along the
    bottom (the title no longer wraps; no empty space at the top).
  - Times show only when the song's length is known, and both use the same style
    (01:30 / 04:29) instead of a lone "00:00". A paused song now shows where it stopped.
  - Idle wording: "Tap play to start your music".

 — 23 Oct 2026 (build 37)
- **Small widget fits how it's placed.** At 2×2 it's the square design (cover and
  song, then previous · play · next). Placed wide and one row tall (like widgets added
  before 3.3), it becomes a one-row black bar: cover, song, then previous · play · next
  on the right, instead of the square design squeezed into a strip. Resizing switches
  between the two.

 — 23 Oct 2026 (build 36)
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
