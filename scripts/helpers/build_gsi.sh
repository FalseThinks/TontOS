#!/usr/bin/env bash
# ==============================================================================
# TontOS Universal Multi-Architecture GSI (Generic System Image) Build Automation
# Target Architectures:
#   - arm64  (Universal 64-bit ARM smartphones: Snapdragon, Tensor, MediaTek, Exynos)
#   - arm    (Universal 32-bit ARM smartphones: Android Go / entry-level / legacy)
#   - x86_64 (Universal 64-bit x86: Emulators, PCs, Chromebooks ARC, tablets)
# Maintainer: FalseThinks <paproken@protonmail.com>
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
AOSP_DIR="${AOSP_DIR:-$HOME/aosp/tontos}"
OUTPUT_ROOT="${OUTPUT_DIR:-$ROOT_DIR/out/gsi}"
CCACHE_DIR="${CCACHE_DIR:-$HOME/.ccache}"

# Default parameters
ARCH="arm64"
PRODUCT_TYPE="tontos" # "tontos" or "aosp"
CUSTOM_TARGET=""
USE_DOCKER=false
SKIP_APK=false
NO_COMPRESS=false
CLEAN_BUILD=false
DRY_RUN=false

# Dynamic parallel job calculation based on hardware specs (~2GB RAM / worker)
TOTAL_CORES=$(nproc 2>/dev/null || echo 4)
TOTAL_RAM_KB=$(grep MemTotal /proc/meminfo 2>/dev/null | awk '{print $2}' || echo 8388608)
RAM_JOBS=$(( TOTAL_RAM_KB / (1024 * 1024 * 2) ))

if (( RAM_JOBS < 2 )); then
    SUGGESTED_JOBS=2
elif (( RAM_JOBS > TOTAL_CORES )); then
    SUGGESTED_JOBS=$TOTAL_CORES
else
    SUGGESTED_JOBS=$RAM_JOBS
fi
BUILD_JOBS="${BUILD_JOBS:-$SUGGESTED_JOBS}"

show_help() {
    cat <<EOF
Uso: $0 [OPCIONES]

Compilación automatizada de GSI (Generic System Image) para TontOS / Android Treble.

Arquitecturas soportadas:
  --arch=arm64       ARM 64-bit universal (Smartphones modernos: Snapdragon, MediaTek, Tensor) [DEFAULT]
  --arch=arm         ARM 32-bit universal (Móviles de entrada / Android Go / IoT)
  --arch=x86_64      x86 64-bit universal (Emuladores, tablets x86, PCs)
  --arch=all         Compilar todas las arquitecturas secuencialmente (arm64, arm, x86_64)

Opciones:
  --product=tontos   GSI nativa TontOS (con Launcher y suite FOSS integrados) [DEFAULT]
  --product=aosp     GSI AOSP pura con overlay de vendor
  --target=TARGET    Sobrescribir el target lunch de AOSP
  --jobs=N, -j N     Número de hilos de compilación (por defecto: $BUILD_JOBS)
  --docker           Compilar dentro del contenedor Docker aislado (Dockerfile.aosp)
  --skip-apk         Omitir compilación previa de TontOSLauncher.apk
  --no-compress      No comprimir con xz el system.img final
  --clean            Limpiar directorio out antes de compilar
  --dry-run          Validar lunch y sincronización de vendor sin ejecutar 'm systemimage'
  --help, -h         Mostrar esta ayuda
EOF
    exit 0
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case "$1" in
        --arch=*) ARCH="${1#*=}" ;;
        -a) ARCH="$2"; shift ;;
        --product=*) PRODUCT_TYPE="${1#*=}" ;;
        --target=*) CUSTOM_TARGET="${1#*=}" ;;
        --jobs=*) BUILD_JOBS="${1#*=}" ;;
        -j) BUILD_JOBS="$2"; shift ;;
        --docker) USE_DOCKER=true ;;
        --skip-apk) SKIP_APK=true ;;
        --no-compress) NO_COMPRESS=true ;;
        --clean) CLEAN_BUILD=true ;;
        --dry-run) DRY_RUN=true ;;
        --help|-h) show_help ;;
        *)
            echo "Opción desconocida: $1" >&2
            echo "Usa $0 --help para ver las opciones disponibles." >&2
            exit 1
            ;;
    esac
    shift
done

resolve_lunch_target() {
    local target_arch="$1"
    if [[ -n "$CUSTOM_TARGET" ]]; then
        echo "$CUSTOM_TARGET"
        return
    fi

    if [[ "$PRODUCT_TYPE" == "tontos" ]]; then
        case "$target_arch" in
            arm64)  echo "tontos_gsi_arm64-trunk_staging-userdebug" ;;
            arm)    echo "tontos_gsi_arm-trunk_staging-userdebug" ;;
            x86_64) echo "tontos_gsi_x86_64-trunk_staging-userdebug" ;;
            *)
                echo "ERROR: Arquitectura no soportada: $target_arch" >&2
                exit 1
                ;;
        esac
    else
        case "$target_arch" in
            arm64)  echo "aosp_arm64-trunk_staging-userdebug" ;;
            arm)    echo "aosp_arm-trunk_staging-userdebug" ;;
            x86_64) echo "aosp_x86_64-trunk_staging-userdebug" ;;
            *)
                echo "ERROR: Arquitectura no soportada: $target_arch" >&2
                exit 1
                ;;
        esac
    fi
}

stage_vendor_files() {
    echo "[*] Sincronizando componentes y vendor tree en AOSP ($AOSP_DIR)..."
    local vendor_dir="$AOSP_DIR/vendor/tontos"
    mkdir -p "$vendor_dir/apps/TontOSLauncher/src"
    mkdir -p "$vendor_dir/apps/prebuilts"
    mkdir -p "$vendor_dir/products"

    # 1. Launcher APK y fuentes
    if [[ -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" ]]; then
        cp -f "$ROOT_DIR/launcher_apk/tontos-launcher.apk" "$vendor_dir/apps/TontOSLauncher/TontOSLauncher.apk"
    fi
    if [[ -f "$ROOT_DIR/launcher_apk/AndroidManifest.xml" ]]; then
        cp -f "$ROOT_DIR/launcher_apk/AndroidManifest.xml" "$vendor_dir/apps/TontOSLauncher/AndroidManifest.xml"
    fi
    if [[ -d "$ROOT_DIR/launcher_apk/src" ]]; then
        rsync -a "$ROOT_DIR/launcher_apk/src/" "$vendor_dir/apps/TontOSLauncher/src/"
    fi

    # 2. Product makefiles
    if [[ -d "$ROOT_DIR/platform/products" ]]; then
        cp -f "$ROOT_DIR/platform/products/"*.mk "$vendor_dir/products/" 2>/dev/null || true
    fi
    if [[ -f "$ROOT_DIR/platform/AndroidProducts.mk" ]]; then
        cp -f "$ROOT_DIR/platform/AndroidProducts.mk" "$vendor_dir/AndroidProducts.mk"
    fi

    # 3. Permissions XMLs
    if [[ -d "$ROOT_DIR/platform/config" ]]; then
        cp -f "$ROOT_DIR/platform/config/"*.xml "$vendor_dir/products/" 2>/dev/null || true
    fi

    # 4. FOSS prebuilts si existen
    if [[ -d "$ROOT_DIR/apps" ]]; then
        cp -rf "$ROOT_DIR/apps/"*.apk "$vendor_dir/apps/prebuilts/" 2>/dev/null || true
    fi
}

build_single_gsi() {
    local target_arch="$1"
    local lunch_target
    lunch_target=$(resolve_lunch_target "$target_arch")
    local arch_out_dir="$OUTPUT_ROOT/$target_arch"
    mkdir -p "$arch_out_dir"

    echo "=========================================================="
    echo " Compilando TontOS GSI para arquitectura: $target_arch"
    echo " Target Lunch:    $lunch_target"
    echo " Directorio AOSP: $AOSP_DIR"
    echo " Salida:          $arch_out_dir"
    echo " Paralelismo:     -j$BUILD_JOBS ($TOTAL_CORES núcleos detectados)"
    echo " Modo de build:   $([ "$USE_DOCKER" = true ] && echo "Docker" || echo "Host nativo")"
    echo "=========================================================="

    if [[ "$CLEAN_BUILD" = true ]]; then
        echo "[*] Limpiando out/target/product en AOSP..."
        rm -rf "$AOSP_DIR/out/target/product"
    fi

    if [[ "$DRY_RUN" = true ]]; then
        echo "[*] MODO DRY-RUN: Validando configuración de entorno y lunch..."
        (
            cd "$AOSP_DIR"
            # shellcheck disable=SC1091
            source build/envsetup.sh
            lunch "$lunch_target"
        )
        echo "[+] Validación de target $lunch_target completada con éxito."
        return 0
    fi

    # Compilación de imagen de sistema GSI
    echo "[*] Iniciando compilación de 'systemimage' para $lunch_target..."
    if [[ "$USE_DOCKER" = true ]]; then
        "$SCRIPT_DIR/docker_build_rom.sh" /bin/bash -c \
            "source build/envsetup.sh && lunch $lunch_target && m -j$BUILD_JOBS systemimage"
    else
        (
            cd "$AOSP_DIR"
            # shellcheck disable=SC1091
            source build/envsetup.sh
            lunch "$lunch_target"
            m -j"$BUILD_JOBS" systemimage
        )
    fi

    # Localizar el archivo system.img generado
    echo "[*] Localizando system.img generado..."
    local system_img=""
    local candidate_dirs=(
        "$AOSP_DIR/out/target/product/tontos_gsi_$target_arch"
        "$AOSP_DIR/out/target/product/aosp_$target_arch"
        "$AOSP_DIR/out/target/product/generic_$target_arch"
        "$AOSP_DIR/out/target/product/generic"
    )

    for d in "${candidate_dirs[@]}"; do
        if [[ -f "$d/system.img" ]]; then
            system_img="$d/system.img"
            break
        fi
    done

    if [[ -z "$system_img" || ! -f "$system_img" ]]; then
        system_img=$(find "$AOSP_DIR/out/target/product" -type f -name "system.img" 2>/dev/null | head -n 1 || true)
    fi

    if [[ -z "$system_img" || ! -f "$system_img" ]]; then
        echo "ERROR: No se encontró system.img tras la compilación de $lunch_target" >&2
        exit 1
    fi

    local gsi_output_img="$arch_out_dir/tontos-gsi-$target_arch.img"
    echo "[+] Copiando $system_img -> $gsi_output_img"
    cp -f "$system_img" "$gsi_output_img"

    # Verificar formato de la imagen generada
    echo "[*] Información de imagen:"
    file "$gsi_output_img" || true
    ls -lh "$gsi_output_img"

    # Generar script de flasheo para el usuario
    local flash_script="$arch_out_dir/flash_gsi.sh"
    cat > "$flash_script" <<FLASH_EOF
#!/usr/bin/env bash
# ==============================================================================
# Script de flasheo rápido TontOS GSI ($target_arch)
# Conecta el teléfono por USB con USB Debugging y OEM Unlocked habilitado.
# ==============================================================================
set -e

IMG_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
IMG_FILE="\$IMG_DIR/tontos-gsi-$target_arch.img"

if [[ ! -f "\$IMG_FILE" && -f "\$IMG_FILE.xz" ]]; then
    echo "[*] Descomprimiendo \$IMG_FILE.xz..."
    xz -d -k "\$IMG_FILE.xz"
fi

if [[ ! -f "\$IMG_FILE" ]]; then
    echo "ERROR: \$IMG_FILE no encontrado." >&2
    exit 1
fi

echo "=== Flasheando TontOS GSI ($target_arch) ==="
echo "1. Reiniciando a fastbootd..."
adb reboot fastboot || fastboot reboot fastboot

echo "2. Esperando dispositivo fastboot..."
fastboot wait-for-device

echo "3. Flasheando partición system..."
fastboot flash system "\$IMG_FILE"

echo "4. ¿Deseas hacer wipe data / factory reset? (Recomendado al instalar GSI)"
read -r -p "Ejecutar fastboot -w? [s/N]: " resp
if [[ "\$resp" =~ ^[sSyY]$ ]]; then
    fastboot -w
fi

echo "5. Reiniciando teléfono..."
fastboot reboot
echo "=== Instalación completada con éxito ==="
FLASH_EOF
    chmod +x "$flash_script"

    # Comprimir si está habilitado
    if [[ "$NO_COMPRESS" = false ]]; then
        echo "[*] Generando archivo comprimido xz (multithread)..."
        xz -T0 -k -f "$gsi_output_img"
        sha256sum "$gsi_output_img.xz" > "$gsi_output_img.xz.sha256"
        echo "[+] Archivo comprimido generado: $gsi_output_img.xz"
        echo "[+] SHA256: $(cat "$gsi_output_img.xz.sha256")"
    else
        sha256sum "$gsi_output_img" > "$gsi_output_img.sha256"
    fi

    echo "=========================================================="
    echo " GSI $target_arch generada exitosamente en $arch_out_dir"
    echo "=========================================================="
}

# --- Ejecución Principal ---

if [[ ! -d "$AOSP_DIR" ]]; then
    echo "ERROR: Árbol AOSP no encontrado en $AOSP_DIR" >&2
    echo "Configura AOSP_DIR=/ruta/aosp o ejecuta scripts/sync_aosp.sh primero." >&2
    exit 1
fi

mkdir -p "$OUTPUT_ROOT"
mkdir -p "$CCACHE_DIR"

# 1. Compilar TontOS Launcher APK si no se omite
if [[ "$SKIP_APK" = false ]]; then
    echo "[*] Paso 1: Verificando / Compilando TontOS Launcher APK firmado..."
    "$SCRIPT_DIR/build_launcher_apk.sh"
fi

# 2. Sincronizar vendor en árbol AOSP
stage_vendor_files

# 3. Compilar según arquitectura seleccionada
if [[ "$ARCH" == "all" ]]; then
    echo "[*] Compilando suite completa de arquitecturas: arm64, arm, x86_64"
    build_single_gsi "arm64"
    build_single_gsi "arm"
    build_single_gsi "x86_64"
else
    build_single_gsi "$ARCH"
fi

echo
echo "=== Proceso GSI finalizado con éxito ==="
echo "Imágenes disponibles en: $OUTPUT_ROOT"
