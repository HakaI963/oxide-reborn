# ONIGAMI native backend (libonigami.so)

Original C++20 desktop-GL-on-ES translation layer for Oxide Launcher.
Owns the full EGL display path and chains to the single host driver.
Not derived from Silica / MobileGlues / Copper Oxide / GL4ES / Zink.

## Layout

- `include/onigami/onigami.h` — public ABI (version/build/probe strings,
  state counters, last-diag).
- `include/onigami/bridge.h` — process/surface lifecycle owned by launcher.
- `include/onigami/backend.h` — single-driver loader, retryable probe cache.
- `include/onigami/translate.h` — shader translation interface.
- `include/onigami/vault.h` — persistent program-vault interface.
- `include/onigami/config.h` — `$ONIGAMI_DATA_DIR/onigami.json` schema.
- `src/egl_context.cpp` — FULL EGL ownership (display/init/config/context/
  current/swap/query/error/bind/proc), honest passthrough chaining.
- `src/gl_frontend.cpp` — null-safe queries, state coalescing, shader hook.
- `src/shader_translate.cpp` — stage-1 line-oriented desktop GLSL to ESSL
  translator (version table, precision injection, legacy keyword migration).
  The glslang/SPIRV-Cross compiler path is pinned, built and linked; it is
  wired in when stage-1 limits are hit on-device (no build change needed).
- `src/program_store.cpp` — memory cache + file vault with budget eviction.
- `src/state_cache.cpp` — UseProgram/bind/cap/viewport coalescing counters.

## EGL contract (differs from Silica deliberately)

ONIGAMI owns the whole display path instead of context-only wrapping, so the
context LWJGL queries is one ONIGAMI created and made current. Errors are pure
passthrough: the layer never reads the backend error flag for logging (that
would consume the application's own read) and never invents success.

## GL_VERSION mapping (documented, suffixed, never bare)

- Backend ES 3.2 verified: `4.6 (Compatibility Profile) ONIGAMI translated
  ES3.2 (backend: ...)`
- Otherwise: `3.3 (Compatibility Profile) ONIGAMI translated (backend: ...)`
- No context current: NULL (honest). Never `""`.

## Build (GitHub Actions only, never local)

See `.github/workflows/onigami.yml`. NDK 27.3, CMake 3.22.1, 4 ABIs.
