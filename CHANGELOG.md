# Aalap changelog

Every change to the app raises the version: each change bumps the minor number
(3.1 → 3.2 → 3.3 …) and the build number (`versionCode`) goes up by one, so phones
always see it as an update. Newest first.

## 8.6.0 — 11 Nov 2026 (build 92)
- **The right song, the right recording.** Every stream's length is checked against the real
  song. A music video with an extra intro, a short clip or an extended upload (heard as "ends
  early", "a line plays twice", "too slow/fast", "a different song") is dropped and the same
  song plays from the next source instead.
- **Fewer songs cut off mid-play.** When a source's stream breaks, Aalap carries on with the
  same song from another source at the same spot, instead of skipping to the next song.
- **Cooler on long listens.** The player wakes far less often (a deeper audio buffer), and the
  per-song tempo measuring stops while Aalap is in the background.
- **Tells you what's warming your phone.** When the phone can't use its low-power audio chip
  (Dolby Atmos does that on Samsung), Profile says so and opens the sound settings.
- **Recommendations stay in your languages.** Punjabi singers are recognised by name, and a
  language you aren't listening to stays a rare guest in your radio and mixes.
- **Quick picks: 60% new, 40% yours.** Today's trending hits and new finds you haven't heard,
  mixed with your likes and most played; a different set on every refresh.
- **Sharp cover art on the player.** The big cover now always loads the full-size picture
  (640–1080 px) instead of a small thumbnail stretched to fit.

## 8.5.1 — 8 Nov 2026 (build 91)
- **Updates download even when GitHub's link is down.** GitHub's normal download link was
  answering with an error for every release; Aalap now tries GitHub's other route for the
  same file, so Update still works. (Your GitHub two-factor login doesn't affect updates.)
- **A yellow update box on Home and in Library.** Tap Update and it fills with yellow from
  left to right as Aalap downloads, shows the percentage, then checks it's genuine.

## 8.5.0 — 8 Nov 2026 (build 90)
- **Android Auto, redone.** Four tabs in the car: For you (Continue, Aaj ka Aalap, Drive mix,
  Quick picks, Top 50 India), Library, Moods and Radio (your top artists). The now-playing
  screen in the car has Less like this, Like and More like this.
- **New "Aalap up next" widget (4×2).** The song playing with play and next, the next two
  songs, and Aaj ka Aalap / Drive mix / Liked that start music in one tap.
- **One song row everywhere.** Albums, playlists, Liked and Downloads use the Quick picks
  look: rounded cover, short artist line and ⋮ on every song.
- **Artist and album pages open instantly** when you go back to one within 3 hours.
- **Pull a page down and it slides down** (every page), instead of sliding off sideways.
- Search keeps what you typed when you come back from an artist or album, and an old artist
  card no longer lingers while you type a new search.

## 8.4.1 — 7 Nov 2026 (build 89)
- The mini player's song title now fades smoothly instead of rolling (the roll stuttered).
- Pulling the queue down part way no longer leaves it stuck lower with a gap; it springs back.
- Closing the queue no longer lets the page show through under the player.
- "crafted by V" at the end of the queue.
- Home chips and Library tiles press in softly when tapped.

## 8.4.0 — 7 Nov 2026 (build 88)
- **Updates finish more reliably.** Android asks Google Play Protect to scan each new Aalap
  before installing it; left unanswered, it says "App not installed". The update dialog now
  says to tap "Scan app", then "Install", and if the update didn't go in, Aalap says so on Home
  and Profile and Update tries again.

## 8.3.0 — 7 Nov 2026 (build 87)
- **Never without music.** If Spotify's catalog stops answering, charts (Top 100 India and
  Global), artist pages and search come from YouTube Music instead, no login needed.
- **The song you meant comes first.** Searching a song puts the original, most played version
  on top; covers, remixes, lo-fi and slowed versions go below unless you ask for them.
- **Share a playlist.** The share button on a playlist sends the song list with an Aalap code;
  a friend shares it to Aalap and it's saved in their Library.
- **New queue.** A Now playing card with live bars, numbered songs with their length, time
  left, Shuffle and Clear, and a row that lifts as you drag it.
- **Download a whole album or playlist with one progress bar**, and a new "Download on Wi-Fi
  only" setting (also in the download dialog).
- **Motion.** Rows and buttons press in and spring back, the chosen tab pops in, the mini
  player rolls to the next song's title, and play/pause turns into each other.
- **Safety.** Aalap warns if this copy isn't the official one, and updates install only when
  signed with Aalap's own key.
- Tapping the version tag on Home opens the update check.
- Lighter on memory in long listening sessions (lookups no longer grow forever).

## 8.2.0 — 6 Nov 2026 (build 86)
- Search results show one artist and "& 3 more", so the line is never cut off next to the
  Your taste / Liked tags.
- Share artist sends "Listen to … on Aalap" with a link anyone can open, and no Spotify address.

## 8.1.0 — 5 Nov 2026 (build 85)
- **Artist page, reworked.** Popular shows 16 songs, 4 to a page like Quick picks (swipe for
  more), with plays as "347M plays". About reads as plain text with Read more, and links
  show as Instagram / YouTube / X buttons instead of long addresses. The ⋯ next to Follow
  now offers Artist radio and Share artist.
- **Search an artist, open the artist.** Searching a name shows the artist as one card; a tap
  opens their page, and they're saved in Recent searches.
- **Simpler song lines everywhere.** Albums, playlists, search, Liked, Downloads, History,
  the queue and the player show two artists and "& 6 more" instead of every credited name.
  Tapping a song on an album page opens the full player too.
- The first page no longer mentions the anonymous Blend ID.

## 8.0.0 — 5 Nov 2026 (build 84)
- **Aalap works fully without Spotify.** Top 50 India and Global, Trending, moods, artist
  pages, albums and full search (artists and albums too) now load for everyone. Spotify and
  YouTube Music only bring your likes and playlists; your taste comes from what you play,
  like and save in Aalap.
- **Easier Spotify sign-in.** Spotify's page fits on one screen (email, password and Log in
  no longer need scrolling). "New to Spotify? Create a free account" signs you up right in
  Aalap. Signed up with Google? Aalap now explains how to use your Gmail with a code, since
  Google blocks its button inside apps (that was the "no account found" problem).
- The first page (name and Gmail) fits with the keyboard open, so Continue is right there.
- **Artist top songs in Search.** Search an artist and their top 10 songs show first, like a
  playlist: Play all, or tap any song.
- **Tap a song, see the player.** Tapping a song anywhere opens the full player.
- **Download progress in the menu.** The ⋮ menu shows "Downloading… 45%" with a progress
  bar, and a red dot on Downloads (Home and Library) marks new downloads until you look.
- Sleep timer's back arrow (and Back) returns to the ⋮ menu instead of closing it.
- **Update banner on Home.** When a new Aalap is out, "Aalap X is available · Update" sits
  at the top of Home and goes away once you've updated.

## 7.14.0 — 3 Nov 2026 (build 83)
- **Song menus scroll.** The ⋮ menu in the player and on song lists now scrolls, so every
  option (down to Sleep timer) is reachable on a phone; rows sit a little closer together.
- **⋮ on Quick picks.** Each Quick pick has a menu: play next, add to queue, add to
  playlist, download, go to artist or album, share, more or less like this.
- **A new account's Library isn't empty.** Before you play or follow anything, it shows
  albums from artists in the charts, trending playlists, and Add artists.

## 7.13.0 — 3 Nov 2026 (build 82)
- **Update offered when you open Aalap.** If a newer Aalap is out, opening the app shows
  "Aalap X is available · Update now". After "Later" it waits a day before asking again on
  opening; Profile, the Home tag and the notification still show it.
- Update reminders now reach you within a day of a new release.

## 7.12.0 — 3 Nov 2026 (build 81)
- **Player edge to edge at the bottom too.** The strip of the page that still showed under
  the player, by the gesture bar, now matches the player.

## 7.11.0 — 3 Nov 2026 (build 80)
- **Update reminders.** When a newer Aalap is out, you get a notification, "Aalap X is
  available". Tapping it opens Profile with **Update now**. If you don't update, it reminds
  you again every 3 days. Aalap checks at most once a day, during its usual hourly library
  sync, so no extra battery is used.
- **Home shows it too.** The version tag next to your name turns into "· Update"; tap it to
  update.
- **Player really full screen.** The thin dark band above the player and the strip of the
  page peeking out below it are gone (Android 11 and newer).

## 7.10.0 — 3 Nov 2026 (build 79)
- **Full-screen player.** The clock and notification icons are hidden while the player or
  queue is open, so the artwork has the whole screen. Swipe down from the top to see them
  for a moment.
- **Update check answers you.** Tapping the version row in Profile says "You're up to date"
  or shows "Aalap X is available" with **Update now**.

## 7.9.0 — 3 Nov 2026 (build 78)
- **Full screen.** Aalap now draws under the status bar like other apps; the grey strip some
  phones put over the top of the screen is gone.
- **Fits your text size.** Aalap follows the phone's font size (85%–130%), so text never gets
  cut off in the mini player, chips and lists, and rows adapt to the phone's display size.
- **Updates inside the app.** Profile shows "Up to date" or "Update available". One tap
  downloads the new version, checks it's a genuine Aalap (same app, newer, signed with
  Aalap's key) and opens Android's installer; your library, likes and settings stay. The
  first time, Android asks you to allow Aalap to install updates.

## 7.8.0 — 2 Nov 2026 (build 77)
- **Songs only on YouTube play again** (game soundtracks like Shadow Fight, indie and
  regional releases). When YouTube asked Aalap to "confirm you're not a bot", Aalap mistook
  it for an age limit and threw away the links that would have worked; now it uses them.
- **Faster when YouTube is picky.** Aalap remembers, for 15 minutes, which ways of asking
  YouTube were refused on your network and skips them, so the next songs start sooner.
- **No more long silent waits.** When no source has a song, the last two checks run side by
  side for at most 10 seconds instead of up to 27.
- **Tap Search again to type.** Tapping Search while you're on it (or double-tapping it
  from anywhere) puts the cursor in the search box with the keyboard open.
- **Version on Home.** Your Aalap version is shown next to your name.

## 7.7.0 — 2 Nov 2026 (build 76)
- **Sharing opens on the first tap.** "Share Aalap" from the profile menu used to need two
  taps (closing the menu stopped the first one); now one tap opens the share sheet, and the
  app is made ready while the menu is open. Sharing a song works the same way.
- While a share is being made, the player's share icon shows a spinner, a short "Getting …
  ready to share" note appears if it takes a moment, and extra taps no longer open the share
  sheet twice.

## 7.6.1 — 2 Nov 2026 (build 75)
- The mini player's play button is round again on narrower screens (for example OPPO phones
  with ColorOS at their default display size), instead of being squeezed into a thin sliver.

## 7.6.0 — 1 Nov 2026 (build 74)
- **Continue listening.** Home shows the song you were in the middle of when Aalap was
  closed, how far you got, and picks up right there with one tap.
- **Smarter shuffle.** Shuffling Liked Songs or a playlist plays what you haven't heard in
  the last two days first; songs you just heard come later and songs you keep skipping come
  last. Nothing is left out.
- **Skips teach Aalap for longer.** A song you skip in the first 10 seconds twice stays out
  of radio and queues for three months instead of two weeks, unless you play it through.
- **Your week in music.** Wrapped now covers this week and last week as well as months,
  with how your listening compares with the week before. On Mondays and Tuesdays, Home
  shows last week in one line.
- **Sound for each device.** The equalizer, bass boost and volume normalisation are kept
  separately for each speaker or pair of headphones, and switch by themselves when you
  connect or pick another one. Your current settings stay as the phone's.
- **Back up your library.** Profile → Backup saves your likes, playlists, history, taste and
  sound settings to a file you choose, and puts them back on this phone or a new one.
  Sign-ins and downloads aren't included.

## 7.5.0 — 1 Nov 2026 (build 73)
- **Lighter while paused.** The playback timer and the home-screen widget's progress
  timer no longer wake the phone every second (and twice a second) while nothing plays; they
  wait for the music to start again.
- Updates: releases are built and checked with `scripts/release.sh` (same signing key,
  higher version, changelog entry) and install over the old app without losing anything.

## 7.4.0 — 1 Nov 2026 (build 72)
- **No account needed.** Spotify is optional: start without signing in, or with YouTube
  Music only. Search uses YouTube Music when Spotify isn't connected (or is busy), and songs
  play from JioSaavn, YouTube and Gaana as before. Spotify still adds your Spotify library,
  playlists, mood stations and its home feed.
- **Choose where it plays.** Tap the output name in the player ("This phone", your Bluetooth
  device, headphones) to pick from what's connected; Bluetooth settings and, on Android 14+,
  Android's own device switcher are one tap away. The media notification's own output button
  (Android 11+) works as before.
- **If Aalap ever closes unexpectedly**, Profile shows "Aalap closed unexpectedly" with a
  report to share (phone, Android version and the error), to find problems on phones we
  can't test.
- Android 8 and 9: Blend's app check and name matching no longer rely on newer Android calls.

## 7.3.0 — 1 Nov 2026 (build 71)
- **Gym Freaks radio.** A new station for lifting and cardio: songs from gym playlists, with
  the ones Aalap knows are high-energy or fast first and calm or slow ones left out. In Search
  → Moods, on Home's mood stations (mornings and afternoons, or once you use it), and by voice:
  "play gym songs on Aalap". When there's nothing to play it says so.
- **The cover's bars glide smoothly** for a few seconds whenever music starts, resumes, changes
  song or you come back to Aalap, then hold still: gliding all the time measured a third of a
  processor core more (a warm phone); holding costs nothing.

## 7.2.0 — 1 Nov 2026 (build 70)
- **Home and your picks load again.** At start-up Aalap asked Spotify for the same lists
  twice at once (and again every time Home opened, plus your top tracks from every player
  screen, never shown): Spotify answered "too many requests" and Home and the picks stayed
  empty. Each list is now asked for once, and not again within a minute.
- **Quick picks say what's happening.** Finding songs, nothing yet, or couldn't load, with
  "Try again", instead of the section quietly missing. New picks that take a while still
  arrive after a refresh, and a failed refresh keeps the picks you had.
- **Liked Songs keeps every take.** "Paro (Unplugged)" liked on YouTube Music no longer
  disappears because "Paro" is liked on Spotify; the same song liked on both still shows once.

## 7.1.0 — 1 Nov 2026 (build 69)
- **The song shown is the song playing.** The time and bar follow the song that's actually
  playing: they no longer freeze at 0:00 after a song changes by itself, jump to 30–40 s,
  or show the last song's time while the next one loads. A song you come back to shows
  where it will carry on from.
- **Play always plays.** After closing Aalap while paused, from the notification, or from
  headphones, Bluetooth or the car (also after Android closed Aalap), Play starts the right
  song from where it stopped, instead of showing "playing" in silence.
- **The notification and lock screen show the right song** during and after a crossfade,
  and no longer flash "stopped" at the end of each crossfade.
- **Fewer gaps and stops.** With Lossless on, a song starts from JioSaavn, YouTube or Gaana
  at once (it waited up to 15 s, or was skipped); with crossfade on, the next song is found
  half a minute early; a call, another app or unplugged headphones pause the crossfade too.
- **The cover's bars move with the music**, gliding on the beat of each song once Aalap has
  heard it, and only while sound is really playing (still while paused, loading or
  interrupted).
- **Keep music playing in the background:** if your phone limits Aalap to save battery
  (common on ColorOS, Samsung, Xiaomi), Profile → Playback shows how to allow it.

## 7.0.0 — 27 Oct 2026 (build 68)
- **Music keeps playing in the background until you stop it.** It used to stop after 5–10
  songs: Android's records showed Aalap closed for low memory while it had lost its "playing
  music" status. Now:
  - Aalap stays a foreground service between songs (while the next one is found, and while
    recommendations come in after the queue): checked on the phone with the screen off, song
    after song, never closed;
  - the player keeps the processor and Wi-Fi awake while music plays (it had no wake lock), and
    a short wake lock covers the gap between songs, so songs keep loading with the screen off;
  - no connection (a tunnel, the phone dozing) isn't the song's fault: Aalap waits for the
    network and tries the same song again instead of skipping until the music stops;
  - swiping Aalap out of Recents no longer stops the music (only a paused player goes).
- **Cooler still.** YouTube's heavy lookup (running YouTube's scrambled JavaScript) runs only
  when JioSaavn hasn't the song; it ran for every song, and twice (the next song is prepared
  early). The start-up YouTube warm-up is gone. Each song change now costs 1–5 s of processor
  time (was 25 s or more); a minute of music costs 14% of one core with Aalap open (was
  19–24%) and 2–6% in the background.

## 6.6.0 — 27 Oct 2026 (build 67)
- **Lighter still.** Measured on the phone, release build, a minute of music each:
  open 19–24% of one core (was 29%), background 2–3%; opening Aalap 1 s.
  - The app is now optimised by R8 (it shipped unoptimised): 2 MB smaller and faster. Nothing
    is renamed or removed, so YouTube's decoding, the JSON models and the web logins work as
    before (every playback path, voice, and each screen checked under it).
  - Debug logging is stripped from the app people install (it built thousands of log lines,
    ten or more per recommendation pick).
  - Every phone compiles Aalap ahead of time at install (a profile ships inside the app);
    shared APKs used to run partly uncompiled, slower and warmer, for days.
  - ExoPlayer 1.5.1 with dynamic scheduling: the playback loop sleeps until there's work
    (it woke every 10 ms while playing).
  - YouTube songs come as AAC rather than Opus, so the phone's audio chip can play those in
    the background too (it can't play Opus on Samsung phones).
  - The mini player's progress moves once a second (one screen redraw a second, was three).
- A demo is a different recording from the finished song ("The Ghost Intervention - Demo").

## 6.5.0 — 26 Oct 2026 (build 66)
- **The phone stays cool.** Measured on the phone, release build, music playing:
  - in the background 0–8% of one core (was ~30%, and 100–150% at times); open ~30% (was 40–65%);
  - Your Mix could loop forever (a core at 100% in the background until Android stopped it): fixed;
  - the mini player redrew itself every frame while a song played (its progress was written
    while drawing): fixed, and the full player's time labels the same way;
  - in the background the phone's own low-power audio chip plays the music (audio offload):
    playback itself costs ~0.4%. Songs then change with a clean cut; your crossfade returns in Aalap;
  - progress bars, lyrics, downloads and Blend refresh only while on screen; the cover
    equalizer and the "Liked" shine rest instead of redrawing forever;
  - the hidden Spotify web page (430 MB, steady CPU, never used) no longer loads;
  - audio copied in one go, not a byte at a time; the playback timer ticks once a second;
  - recommendations read each song's words once instead of again for every pick.
- **Official songs, almost every song plays.** JioSaavn's official uploads go first (found in
  ~0.4 s, no heavy decoding), then YouTube, then Gaana: 104 of 105 chart songs and 295 of 298
  KK results play. Recommendations skip users' YouTube uploads, and playlists come from
  Spotify's official ones first. When YouTube refuses streams, JioSaavn and Gaana go first for
  10 minutes, and a voice request no longer stops the search before they get their turn.
- **Ask with any button.** Profile → Voice: hold either volume key, only up, only down, or
  "My own button" (press any button of your phone to teach it), or use Android's floating
  Accessibility button, which can be dragged anywhere on screen.

## 6.4.0 — 26 Oct 2026 (build 65)
- **A song no source has never stops the music.** When YouTube, JioSaavn, Gaana and the last
  resorts all lack the exact song (never another song in its place), Aalap says "This song
  isn't available right now. Playing the next one" and moves on:
  - also when finding it goes wrong (a network error used to leave the music silent);
  - after 5 in a row (the connection is gone, most likely) it stops and says so, instead of
    skipping through everything.
- **Faster:** the next song is found while the current one is being found, and YouTube's
  player is fetched in the background as the app opens. A song that can't be found now
  costs 2 s before the next plays (9.6 s right after opening), from 21.6 s.

## 6.3.0 — 26 Oct 2026 (build 64)
- **The song you see is the song you hear.** Every recording is checked against the song's
  own title, its singers (one by one: "Kinjal Dave" is never "Jinal Chauhan"), its length
  and its album, and must be the same take:
  - Asked for "Paro (Unplugged)" or "KALYANI - Remix", that take plays, never the plain song;
    asked for the song, never a remix, reprise ("Bulleya (Reprise)"), live, cover
    ("Originally by…"), lofi, slowed or sped-up upload. The same rule now holds on YouTube,
    JioSaavn (its last resort too) and Gaana (which ignored brackets, so "Paro (Unplugged)"
    matched plain "Paro").
  - A title must really match (60%, judged without "(From …)", "feat." and the like).
  - Checked twice or three times: when nothing passes, it looks again with just the song's
    name and main singer, then with all its singers ("Agar Tum Saath Ho" and "We Don't Talk
    Anymore" weren't found before), then YouTube videos; a wrong song never plays instead.
  - Checked on today's 30 Aaj ka Aalap songs: two would have played the wrong recording, two
    wouldn't have played; all now right.
- **Notification:** tapping it opens the player on the song that's playing. Next on the last
  song carries on into recommendations instead of jumping back to the first (it loops only
  with Loop playlist on), and "Playing from" shows each recommended song's own album.
- **Sharing a song:** one simple share for everyone: the message carries Spotify and YouTube
  Music links that open on Android and iPhone alike, written in the message itself so
  WhatsApp keeps them (they were hidden before and got dropped). The card carries the real
  Aalap app icon.
- **Logging out and in keeps everything yours:** besides likes, playlists and history, your
  recommendation settings (discovery, languages, follow my pace), Blends and today's mixes
  now go with your account and come back when you log in.
- **Cleaner mixes:** each song once (the same song listed twice is one), and the song itself
  rather than a remix, lofi, slowed or cover, unless the mix is mostly those.
- **Song videos are off by default** (turn them on in Profile → Song videos).
- **"Playing on" icon:** Aalap's own (a phone sending out sound; headphones when connected).

## 6.2.0 — 25 Oct 2026 (build 63)
- **"Follow my pace" switch** (Profile → Recommendations). Your pace lately (e.g. "easy-going ·
  about 94 bpm, your last 25 songs") is always shown; with the switch on, Your Mix and
  language mixes lean towards it; off, they don't look at it.
- **Your feedback, shown as what it's about.** Instead of one "Clear more / less" row:
  - More like this and Less like this each show how many songs, their pace ("Fast, lively ·
    about 128 bpm"), the artists ("Arijit Singh ×3") and the kinds of song (Romantic ×4).
  - ✕ on an artist or a kind forgets just those songs' feedback; "Clear all" forgets that side.
  - Never play: blocked artists (✕ to unblock one) and blocked songs, with Clear all.

## 6.1.0 — 25 Oct 2026 (build 62)
- **Artist search no longer crashes.** Searching "arijit" closed the app: Spotify has more
  than one artist with the same name ("Arijit"), and the list used the name to tell artists
  apart. It now uses each artist's own id, so both show.
- **Your artists: "Trending now".** Up to 24 artists from today's Top 50 India and Trending
  India charts ("12 chart songs"), right after your top artists, loading alongside them.
  Ones you follow show Following. "Fans also like" starts from your top 6 artists (was 4).
- **Follow and unfollow everywhere alike.** Following from search works like anywhere else
  (similar artists slide in after it); unfollowing under Following fades and shrinks the
  artist away and the rest slide up, instead of it vanishing.
- **Library → Playlists: "Trending playlists".** The official Trending Now India, Top 50
  India, Hot Hits Hindi, Hot Hits Punjabi, This Is Arijit Singh, Romantic Bollywood Mix,
  Viral Hits, Bollywood Central, Desi Hits, I-Pop Superhits… (fan-made copies left out),
  under your own playlists.
- **Refreshed daily:** trending playlists and trending artists follow today's charts.
- **Light theme:**
  - Blend pages tint softly with the Blend's colour instead of a dark plum band; the score,
    rings and chart read on cream.
  - No more grey band over the status bar anywhere in the app: the closed player's window
    was asking for white icons.

## 6.0.0 — 25 Oct 2026 (build 61)
- **Your pace, in any language.** Aalap now works out your pace lately: the tempo and energy
  of the last 25 songs you really listened to (30 s or more, newest counting most),
  whatever their language. Recommendations lean towards it:
  - Language mixes ("play English songs", "play Hindi songs") start with the songs closest
    to your pace instead of a random shuffle, so after a run of fast Hindi songs the English
    mix starts fast too. The voice card says so ("…fast and lively like you've been playing").
  - Your Mix favours favourites that match your pace.
  - Profile → Recommendations shows "Your pace lately" (e.g. "Easy-going · about 95 bpm").
  - Half and double tempo count as a match (a 70 bpm song fits a 140 bpm run), and songs not
    measured yet sit in the middle rather than being pushed out.
- **Delete a Blend.** A delete button at the top of every Blend, and a clear "Delete Blend"
  button at the bottom, both asking first. It comes off your phone with its songs and score;
  your friend keeps theirs.

## 5.8.0 — 25 Oct 2026 (build 60)
- **"Ask Aalap" icon only if you want it.** The extra app-drawer icon is gone by default;
  holding a volume key (switched on in Accessibility) asks Aalap from anywhere. Profile →
  Voice → "Ask Aalap" icon brings it back, e.g. for the side key's double press.

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
