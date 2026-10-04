#!/usr/bin/env bash
# Builds a signed Tonnage APK that bundles the web app (../) in a WebView.
#
# Uses the Android build tools packaged by Debian/Ubuntu, so no Android Studio
# or Gradle is needed:
#   sudo apt-get install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23
# plus a JDK (javac, keytool).
#
# Output: android/build/tonnage.apk
#
# Signing: uses $TONNAGE_KEYSTORE (default ~/.android/tonnage.keystore) and
# creates it on first run. Android only installs an update over an existing
# install when both are signed with the same key, so keep that file.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WEB="$(dirname "$HERE")"
OUT="$HERE/build"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
KEYSTORE="${TONNAGE_KEYSTORE:-$HOME/.android/tonnage.keystore}"
KEYSTORE_PASS="${TONNAGE_KEYSTORE_PASS:-tonnage-release}"
KEY_ALIAS="${TONNAGE_KEY_ALIAS:-tonnage}"

for tool in aapt javac dalvik-exchange zipalign apksigner keytool; do
  command -v "$tool" >/dev/null || { echo "Missing $tool. See the install line at the top of this script." >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "Missing $ANDROID_JAR (apt-get install android-sdk-platform-23)." >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/assets/www/icons" "$OUT/gen" "$OUT/classes"

echo "Bundling web app"
cp "$WEB/index.html" "$WEB/styles.css" "$WEB/logic.js" "$WEB/app.js" "$WEB/manifest.webmanifest" "$OUT/assets/www/"
cp "$WEB"/icons/*.png "$WEB"/icons/*.svg "$OUT/assets/www/icons/"

echo "Packaging resources"
aapt package -f \
  -M "$HERE/AndroidManifest.xml" \
  -S "$HERE/res" \
  -A "$OUT/assets" \
  -I "$ANDROID_JAR" \
  -J "$OUT/gen" \
  -F "$OUT/tonnage-unsigned.apk"

echo "Compiling Java"
# shellcheck disable=SC2046
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')

echo "Converting to dex"
dalvik-exchange --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add -k tonnage-unsigned.apk classes.dex >/dev/null)

echo "Aligning"
zipalign -f -p 4 "$OUT/tonnage-unsigned.apk" "$OUT/tonnage-aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "Creating signing key at $KEYSTORE"
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
    -storepass "$KEYSTORE_PASS" -keypass "$KEYSTORE_PASS" -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Tonnage" >/dev/null
fi

echo "Signing"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KEYSTORE_PASS" --ks-key-alias "$KEY_ALIAS" \
  --out "$OUT/tonnage.apk" "$OUT/tonnage-aligned.apk"
apksigner verify "$OUT/tonnage.apk"
rm -f "$OUT/tonnage.apk.idsig"

echo "Built $OUT/tonnage.apk ($(du -h "$OUT/tonnage.apk" | cut -f1))"
