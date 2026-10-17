#
# Copyright (C) 2026 TontOS Open Source Project
#
# TontOS Universal ARM64 Generic System Image (GSI)
# For Project Treble compliant 64-bit ARM smartphones
# (Snapdragon, MediaTek, Tensor, Exynos, UNISOC)
#

# Inherit AOSP 64-bit GSI base
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)

# Relax artifact path requirements for GSI custom vendor overlay
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# Dynamic partitions support (mandatory for modern Treble GSIs)
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true

# Inject TontOS Core (TontOSLauncher, permissions, minimalist identity)
$(call inherit-product, vendor/tontos/products/tontos_base.mk)

PRODUCT_NAME := tontos_gsi_arm64
PRODUCT_DEVICE := generic_arm64
PRODUCT_BRAND := TontOS
PRODUCT_MANUFACTURER := TontOS
PRODUCT_MODEL := TontOS Universal GSI (ARM64)

PRODUCT_SYSTEM_PROPERTIES += \
    ro.tontos.gsi=true \
    ro.tontos.arch=arm64 \
    persist.sys.timezone=UTC
