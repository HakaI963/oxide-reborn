# Oxide Reborn — Preserved Compatibility Identifiers

Oxide Reborn renames product branding and the Android application identity, but a number of
identifiers must stay exactly as they are because they are part of a contract with something this
repository does not own. This file records each one, what depends on it, and whether a user can
see it.

Nothing in this list is user-facing branding; they are all binary layouts, external APIs or
third-party contracts.

## 1. Native library file names

| Identifier | Depends on | User-visible |
|---|---|---|
| `libpojavexec.so` | `Android.mk` (`LOCAL_MODULE := pojavexec`), `NativeLibraryLoader.loadPojavLib()`, `System.loadLibrary("pojavexec")`, and the in-repo LWJGL replacements `LWJGL/3.3.3` / `LWJGL/3.4.1` `GLFW.<clinit>` which call `System.loadLibrary("pojavexec")` and `Library.loadNative(GLFW.class, "org.lwjgl.glfw", "libpojavexec.so", true)` | No |
| `libpojavexec_awt.so` | `Android.mk` (`LOCAL_MODULE := pojavexec_awt`), `NativeLibraryLoader.loadPojavAWTLib()` | No |
| `libexithook.so`, `libvulkan_check.so`, `libdriver_helper.so`, `liblinkerhook.so`, `libawt_xawt.so`, `libflite*.so` | `Android.mk` module names + `System.loadLibrary` call sites | No |
| `libterracotta.so` | `Terracotta/src/main/jniLibs/<abi>/` prebuilt Rust binaries and `TerracottaAndroidAPI` | No |

These names are inherited from the Pojav-derived launch backend. Renaming them would require
changing the ndk-build module graph, three `System.loadLibrary` call sites, the vendored LWJGL
`GLFW` classes that are merged into the game classpath, and the `libpojavexec.so` name that those
vendored LWJGL classes pass to `SharedLibrary`. They are internal artefact names, not branding.

## 2. Native environment variables read by C code

`OxideLauncher/src/main/jni/**` reads the following from the game process environment. They are
produced by `Launcher.setJavaEnv` / `GameLauncher.setRendererEnv` and consumed by the native
renderer bridges, `egl_bridge.c`, `osmesa_loader.c`, `egl_loader.c`, `input_bridge_v3.c` and
`lwjgl_dlopen_hook.c`:

`POJAV_NATIVEDIR`, `POJAV_RENDERER`, `POJAVEXEC_EGL`, `POJAV_FFMPEG_PATH`,
`POJAV_ZINK_PREFER_SYSTEM_DRIVER`, `POJAV_VSYNC_IN_ZINK`, `POJAV_SDL_REUSE_WINDOW`,
`POJAV_EMUI_ITERATOR_MITIGATE`, `LIBGL_ES`, `LIBGL_GLES`, `LIB_MESA_NAME`, `SDL_EGL_LIBRARY`,
`SDL_OPENGL_LIBRARY`, `VULKAN_PTR`, `GALLIUM_DRIVER`, `MESA_*`, `XDG_DATA_HOME`.

In addition the JVM is started with `-Dpojav.path.minecraft` and `-Dpojav.path.private.account`,
which are read by Minecraft-side mods (for example `lwjgl3ify`) that follow the PojavLauncher
convention.

User-visible: no.

## 3. JNI symbols that borrow third-party namespaces

`input_bridge_v3.c`, `awt_bridge.c`, `xawt_fake.c` and `gl_bridge.c` export symbols in namespaces
that belong to LWJGL, OpenJDK, Caciocavallo and Android:

* `Java_org_lwjgl_glfw_*`, `Java_org_lwjgl_opengl_PojavRendererInit_*`, `Java_org_lwjgl_vulkan_VK_*`
* `Java_java_awt_*`, `Java_sun_awt_UNIXToolkit_*`
* `Java_net_java_openjdk_cacio_ctc_CTC{Clipboard,DesktopPeer}_*` and the legacy
  `Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_*` aliases
* `Java_android_view_Surface_nativeGetBridgeSurfaceAWT`,
  `Java_android_os_OpenJDKNativeRegister_nativeRegisterNatives`
* `Java_com_oracle_dalvik_VMLauncher_launchJVM` (kept explicitly by `proguard-rules.pro`)

These must match the class names that the JVM looks up at runtime; several of the classes live
inside LWJGL jars or inside the game JVM, not in this repository. User-visible: no.

By contrast, symbols for classes owned by this repository **were** migrated:
`Java_dev_oxide_launcher_bridge_OxideBridge_*`, `Java_dev_oxide_launcher_bridge_LoggerBridge_*`,
`Java_dev_oxide_launcher_utils_device_VulkanChecker_*`,
`Java_dev_oxide_launcher_game_sdl_SdlBridge_*`, together with the matching `FindClass` paths
(`dev/oxide/launcher/bridge/OxideNativeInvoker`,
`dev/oxide/launcher/game/input/CriticalNativeTest`,
`dev/oxide/launcher/utils/device/VulkanCapabilities`, `dev/oxide/launcher/bridge/FliteTts`).
`proguard-rules.pro` was updated so R8 keeps the renamed classes.

## 4. Borrowed Java namespaces kept as-is

| Package | Why it stays |
|---|---|
| `net.burningtnt.terracotta` | The prebuilt `libterracotta.so` binaries hard-code `net/burningtnt/terracotta/TerracottaAndroidAPI` and the native method names `start0`, `getState0`, `setWaiting0`, `setScanning0`, `setGuesting0`, `verifyRoomCode0`, `getMetadata0`, `prepareExportLogs0`, `finishExportLogs0`, `panic0`. Renaming the Java class breaks LAN multiplayer with `NoClassDefFoundError`. |
| `org.lwjgl.**` (in `OxideLauncher/src/main/java` and `LWJGL/**`) | Replaces LWJGL classes on the game classpath; class names are part of the Minecraft/LWJGL API. |
| `org.libsdl.app.**` | SDL3 Java bindings; the natives live in `libSDL3.so` from `libs/SDL-release.aar`. |
| `org.jackhuang.hmcl.util.**` | HMCL-derived utilities kept under their original namespace so the derivation stays traceable. |
| `com.oracle.dalvik.VMLauncher` | Launched from the JVM boot path. |

## 5. Third-party plugin contracts

Renderer, driver, native and ffmpeg plugins are **other installed APKs** that declare their kind
through `AndroidManifest` `<meta-data>` keys. These keys are the public plugin ABI and are
recognised exactly as upstream defines them:

`fclPlugin`, `fclPlugin_V2`, `FCLNativePlugin`, `pojavEnv`, `renderer`, `des`, `minMCVer`,
`maxMCVer`, `driver`.

The `zalithRendererPlugin` key is the upstream brand's own key. Oxide Reborn **accepts both**
`oxideRendererPlugin` and `zalithRendererPlugin` so that already published renderer plugins keep
working; it does not drop support for either.

User-visible: only insofar as a user can install a third-party plugin APK that was built for
upstream Oxide Reborn — which is the point of keeping the contract.

## 6. External applications and services

| Reference | Why it stays |
|---|---|
| `<package android:name="net.kdt.pojavlaunch.ffmpeg"/>` in `AndroidManifest.xml` and `FFmpegPluginManager` | Package name of an unrelated third-party app used as the optional ffmpeg/Twitch provider. |
| `https://github.com/ZalithLauncher/NativeLibPlugin/releases` | Prebuilt native plugin bundle consumed by `NativePluginManager`. It contains ABI-compatible `.so` files only, so it is a working artifact source rather than branding. |
| `https://github.com/FCL-Team/FoldCraftLauncher`, `ShirosakiMio/FCLRendererPlugin`, `FCL-Team/FCLDriverPlugin` | Third-party renderer/driver plugin releases. |
| `PojavLauncher`, `Fold Craft Launcher`, `HMCL`, `Plain Craft Launcher 2`, `MCMod` entries in the About screen, `res/drawable/img_launcher_*.png`, `res/raw/*_license.txt`, and the TextMate `NOTICE.md` | Required legal attribution for code and assets that Oxide Reborn actually derives from. |

## 7. Update channel

The self-update check reads `update/latest_version_md.json` from this repository through the
GitHub Contents API, with `https://cdn.jsdelivr.net/gh/HakaI963/oxide-reborn@main/update/...` as a
fallback for networks that cannot reach `api.github.com`. The JSON schema is unchanged from
upstream (`code`, `version`, `created_at`, `files`, `default_body`, `bodies`, optional
`cloud_drives`), so existing tooling keeps working.

## 8. On-disk data created by a previous Zalith Launcher 2 install

`applicationId` changed to `dev.oxide.launcher`, therefore Android treats Oxide Reborn as a
**new application**: game directories, accounts, MMKV settings and control layouts created by an
upstream Zalith Launcher 2 install live under the old package's private directory and are not
imported. Game folders inside shared storage (the `.minecraft` directory selected by the user) are
unaffected and remain usable — Oxide Reborn reads and writes the standard Minecraft layout
(`versions/`, `libraries/`, `assets/`, `resourcepacks/`, `shaderpacks/`, `saves/`).

Per-instance launcher data lives in a subdirectory named after the launcher identifier inside each
version folder, and the settings namespace follows the launcher identifier, so a new identifier
means new, empty per-instance metadata.