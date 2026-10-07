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

import dev.oxide.launcher.game.renderer.renderers.CopperOxideRenderer
import dev.oxide.launcher.game.renderer.renderers.HolyGL4ESRenderer
import dev.oxide.launcher.game.renderer.renderers.LTWRenderer
import dev.oxide.launcher.game.version.installed.utils.isBiggerVer
import dev.oxide.launcher.game.version.installed.utils.isLowerOrEqualVer
import dev.oxide.launcher.game.version.installed.utils.isLowerVer
import dev.oxide.launcher.game.versioninfo.popularVersions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 启动游戏前的渲染器版本闸门回归测试，外加新渲染器注册表的接线测试。
 *
 * 闸门在 `ui/screens/content/elements/LauncherElements.kt` 里就是两行：
 *
 *     (currentRenderer.getMinMCVersion()?.let { mcVer.isLowerVer(it) } ?: false) ||
 *         (currentRenderer.getMaxMCVersion()?.let { mcVer.isBiggerVer(it) } ?: false)
 *
 * 也就是选中的 MC 版本严格大于渲染器上限时，渲染器会被直接拒绝。而
 * `GameVersionNumber` 的 ReleaseType 顺序是 `SNAPSHOT < PRE_RELEASE < RC < GA`，
 * 于是把上限写成 "26.3-snapshot-3" 会让 "26.3" 被判为更大——本测试钉住三台
 * 内置渲染器（Copper Oxide、LTW、Holy GL4ES）的上限必须是正式版号 "26.3"。
 * Mojo Zink 已删除：它的 id、类名与 mesa 产物名都在下面的已删除标记里，
 * 出现在自有源码中即判失败。
 *
 * 注册表部分钉住：`Renderers.init()` 之后表里恰好是这三个 id，默认回退（未知 id）
 * 落到 Copper Oxide。源码守卫钉住：渲染器自有源码里不再引用六台被删的旧渲染器
 * 与已删除的 Mojo Zink。
 *
 * 为什么可以直接 new 这些 object：它们都是无参的 `object`，版本上下限只是返回
 * 常量字符串，不碰外部状态。所以这里不需要 Activity/Context，也不需要
 * Robolectric，只有 JUnit4 就够。
 */
class RendererVersionGateTest {

    private val builtinRenderers: List<RendererInterface> = listOf(
        CopperOxideRenderer,
        LTWRenderer,
        HolyGL4ESRenderer
    )

    private val expectedRendererIds: Set<String> = setOf(
        "opengles3_oxide_copper",
        "opengles3_oxide_ltw",
        "opengles2_oxide_holy"
    )

    private val deletedRendererMarkers: List<String> by lazy {
        listOf(
            "opengles3_desktopgl_zink_kopper",
            "gallium_virgl",
            "gallium_freedreno",
            "gallium_panfrost",
            "\"opengles3\"",
            "\"opengles2\"",
            "libng_gl4es.so",
            "libgl4es_114.so",
            "libglxshim.so",
            "libOSMesa_2121.so",
            "libOSMesa_8.so",
            "libOSMesa_2300d.so",
            "object NGGL4ESRenderer",
            "object GL4ESRenderer",
            "object KopperZinkRenderer",
            "object VirGLRenderer",
            "object FreedrenoRenderer",
            "object PanfrostRenderer",
            "object MojoZinkRenderer",
            "MojoZinkRenderer",
            "oxide_vulkan_zink",
            "mojo-zink",
            "Mojo Zink",
            "libEGL_mesa",
            "libgallium_dri",
            "libdrm",
            "VirGLRenderer",
            "Krypton",
            "Kopper Zink",
            "Freedreno (Adreno)",
            "Panfrost (Mali)"
        )
    }

    private fun ownedRendererSources(): List<File> {
        val root = locateMainRoot()
        val owned = listOf(
            "java/dev/oxide/launcher/game/renderer",
            "java/dev/oxide/launcher/game/plugin/renderer",
            "java/dev/oxide/launcher/game/plugin/renderer_v2"
        )
        return owned.map { root.resolve(it) }.filter { it.isDirectory }
            .flatMap { it.walkTopDown().filter { file -> file.isFile }.toList() }
    }

    private fun locateMainRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main")
            if (candidate != null && candidate.isDirectory) return candidate
            dir = dir?.parentFile
        }
        error("could not locate src/main from " + File("").absolutePath)
    }

    @Test
    fun newRenderersAdmitThe26_3Release() {
        builtinRenderers.forEach { renderer ->
            val max = requireNotNull(renderer.getMaxMCVersion()) {
                renderer.getRendererName() + " has no max version, cannot join the gate"
            }
            assertTrue(
                renderer.getRendererName() + " caps at " + max + ", 26.3 would be rejected",
                "26.3".isLowerOrEqualVer(max)
            )
        }
    }

    @Test
    fun newRendererMaximaAreReleasesNotSnapshots() {
        // ReleaseType 中 SNAPSHOT 排在 GA 之前，所以 "26.3-snapshot-3" 严格小于
        // "26.3"：上限一旦写成快照串，同版本的正式版就会被闸门挡在门外。
        assertTrue("26.3-snapshot-3".isLowerVer("26.3"))

        builtinRenderers.forEach { renderer ->
            val max = requireNotNull(renderer.getMaxMCVersion())
            assertEquals(
                renderer.getRendererName() + " must pin the release, got " + max,
                "26.3",
                max
            )
            assertFalse(
                renderer.getRendererName() + " admits 26.4, the cap no longer gates anything",
                "26.4".isLowerOrEqualVer(max)
            )
        }
    }

    @Test
    fun registryContainsExactlyTheThreeNewRenderers() {
        Renderers.init(reset = true)
        val ids = Renderers.getRenderers().map { it.getRendererId() }.toSet()
        assertEquals(expectedRendererIds, ids)
    }

    @Test
    fun unknownRendererFallsBackToCopperOxide() {
        Renderers.init(reset = true)
        Renderers.setCurrentRenderer("00000000-0000-0000-0000-000000000000")
        val current = Renderers.getCurrentRenderer()
        assertEquals(CopperOxideRenderer.getUniqueIdentifier(), current.getUniqueIdentifier())
        assertEquals("opengles3_oxide_copper", current.getRendererId())
    }

    @Test
    fun ownedRendererSourcesNameNoDeletedBuiltin() {
        val offenders = ownedRendererSources().flatMap { file ->
            val text = file.readText()
            deletedRendererMarkers.filter { marker -> text.contains(marker) }
                .map { marker -> file.path + " still names " + marker }
        }
        assertTrue(
            "deleted builtins are still referenced: " + offenders.joinToString("; "),
            offenders.isEmpty()
        )
    }

    @Test
    fun popularVersionsContains26_3() {
        assertTrue(
            "popularVersions has no 26.3: " + popularVersions,
            popularVersions.contains("26.3")
        )
    }

    @Test
    fun popularVersionsStaySortedDescending() {
        val sorted = popularVersions.sortedWith { a, b ->
            if (a.isBiggerVer(b)) -1 else if (b.isBiggerVer(a)) 1 else 0
        }
        assertTrue(
            "popularVersions must be newest first, got " + popularVersions,
            sorted == popularVersions
        )
    }
}
