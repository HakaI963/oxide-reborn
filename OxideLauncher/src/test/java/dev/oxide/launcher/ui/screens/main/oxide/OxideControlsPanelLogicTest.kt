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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 控制分类的行可见性
 *
 * 「关掉手势之后哪些行会消失」「SDL 直通下还在显示的那些映射行到底是哪些」
 * 是这一页最容易被改坏的地方。控件留着只置灰已经算一种处理，这里改成前提不满足
 * 就不渲染；代价是纯函数与实际渲染必须永远一致，否则界面上会出现一排
 * 「纯函数说该隐藏、却还摆在那儿」的控件——点下去什么也不发生。
 *
 * 因此这里既测纯函数本身，也直接把 [OxideControlsPanel] 的源码钉住：
 * 凡是会条件隐藏的行，界面上必须真的查了那张表。
 */
class OxideControlsPanelLogicTest {

    /** 除鼠标模式外全部打开，且设备什么传感器都有 */
    private fun allOn() = OxideControlPrerequisites(
        mouseClickMode = false,
        mouseSlideMode = true,
        gestureControl = true,
        gyroscopeAvailable = true,
        gyroscopeControl = true,
        gyroscopeSmoothing = true,
        gamepadControl = true,
        gamepadMapped = true,
    )

    /** 除鼠标模式外全部关闭 */
    private fun allOff() = allOn().copy(
        gestureControl = false,
        gyroscopeControl = false,
        gyroscopeSmoothing = false,
        gamepadControl = false,
        gamepadMapped = false,
    )

    // -----------------------------------------------------------------------
    // 手势
    // -----------------------------------------------------------------------

    @Test
    fun `gestures off hides every gesture row except the switch itself`() {
        val rows = visibleControlRows(allOff())
        assertTrue("the switch that turns gestures on must stay", OxideControlRow.GestureControl in rows)
        for (hidden in listOf(
            OxideControlRow.GestureTapAction,
            OxideControlRow.GestureLongPressAction,
            OxideControlRow.GestureLongPressDelay,
        )) {
            assertFalse("$hidden must not be offered while gestures are off", hidden in rows)
        }
    }

    @Test
    fun `gestures on brings the tap action, the long-press action and the delay back`() {
        val rows = visibleControlRows(allOn())
        for (shown in listOf(
            OxideControlRow.GestureTapAction,
            OxideControlRow.GestureLongPressAction,
            OxideControlRow.GestureLongPressDelay,
        )) {
            assertTrue("$shown must be offered once gestures are on", shown in rows)
        }
    }

    // -----------------------------------------------------------------------
    // 手柄
    // -----------------------------------------------------------------------

    @Test
    fun `gamepad off hides every gamepad row except the switch itself`() {
        val rows = visibleControlRows(allOff())
        assertTrue(
            "the switch that turns the gamepad on must stay",
            OxideControlRow.GamepadControl in rows
        )
        for (hidden in listOf(
            OxideControlRow.GamepadInputMode,
            OxideControlRow.GamepadMappingConfig,
            OxideControlRow.GamepadBindings,
            OxideControlRow.GamepadDeadZone,
            OxideControlRow.GamepadCursorSensitivity,
            OxideControlRow.GamepadCameraSensitivity,
            OxideControlRow.GamepadJoystickMode,
        )) {
            assertFalse("$hidden must not be offered while the gamepad is off", hidden in rows)
        }
    }

    @Test
    fun `sdl passthrough keeps the mode picker but drops the remapping rows`() {
        // SDL 直通由游戏自己去认手柄，启动器这边没有映射可调；
        // 旧设置页用的是同一个条件（gamepadControl && inputMode == Mapped）
        val rows = visibleControlRows(allOn().copy(gamepadMapped = false))
        assertTrue(
            "the user still has to be able to switch back to the mapped mode",
            OxideControlRow.GamepadInputMode in rows
        )
        for (hidden in listOf(
            OxideControlRow.GamepadMappingConfig,
            OxideControlRow.GamepadBindings,
            OxideControlRow.GamepadDeadZone,
            OxideControlRow.GamepadCursorSensitivity,
            OxideControlRow.GamepadCameraSensitivity,
            OxideControlRow.GamepadJoystickMode,
        )) {
            assertFalse("$hidden means nothing under SDL passthrough", hidden in rows)
        }
    }

    @Test
    fun `mapped mode offers every gamepad row`() {
        val rows = visibleControlRows(allOn())
        assertEquals(
            OxideControlRow.entries.filter { it.section == OxideControlSection.Gamepad },
            rows.filter { it.section == OxideControlSection.Gamepad }
        )
    }

    // -----------------------------------------------------------------------
    // 陀螺仪
    // -----------------------------------------------------------------------

    @Test
    fun `a device without a gyroscope keeps the switch but loses every derived row`() {
        val rows = visibleControlRows(allOn().copy(gyroscopeAvailable = false))
        assertTrue(
            "the switch is what explains why nothing else is there",
            OxideControlRow.GyroscopeControl in rows
        )
        for (hidden in listOf(
            OxideControlRow.GyroscopeSensitivity,
            OxideControlRow.GyroscopeSampleRate,
            OxideControlRow.GyroscopeSmoothing,
            OxideControlRow.GyroscopeSmoothingWindow,
            OxideControlRow.GyroscopeInvertX,
            OxideControlRow.GyroscopeInvertY,
        )) {
            assertFalse("$hidden cannot work without the sensor", hidden in rows)
        }
    }

    @Test
    fun `smoothing off hides the smoothing window but keeps the smoothing switch`() {
        val rows = visibleControlRows(allOn().copy(gyroscopeSmoothing = false))
        assertTrue(OxideControlRow.GyroscopeSmoothing in rows)
        assertFalse(
            "a smoothing window while smoothing is off would do nothing",
            OxideControlRow.GyroscopeSmoothingWindow in rows
        )
    }

    // -----------------------------------------------------------------------
    // 鼠标
    // -----------------------------------------------------------------------

    @Test
    fun `click mode offers hiding the mouse and drops the slide-only toggle`() {
        val rows = visibleControlRows(allOn().copy(mouseClickMode = true, mouseSlideMode = false))
        assertTrue(OxideControlRow.HideMouse in rows)
        assertFalse(OxideControlRow.EnableMouseClick in rows)
    }

    @Test
    fun `slide mode is the mirror image`() {
        val rows = visibleControlRows(allOn())
        assertFalse(OxideControlRow.HideMouse in rows)
        assertTrue(OxideControlRow.EnableMouseClick in rows)
    }

    @Test
    fun `neither mouse mode ever hides the mode picker itself`() {
        assertTrue(OxideControlRow.MouseMode.isVisible(allOn().copy(mouseSlideMode = false)))
        assertTrue(OxideControlRow.MouseMode.isVisible(allOn().copy(mouseClickMode = true)))
    }

    // -----------------------------------------------------------------------
    // 整体不变量
    // -----------------------------------------------------------------------

    @Test
    fun `turning everything off never removes a switch that turns something back on`() {
        // 前提全关时仍然存在的行正是那些入口：没有它们用户就再也开不回来了
        val rows = visibleControlRows(allOff())
        for (entry in listOf(
            OxideControlRow.MouseMode,
            OxideControlRow.PhysicalMouseMode,
            OxideControlRow.GestureControl,
            OxideControlRow.GyroscopeControl,
            OxideControlRow.GamepadControl,
            OxideControlRow.ControlLayouts,
        )) {
            assertTrue("$entry must survive with everything switched off", entry in rows)
        }
    }

    @Test
    fun `the visible rows keep the declaration order, which is the order on screen`() {
        val rows = visibleControlRows(allOn())
        assertEquals(rows.sortedBy { it.ordinal }, rows)
    }

    @Test
    fun `only the mouse mode toggles decide which of the two mouse rows shows`() {
        // 虚拟鼠标只有两种模式，因此这两行必须正好互斥：不能同时出现，也不能同时消失
        val click = visibleControlRows(allOn().copy(mouseClickMode = true, mouseSlideMode = false))
        val slide = visibleControlRows(allOn())
        assertTrue(
            "click mode offers hide-mouse and not slide-click",
            (OxideControlRow.HideMouse in click) && (OxideControlRow.EnableMouseClick !in click)
        )
        assertTrue(
            "slide mode offers slide-click and not hide-mouse",
            (OxideControlRow.EnableMouseClick in slide) && (OxideControlRow.HideMouse !in slide)
        )
    }

    @Test
    fun `every row the pure function can hide really is gated in the panel`() {
        val configurations = listOf(
            allOn(),
            allOff(),
            allOn().copy(mouseClickMode = true, mouseSlideMode = false),
            allOn().copy(gamepadMapped = false),
            allOn().copy(gyroscopeAvailable = false),
            allOn().copy(gyroscopeSmoothing = false),
        )
        val source = panelSource
        for (row in OxideControlRow.entries) {
            val always = configurations.all { row in visibleControlRows(it) }
            val never = configurations.none { row in visibleControlRows(it) }
            if (always || never) continue
            assertTrue(
                "$row can be hidden, so the panel must gate it on the row table",
                source.contains("OxideControlRow.${row.name} in rows")
            )
        }
    }

    @Test
    fun `every row that is never hidden is rendered without a gate`() {
        // 反过来也钉住：那几行是"前提全关也还在"的入口，给它们加一个永远为真的
        // 判断只会让后来的人误以为它们也会被隐藏
        val configurations = listOf(
            allOn(),
            allOff(),
            allOn().copy(mouseClickMode = true, mouseSlideMode = false),
            allOn().copy(gamepadMapped = false),
            allOn().copy(gyroscopeAvailable = false),
            allOn().copy(gyroscopeSmoothing = false),
        )
        val source = panelSource
        for (row in OxideControlRow.entries) {
            if (!configurations.all { row in visibleControlRows(it) }) continue
            assertFalse(
                "$row is always available, so gating it is dead code",
                source.contains("OxideControlRow.${row.name} in rows")
            )
        }
    }

    @Test
    fun `the panel renders no dimension of its own`() {
        // 每一处尺寸都必须来自 metrics；写死的 dp 会在某一档宽度上错开
        assertFalse(
            "hard-coded dp in $PANEL: every dimension must come from metrics",
            panelSource.contains(".dp")
        )
    }

    @Test
    fun `the panel only writes settings through save and only reads them through state`() {
        val calls = Regex("""AllSettings\.(\w+)\.(\w+)\(""").findAll(panelSource)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

        val badWrites = calls.filter { (_, method) -> method != "save" }
        assertTrue(
            "settings must only be written with save(...); found " +
                badWrites.joinToString { "${it.first}.${it.second}(" },
            badWrites.isEmpty()
        )
        assertFalse("getValue() bypasses the observable state", panelSource.contains(".getValue()"))

        val referenced = Regex("""AllSettings\.(\w+)""").findAll(panelSource)
            .map { it.groupValues[1] }
            .distinct()
            .toSet()
        // 八个指针热点由启动器本来就有的 MouseHotspotEditorDialog 写，
        // 面板只把设置单元递进去，因此这里只读
        val writtenElsewhere = HOTSPOT_UNITS.keys
        val onlyRead = referenced - calls.map { it.first }.toSet() - writtenElsewhere
        assertTrue("these settings are read but never written here: $onlyRead", onlyRead.isEmpty())
    }

    // -----------------------------------------------------------------------
    // 指针热点
    // -----------------------------------------------------------------------

    @Test
    fun `all eight pointer hotspots are reachable from the panel, each with its own shape`() {
        // 少一个就是游戏里那种指针少一种形状可用，而且界面上完全看不出来
        val declared = Regex("""(?m)^\s+val\s+(\w+MouseHotspot)\s*=\s*parcelableSetting\("""")
            .findAll(locate("setting/AllSettings.kt").readText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(HOTSPOT_UNITS.keys, declared.toSet())

        for ((unit, shape) in HOTSPOT_UNITS) {
            assertTrue(
                "$unit must be offered in the controls panel, with the $shape preview",
                panelSource.contains("AllSettings.$unit,") && panelSource.contains("CursorShape.$shape,")
            )
        }
    }

    // -----------------------------------------------------------------------
    // 不再推进旧设置栈
    // -----------------------------------------------------------------------

    @Test
    fun `java, renderer, control and gamepad never push the legacy settings stack`() {
        // 旧 Zalith 设置页带着它自己上游的图标顶栏与粉色强调轨；从新设置页跳回去
        // 就是那个漏。这四类现在由设置页自己承接（两个抽屉 + 控制面板），
        // 因此 navKey 必须返回 null，调用点才不会推进旧栈
        val source = locate("ui/screens/main/MainScreen.kt").readText()
        val navKey = Regex("""(?s)private fun OxideSettingsSection\.navKey\(\).*?\n\}""")
            .find(source)?.value
            ?: error("could not find OxideSettingsSection.navKey() in MainScreen.kt")

        for (section in listOf("Renderer", "JavaManager", "Control", "Gamepad")) {
            assertTrue(
                "$section is hosted by the Oxide settings page, so it must map to null",
                navKey.contains("OxideSettingsSection.$section -> null")
            )
        }
        assertFalse(
            "no settings section may push a legacy NormalNavKey.Settings entry any more",
            navKey.contains("NormalNavKey.Settings.")
        )
    }

    @Test
    fun `the settings page routes java and renderer to the drawers that already exist`() {
        // 这两类不能另写一份界面：Java 走 OxideJavaDrawer，渲染器与图形走
        // OxideRendererDrawer，且 Graphics 必须落在图形那一页标签上
        val page = locate("ui/screens/main/oxide/OxideSettingsPage.kt").readText()
        assertTrue(
            "the Java category must go to the Java drawer",
            page.contains("OxideSettingsCategory.Java -> OxideSettingsDrawer.Java")
        )
        assertTrue(
            "Renderer and Graphics share the renderer drawer",
            page.contains("OxideSettingsCategory.Renderer, OxideSettingsCategory.Graphics -> OxideSettingsDrawer.Renderer")
        )
        assertTrue(
            "the drawer must actually be rendered with the requested tab",
            page.contains("initialTab = openDrawer.initialTab")
        )
    }

    @Test
    fun `the gamepad binding tabs line up with the order the drawer renders them`() {
        assertEquals(0, OxideGamepadBindingTabs.IN_GAME)
        assertEquals(1, OxideGamepadBindingTabs.IN_MENU)
        val inGame = panelSource.indexOf("settings_gamepad_mapping_in_game")
        val inMenu = panelSource.indexOf("settings_gamepad_mapping_in_menu")
        assertTrue("the in-game tab must be rendered at all", inGame >= 0)
        assertTrue(
            "the drawer must render the in-game tab first and the in-menu tab second",
            inGame < inMenu
        )
    }

    // -----------------------------------------------------------------------
    // 定位源码
    // -----------------------------------------------------------------------

    private companion object {
        const val PANEL = "OxideControlsPanel.kt"

        val panelSource: String by lazy { locate("ui/screens/main/oxide/$PANEL").readText() }

        /**
         * 八个指针热点：设置单元 → 预览用的指针形状
         *
         * 形状名字写在这里而不是读 [dev.oxide.launcher.bridge.CursorShape]：
         * 那个枚举在静态初始化时会去碰 `android.view.PointerIcon`，
         * 纯 JVM 单元测试里读它的 entries 会炸。这里比的是名字，
         * 面板源码里同样是以 `CursorShape.X` 的形式出现，两边因此对得上。
         */
        val HOTSPOT_UNITS = linkedMapOf(
            "arrowMouseHotspot" to "Arrow",
            "linkMouseHotspot" to "Hand",
            "iBeamMouseHotspot" to "IBeam",
            "crossHairMouseHotspot" to "CrossHair",
            "resizeNSMouseHotspot" to "ResizeNS",
            "resizeEWMouseHotspot" to "ResizeEW",
            "resizeAllMouseHotspot" to "ResizeAll",
            "notAllowedMouseHotspot" to "NotAllowed",
        )

        /**
         * 从当前工作目录往上找源文件
         *
         * 单元测试的工作目录不一定是模块根目录，所以逐级上溯，
         * 找不到就直接报错——绝不能悄悄跳过，那样的测试等于没有。
         */
        fun locate(relativePath: String): File {
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
}
