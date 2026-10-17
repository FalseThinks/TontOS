#
# Copyright (C) 2026 TontOS Open Source Project
#
# TontOS Universal x86_64 Generic System Image (GSI)
# For emulators, Chromebooks (ARC), x86 tablets and handhelds
#

# Inherit AOSP x86_64 GSI base
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_x86_64.mk)

# Relax artifact path requirements for GSI custom vendor overlay
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# Dynamic partitions support
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true

# Inject TontOS Core (TontOSLauncher, permissions, minimalist identity)
$(call inherit-product, vendor/tontos/products/tontos_base.mk)

PRODUCT_NAME := tontos_gsi_x86_64
PRODUCT_DEVICE := generic_x86_64
PRODUCT_BRAND := TontOS
PRODUCT_MANUFACTURER := TontOS
PRODUCT_MODEL := TontOS Universal GSI (x86_64)

PRODUCT_SYSTEM_PROPERTIES += \
    ro.tontos.gsi=true \
    ro.tontos.arch=x86_64 \
    persist.sys.timezone=UTC
