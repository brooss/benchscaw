LOCAL_PATH := $(call my-dir) 
include $(CLEAR_VARS) 
LOCAL_MODULE := ffmpeg-prebuilt 
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libffmpeg.so
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS) 
LOCAL_MODULE := libvpx-prebuilt 
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libvpx.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE    := benchscaw
LOCAL_SRC_FILES := benchscaw.cpp
LOCAL_STATIC_LIBRARIES := cpufeatures libvpx-prebuilt 
LOCAL_CFLAGS 	:= -D__STDC_CONSTANT_MACROS
LOCAL_SHARED_LIBRARIES	:= ffmpeg-prebuilt 
LOCAL_LDLIBS    := -llog -landroid -lEGL -lGLESv1_CM 
LOCAL_ARM_MODE := arm
include $(BUILD_SHARED_LIBRARY)
$(call import-module,android/cpufeatures)
