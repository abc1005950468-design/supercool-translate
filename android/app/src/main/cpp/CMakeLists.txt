cmake_minimum_required(VERSION 3.22.1)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

# llama.cpp lives outside the app module; keep the path relative to this file.
set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../../work/llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "llama.cpp not found at ${LLAMA_SRC}")
endif()

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
# The Adreno 830 llama.cpp Vulkan path produced invalid logits in device
# testing. Gemma uses LiteRT-LM for GPU acceleration; keep this failed
# backend out of release APKs so the 300 MB sideload limit is preserved.
option(OFFLINE_TRANSLATE_LLAMA_VULKAN "Build the experimental llama.cpp Vulkan backend" OFF)

if(OFFLINE_TRANSLATE_LLAMA_VULKAN AND DEFINED ENV{VULKAN_SDK})
    # Android's NDK ships Vulkan C headers and libvulkan.so, but not the
    # C++ bindings or shader toolchain required by ggml-vulkan.
    set(Vulkan_INCLUDE_DIR "$ENV{VULKAN_SDK}/include" CACHE PATH "" FORCE)
    set(Vulkan_GLSLC_EXECUTABLE "$ENV{VULKAN_SDK}/bin/glslc.exe"
        CACHE FILEPATH "" FORCE)
    set(SPIRV-Headers_DIR "$ENV{VULKAN_SDK}/share/cmake/SPIRV-Headers"
        CACHE PATH "" FORCE)
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        # Vulkan 1.1 (API 28) core entry points are required by ggml-vulkan.
        # Keep the app at minSdk 26: Android 8 simply skips the Vulkan backend
        # when the dynamic backend loader cannot resolve these newer symbols.
        set(Vulkan_LIBRARY
            "${CMAKE_SYSROOT}/usr/lib/aarch64-linux-android/28/libvulkan.so"
            CACHE FILEPATH "" FORCE)
    else()
        set(Vulkan_LIBRARY
            "${CMAKE_SYSROOT}/usr/lib/x86_64-linux-android/28/libvulkan.so"
            CACHE FILEPATH "" FORCE)
    endif()
endif()

if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_BACKEND_DL ON)
    # KleidiAI repacks all weights at load time and falls back to a very slow
    # scalar path on some phone CPUs; the plain NEON kernels are faster overall.
    set(GGML_CPU_KLEIDIAI OFF)
    # OpenMP spawns a full thread team per core and starves the UI thread on
    # phones (observed as ANR while ASR + LLM run together). Use ggml's own
    # bounded thread pool instead.
    set(GGML_OPENMP OFF)
else()
    set(GGML_SYSTEM_ARCH "x86")
    set(GGML_BACKEND_DL ON)
    set(GGML_CPU_KLEIDIAI OFF)
    set(GGML_OPENMP OFF)
endif()

if(OFFLINE_TRANSLATE_LLAMA_VULKAN AND DEFINED ENV{VULKAN_SDK})
    set(GGML_VULKAN ON)
else()
    set(GGML_VULKAN OFF)
endif()

add_subdirectory(${LLAMA_SRC} build-llama)

add_library(ai-chat SHARED ai_chat.cpp)

target_compile_definitions(ai-chat PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
)

target_include_directories(ai-chat PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src)

target_link_libraries(ai-chat
        llama
        llama-common
        android
        log)

# Android's System.loadLibrary uses local symbol visibility. Both Sherpa's
# JNI and ONNX Runtime Java's JNI resolve OrtGetApiBase dynamically, so load
# the shared ORT core with RTLD_GLOBAL through this tiny bridge.
add_library(ort-loader SHARED ort_loader.cpp)
target_link_libraries(ort-loader
        android
        log)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
        IMPORTED_LOCATION
        "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so")

add_library(nllb-ort SHARED nllb_ort.cpp)
target_include_directories(nllb-ort PRIVATE
        "${CMAKE_CURRENT_LIST_DIR}/onnxruntime")
target_link_libraries(nllb-ort
        onnxruntime
        android
        log)
