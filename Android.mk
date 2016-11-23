LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := unifiedNlp
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := unifiedNlp

unifiednlp_root  := $(LOCAL_PATH)
unifiednlp_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
unifiednlp_build := $(unifiednlp_root)/build
unifiednlp_apk   := build/outputs/apk/unifiedNlpBackend-release-unsigned.apk
$(unifiednlp_root)/$(unifiednlp_apk):
	rm -Rf $(unifiednlp_build)
	mkdir -p $(unifiednlp_out)
	mkdir -p $(unifiednlp_build)
	ln -sf $(unifiednlp_out) $(unifiednlp_build)
	cd $(unifiednlp_root) && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" ./gradlew assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(unifiednlp_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
