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

package dev.oxide.launcher.game.renderer

import dev.oxide.launcher.setting.AllSettings
import java.io.File

/**
 * MobileGlues `config.json` authoring for the Copper Oxide renderer.
 *
 * The driver reads `${'$'}MG_DIR_PATH/config.json` at startup. The key names below
 * are the verified schema from the MobileGlues source audit (core commit
 * f cdf914): `enableANGLE` (not `enableAngle`), `enableNoError` (not
 * `ignoreError`), the three `enableExt*` flags, `maxGlslCacheSize` in bytes,
 * and `fsr1Setting`. There is no `bufferCoherentAsFlush` key (the behaviour
 * is derived inside the driver), and the legacy `multidrawMode*` keys are
 * ignored by current builds -- so this writer emits neither, and the parser
 * rejects them as unknown.
 *
 * Deliberately NOT offered here (and therefore never written):
 * - `customGLVersion`: spoofing the GL version turns a clean disable into
 *   native crashes (Voxy needs the real 4.6).
 * - `hideMGEnvLevel` above 0: level 1+ randomises the GL vendor/renderer
 *   strings, which undoes the Oxide branding and breaks game GL detection.
 * - `multidrawOrder` and friends: legacy surface, unsafe to poke.
 * - `angleDepthClearFixMode`: left at the driver default.
 *
 * Neutral-default contract: [CopperOxideTuning.toConfigJson] returns null
 * while tuning is disabled, and [authorCopperOxideConfig] then writes nothing.
 * With tuning off the launch dir contains no `config.json`, which is
 * byte-identical to today's behaviour (the driver falls back to its compiled
 * defaults either way). Tuning on always writes the full file so the result
 * is deterministic and round-trips through [parseCopperOxideConfigJson].
 *
 * Dir interplay: tuning forces the launcher-private dir (the same dir
 * `MG_DIR_PATH` points at) even when the data-dir switch is off, because the
 * file must live where `MG_DIR_PATH` points and `/sdcard` is unwritable under
 * scoped storage. [resolveCopperOxideDataDir] only ever returns the passed
 * private dir or null -- it can never resolve to shared storage. The drawer
 * shows a read-only note while this forcing is in effect.
 *
 * Pure except for [copperOxideTuningFromSettings] (reads AllSettings) and
 * [authorCopperOxideConfig] (one file write): everything else is unit-tested.
 */
const val COPPER_OXIDE_CONFIG_FILE_NAME = "config.json"

/**
 * The only key names the writer may emit and the parser may accept.
 *
 * Source-guarded by CopperOxideConfigTest: every quoted config key in this
 * file must be a member of this set.
 */
val COPPER_OXIDE_CONFIG_VERIFIED_KEYS: Set<String> = setOf(
    "enableANGLE",
    "enableNoError",
    "enableExtComputeShader",
    "enableExtTimerQuery",
    "enableExtDirectStateAccess",
    "maxGlslCacheSize",
    "fsr1Setting",
)

const val COPPER_OXIDE_FSR_MIN = 0
const val COPPER_OXIDE_FSR_MAX = 4
const val COPPER_OXIDE_ANGLE_MIN = 0
const val COPPER_OXIDE_ANGLE_MAX = 3
const val COPPER_OXIDE_NO_ERROR_MIN = 0
const val COPPER_OXIDE_NO_ERROR_MAX = 3

/** The UI only offers Auto/Disable; L1/L2 are cheats that break games. */
const val COPPER_OXIDE_NO_ERROR_OFFERED_MAX = 1

const val COPPER_OXIDE_GLSL_CACHE_MB_MIN = 0
const val COPPER_OXIDE_GLSL_CACHE_MB_MAX = 512
const val COPPER_OXIDE_GLSL_CACHE_BYTES_PER_MB = 1048576L

/**
 * Snapshot of the Copper tuning settings.
 *
 * Defaults are the benign baseline: FSR off (full-resolution rendering, as
 * today), ANGLE and NoError on driver default, extensions exposed, shader
 * cache on at 64 MB. The extension/cache defaults are a chosen fail-safe
 * direction, not audited driver defaults -- the provably neutral case is
 * `enabled = false`, which writes nothing at all.
 */
data class CopperOxideTuning(
    val enabled: Boolean = false,
    val fsr: Int = 0,
    val glslCacheMb: Int = 64,
    val angle: Int = 0,
    val noError: Int = 0,
    val extCompute: Boolean = true,
    val extTimerQuery: Boolean = true,
    val extDsa: Boolean = true,
)

/**
 * Serializes the tuning snapshot to the `config.json` body.
 *
 * Returns null while tuning is disabled: the caller must then write nothing,
 * which keeps the driver on its compiled defaults (byte-identical to today).
 * Out-of-range numbers are clamped into the verified ranges; the cache size
 * converts from MB to bytes, with 0 (or below) disabling the cache.
 */
fun CopperOxideTuning.toConfigJson(): String? {
    if (!enabled) return null
    val fsrValue = fsr.coerceIn(COPPER_OXIDE_FSR_MIN, COPPER_OXIDE_FSR_MAX)
    val angleValue = angle.coerceIn(COPPER_OXIDE_ANGLE_MIN, COPPER_OXIDE_ANGLE_MAX)
    val noErrorValue = noError.coerceIn(COPPER_OXIDE_NO_ERROR_MIN, COPPER_OXIDE_NO_ERROR_MAX)
    val cacheBytes = if (glslCacheMb <= 0) {
        0L
    } else {
        glslCacheMb.coerceIn(COPPER_OXIDE_GLSL_CACHE_MB_MIN, COPPER_OXIDE_GLSL_CACHE_MB_MAX)
            .toLong() * COPPER_OXIDE_GLSL_CACHE_BYTES_PER_MB
    }
    return buildString {
        append("{")
        append("\"enableANGLE\":").append(angleValue).append(",")
        append("\"enableNoError\":").append(noErrorValue).append(",")
        append("\"enableExtComputeShader\":").append(if (extCompute) 1 else 0).append(",")
        append("\"enableExtTimerQuery\":").append(if (extTimerQuery) 1 else 0).append(",")
        append("\"enableExtDirectStateAccess\":").append(if (extDsa) 1 else 0).append(",")
        append("\"maxGlslCacheSize\":").append(cacheBytes).append(",")
        append("\"fsr1Setting\":").append(fsrValue)
        append("}")
    }
}

/**
 * Parses a `config.json` body back to key/value pairs.
 *
 * Only the keys this writer emits (the safe verified subset) are accepted:
 * anything else -- including the plausible but wrong `enableAngle` /
 * `ignoreError`, the derived `bufferCoherentAsFlush`, and the real but
 * deliberately unoffered `customGLVersion` / `hideMGEnvLevel` /
 * `multidrawOrder*` / `angleDepthClearFixMode` -- throws
 * [IllegalArgumentException]. Used by tests for the round-trip; the
 * driver itself is the only production reader.
 */
fun parseCopperOxideConfigJson(json: String): Map<String, Long> {
    val pairs = Regex("\"([^\"]+)\"\\s*:\\s*(-?\\d+)").findAll(json)
        .map { it.groupValues[1] to it.groupValues[2].toLong() }
        .toList()
    if (pairs.isEmpty()) error("not a Copper Oxide config.json body")
    val unknown = pairs.map { it.first }
        .filter { it !in COPPER_OXIDE_CONFIG_VERIFIED_KEYS }
        .distinct()
    if (unknown.isNotEmpty()) {
        throw IllegalArgumentException("unknown MobileGlues config keys: " + unknown.joinToString())
    }
    return pairs.toMap()
}

/**
 * Reads the tuning snapshot out of AllSettings.
 *
 * Every read is guarded: a half-migrated install must fall back to tuning
 * disabled (write nothing) rather than crash the launch path.
 */
fun copperOxideTuningFromSettings(): CopperOxideTuning = CopperOxideTuning(
    enabled = runCatching { AllSettings.copperOxideTuningEnabled.getValue() }.getOrDefault(false),
    fsr = runCatching { AllSettings.copperOxideFsr.getValue() }.getOrDefault(0),
    glslCacheMb = runCatching { AllSettings.copperOxideGlslCacheMb.getValue() }.getOrDefault(64),
    angle = runCatching { AllSettings.copperOxideAngle.getValue() }.getOrDefault(0),
    noError = runCatching { AllSettings.copperOxideNoError.getValue() }.getOrDefault(0),
    extCompute = runCatching { AllSettings.copperOxideExtCompute.getValue() }.getOrDefault(true),
    extTimerQuery = runCatching { AllSettings.copperOxideExtTimerQuery.getValue() }.getOrDefault(true),
    extDsa = runCatching { AllSettings.copperOxideExtDsa.getValue() }.getOrDefault(true),
)

/**
 * Decides which dir `config.json` belongs in.
 *
 * Tuning forces the private dir even when the data-dir switch is off: the
 * file must live where `MG_DIR_PATH` points (the renderer sets it to this
 * same dir in that case), and shared storage is unwritable under scoped
 * storage. Returns null when neither is on, meaning no dir and no file.
 * This only ever returns [privateDir] or null -- never shared storage.
 */
fun resolveCopperOxideDataDir(
    usePrivateDir: Boolean,
    tuningEnabled: Boolean,
    privateDir: File,
): File? = if (tuningEnabled || usePrivateDir) privateDir else null

/**
 * Writes `config.json` into [dir], creating it first.
 *
 * Returns false (writing nothing) when tuning is disabled. IO failures also
 * return false: a missing config file only means driver defaults, so launch
 * must never fail because tuning could not be persisted.
 */
fun authorCopperOxideConfig(dir: File, tuning: CopperOxideTuning): Boolean {
    val json = tuning.toConfigJson() ?: return false
    return runCatching {
        dir.mkdirs()
        File(dir, COPPER_OXIDE_CONFIG_FILE_NAME).writeText(json)
        true
    }.getOrDefault(false)
}
