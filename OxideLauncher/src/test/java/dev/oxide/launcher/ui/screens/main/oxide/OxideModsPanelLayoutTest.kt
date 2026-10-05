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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 内容管理器（模组 / 资源包 / 光影 / 存档 / 截图）的版面
 *
 * v1.7.0 设备截图上那一条用户反馈是：菜单太小，选中任何一个之后就没有空间再选第二个。
 * 三个成因分别对应下面几组断言：
 *
 *  1. **面板太小**。[oxideSubWindowPanelBounds] 给这一类面板用的是更宽更高的一档比例
 *     （[OxideSubWindowWideWidthFraction] / [OxideSubWindowWideHeightFraction]），
 *     并且面板**占满**它的高度上限——不占满的话，内部那个 `weight(1f)` 量到的是 0。
 *  2. **选中之后列表变矮**。批量动作此前是压在列表上面的一整行（模组是两整行）；
 *     现在它们横过来住进列表右侧的窄栏，于是 `selected` 只影响 `railWidth`。
 *  3. **上面那一块太厚**。模组的 chrome 见 [oxideModsChromeHeight]，其余几类见
 *     [oxideContentChromeHeight]；两处都与"有没有选中"无关。
 *
 * 另外两条是**边界**而不是布局偏好，因此钉得更死：
 *
 *  - 外壳必须给高度上限，而不是包住内容：[oxideSubWindowPanelBounds] 的高度永远为正
 *    （正无穷的 maxHeight 就是 v1.5.0 那个 P0 崩溃的成因），也永远不超过可用尺寸。
 *  - 别的子窗口（账号浏览器、许可面板）不传新参数时拿到的仍然是旧的两档默认比例。
 *
 * 最后一组是**结构**而不是数值：同一块面板里同一个方向上只能有一个纵向滚动容器。
 * 两个纵向滚动容器会在测量时抛异常，因此这件事只能读源码断言，办法与
 * [OxideContentSurfaceGuardTest] 相同：读去掉注释之后的源码，并且按**函数**切块，
 * 这样对话框里的滚动容器不会被算到面板头上。
 *
 * 这里没有 Compose，也没有 Android：全部断言都打在纯函数上，
 * 因此它们能在没有 SDK 的机器上跑。
 */
class OxideModsPanelLayoutTest {

    /** 三种可用高度：最小横屏、一个常见横屏、以及平板那一档 */
    private val heights = listOf(360.dp, 450.dp, 586.dp)

    /** 两种界面缩放；放大之后每个 dp 都跟着长，因此"还剩几行"必须重新算而不是沿用 */
    private val guiScales = listOf(100, 125)

    /**
     * 可用尺寸与 [oxideMetricsFor] 必须成对
     *
     * 面板拿到的是页面剩下的高度，而页面的尺寸也由同一个高度推导，因此两者必须
     * 同源：拿 640dp 的 metrics 去算一块 360dp 高的面板，会得到一个永远不会发生的数。
     */
    private fun metricsAt(availableHeight: Dp, guiScalePercent: Int) = oxideMetricsFor(
        widthDp = 1280,
        heightDp = availableHeight.value.toInt(),
        guiScalePercent = guiScalePercent,
    )

    private fun modsLayoutAt(
        availableHeight: Dp,
        guiScalePercent: Int,
        selected: Boolean,
        instanceFacts: Boolean = false,
    ) = oxideModsPanelLayout(
        availableWidth = 1280.dp,
        availableHeight = availableHeight,
        metrics = metricsAt(availableHeight, guiScalePercent),
        selected = selected,
        instanceFacts = instanceFacts,
    )

    // ---- 面板比别的子窗口大 -------------------------------------------------

    @Test
    fun theContentManagerPanelGetsTheWiderAndTallerFraction() {
        // 只有内容管理器这一类是"读一张列表"的。账号浏览器与许可面板读几句话，
        // 0.82 / 0.86 正好，因此新比例只在这些调用点上显式给出
        assertEquals(0.94f, OxideSubWindowWideWidthFraction, 0f)
        assertEquals(0.96f, OxideSubWindowWideHeightFraction, 0f)
        assertTrue(
            "the wide fractions must be roomier than the default ones",
            OxideSubWindowWideWidthFraction > OxideSubWindowWidthFraction &&
                OxideSubWindowWideHeightFraction > OxideSubWindowHeightFraction,
        )
    }

    @Test
    fun everyOtherSubWindowStillGetsTheOldDefaultFractions() {
        // 不传 widthFraction / heightFraction 的调用方必须一字不差地拿到旧的两档：
        // `oxideSubWindowPanelBounds` 的默认参数就是它们
        for (height in heights) {
            for (panelMax in listOf(480.dp, 620.dp, 860.dp)) {
                val plain = oxideSubWindowPanelBounds(
                    availableWidth = 1280.dp,
                    availableHeight = height,
                    maxPanelWidth = panelMax,
                )
                val explicitDefaults = oxideSubWindowPanelBounds(
                    availableWidth = 1280.dp,
                    availableHeight = height,
                    maxPanelWidth = panelMax,
                    widthFraction = OxideSubWindowWidthFraction,
                    heightFraction = OxideSubWindowHeightFraction,
                )
                assertEquals(plain, explicitDefaults)
            }
        }
        assertEquals(0.82f, OxideSubWindowWidthFraction, 0f)
        assertEquals(0.86f, OxideSubWindowHeightFraction, 0f)
    }

    @Test
    fun theContentManagerPanelIsTallerThanADefaultSubWindowAtEveryHeight() {
        for (height in heights) {
            val mods = modsLayoutAt(height, 100, selected = false)
            val plain = oxideSubWindowPanelBounds(
                availableWidth = 1280.dp,
                availableHeight = height,
                maxPanelWidth = OxideModsPanelMaxWidth,
            )
            assertTrue(
                "the content manager panel must be taller than a default sub-window at $height, " +
                    "got ${mods.panelHeight} against ${plain.height}",
                mods.panelHeight > plain.height,
            )
            // 宽度上限也只在这一块放宽，其余子窗口仍是 620dp
            assertEquals(
                OxideModsPanelMaxWidth,
                oxideSubWindowPanelBounds(
                    availableWidth = 2400.dp,
                    availableHeight = height,
                    maxPanelWidth = OxideModsPanelMaxWidth,
                ).width,
            )
            assertTrue(
                "the panel must be wider than the 620dp default cap, got ${mods.panelWidth}",
                mods.panelWidth > 620.dp,
            )
        }
    }

    // ---- 列表看得到几行 -----------------------------------------------------

    @Test
    fun atTheDefaultUiScaleTheListShowsAtLeastThreeRowsWithAndWithoutASelection() {
        // 360dp 是要支持的最小横屏，那里必须还看得到三条模组——"看得到"是那条
        // 用户反馈的底线，它比"看得到四条"更该被钉住
        for (height in heights) {
            for (selected in listOf(false, true)) {
                val layout = modsLayoutAt(height, 100, selected = selected)
                assertTrue(
                    "height=$height selected=$selected: only ${layout.visibleRows} rows fit " +
                        "(chrome ${layout.chromeHeight}, panel ${layout.panelHeight}, row ${layout.rowHeight})",
                    layout.visibleRows >= 3,
                )
            }
        }
    }

    @Test
    fun at125PercentTheTallerLandscapesShowThreeRowsAndTheShortestFitsTwo() {
        // 放大之后 chrome 与行高一起长 25%，因此最短的那一档只能装下两行。
        // 这个数是照实钉住的，而不是被抬高的目标：哪一天它变成 3，
        // 这个测试就会先炸，那时再决定是"少了"还是"够了"
        val expected = mapOf(
            360.dp to 2,
            450.dp to 4,
            586.dp to 6,
        )
        for (height in heights) {
            for (selected in listOf(false, true)) {
                val layout = modsLayoutAt(height, 125, selected = selected)
                assertEquals(
                    "height=$height selected=$selected: ${layout.visibleRows} rows",
                    expected.getValue(height),
                    layout.visibleRows,
                )
            }
        }
        assertTrue(
            "the taller landscapes must still show three rows at 125%",
            listOf(450.dp, 586.dp).all {
                modsLayoutAt(it, 125, selected = true).visibleRows >= 3
            },
        )
    }

    @Test
    fun theInstanceFactsLineCostsAtMostTwoMicroLabels() {
        // 实例那一行加载器最多两行小标签，因此最坏情况也只多掉两行小标签，
        // 而不是再吃掉一行按钮
        for (height in heights) {
            for (scale in guiScales) {
                val without = modsLayoutAt(height, scale, selected = false, instanceFacts = false)
                val with = modsLayoutAt(height, scale, selected = false, instanceFacts = true)
                assertTrue(
                    "the instance facts must not shrink the chrome",
                    with.chromeHeight > without.chromeHeight,
                )
                assertTrue(
                    "the instance facts must never cost more than two micro-label lines, " +
                        "got ${with.chromeHeight - without.chromeHeight} at height=$height scale=$scale%",
                    with.chromeHeight - without.chromeHeight <= without.rowHeight,
                )
            }
        }
    }

    // ---- 本次修的那条回归 ---------------------------------------------------

    @Test
    fun selectingNeverChangesTheListHeight() {
        // 这正是用户报的那条：勾选第一个之后就没有地方选第二个。
        // 根因是批量条从纵向流里拿走了约两行，因此这几个数必须与 selected 无关
        for (height in heights) {
            for (scale in guiScales) {
                for (facts in listOf(false, true)) {
                    val idle = modsLayoutAt(height, scale, selected = false, instanceFacts = facts)
                    val picked = modsLayoutAt(height, scale, selected = true, instanceFacts = facts)
                    val where = "height=$height guiScale=$scale% instanceFacts=$facts"
                    assertEquals("$where: chrome must not move", idle.chromeHeight, picked.chromeHeight)
                    assertEquals("$where: the list must not shrink", idle.listHeight, picked.listHeight)
                    assertEquals("$where: the row height must not move", idle.rowHeight, picked.rowHeight)
                    assertEquals("$where: the row count must not drop", idle.visibleRows, picked.visibleRows)
                }
            }
        }
    }

    // ---- 窄栏 ---------------------------------------------------------------

    @Test
    fun theRailIsDrawnOnlyWhenSomethingIsSelected() {
        for (height in heights) {
            for (scale in guiScales) {
                assertEquals(
                    "height=$height guiScale=$scale%: no selection means no rail at all",
                    0.dp,
                    modsLayoutAt(height, scale, selected = false).railWidth,
                )
                assertTrue(
                    "height=$height guiScale=$scale%: a selection must draw the rail, " +
                        "got ${modsLayoutAt(height, scale, selected = true).railWidth}",
                    modsLayoutAt(height, scale, selected = true).railWidth > 0.dp,
                )
            }
        }
    }

    @Test
    fun theRailWidthComesFromAnExistingMetricRatherThanAMagicNumber() {
        // 模组那一栏与左边那条分类列是同一份推导（155 起步、夹在 132..260dp 之间）：
        // 放大跟着长，缩到最小档也不会塌成一个装不下文案的空条
        for (scale in guiScales) {
            val metrics = metricsAt(640.dp, scale)
            assertEquals(
                "the mods rail must be exactly the category column width",
                metrics.contentRailWidth(),
                oxideModsRailWidth(metrics),
            )
            assertEquals(
                metrics.contentRailWidth(),
                modsLayoutAt(640.dp, scale, selected = true).railWidth,
            )
            assertTrue(
                "guiScale=$scale%: the rail must stay wide enough for a real label",
                oxideModsRailWidth(metrics) >= 132.dp,
            )
            assertTrue(
                "guiScale=$scale%: the rail must stay bounded",
                oxideModsRailWidth(metrics) <= 260.dp,
            )
        }
        assertTrue(
            "a larger ui scale must not make the rail narrower",
            oxideModsRailWidth(metricsAt(640.dp, 125)) > oxideModsRailWidth(metricsAt(640.dp, 100)),
        )
    }

    @Test
    fun theRailStaysInsideThePanelBesideTheList() {
        // 窄栏与列表并排，因此它加上中间那段间距必须窄于面板本身，
        // 否则列表会被压到只剩一个图标
        for (height in heights) {
            for (scale in guiScales) {
                val metrics = metricsAt(height, scale)
                val layout = modsLayoutAt(height, scale, selected = true)
                assertTrue(
                    "height=$height guiScale=$scale%: rail ${layout.railWidth} + gap " +
                        "${metrics.secRowGap} must fit inside ${layout.panelWidth}",
                    layout.railWidth + metrics.secRowGap < layout.panelWidth,
                )
            }
        }
    }

    // ---- 上面那一块 ----------------------------------------------------------

    @Test
    fun selectAllNoLongerOccupiesAWholeRowOfItsOwn() {
        // 全选此前独占一整行 28dp 加一段间距；并进本来就有的一行之后，
        // chrome 里剩下的就是标题、搜索、筛选三块
        for (height in heights) {
            for (scale in guiScales) {
                val metrics = metricsAt(height, scale)
                val chrome = oxideModsChromeHeight(metrics, instanceFacts = false)
                assertTrue(
                    "the chrome must still hold the header, search, sort and filter rows, got $chrome",
                    chrome > metrics.secInputHeight * 2,
                )
                // chrome 至少装得下标题栏 + 内容留白 + 搜索 + 筛选 + 三段间距
                assertTrue(
                    "the chrome must at least cover the search and filter rows, got $chrome",
                    chrome > OxideSubWindowTitleBarHeight + OxideSubWindowContentPadding * 2,
                )
            }
        }
    }

    @Test
    fun theChromeScalesWithTheUiScale() {
        val at100 = oxideModsChromeHeight(metricsAt(640.dp, 100), instanceFacts = true)
        val at125 = oxideModsChromeHeight(metricsAt(640.dp, 125), instanceFacts = true)
        assertTrue("guiScale=125% must not shrink the chrome, got $at100 then $at125", at125 > at100)
    }

    // ---- 外壳的高度上限（P0 崩溃的边界）-------------------------------------

    @Test
    fun thePanelHeightCapIsAlwaysPositiveAndNeverExceedsTheWindow() {
        // 高度上限必须在滚动之前夹住：正无穷的 maxHeight 就是 v1.5.0 那个
        // 滚动容器崩溃的成因，因此这里连 0 都不允许出现
        val sizes = listOf(0.dp, 1.dp, 14.dp, 28.dp, 200.dp, 360.dp, 640.dp, 1440.dp, 4096.dp)
        for (height in sizes) {
            for (fraction in listOf(
                OxideSubWindowHeightFraction,
                OxideSubWindowWideHeightFraction,
            )) {
                val cap = oxideSubWindowPanelBounds(
                    availableWidth = 1280.dp,
                    availableHeight = height,
                    maxPanelWidth = 620.dp,
                    heightFraction = fraction,
                ).height
                assertTrue(
                    "the height cap must stay positive at availableHeight=$height " +
                        "heightFraction=$fraction, got $cap",
                    cap > 0.dp,
                )
                assertTrue(
                    "the panel must never exceed the window at availableHeight=$height, got $cap",
                    cap <= maxOf(height, 1.dp),
                )
            }
        }
    }

    @Test
    fun aReallyTinyWindowStillGetsAUsableHeightCap() {
        // 14dp 的可用高度减去两侧 28dp 留白是负数；夹到 1dp 而不是让它变成 0 或负
        assertEquals(
            1.dp,
            oxideSubWindowPanelBounds(
                availableWidth = 1280.dp,
                availableHeight = 14.dp,
                maxPanelWidth = 620.dp,
            ).height,
        )
    }

    // ---- 其余四类内容管理器 -------------------------------------------------

    @Test
    fun theContentManagersChromeIsAlsoIndependentOfTheSelection() {
        // 资源包、存档与截图这几块共用 [oxideContentPanelLayout]。选中动作现在住进
        // 控件区右侧的窄栏，因此这些分类也一样：勾选不改变列表上方那一块的高度
        for (category in OxideContentCategory.entries) {
            if (category == OxideContentCategory.Mods) continue
            for (height in heights) {
                for (scale in guiScales) {
                    val metrics = metricsAt(height, scale)
                    val importRow = category != OxideContentCategory.Screenshots
                    val idle = oxideContentPanelLayout(
                        metrics = metrics,
                        category = category,
                        hasImportRow = importRow,
                        selected = false,
                    )
                    val picked = oxideContentPanelLayout(
                        metrics = metrics,
                        category = category,
                        hasImportRow = importRow,
                        selected = true,
                    )
                    val where = "$category height=$height guiScale=$scale%"
                    assertEquals("$where: chrome must not move", idle.chromeHeight, picked.chromeHeight)
                    assertEquals(
                        "$where: the selection row must cost no vertical space",
                        0.dp,
                        picked.selectionRowHeight,
                    )
                    assertEquals("$where: no selection, no rail", 0.dp, idle.railWidth)
                    assertEquals(
                        "$where: the rail width is the category column metric",
                        metrics.contentRailWidth(),
                        picked.railWidth,
                    )
                }
            }
        }
    }

    @Test
    fun theContentManagersChromeOnlyCountsTheRowsItActuallyDraws() {
        // 截图不支持导入；模组之外那三类没有状态筛选（而模组根本不走这一块）。
        // 两者都只能省掉自己的高度，不能凭空多出一段
        for (height in heights) {
            val metrics = metricsAt(height, 100)
            val withImport = oxideContentPanelLayout(
                metrics = metrics,
                category = OxideContentCategory.Shaders,
                hasImportRow = true,
            )
            val withoutImport = oxideContentPanelLayout(
                metrics = metrics,
                category = OxideContentCategory.Screenshots,
                hasImportRow = false,
            )
            assertTrue(
                "dropping the import row must make the chrome shorter at $height",
                withoutImport.chromeHeight < withImport.chromeHeight,
            )
            assertTrue(
                "the chrome must still hold the section title and the search box at $height",
                withoutImport.chromeHeight > metrics.secInputHeight,
            )
            assertTrue(
                "the chrome must be positive at $height",
                withoutImport.chromeHeight > 0.dp,
            )
        }
    }

    // ---- 同一方向上只能有一个纵向滚动容器 -----------------------------------

    @Test
    fun theModsPanelStillDeclaresExactlyOneVerticalScroller() {
        // 外壳把纵向滚动关掉，纵向空间全部交给那一个 LazyColumn。
        // 按函数切块去看，因此两个对话框里的滚动容器不会被算到面板头上
        val surface = bodyOf("OxideModsSurface.kt", "fun OxideModsSurface(")
        assertTrue(
            "the mods sub-window must not scroll itself",
            surface.contains("scrollable = false"),
        )
        val content = bodyOf("OxideModsSurface.kt", "fun OxideModsContent(")
        assertEquals(
            "the mods panel must hold exactly one lazy list",
            1,
            countOf("LazyColumn(", content),
        )
        assertEquals(
            "the mods panel must not add a second vertical scroll container",
            0,
            countOf(".verticalScroll(", content),
        )
    }

    @Test
    fun theContentManagersRailDoesNotIntroduceASecondVerticalScroller() {
        val panel = bodyOf("OxideContentManagers.kt", "fun OxideContentPanel(")
        assertEquals(
            "the content panel must hold exactly one lazy list",
            1,
            countOf("LazyColumn(", panel),
        )
        assertEquals(
            "the selection rail must not add a vertical scroll container",
            0,
            countOf(".verticalScroll(", panel),
        )
        // 窄栏自己那一块也不能滚：它是高度由内容决定的一栏
        val rail = bodyOf("OxideContentManagers.kt", "fun OxideContentSelectionRail(")
        assertEquals(
            "the selection rail must not scroll in either direction",
            0,
            countOf("Scroll(", rail),
        )
        // 唯一那一个纵向滚动容器属于左边的分类列（它自己一个窄栏，与列表无关）
        assertEquals(
            "the only vertical scroller left is the category column",
            1,
            countOf(".verticalScroll(", bodyOf("OxideContentManagers.kt", "fun OxideContentRail(")),
        )
    }

    @Test
    fun theSelectionActionsAreGoneFromTheVerticalFlowAndLiveInTheRail() {
        val panel = bodyOf("OxideContentManagers.kt", "fun OxideContentPanel(")
        // 选中动作现在住在控件那一项右侧的窄栏里，而不是压在列表上面的一行
        assertTrue(
            "the selection actions must be handed to the rail",
            panel.contains("OxideContentSelectionRail("),
        )
        assertTrue(
            "the rail must be drawn only while something is selected",
            panel.contains("if (selection.isNotEmpty()) {"),
        )
        // 控件那一项本身必须是一行：左边搜索与筛选，右边窄栏
        val first = panel.indexOf(CONTROLS_ITEM)
        val controlsStart = panel.indexOf(CONTROLS_ITEM, first + 1)
        val controls = panel.substring(controlsStart, panel.indexOf(LOADING_ITEM))
        assertTrue(
            "the controls item must be a Row so the rail can sit beside it",
            controls.substringAfter('{').trimStart().startsWith("Row("),
        )
        // 窄栏的宽度取分类列那一份推导，不是写死的 dp
        val rail = bodyOf("OxideContentManagers.kt", "fun OxideContentSelectionRail(")
        assertTrue(
            "the rail width must come from the shared category column metric",
            rail.contains("modifier = modifier.width(metrics.contentRailWidth())"),
        )
        // 动作、标签与启用条件一个都没有少。选中动作的文案在窄栏里就地读，
        // 全选与取消选择的文案仍然在面板顶部读一次再传下去
        for (res in listOf(
            "R.string.oxide_mgr_action_delete_selected",
            "R.string.oxide_mgr_action_clear_selection",
            "R.string.oxide_mgr_action_select_all",
        )) {
            assertTrue(
                "the panel and its rail must keep $res",
                rail.contains(res) || panel.contains(res),
            )
        }
        assertTrue(
            "the rail must keep the delete button's enabled condition",
            rail.contains("enabled = !busy,"),
        )
        assertTrue(
            "select-all must keep its enabled condition",
            controls.contains("enabled = visible.isNotEmpty(),"),
        )
    }

    // ---- 读源码的三个小工具 -------------------------------------------------

    /** 出现次数；比 `count { }` 少一层 lambda，断言读起来更直接 */
    private fun countOf(needle: String, source: String): Int =
        Regex(Regex.escape(needle)).findAll(source).count()

    /**
     * 取出 [name] 里 [signature] 那个顶层函数的函数体（不含签名本身）
     *
     * 按花括号配对切块，而不是对整个文件做断言：模组那两个对话框里各有一个
     * 纵向滚动容器，它们是**另外的表面**，与面板的滚动纪律无关。按函数切开之后，
     * "面板里只有那一个滚动容器"这句话才有意义。
     */
    private fun bodyOf(file: String, signature: String): String {
        val source = code(readSource(file))
        val start = source.indexOf(signature)
        assertTrue("could not find $signature in $file", start >= 0)
        val open = source.indexOf('{', start)
        assertTrue("$signature has no body", open >= 0)
        var depth = 0
        for (index in open until source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, index)
                }
            }
        }
        error("unbalanced braces while reading $signature from $file")
    }

    /**
     * 去掉字符串与注释，只留下真正会被编译的代码
     *
     * 顺序与 [OxideContentSurfaceGuardTest] 里的一样：先把字符串换掉，再去注释，
     * 否则字符串里的 `//` 会把后面整行连同花括号一起吃掉。
     */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    private fun readSource(name: String): String = locate(
        "ui/screens/main/oxide/$name"
    ).readText()

    /**
     * 从当前工作目录往上找源文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯，找不到就直接报错——
     * 绝不能悄悄跳过，那样的测试等于没有。
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
        /** 去掉字符串之后 [item] 的 key 变成空串，因此按这一段定位控件那一项 */
        const val CONTROLS_ITEM = "item(key = \"\")"

        /** 列表的第一项；它紧跟在控件那一项之后，用来给上面那一段划上边界 */
        const val LOADING_ITEM = "loading -> item"

        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}