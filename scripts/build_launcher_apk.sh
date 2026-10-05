#!/usr/bin/env bash
set -euo pipefail

# TontOS AMOLED Detox Home Launcher APK Builder
# Compiles a minimal pure-black launcher without Gradle dependencies.

BUILD_TOOLS="/home/paproka/Android/Sdk/build-tools/36.0.0"
PLATFORM_JAR="/home/paproka/Android/Sdk/platforms/android-37.0/android.jar"
SRC_DIR="/home/paproka/Desktop/TontoOS/launcher_apk"
BUILD_DIR="$SRC_DIR/build"
OUTPUT_APK="$SRC_DIR/tontos-launcher.apk"

echo "=============================================="
echo " Building TontOS AMOLED Detox Launcher APK"
echo "=============================================="

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"/classes
mkdir -p "$BUILD_DIR"/res

# 1. Compile resources with aapt2
"$BUILD_TOOLS/aapt2" compile --dir "$SRC_DIR/res" -o "$BUILD_DIR/compiled_res.zip" 2>/dev/null || true

# 2. Link APK skeleton
if [ -f "$BUILD_DIR/compiled_res.zip" ]; then
    "$BUILD_TOOLS/aapt2" link -I "$PLATFORM_JAR" \
        --manifest "$SRC_DIR/AndroidManifest.xml" \
        -o "$BUILD_DIR/unaligned.apk" \
        "$BUILD_DIR/compiled_res.zip" \
        --auto-add-overlay
else
    "$BUILD_TOOLS/aapt2" link -I "$PLATFORM_JAR" \
        --manifest "$SRC_DIR/AndroidManifest.xml" \
        -o "$BUILD_DIR/unaligned.apk"
fi

# 3. Compile Java source
javac --release 17 -cp "$PLATFORM_JAR" -d "$BUILD_DIR/classes" $(find "$SRC_DIR/src" -name "*.java")

# 4. Convert classes to dex with d8
"$BUILD_TOOLS/d8" --lib "$PLATFORM_JAR" --output "$BUILD_DIR" $(find "$BUILD_DIR/classes" -name "*.class")

# 5. Add classes.dex into unaligned.apk
(cd "$BUILD_DIR" && jar -uf unaligned.apk classes.dex)

# 6. Zipalign
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

# 7. Generate debug keystore if not present
DEBUG_KEYSTORE="$SRC_DIR/debug.keystore"
if [ ! -f "$DEBUG_KEYSTORE" ]; then
    keytool -genkeypair -v -keystore "$DEBUG_KEYSTORE" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

# 8. Sign APK with apksigner
"$BUILD_TOOLS/apksigner" sign --ks "$DEBUG_KEYSTORE" \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUTPUT_APK" "$BUILD_DIR/aligned.apk"

echo "=============================================="
echo "[+] TontOS Launcher APK successfully built!"
echo "    Output: $OUTPUT_APK ($(du -h "$OUTPUT_APK" | cut -f1))"
echo "=============================================="
