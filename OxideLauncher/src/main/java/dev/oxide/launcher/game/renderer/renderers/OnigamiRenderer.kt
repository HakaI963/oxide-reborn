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
import dev.oxide.launcher.game.renderer.onigami.OnigamiEnv
import dev.oxide.launcher.game.renderer.onigami.OnigamiIdentity
import dev.oxide.launcher.game.renderer.onigami.OnigamiPerformanceMode
import dev.oxide.launcher.game.renderer.onigami.OnigamiTuning
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * Onigami renderer.
 *
 * HARD RULE: loads ONLY libonigami.so (own backend). No fallback to any
 * other renderer's library, no call forwarding, no wrapping. If the library
 * is absent the dlopen fails loudly in the launch log; GameLauncher must NOT
 * silently substitute another backend.
 */
object OnigamiRenderer : RendererInterface {
    override fun getRendererId(): String = OnigamiIdentity.RENDERER_ID
    override fun getUniqueIdentifier(): String = OnigamiIdentity.UNIQUE_ID
    override fun getRendererName(): String = OnigamiIdentity.NAME
    override fun getRendererSummary(): String = OnigamiIdentity.summary()
    override fun getMinMCVersion(): String = OnigamiIdentity.MIN_MC
    override fun getMaxMCVersion(): String = OnigamiIdentity.MAX_MC
    override fun getRendererLibrary(): String = OnigamiIdentity.NATIVE_LIBRARY
    // No EGL override: display acquisition, init, configs, surfaces and swap
    // stay on the host EGL end to end (SDL + bridge resolve system EGL).
    // libonigami.so interposes GL entry points plus the EGL context lifecycle
    // natively. A second display path inside the launcher is what can return
    // NO_DISPLAY while the host would succeed, so it must not exist.
    override fun getRendererEGL(): String? = null
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val dir = onigamiDataDir()
        dir.mkdirs()
        authorOnigamiConfig(dir)
        val mode = runCatching {
            OnigamiPerformanceMode.valueOf(AllSettings.onigamiPerformanceMode.getValue())
        }.getOrDefault(OnigamiPerformanceMode.AUTO).name
        OnigamiEnv.merged(mode, dir.absolutePath)
    }

    private fun onigamiDataDir(): File = File(PathManager.DIR_FILES_PRIVATE, OnigamiIdentity.DATA_DIR)

    private fun authorOnigamiConfig(dir: File) {
        runCatching {
            val mode = runCatching {
                OnigamiPerformanceMode.valueOf(AllSettings.onigamiPerformanceMode.getValue())
            }.getOrDefault(OnigamiPerformanceMode.AUTO)
            val resolved = OnigamiTuning.resolve(
                mode,
                AllSettings.onigamiShaderCacheMb.getValue(),
                AllSettings.onigamiCoalescing.getValue(),
                AllSettings.onigamiDiagnostics.getValue(),
            )
            val tmp = File(dir, "onigami.json.tmp")
            val dst = File(dir, "onigami.json")
            tmp.writeText(OnigamiTuning.toConfigJson(mode, resolved))
            if (dst.exists()) dst.delete()
            tmp.renameTo(dst)
        }
    }
}
