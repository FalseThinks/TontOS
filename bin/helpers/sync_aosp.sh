#!/usr/bin/env bash
# Sync pinned AOSP outside repo (T05). Minimal disk footprint profile.
# Downloads ~35-60G shallow (vs 60-100G full). Takes hours regardless.
# Usage: ./bin/helpers/sync_aosp.sh (reads platform/manifests/pinned.json)
set -euo pipefail
# repo + SSL: force system toolchain. Conda's Python (miniconda3)
# earlier in PATH breaks repo SSL certificate verification.
PATH="$(printf '%s' "$PATH" | tr ':' '\n' | grep -v '^/opt/miniconda3' | paste -sd: -)"
export PATH="/usr/bin:/usr/local/bin:/usr/local/sbin:$PATH"
export GIT_SSL_CAINFO=/etc/ssl/certs/ca-certificates.crt
export SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
export REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BRANCH="$(python3 -c "import json;print(json.load(open('$ROOT/platform/manifests/pinned.json'))['branch'])")"
DIR="${AOSP_TREE:-$HOME/aosp/tontos}"
echo "branch=$BRANCH dir=$DIR"
mkdir -p "$DIR"
cd "$DIR"
if [ ! -d .repo ]; then
  # --depth ONLY exists in `repo init` (repo 2.65: `repo sync` does not accept it).
  # Minimal groups: default minus darwin/mips. Do NOT exclude `device`:
  # Cuttlefish target resides in device/google/cuttlefish (groups=device,pdk).
  repo init -u https://android.googlesource.com/platform/manifest -b "$BRANCH" \
    --depth=1 --groups=default,-darwin,-mips
fi
# -c only current branch, no tags/bundles, prune, optimized fetch, -j4 (less tmp/ram).
# NOTE: no --depth here (does not exist in `repo sync`); shallow behavior comes from init.
# Enable `device` group for CF: if an older init used -device,
# the group was stored in .repo/manifests.git/config. `repo init -g`
# reconfigures groups without wiping the tree.
repo init -g default,device,-darwin,-mips
repo sync -c --no-tags --no-clone-bundle --optimized-fetch --prune -j4
# T05 fix: if an older tree was initialized with `-device` (excluding cuttlefish),
# fetch only the 3 CF projects without resyncing the entire device group.
repo sync -c --no-tags --no-clone-bundle --optimized-fetch -j4 \
  device/google/cuttlefish device/google/cuttlefish_prebuilts device/google/cuttlefish_vmm
REV="$(git -C .repo/manifests rev-parse HEAD)"
echo "manifest_rev=$REV"
python3 - "$ROOT/platform/manifests/pinned.json" "$REV" <<'EOF'
import json,sys
p=sys.argv[1]; rev=sys.argv[2]
d=json.load(open(p)); d['manifest_rev']=rev
json.dump(d,open(p,'w'),indent=2); open(p,'a').write('\n')
EOF
echo "PIN updated in platform/manifests/pinned.json"
