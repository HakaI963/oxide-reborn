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
 * Copper Oxide product identity.
 *
 * Copper Oxide is the product now, not a small MobileGlues optimization.
 * Lineage: OpenGL-on-OpenGL-ES implementation by MobileGL-Dev (LGPL-2.1),
 * vendored as a native library (see THIRD_PARTY.md). The Kotlin core in this
 * package is Oxide's own independent renderer layer: env construction,
 * capability detection, device tuning and shader-cache policy. It never
 * spoofs GL versions, vendor strings or extension support; everything it
 * reports is detected or explicitly documented as a chosen default.
 *
 * The renderer id and OXIDE_RENDERER_FLAVOR live in an Oxide-specific
 * namespace so no other launcher selects this pipeline. That namespacing is
 * a selection contract, not a lockout: the underlying library stays LGPL-2.1
 * and the plugin ABI is untouched.
 *
 * The GL vendor/renderer strings seen inside Minecraft come from the driver
 * itself. Oxide does not rewrite them: faking them breaks game GL detection
 * and turns clean disables into native crashes.
 */
object CopperOxideIdentity {
    const val RENDERER_ID: String = "opengles3_oxide_copper"
    const val UNIQUE_ID: String = "52a0f58e-1694-4d47-9ce6-5fa0894413a7"
    const val NAME: String = "Copper Oxide"
    const val FLAVOR: String = "copper-oxide"
    const val NATIVE_LIBRARY: String = "libmobileglues.so"
    const val MIN_MC_VERSION: String = "1.17"
    const val MAX_MC_VERSION: String = "26.3"

    /**
     * Driver data dir name under the launcher files dir.
     *
     * The driver falls back to the compiled-in "/sdcard/MG" when MG_DIR_PATH
     * is empty (unwritable under scoped storage). The private subdir keeps
     * the same "mobileglues" name as the driver library so data never mixes
     * with other launchers' directories.
     */
    const val DATA_DIR_NAME: String = "mobileglues"

    fun isCopperOxideId(rendererId: String): Boolean = rendererId == RENDERER_ID

    /**
     * Honest one-line summary for the launch log and the renderer picker.
     * States lineage and the no-spoofing contract; never claims FPS numbers.
     */
    fun summary(): String =
        "Copper Oxide: independent OpenGL-on-GLES renderer (MobileGlues lineage, LGPL-2.1). " +
            "Capabilities are detected, never spoofed."

    /** Stable log line: product (flavor / renderer id). */
    fun logLine(): String = NAME + " (" + FLAVOR + " / " + RENDERER_ID + ")"
}
