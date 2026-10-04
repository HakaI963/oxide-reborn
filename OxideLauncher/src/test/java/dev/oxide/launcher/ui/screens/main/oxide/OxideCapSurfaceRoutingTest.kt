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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 最后两条旧导航链：协议全文与应用内浏览器
 *
 * `NormalNavKey.License` 与 `NormalNavKey.WebScreen` 是 Oxide 界面上仅剩的两条用户
 * 可见旧路线：协议全文从"关于"（以及仍然可达的旧设置栈"关于"）推开，内置浏览器从
 * 设备码登录推开。两者都是一整页旧 Zalith 界面：自己的图标顶栏、底下那页照原样透
 * 上来、没有可见的关闭按钮。
 *
 * 浏览器那一条还有第二层风险，因此必须钉住的是**问法**而不只是去路：
 * 设备码轮询靠 `checkIfInWebScreen` 判断用户是不是自己把网页关掉了
 * （见 `AccountUtils.microsoftLogin`）。旧问法是"栈顶那个键是不是 WebScreen"，一旦
 * 这条链断开，那个判断恒为假，登录会在授权页还开着的时候被静默取消。所以下面既钉住
 * 旧键彻底不再被推进，也钉住三个调用点问的是浏览器自己的状态。
 *
 * 至于协议全文：关于面板只**列**协议（`oxideAboutLicenceEntries` 从致谢名单派生若干行，
 * 每行一枚"读协议"按钮），它自己不渲染任何正文，所以旧那一页不能靠"关于已经能读了"
 * 来替代——`res/raw` 里那十九份文本是 GPLv3 要求随附的正文，删掉渲染等于让它们在
 * 应用里无法阅读。因此这一页换成了 Oxide 自己的面板，而不是消失。
 */
class OxideCapSurfaceRoutingTest {

    // -----------------------------------------------------------------------
    // 旧键不再被推进
    // -----------------------------------------------------------------------

    @Test
    fun `the main screen pushes neither of the last two old routes`() {
        val code = codeOf(locate("ui/screens/main/MainScreen.kt").readText())
        // 只看代码：注释里提到这两个旧键是正常的——那里要解释为什么不再用它们，
        // 而一条断言不该被注释满足
        for (gone in listOf(
            "entry<NormalNavKey.License>",
            "entry<NormalNavKey.WebScreen>",
            "NormalNavKey.License(",
            "NormalNavKey.WebScreen(",
        )) {
            assertFalse("$gone is a full old Zalith page, it must not be reachable", code.contains(gone))
        }
        for (old in listOf("LicenseScreen(", "WebViewScreen(")) {
            assertFalse("$old must not be rendered by the shell", code.contains(old))
        }
        assertFalse(
            "the shell must not push the browser either, the surface owns it now",
            code.contains("navigateToWeb("),
        )
    }

    @Test
    fun `the two old pages are gone, not merely unlinked`() {
        assertFalse(
            "LicenseScreen rendered the licence text and nothing else does now",
            locateIfPresent("ui/screens/content/LicenseScreen.kt") != null,
        )
        val webViewScreen = codeOf(locate("ui/screens/content/WebViewScreen.kt").readText())
        assertFalse(
            "the full-page browser is what this change replaces",
            webViewScreen.contains("fun WebViewScreen("),
        )
        assertFalse(
            "navigateToWeb must not push anything any more",
            webViewScreen.contains("navigateTo("),
        )
        assertFalse(
            "the browser must not be a navigation entry any more",
            webViewScreen.contains("NormalNavKey.WebScreen("),
        )
        // 那座桥还在：旧账号管理界面靠它编译，而它现在指向 Oxide 的浏览器
        assertTrue(webViewScreen.contains("fun NavBackStack<TitledNavKey>.navigateToWeb"))
        assertTrue(webViewScreen.contains("openOxideBrowser(webUrl)"))
    }

    // -----------------------------------------------------------------------
    // 两个去处都落在 Oxide 自己的面板上
    // -----------------------------------------------------------------------

    @Test
    fun `both licence entry points open the oxide licence panel`() {
        val mainScreen = codeOf(locate("ui/screens/main/MainScreen.kt").readText())
        assertTrue(
            "the about panel still has to be able to open a licence",
            mainScreen.contains("OxideAboutPanel("),
        )
        assertTrue(
            "the old settings stack still has a licence button, and it is still reachable",
            mainScreen.contains("openLicenseScreen = { raw ->"),
        )
        assertEquals(
            "both licence entry points must go through the one host callback",
            2,
            Regex("""onOpenLicence\(raw\)""").findAll(mainScreen).count(),
        )
        assertTrue(
            "the host must actually render the licence panel",
            mainScreen.contains("OxideLicencePanel(raw = raw"),
        )
    }

    @Test
    fun `the browser is hosted above the navigation tree, not inside an entry`() {
        val mainScreen = codeOf(locate("ui/screens/main/MainScreen.kt").readText())
        assertTrue(mainScreen.contains("OxideBrowserPanel("))
        // 盖板必须声明在导航树之后：Compose 的 Box 按声明顺序绘制，写在前面就等于
        // 被底下那一页压住
        assertTrue(
            "the surfaces must be composed after the navigation tree",
            mainScreen.indexOf("OxideCapSurfaces(") > mainScreen.indexOf("NavigationUI("),
        )
        // 不透明：只铺 PanelBackdrop 仍会漏出约 5%，底下那页的粗体大标题在真机上
        // 看得见，两块标题会重影
        assertTrue(mainScreen.contains("OxideDestinationBackdrop("))
    }

    @Test
    fun `both surfaces are centred panels rather than full pages`() {
        for (surface in listOf("OxideBrowserSurface.kt", "OxideLicencePanel.kt")) {
            val source = codeOf(locate("ui/screens/main/oxide/$surface").readText())
            assertTrue(
                "$surface must use the shared sub-window shell",
                source.contains("OxideSubWindow("),
            )
            assertFalse(
                "$surface must not reintroduce a full-page navigation entry",
                source.contains("navigateTo("),
            )
        }
    }

    // -----------------------------------------------------------------------
    // 浏览器开着吗：三个调用点问的是同一份状态
    // -----------------------------------------------------------------------

    @Test
    fun `no call site guesses from the navigation key any more`() {
        val callers = listOf("OxideAccountPage.kt", "OxideLaunchPreflight.kt")
            .map { codeOf(locate("ui/screens/main/oxide/$it").readText()) }
        for (source in callers) {
            assertFalse(
                "no call site may read the nav key to guess that the browser is open",
                source.contains("is NormalNavKey.WebScreen"),
            )
        }
        assertEquals(
            "the account page asks twice, the preflight asks once",
            3,
            Regex("""globalOxideBrowser\.isOpen\(\)""")
                .findAll(callers.joinToString("\n"))
                .count(),
        )
    }

    @Test
    fun `the browser state is answerable without a composition`() {
        // 读它的是设备码任务的轮询循环，写它的是同一条任务：两边都在 IO 线程上。
        // 一旦这份状态变成组合状态（例如 remember + mutableStateOf），后台读到的
        // 就是一个没提交过的快照，于是登录轮询会看到"没开"并取消这一次登录。
        val source = locate("ui/screens/main/oxide/OxideBrowserSurface.kt").readText()
        val stateClass = Regex("""(?s)class OxideBrowserState \{.*?\n\}""")
            .find(source)
            ?.value
            ?: error("could not find the OxideBrowserState class in OxideBrowserSurface.kt")
        assertFalse(
            "the browser state must not hold Compose state",
            stateClass.contains("mutableStateOf"),
        )
        assertTrue(stateClass.contains("MutableStateFlow"))
        assertTrue(stateClass.contains("fun isOpen()"))
    }

    @Test
    fun `the browser closes itself when the sign-in it belongs to is over`() {
        // 旧实现在登录跑完之后调 backToMain()，把导航栈清回主界面，顺带把网页那一项
        // 弹掉。网页不再是栈上的条目，不补这一步，授权成功之后那块盖住整个界面的
        // 面板会一直留着，而游戏已经在它背后开始启动了。
        val mainScreen = locate("ui/screens/main/MainScreen.kt").readText()
        assertTrue(
            "the host has to close the browser when the sign-in ends",
            mainScreen.contains("OxideBrowserClosesWhenSignInEnds("),
        )
    }

    private companion object {

        /**
         * 从当前工作目录往上找源文件
         *
         * 单元测试的工作目录不一定是模块根目录，所以逐级上溯；找不到直接报错，
         * 绝不悄悄跳过——那样的断言等于没有。
         */
        fun locate(relativePath: String): File =
            locateIfPresent(relativePath) ?: error(
                "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                    File("").absolutePath
            )

        /**
         * 去掉字符串与注释，只留下真正会被编译的代码
         *
         * 这一份改动留下的注释里到处都写着那两个旧键的名字——那里解释的正是为什么
         * 不再推进它们。断言必须只看代码，否则一条"旧键已经不再出现"的断言会被它
         * 自己文件里的说明当场推翻。
         *
         * 顺序要紧：先把字符串换掉，再去注释。否则一个字符串里的 `//`
         * （某个网址）会被当成行注释，把后面整行代码连同它的花括号一起吃掉。
         * 与 `OxideContentSurfaceGuardTest` 用的是同一套写法。
         */
        fun codeOf(source: String): String = source
            .replace(RAW_STRING, REPLACED)
            .replace(STRING, REPLACED)
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, "")

        /** 同上，但"这个文件已经不在了"是它要回答的问题，因此不报错 */
        fun locateIfPresent(relativePath: String): File? {
            var dir: File? = File("").absoluteFile
            repeat(8) {
                val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
                if (candidate != null && candidate.isFile) return candidate
                dir = dir?.parentFile
            }
            return null
        }

        /** 原始字符串：里面可以出现引号、换行与注释符号，必须先换掉 */
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /** 被换掉的那一段字符串：空引号足以让"这一行是不是注释"这件事失去意义 */
        const val REPLACED = "\"\""
    }
}