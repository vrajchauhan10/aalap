# Aalap changelog

Every change to the app raises the version: each change bumps the minor number
(3.1 → 3.2 → 3.3 …) and the build number (`versionCode`) goes up by one, so phones
always see it as an update. Newest first.

## 5.7.0 — 25 Oct 2026 (build 59)
- **"Hey Aalap" works over any app, and in Aalap over the player.**
  - The bar now shows over other apps when Aalap is closed and music plays on. Samsung
    pauses the screen ticks of apps in the background, so the bar was added but never drawn;
    it now runs on its own clock.
  - It shows over the full player too, which sits in a window of its own above Aalap's screen.
  - The request after "Hey Aalap" is heard: one listening session stays open from the wake
    words to the request, and its end is found even when the speech engine doesn't report it.
    "Hey Aalap, play Kesariya" said in one breath works too.
  - Hears you over loud music: the mic records through the phone's echo canceller on the
    speech source (the call source this Samsung uses was all but silent), with "Aalap"
    taught to the speech engine.
  - It no longer switches itself off when Android starts it twice as Aalap opens, and it
    recovers by itself if the mic stream or the speech engine stops.
- **Hold a volume key to ask Aalap.** Hold volume up or down for a moment, over any app, and
  the bar comes up listening; a quick press still changes the volume. Works with "Hey Aalap"
  off. Switch it on once in Accessibility (Profile → Voice has the button).
- **Side key:** Samsung keeps press-and-hold of the side key for Bixby or the power menu,
  but double press can open "Ask Aalap" (Settings → Advanced features → Side button →
  Double press → Open app → Ask Aalap).
- A song asked for while Aalap was closed now gets its notification and lock-screen controls.
- **Voice bar:** the orange edge fills a little slower, from both ends meeting at the top,
  then fades away; the sound bars along the bottom stay and move with your voice.
- **"Play Hindi songs"** (or Punjabi, Tamil, English…) plays a mix of your own songs in
  that language in under a second, topped up with popular ones; it used to search for a
  song called "Hindi songs".
- **The card always shows the song that plays.** Aalap checks the song really started,
  and tries once more if it didn't (before, a song that failed to load skipped to the next
  one while the card still showed the first).
- **Blend:** "Your score this week" is a bar per day, Monday to Sunday, with today
  highlighted and the start, best and today's score below (no more empty black chart).

## 5.6.0 — 25 Oct 2026 (build 58)
- **Aaj ka Aalap posters: the V shows properly in every design.** Checked all 100 on the
  phone; 23 had a V with only one arm showing, an arm hidden under the words panel, or
  too faint to see.
  - The V now sits further right, clear of the panel's curved edges.
  - Designs whose pattern runs one way (Batik parang, Harris Tweed, Lahariya, Ajrakh jaal,
    Sashiko, Arabesque…) now pick the pattern's own pieces nearest both arms of a V, so
    the V is always two whole arms.
  - Faint ones are clearer: Madhubani, Kalamkari, Kuba, Mola, Mirror work, Persian carpet,
    Lotus mandala, Argyle, Azulejo, Khatamband, Paisley.

## 5.5.0 — 25 Oct 2026 (build 57)
- **Blend:**
  - Petals fall the first time you open it, and again when you come back after a while.
  - Your Blends come first on the screen, then making a new one.
  - A "How Blend stays in sync" card says what happens on both phones and shows when
    you're connected.
  - When a friend adds your code you get a notification ("Meera added your code: your
    Blend is ready"), which opens Blend.
- **Profile:**
  - Your photo on Home now opens the same menu as in Library (profile and settings,
    Blend, listening history, share Aalap) instead of jumping straight into settings.
  - The menu has "Blend with a friend".
  - The Profile page starts with four quick tiles (Blend, Wrapped, History, Share
    Aalap), then your services and taste, then everyday settings (playback, quality and
    crossfade, voice, recommendations, sound); appearance and advanced options are lower.
- **Song videos from YouTube now find film songs** uploaded by the music label rather
  than the singer (e.g. Mashooqa).
- **"Hey Aalap":**
  - **The voice bar comes up wherever you are**, over any app, when you allow "Display over
    other apps" (Profile → Voice has the button). Without it, the notification shows what's
    happening.
  - **A lighter voice bar:** a slim card with a thin orange edge that traces round it in
    a quarter of a second and pulls back as fast; a small orange dot breathes with your
    voice, one line shows your words, then the song.
  - **Requests from the background now play:** the music is turned right down instead of
    paused while it listens, and the listener waits a minute before stopping, so Aalap
    keeps its network (Android cuts it for idle background apps, e.g. in Battery Saver).
    Tested: "Hey Aalap" … "play Believer by Imagine Dragons" with Aalap minimised.
  - Understands more ways it's heard ("hal aap", "hey alarm", "all of play…"), and starts
    listening again at once after each round so words aren't missed.
  - If a request finds nothing, the music comes back to full volume and the bar says so.

## 5.4.0 — 25 Oct 2026 (build 56)
- **"Hey Aalap" / "OK Aalap"** (Profile → Voice, off by default). Say it, then a song,
  singer or mood, or all at once ("Hey Aalap, play Kesariya"). It listens while Aalap is
  open, and keeps listening in the background while Aalap's music plays; it stops when
  Aalap is closed and nothing plays. With Aalap open the voice card comes up; in the
  background the music pauses, Aalap hears the request and plays it. It uses the phone's
  offline speech engine (nothing is sent anywhere), steps aside for calls, WhatsApp voice
  notes and any other recording, and a notification shows while it listens (with
  "Turn off").
- **Song videos work whatever your Spotify setting.** Spotify only sends its Canvas
  videos to accounts that have Canvas on in the Spotify app. When it sends none, Aalap
  now plays the song's official music video from YouTube behind the player (muted, in
  step with the song, 480p or less), through the same YouTube pipeline as the audio. So
  Aalap's own "Song videos" setting decides.
- **Voice card:** it slides up from the bottom and its border draws itself once, quickly:
  two lines leave the middle of the bottom edge in opposite directions and meet at the
  top. While you speak only the bottom edge moves, with your voice; while Aalap thinks
  the border glows softly.

## 5.3.0 — 25 Oct 2026 (build 55)
- **Aaj ka Aalap posters: the maker's V is now part of each pattern**, never drawn on
  top. In 28 designs that showed a plain V (a letter, a glyph, lines across the art) it
  is now made from the design's own pieces in its own colours: two petals of a mandala,
  five gold stars of the zellige, three white banners of the papel picado, brass pins in
  the khatamband, dyed tufts in the Beni Ourain rug…
- **20 new fine patterns from around the world** (100 in all): Sakura komon, Ichimatsu,
  Uroko (Japan), Pois (France), Iznik tulips (Türkiye), Delft tiles (Netherlands),
  Pysanky stars (Ukraine), Talavera (Mexico), Liberty florals (England), Selbu roses
  (Norway), Xiangyun clouds (China), Calçada (Portugal), Roman mosaic (Italy), Tibeb
  (Ethiopia), Suzani (Uzbekistan), Matyó roses (Hungary), Wycinanki (Poland), Dancheong
  (Korea), Batik truntum (Indonesia), Adire (Nigeria). Each hides its V the same way.
- **Voice card:** its edge lights up. While you speak a band of light runs round the card
  (longer the louder you speak) and the bottom edge fills out from the middle with your
  voice; while Aalap thinks the border fills round; when it finds the song the ring closes.
- **Sign-in screen:** a slowly turning mandala, the parts arriving one by one, what Aalap
  does at a glance, a note on privacy, and Continue lights up once the details are right
  (the keyboard's Done also continues).
- **Song videos:** Spotify changed where song videos come from; Aalap now asks the new
  way, and asks again signed out when your Spotify account has videos turned off, so
  Aalap's own "Song videos" setting decides. (Right now Spotify isn't sending videos to
  any app outside its own, so most songs show the artwork; they come back by themselves
  when Spotify sends them.) A video that fails no longer leaves a black screen.
- **Security:**
  - The hidden Spotify player could be inspected over USB in release builds, and it holds
    your Spotify login; debugging is now only in test builds.
  - Your YouTube login, the Blend sign-in and the in-app browser's cookies are no longer
    included in phone backups or transfers (the YouTube login moved to its own file).
  - Other apps can still play / pause / skip Aalap (as with any music app) but can no
    longer read your library (Liked Songs, playlists, mixes); only Android itself and car
    / assistant apps can.
  - The shared list of FLAC servers is only used for secure (https) addresses.
  - A Blend code can't be crafted to use huge amounts of memory.

## 5.2.0 — 25 Oct 2026 (build 54)
- **Blends are shared between both phones** (free Firebase, no account to make: each
  phone signs in anonymously by itself). The same 30 songs on both phones, made fresh
  every day by whichever phone opens Aalap first. A friend who makes a Blend from your
  code finds it in their Library, and it shows up in yours by itself.
- **Listening raises your Blend score, on both phones.** Each Blend song either of you
  hears for 30 s adds a point; a song you've both heard adds two more; points last 7
  days. Your friend's listening arrives when Aalap opens and every minute while a Blend
  is on screen.
- **Library → Blends:** a new tab next to Playlists, Albums and Artists (and "Your
  Blends" under All). A Blend's page shows the score ring, taste match plus what
  listening added, the score day by day, this week's songs (you, your friend, both),
  how many more to reach the next level, and today's 30 marked Yours / theirs / New with
  who has heard each.
- Blends no longer appear in Playlists (Playlists are only the ones you make); the old
  automatic Blend copies were removed.
- Blend screens close with a pull down, and carry the "crafted by V" mark.
- Only the two people in a Blend can read or change it (Firestore security rules).
  Tested with a second phone simulated against the real database: same songs on both,
  listening counted both ways, daily refresh, a friend's Blend appearing by itself, and
  a stranger refused.

## 5.1.0 — 25 Oct 2026 (build 53)
- **Voice, rebuilt.** The mic now listens inside Aalap: a card shows the words as you
  speak, then "Finding the best match…" for a moment, then the song it's playing. It
  understands film names ("Saiyaara Ek Tha Tiger" plays the 2012 song; just "Saiyaara"
  plays the most played one), prefers the original over remixes, lofi and covers, and
  checks the recogniser's other hearings too.
- **The mic never disturbs other apps.** During a call, a WhatsApp voice note or any
  other recording, Aalap won't open the mic and says why. It lets go of the mic the
  moment you stop speaking, on cancel, after 12 s at most, or when you leave Aalap.
  Android asks once for microphone permission.
- **"Aaj ka Aalap" by voice works however it's heard** ("aaj ka alap", "aj ka aalaap",
  "today's aalap"…), while "Aaj Ki Raat" still plays the song.
- **Blend, redesigned.** Its own screen (Home → Blend, or Settings → Share):
  - copy or share your taste code; a friend's code fills in by itself when you open Blend
    after copying it, or when their message is shared to Aalap;
  - a match score with its own animation for each level: Opposites attract, Common
    ground, Great match, Music twins, Soulmates;
  - Save Blend, Play, and Share score (a picture card with your score, which also carries
    your code so friends can blend back);
  - saved Blends get a fresh mix every day, and a newer code from the same friend
    updates your Blend with them.
- **Aaj ka Aalap posters:** a new poster every time you open Aalap. All 80 designs show
  before any repeats, and a poster never comes back within 50 opens.

## 5.0.0 — 24 Oct 2026 (build 52)
- **Aalap listens to the music.** While a song plays, Aalap measures its tempo and
  energy on the phone (nothing is uploaded) and remembers them. Recommendations use the
  real sound, not just labels: a calm song isn't followed by a loud one.
- **Smooth flow.** The next song keeps close to the last one's energy and tempo, so the
  queue doesn't jump from a slow ballad to a dance track.
- **Learns from how long you listen.** Hearing a song to the end counts more than half
  of it; skipping in the first seconds counts against it more than a late skip.
- **"More like this" / "Less like this" / "Don't play this song" / "Don't play <artist>"**
  in every song's menu and the player menu. The queue changes right away; it's
  remembered, and Settings → Recommendations can clear it. A blocked artist doesn't play
  in duets or features either.
- **Discovery dial** (Settings → Recommendations): Familiar, Mostly familiar, Balanced,
  Explore, Surprise me.
- **Language mix** (Settings → Recommendations): choose Off / Some / Lots per language
  (Hindi, Punjabi, English…). Languages you ask for come in even when the song you
  started with is in another one, in the same mood.
- **Re-discover and surprises.** Old favourites you haven't played in a while come back
  now and then, and sometimes a song by an artist new to you that fits the mood.
- **Ask Aalap.** A mic button on Home, an "Ask Aalap" Quick Settings tile and an app
  shortcut (long-press the icon): say a song, artist or mood. The first request right
  after the app opens no longer fails.
- **Blend** (Settings → Share): share your taste as a code, and make a playlist with a
  friend's code: your songs that suit them, theirs that suit you, and new songs you'd
  both like. Free and private: no server or account, and no history is shared.
- Tested: 56 automatic tests (feel, flow, dial, language mix, feedback, re-discover,
  tempo detection, Blend codes) and on the phone (tempo of real songs, language mix,
  blocking, Blend, the menus, mic, tile and shortcut).

## 4.4.0 — 24 Oct 2026 (build 51)
- **Ready for Gemini (Android AppFunctions).** Aalap now tells Android what it can do
  for an assistant: play music (a song, an artist, a mood, Liked Songs, Aaj ka Aalap, or
  carry on), pause, resume, next, previous, what's playing, and like the current song.
  Android 16 lists these next to Samsung's own apps' actions, and they work while Aalap
  is closed. Gemini already uses this system for Google's and Samsung's apps; Google is
  opening it to other apps through an early access programme, so Gemini will call
  Aalap's actions once Aalap is let in. Tested on the phone the way an assistant calls
  them: every action worked, including from the home screen.
- **Fixed a crash when music started very quickly from the background** (resume from
  the widget, notification, headset or an assistant): the "Starting your music…" helper
  could be stopped before Android had made it a foreground service, and Android closed
  the app. Tested: pause and resume three times in a row, no crash.
- Voice requests: a request that matches nothing now says so instead of playing an
  unrelated song, and small spelling differences still find the song ("kesriya arijit").
- Builds with the Android 16 SDK (compileSdk 36) and Android Gradle Plugin 8.9.1.

## 4.3.0 — 24 Oct 2026 (build 50)
- **Play Aalap with your voice.** "Hey Google, play Kesariya on Aalap" (also Gemini and
  Bixby, on the phone or in the car): Aalap opens on the player and plays it. It
  understands songs ("Kesariya by Arijit Singh"), artists ("play Arijit Singh songs"),
  "my liked songs", "Aaj ka Aalap", moods ("party songs", "some lofi", "bhajans",
  "workout music") and "play music" (carries on). With the screen off it plays without
  opening the app. When several songs share a name it picks the exact title, the artist
  you said, or the one you already play. Also `aalap://play?q=…` for Routines and shortcuts.
- **Opening the app from the notification no longer jumps back a song.** The player's
  cover pager remembered an old page and, when the app came back, played that page's
  song. It now always starts on the song that's playing, and only a swipe of your finger
  changes the song. Tested: 10 reopenings with the song changing each time, no jumps
  (the old version jumped back).
- **Tapping Library (or any tab) no longer skips a song.** The full player also used to
  open by itself when a song ended, which could jump songs; it doesn't any more.
  Tested: 8 rounds of next song, open/close player, Library, Home: the song never changed.

## 4.2.0 — 24 Oct 2026 (build 49)
- **Recommendations follow the feel of what you chose, not just your likes.** Every song
  now has a feel (energetic, party, happy, romantic, sad, chill, devotional, focus), a
  tempo, a language and a style (lofi, rap, sufi…), learned from its title, the mood
  stations, its artist and what plays next to it. What comes next has to fit the song you
  chose and the session so far: start with an energetic song and the queue stays
  energetic. Tested on 14 songs across genres on the phone: songs that keep the feel
  went from 37% to 85%; Millionaire is no longer followed by ten ballads.
- **Your behaviour, trends and all-time favourites count, likes count less.** The score
  mixes what the world plays next, what fits the session, the artists and feels you
  actually finish (vs skip), what you play at this time of day, your own habits and
  today's charts. Liked songs are capped at about a third of a queue (they were 35% on
  the phone test, now 12%); about a third are songs you know, the rest new to you.
- **Skips steer the queue.** Skip a song Aalap queued and the queued songs too much like
  it (same artist, or same feel and tempo) are dropped and replaced.
- **Tapping a song in Liked Songs or a playlist** plays that song, then a mix of
  recommendations that fit it with songs from the list mixed in at random (about a
  third), instead of the rest of the list in order. Also in Downloads and on artist
  pages. The list's Play and Shuffle buttons still play the whole list; albums play in order.
- **Loop modes.** Tap the loop button: Off → loop the playlist → loop this song. Press
  and hold for all five: Off, this song once more, this song on repeat, this playlist
  once more, this playlist on repeat. A badge shows ∞ or 1×. Looping a playlist starts
  it over at the end instead of adding recommendations.
- Test report: `docs/recommendation/Report-4.2.md`.

## 4.1.0 — 24 Oct 2026 (build 48)
- **Aalap now has its own app ID, `com.music.aalap`.** Phones see it as a new app: it
  installs next to the earlier build instead of updating it, and starts fresh (sign in
  again; likes, history and downloads from the earlier build don't carry over). Remove
  the earlier build once you're set up.

## 4.0.0 — 23 Oct 2026 (build 47)
- **A V in every Aaj ka Aalap poster.** Each design now carries the maker's V, made from
  its own pieces rather than stamped on top: two marigold petals at the mandala's heart,
  sashiko waves stitched in orange, one gold dip in the Bidri inlay, a V of mirrors,
  beads, checks or stars in the grid patterns, two raised arms in the Warli dance, and so on.
- **30 new designs (80 in all):** Paisley, Buti, Ajrakh jaal, Sanganeri, Kantha, Nakshi
  kantha, Rangoli, Lotus mandala, Pochampally ikat, Toda embroidery, Tumpal border,
  Bargello, Granny square, Basketweave, Herringbone parquet, Gingham, Tartan, Argyle,
  Chevron knit, Asanoha sashiko, Shweshwe, Adinkra, Girih, Celtic knotwork, Arabesque,
  Quatrefoil, Terrazzo, Papel picado, Paj ntaub and Vyshyvanka. Each has its V too.
- **Aalap's own name throughout the code** (package `com.music.aalap`). Exported songs go
  to Music/Aalap.

## 3.16.0 — 24 Oct 2026 (build 46)
- **Smaller signature on the music player** (about 30% smaller), so it stays out of
  the way of the controls. Other pages keep the regular size.

 — 24 Oct 2026 (build 45)
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
