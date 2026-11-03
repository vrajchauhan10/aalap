#!/bin/sh
# Publishes dist/Aalap-<version>.apk as the GitHub release v<version>, which is what Aalap's
# Profile → "Update available" finds (AppUpdate.kt reads releases/latest).
# Run scripts/release.sh first (it builds, tests and checks the signing key).
#
# Needs a GitHub token with "Contents: read and write" on vrajchauhan10/aalap:
#   GITHUB_TOKEN=... scripts/publish-release.sh            # the version in app/build.gradle.kts
#   GITHUB_TOKEN=... scripts/publish-release.sh 7.9.0      # a specific one already in dist/
# The release notes are that version's section of CHANGELOG.md.
set -e
cd "$(dirname "$0")/.."
REPO=vrajchauhan10/aalap
[ -n "$GITHUB_TOKEN" ] || { echo "Set GITHUB_TOKEN (see the comment at the top)."; exit 1; }
V="${1:-$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)}"
APK="dist/Aalap-$V.apk"
[ -f "$APK" ] || { echo "$APK not found: run scripts/release.sh first."; exit 1; }

# Same checks as release.sh: Aalap's key, so phones accept it as an update.
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
signer=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
[ "$signer" = "$(tr -d ' \n' < scripts/release-signer.sha256)" ] || { echo "$APK isn't signed with Aalap's key."; exit 1; }
shasum -a 256 "$APK" | awk '{print $1}' > "$APK.sha256"

NOTES=$(awk -v v="## $V " 'index($0, v) == 1 {on=1; next} /^## / {on=0} on' CHANGELOG.md)
BODY=$(python3 -c 'import json,sys; print(json.dumps({"tag_name": "v"+sys.argv[1], "name": "Aalap "+sys.argv[1], "body": sys.argv[2], "draft": False, "prerelease": False}))' "$V" "$NOTES")
API=https://api.github.com/repos/$REPO
AUTH="Authorization: Bearer $GITHUB_TOKEN"

resp=$(curl -sS -X POST -H "$AUTH" -H "Accept: application/vnd.github+json" "$API/releases" -d "$BODY")
id=$(printf '%s' "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("id",""))')
[ -n "$id" ] || { echo "GitHub didn't create the release:"; printf '%s\n' "$resp"; exit 1; }

for f in "$APK" "$APK.sha256"; do
  name=$(basename "$f")
  type=application/vnd.android.package-archive; [ "${name%.sha256}" != "$name" ] && type=text/plain
  curl -sS -X POST -H "$AUTH" -H "Content-Type: $type" --data-binary @"$f" \
    "https://uploads.github.com/repos/$REPO/releases/$id/assets?name=$name" > /dev/null
  echo "uploaded $name"
done
echo "Published https://github.com/$REPO/releases/tag/v$V"
