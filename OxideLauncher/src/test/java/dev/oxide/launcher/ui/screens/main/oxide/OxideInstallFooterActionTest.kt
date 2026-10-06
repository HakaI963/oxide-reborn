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
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 最后一步那个「安装」按钮
 *
 * v1.8.0 的设备反馈是：装到第三步之后，屏幕里同时出现一个能用的「安装」和一个
 * 永远禁用的「下一步」，而那个能用的还压在**可滚动内容区的末尾**——用户必须先往下
 * 滚，才看得见它。第三步本来就没有下一步（[oxideInstallNextStep] 在那里返回 null，
 * 因为它自己就是终点，再往前不是第四步而是安装本身），所以那个位置上画着的
 * 「下一步」是一条死路，而真正管用的那个按钮被埋在滚动区里。
 *
 * 修法只有一条：**取消重复的那个按钮，让底部动作行上那一个变成安装**。
 * 下面几组断言把这条钉死：
 *
 *  - [oxideInstallFooterAction] 是「做什么 / 写什么字 / 亮不亮」的唯一定义，
 *    三件事同源，因此不存在「文案是一种动作、点击却是另一种」的可能。
 *  - [oxideInstallNameUsable] 把「装不装得下去」摊成可以逐条测的判据，
 *    空名字、非法字符、重名、探测未回这四种都各有各的那一条。
 *  - 最后一组读源码：可滚动的第三步里**不允许**再出现 `download_install`，
 *    那个重复的按钮因此回不来。读法与 [OxideModsPanelLayoutTest] 相同：
 *    去掉字符串与注释之后按**函数**切块，而不是对整个文件做断言。
 *
 * 这里没有 Compose，也没有 Android：全部断言都打在纯函数与源码文本上，
 * 因此它们能在没有 SDK 的机器上跑。
 */
class OxideInstallFooterActionTest {

    // ---- 前两步：仍然是「下一步」 --------------------------------------------

    @Test
    fun theVersionStepOffersNextAndNothingElse() {
        val action = oxideInstallFooterAction(
            step = OxideInstallStep.Version,
            nextStep = OxideInstallStep.Loader,
            canInstall = false,
        )
        assertEquals(OxideInstallFooterKind.Next, action.kind)
        assertEquals(R.string.oxide_inst_next, action.labelRes)
        assertTrue(action.enabled)
    }

    @Test
    fun theLoaderStepOffersNextAndNothingElse() {
        val action = oxideInstallFooterAction(
            step = OxideInstallStep.Loader,
            nextStep = OxideInstallStep.Install,
            canInstall = false,
        )
        assertEquals(OxideInstallFooterKind.Next, action.kind)
        assertEquals(R.string.oxide_inst_next, action.labelRes)
        assertTrue(action.enabled)
    }

    @Test
    fun nextStaysVisibleButDisabledWhenThereIsNowhereToGo() {
        // 「还没选版本所以到不了下一步」必须看得见，而不是让按钮凭空消失
        for (step in listOf(OxideInstallStep.Version, OxideInstallStep.Loader)) {
            val action = oxideInstallFooterAction(step, nextStep = null, canInstall = true)
            assertEquals("步骤 $step", OxideInstallFooterKind.Next, action.kind)
            assertEquals("步骤 $step", R.string.oxide_inst_next, action.labelRes)
            assertFalse("步骤 $step：走不到下一步时必须禁用", action.enabled)
        }
    }

    @Test
    fun theInstallReadinessNeverDrivesTheFirstTwoSteps() {
        // canInstall 是第三步的事：它不该顺手动到前两步的按钮上，
        // 否则「名字还不可用」会让「下一步」莫名其妙地灭掉
        for (step in listOf(OxideInstallStep.Version, OxideInstallStep.Loader)) {
            assertTrue(
                "步骤 $step：名字不可用也不该影响下一步",
                oxideInstallFooterAction(step, OxideInstallStep.Loader, canInstall = false).enabled,
            )
        }
    }

    // ---- 第三步：那个按钮就是安装 --------------------------------------------

    @Test
    fun theInstallStepIsWhereTheCommitButtonLives() {
        val action = oxideInstallFooterAction(
            step = OxideInstallStep.Install,
            nextStep = null,
            canInstall = true,
        )
        assertEquals(
            "第三步右侧那个按钮必须是安装，不是那条走不通的「下一步」",
            OxideInstallFooterKind.Install,
            action.kind,
        )
        assertEquals(R.string.download_install, action.labelRes)
        assertTrue(action.enabled)
    }

    @Test
    fun theInstallStepIsNeverOfferedANextStepEvenIfOneWerePassed() {
        // oxideInstallNextStep 在第三步返回 null；万一哪一天它变了，
        // 这一条也不允许把第三步悄悄变回一条死路
        val action = oxideInstallFooterAction(
            step = OxideInstallStep.Install,
            nextStep = OxideInstallStep.Version,
            canInstall = true,
        )
        assertEquals(OxideInstallFooterKind.Install, action.kind)
        assertEquals(R.string.download_install, action.labelRes)
    }

    @Test
    fun theInstallButtonIsGreyedOutExactlyWhenTheNameIsNotUsable() {
        // canInstall 的真实含义：页面把它算成 oxideInstallNameUsable(...) 再叠上
        // 没有另一次安装在跑。下面把 oxideInstallNameUsable 的每一种不可用都走一遍，
        // 那个「安装」必须一步不落地跟着灰掉——不是藏起来，是画着但禁用。
        val blank = oxideInstallNameUsable("", filenameInvalid = false, existsProbe = false)
        val illegal = oxideInstallNameUsable("a/b", filenameInvalid = true, existsProbe = false)
        val taken = oxideInstallNameUsable("1.20.4", filenameInvalid = false, existsProbe = true)
        val probing = oxideInstallNameUsable("1.20.4", filenameInvalid = false, existsProbe = null)
        val fine = oxideInstallNameUsable("1.20.4 Fabric0.19.5", filenameInvalid = false, existsProbe = false)

        assertFalse("版本名为空", blank)
        assertFalse("版本名含非法字符", illegal)
        assertFalse("与已装的那个重名", taken)
        assertFalse("重名探测还没回来", probing)
        assertTrue("一个正常且没被占用的名字", fine)

        for (canInstall in listOf(blank, illegal, taken, probing)) {
            assertFalse(
                "名字不可用时第三步那个「安装」必须灭着，canInstall=$canInstall",
                oxideInstallFooterAction(OxideInstallStep.Install, null, canInstall).enabled,
            )
        }
        assertTrue(
            "名字可用时第三步那个「安装」必须亮",
            oxideInstallFooterAction(OxideInstallStep.Install, null, fine).enabled,
        )
    }

    @Test
    fun theInstallButtonIsAlsoGreyedOutWhileAnotherInstallIsRunning() {
        // 页面把 canInstall 交给动作行之前会再叠一条「此刻没有另一次安装在跑」，
        // 因此这一条只钉住那一半：探测说可用，但正在跑的那一次仍然让它灭着
        val nameUsable = oxideInstallNameUsable("1.20.4", filenameInvalid = false, existsProbe = false)
        assertTrue(nameUsable)
        assertFalse(
            oxideInstallFooterAction(OxideInstallStep.Install, null, canInstall = false).enabled
        )
        assertTrue(
            oxideInstallFooterAction(OxideInstallStep.Install, null, canInstall = true).enabled
        )
    }

    // ---- 那个重复的按钮不许回来 ----------------------------------------------

    @Test
    fun theScrollableConfirmStepRendersNoInstallButtonOfItsOwn() {
        // 这正是 v1.8.0 设备截图里的那条：最后一步上同时有一个能用的「安装」
        // 和一个永远禁用的「下一步」，而能用的那个在可滚动内容区的末尾
        val page = code(readSource("OxideInstallVersionPage.kt"))
        val step = bodyOf(source = page, signature = "private fun OxideInstallConfirmStep(")
        assertFalse(
            "可滚动的第三步里不允许再画一个提交安装的按钮",
            mentionsResource(step, "R.string.download_install"),
        )
        // 摘要与表单要留在原地，不能跟着按钮一起被删掉
        assertTrue(
            "摘要本身必须还在（THIS INSTALL WILL CONTAIN）",
            step.contains("oxide_inst_summary"),
        )
        assertTrue(step.contains("oxide_inst_chosen_version"))
        // 装到一半换成进度面板、成功之后那一句、已经装过那一条早退，一条都没有少
        assertTrue("装到一半换成进度面板的那条路仍然在", step.contains("OxideInstallProgressPanel("))
        assertTrue("成功之后那一句仍然在", step.contains("download_install_success_message"))
        assertTrue("已经装过那一条早退仍然在", step.contains("versions_manage_install_exists"))
        assertTrue("失败之后那一条重试仍然在", step.contains("requestInstall()"))

        // 版本名的可用性与重试仍然从参数进来（调用点写的是 `nameIsError = nameIsError`，
        // 因此声明处的冒号写法不会被误认成调用点）
        assertTrue("nameIsError 参数不能少", page.contains("nameIsError: Boolean"))
        assertTrue("nameErrorMessage 参数不能少", page.contains("nameErrorMessage: String?"))
        assertTrue("requestInstall 参数不能少", page.contains("requestInstall: () -> Unit"))
        assertTrue("startInstall 参数不能少", page.contains("startInstall: (GameDownloadInfo) -> Unit"))
    }

    @Test
    fun thePanelDeclaresExactlyOneInstallActionAndItIsTheFooterOne() {
        // 整页里 download_install 与 oxide_inst_next 一个字都不该出现：
        // 两句文案现在只住在 oxideInstallFooterAction 那一处定义里，
        // 界面只照着画，因此「又长出一个按钮」没有任何入口。
        // （这里必须精确匹配资源名：download_install_warning_mobile_data 那些
        //   提醒与错误文案一个字都不能被算进来。）
        val page = code(readSource("OxideInstallVersionPage.kt"))
        assertFalse(mentionsResource(page, "R.string.download_install"))
        assertFalse(mentionsResource(page, "R.string.oxide_inst_next"))

        val footer = bodyOf(source = page, signature = "private fun OxideInstallFooter(")
        assertTrue(
            "按钮上的字必须来自那一份纯定义",
            footer.contains("stringResource(action.labelRes)"),
        )
        assertTrue(
            "点下去做什么也必须来自那一份纯定义",
            footer.contains("OxideInstallFooterKind.Install -> requestInstall"),
        )
        assertTrue(
            "前两步仍然走下一步",
            footer.contains("OxideInstallFooterKind.Next -> onGoNext"),
        )
        assertTrue(
            "亮不亮同样来自那一份纯定义",
            footer.contains("enabled = action.enabled"),
        )

        // 页面上装不装得下去要传到动作行，否则第三步那个按钮永远不亮
        val call = bodyOf(source = page, signature = "fun OxideInstallVersionPage(")
        assertTrue("动作行必须拿到 requestInstall", call.contains("requestInstall = requestInstall,"))
        assertTrue("动作行必须拿到那份纯定义算出来的按钮", call.contains("action = footerAction,"))
        assertTrue(
            "底部动作行仍然整行收在安装进行时那个条件里",
            call.contains("if (operation !is OxideInstallOperation.Installing) {"),
        )
        assertTrue(
            "canInstall 必须由名字可用性与没有另一次安装在跑共同决定",
            call.contains("canInstall = nameUsable && operation is OxideInstallOperation.None,"),
        )
    }

    @Test
    fun theThirdStepStillReadsAsTheInstallStepItIs() {
        // 步骤轨第三格的标题是「安装」，下面那一行写的是它**此刻的状态**
        // （进行中 / 完成），不是「下一步」。它与这次改动无关，也不该为了这条反馈
        // 被改成别的东西——这里只钉住标题与状态仍然是原来那一份
        assertEquals(R.string.oxide_inst_step_install, OxideInstallStep.Install.titleRes)
        val states = oxideInstallStepStates(
            OxideInstallFlowState(gameVersion = "1.21.1", step = OxideInstallStep.Install),
        )
        assertEquals(OxideInstallStepState.Active, states[2])
        assertEquals("3", oxideInstallStepMarker(OxideInstallStepState.Active, 2))
    }

    // ---- 读源码的几个小工具 -------------------------------------------------

    /**
     * 从 [source] 里取出 [signature] 那个函数的函数体（不含签名本身）
     *
     * 按花括号配对切块，而不是对整个文件做断言：「第三步里没有安装按钮」这句话
     * 只有在**只看第三步**时才成立——同文件里 [OxideInstallFooter] 正是那个按钮
     * 该在的地方。字符串与注释在传进来之前已经去掉，因此这里的括号配对
     * 不会被字符串里的括号带偏。
     *
     * 函数体从**参数表之后**开始找，而不是从签名的第一个 `{`：带默认值的参数里
     * 就有 `= {}`（`OxideInstallVersionPage` 的 `onDismiss` 就是一个），
     * 从那里开始配对会立刻配平并切出一个空块。
     *
     * [source] 显式作为参数传进来，而不是让这个函数自己去读文件：
     * 读文件只发生在 [readSource] 里，而那是在测试方法体内被调用的。
     */
    private fun bodyOf(source: String, signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("could not find $signature", start >= 0)
        val signatureEnd = source.closeParenFrom(source.indexOf('(', start))
        val open = source.indexOf('{', signatureEnd)
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
        error("unbalanced braces while reading $signature")
    }

    /** 从 [open] 起做圆括号配对，返回与它配对的那个 `)` 的下标 */
    private fun String.closeParenFrom(open: Int): Int {
        var depth = 0
        for (index in open until length) {
            when (this[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        error("unbalanced parentheses from index $open")
    }

    /**
     * [source] 里有没有**恰好**是这个资源名的引用
     *
     * 不能用 `contains`：资源名互为前缀，`R.string.download_install` 是
     * `R.string.download_install_warning_mobile_data` 的前缀，而那些移动流量提醒
     * 与错误文案一个字都不该被算进来。因此后面必须不能再接标识符字符。
     */
    private fun mentionsResource(source: String, reference: String): Boolean =
        Regex(Regex.escape(reference) + "(?![A-Za-z0-9_])").containsMatchIn(source)

    /**
     * 去掉字符串与注释，只留下真正会被编译的代码
     *
     * 顺序与 [OxideModsPanelLayoutTest] 里的一样：先把字符串换掉，再去注释，
     * 否则字符串里的 `//` 会把后面整行连同花括号一起吃掉。
     *
     * 字符字面量**放在最后**处理：到这一步文件里已经只剩代码，
     * `'{'` / `'}'` 这种才不会被 [bodyOf] 当成真正的花括号——放进配对之前先剥掉。
     */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")
        .replace(CHAR_LITERAL, "\"\"")

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
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")

        /** 字符字面量；必须在字符串与注释都去掉之后才用 */
        val CHAR_LITERAL = Regex("""'(\\.|[^'\\\n])'""")
    }
}