package com.mobileclient.sdk

import android.util.Log

/** Loads libmobileclient.so and exposes the few native services Kotlin needs. */
internal object NativeBridge {
    @Volatile var loaded: Boolean = false
        private set

    /** true once the eglSwapBuffers hook is active (FPS counter is exact). */
    @Volatile var hooked: Boolean = false
        private set

    fun load() {
        if (loaded) return
        try {
            System.loadLibrary("mobileclient")
            loaded = true
            hooked = nativeInit()
        } catch (t: Throwable) {
            Log.w(MobileClient.TAG, "native layer unavailable, using Java fallbacks: $t")
        }
    }

    /** Frames per second at eglSwapBuffers, or -1 when the hook is not active. */
    fun fps(): Int = if (hooked) nativeFps() else -1

    private external fun nativeInit(): Boolean
    private external fun nativeFps(): Int
}
