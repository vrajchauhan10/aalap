#!/bin/sh
# Builds a release APK that installs over the current Aalap without uninstalling.
# Checks before it hands anything out:
#   - unit tests pass;
#   - the APK is signed by the same key as every Aalap so far (scripts/release-signer.sha256);
#   - its versionCode is higher than every APK in dist/ (Android refuses a lower one);
#   - CHANGELOG.md has an entry for its versionName.
# Then copies it to dist/Aalap-<version>.apk with a .sha256 next to it.
# Usage: scripts/release.sh            build and check
#        scripts/release.sh --install  also update the connected phone (adb install -r, never uninstall)
# See docs/RELEASING.md.
set -e
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$SDK"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
ADB="$SDK/platform-tools/adb"

./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain -q
APK=app/build/outputs/apk/release/app-release.apk

badging="$("$BT/aapt2" dump badging "$APK")"
pkg=$(printf '%s' "$badging" | sed -n "s/^package: name='\([^']*\)'.*/\1/p")
code=$(printf '%s' "$badging" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
name=$(printf '%s' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")
[ "$pkg" = "com.music.aalap" ] || { echo "Wrong application id: $pkg"; exit 1; }

signer=$("$BT/apksigner" verify --print-certs "$APK" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
expected=$(tr -d ' \n' < scripts/release-signer.sha256)
if [ "$signer" != "$expected" ]; then
  echo "Signed by $signer, but Aalap is signed by $expected."
  echo "Phones would refuse this update. Build on the machine that holds the Aalap key (docs/RELEASING.md)."
  exit 1
fi

highest=0
for f in dist/*.apk; do
  [ -f "$f" ] || continue
  c=$("$BT/aapt2" dump badging "$f" | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p")
  [ "$c" -gt "$highest" ] && highest=$c
done
if [ "$code" -le "$highest" ]; then
  echo "versionCode $code is not higher than the newest in dist/ ($highest): raise it in app/build.gradle.kts."
  exit 1
fi
grep -q "^## $name " CHANGELOG.md || { echo "CHANGELOG.md has no entry for $name."; exit 1; }

mkdir -p dist
OUT="dist/Aalap-$name.apk"
cp "$APK" "$OUT"
shasum -a 256 "$OUT" | awk '{print $1}' > "$OUT.sha256"
echo "Release $name ($code), $pkg, signer OK: $OUT"

if [ "$1" = "--install" ]; then
  before=$("$ADB" shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
  "$ADB" install -r "$OUT"
  after=$("$ADB" shell dumpsys package "$pkg" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
  echo "Phone: versionCode ${before:-none} -> $after"
  [ "$after" = "$code" ] || { echo "The phone didn't take the update."; exit 1; }
fi
