#!/usr/bin/env bash
# Host preflight check for modular AOSP build (T03/T06). Read-only except ccache.
set -euo pipefail

fail=0
need() { command -v "$1" >/dev/null 2>&1 || { echo "MISSING: $1"; fail=1; }; }
echo "== Tools =="
for t in repo git python3 java adb ccache lunch m; do
  if command -v "$t" >/dev/null 2>&1; then echo "ok: $t $(command -v "$t")";
  else echo "MISS: $t"; [ "$t" = lunch ] || [ "$t" = m ] || fail=1; fi
done
echo "(lunch/m appear after 'source build/envsetup.sh', do not install globally)"
echo
echo "== Java =="
java -version 2>&1 | head -2
echo
echo "== Disk / Memory =="
df -h /home | tail -1
free -h | head -2
nproc
echo
echo "== KVM =="
ls -l /dev/kvm 2>&1 || fail=1
echo
echo "== ccache =="
ccache -s 2>&1 | head -5
echo
if [ "$fail" -ne 0 ]; then echo "PREFLIGHT: FAIL"; exit 1; fi
echo "PREFLIGHT: OK (lunch/m only exist inside AOSP tree)"
