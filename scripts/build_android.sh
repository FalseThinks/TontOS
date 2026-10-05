#!/usr/bin/env bash
set -euo pipefail

TARGET="x86_64-unknown-linux-musl"

echo "=============================================="
echo " TontOS: Compiling Static Binaries for Emulator"
echo " Target: $TARGET (Linux 6.6+)"
echo "=============================================="

cargo build --workspace --target "$TARGET" --config "target.$TARGET.linker=\"clang\""

echo ""
echo "Deploying to active emulator instance..."
if adb devices | grep -q "emulator"; then
    BINARIES=(
        "tont-powerd"
        "tont-appd"
        "tont-syncd"
        "tont-telephony"
        "tont-sensors"
        "tont-settings-app"
    )

    for bin in "${BINARIES[@]}"; do
        if [ -f "target/$TARGET/debug/$bin" ]; then
            adb push "target/$TARGET/debug/$bin" /data/local/tmp/
        fi
    done

    adb shell chmod 755 /data/local/tmp/tont-*
    echo "Successfully deployed to /data/local/tmp/ on emulator!"
    echo "Running live check on emulator:"
    adb shell "RUST_LOG=info /data/local/tmp/tont-powerd" 2>&1
    adb shell "RUST_LOG=info /data/local/tmp/tont-sensors" 2>&1
else
    echo "No emulator detected via adb. Run 'android emulator start medium_phone' first."
fi
echo "=============================================="
