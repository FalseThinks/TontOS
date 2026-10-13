#
# Copyright (C) 2026 TontOS Open Source Project
#
# TontOS Cuttlefish Virtual Target (x86_64)
# Fresh vanilla minimalist system without Google / AOSP bloatware
#

# Heredar base 64-bit y sistema genérico de AOSP
$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit_only.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/generic_system.mk)

PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# Heredar extensiones de sistema (telefonía y base)
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_system_ext.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_system_ext.mk)

# Componentes esenciales de system_ext (Settings, SystemUI, StorageManager)
# Excluyendo deliberadamente: Launcher3QuickStep, AvatarPicker, WallpaperCropper
PRODUCT_PACKAGES += \
    AccessibilityMenu \
    Provision \
    Settings \
    StorageManager \
    SystemUI \
    preinstalled-packages-handheld-system-ext.xml

# Heredar componentes de producto y telefonía básica
$(call inherit-product, $(SRC_TARGET_DIR)/product/telephony_product.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/media_product.mk)

# Componentes de producto estrictamente esenciales
# Excluyendo deliberadamente: QuickSearchBox, Gallery2, Browser2, DeskClock, Music, Calendar
PRODUCT_PACKAGES += \
    Contacts \
    LatinIME \
    SettingsIntelligence \
    frameworks-base-overlays

# Capa de hardware y vendor de Cuttlefish
$(call inherit-product, device/google/cuttlefish/shared/phone/device_vendor.mk)
$(call inherit-product, packages/modules/Virtualization/apex/product_packages.mk)
$(call inherit-product, device/google/cuttlefish/vsoc_x86_64/bootloader.mk)

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/aosp_excluded_hardware.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/aosp_excluded_hardware.xml

# Window Extensions
$(call inherit-product, $(SRC_TARGET_DIR)/product/window_extensions.mk)

# Inyectar TontOS Core (TontOSLauncher, propiedades, branding)
$(call inherit-product, vendor/tontos/products/tontos_base.mk)

PRODUCT_NAME := tontos_cf_x86_64
PRODUCT_DEVICE := vsoc_x86_64_only
PRODUCT_BRAND := TontOS
PRODUCT_MANUFACTURER := TontOS
PRODUCT_MODEL := TontOS Phone x86_64

PRODUCT_VENDOR_PROPERTIES += \
    ro.soc.manufacturer=$(PRODUCT_MANUFACTURER) \
    ro.soc.model=$(PRODUCT_DEVICE)

PRODUCT_IGNORE_ALL_ANDROIDMK := true

ifeq ($(TARGET_PRODUCT),tontos_cf_x86_64)
PRODUCT_SOONG_ONLY := $(RELEASE_SOONG_ONLY_CUTTLEFISH)
endif
