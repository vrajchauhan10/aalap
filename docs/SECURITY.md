# Security: what protects Aalap users

## The key that matters
Every Aalap release is signed with Aalap's private release key (`keystore.properties` and
the keystore file live only on the maintainer's Mac; neither is in Git). Its certificate
SHA-256 is public: `scripts/release-signer.sha256` and `AppUpdate.OFFICIAL_SIGNER`.

Anyone can read or copy the code. Nobody without the key can:
- push to this repository (GitHub only lets the owner/collaborators write; forks are separate);
- make a phone accept their build as an **update** to the real Aalap: Android refuses to
  replace an app with one signed by a different key;
- get their build installed by Aalap's in-app updater: it installs only APKs signed with the
  official key (`AppUpdate.verify`).

## In the app
- **Updates**: downloaded only from this repo's GitHub releases, checked for the same app id,
  a newer version code, and the official signing key before Android's installer opens.
- **Genuine check**: on Home, a copy not signed with the official key shows "This isn't the
  official Aalap" and links to the real download (`AppUpdate.genuine`). A determined attacker
  can strip this from their copy; it protects people who were handed a lightly modified one.
- **Shared codes** (Blend, playlists) are size-limited before and after unzipping and only
  ever become songs Aalap itself plays.

## Owner checklist
1. Back up the keystore and `keystore.properties` offline (two places). Losing them means no
   phone can update to your future releases; leaking them means others can.
2. GitHub: two-factor login on, and a branch protection rule on `main` (no force pushes).
3. Never commit tokens or keys (`local.properties`, `keystore.properties` are ignored).
4. Tell users: download Aalap only from github.com/vrajchauhan10/aalap/releases (or the
   in-app update / "Share Aalap"), and check `Aalap-x.apk.sha256` if in doubt.
