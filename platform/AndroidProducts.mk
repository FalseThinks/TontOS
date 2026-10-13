#
# Copyright (C) 2026 TontOS Open Source Project
#

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/products/tontos_cf_x86_64.mk \
    $(LOCAL_DIR)/products/tontos_gsi_arm64.mk \
    $(LOCAL_DIR)/products/tontos_gsi_arm.mk \
    $(LOCAL_DIR)/products/tontos_gsi_x86_64.mk

COMMON_LUNCH_CHOICES := \
    tontos_cf_x86_64-trunk_staging-userdebug \
    tontos_gsi_arm64-trunk_staging-userdebug \
    tontos_gsi_arm-trunk_staging-userdebug \
    tontos_gsi_x86_64-trunk_staging-userdebug
