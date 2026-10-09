/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package dev.oxide.launcher.game.renderer.onigami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Onigami display-path and probe contract.
 *
 * The launcher publishes NO EGL override (OnigamiRenderer.getRendererEGL is
 * null) so SDL and the Oxide bridge resolve system EGL for display
 * acquisition, configs, surfaces and swap. Display-path ownership lives one
 * layer down: libonigami.so interposes the EGL entry points natively and
 * chains to the host driver underneath (verified per ABI by
 * .github/workflows/onigami.yml). There is exactly one display path and no
 * launcher-level override that could return NO_DISPLAY while the host would
 * succeed.
 *
 * Probe honesty: core getters return null (never "" and never a placeholder)
 * when nothing is known; "<unknown>" is UI-only.
 */
class OnigamiEglContractTest {

    @Before
    fun resetProbe() {
        OnigamiProbe.resetForTest()
    }

    @Test
    fun launcherPublishesNoEglOverride() {
        assertNull(
            "Launcher-level EGL override must stay null; the native " +
                "interposition owns the display path.",
            dev.oxide.launcher.game.renderer.renderers.OnigamiRenderer.getRendererEGL(),
        )
    }

    @Test
    fun noPreloadLibraries() {
        assertEquals(
            emptyList<String>(),
            dev.oxide.launcher.game.renderer.renderers.OnigamiRenderer.getDlopenLibrary().value,
        )
    }

    @Test
    fun nativeLibraryIsOwnBackend() {
        assertEquals(
            "libonigami.so",
            dev.oxide.launcher.game.renderer.renderers.OnigamiRenderer.getRendererLibrary(),
        )
    }

    @Test
    fun unknownGpuIsNullInLogicAndPlaceholderOnlyInUi() {
        assertNull(OnigamiProbe.rendererOrProbe(null))
        assertNull(OnigamiProbe.versionOrProbe(null))
        assertEquals("<unknown>", OnigamiProbe.rendererForUi(null))
        assertEquals("<unknown>", OnigamiProbe.versionForUi(null))
    }

    @Test
    fun probeRetryFillsCacheAndCountsFallbacks() {
        val before = OnigamiProbe.fallbackCount()
        assertNull(OnigamiProbe.rendererOrProbe(null))
        assertTrue(OnigamiProbe.fallbackCount() > before)
        OnigamiProbe.noteProbe("Adreno 825", "OpenGL ES 3.2 V@0600.0")
        assertEquals("Adreno 825", OnigamiProbe.rendererOrProbe(null))
        assertEquals("OpenGL ES 3.2 V@0600.0", OnigamiProbe.versionOrProbe(null))
        assertTrue(OnigamiProbe.isAdreno(OnigamiProbe.rendererOrProbe(null)))
    }

    @Test
    fun blankProbeInputsNeverStick() {
        OnigamiProbe.noteProbe("", "")
        assertNull(OnigamiProbe.rendererOrProbe(null))
        assertNull(OnigamiProbe.versionOrProbe(null))
        OnigamiProbe.noteProbe(null, null)
        assertNull(OnigamiProbe.rendererOrProbe(null))
    }

    @Test
    fun liveAnswersBeatCache() {
        OnigamiProbe.noteProbe("Adreno 825", "OpenGL ES 3.2")
        assertEquals("Cached GPU", OnigamiProbe.rendererOrProbe("Cached GPU"))
        assertEquals("Cached v1", OnigamiProbe.versionOrProbe("Cached v1"))
    }
}
