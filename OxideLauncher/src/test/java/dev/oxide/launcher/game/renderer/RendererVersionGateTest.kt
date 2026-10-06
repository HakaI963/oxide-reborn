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

import dev.oxide.launcher.game.renderer.renderers.FreedrenoRenderer
import dev.oxide.launcher.game.renderer.renderers.KopperZinkRenderer
import dev.oxide.launcher.game.renderer.renderers.NGGL4ESRenderer
import dev.oxide.launcher.game.renderer.renderers.VirGLRenderer
import dev.oxide.launcher.game.version.installed.utils.isBiggerVer
import dev.oxide.launcher.game.version.installed.utils.isLowerOrEqualVer
import dev.oxide.launcher.game.version.installed.utils.isLowerVer
import dev.oxide.launcher.game.versioninfo.popularVersions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动游戏前的渲染器版本闸门回归测试。
 *
 * 闸门在 `ui/screens/content/elements/LauncherElements.kt` 里就是两行：
 *
 *     (currentRenderer.getMinMCVersion()?.let { mcVer.isLowerVer(it) } ?: false) ||
 *         (currentRenderer.getMaxMCVersion()?.let { mcVer.isBiggerVer(it) } ?: false)
 *
 * 也就是**选中的 MC 版本严格大于渲染器上限时，渲染器会被直接拒绝**。而
 * `GameVersionNumber` 的 ReleaseType 顺序是 `SNAPSHOT < PRE_RELEASE < RC < GA`，
 * 于是把上限写成 "26.3-snapshot-3" 会让 "26.3" 被判为更大——四个现代渲染器
 * （Freedreno、Kopper Zink、Krypton Wrapper、VirGL）在 26.3 上**同时**被拒绝，
 * 表现就是"26.3 上任何渲染器都不工作"。本测试钉住这件事。
 *
 * 为什么可以直接 new 这些 object：它们都是无参的 `object`，`getMaxMCVersion()`
 * 只是返回常量字符串；唯一碰到外部状态的地方（VirGLRenderer 的 PathManager）被关在
 * `lazy {}` 里，取上限不会触发。所以这里不需要 Activity/Context，也不需要 Robolectric，
 * 只有 JUnit4 就够（`build.gradle.kts` 里 testImplementation 只有 junit / mockwebserver3 /
 * paparazzi，没有 Robolectric）。
 *
 * 注意 GL4ESRenderer 与 PanfrostRenderer 不在这份集合里：它们的上限是 "1.21.4"，
 * 是**真的**停在那里，不是被写错了，所以本测试不约束它们，也不该约束它们。
 */
class RendererVersionGateTest {

    /**
     * 走的是启动页真正使用的那四个渲染器（对应 `Renderers.init()` 里的现代渲染器）。
     */
    private val modernRenderers: List<RendererInterface> = listOf(
        NGGL4ESRenderer,
        KopperZinkRenderer,
        VirGLRenderer,
        FreedrenoRenderer
    )

    @Test
    fun modernRenderersAdmitThe26_3Release() {
        modernRenderers.forEach { renderer ->
            val max = requireNotNull(renderer.getMaxMCVersion()) {
                "${renderer.getRendererName()} 没有声明上限版本，无法参与版本闸门"
            }
            assertTrue(
                "${renderer.getRendererName()} 的上限是 $max，26.3 正式版比它更新，会被闸门拒绝",
                "26.3".isLowerOrEqualVer(max)
            )
        }
    }

    @Test
    fun modernRenderersDeclareTheReleaseNotASnapshot() {
        // 触发这次回归的那条规则：ReleaseType 中 SNAPSHOT 排在 GA 之前，
        // 所以 "26.3-snapshot-3" 严格小于 "26.3"。这正是旧上限把 26.3 挡在门外的原因。
        assertTrue("26.3-snapshot-3".isLowerVer("26.3"))

        modernRenderers.forEach { renderer ->
            val max = requireNotNull(renderer.getMaxMCVersion())
            assertFalse(
                "${renderer.getRendererName()} 的上限 $max 是快照串，快照号永远比同版本的正式版小，" +
                        "等于把同版本的正式版挡在闸门外",
                max.contains("-snapshot-")
            )
            assertFalse(
                "${renderer.getRendererName()} 的上限 $max 仍是快照串",
                "26.3".isBiggerVer(max)
            )
        }
    }

    @Test
    fun modernRendererMaximaStillGateNewerReleases() {
        // 反向护栏：把上限抬到 26.3 之后不能变成"无限制"，26.4 仍然必须被拒绝。
        modernRenderers.forEach { renderer ->
            val max = requireNotNull(renderer.getMaxMCVersion())
            assertFalse(
                "${renderer.getRendererName()} 的上限 $max 没有拦住比它更新的 26.4",
                "26.4".isLowerOrEqualVer(max)
            )
        }
    }

    @Test
    fun popularVersionsContains26_3() {
        assertTrue(
            "热门版本列表里没有 26.3，26.3 不会出现在安装/搜索的默认版本筛选中",
            popularVersions.contains("26.3")
        )
    }

    @Test
    fun popularVersionsStaySortedDescending() {
        val sorted = popularVersions.sortedWith { a, b ->
            if (a.isBiggerVer(b)) -1 else if (b.isBiggerVer(a)) 1 else 0
        }
        assertTrue(
            "热门版本列表必须是降序的，实际为 $popularVersions",
            sorted == popularVersions
        )
    }
}