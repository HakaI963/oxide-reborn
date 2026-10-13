// SDL JNI_OnLoad isolation for the embedded game JVM.
// Reference: https://github.com/AngelAuraMC/Amethyst-Android/commit/e4c79084d

#include <dlfcn.h>
#include <jni.h>
#include <bytehook.h>
#include <stdlib.h>
#include <string.h>

#include "environ/environ.h"
#include "logger/logger.h"
#include "sdl_hook.h"
#include "utils.h"

typedef void *(*dlsym_func_t)(void *handle, const char *symbol);

/* Silica engine entries (linked via libpojavexec). Wrap only AFTER the
 * bridge has seen real swaps: boot-time resolvers (LWJGL probe, SDL init)
 * keep the raw pointer, so JVM boot behavior is bit-identical. */
void* silica_engine_swap_entry(void);
int silica_engine_swaps_seen(void);
typedef jint (*jni_on_load_func_t)(JavaVM *vm, void *reserved);

typedef bytehook_stub_t (*bytehook_hook_all_fn)(const char *callee_path_name, const char *symbol,
                                                  void *new_func, bytehook_hooked_t hooked, void *hooked_arg);

static void *sdlHandle;
static jni_on_load_func_t originalSdlJniOnLoad;

static jint isolatedSdlJniOnLoad(JavaVM *vm, void *reserved) {
    if (originalSdlJniOnLoad != NULL && pojav_environ->dalvikJavaVMPtr == vm) {
        return originalSdlJniOnLoad(vm, reserved);
    }
    return JNI_VERSION_1_4;
}

static void *customDlsym(void *handle, const char *symbol) {
    void *result = BYTEHOOK_CALL_PREV(customDlsym, dlsym_func_t, handle, symbol);
    BYTEHOOK_POP_STACK();
    if (sdlHandle == NULL) {
        sdlHandle = dlopen("libSDL3.so", RTLD_LOCAL | RTLD_NOW);
    }
    if (sdlHandle != NULL && handle == sdlHandle && symbol != NULL && strcmp(symbol, "JNI_OnLoad") == 0) {
        originalSdlJniOnLoad = (jni_on_load_func_t) result;
        result = (void *) isolatedSdlJniOnLoad;
    }
    // Silica FG: wrap ANY dlsym-resolved eglSwapBuffers (SDL's own EGL
    // path, LWJGL-EGL, ...). Linked call into libpojavexec: present at load,
    // engine self-gates (default off = direct call). Bridge-resolved pointers
    // bypass libc dlsym, so no frame is ever wrapped twice.
    if (result != NULL && symbol != NULL && strcmp(symbol, "eglSwapBuffers") == 0) {
        if (silica_engine_swaps_seen() > 0) {
            void* w = silica_engine_swap_entry();
            if (w != NULL) return w;
        }
    }
    // LWJGL-EGL, …) so generated frames ride every present path, not just the
    // JNI bridge. The engine self-gates (default off = direct call). One slot:
    // all in-process EGL handles serve one driver.
    if (result != NULL && symbol != NULL && strcmp(symbol, "eglSwapBuffers") == 0) {
        static void* silicaWrapFn = NULL;
        static int silicaWrapTried = 0;
        static int silicaWrapBusy = 0;
        if (!silicaWrapFn && !silicaWrapTried && !silicaWrapBusy) {
            silicaWrapTried = 1;
            silicaWrapBusy = 1;
            void* sh = dlopen("libsilica_fg.so", RTLD_LAZY | RTLD_LOCAL);
            if (sh) {
                typedef void* (*wrapfn_t)(void*);
                wrapfn_t wf = (wrapfn_t)dlsym(sh, "silica_fg_wrap_swap");
                if (wf) silicaWrapFn = wf(result);
            }
            silicaWrapBusy = 0;
        }
        if (silicaWrapFn) return silicaWrapFn;
    }
    // LWJGL 的 org.lwjgl.sdl 绑定等消费方经 dlsym 解析 SDL 函数指针后直接调用，
    // hook_all 的 GOT 导入补丁拦截不到；在解析出口统一换成 sdl_hook 的 dlsym 层代理
    if (result != NULL && symbol != NULL) {
        void *proxy = sdlDlsymProxy(symbol, result);
        if (proxy != NULL) {
            LOG_TO_I("SDL_Hook: dlsym proxy for %s", symbol);
            result = proxy;
        }
    }
    return result;
}

void create_sdl_dlopen_hooks(bytehook_hook_all_fn hookAll) {
    if (hookAll == NULL) return;
    hookAll(NULL, "dlsym", (void *) customDlsym, NULL, NULL);
}