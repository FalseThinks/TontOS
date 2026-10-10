#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

GSI_SCRIPT="scripts/make_gsi_rootfs.sh"
OTA_SCRIPT="scripts/make_ota_package.sh"
PATCH_INTEGRITY_SCRIPT="scripts/patch_play_integrity.sh"
VERIFY_INTEGRITY_SCRIPT="scripts/verify_play_integrity.sh"
BOOTLOADER_MANAGER="launcher_apk/src/org/tontos/launcher/BootloaderManager.java"
SETTINGS_ACTIVITY="launcher_apk/src/org/tontos/launcher/SettingsActivity.java"
LOCALIZATION="launcher_apk/src/org/tontos/launcher/I18n.java"
LAUNCHER_MANIFEST="launcher_apk/AndroidManifest.xml"
NFC_PAYMENT_MANAGER="launcher_apk/src/org/tontos/launcher/NfcPaymentSandboxManager.java"
SCRATCH="$ROOT/target/safety-test-$$"

if [[ -e "$SCRATCH" ]]; then
    echo "Safety test scratch path already exists: $SCRATCH" >&2
    exit 1
fi
mkdir -p "$SCRATCH/fakebin"
trap 'rm -rf "$SCRATCH"' EXIT

directory_signature() {
    local path="$1"
    if [[ ! -e "$path" ]]; then
        echo "absent"
        return
    fi
    find "$path" -printf '%y %P\n' | sort
    find "$path" -type f -print0 | sort -z | xargs -0 -r sha256sum
}

bash -n "$GSI_SCRIPT" "$OTA_SCRIPT" "$PATCH_INTEGRITY_SCRIPT" "$VERIFY_INTEGRITY_SCRIPT"
grep -Fq 'not an Android GSI or flashable image' "$GSI_SCRIPT"
if grep -Eiq 'verifiedbootstate|google/shiba|pixel 8|fastboot flash|img2simg' "$GSI_SCRIPT"; then
    echo "Legacy GSI packager contains fabricated Android or flashability claims" >&2
    exit 1
fi

GSI_OUTPUTS_BEFORE="$(directory_signature target/gsi)"
LINUX_OUTPUTS_BEFORE="$(directory_signature target/linux-rootfs)"
if OTA_OUTPUT="$(bash "$OTA_SCRIPT" 2>&1)"; then
    echo "OTA builder unexpectedly succeeded" >&2
    exit 1
else
    OTA_STATUS=$?
fi
[[ "$OTA_STATUS" -eq 1 ]]
grep -Fq 'No authenticated, verified updater' <<<"$OTA_OUTPUT"
[[ "$GSI_OUTPUTS_BEFORE" == "$(directory_signature target/gsi)" ]]
[[ "$LINUX_OUTPUTS_BEFORE" == "$(directory_signature target/linux-rootfs)" ]]

GSI_OUTPUT="$(bash "$GSI_SCRIPT" --dry-run)"
grep -Fq '[DRY RUN] No build or output files will be created.' <<<"$GSI_OUTPUT"
grep -Fq 'not an Android GSI or flashable image' <<<"$GSI_OUTPUT"
[[ "$GSI_OUTPUTS_BEFORE" == "$(directory_signature target/gsi)" ]]
[[ "$LINUX_OUTPUTS_BEFORE" == "$(directory_signature target/linux-rootfs)" ]]

if PATCH_OUTPUT="$(bash "$PATCH_INTEGRITY_SCRIPT" 2>&1)"; then
    echo "Play Integrity patcher unexpectedly succeeded" >&2
    exit 1
else
    PATCH_STATUS=$?
fi
[[ "$PATCH_STATUS" -eq 1 ]]
grep -Fq 'Play Integrity / CTS spoofing is unsupported' <<<"$PATCH_OUTPUT"
if PATCH_DRY_RUN_OUTPUT="$(bash "$PATCH_INTEGRITY_SCRIPT" --dry-run 2>&1)"; then
    echo "Play Integrity patcher unexpectedly accepted --dry-run" >&2
    exit 1
else
    PATCH_DRY_RUN_STATUS=$?
fi
[[ "$PATCH_DRY_RUN_STATUS" -eq 1 ]]
grep -Fq 'Play Integrity / CTS spoofing is unsupported' <<<"$PATCH_DRY_RUN_OUTPUT"
if grep -Eiq 'setprop|resetprop|adb|pixel8|google/shiba|tontos_play_integrity' "$PATCH_INTEGRITY_SCRIPT"; then
    echo "Play Integrity patcher contains device-mutation or spoofing behavior" >&2
    exit 1
fi

VERIFY_HELP="$(bash "$VERIFY_INTEGRITY_SCRIPT" --help)"
grep -Fq 'Read Android boot/build properties' <<<"$VERIFY_HELP"
if grep -Eiq 'MEETS_(BASIC|DEVICE)_INTEGRITY|CTS-certified|Google Wallet Ready|satisfies all requirements|DEVICE_INTEGRITY=true|setprop|resetprop' "$VERIFY_INTEGRITY_SCRIPT"; then
    echo "Integrity report contains a fabricated verdict or mutation command" >&2
    exit 1
fi
grep -Fq 'do not establish Play Integrity, CTS certification, or Verified Boot' "$VERIFY_INTEGRITY_SCRIPT"
grep -Fq 'ro.boot.verifiedbootstate' "$VERIFY_INTEGRITY_SCRIPT"
grep -Fq 'ro.build.fingerprint' "$VERIFY_INTEGRITY_SCRIPT"

cat > "$SCRATCH/fakebin/getprop" <<'EOF'
#!/usr/bin/env bash
case "$1" in
    ro.boot.verifiedbootstate) echo green ;;
    ro.boot.flash.locked) echo 1 ;;
    ro.boot.vbmeta.device_state) echo locked ;;
    ro.boot.veritymode) echo enforcing ;;
    ro.build.type) echo user ;;
    ro.build.tags) echo release-keys ;;
    ro.debuggable) echo 0 ;;
    ro.secure) echo 1 ;;
    ro.build.fingerprint) echo test/device/build ;;
    ro.build.version.security_patch) echo 2099-12-31 ;;
esac
EOF
cat > "$SCRATCH/fakebin/pm" <<'EOF'
#!/usr/bin/env bash
printf 'Users:\n\tUserInfo{0:Owner:13} running\n'
EOF
chmod +x "$SCRATCH/fakebin/getprop" "$SCRATCH/fakebin/pm"
VERIFY_OUTPUT="$(PATH="$SCRATCH/fakebin:/usr/bin:/bin" ANDROID_ROOT=/system bash "$VERIFY_INTEGRITY_SCRIPT")"
grep -Fq 'Verified-boot-state:' <<<"$VERIFY_OUTPUT"
grep -Fq 'Build-fingerprint:' <<<"$VERIFY_OUTPUT"
grep -Fq 'Security-patch-level:' <<<"$VERIFY_OUTPUT"
grep -Fq '2099-12-31' <<<"$VERIFY_OUTPUT"
grep -Fq 'test/device/build' <<<"$VERIFY_OUTPUT"
grep -Fq 'UserInfo{0:Owner:13}' <<<"$VERIFY_OUTPUT"
grep -Fq 'No integrity verdict or certification status was evaluated.' <<<"$VERIFY_OUTPUT"

if grep -Eiq 'SharedPreferences|setprop|RandomAccessFile|persistent_data_block|/dev/block|FRP|verifiedbootstate=green|setLocked|isLocked' "$BOOTLOADER_MANAGER"; then
    echo "Bootloader manager contains a prohibited mutation or fabricated property" >&2
    exit 1
fi
grep -Fq 'getprop' "$BOOTLOADER_MANAGER"
grep -Fq 'KEY_BOOTLOADER_UNKNOWN' "$BOOTLOADER_MANAGER"
grep -Fq 'KEY_BOOTLOADER_LOCKED' "$BOOTLOADER_MANAGER"
grep -Fq 'BootloaderManager.getStatusDisplay' "$SETTINGS_ACTIVITY"
grep -Fq 'android.intent.category.LAUNCHER' "$LAUNCHER_MANIFEST"
grep -Fq 'android.intent.category.HOME' "$LAUNCHER_MANIFEST"
if grep -Eiq 'BootloaderManager\.(setLocked|isLocked)|KEY_BOOTLOADER_ACTION_|KEY_BOOTLOADER_WARNING_' "$SETTINGS_ACTIVITY"; then
    echo "Settings UI exposes bootloader controls that this app cannot perform" >&2
    exit 1
fi

if grep -Eiq 'MEETS_(BASIC|DEVICE|STRONG)|IntegrityVerdict|evaluatePlayIntegrity|Certified|Locked / AVB|/system/bin/su|System\.getProperty|FEATURE_STRONGBOX_KEYSTORE|GrapheneOS-style|SANDBOX_ISOLATED|FOSS_SECURE' "$NFC_PAYMENT_MANAGER"; then
    echo "NFC payment manager contains an inferred integrity or sandbox-security verdict" >&2
    exit 1
fi
grep -Fq 'Unavailable (no trusted attestation API integrated)' "$NFC_PAYMENT_MANAGER"
grep -Fq 'Not evaluated' "$NFC_PAYMENT_MANAGER"
grep -Fq 'NFC on; attestation unavailable' "$NFC_PAYMENT_MANAGER"
grep -Fq 'Not verified by this app' "$NFC_PAYMENT_MANAGER"
grep -Fq 'isolation not verified' "$NFC_PAYMENT_MANAGER"
grep -Fq 'getAttestationStatus()' "$NFC_PAYMENT_MANAGER"

if grep -Eiq '0[[:space:]]*mW|subpixel[^"]*(0|zero)|0% (blue light|luz azul|lumière bleue|Blaulicht|światła niebieskiego|luce blu|синего света|синього світла|蓝光|ブルーライト|blauw licht)|photopic efficiency peak|4 low-power themes|4 Energiespar-Designs|4 temas de bajo consumo|4 thèmes basse conso|4 temi a basso consumo|4 temas de baixo consumo|4 энергосберегающие темы|4 енергоощадні теми|4 种低功耗主题|4つの省電力テーマ|4 zuinige thema' "$LOCALIZATION"; then
    echo "Localized launcher copy contains an unmeasured battery-saving or light-emission claim" >&2
    exit 1
fi
if grep -Eiq 'KEY_(WELCOME_BODY|BACKUP_HEADER).*(cryptograph|encrypt|cifrad|chiffr|crittograf|encript|зашифр|加密|暗号化)' "$LOCALIZATION"; then
    echo "Localized copy claims backups are encrypted, but the backup files are plain JSON" >&2
    exit 1
fi
if grep -Eiq 'Maximum Lifespan|double battery lifespan|Stops charging at 80%|Direct USB Power|without cycling battery|OTA Channel|OTA-Kanal|Kanał OTA|canal OTA|Canal OTA|OTA 渠道|OTAチャンネル|OTA-кана|OTA-kanaal|GitHub OTA|Checking OTA|Checking for updates' "$LOCALIZATION"; then
    echo "Localized copy claims unsupported charging control or an OTA updater" >&2
    exit 1
fi

echo "Safety packaging checks passed."
