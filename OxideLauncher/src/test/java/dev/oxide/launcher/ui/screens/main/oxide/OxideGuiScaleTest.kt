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

package dev.oxide.launcher.ui.screens.main.oxide

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.oxide.launcher.ui.theme.Oxide
import dev.oxide.launcher.ui.theme.oxideScaledTextStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 界面缩放（`launcherGuiScale`）的单测
 *
 * 要钉住四件事：
 *  1. 100% 是**恒等**：下面第一条把今天（缩放功能存在之前）的每一个尺寸都抄了一遍，
 *     所以这条测试等于一张"我没有动过参考稿版面"的收据；
 *  2. 任何非 100% 的档位下，几何与字号乘的是**同一个**系数，比例关系不变；
 *  3. 放大时列数自己退让，而不是把卡片裁掉——所以极端档位也测了；
 *  4. 放大到极限时侧栏仍然装得下，这是唯一会真的溢出屏幕的地方。
 *
 * 全部是纯函数：既不组合也不读设置存储，因此不需要 Robolectric。
 * 接线（谁读那个设置）用读源码的方式钉住，理由见
 * [bothHalvesOfTheScaleReadTheSameSetting]。
 */
class OxideGuiScaleTest {

    /**
     * 100% 必须等于缩放功能存在之前的每一个值
     *
     * 期望值全部来自 `OxideShellGeometryTest` 里那份参考稿直译表——只要这里过，
     * 就证明 [oxideMetricsFor] 的默认档没有改动过既有版面。
     */
    @Test
    fun oneHundredPercentIsExactlyTheUnscaledLayout() {
        val compact = oxideMetricsFor(640, 360)
        assertDp(145f, compact.sidebarWidth)
        assertDp(12f, compact.sidebarPaddingH)
        assertDp(34f, compact.brandGap)
        assertDp(21f, compact.pagePaddingH)
        assertDp(46f, compact.topBarHeight)
        assertDp(38f, compact.navItemHeight)
        assertDp(41f, compact.navStep)
        assertDp(220f, compact.cardMinWidth)
        assertDp(112f, compact.brandSlotWidth)
        assertEquals(2, compact.maxCardColumns)
        assertEquals(2, compact.gridColumns(453.dp))
        assertEquals(1f, compact.guiScale, 0.0001f)
        assertEquals(29f, compact.heroTitleDp, 0.0001f)

        val expanded = oxideMetricsFor(1280, 760)
        assertDp(184f, expanded.sidebarWidth)
        assertDp(15f, expanded.sidebarPaddingH)
        assertDp(27f, expanded.pagePaddingH)
        assertDp(153f, expanded.sidebarNavWidth)
        assertDp(285f, expanded.cardMinWidth)
        assertDp(164f, expanded.navTravel)
        assertEquals(3, expanded.gridColumns(1042.dp))
        assertDp(520f, expanded.drawerWidth)

        // 默认参数就是 100%，不传与显式传必须完全一致
        assertEquals(oxideMetricsFor(1280, 760), oxideMetricsFor(1280, 760, 100))
        assertEquals(
            oxideMetricsFor(1000, 700),
            oxideMetricsFor(1000, 700, OxideGuiScaleDefaultPercent),
        )
    }

    // ---- 系数 ---------------------------------------------------------------

    /** 百分比 → 系数；越界夹回区间，所以调用方不必自己判断 */
    @Test
    fun factorIsThePercentageAndClampsToTheDeclaredRange() {
        assertEquals(0.75f, oxideGuiScaleFactor(75), 0.0001f)
        assertEquals(1f, oxideGuiScaleFactor(100), 0.0001f)
        assertEquals(1.25f, oxideGuiScaleFactor(125), 0.0001f)
        assertEquals(1.5f, oxideGuiScaleFactor(150), 0.0001f)
        // 越界值夹回两端，不会算出 0 或者 2 倍这种把版面毁掉的系数
        assertEquals(0.75f, oxideGuiScaleFactor(0), 0.0001f)
        assertEquals(0.75f, oxideGuiScaleFactor(-40), 0.0001f)
        assertEquals(1.5f, oxideGuiScaleFactor(400), 0.0001f)
        assertEquals(1f, oxideGuiScaleFactor(100), 0.0001f)
    }

    /** 下拉里的每一档都真的落在设置声明的区间内，而且 100% 在中间 */
    @Test
    fun theOfferedStepsAreInsideTheDeclaredRangeAndCentredOnOneHundred() {
        assertEquals(listOf(75, 100, 125, 150), OxideGuiScaleSteps)
        for (percent in OxideGuiScaleSteps) {
            assertTrue(
                "percent=$percent is outside the setting's range",
                percent in OxideGuiScaleMinPercent..OxideGuiScaleMaxPercent,
            )
        }
        assertEquals(100, OxideGuiScaleDefaultPercent)
        // 上下限就是两端的档位，范围和下拉不会各说各的
        assertEquals(OxideGuiScaleMinPercent, OxideGuiScaleSteps.first())
        assertEquals(OxideGuiScaleMaxPercent, OxideGuiScaleSteps.last())
    }

    // ---- 缩放本身 -----------------------------------------------------------

    /** 缩小：每一个 dp 都乘 0.75 */
    @Test
    fun everythingShrinksBelowOneHundredPercent() {
        val base = oxideMetricsFor(1280, 760, 100)
        val small = oxideMetricsFor(1280, 760, 75)

        assertScaledBy(0.75f, base, small)
        // heroTitleDp 是排版数值而不是 dp，走浮点比较而不是 assertDp
        assertEquals("heroTitleDp", base.heroTitleDp * 0.75f, small.heroTitleDp, 0.001f)
        // 宽度档本身不变：缩放不该顺手把版面推去另一档断点
        assertEquals(base.widthClass, small.widthClass)
        assertEquals(0.75f, small.guiScale, 0.0001f)
    }

    /** 放大：125% 与 150% 都乘对应的系数 */
    @Test
    fun everythingGrowsAboveOneHundredPercent() {
        val base = oxideMetricsFor(1280, 760, 100)
        for (percent in listOf(125, 150)) {
            val factor = percent / 100f
            val big = oxideMetricsFor(1280, 760, percent)
            assertScaledBy(factor, base, big)
            assertEquals("heroTitleDp", base.heroTitleDp * factor, big.heroTitleDp, 0.001f)
            assertEquals(factor, big.guiScale, 0.0001f)
        }
    }

    /**
     * 放大后侧栏仍然装得下：这是 150% 唯一会真的出问题的地方
     *
     * 侧栏的上下内边距、品牌槽与落款都是固定值，所以放大的导航列如果直接乘 1.5
     * 就会把底部块挤出屏幕。这里逐档核对"装得下"这条不等式——它在 150% 的窄屏上是
     * **靠导航项让出空间**才成立的，而不是靠改那几项固定值。
     */
    @Test
    fun theSidebarStillFitsAtEveryStep() {
        for (percent in OxideGuiScaleSteps) {
            for (width in intArrayOf(480, 640, 800, 901, 1000, 1280, 2560)) {
                for (height in intArrayOf(300, 320, 340, 360, 480, 700, 760, 1600)) {
                    val m = oxideMetricsFor(width, height, percent)
                    val needed = Oxide.SidebarPaddingTop + Oxide.BrandSlotHeight + m.brandGap +
                        m.navTravel + Oxide.SidebarFooterPaddingTop + Oxide.SidebarBorder +
                        OxideSidebarFooterLineHeight * OxideSidebarFooterLines + Oxide.SidebarPaddingBottom
                    assertTrue(
                        "percent=$percent width=$width height=$height needed=${needed.value}dp",
                        needed.value <= height + 0.01f,
                    )
                    // 让出空间的代价是导航项变矮，但绝不能矮过绝对下限
                    assertTrue(
                        "percent=$percent width=$width height=$height item=${m.navItemHeight}",
                        m.navItemHeight >= OxideMinNavItemHeight,
                    )
                    // 也必须仍然装得下放大后的导航文字，否则那一项自己会溢出
                    assertTrue(
                        "percent=$percent width=$width height=$height " +
                            "item=${m.navItemHeight} navText=${m.navTextLineHeight}",
                        m.navTextLineHeight <= m.navItemHeight,
                    )
                }
            }
        }
    }

    /** 100% 时导航项高度与缩放功能存在之前逐档一致 */
    @Test
    fun navItemHeightMatchesTheReferenceBandsAtOneHundredPercent() {
        // oxideNavItemHeightFor 只吃高度与宽度档（宽度已折进宽度档），不再收宽度
        assertDp(38f, oxideNavItemHeightFor(760, OxideWidthClass.Expanded))
        assertDp(38f, oxideNavItemHeightFor(340, OxideWidthClass.Expanded))
        assertDp(38f, oxideNavItemHeightFor(OxideShortScreenHeight, OxideWidthClass.Compact))
        // 矮屏档仍然是 32dp
        assertDp(32f, oxideNavItemHeightFor(300, OxideWidthClass.Compact))
        // 100% 时导航文字行高就是参考稿的 14sp
        assertDp(14f, oxideNavTextLineHeight(oxideGuiScaleFactor(100)))
    }

    /**
     * 导航项高度永远不小于它自己那行文字
     *
     * 这是"150% 不裁切"的另一条腿：让出空间可以矮，但矮到装不下文字时那一项自己就
     * 会溢出。极端组合（150% + 300dp 高的横屏）也必须成立。
     */
    @Test
    fun navItemsAlwaysFitTheirOwnLabel() {
        for (percent in OxideGuiScaleSteps) {
            val text = oxideNavTextLineHeight(oxideGuiScaleFactor(percent))
            for (widthClass in OxideWidthClass.entries) {
                for (height in intArrayOf(240, 260, 280, 300, 340, 480, 760)) {
                    val item = oxideNavItemHeightFor(height, widthClass, percent)
                    assertTrue(
                        "percent=$percent $widthClass height=$height " +
                            "item=${item.value}dp text=${text.value}dp",
                        item.value >= text.value - 0.01f,
                    )
                }
            }
        }
    }

    /** 每一档宽度、每一个可用高度下，缩放都不能产出非法尺寸 */
    @Test
    fun everyBandAndHeightStaysSaneAtEveryStep() {
        for (percent in OxideGuiScaleSteps) {
            for (width in intArrayOf(480, 640, 800, 901, 1120, 1121, 1280, 1281, 2560)) {
                for (height in intArrayOf(300, 340, 360, 700, 760, 1600)) {
                    val m = oxideMetricsFor(width, height, percent)
                    val where = "percent=$percent width=$width height=$height"
                    assertTrue("$where sidebar=${m.sidebarWidth}", m.sidebarWidth > 0.dp)
                    assertTrue("$where navWidth=${m.sidebarNavWidth}", m.sidebarNavWidth > 0.dp)
                    assertTrue("$where brandSlot=${m.brandSlotWidth}", m.brandSlotWidth > 0.dp)
                    assertTrue("$where navStep=${m.navStep}", m.navStep > 0.dp)
                    assertTrue("$where cardMin=${m.cardMinWidth}", m.cardMinWidth > 0.dp)
                    assertTrue("$where topBar=${m.topBarHeight}", m.topBarHeight > 0.dp)
                    assertTrue("$where drawer=${m.drawerWidth}", m.drawerWidth > 0.dp)
                    // 抽屉不能宽过它所在的那块屏幕：放大到极限也只占三分之二
                    assertTrue(
                        "$where drawer=${m.drawerWidth} width=$width",
                        m.drawerWidth <= (width * OxideDrawerMaxWidthFraction).dp + 0.01.dp,
                    )
                    assertTrue("$where minColumns", m.minCardColumns >= 1)
                    assertTrue(
                        "$where logo=${m.brandLogoScale}",
                        m.brandLogoScale >= Oxide.Motion.IntroScaleMin &&
                            m.brandLogoScale <= Oxide.Motion.IntroScaleMax,
                    )
                }
            }
        }
    }

    // ---- 列数 ---------------------------------------------------------------

    /**
     * 放大时列数自己退让，而不是把卡片裁掉
     *
     * 1280dp 上 100% 是三列；放大之后侧栏与留白一起变大，内容区反而更窄，
     * 而卡片最小宽度也变大了，于是同样的宽度装不下三列。断言的是"列数变少且
     * 每列仍然放得下"，这正是"不裁切"的定义。
     */
    @Test
    fun columnsDropInsteadOfClippingWhenTheUiIsEnlarged() {
        val width = 1280
        val normal = oxideMetricsFor(width, 760, 100)
        val contentWidthAt = { m: OxideMetrics ->
            width.dp - m.sidebarWidth - m.pagePaddingH * 2
        }

        val normalColumns = normal.gridColumns(contentWidthAt(normal))
        assertEquals(3, normalColumns)

        val enlarged = oxideMetricsFor(width, 760, 150)
        val enlargedColumns = enlarged.gridColumns(contentWidthAt(enlarged))
        assertTrue(
            "150% must not keep ${normalColumns} columns, was $enlargedColumns",
            enlargedColumns < normalColumns,
        )
        assertTrue("columns=$enlargedColumns", enlargedColumns >= enlarged.minCardColumns)

        // 每一列都真的塞得下卡片最小宽度 + 间距，否则就是裁切而不是退让
        val columnWidth = (contentWidthAt(enlarged) - enlarged.cardGap * (enlargedColumns - 1)) /
            enlargedColumns
        assertTrue(
            "column=${columnWidth}dp cannot hold cardMin=${enlarged.cardMinWidth}dp",
            columnWidth >= enlarged.cardMinWidth,
        )
    }

    /** 小横屏上放大：列数掉到一列，而不是三列被压扁或裁掉 */
    @Test
    fun aSmallLandscapeScreenLosesColumnsBeforeItLosesContent() {
        val width = 800
        val normal = oxideMetricsFor(width, 480, 100)
        val contentWidthAt = { m: OxideMetrics -> width.dp - m.sidebarWidth - m.pagePaddingH * 2 }

        assertEquals(2, normal.gridColumns(contentWidthAt(normal)))

        val enlarged = oxideMetricsFor(width, 480, 150)
        val columns = enlarged.gridColumns(contentWidthAt(enlarged))
        assertTrue("columns=$columns", columns < 2)

        // 放大到极限也不能把内容挤成 0 列
        assertTrue("columns=$columns", columns >= 1)

        // 最小的一块屏幕放到最大：仍然是一列，绝不出现 0 列
        val tiniestWidth = 480
        val tiniest = oxideMetricsFor(tiniestWidth, 300, 150)
        assertEquals(
            1,
            tiniest.gridColumns(
                tiniestWidth.dp - tiniest.sidebarWidth - tiniest.pagePaddingH * 2,
            ),
        )
    }

    /** 缩小不会凭空多出列：列数上限仍由宽度档决定 */
    @Test
    fun shrinkingDoesNotInventExtraColumns() {
        val small = oxideMetricsFor(1280, 760, 75)
        val contentWidth = 1280.dp - small.sidebarWidth - small.pagePaddingH * 2
        assertTrue(
            "columns=${small.gridColumns(contentWidth)}",
            small.gridColumns(contentWidth) <= small.maxCardColumns,
        )
        assertEquals(3, small.maxCardColumns)
    }

    // ---- 字号 ---------------------------------------------------------------

    /**
     * 两半接线都指着同一个设置
     *
     * 前面几条只验证了纯函数；这一条验证接线本身：几何（[oxideMetricsFor]）与字号
     * （`Oxide.Type`）必须都经由 [oxideGuiScaleFactor] 读 `AllSettings.launcherGuiScale.state`，
     * 而不是各自去读别的地方——那正是"控件在、但不生效"那个 bug 的形状。
     *
     * 这里读源码而不是真的改设置：`AllSettings` 的对象初始化会牵起整个设置注册表
     * （含 MMKV 与 Android 框架），在纯 JVM 单测里读它既没有意义也不稳。
     */
    @Test
    fun bothHalvesOfTheScaleReadTheSameSetting() {
        val metrics = locate("ui/screens/main/oxide/OxideMetrics.kt").readText()
        val theme = locate("ui/theme/Oxide.kt").readText()

        for ((name, source) in listOf("OxideMetrics.kt" to metrics, "Oxide.kt" to theme)) {
            assertTrue(
                "$name must read AllSettings.launcherGuiScale.state",
                source.contains("AllSettings.launcherGuiScale.state"),
            )
            assertTrue(
                "$name must scale through oxideGuiScaleFactor",
                source.contains("oxideGuiScaleFactor("),
            )
        }
        // 几何那一半把设置值交给 rememberOxideMetrics，因此改设置立刻重算
        assertTrue(
            "rememberOxideMetrics must key its memo on the setting",
            metrics.contains("configuration.screenHeightDp, guiScalePercent"),
        )
    }

    /**
     * 字号坡道乘的是同一个系数
     *
     * 逐个样式核对字号、行高、字距都乘了 [factor]，并且字号之间的**比例**保持不变——
     * 放大不会把某一个字号单独撑开，缩小也不会把层级差压没。
     */
    @Test
    fun theTypeRampScalesByTheSameFactorAsTheLayout() {
        for (percent in OxideGuiScaleSteps) {
            val factor = oxideGuiScaleFactor(percent)
            for (base in BASE_RAMP) {
                val scaled = oxideScaledTextStyle(base, factor)
                val where = "percent=$percent style=$base"
                assertEquals(
                    "$where fontSize",
                    base.fontSize.value * factor,
                    scaled.fontSize.value,
                    0.001f,
                )
                assertEquals(
                    "$where lineHeight",
                    base.lineHeight.value * factor,
                    scaled.lineHeight.value,
                    0.001f,
                )
                assertEquals(
                    "$where letterSpacing",
                    base.letterSpacing.value * factor,
                    scaled.letterSpacing.value,
                    0.001f,
                )
                // 层级关系不变：行高与字号的比值、字距与字号的比值都原样保留
                assertEquals(
                    "$where lineHeight ratio",
                    base.lineHeight.value / base.fontSize.value,
                    scaled.lineHeight.value / scaled.fontSize.value,
                    0.0001f,
                )
                assertEquals(
                    "$where letterSpacing ratio",
                    base.letterSpacing.value / base.fontSize.value,
                    scaled.letterSpacing.value / scaled.fontSize.value,
                    0.0001f,
                )
            }
        }
    }

    /** 100% 时缩放是恒等：字距为 0 或负值时也不能凭空变出偏移 */
    @Test
    fun scalingIsIdentityAtOneHundredPercent() {
        for (base in BASE_RAMP) {
            val scaled = oxideScaledTextStyle(base, oxideGuiScaleFactor(100))
            assertEquals("fontSize of $base", base.fontSize.value, scaled.fontSize.value, 0.0001f)
            assertEquals("lineHeight of $base", base.lineHeight.value, scaled.lineHeight.value, 0.0001f)
            assertEquals(
                "letterSpacing of $base",
                base.letterSpacing.value,
                scaled.letterSpacing.value,
                0.0001f,
            )
        }
        // 负字距（参考稿的大标题是 -1.4px）在放大后必须仍是负的，
        // 否则紧凑的负字距会变成字距，整条坡道的性格就变了
        val tight = oxideScaledTextStyle(BASE_RAMP.last(), 1.5f)
        assertTrue("letterSpacing=${tight.letterSpacing}", tight.letterSpacing.value < 0f)
        // 缩小到 75% 时负字距也不能变成 0——那会让标题散开
        assertTrue(oxideScaledTextStyle(BASE_RAMP.last(), 0.75f).letterSpacing.value < 0f)
    }

    /** 几何与字号用的是同一个系数，而不是各乘各的 */
    @Test
    fun metricsAndTypeAgreeOnTheFactor() {
        for (percent in OxideGuiScaleSteps) {
            val m = oxideMetricsFor(1280, 760, percent)
            assertEquals(
                "percent=$percent",
                oxideGuiScaleFactor(percent),
                m.guiScale,
                0.0001f,
            )
            // heroTitle 是排版而不是 dp，用它验证两边确实同源
            assertEquals(
                "percent=$percent",
                36f * m.guiScale,
                m.heroTitleDp,
                0.001f,
            )
        }
    }

    /** `scaled()` 乘的是宽度档的 density，不是用户的界面缩放 */
    @Test
    fun theDensityHelperDoesNotDoubleApplyTheGuiScale() {
        // Oxide.Type 的字号已经带上了用户缩放，若 scaled() 再乘一次就会叠成平方
        val big = oxideMetricsFor(1280, 760, 150)
        assertEquals(1f, big.density, 0.0001f)
        val bodyAtBigScale = oxideScaledTextStyle(style(8f, 12f, 0f), big.guiScale)
        assertEquals(
            "scaled() must not re-apply guiScale",
            bodyAtBigScale.fontSize.value,
            big.scaled(bodyAtBigScale.fontSize.value),
            0.0001f,
        )
    }

    // ---- 工具 ---------------------------------------------------------------

    private fun assertDp(expected: Float, actual: Dp) {
        assertEquals("expected ${expected}dp but was ${actual.value}dp", expected, actual.value, 0.01f)
    }

    /**
     * 逐条核对"每一个跟着界面缩放走的尺寸都乘了同一个系数"
     *
     * 尺寸清单只在这里列一次，所以新加一个尺寸时缩小/放大两条测试都会自动带上它；
     * 分支侧也只改那一处，漏乘的档位立刻会被这两条测试抓住。
     */
    private fun assertScaledBy(factor: Float, base: OxideMetrics, scaled: OxideMetrics) {
        val actual = scaled.scalableDpValues()
        for ((name, baseValue) in base.scalableDpValues()) {
            assertEquals(
                "$name must be ${baseValue}dp * $factor",
                baseValue * factor,
                actual.getValue(name),
                0.01f,
            )
        }
    }

    /**
     * 跟着界面缩放一起乘系数的那些尺寸
     *
     * 不含 [OxideMetrics.widthClass]、[OxideMetrics.density] 与列数上下限：这三个
     * 量的是**屏幕**，缩放不该动它们。不含 `heroTitleDp`：它是排版数值而不是 dp。
     */
    private fun OxideMetrics.scalableDpValues(): Map<String, Float> = mapOf(
        "sidebarWidth" to sidebarWidth.value,
        "sidebarPaddingH" to sidebarPaddingH.value,
        "brandGap" to brandGap.value,
        "pagePaddingH" to pagePaddingH.value,
        "pagePaddingV" to pagePaddingV.value,
        "sectionGap" to sectionGap.value,
        "cardGap" to cardGap.value,
        "cardMinWidth" to cardMinWidth.value,
        "drawerWidth" to drawerWidth.value,
        "topBarHeight" to topBarHeight.value,
        "navItemHeight" to navItemHeight.value,
        "navStep" to navStep.value,
        "navTravel" to navTravel.value,
        "sidebarNavWidth" to sidebarNavWidth.value,
        "brandSlotWidth" to brandSlotWidth.value,
    )

    /**
     * 从当前工作目录往上找源文件
     *
     * 单测的工作目录不一定是模块根目录，所以逐级上溯；找不到就直接报错，
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

    private companion object {
        /**
         * 参考稿的字号坡道原样抄在这里
         *
         * 单独抄一份而不是读 `Oxide.Type`：那些 getter 已经乘过用户的缩放，
         * 拿它们当基准会把系数算两次。这里是**未缩放**的基准，所以比例关系可核对。
         */
        private fun style(fontSize: Float, lineHeight: Float, letterSpacing: Float) = TextStyle(
            fontSize = fontSize.sp,
            lineHeight = lineHeight.sp,
            letterSpacing = letterSpacing.sp,
        )

        /** MicroLabel / Label / Mono / Body / Title / Nav / DrawerTitle / PageTitle */
        val BASE_RAMP = listOf(
            style(6f, 9f, 0.7f),
            style(7f, 10f, 1f),
            style(7f, 12f, 0f),
            style(8f, 12f, 0f),
            style(9f, 13f, 0f),
            style(10f, 14f, 0.05f),
            style(16f, 20f, -0.8f),
            style(22f, 22f, -1.4f),
        )
    }
}