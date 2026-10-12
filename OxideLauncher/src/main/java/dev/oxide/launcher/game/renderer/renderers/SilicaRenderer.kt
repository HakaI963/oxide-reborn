/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.renderers

import dev.oxide.launcher.game.renderer.RendererInterface
import dev.oxide.launcher.game.renderer.onigami.OnigamiPerformanceMode
import dev.oxide.launcher.game.renderer.onigami.OnigamiTuning
import dev.oxide.launcher.game.renderer.silica.SilicaEnv
import dev.oxide.launcher.game.renderer.silica.SilicaIdentity
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * Silica renderer.
 *
 * Translation core is the shared libonigami.so (refcounted dlopen; the
 * silica data dir keeps onigami.json/vault/log/stats namespaced so nothing
 * collides with ONIGAMI). Frame generation is the vendored win-fg Vulkan
 * layer (own .so + manifest + env, see SilicaEnv): it hooks the game's OWN
 * Vulkan swapchain (Minecraft 26.2+ native Vulkan). No Vulkan swapchain, no
 * FG — pure translation, stated honestly in the UI.
 *
 * HARD RULE (inherited): never loads, wraps, or falls back to any other
 * renderer's translation library. If libonigami.so is absent the launch
 * fails loudly; nothing is silently substituted.
 */
object SilicaRenderer : RendererInterface {
    override fun getRendererId(): String = SilicaIdentity.RENDERER_ID
    override fun getUniqueIdentifier(): String = SilicaIdentity.UNIQUE_ID
    override fun getRendererName(): String = SilicaIdentity.NAME
    override fun getRendererSummary(): String = SilicaIdentity.summary()
    override fun getMinMCVersion(): String = SilicaIdentity.MIN_MC
    override fun getMaxMCVersion(): String = SilicaIdentity.MAX_MC
    override fun getRendererLibrary(): String = SilicaIdentity.NATIVE_LIBRARY
    override fun getRendererEGL(): String? = null
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val dir = SilicaEnv.dataDir()
        dir.mkdirs()
        authorTranslationConfig(dir)
        writeFgPackage(dir)
        SilicaEnv.merged(
            OnigamiPerformanceMode.OPTIMIZED.name,
            dir.absolutePath,
            AllSettings.silicaFgEnabled.getValue(),
            AllSettings.silicaFgMult.getValue(),
            AllSettings.silicaFgModel.getValue(),
            AllSettings.silicaFgPerf.getValue(),
            AllSettings.silicaFgPacing.getValue(),
        )
    }

    private fun silicaDataDir(): File = SilicaEnv.dataDir()

    private fun authorTranslationConfig(dir: File) {
        runCatching {
            val resolved = OnigamiTuning.resolve(
                AllSettings.onigamiShaderCacheMb.getValue(),
                AllSettings.onigamiCoalescing.getValue(),
                AllSettings.onigamiDiagnostics.getValue(),
                AllSettings.onigamiRenderScale.getValue(),
                AllSettings.onigamiUpscaleMode.getValue(),
            )
            val tmp = File(dir, "onigami.json.tmp")
            val dst = File(dir, "onigami.json")
            tmp.writeText(OnigamiTuning.toConfigJson(OnigamiPerformanceMode.OPTIMIZED, resolved))
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }

    private fun writeFgPackage(dir: File) {
        runCatching {
            // conf.toml: win-fg hot-reloads it live, so in-game FG changes
            // apply without restart.
            File(dir, SilicaEnv.CONF_NAME).writeText(
                SilicaEnv.confToml(
                    AllSettings.silicaFgEnabled.getValue(),
                    AllSettings.silicaFgMult.getValue(),
                    AllSettings.silicaFgModel.getValue(),
                    AllSettings.silicaFgPerf.getValue(),
                    AllSettings.silicaFgPacing.getValue(),
                )
            )
            // Layer manifest with absolute library path (robust to loader CWD).
            val layers = SilicaEnv.layersDir()
            layers.mkdirs()
            File(layers, SilicaEnv.MANIFEST_NAME).writeText(
                SilicaEnv.manifestJson(
                    File(PathManager.DIR_NATIVE_LIB, SilicaIdentity.WINFG_LIBRARY).absolutePath
                )
            )
        }
    }

    /**
     * Rewrite conf.toml immediately. win-fg watches it (~1Hz mtime), so
     * in-game frame-generation changes apply live, no restart.
     */
    fun refreshFgConfig() {
        runCatching {
            val dir = silicaDataDir()
            dir.mkdirs()
            writeFgPackage(dir)
        }
    }
}
