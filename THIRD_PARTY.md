# Third-Party Components and Licenses

Oxide Launcher is a derivative work of **Zalith Launcher 2** (GPL-3.0). This file records the
provenance of the upstream baseline, the third-party components that are redistributed or linked
by the build, and the modifications made by the Oxide Launcher project.

## Provenance

| | |
|---|---|
| Upstream project | [ZalithLauncher/ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2) |
| Upstream license | GNU General Public License v3.0 (see [`LICENSE`](LICENSE)) |
| Upstream baseline commit | `68a441b0e4525ce6730efb9868befc69324d1d70` (`main`, 2026-10-02) |
| Secondary upstream project | [Star1xr/ZalithLauncher2Plus](https://github.com/Star1xr/ZalithLauncher2Plus) (GPL-3.0, archived 2026-08-14) |
| Secondary upstream baseline | `7d7578c5542f65751af4f50963fd74aaa6c5bf1c` (`main`) |
| Secondary upstream contributors | Copyright © 2026 Star1xr <166748405+Star1xr@users.noreply.github.com> |
| This repository | [HakaI963/oxide-reborn](https://github.com/HakaI963/oxide-reborn) — independent repository, **not** a fork of either upstream project |

## Account and authentication code adapted from ZalithLauncher2Plus

The account system in this launcher is not written from scratch. The following behaviour was
adapted from [Star1xr/ZalithLauncher2Plus](https://github.com/Star1xr/ZalithLauncher2Plus)
(Copyright © 2026 Star1xr, GPL-3.0), which is a fork of Zalith Launcher 2:

| Upstream commit | Subject | What was adapted |
|---|---|---|
| `4d513f687aff8a905e65213978633a2f3ad5fd72` | save Microsoft account even if skin download fails | A failing skin/cape download no longer discards an already authenticated Microsoft login |
| `51a77c9864381c6c36bf9f46ee74bfbb055cecdd` | Add cape support to offline Yggdrasil server | `OfflineYggdrasilServer.addCharacter` now loads the account's cape, activating the `/textures` cape path that was declared but never populated |
| `d98a271d531e10ea75c2fd7cb27c3a2d9a17f071` | Start offline Yggdrasil server when cape file exists even without skin | The offline server is also started for a cape-only account |
| `50a4fec194b9608d2355d7baf74e7e2ed6e65114` | Inject local cape for Ely.by accounts via offline Yggdrasil server | The Ely.by launch branch that serves a locally selected cape through the local server |
| `6d852486982e3eeb25d95086db3dbbbf2da733d6` | Add client-side cape support for Ely.by accounts | `Account.isElyByAccount()` and the extended `isSkinChangeAllowed()` / account screen gates |
| `9660b25aa7f8c29be4944e9ecfac5b483a9e9261` | One-tap Ely.by auth server | The Ely.by auth server entry and its published authlib-injector endpoint |
| `91a730ffb99c6d60f07521c4af7af539ecb96e77` | Cape file validation | `validateCapeFile()` (64x32) and the local cape import that makes the offline cape path reachable |
| `5cb5f327d480d41bcb45c40e1010459b78716061`, `498a5cba10c2f6115104870a7890a9473fe2e3db` | Importing/exporting settings/configs/accounts | Settings and account backup/restore, rewritten to never write credentials |
| `b0dd969c5f3983407a12761b2401fb2dc533e556` | "OPTIMIZE THE CODE AND FIX OFFLINE ACCOUNTS" | Removal of the region/Microsoft account lockout that made offline accounts unusable outside Greater China, and the `loadFromProfileID` null-type wildcard |

Files carrying this adaptation state it in a dual-upstream header naming both
`MovTery <movtery228@qq.com>` and `Star1xr <166748405+Star1xr@users.noreply.github.com>`, because
the upstream Plus files that were adapted do not carry a `Copyright` line of their own.

Deliberate deviations from the Plus implementation, each with a reason:

* Plus's backup writes every credential to `/storage/emulated/0/zalithplus/`; here the backup
  schema has no credential property and the file is only written where the user asks for it.
* Plus does not strip the trailing slash when comparing Ely.by URLs, so its Ely.by detection can
  never match a server added through the UI. Here the comparison ignores trailing slashes.
* Plus's account-management screen was replaced by a dual-panel layout with drag-and-drop account
  reordering. Oxide keeps the upstream layout, because the reorder is not persisted anyway.
* Plus's cape gallery (`minecraftcapes.net`) and per-account cape collection were not adapted; they
  are a cosmetics feature served by an unofficial third-party API, not authentication.
* Plus's removal of the automatic promotion of a freshly saved account to the current account was
  not adopted, because that promotion is what keeps a re-login on the same account.

Every first-party source file carries the original upstream GPL-3.0 header, including
`Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors`. Those headers are preserved
verbatim: they are a licence condition of GPLv3 §7(b) and of the upstream project's own additional
terms. Oxide Launcher branding is applied to the product identity, not to the copyright attribution.

Upstream source directories that are derived from other open source projects keep their own
licences, headers and notices:

* `OxideLauncher/src/main/java/org/jackhuang/hmcl/util/**` — derived from HMCL (GPL-3.0), header preserved.
* `OxideLauncher/src/main/java/org/libsdl/app/**` — SDL3 Java bindings (zlib), header preserved.
* `LWJGL/**` — LWJGL (BSD-3-Clause) with Oxide Launcher patches; `LWJGL/patches/**` is GPL-3.0.
* `OxideLauncher/src/main/assets/textmate/**` — TextMate grammars, see
  [`OxideLauncher/src/main/assets/textmate/NOTICE.md`](OxideLauncher/src/main/assets/textmate/NOTICE.md) (MIT).
* `OxideLauncher/src/main/jni/**` — native bridge derived from PojavLauncher, Fold Craft Launcher,
  Amethyst-Android and lwjgl3, attribution retained in the source comments.
* `OxideLauncher/src/main/res/raw/*_license.txt` — full licence texts shipped inside the APK and
  surfaced in the in-app "Third-Party Libraries" screen.

## Modifications by Oxide Launcher

The following changes were made on top of the upstream baseline. They are described here because
GPLv3 §5(a) requires modified versions to be distinguishable and §7(c) requires the product to be
renamed.

1. **Product identity** — renamed to *Oxide Launcher*: application label, launcher title, splash
   branding, Android theme, task description, log prefixes, the self-update channel and all
   repository URLs now point at `HakaI963/oxide-reborn`.
2. **Android application identity** — `applicationId` / `namespace` moved to `dev.oxide.launcher`
   (debug builds use `dev.oxide.launcher.debug`); all Kotlin/Java package roots, JNI symbol names,
   `FindClass` strings and ProGuard keep rules were migrated in lockstep.
3. **Native ABI surface left intact on purpose** — see [`COMPATIBILITY.md`](COMPATIBILITY.md).
4. **Build, CI and release pipeline** rewritten for `oxide-reborn` (unit tests, lint, debug APK,
   per-ABI release APKs, automatic release upload).
5. **Signing** — upstream keystores removed from the repository; release signing material is
   supplied by CI.
6. **Housekeeping** — unused `net.zetetic:sqlcipher-android` dependency removed, `*-sources.jar`
   excluded from the APK, update-check fallback re-pointed at a reachable mirror.
7. **Account and authentication** — offline accounts no longer require a Microsoft login or a
   Chinese locale, offline and Ely.by accounts can carry a local cape, deleting an account also
   deletes its cape, and account/settings backup and restore were added. Adapted from
   ZalithLauncher2Plus as detailed above.
8. **Account credential handling** — session tokens are redacted by value from the game log that
   the share-log feature can upload, the account database is excluded from cloud backup and device
   transfer, the offline Yggdrasil server binds to loopback only, `Account.toString()` no longer
   prints credentials, server-controlled error text is redacted and length-limited, and
   `SettingsRegistry.allSettings` was exposed for the backup feature.

## Bundled runtime components

These binaries are unpacked from `src/main/assets/components/**` at first launch and are *not*
covered by the Maven dependency table below:

| Component | Purpose | Licence |
|---|---|---|
| `authlib-injector.jar`, `nide8auth.jar` | offline / third-party Yggdrasil authentication agent | LGPL-3.0 |
| `cacio-androidnw-*.jar`, `cacio-shared-*.jar`, `ResConfHack.jar` | headless AWT for Java 8 runtimes | GPL-2.0 / LGPL-2.1 |
| `cacio-agent.jar`, `cacio-shared-1.19.1*.jar`, `cacio-tta-1.19.1*.jar` | headless AWT for Java 17+ runtimes | GPL-2.0 / LGPL-2.1 |
| `MioLaunchWrapper.jar`, `MioLibPatcher.jar` | Minecraft launch wrapper / library patcher | GPL-3.0 |
| `HMCLTransformerDiscoveryService-1.0.jar` | Forge transformer discovery shim | GPL-3.0 |
| `forge_installer.jar` | Forge/NeoForge installer runner | LGPL-3.0 |
| JRE packs `assets/runtimes/jre-{8,17,21,25}/*.tar.xz` | OpenJDK builds for Android (Temurin derivative, Zulu derivative) | GPL-2.0 with Classpath Exception |
| `assets/runtimes/jna/jna-arm64.zip` | JNA native bindings | Apache-2.0 / LGPL-2.1 |

## Gradle and Maven dependencies

| androidx-appcompat                    | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/appcompat)         |
| androidx-constraintlayout-compose     | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/develop/ui/compose/layouts/constraintlayout) |
| androidx-webkit                       | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/webkit)            |
| ANGLE                                 | Copyright 2018 The ANGLE Project Authors                                                                      | BSD 3-Clause License | [Link↗](http://angleproject.org/)                                                  |
| Apache Commons Codec                  | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-codec)                           |
| Apache Commons Compress               | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-compress)                        |
| Apache Commons IO                     | -                                                                                                             | Apache 2.0           | [Link↗](https://commons.apache.org/proper/commons-io)                              |
| ByteHook                              | Copyright © 2020-2024 ByteDance, Inc.                                                                         | MIT License          | [Link↗](https://github.com/bytedance/bhook)                                        |
| BuildKeys                             | Copyright © 2026 MovTery                                                                                      | Aoache 2.0           | [Link↗](https://github.com/MovTery/BuildKeys)                                      |
| Coil Compose                          | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Coil Gifs                             | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Coil SVG                              | Copyright © 2025 Coil Contributors                                                                            | Apache 2.0           | [Link↗](https://github.com/coil-kt/coil)                                           |
| Fishnet                               | Copyright © 2025 Kyant                                                                                        | Apache 2.0           | [Link↗](https://github.com/Kyant0/Fishnet)                                         |
| Holy GL4ES                            | Copyright © 2016-2018 Sebastien Chevalier; Copyright © 2013-2016 Ryan Hileman                               | MIT License          | [Link↗](https://github.com/FCL-Team/Holy-GL4ES)                                        |
| Gson                                  | Copyright © 2008 Google Inc.                                                                                  | Apache 2.0           | [Link↗](https://github.com/google/gson)                                            |
| kotlinx.coroutines                    | Copyright © 2000-2020 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://github.com/Kotlin/kotlinx.coroutines)                              |
| ktor-client-content-negotiation       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-client-core                      | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-client-okhttp                    | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-http                             | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| ktor-serialization-kotlinx-json       | Copyright © 2000-2023 JetBrains s.r.o.                                                                        | Apache 2.0           | [Link↗](https://ktor.io)                                                           |
| LTW                                   | Copyright © MojoLauncher                                                                                      | LGPL-3.0 License     | [Link↗](https://github.com/MojoLauncher/LTW)                                       |
| LWJGL - Lightweight Java Game Library | Copyright © 2012-present Lightweight Java Game Library All rights reserved.                                   | BSD 3-Clause License | [Link↗](https://github.com/LWJGL/lwjgl3)                                           |
| material-color-utilities              | Copyright 2021 Google LLC                                                                                     | Apache 2.0           | [Link↗](https://github.com/material-foundation/material-color-utilities)           |
| Maven Artifact                        | Copyright © The Apache Software Foundation                                                                    | Apache 2.0           | [Link↗](https://github.com/apache/maven/tree/maven-3.9.9/maven-artifact)           |
| Media3                                | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/media3)            |
| MMKV                                  | Copyright © 2018 THL A29 Limited, a Tencent company.                                                          | BSD 3-Clause License | [Link↗](https://github.com/Tencent/MMKV)                                           |
| Navigation 3                          | Copyright © The Android Open Source Project                                                                   | Apache 2.0           | [Link↗](https://developer.android.com/jetpack/androidx/releases/navigation3)       |
| MobileGlues                           | Copyright (c) 2025-2026 MobileGL-Dev                                                                          | LGPL-2.1 License     | [Link↗](https://github.com/MobileGL-Dev/MobileGlues)                               |
| OkHttp                                | Copyright © 2019 Square, Inc.                                                                                 | Apache 2.0           | [Link↗](https://github.com/square/okhttp)                                          |
| Okio                                  | Copyright © 2013 Square, Inc.                                                                                 | Apache 2.0           | [Link↗](https://square.github.io/okio/)                                            |
| OpenNBT                               | Copyright © 2013-2021 Steveice10.                                                                             | MIT License          | [Link↗](https://github.com/GeyserMC/OpenNBT)                                       |
| Process Phoenix                       | Copyright © 2015 Jake Wharton                                                                                 | Apache 2.0           | [Link↗](https://github.com/JakeWharton/ProcessPhoenix)                             |
| proxy-client-android                  | -                                                                                                             | LGPL-3.0 License     | [Link↗](https://github.com/TouchController/TouchController)                        |
| Reorderable                           | Copyright © 2023 Calvin Liang                                                                                 | Apache 2.0           | [Link↗](https://github.com/Calvin-LL/Reorderable)                                  |
| sdl2-compat                           | Copyright (C) 2026 Sam Lantinga <slouken@libsdl.org>                                                          | Zlib License         | [Link↗](https://github.com/libsdl-org/sdl2-compat)                                 |
| SDL3                                  | Copyright (C) 1997-2026 Sam Lantinga <slouken@libsdl.org>                                                     | Zlib License         | [Link↗](https://github.com/libsdl-org/SDL)                                         |
| skinview3d                            | Copyright © 2014-2018 Kent Rasmussen; Copyright © 2017-2022 Haowei Wen, Sean Boult and contributors           | MIT License          | [Link↗](https://github.com/bs-community/skinview3d)                                |
| sora-editor                           | Copyright (C) 2020-2026  Rosemoe                                                                              | LGPL-2.1 License     | [Link↗](https://github.com/Rosemoe/sora-editor)                                    |
| StringFog                             | Copyright © 2016-2023, Megatron King                                                                          | Apache 2.0           | [Link↗](https://github.com/MegatronKing/StringFog)                                 |
| tm4e (TextMate for Eclipse)           | Copyright © Eclipse Foundation                                                                                | EPL-2.0 License      | [Link↗](https://github.com/eclipse-tm4e/tm4e)                                      |
| XZ for Java                           | Copyright © The XZ for Java authors and contributors                                                          | 0BSD License         | [Link↗](https://tukaani.org/xz/java.html)                                          |
| sqlcipher-android | - | Apache 2.0 | Removed from Oxide Launcher (declared but never used) |
| zip4j | Copyright © 2008-2024 Sharath Prajapati | Apache 2.0 | [Link↗](https://github.com/sjwood/zip4j) |
| LunarCalendar | Copyright © 2021 xhinliang | Apache 2.0 | [Link↗](https://github.com/xhinliang/LunarCalendar) |
| toml4j | Copyright © 2015 Moandjiezana | Apache 2.0 | [Link↗](https://github.com/moandjiezana/toml4j) |
| jspecify | Copyright © 2022-2024 Tatu Saloranta | Apache 2.0 | [Link↗](https://jspecify.dev) |
| jsr305 | Copyright © 2007-2018, Kevin Bourrillion | Apache 2.0 | [Link↗](https://github.com/findbugsproject/findbugs/tree/master/jsr305) |
| Room | Copyright © The Android Open Source Project | Apache 2.0 | [Link↗](https://developer.android.com/training/data-storage/room) |
| Hilt / Dagger | Copyright © Google LLC | Apache 2.0 | [Link↗](https://dagger.dev/hilt) |
| Material Components for Android | Copyright © The Android Open Source Project | Apache 2.0 | [Link↗](https://developer.android.com/develop/ui/views/components/material) |
| compose-richtext | Copyright © 2018 Fatih Aydin | Apache 2.0 | [Link↗](https://github.com/halilibo/compose-richtext) |
| material-kolor | Copyright © 2022 MaterialKolors contributors | Apache 2.0 | [Link↗](https://github.com/material-foundation/material-color-utilities) |
| minidns-hla | Copyright © 2016-2023 the minidns authors | Apache 2.0 | [Link↗](https://github.com/minidns/minidns) |
| documentfile | Copyright © The Android Open Source Project | Apache 2.0 | [Link↗](https://developer.android.com/training/data-storage/shared/documents-files) |
| constraintlayout-compose | Copyright © The Android Open Source Project | Apache 2.0 | [Link↗](https://developer.android.com/develop/ui/compose/layouts/constraintlayout) |
| mockwebserver3 (test only) | Copyright © 2019 Square, Inc. | Apache 2.0 | [Link↗](https://github.com/square/okhttp/tree/master/mockwebserver) |
| flite (bundled native TTS) | Copyright © 2005-2014 Carnegie Mellon University | BSD 3-Clause License | [Link↗](https://github.com/festvox/flite) |
