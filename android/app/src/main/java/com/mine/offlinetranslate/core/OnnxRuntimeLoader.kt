package com.mine.offlinetranslate.core

/**
 * The shared ORT core must be loaded before either the Sherpa JNI or ONNX
 * Runtime Java JNI. Android resolves OrtGetApiBase from the process-global
 * native namespace, so relying on loadLibrary order inside each bridge is not
 * sufficient.
 */
object OnnxRuntimeLoader {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("ort-loader")
        check(nativeLoadGlobal()) { "无法全局加载 libonnxruntime.so" }
        loaded = true
    }

    private external fun nativeLoadGlobal(): Boolean
}
