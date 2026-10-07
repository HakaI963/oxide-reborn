/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.renderer.renderers

import dev.oxide.launcher.game.renderer.CopperOxideTuning
import dev.oxide.launcher.game.renderer.RendererInterface
import dev.oxide.launcher.game.renderer.authorCopperOxideConfig
import dev.oxide.launcher.game.renderer.copperOxideTuningFromSettings
import dev.oxide.launcher.game.renderer.copperoxide.CopperOxideEnv
import dev.oxide.launcher.game.renderer.copperoxide.CopperOxideIdentity
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * Copper Oxide, the default builtin renderer.
 *
 * Copper Oxide is the product now: Oxide's independent OpenGL-on-OpenGL-ES
 * renderer core (identity, env, capability detection and device tuning in
 * `game.renderer.copperoxide`) over the MobileGlues lineage native driver
 * by MobileGL-Dev (LGPL-2.1, see THIRD_PARTY.md). Oxide does not own the
 * upstream driver project; the Kotlin core and the launch wiring are Oxide's
 * own. The native library is vendored from the MobileGlues-plugin dev CI
 * artifact; the environment below mirrors the upstream plugin manifest's
 * `pojavEnv` (`LIBGL_ES`, `POJAVEXEC_EGL`/`LIBGL_EGL`,
 * `MG_COUNT_LAUNCH`). `POJAV_RENDERER` is intentionally not repeated here:
 * GameLauncher always sets it to [getRendererId].
 *
 * No capability spoofing: GL version, vendor/renderer strings and extension
 * support come from the driver and from EGL detection. This file only emits
 * the verified getenv keys (see CopperOxideEnv).
 *
 * The renderer id and the `OXIDE_RENDERER_FLAVOR` variable live in an
 * Oxide-specific namespace so that no other launcher selects this pipeline.
 * That namespacing is a selection contract, not a lockout: the underlying
 * library stays LGPL-2.1 and the plugin ABI is untouched.
 */
object CopperOxideRenderer : RendererInterface {
    override fun getRendererId(): String = CopperOxideIdentity.RENDERER_ID

    override fun getUniqueIdentifier(): String = CopperOxideIdentity.UNIQUE_ID

    override fun getRendererName(): String = CopperOxideIdentity.NAME

    override fun getRendererSummary(): String = CopperOxideIdentity.summary()

    override fun getMinMCVersion(): String = CopperOxideIdentity.MIN_MC_VERSION

    // 这里必须写正式版号，不能写快照号：GameVersionNumber 里快照（SNAPSHOT）排在正式版（GA）之前，
    // 写成 "26.3-snapshot-3" 会让 26.3 被判定为“比上限更大”，于是该渲染器在 26.3 上被禁用。
    override fun getMaxMCVersion(): String = CopperOxideIdentity.MAX_MC_VERSION

    // 展示版本号默认继承 getMaxMCVersion()，两处必须同源。

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val base = CopperOxideEnv.baseEnv()
        val usePrivateDir = runCatching { AllSettings.copperOxidePrivateDataDir.getValue() }.getOrDefault(false)
        val tuning = runCatching { copperOxideTuningFromSettings() }.getOrDefault(CopperOxideTuning())
        if (!tuning.enabled && !usePrivateDir) return@lazy base
        val dir = File(PathManager.DIR_FILES_EXTERNAL, COPPER_OXIDE_DATA_DIR_NAME)
        // mkdirs is a syscall even when the dir exists; skip it on the hot path.
        if (!dir.isDirectory) runCatching { dir.mkdirs() }
        // Tuning forces the private dir even when the data-dir switch is off:
        // the file must live where MG_DIR_PATH points and shared storage is
        // unwritable under scoped storage. Both switches off returns the base
        // map above, byte-identical to the pre-tuning behaviour.
        if (tuning.enabled) runCatching { authorCopperOxideConfig(dir, tuning) }
        CopperOxideEnv.merged(base, CopperOxideEnv.dataDirEnv(usePrivateDir = true, privateDir = dir.absolutePath))
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = CopperOxideIdentity.NATIVE_LIBRARY

    override fun getRendererEGL(): String = CopperOxideIdentity.NATIVE_LIBRARY
}

/**
 * 启动器私有目录下 Copper Oxide 驱动数据子目录名
 *
 * 驱动默认目录是编译进 .so 的 "/sdcard/MG"（MG_DIR_PATH 为空时的回落），
 * 私有子目录取名 "mobileglues"，与驱动库文件名一致，避免与其它启动器的数据混在一起.
 * Single source of truth lives in [CopperOxideIdentity.DATA_DIR_NAME]; this
 * alias stays for callers compiled against the old location.
 */
internal const val COPPER_OXIDE_DATA_DIR_NAME: String = "mobileglues"

/**
 * Copper Oxide 驱动数据目录选项到环境变量的映射
 *
 * 证据（均来自对 libmobileglues.so 的反汇编与 strings，不依赖任何文档）：
 * - 该 .so 共 14 处 getenv 调用：4 处属于 libc++ 的临时目录逻辑（TMPDIR 等），
 *   其余 10 处全部是驱动自己的键：MG_DIR_PATH、MG_ANGLE_DIR、MG_COUNT_LAUNCH、
 *   MG_PLUGIN_STATUS、FCL_VERSION_CODE、ZALITH_VERSION_CODE、PGW_VERSION_CODE
 *   与 MG_BENCH_BUDGET_MS、MG_BENCH_WIDTH、MG_BENCH_HEIGHT。不存在其它
 *   MG_、LIBGL_ 或 MOBILEGLUES 前缀的环境变量读取点。
 * - MG_DIR_PATH 在初始化函数中被 getenv 后 strdup 保存，并在其后调用的路径
 *   拼接函数里与 "/config.json"（rodata 偏移语义，下同）、"/latest.log"、
 *   "/glsl_cache.tmp"、"/stats.json" 逐个拼接；MG_DIR_PATH 为空时回落到
 *   全局默认目录（rodata 中的 "/sdcard/MG"）。
 * - 本选项只控制是否多传这一个已验证的变量：关时返回空表，启动环境与
 *   此前逐字节一致；开时返回 MG_DIR_PATH 一项。任何其它"优化变量"
 *   （MSAA、交换间隔、着色器缓存大小、ANGLE 开关）在该 .so 里都没有
 *   getenv 读取点，一律不在此表达。
 *
 * 纯函数：不碰 AllSettings 与文件系统，可单测。
 * Delegates to [CopperOxideEnv.dataDirEnv] so both spellings stay in sync.
 *
 * @param usePrivateDir 对应 [AllSettings.copperOxidePrivateDataDir]
 * @param privateDir 已解析好的私有目录绝对路径
 */
internal fun copperOxideDriverDataDirEnv(usePrivateDir: Boolean, privateDir: String): Map<String, String> =
    CopperOxideEnv.dataDirEnv(usePrivateDir, privateDir)
