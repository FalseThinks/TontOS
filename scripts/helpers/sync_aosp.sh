#!/usr/bin/env bash
# Sync AOSP pineado fuera del repo (T05). Perfil MÍNIMO disco.
# Descarga ~35-60G shallow (vs 60-100G full). Tarda horas igual.
# Uso: ./scripts/sync_aosp.sh   (lee platform/manifests/pinned.json)
set -euo pipefail
# repo + SSL: forzar toolchain del sistema. El python de conda (miniconda3)
# primero en PATH rompe la verificación de certificados de repo.
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
  # --depth SOLO existe en `repo init` (repo 2.65: `repo sync` no lo acepta).
  # Grupos MÍNIMOS: default menos darwin/mips. NO excluir `device`:
  # el target CF vive en device/google/cuttlefish (groups=device,pdk).
  repo init -u https://android.googlesource.com/platform/manifest -b "$BRANCH" \
    --depth=1 --groups=default,-darwin,-mips
fi
# -c solo rama actual, sin tags/bundles, prune, fetch optimizado, -j4 (menos tmp/ram).
# NOTA: sin --depth aquí (no existe en `repo sync`); el shallow viene del init.
# Habilita grupo `device` solo para CF: el init viejo se hizo con -device,
# el grupo quedó grabado en .repo/manifests.git/config. `repo init -g`
# reconfigura grupos sin borrar el árbol.
repo init -g default,device,-darwin,-mips
repo sync -c --no-tags --no-clone-bundle --optimized-fetch --prune -j4
# Reparación T05: si un árbol viejo se inició con `-device` (excluye cuttlefish),
# traer solo los 3 proyectos CF sin resincronizar todo el grupo device.
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
echo "PIN actualizado en platform/manifests/pinned.json"
