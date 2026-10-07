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

package dev.oxide.launcher.game.renderer.copperoxide

/**
 * Pure Copper Oxide process-environment construction.
 *
 * Split from IO on purpose: these functions allocate the smallest map that
 * expresses the verified driver contract and do no settings reads, no file
 * IO and no string churn. The launch path (CopperOxideRenderer) is the only
 * place that touches AllSettings and the filesystem; unit tests pin this
 * file without Robolectric.
 *
 * Verified driver reads (disassembled libmobileglues.so, strings + getenv
 * sites): MG_DIR_PATH, MG_ANGLE_DIR, MG_COUNT_LAUNCH, MG_PLUGIN_STATUS,
 * FCL/ZALITH/PGW_VERSION_CODE, MG_BENCH_*. No other MG_, LIBGL_ or
 * MOBILEGLUES key has a getenv site, so nothing else is emitted here.
 * POJAV_RENDERER is set by GameLauncher, never here.
 */
object CopperOxideEnv {
    /** Base map: GLES3 + driver EGL + launch counter + flavor. No IO. */
    fun baseEnv(): Map<String, String> = mapOf(
        "LIBGL_ES" to "3",
        "LIBGL_EGL" to CopperOxideIdentity.NATIVE_LIBRARY,
        "MG_COUNT_LAUNCH" to "1",
        "OXIDE_RENDERER_FLAVOR" to CopperOxideIdentity.FLAVOR,
    )

    /**
     * MG_DIR_PATH mapping. Pure: no settings, no filesystem.
     *
     * Off returns an empty map so the launch env stays byte-identical to the
     * no-tuning case (driver falls back to its compiled defaults). On
     * returns exactly one verified variable.
     */
    fun dataDirEnv(usePrivateDir: Boolean, privateDir: String): Map<String, String> =
        if (usePrivateDir && privateDir.isNotBlank()) mapOf("MG_DIR_PATH" to privateDir)
        else emptyMap()

    /**
     * Merge base + data-dir without intermediate copies beyond the result.
     * Pre-sizes the LinkedHashMap so the launch path does one allocation.
     */
    fun merged(base: Map<String, String>, dataDir: Map<String, String>): Map<String, String> {
        if (dataDir.isEmpty()) return base
        val out = LinkedHashMap<String, String>(base.size + dataDir.size)
        out.putAll(base)
        out.putAll(dataDir)
        return out
    }
}
