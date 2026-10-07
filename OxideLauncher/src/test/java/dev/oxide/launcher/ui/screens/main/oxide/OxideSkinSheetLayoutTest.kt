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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 换肤菜单的双栏布局与预览开关（藏披风、缩放距离、栏宽）
 *
 * 3D 预览本身是 WebView（`SkinPreview3D`），layoutlib 不渲染 WebView，
 * 所以这一单进不了 Paparazzi golden——能钉住的就是三样东西：
 * 藏披风与缩放距离这两个纯函数、两栏宽度这组常量，
 * 以及"菜单确实按双栏接上了预览"这条接线（读源码）。
 *
 * 全部是纯函数或读源码：既不组合也不读设置存储，因此不需要 Robolectric。
 */
class OxideSkinSheetLayoutTest {

    private val savedCape = File("saved-cape.png")

    // ---- 藏披风 ---------------------------------------------------------------

    /** 藏起来时落盘有披风也不画：送给预览的就是 null，而不是删文件 */
    @Test
    fun hiddenCapeHidesEvenASavedCape() {
        assertNull(
            oxideSkinPreviewCapeFile(
                savedCapeFile = savedCape,
                savedCapeExists = true,
                capeHidden = true,
            )
        )
    }

    /** 没藏时文件在就画：开关默认关着，老行为原样保留 */
    @Test
    fun visibleCapeFollowsTheSavedFilePresence() {
        assertEquals(
            savedCape,
            oxideSkinPreviewCapeFile(
                savedCapeFile = savedCape,
                savedCapeExists = true,
                capeHidden = false,
            )
        )
        assertNull(
            "a cape file that is gone must not stay on the model",
            oxideSkinPreviewCapeFile(
                savedCapeFile = savedCape,
                savedCapeExists = false,
                capeHidden = false,
            )
        )
    }

    /** 开关优先于一切：文件不在时藏与不藏都是 null，不会凭空变出一张披风 */
    @Test
    fun hiddenCapeWithNoSavedFileIsStillNull() {
        assertNull(
            oxideSkinPreviewCapeFile(
                savedCapeFile = null,
                savedCapeExists = false,
                capeHidden = true,
            )
        )
        assertNull(
            oxideSkinPreviewCapeFile(
                savedCapeFile = null,
                savedCapeExists = false,
                capeHidden = false,
            )
        )
    }

    // ---- 缩放距离 ---------------------------------------------------------------

    /** 缺省距离落在允许区间里：JS 与 Kotlin 用的是同一组数 */
    @Test
    fun defaultDistanceIsInsideTheAllowedRange() {
        assertTrue(
            "min must be below max",
            OxideSkinPreviewDistanceMin < OxideSkinPreviewDistanceMax,
        )
        assertTrue(
            "default must be inside the range",
            OxideSkinPreviewDistanceDefault in
                OxideSkinPreviewDistanceMin..OxideSkinPreviewDistanceMax,
        )
        assertTrue("step must move the camera", OxideSkinPreviewDistanceStep > 0)
    }

    /** 夹取：越界的值收敛到边界，区间里的值原样通过 */
    @Test
    fun distanceClampKeepsValuesInsideTheRange() {
        assertEquals(
            OxideSkinPreviewDistanceMin,
            oxideSkinPreviewDistanceClamped(OxideSkinPreviewDistanceMin - 100),
        )
        assertEquals(
            OxideSkinPreviewDistanceMax,
            oxideSkinPreviewDistanceClamped(OxideSkinPreviewDistanceMax + 100),
        )
        assertEquals(
            OxideSkinPreviewDistanceDefault,
            oxideSkinPreviewDistanceClamped(OxideSkinPreviewDistanceDefault),
        )
    }

    /** 放大走一步：距离按步长缩短，到头停在最近处而不是穿过模型 */
    @Test
    fun zoomInMovesOneStepCloserAndStopsAtTheMinimum() {
        assertEquals(
            OxideSkinPreviewDistanceDefault - OxideSkinPreviewDistanceStep,
            oxideSkinPreviewZoomIn(OxideSkinPreviewDistanceDefault),
        )
        assertEquals(
            OxideSkinPreviewDistanceMin,
            oxideSkinPreviewZoomIn(OxideSkinPreviewDistanceMin),
        )
        var distance = OxideSkinPreviewDistanceDefault
        repeat(20) { distance = oxideSkinPreviewZoomIn(distance) }
        assertEquals(OxideSkinPreviewDistanceMin, distance)
    }

    /** 缩小走一步：距离按步长拉远，到头停在最远处而不是飞出去 */
    @Test
    fun zoomOutMovesOneStepBackAndStopsAtTheMaximum() {
        assertEquals(
            OxideSkinPreviewDistanceDefault + OxideSkinPreviewDistanceStep,
            oxideSkinPreviewZoomOut(OxideSkinPreviewDistanceDefault),
        )
        assertEquals(
            OxideSkinPreviewDistanceMax,
            oxideSkinPreviewZoomOut(OxideSkinPreviewDistanceMax),
        )
        var distance = OxideSkinPreviewDistanceDefault
        repeat(20) { distance = oxideSkinPreviewZoomOut(distance) }
        assertEquals(OxideSkinPreviewDistanceMax, distance)
    }

    /** 放大再缩小回到原地：步长对称，按钮不会把视角越按越偏 */
    @Test
    fun zoomInThenOutReturnsToTheSameDistance() {
        val moved = oxideSkinPreviewZoomIn(OxideSkinPreviewDistanceDefault)
        assertEquals(OxideSkinPreviewDistanceDefault, oxideSkinPreviewZoomOut(moved))
    }

    // ---- 栏宽 -------------------------------------------------------------------

    /** 两栏加起来正好是一整行：预览格比控制格宽，模型才有地方转 */
    @Test
    fun twoPaneWeightsFillTheRowWithAPreviewMajority() {
        assertEquals(
            0.55f,
            OxideSkinTwoPanePreviewWeight,
            0.001f,
        )
        assertEquals(
            0.45f,
            OxideSkinTwoPaneControlsWeight,
            0.001f,
        )
        assertEquals(
            1f,
            OxideSkinTwoPanePreviewWeight + OxideSkinTwoPaneControlsWeight,
            0.001f,
        )
        assertTrue(
            "the preview pane must be the wider one",
            OxideSkinTwoPanePreviewWeight > OxideSkinTwoPaneControlsWeight,
        )
    }

    // ---- 接线：换肤菜单 -----------------------------------------------------------

    /**
     * 换肤菜单是固定的左右两栏，整张单子不跟着滚动
     *
     * 左格只放预览且不滚动，右栏放导入与披风；外层滚动容器只属于右栏，
     * 否则拖拽旋转模型的手势会被滚动吃掉，披风区又会被裁出视野。
     */
    @Test
    fun skinSheetIsAFixedTwoPaneRow() {
        val source = code("ui/screens/main/oxide/OxideAccountPage.kt")
        source.mustContain(
            "val skinTarget = sheet as? AccountSheet.Skin",
            "the skin sheet must branch off before the scrolling container",
        )
        source.mustContain(
            ".weight(OxideSkinTwoPanePreviewWeight)",
            "the left pane must take the preview share of the row",
        )
        source.mustContain(
            ".weight(OxideSkinTwoPaneControlsWeight)",
            "the right pane must take the controls share of the row",
        )
        source.mustContain(
            "interactionEnabled = true",
            "the preview must accept drag-to-rotate now that no outer scroll steals it",
        )
        assertTrue(
            "the preview must no longer be a display-only box",
            !source.contains("interactionEnabled = false"),
        )
    }

    /**
     * 其余浮层保持原来的滚动行为：双栏只属于换肤菜单
     *
     * 登录、离线创建与服务器登录仍然是纵向滚动的一栏，
     * 换肤菜单的改动不能把它们的滚动容器顺手拆掉。
     */
    @Test
    fun otherSheetsKeepTheirScrollingContainer() {
        val source = code("ui/screens/main/oxide/OxideAccountPage.kt")
        source.mustContain(
            ".verticalScroll(rememberScrollState())",
            "non-skin sheets must keep their scrolling column",
        )
        source.mustContain(
            "is AccountSheet.Skin -> Unit",
            "the scrolling branch must still name the skin sheet to stay exhaustive",
        )
    }

    /**
     * 预览的三个开关都接上了：藏披风、动画、距离与复位
     *
     * 藏披风走的是 takeUnless 而不是删文件；动画是 ModelAnimation 的下拉；
     * 缩放按步长改距离；复位把距离打回默认并翻转一次复位信号。
     */
    @Test
    fun skinSheetWiresPreviewSwitches() {
        val source = code("ui/screens/main/oxide/OxideAccountPage.kt")
        source.mustContain(
            "capeFile = preview.capeFile.takeUnless { capeHidden }",
            "hiding the cape must null the preview input without touching the file",
        )
        source.mustContain(
            "OxideToggle(",
            "the hide-cape switch must be a toggle row",
        )
        source.mustContain(
            "ModelAnimation.entries",
            "the animation picker must cover every ModelAnimation entry",
        )
        source.mustContain(
            "DropdownMenu(",
            "the animation picker must be a dropdown, not a full list",
        )
        source.mustContain(
            "oxideSkinPreviewZoomIn(previewDistance)",
            "the zoom-in control must step the preview distance",
        )
        source.mustContain(
            "oxideSkinPreviewZoomOut(previewDistance)",
            "the zoom-out control must step the preview distance",
        )
        source.mustContain(
            "resetViewKey = previewResetTick",
            "the reset control must flip the preview reset signal",
        )
    }

    // ---- 接线：预览组件与 JS -------------------------------------------------------

    /**
     * SkinPreview3D 把距离与复位透给 WebView
     *
     * 缩放按钮改的是 distance，复位按钮翻的是 resetViewKey，
     * 两条都必须一路调到 PlayerSkin 的 JS 调用上。
     */
    @Test
    fun skinPreview3DExposesDistanceAndReset() {
        val source = code("ui/components/_PlayerSkin.kt")
        source.mustContain(
            "fun setDistance(distance",
            "PlayerSkin must expose a distance-only JS call",
        )
        source.mustContain(
            "distance: Int = OxideSkinPreviewDistanceDefault",
            "SkinPreview3D must carry the preview distance with the shared default",
        )
        source.mustContain(
            "resetViewKey: Any? = null",
            "SkinPreview3D must carry an explicit view-reset signal",
        )
        source.mustContain(
            "playerSkin.setDistance(distance)",
            "distance changes must reach the page without reframing the orbit",
        )
        source.mustContain(
            "playerSkin.setAzimuthAndPitch(azimuth, pitch, distance)",
            "reset must restore the viewing angle together with the distance",
        )
        assertTrue(
            "panning must stay off: it drags the model out of the pane",
            source.contains("enablePan") || codeAsset("skinview.js").contains("enablePan = false"),
        )
    }

    /**
     * skinview.js 提供缩放钩子，旋转与双击复位原样保留
     *
     * 缩放打开后拖拽旋转（enableRotate）与双击回正（dblclick）都不能丢，
     * 平移（enablePan）继续关着，否则模型会被拖出格子。
     */
    @Test
    fun skinViewJsExposesASetDistanceHook() {
        val js = codeAsset("skinview.js")
        js.mustContain(
            "function setDistance(",
            "the page must expose a distance-only zoom hook",
        )
        js.mustContain(
            "enableZoom = true",
            "pinch/button zoom must be enabled in the viewer controls",
        )
        js.mustContain(
            "enablePan = false",
            "panning must stay disabled",
        )
        js.mustContain(
            "setAzimuthAndPitch(",
            "the existing azimuth/pitch entry must stay in place",
        )
        js.mustContain(
            "dblclick",
            "double-tap view reset must stay in place",
        )
    }

    // ---- 读源码的帮手 ---------------------------------------------------------------

    /** 源码断言：缺了哪一条接线就报哪一条，绝不悄悄跳过 */
    private fun String.mustContain(fragment: String, message: String) {
        assertTrue(message, contains(fragment))
    }

    private fun code(relativePath: String): String = locateJava(relativePath).readText()

    private fun codeAsset(name: String): String = locateAsset(name).readText()

    /**
     * 从当前工作目录往上找源文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯；找不到就直接报错，
     * 绝不悄悄跳过——那样这条测试等于没有。
     */
    private fun locateJava(relativePath: String): File {
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

    /** assets 目录与源文件不在同一棵树下，skinview.js 单独找 */
    private fun locateAsset(name: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/assets/skinview/$name")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/assets/skinview/$name from " +
                File("").absolutePath
        )
    }
}
