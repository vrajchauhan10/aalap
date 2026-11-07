# Security: what protects Aalap users

## The key that matters
Every Aalap release is signed with one key: this Mac's `~/.android/debug.keystore` (see
docs/RELEASING.md; its passwords are Android's defaults, so the **file itself** is the secret).
It is not in Git. Its certificate SHA-256 is public: `scripts/release-signer.sha256` and
`AppUpdate.OFFICIAL_SIGNER`. A backup with restore notes is in
`~/Documents/Aalap-signing-key-backup/` (2026-11-07); keep copies offline in two places.

Anyone can read or copy the code. Nobody without the key file can:
- make a phone accept their build as an **update** to the real Aalap (Android refuses an app
  signed with a different key);
- get their build installed by Aalap's in-app updater (it installs only APKs signed with the
  official key, `AppUpdate.verify`).
Pushing to this repository needs the owner's GitHub login; `main` is protected against
force-pushes and deletion (set 2026-11-07).

## In the app
- **Updates**: downloaded only from this repo's GitHub releases, checked for the same app id,
  a newer version code, and the official signing key before Android's installer opens.
- **Genuine check**: on Home, a copy not signed with the official key shows "This isn't the
  official Aalap" and links to the real download (`AppUpdate.genuine`). A determined attacker
  can strip this from their copy; it protects people who were handed a lightly modified one.
- **Shared codes** (Blend, playlists) are size-limited before and after unzipping and only
  ever become songs Aalap itself plays.

## Owner checklist
1. Keep the key backup (`~/Documents/Aalap-signing-key-backup/`) in two offline places.
   Losing it means no phone can update to your future releases; leaking it means others can.
2. GitHub: turn on two-factor login (Settings → Password and authentication). `main` already
   blocks force-pushes and deletion.
3. Never commit tokens or keys (`local.properties`, `keystore.properties` are ignored).
4. Users are told where to download: README "Download safely" and every release's notes
   (added by scripts/publish-release.sh).
