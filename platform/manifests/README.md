# TontOS AOSP pin (T05) — PERFIL MÍNIMO DISCO

Prototipo no-flasheable: `aosp_cf_x86_64_only_phone-userdebug`.
Checkout SIEMPRE fuera del repo (`~/aosp/tontos`), aquí solo metadata pineada.

- Upstream: `https://android.googlesource.com/platform/manifest`
- Branch pin: `android-17.0.0_r1` (Android 17, target CF probado en docs oficiales)
- Alternativa rodante: `android-latest-release` (solo si el pin deja de sincronizar)
- Manifest commit: se registra tras `repo init` en `pinned.json` (`manifest_rev`)
- Sync MÍNIMO: `repo init --groups=default,-darwin,-mips` (NO excluir
  `device`: el target CF vive en device/google/cuttlefish) +
  `sync -c --no-tags --no-clone-bundle --optimized-fetch --prune -j4`
  (~40-65G, NO `--groups=all` que son ~200G).
  NOTA repo 2.65: `--depth` solo existe en `repo init`, no en `repo sync`.
- Build: SOLO módulos (`bootimage systemimage vbmetaimage`, ~25-45G en out/).
  NO `m` completo (~80-120G). En host 15Gi RAM usa `m -j6`.
- ccache tope BAJO: 30G (`ccache -M 30G`). Baja a 20G si `df` >85%.
- `out/` vive dentro del árbol AOSP, NUNCA en `TontoOS/out/`
- Rust (`tontosctl`) solo orquesta: `AOSP_TREE=~/aosp/tontos` + `lunch` + `m`

Recrear misma revisión:

```sh
./bin/helpers/aosp_preflight.sh
./bin/helpers/sync_aosp.sh
```
