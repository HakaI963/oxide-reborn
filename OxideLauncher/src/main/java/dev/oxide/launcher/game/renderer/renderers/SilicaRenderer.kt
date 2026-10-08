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
import dev.oxide.launcher.game.renderer.silica.SilicaEnv
import dev.oxide.launcher.game.renderer.silica.SilicaIdentity
import dev.oxide.launcher.game.renderer.silica.SilicaTuning
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * Silica renderer (phase 1).
 *
 * Oxide-integrated pipeline; translation backend is the proven
 * libcopperoxide.so until the independent Silica native exists.
 * Always uses a private data dir so full config applies without spoofing.
 * See SILICA.md for architecture, upstream-fix port and measurement plan.
 */
object SilicaRenderer : RendererInterface {
    override fun getRendererId(): String = SilicaIdentity.RENDERER_ID
    override fun getUniqueIdentifier(): String = SilicaIdentity.UNIQUE_ID
    override fun getRendererName(): String = SilicaIdentity.NAME
    override fun getRendererSummary(): String = SilicaIdentity.summary()
    override fun getMinMCVersion(): String = SilicaIdentity.MIN_MC_VERSION
    override fun getMaxMCVersion(): String = SilicaIdentity.MAX_MC_VERSION
    override fun getRendererLibrary(): String = SilicaIdentity.NATIVE_LIBRARY
    override fun getRendererEGL(): String = SilicaIdentity.EGL_LIBRARY
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val dir = silicaDataDir()
        dir.mkdirs()
        authorSilicaConfig(dir)
        SilicaEnv.merged(dir.absolutePath)
    }

    private fun silicaDataDir(): File = File(PathManager.DIR_FILES_PRIVATE, SilicaIdentity.DATA_DIR_NAME)

    private fun authorSilicaConfig(dir: File) {
        runCatching {
            val mode = runCatching {
                SilicaTuning.SilicaPerformanceMode.valueOf(AllSettings.silicaPerformanceMode.getValue())
            }.getOrDefault(SilicaTuning.SilicaPerformanceMode.AUTO)
            val resolved = SilicaTuning.resolve(
                mode,
                AllSettings.silicaShaderCacheMb.getValue(),
                AllSettings.silicaUpscale.getValue(),
            )
            val tmp = File(dir, "config.json.tmp")
            val dst = File(dir, "config.json")
            tmp.writeText(SilicaTuning.toConfigJson(resolved))
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }
}
