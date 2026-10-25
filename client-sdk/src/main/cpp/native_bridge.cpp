// JNI glue: Kotlin <-> native, plus the C API declared in mobileclient.h.
#include "mobileclient.h"
#include "internal.h"

#include <android/log.h>

#define TAG "MobileClient"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

JavaVM* g_vm = nullptr;
jclass g_cls = nullptr;  // com.mobileclient.sdk.MobileClient (global ref)
jmethodID g_init = nullptr, g_open = nullptr, g_close = nullptr, g_toggle = nullptr,
          g_setEnabled = nullptr, g_isEnabled = nullptr;

// Gets a JNIEnv for the calling thread (attaching it if needed).
struct EnvScope {
    JNIEnv* env = nullptr;
    bool attached = false;
    EnvScope() {
        if (!g_vm) return;
        if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
            if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) attached = true;
            else env = nullptr;
        }
    }
    ~EnvScope() {
        if (attached) g_vm->DetachCurrentThread();
    }
    bool ok() const { return env != nullptr && g_cls != nullptr; }
};

void callVoid(jmethodID m) {
    EnvScope s;
    if (s.ok() && m) s.env->CallStaticVoidMethod(g_cls, m);
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;

    jclass c = env->FindClass("com/mobileclient/sdk/MobileClient");
    if (!c) {
        env->ExceptionClear();
        LOGW("MobileClient class not found (obfuscated?) - C menu API disabled");
        return JNI_VERSION_1_6;
    }
    g_cls = static_cast<jclass>(env->NewGlobalRef(c));
    g_init = env->GetStaticMethodID(c, "init", "(Landroid/app/Activity;)V");
    g_open = env->GetStaticMethodID(c, "openMenu", "()V");
    g_close = env->GetStaticMethodID(c, "closeMenu", "()V");
    g_toggle = env->GetStaticMethodID(c, "toggleMenu", "()V");
    g_setEnabled = env->GetStaticMethodID(c, "setModuleEnabled", "(Ljava/lang/String;Z)V");
    g_isEnabled = env->GetStaticMethodID(c, "isModuleEnabled", "(Ljava/lang/String;)Z");
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(c);
    return JNI_VERSION_1_6;
}

// ---- called from Kotlin (NativeBridge) -------------------------------------------

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mobileclient_sdk_NativeBridge_nativeInit(JNIEnv*, jobject) {
    return mc_hooks_init() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_mobileclient_sdk_NativeBridge_nativeFps(JNIEnv*, jobject) {
    return mc_get_fps();
}

// ---- C API (callable from your own NDK code) -------------------------------------

extern "C" {

void mc_attach(JNIEnv* env, jobject activity) {
    if (env && g_cls && g_init) env->CallStaticVoidMethod(g_cls, g_init, activity);
}

void mc_open_menu(void) { callVoid(g_open); }
void mc_close_menu(void) { callVoid(g_close); }
void mc_toggle_menu(void) { callVoid(g_toggle); }

int mc_is_module_enabled(const char* id) {
    EnvScope s;
    if (!s.ok() || !g_isEnabled || !id) return 0;
    jstring j = s.env->NewStringUTF(id);
    const jboolean r = s.env->CallStaticBooleanMethod(g_cls, g_isEnabled, j);
    s.env->DeleteLocalRef(j);
    return r == JNI_TRUE ? 1 : 0;
}

void mc_set_module_enabled(const char* id, int enabled) {
    EnvScope s;
    if (!s.ok() || !g_setEnabled || !id) return;
    jstring j = s.env->NewStringUTF(id);
    s.env->CallStaticVoidMethod(g_cls, g_setEnabled, j, enabled ? JNI_TRUE : JNI_FALSE);
    s.env->DeleteLocalRef(j);
}

}  // extern "C"
