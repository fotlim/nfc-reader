LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := NfcReader
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_OWNER := fotlim
LOCAL_SRC_FILES := NfcReader.apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

# 自带签名打包，不占用平台签名
LOCAL_CERTIFICATE := PRESIGNED

# 无需预优化，首次启动时由系统完成
LOCAL_DEX_PREOPT := false

# 普通应用即可（NFC 是 normal 权限，不需要 priv-app）
LOCAL_PRIVILEGED_MODULE := false

include $(BUILD_PREBUILT)
