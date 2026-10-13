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

# Permisos por defecto para suite FOSS y TontOSLauncher
PRODUCT_COPY_FILES += \
    vendor/tontos/products/default-permissions-tontos.xml:system/etc/default-permissions/default-permissions-tontos.xml \
    vendor/tontos/products/privapp-permissions-tontos.xml:system_ext/etc/permissions/privapp-permissions-tontos.xml

# Propiedades por defecto del sistema TontOS
PRODUCT_SYSTEM_EXT_PROPERTIES += \
    ro.tontos.version=1.0.0-beta \
    ro.tontos.channel=RELEASE \
    ro.tontos.maintainer=FalseThinks \
    ro.tontos.minimalist=true

PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.notification_sound= \
    ro.config.alarm_alert= \
    ro.config.ringtone=
