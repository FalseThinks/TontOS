#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'EOF'
ERROR: TontOS OTA package generation is unsupported.

No authenticated, verified updater compatible with a defined target device and
partition layout has been established. This script intentionally creates no
package or output files. The Linux rootfs archive is not an Android GSI or OTA
payload and cannot be installed with recovery sideload or update_engine.

OTA generation can be enabled only after a real updater path exists, including
target compatibility checks, authenticated payload signing, and verification by
the device's trusted updater.
EOF
exit 1
