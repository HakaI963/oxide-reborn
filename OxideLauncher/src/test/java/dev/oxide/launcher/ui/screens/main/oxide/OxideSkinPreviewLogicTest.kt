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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.game.account.wardrobe.SkinModelType
import dev.oxide.launcher.ui.screens.content.elements.ChangeSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 皮肤菜单里 3D 预览的取数逻辑（`oxideSkinPreviewSource` / `oxideSkinPreviewHeight`）
 *
 * 预览本身是 WebView（`SkinPreview3D`），layoutlib 不渲染 WebView，所以它进不了
 * Paparazzi  golden——能钉住的就是"哪一份皮肤与披风会被送到那一格里"这个纯函数，
 * 以及"皮肤菜单确实把预览接上了"这条接线。两者都在这里。
 *
 * 全部是纯函数或读源码：既不组合也不读设置存储，因此不需要 Robolectric。
 */
class OxideSkinPreviewLogicTest {

    private val savedSkin = File("saved-skin.png")
    private val savedCape = File("saved-cape.png")
    private val pendingSkin = File("pending-skin.png")

    // ---- 待应用的那一份优先 -----------------------------------------------

    /** 导入 PNG 之后、按下 Apply 之前，预览必须跟着手里那一份走 */
    @Test
    fun aPendingSkinWinsOverTheSavedOne() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.ChangeSkinData(pendingSkin, SkinModelType.ALEX),
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.STEVE,
            savedSkinExists = true,
            savedCapeFile = savedCape,
            savedCapeExists = true,
        )
        assertEquals(pendingSkin, preview.skinFile)
        // 手臂型号属于待应用的那一份，点"细臂"立刻重画，而不是等 Apply
        assertEquals(SkinModelType.ALEX, preview.modelType)
    }

    /** 没有待应用的那一份时，预览读已保存的那一份 */
    @Test
    fun withoutAPendingSkinTheSavedOneIsShown() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.None,
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.STEVE,
            savedSkinExists = true,
            savedCapeFile = null,
            savedCapeExists = false,
        )
        assertEquals(savedSkin, preview.skinFile)
        assertEquals(SkinModelType.STEVE, preview.modelType)
    }

    /** 已保存的那一份不在磁盘上时，交回默认 Steve，而不是显示一张不存在的图 */
    @Test
    fun aSavedSkinThatIsGoneFallsBackToTheDefault() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.None,
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.ALEX,
            savedSkinExists = false,
            savedCapeFile = null,
            savedCapeExists = false,
        )
        assertNull(preview.skinFile)
    }

    /** ResetSkin 与 None 一样：都是"没有待应用的那一份" */
    @Test
    fun resetSkinAlsoFallsBackToTheSavedOne() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.ResetSkin,
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.STEVE,
            savedSkinExists = true,
            savedCapeFile = null,
            savedCapeExists = false,
        )
        assertEquals(savedSkin, preview.skinFile)
    }

    /** 待应用的那一份即使已保存的不在，也照样显示——导入就是导入 */
    @Test
    fun aPendingSkinIsShownEvenWhenTheSavedOneIsGone() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.ChangeSkinData(pendingSkin, SkinModelType.STEVE),
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.NONE,
            savedSkinExists = false,
            savedCapeFile = null,
            savedCapeExists = false,
        )
        assertEquals(pendingSkin, preview.skinFile)
    }

    /** 手臂型号是 NONE 时交给预览自己从像素判断，而不是当成"没有型号" */
    @Test
    fun aPendingSkinWithNoModelLetsThePreviewDetectIt() {
        val preview = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.ChangeSkinData(pendingSkin, SkinModelType.NONE),
            savedSkinFile = savedSkin,
            savedSkinModel = SkinModelType.ALEX,
            savedSkinExists = true,
            savedCapeFile = null,
            savedCapeExists = false,
        )
        assertEquals(SkinModelType.NONE, preview.modelType)
    }

    // ---- 披风 ---------------------------------------------------------------

    /** 披风没有"待应用"这一态：文件在就画，不在就不画 */
    @Test
    fun theCapeFollowsTheSavedFilePresence() {
        val withCape = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.None,
            savedSkinFile = null,
            savedSkinModel = SkinModelType.NONE,
            savedSkinExists = false,
            savedCapeFile = savedCape,
            savedCapeExists = true,
        )
        assertEquals(savedCape, withCape.capeFile)

        val withoutCape = oxideSkinPreviewSource(
            pendingSkin = ChangeSkin.None,
            savedSkinFile = null,
            savedSkinModel = SkinModelType.NONE,
            savedSkinExists = false,
            savedCapeFile = savedCape,
            savedCapeExists = false,
        )
        assertNull("a cape file that is gone must not stay on the model", withoutCape.capeFile)
    }

    // ---- 高度 ---------------------------------------------------------------

    /** 高度跟着卡片最小宽度走，比例 1.2 */
    @Test
    fun thePreviewHeightFollowsTheCardWidth() {
        assertEquals(342, oxideSkinPreviewHeight(285))
        assertEquals(180, oxideSkinPreviewHeight(150))
    }

    /** 再矮也夹在 150dp：低于下限就看不出是个玩家模型 */
    @Test
    fun thePreviewHeightNeverGoesBelowTheMinimum() {
        assertEquals(OxideSkinPreviewMinHeightDp, oxideSkinPreviewHeight(100))
        assertEquals(OxideSkinPreviewMinHeightDp, oxideSkinPreviewHeight(0))
        assertEquals(OxideSkinPreviewMinHeightDp, oxideSkinPreviewHeight(-50))
    }

    /** 再高也夹在 420dp：高于上限会把导入行与 Apply 一起挤出视野 */
    @Test
    fun thePreviewHeightNeverGoesAboveTheMaximum() {
        assertEquals(OxideSkinPreviewMaxHeightDp, oxideSkinPreviewHeight(500))
        assertEquals(OxideSkinPreviewMaxHeightDp, oxideSkinPreviewHeight(4000))
    }

    /** 上下限本身就是合法输入，夹取不会把它们改掉 */
    @Test
    fun theBoundsThemselvesAreValidHeights() {
        assertEquals(150, oxideSkinPreviewHeight(125))
        assertEquals(420, oxideSkinPreviewHeight(350))
        for (width in intArrayOf(0, 100, 125, 285, 350, 500, 4000)) {
            val height = oxideSkinPreviewHeight(width)
            assertTrue(
                "width=$width produced height=$height outside the declared bounds",
                height in OxideSkinPreviewMinHeightDp..OxideSkinPreviewMaxHeightDp,
            )
        }
    }

    // ---- 接线 ---------------------------------------------------------------

    /**
     * 皮肤菜单确实把 3D 预览接上了，而且接的是取数函数给出的那一份
     *
     * 预览进不了 golden（WebView 在 layoutlib 里不渲染），所以接线本身用读源码的方式
     * 钉住：菜单里必须出现 `SkinPreview3D`，且三个入参都来自 [oxideSkinPreviewSource]。
     * 少了任何一条，"预览不刷新"或"预览根本没接"都会静悄悄地在重构里溜回来。
     */
    @Test
    fun theSkinSheetWiresThePreviewToThePreviewSource() {
        val source = readSource("OxideAccountPage.kt")
        assertTrue(
            "the skin sheet must embed the existing SkinPreview3D WebView",
            source.contains("SkinPreview3D("),
        )
        assertTrue(
            "the preview must show the skin chosen by oxideSkinPreviewSource",
            source.contains("skinFile = preview.skinFile"),
        )
        assertTrue(
            "the preview must show the cape chosen by oxideSkinPreviewSource",
            source.contains("capeFile = preview.capeFile"),
        )
        assertTrue(
            "the preview must follow the model chosen by oxideSkinPreviewSource",
            source.contains("modelType = preview.modelType"),
        )
        assertTrue(
            "the sheet must ask oxideSkinPreviewSource what to show",
            source.contains("oxideSkinPreviewSource("),
        )
    }

    private fun readSource(name: String): String = locate(
        "ui/screens/main/oxide/$name"
    ).readText()

    /**
     * 从当前工作目录往上找源文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯；找不到就直接报错，
     * 绝不悄悄跳过——那样这条测试等于没有。
     */
    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                File("").absolutePath
        )
    }
}
