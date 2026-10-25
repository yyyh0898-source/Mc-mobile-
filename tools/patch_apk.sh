#!/usr/bin/env bash
# patch_apk.sh - inject Mobile Client into a Minecraft Bedrock APK that you own.
#
# usage: patch_apk.sh <minecraft-base.apk> <client-sdk-release.aar> <shadowhook.aar> <out.apk>
#
# needs : apktool, unzip, python3, Android build-tools (d8, zipalign, apksigner)
# env   : ANDROID_HOME                      Android SDK location (required)
#         KOTLIN_STDLIB                     kotlin-stdlib jar (auto-detected in ~/.gradle if unset)
#         KEYSTORE KS_ALIAS KS_PASS         signing key (default: ~/.android/debug.keystore)
#         TARGET_REGEX                      activities that get the overlay
#
# What it does: adds the SDK + Kotlin stdlib as an extra dex, copies libmobileclient.so and
# libshadowhook.so, and declares ClientInitProvider in the manifest (the SDK starts itself - no
# smali editing). Original code and resources are left untouched.
#
# NOTE: split APKs (base + split_config.*): patch base.apk, then re-sign EVERY split with the same
#       key and install with `adb install-multiple`. The patched app has a different signature than
#       the Play Store build -> uninstall the original first (back up your worlds!).
#       Use it on your own copy only; do not redistribute patched APKs.
set -euo pipefail

[ $# -eq 4 ] || { sed -n '2,6p' "$0"; exit 1; }
MC_APK=$(realpath "$1"); SDK_AAR=$(realpath "$2"); SH_AAR=$(realpath "$3"); OUT=$(realpath -m "$4")

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
BT="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
ANDROID_JAR="$ANDROID_HOME/platforms/$(ls "$ANDROID_HOME/platforms" | sort -V | tail -1)/android.jar"
KOTLIN_STDLIB="${KOTLIN_STDLIB:-$(find "$HOME/.gradle" -name 'kotlin-stdlib-2*.jar' ! -name '*sources*' 2>/dev/null | sort | tail -1)}"
[ -f "$KOTLIN_STDLIB" ] || { echo "kotlin-stdlib jar not found - set KOTLIN_STDLIB=/path/to/kotlin-stdlib-x.y.z.jar"; exit 1; }
KEYSTORE="${KEYSTORE:-$HOME/.android/debug.keystore}"
KS_ALIAS="${KS_ALIAS:-androiddebugkey}"
KS_PASS="${KS_PASS:-android}"
export TARGET_REGEX="${TARGET_REGEX:-com\\.mojang\\.minecraftpe\\.MainActivity}"

W=$(mktemp -d); trap 'rm -rf "$W"' EXIT

echo "[1/6] extracting AARs"
mkdir "$W/sdk" "$W/sh"
unzip -q "$SDK_AAR" -d "$W/sdk"
unzip -q "$SH_AAR" -d "$W/sh"
[ -f "$W/sdk/jni/arm64-v8a/libmobileclient.so" ] || { echo "libmobileclient.so (arm64-v8a) missing in the SDK AAR"; exit 1; }
[ -f "$W/sh/jni/arm64-v8a/libshadowhook.so" ] || { echo "libshadowhook.so (arm64-v8a) missing in the shadowhook AAR"; exit 1; }

echo "[2/6] dexing SDK + Kotlin stdlib"
mkdir "$W/dex"
"$BT/d8" --release --min-api 26 --lib "$ANDROID_JAR" --output "$W/dex" "$W/sdk/classes.jar" "$KOTLIN_STDLIB"

echo "[3/6] decoding APK (dex + resources kept as they are)"
apktool d -s -f "$MC_APK" -o "$W/app" >/dev/null

echo "[4/6] adding dex, native libs and manifest entries"
N=$(ls "$W/app" | grep -c '^classes[0-9]*\.dex$' || true)
[ "$N" -ge 1 ] || { echo "no classes.dex found after decoding"; exit 1; }
i=$((N + 1))
for d in "$W"/dex/classes*.dex; do
  cp "$d" "$W/app/classes${i}.dex"
  i=$((i + 1))
done
mkdir -p "$W/app/lib/arm64-v8a"
cp "$W/sdk/jni/arm64-v8a/libmobileclient.so" "$W/app/lib/arm64-v8a/"
cp "$W/sh/jni/arm64-v8a/libshadowhook.so" "$W/app/lib/arm64-v8a/"

python3 - "$W/app/AndroidManifest.xml" <<'PY'
import os, re, sys
path = sys.argv[1]
s = open(path, encoding="utf-8").read()
pkg = re.search(r'package="([^"]+)"', s).group(1)
if "ClientInitProvider" not in s:
    add = (
        '\n    <provider android:name="com.mobileclient.sdk.ClientInitProvider"'
        ' android:authorities="%s.mobileclient-init" android:exported="false" android:initOrder="50"/>'
        '\n    <meta-data android:name="mobileclient.auto_attach" android:value="true"/>'
        '\n    <meta-data android:name="mobileclient.target_regex" android:value="%s"/>\n'
    ) % (pkg, os.environ["TARGET_REGEX"])
    s = s.replace("</application>", add + "</application>", 1)
# the extra .so files must be extracted by the installer
s = s.replace('android:extractNativeLibs="false"', 'android:extractNativeLibs="true"')
open(path, "w", encoding="utf-8").write(s)
PY

echo "[5/6] rebuilding + aligning"
apktool b "$W/app" -o "$W/unsigned.apk" >/dev/null
"$BT/zipalign" -f -p 4 "$W/unsigned.apk" "$W/aligned.apk"

echo "[6/6] signing"
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" --out "$OUT" "$W/aligned.apk"

echo "done -> $OUT"
echo "Next: uninstall the original (back up worlds first), install this APK, start it from the launcher."
