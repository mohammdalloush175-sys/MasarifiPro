#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
KEYSTORE="${MASARIFI_KEYSTORE:-$ROOT_DIR/masarifipro-release-key.jks}"
KEY_ALIAS="${MASARIFI_KEY_ALIAS:-masarifipro}"

if [[ ! -f "$KEYSTORE" ]]; then
  echo "Keystore not found: $KEYSTORE" >&2
  echo "Copy the existing Masarifi Pro keystore here or set MASARIFI_KEYSTORE." >&2
  exit 1
fi

if [[ ! -d "$SDK_ROOT/build-tools" ]]; then
  echo "Android SDK build-tools not found under: $SDK_ROOT" >&2
  exit 1
fi

BUILD_TOOLS_VERSION="$(find "$SDK_ROOT/build-tools" -mindepth 1 -maxdepth 1 -type d -printf '%f\n' | sort -V | tail -1)"
BUILD_TOOLS="$SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

[[ -x "$ZIPALIGN" ]] || { echo "zipalign not found: $ZIPALIGN" >&2; exit 1; }
[[ -x "$APKSIGNER" ]] || { echo "apksigner not found: $APKSIGNER" >&2; exit 1; }

chmod +x ./gradlew
./gradlew clean :app:assembleRelease

VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
if [[ -z "$VERSION_NAME" ]]; then
  echo "Could not read versionName from app/build.gradle.kts" >&2
  exit 1
fi

UNSIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/app-release-unsigned.apk"
ALIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/MasarifiPro-v${VERSION_NAME}-aligned.apk"
SIGNED_APK="$ROOT_DIR/app/build/outputs/apk/release/MasarifiPro-v${VERSION_NAME}-release-signed.apk"

[[ -f "$UNSIGNED_APK" ]] || { echo "Unsigned APK not found: $UNSIGNED_APK" >&2; exit 1; }

"$ZIPALIGN" -p -f 4 "$UNSIGNED_APK" "$ALIGNED_APK"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-key-alias "$KEY_ALIAS" \
  --out "$SIGNED_APK" \
  "$ALIGNED_APK"
"$APKSIGNER" verify --verbose --print-certs "$SIGNED_APK"

echo
echo "Signed APK: $SIGNED_APK"
echo "Upgrade install: adb install -r \"$SIGNED_APK\""
