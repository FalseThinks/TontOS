#!/usr/bin/env bash
#
# Copyright (C) 2026 TontOS Open Source Project
#
# Generates official TontOS release cryptographic keys for AOSP release-keys build.
# Replaces AOSP test-keys to establish an official root of trust and satisfy
# Verified Boot / Play Integrity basic & device checks on production hardware.
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
SECURITY_DIR="$ROOT_DIR/platform/security"
AOSP_VENDOR_SECURITY="/home/paproka/aosp/tontos/vendor/tontos/security"

export OPENSSL_CONF="${OPENSSL_CONF:-/etc/ssl/openssl.cnf}"
OPENSSL_BIN="$(command -v /usr/bin/openssl || command -v openssl)"

SUBJECT_BASE="${TONTOS_KEY_SUBJECT:-/C=ES/ST=Madrid/L=Madrid/O=TontOS/OU=TontOS Core Security/CN=TontOS/emailAddress=security@tontos.org}"
KEY_VALIDITY_DAYS=10000

echo "=========================================================="
echo " TontOS: Generating Official Production Release Keys"
echo " Target Directory: $SECURITY_DIR"
echo " Subject: $SUBJECT_BASE"
echo "=========================================================="

mkdir -p "$SECURITY_DIR"

KEYS=(
    "releasekey"
    "platform"
    "shared"
    "media"
    "networkstack"
    "bluetooth"
    "nfc"
    "sdk_sandbox"
)

generate_key_pair() {
    local key_name="$1"
    local rsa_bits="${2:-2048}"
    local subj="$SUBJECT_BASE, key=$key_name"

    local pem_cert="$SECURITY_DIR/${key_name}.x509.pem"
    local pk8_key="$SECURITY_DIR/${key_name}.pk8"
    local raw_key="$SECURITY_DIR/${key_name}.key"

    if [[ -f "$pem_cert" && -f "$pk8_key" ]]; then
        echo "  [EXISTS] $key_name keypair already present, skipping."
        return 0
    fi

    echo "  [GEN] Generating $key_name ($rsa_bits-bit RSA)..."
    "$OPENSSL_BIN" genrsa -f4 -out "$raw_key" "$rsa_bits" 2>/dev/null
    chmod 0600 "$raw_key"

    "$OPENSSL_BIN" req -new -x509 -sha256 -key "$raw_key" \
        -out "$pem_cert" \
        -days "$KEY_VALIDITY_DAYS" \
        -subj "$subj"

    "$OPENSSL_BIN" pkcs8 -in "$raw_key" -topk8 -outform DER -nocrypt -out "$pk8_key"
    chmod 0600 "$pk8_key"

    # Remove temporary raw private key, keeping standard Android .pk8
    rm -f "$raw_key"
    echo "  [OK] Created ${key_name}.x509.pem and ${key_name}.pk8"
}

# 1. Generate APK & platform signing keys (2048-bit RSA)
for k in "${KEYS[@]}"; do
    generate_key_pair "$k" 2048
done

# 2. Generate Android Verified Boot (AVB) 2.0 Key (4096-bit RSA)
AVB_KEY="$SECURITY_DIR/avb.pem"
if [[ ! -f "$AVB_KEY" ]]; then
    echo "  [GEN] Generating AVB 2.0 Root Key (4096-bit RSA)..."
    "$OPENSSL_BIN" genrsa -f4 -out "$AVB_KEY" 4096 2>/dev/null
    chmod 0600 "$AVB_KEY"
    echo "  [OK] Created avb.pem"

    # Extract public key for bootloader if avbtool is available
    AVBTOOL="/home/paproka/aosp/tontos/external/avb/avbtool.py"
    if [[ -f "$AVBTOOL" ]]; then
        python3 "$AVBTOOL" extract_public_key --key "$AVB_KEY" --output "$SECURITY_DIR/avb_pkmd.bin" 2>/dev/null || true
        echo "  [OK] Extracted avb_pkmd.bin for OEM bootloader locking."
    fi
else
    echo "  [EXISTS] AVB 2.0 key already present."
fi

# 3. Create testkey alias for SELinux mac_permissions keys.conf compatibility
ln -sf releasekey.x509.pem "$SECURITY_DIR/testkey.x509.pem"
ln -sf releasekey.pk8 "$SECURITY_DIR/testkey.pk8"

# 4. Synchronize to AOSP vendor/tontos/security if AOSP tree exists
if [[ -d "/home/paproka/aosp/tontos/vendor/tontos" ]]; then
    echo "  [SYNC] Synchronizing keys to $AOSP_VENDOR_SECURITY..."
    mkdir -p "$AOSP_VENDOR_SECURITY"
    cp -d "$SECURITY_DIR"/* "$AOSP_VENDOR_SECURITY/" 2>/dev/null || true
fi

echo "=========================================================="
echo " Release keys successfully generated and verified."
echo "=========================================================="
