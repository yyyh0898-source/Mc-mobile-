#ifndef MOBILECLIENT_H
#define MOBILECLIENT_H

#include <jni.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define MC_API __attribute__((visibility("default")))

/* ---- Client lifecycle / menu (forwarded to the Kotlin SDK) ------------------ */

/** Attach the client to a running Activity (same as MobileClient.init(activity)). */
MC_API void mc_attach(JNIEnv* env, jobject activity);
MC_API void mc_open_menu(void);
MC_API void mc_close_menu(void);
MC_API void mc_toggle_menu(void);
MC_API int  mc_is_module_enabled(const char* module_id);
MC_API void mc_set_module_enabled(const char* module_id, int enabled);

/* ---- Native services -------------------------------------------------------- */

/** Frames per second measured at eglSwapBuffers; -1 when the hook is not installed. */
MC_API int mc_get_fps(void);

/** Search an IDA-style pattern ("48 8B ?? 05 ...") in the executable segments of a loaded
 *  library whose path contains library_name. Returns the absolute address, or 0. */
MC_API uintptr_t mc_find_pattern(const char* library_name, const char* pattern);

/** Inline-hook a function (ShadowHook, UNIQUE mode). Returns a stub handle or NULL. */
MC_API void* mc_hook_function(void* target, void* proxy, void** original);
MC_API int   mc_unhook(void* stub);

#ifdef __cplusplus
}
#endif
#endif /* MOBILECLIENT_H */
