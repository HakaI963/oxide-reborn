# Oxide Reborn

![Build](https://github.com/HakaI963/oxide-reborn/actions/workflows/ci.yml/badge.svg)
![License](https://img.shields.io/badge/license-GPL--3.0-blue.svg)
![Platform](https://img.shields.io/badge/platform-Android%2026%2B-green.svg)

**Oxide Reborn** is a launcher for **Minecraft: Java Edition** on **Android**. It prepares and runs
real Minecraft installations on Android devices: version resolution, library and asset
verification, Java runtime management, native renderer bridging, on-screen and physical input
handling, and a full instance/multiplayer tooling set.

> [!IMPORTANT]
> **Oxide Reborn is an unofficial, independently maintained build of
> [Zalith Launcher 2](https://github.com/ZalithLauncher/ZalithLauncher2).** It is not affiliated
> with, endorsed by, or supported by the original project. The entire source, including every
> original copyright notice, is preserved under GPL-3.0 — see [`LICENSE`](LICENSE),
> [`THIRD_PARTY.md`](THIRD_PARTY.md) and [`COMPATIBILITY.md`](COMPATIBILITY.md).

## What it does

* **Versions** — vanilla, Fabric / Quilt / Legacy Fabric, Forge / NeoForge / Cleanroom, OptiFine,
  and modpacks from CurseForge, Modrinth, MultiMC and MCBBS.
* **Instances** — per-version RAM, JVM arguments, game arguments, renderer, graphics API, Vulkan
  driver, Java runtime, isolation, custom info, and complete control layouts.
* **Runtimes** — bundled OpenJDK 8 / 17 / 21 / 25 packs per ABI, plus external runtime import and
  automatic version picking.
* **Renderers** — Krypton Wrapper (NG-GL4ES), GL4ES, Kopper Zink, VirGL, Freedreno (Adreno),
  Panfrost (Mali) and downloadable renderer / driver / native plugins.
* **Input** — touch controls with an in-app editor, physical keyboard and mouse, gamepads
  (including SDL direct input), joysticks, gyroscope, and the in-game hotbar.
* **Accounts** — offline accounts, Microsoft accounts, and third-party Yggdrasil servers, with
  skin and cape management.
* **Content** — mods, modpacks, resource packs, shaders, worlds and saves, with browsing,
  search, favourites, downloads and progress reporting.
* **Files** — a separate file manager process with archive extraction, compression, a trash
  system, a text/hex editor and bulk operations.
* **Extras** — per-version log viewer, crash log sharing, festival effects, themes, and a
  Minecraft server list with in-app status.

## Building

GitHub Actions is the authoritative build environment. Local builds are supported but optional.

| Requirement | Version |
|---|---|
| JDK | 21 |
| Gradle | 9.5.0 (wrapper) |
| Android Gradle Plugin | 9.3.0 |
| Kotlin | 2.4.20 |
| compileSdk | Android 37 (minor API level 2) |
| minSdk / targetSdk | 26 / 34 |
| NDK | 25.2.9519653 (ndk-build, `src/main/jni/Android.mk`) |

```bash
git clone https://github.com/HakaI963/oxide-reborn.git
cd oxide-reborn

# unit tests + lint + debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# release APK for a single ABI (all | arm | arm64 | x86 | x86_64)
./gradlew assembleRelease -Darch=arm64
```

Release signing material is **not** committed. CI materialises `OxideLauncher/oxide_launcher.jks`
before Gradle configures; a local release build without a keystore produces an unsigned APK.
Locally you may instead put `STORE_PASSWORD`, `KEY_PASSWORD` and `KEY_ALIAS` in the environment, or
write them into git-ignored `.store_password.txt`, `.key_password.txt` and `.key_alias.txt` files.

Branding lives in [`OxideLauncher/gradle.properties`](OxideLauncher/gradle.properties):
`launcher_app_name` (the Android label), `launcher_name` (internal identifier), `launcher_short_name`
(user agent and clipboard labels) and `url_home`.

## Continuous integration

| Workflow | Trigger | What it does |
|---|---|---|
| [`ci.yml`](.github/workflows/ci.yml) | push to `main`, pull request, manual | installs the Android SDK and NDK, runs unit tests, Android Lint, and assembles a debug APK |
| [`release.yml`](.github/workflows/release.yml) | published release | builds signed release APKs for `all`, `arm`, `arm64`, `x86`, `x86_64` and attaches them to the release |

Optional repository secrets: `OXIDE_KEYSTORE_BASE64`, `OXIDE_KEYSTORE_PASSWORD`,
`OXIDE_KEY_ALIAS`, `OAUTH_CLIENT_ID`, `CURSEFORGE_API_KEY`. Without a keystore secret, CI creates an
ephemeral one for that run, which is enough to produce installable APKs but cannot upgrade-install
over a previously released build.

## Self-update

The launcher checks this repository for a newer release once per hour at most and once every five
seconds at most when you ask for it manually. It never installs anything by itself; it offers the
per-ABI APK for download. The release metadata is the `update/latest_version_md.json` file in this
repository and uses the same schema as upstream.

## License and provenance

Oxide Reborn is licensed under the **GNU General Public License v3.0**. Because it is a modified
version, GPLv3 §7 applies:

1. The program has been renamed and re-versioned so it cannot be confused with the original. The
   splash screen and the About screen both state that this is an unofficial modified build.
2. The copyright notices shown by the program have not been removed — every upstream source file
   still carries its original header.

The full upstream attribution, the third-party component inventory and the list of modifications
are in [`THIRD_PARTY.md`](THIRD_PARTY.md). The identifiers that intentionally keep upstream naming
because they are native ABI, external API or third-party contracts are listed, with reasons, in
[`COMPATIBILITY.md`](COMPATIBILITY.md).

## Credits

* Original project: [ZalithLauncher/ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2)
  and [MovTery](https://github.com/MovTery) — copyright (C) 2025 MovTery and contributors.
* Launch backend: [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher).
* [Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher),
  [HMCL](https://github.com/HuangJunJie2017/HMCL),
  [Plain Craft Launcher 2](https://github.com/Meloong-Git/PCL),
  [Terracotta](https://github.com/burningtnt/Terracotta).
* [LWJGL](https://www.lwjgl.org/), [SDL](https://www.libsdl.org/),
  [MCMod](https://www.mcmod.cn/), and the many libraries listed in
  [`THIRD_PARTY.md`](THIRD_PARTY.md).