# Releasing Aalap and updating phones

Verified 2026-11-01 on this Mac and a Galaxy S24 FE (Android 17).

## What makes an update install over the old app
Android installs an APK over the installed app (keeping all its data) only when:
1. the **application id** is the same: `com.music.aalap` (never change it);
2. it is **signed by the same key** (or a key rotated from it with an APK v3 lineage);
3. its **versionCode is not lower** (we always raise it);
4. its **minSdk** fits the phone (26 = Android 8.0).

Never uninstall to update: uninstalling deletes likes, playlists, history, settings and
downloads that only live on the phone.

## The signing key (important)
- `keystore.properties` and `~/.aalap-signing` (used by `scripts/sign-release.sh`) are **not on
  this Mac**. `app/build.gradle.kts` then signs release builds with this Mac's
  `~/.android/debug.keystore`.
- Every build handed out recently (`dist/Aalap-7.0.0.apk` and later) and the app on the test
  phone are signed by that key: certificate SHA-256
  `1c1e8f2cfb03494bcde526b2a3ca25d0ddbcd9334b648600b174a46581a3e6c1`
  (kept in `scripts/release-signer.sha256`, checked by `scripts/release.sh`).
- **Back up `~/.android/debug.keystore`** somewhere safe (it is the release key now). If it is
  lost, phones can't take updates any more without uninstalling.
- To move to a proper release key later, don't just switch: sign with APK key rotation
  (`apksigner sign --next-signer ... --lineage`), as `scripts/sign-release.sh` does for the
  older keys A/B, so installed phones accept the new key. Not done yet.
- Older installs signed with keys A/B (1.x–6.x era, per `sign-release.sh`) can't take a build
  signed only by the debug key; they need that rotation script and those keys.

## Release steps
1. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` (minor for changes,
   major for big ones), and add the `## <versionName>` entry at the top of `CHANGELOG.md`.
2. `scripts/release.sh` runs the unit tests, builds, checks the id, signer, versionCode
   (higher than every APK in `dist/`) and changelog, and writes `dist/Aalap-<version>.apk`
   + `.sha256`. `scripts/release.sh --install` also updates the connected phone with
   `adb install -r` and checks the new versionCode.
3. Hand out the APK from `dist/` (sideloading). An AAB is only for Google Play, which would
   also need Play App Signing set up with this key; not used.

## Data compatibility checklist (before every release)
- `RecoDb` (`aalap_reco.db`, VERSION 3): `onUpgrade` re-runs `CREATE ... IF NOT EXISTS`, so
  **only new tables/indexes are safe**. A new column needs a real migration (`ALTER TABLE`) and
  a VERSION bump. There is no `onDowngrade`: an older build can't open a newer database.
- SharedPreferences: keep existing file and key names and value formats; add new keys with
  defaults. Per-user files are listed in `UserVault.FILES` (logout/login keeps them).
- Media cache / downloads: paths `cacheDir/media`, `filesDir/downloads` unchanged.

## Rollback and recovery
- Android won't install a lower versionCode over a higher one (and downgrading would also
  break the database). To roll back, **release the previous code again with a higher
  versionCode** (e.g. 7.5.1 with 7.4.0's code). Keep every released APK in `dist/`.
- If an update fails to install: the old app and its data stay as they were; check the
  signer and versionCode with `scripts/release.sh`.
- A crash after an update leaves a report under Profile → Playback ("Aalap closed
  unexpectedly"), shareable without a computer.

## Tested
- 7.3.0 (71) → 7.4.0 (72) over the top on the S24 FE: same first-install date, Liked 215,
  Artists 84, Plays 510 before and after, launched without a crash.
- Fresh install: the debug variant (`com.music.aalap.debug`, installs beside the real app)
  from scratch: notification permission prompt (denied), name/email, "Connect your music"
  without any sign-in, Home, YouTube Music search, playback; then removed.
