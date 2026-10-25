// Native services: frame counter hook (eglSwapBuffers), pattern scanner and a thin
// wrapper over ShadowHook so your own game hooks can be written in C++.
#include "mobileclient.h"
#include "internal.h"

#include <EGL/egl.h>
#include <android/log.h>
#include <link.h>
#include <stdlib.h>
#include <string.h>

#include <atomic>
#include <chrono>
#include <mutex>
#include <vector>

#include "shadowhook.h"

#define TAG "MobileClient"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

namespace {

inline int64_t nowNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
               std::chrono::steady_clock::now().time_since_epoch())
        .count();
}

std::atomic<bool> g_hooked{false};
std::atomic<int> g_fps{0};
std::atomic<int64_t> g_lastFrameNs{0};
int64_t g_windowStartNs = 0;  // touched by the render thread only
int g_windowFrames = 0;

typedef EGLBoolean (*SwapFn)(EGLDisplay, EGLSurface);
SwapFn g_origSwap = nullptr;

void onFrame() {
    const int64_t now = nowNs();
    g_lastFrameNs.store(now, std::memory_order_relaxed);
    if (g_windowStartNs == 0) {
        g_windowStartNs = now;
        g_windowFrames = 0;
        return;
    }
    ++g_windowFrames;
    const int64_t dt = now - g_windowStartNs;
    if (dt >= 1000000000LL) {
        g_fps.store(static_cast<int>((g_windowFrames * 1000000000LL + dt / 2) / dt),
                    std::memory_order_relaxed);
        g_windowStartNs = now;
        g_windowFrames = 0;
    }
}

EGLBoolean proxySwapBuffers(EGLDisplay display, EGLSurface surface) {
    onFrame();
    return g_origSwap ? g_origSwap(display, surface) : EGL_FALSE;
}

bool ensureShadowHook() {
    static std::once_flag once;
    static bool ok = false;
    std::call_once(once, [] {
        const int rc = shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
        ok = (rc == 0);
        if (!ok) LOGW("shadowhook_init failed: %d", rc);
    });
    return ok;
}

// ---- pattern scanner ---------------------------------------------------------

std::vector<int> parsePattern(const char* s) {
    std::vector<int> out;  // -1 = wildcard
    const char* p = s;
    while (*p) {
        while (*p == ' ') ++p;
        if (!*p) break;
        if (*p == '?') {
            out.push_back(-1);
            while (*p == '?') ++p;
            continue;
        }
        char* end = nullptr;
        const long v = strtol(p, &end, 16);
        if (end == p) break;
        out.push_back(static_cast<int>(v & 0xFF));
        p = end;
    }
    return out;
}

struct ScanCtx {
    const char* lib;
    const std::vector<int>* pat;
    uintptr_t result;
};

int scanCallback(struct dl_phdr_info* info, size_t, void* data) {
    auto* ctx = static_cast<ScanCtx*>(data);
    if (!info->dlpi_name || !strstr(info->dlpi_name, ctx->lib)) return 0;
    const std::vector<int>& p = *ctx->pat;
    const size_t n = p.size();
    for (int i = 0; i < info->dlpi_phnum; ++i) {
        const ElfW(Phdr)& ph = info->dlpi_phdr[i];
        if (ph.p_type != PT_LOAD || !(ph.p_flags & PF_X) || !(ph.p_flags & PF_R)) continue;
        const auto* start = reinterpret_cast<const uint8_t*>(info->dlpi_addr + ph.p_vaddr);
        const size_t len = ph.p_memsz;
        if (len < n) continue;
        for (size_t off = 0; off + n <= len; ++off) {
            size_t j = 0;
            while (j < n && (p[j] < 0 || start[off + j] == static_cast<uint8_t>(p[j]))) ++j;
            if (j == n) {
                ctx->result = reinterpret_cast<uintptr_t>(start + off);
                return 1;  // stop iterating
            }
        }
    }
    return 0;
}

}  // namespace

bool mc_hooks_init() {
    static std::once_flag once;
    std::call_once(once, [] {
        if (!ensureShadowHook()) return;
        void* stub = shadowhook_hook_sym_name("libEGL.so", "eglSwapBuffers",
                                              reinterpret_cast<void*>(proxySwapBuffers),
                                              reinterpret_cast<void**>(&g_origSwap));
        if (stub == nullptr) {
            const int e = shadowhook_get_errno();
            LOGW("hooking eglSwapBuffers failed: %d (%s)", e, shadowhook_to_errmsg(e));
            return;
        }
        g_hooked.store(true);
        LOGI("eglSwapBuffers hooked");
    });
    return g_hooked.load();
}

extern "C" {

int mc_get_fps(void) {
    if (!g_hooked.load()) return -1;
    if (nowNs() - g_lastFrameNs.load(std::memory_order_relaxed) > 1500000000LL) return 0;
    return g_fps.load(std::memory_order_relaxed);
}

uintptr_t mc_find_pattern(const char* library_name, const char* pattern) {
    if (!library_name || !pattern) return 0;
    const std::vector<int> pat = parsePattern(pattern);
    if (pat.empty()) return 0;
    ScanCtx ctx{library_name, &pat, 0};
    dl_iterate_phdr(scanCallback, &ctx);
    return ctx.result;
}

void* mc_hook_function(void* target, void* proxy, void** original) {
    if (!target || !proxy || !ensureShadowHook()) return nullptr;
    return shadowhook_hook_func_addr(target, proxy, original);
}

int mc_unhook(void* stub) {
    return stub ? shadowhook_unhook(stub) : -1;
}

}  // extern "C"
