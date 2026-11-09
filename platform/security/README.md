# TontOS Official Production Release Keys

This directory holds the cryptographic release keys for official production builds of TontOS.

## Security Notice

> [!CAUTION]
> The private keys (`*.pk8` and `*.pem`) generated in this directory represent the root of trust for TontOS builds, OTA updates, and Android Verified Boot (AVB). They are excluded from version control via `.gitignore`. Store offline and back up securely in hardware vaults.

## Key Hierarchy

| Key | Format | Role |
|-----|--------|------|
| `releasekey` | RSA 2048 | Root product certificate for packages that do not specify a key (`PRODUCT_DEFAULT_DEV_CERTIFICATE`) and OTA payload verification. |
| `platform` | RSA 2048 | Core platform key for system packages (`TontOSLauncher`, Settings, Framework). |
| `shared` | RSA 2048 | Used for packages that share the `android.uid.shared` identity. |
| `media` | RSA 2048 | Used for packages within the media/download process domain. |
| `networkstack` | RSA 2048 | Dedicated key for network stack modules (`com.android.networkstack`). |
| `bluetooth` | RSA 2048 | Dedicated key for Bluetooth subsystem APKs. |
| `nfc` | RSA 2048 | Dedicated key for NFC subsystem and HCE services. |
| `sdk_sandbox` | RSA 2048 | Key for unprivileged SDK runtime sandboxing modules. |
| `avb.pem` | RSA 4096 | Android Verified Boot 2.0 root key used to sign `vbmeta.img`. |
| `avb_pkmd.bin` | Binary | Public key metadata digest flashed to target device bootloader to enforce `ro.boot.verifiedbootstate=green`. |

## Generation

Regenerate keys at any time via:
```bash
./bin/helpers/generate_release_keys.sh
```
