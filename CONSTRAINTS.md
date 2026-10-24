# CONSTRAINTS.md — TontOS quality bars (mínimas)

Contrato vigente. Todo agente/PR debe respetarlo. Debilitarlo exige aprobación explícita.

## 1. Claims honestos (sin medición no hay claim)
- No afirmar ahorro batería, mejora rendimiento, OTA funcional, Verified Boot / CTS / Play Integrity sin evidencia medida en hardware soportado.
- Temas visuales ≠ ahorro energía. Release-channel abre páginas release, no es updater.
- `bin/helpers/make_ota_package.sh` fail-closed hasta updater firmado y compatible.

## 2. Batería pref-only
- Límites carga / bypass solo si guest expone interfaz verificada; si no, reportar unsupported.
- Sin sysfs directo sin verificación; preferencias, no control hardware inventado.

## 3. CI consolidado
- Pipeline único: `.github/workflows/main.yml` (Rust, Android, Trivy, Semgrep, UI; CodeQL condicional). No resucitar `ci.yml` / `codeql.yml` / `ui-tests.yml` separados.
- `apps/*.apk` con excepción `!apps/*.apk` en `.gitignore` (solo APKs curados en `apps/`).

## 4. Alcance docs-sync
- Sincronizar docs sin tocar producto: prohibido editar `crates/`, `launcher_apk/`, `bin/` en tareas docs-only.
- No `git add` / commit / push sin pedido explícito.

## 5. Seguridad Android (OWASP MASVS/MASWE)
- Android `targetSdk 34`, sin `WebView`/`addJavascriptInterface`/`TrustManager` inseguro; sin `http://` en repo.
- `AndroidManifest.xml:11-14` `INTERNET+REQUEST_INSTALL_PACKAGES` para browser/APK; eliminados `WRITE_SECURE_SETTINGS`/`DELETE_PACKAGES`; `exported` solo 3 activities.
- Permisos privilegiados fail-closed: `svc`/`settings put` con `Settings.System.canWrite` + `Settings.System.putInt` + `WifiManager.setWifiEnabled`/`BluetoothAdapter` antes de `svc`; DND con `isNotificationPolicyAccessGranted`.
- APK `SettingsActivity:1590` `ACTION_VIEW` desde `getExternalFilesDir` app-scoped + size cap 300MB + `canRequestPackageInstalls` gate + fallback `pm install` explícito; sin `FileProvider`/androidx.
- Rust: `deny.toml` audit/licencia `deny`, `cargo audit/deny` en CI, `Command::new` solo con args hardcode, sin `unsafe` en producto (solo `tont-init`), todo `SharedPreferences` `MODE_PRIVATE`.
- Claims confianza honestos: `make_ota_package.sh:4-16`/`patch_play_integrity.sh` exit 1, `verify_play_integrity.sh:22` sin attestation, `BootloaderManager:10-12` solo `ro.boot.flash.locked`.

## 6. Runtime en VM desechable (checkpoint final)
- E2E Waydroid/Android/HTTP/stop-start/reboot solo en x86_64 runner desechable (`tasks/todo.md:240-243`); local `skip`→`fail` con `TONTOS_DESIGNATED_RUNNER=1`.
