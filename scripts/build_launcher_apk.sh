#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
SRC_DIR="$ROOT_DIR/launcher_apk"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-34}"
ANDROID_BUILD_TOOLS_VERSION="${ANDROID_BUILD_TOOLS_VERSION:-36.0.0}"
BUILD_TOOLS="$SDK_ROOT/build-tools/$ANDROID_BUILD_TOOLS_VERSION"
PLATFORM_JAR="$SDK_ROOT/platforms/$ANDROID_PLATFORM/android.jar"
OUTPUT_APK="${OUTPUT_APK:-$SRC_DIR/tontos-launcher.apk}"
DEBUG_KEYSTORE="${DEBUG_KEYSTORE:-$SRC_DIR/debug.keystore}"
BUILD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tontos-launcher-build.XXXXXX")"
trap 'rm -rf "$BUILD_DIR"' EXIT

if [[ ! -f "$PLATFORM_JAR" ]]; then
    echo "Android platform not found: $PLATFORM_JAR" >&2
    exit 1
fi

for tool in aapt2 d8 zipalign apksigner; do
    if [[ ! -x "$BUILD_TOOLS/$tool" ]]; then
        echo "Android build tool not found: $BUILD_TOOLS/$tool" >&2
        exit 1
    fi
done

for tool in javac jar keytool; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Required command not found in PATH: $tool" >&2
        exit 1
    fi
done

mkdir -p "$BUILD_DIR/classes" "$(dirname -- "$OUTPUT_APK")"

resource_args=()
if [[ -d "$SRC_DIR/res" ]] && find "$SRC_DIR/res" -type f -print -quit | grep -q .; then
    "$BUILD_TOOLS/aapt2" compile --dir "$SRC_DIR/res" -o "$BUILD_DIR/compiled_res.zip"
    resource_args+=("$BUILD_DIR/compiled_res.zip")
fi

"$BUILD_TOOLS/aapt2" link -I "$PLATFORM_JAR" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    -o "$BUILD_DIR/unaligned.apk" \
    "${resource_args[@]}" \
    --auto-add-overlay

javac --release 17 -cp "$PLATFORM_JAR" -d "$BUILD_DIR/classes" \
    $(find "$SRC_DIR/src" -name "*.java")
"$BUILD_TOOLS/d8" --lib "$PLATFORM_JAR" --output "$BUILD_DIR" \
    $(find "$BUILD_DIR/classes" -name "*.class")
(cd "$BUILD_DIR" && jar -uf unaligned.apk classes.dex)
"$BUILD_TOOLS/zipalign" -f -p 4 "$BUILD_DIR/unaligned.apk" "$BUILD_DIR/aligned.apk"

if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
    keytool -genkeypair -noprompt -keystore "$DEBUG_KEYSTORE" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign --ks "$DEBUG_KEYSTORE" \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUTPUT_APK" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" verify --verbose "$OUTPUT_APK"

echo "Launcher APK built successfully: $OUTPUT_APK ($(du -h "$OUTPUT_APK" | cut -f1))"
