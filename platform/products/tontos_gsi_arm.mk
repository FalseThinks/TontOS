#
# Copyright (C) 2026 TontOS Open Source Project
#
# TontOS Universal ARM32 Generic System Image (GSI)
# For budget / entry-level 32-bit ARM smartphones (Android Go / legacy Treble)
#

# Inherit AOSP 32-bit GSI base
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm.mk)

# Relax artifact path requirements for GSI custom vendor overlay
PRODUCT_ENFORCE_ARTIFACT_PATH_REQUIREMENTS := relaxed

# Dynamic partitions support
PRODUCT_USE_DYNAMIC_PARTITIONS := true
PRODUCT_USE_DYNAMIC_PARTITION_SIZE := true

# Inject TontOS Core (TontOSLauncher, permissions, minimalist identity)
$(call inherit-product, vendor/tontos/products/tontos_base.mk)

PRODUCT_NAME := tontos_gsi_arm
PRODUCT_DEVICE := generic
PRODUCT_BRAND := TontOS
PRODUCT_MANUFACTURER := TontOS
PRODUCT_MODEL := TontOS Universal GSI (ARM32)

PRODUCT_SYSTEM_PROPERTIES += \
    ro.tontos.gsi=true \
    ro.tontos.arch=arm \
    ro.config.low_ram=true \
    persist.sys.timezone=UTC
