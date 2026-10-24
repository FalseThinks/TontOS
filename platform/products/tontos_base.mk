#
# Copyright (C) 2026 FalseThinks <paproken@protonmail.com>
#
# TontOS Core Minimalist System Configuration
# Pure AMOLED, FOSS-first, digital detox base.
#

# Identidad del Sistema TontOS
PRODUCT_BRAND := TontOS
PRODUCT_MANUFACTURER := FalseThinks
PRODUCT_MODEL := TontOS Phone (Minimalist)
PRODUCT_SYSTEM_NAME := tontos
PRODUCT_SYSTEM_BRAND := TontOS
PRODUCT_SYSTEM_MANUFACTURER := FalseThinks
PRODUCT_SYSTEM_MODEL := TontOS Minimalist

# Launcher principal de TontOS
PRODUCT_PACKAGES += \
    TontOSLauncher

# Suite FOSS Preinstalada
PRODUCT_PACKAGES += \
    OrganicMaps \
    AuroraStore \
    FairEmail \
    FossBrowser \
    BreezyWeather \
    FossifyCamera \
    FossifyContacts \
    FossifyFileManager \
    FossifyGallery \
    FossifyMessages \
    FossifyMusicPlayer \
    FossifyPhone

# Excluir Dialer y Contacts nativos de AOSP en favor de la suite Fossify
PRODUCT_PACKAGES_REMOVE += \
    Dialer \
    Contacts

# Permisos por defecto para suite FOSS y TontOSLauncher
PRODUCT_COPY_FILES += \
    vendor/tontos/products/default-permissions-tontos.xml:system/etc/default-permissions/default-permissions-tontos.xml \
    vendor/tontos/products/privapp-permissions-tontos.xml:system_ext/etc/permissions/privapp-permissions-tontos.xml

# Sonidos del sistema y tonos (AOSP Audio Package)
$(call inherit-product-if-exists, frameworks/base/data/sounds/AudioPackage14.mk)

# Propiedades por defecto del sistema TontOS
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.tontos.version=1.0.0-beta \
    ro.tontos.channel=RELEASE \
    ro.tontos.maintainer=FalseThinks \
    ro.tontos.minimalist=true \
    persist.sys.development_settings=1

PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.notification_sound=Tethys.ogg \
    ro.config.alarm_alert=Oxygen.ogg \
    ro.config.ringtone=Titania.ogg

# Perfil Low-RAM (Consumo <= 500 MB RAM) y control de memoria
PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.low_ram=true \
    ro.sys.fw.bg_apps_limit=4 \
    ro.config.max_starting_bg=1 \
    dalvik.vm.heapgrowthlimit=128m \
    dalvik.vm.heapsize=256m \
    dalvik.vm.heapstartsize=8m \
    dalvik.vm.heaptargetutilization=0.75 \
    dalvik.vm.heapminfree=512k \
    dalvik.vm.heapmaxfree=8m \
    persist.sys.zram_enabled=1 \
    ro.lmk.kill_heaviest_task=true \
    ro.lmk.kill_timeout_ms=100 \
    ro.lmk.use_minfree_levels=true \
    ro.statsd.enable=false \
    persist.traced.enable=0

