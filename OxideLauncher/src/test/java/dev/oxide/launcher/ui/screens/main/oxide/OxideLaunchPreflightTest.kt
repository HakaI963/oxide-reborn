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

import dev.oxide.launcher.ui.screens.content.elements.LaunchGameOperation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 启动前检查的决定过程
 *
 * 这一段以前是七个各弹各的 Modal，因此"按 Play 之后到底弹哪一个"取决于哪条检查先返回。
 * 现在它被收成两个纯函数：[oxidePreflightBranch] 决定停在哪一条，
 * [oxidePreflightAsk] 决定问什么、给哪些选择。
 * 下面把顺序、每一个结局、以及"这一段该不该把启动页盖上来"逐条钉死。
 */
class OxideLaunchPreflightTest {

    private fun state(
        hasVersion: Boolean = true,
        versionNameValid: Boolean = true,
        hasAccount: Boolean = true,
        rendererSupported: Boolean = true,
        plugins: List<String> = emptyList(),
        needsStoragePermission: Boolean = false,
    ) = OxidePreflightState(
        hasVersion = hasVersion,
        versionNameValid = versionNameValid,
        hasAccount = hasAccount,
        rendererSupported = rendererSupported,
        unsupportedPluginNames = plugins,
        needsStoragePermission = needsStoragePermission,
    )

    // -----------------------------------------------------------------------
    // 顺序
    // -----------------------------------------------------------------------

    /** 一切都就绪时不用问任何东西，直接启动 */
    @Test
    fun readyLaunchesWithoutAsking() {
        assertEquals(OxidePreflightBranch.RealLaunch, oxidePreflightBranch(state()))
        assertEquals(OxidePreflightState.Ready, state())
        assertTrue(oxidePreflightAsk(OxidePreflightBranch.RealLaunch).actions.isEmpty())
    }

    /**
     * `TryLaunch` 里的顺序是 实例 → 名字 → 账号 → 渲染器 → 插件 → 权限
     *
     * 每一次只把一条检查弄坏，其余保持就绪：坏哪一条就必须停在哪一条。
     * 这条顺序是这份逻辑的全部意义——调换它就是换掉后端的判断。
     */
    @Test
    fun eachBrokenCheckStopsAtItself() {
        assertEquals(
            OxidePreflightBranch.NoVersion,
            oxidePreflightBranch(state(hasVersion = false)),
        )
        assertEquals(
            OxidePreflightBranch.InvalidVersionName,
            oxidePreflightBranch(state(versionNameValid = false)),
        )
        assertEquals(
            OxidePreflightBranch.NoAccount,
            oxidePreflightBranch(state(hasAccount = false)),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedRenderer,
            oxidePreflightBranch(state(rendererSupported = false)),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedPlugins,
            oxidePreflightBranch(state(plugins = listOf("Sodium"))),
        )
        assertEquals(
            OxidePreflightBranch.RendererNoStoragePermission,
            oxidePreflightBranch(state(needsStoragePermission = true)),
        )
    }

    /** 同时坏了多条时，先到的那条赢——与旧实现里"先说没有账号再看渲染器"一致 */
    @Test
    fun earlierCheckWinsOverLaterOne() {
        assertEquals(
            OxidePreflightBranch.NoVersion,
            oxidePreflightBranch(
                state(
                    hasVersion = false,
                    versionNameValid = false,
                    hasAccount = false,
                    rendererSupported = false,
                    plugins = listOf("Sodium"),
                    needsStoragePermission = true,
                ),
            ),
        )
        assertEquals(
            OxidePreflightBranch.InvalidVersionName,
            oxidePreflightBranch(
                state(
                    versionNameValid = false,
                    hasAccount = false,
                    rendererSupported = false,
                    plugins = listOf("Sodium"),
                    needsStoragePermission = true,
                ),
            ),
        )
        assertEquals(
            OxidePreflightBranch.NoAccount,
            oxidePreflightBranch(
                state(
                    hasAccount = false,
                    rendererSupported = false,
                    plugins = listOf("Sodium"),
                    needsStoragePermission = true,
                ),
            ),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedRenderer,
            oxidePreflightBranch(
                state(
                    rendererSupported = false,
                    plugins = listOf("Sodium"),
                    needsStoragePermission = true,
                ),
            ),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedPlugins,
            oxidePreflightBranch(
                state(plugins = listOf("Sodium"), needsStoragePermission = true),
            ),
        )
    }

    /** 插件列表为空就算没有插件：非空判断而不是"个数 > 1"之类的含糊条件 */
    @Test
    fun oneUnsupportedPluginIsEnough() {
        assertEquals(
            OxidePreflightBranch.UnsupportedPlugins,
            oxidePreflightBranch(state(plugins = listOf("Lithium"))),
        )
        assertEquals(
            OxidePreflightBranch.RealLaunch,
            oxidePreflightBranch(state(plugins = emptyList())),
        )
    }

    // -----------------------------------------------------------------------
    // 凭据：真正跑起来之后回调回来的那三种
    // -----------------------------------------------------------------------

    /** 凭据还新鲜时不问任何东西 */
    @Test
    fun freshTokenAsksNothing() {
        assertEquals(
            OxidePreflightBranch.RealLaunch,
            oxideTokenBranch(OxideTokenState.Fresh, OxideAccountKind.Microsoft),
        )
        assertEquals(
            OxidePreflightBranch.RealLaunch,
            oxideTokenBranch(OxideTokenState.Fresh, OxideAccountKind.ThirdParty),
        )
    }

    /** 凭据被拒时，微软账号与第三方账号给的是两种不同的问法 */
    @Test
    fun rejectedTokenSplitsByAccountKind() {
        assertEquals(
            OxidePreflightBranch.AccountReloginMicrosoft,
            oxideTokenBranch(OxideTokenState.Rejected, OxideAccountKind.Microsoft),
        )
        assertEquals(
            OxidePreflightBranch.AccountReloginPassword,
            oxideTokenBranch(OxideTokenState.Rejected, OxideAccountKind.ThirdParty),
        )
    }

    /** 刷新失败与凭据种类无关：三个结局（重试 / 跳过 / 取消）始终齐全 */
    @Test
    fun refreshFailureIsTheSameForEveryAccountKind() {
        OxideAccountKind.entries.forEach { kind ->
            assertEquals(
                OxidePreflightBranch.AccountRefreshFailed,
                oxideTokenBranch(OxideTokenState.RefreshFailed, kind),
            )
        }
    }

    // -----------------------------------------------------------------------
    // 问什么、给哪些选择
    // -----------------------------------------------------------------------

    @Test
    fun unsupportedRendererAsksAnywayOrAbort() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.UnsupportedRenderer)
        assertEquals(
            listOf(OxidePreflightAction.LaunchAnyway, OxidePreflightAction.Abort),
            ask.actions,
        )
        assertEquals(OxidePreflightMessage.UnsupportedRenderer, ask.message)
        assertFalse(ask.requiresPasswordInput)
    }

    @Test
    fun unsupportedPluginsAskAnywayOrAbort() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.UnsupportedPlugins)
        assertEquals(
            listOf(OxidePreflightAction.LaunchAnyway, OxidePreflightAction.Abort),
            ask.actions,
        )
        assertEquals(OxidePreflightMessage.UnsupportedPlugins, ask.message)
    }

    /**
     * 存储权限这一条保留三个出口
     *
     * 旧的 Material 提示本来就有"授权"和"忽略"两个按钮，再加上启动页自己的"取消"，
     * 因此授权与"仍然启动"都必须留着——忽略授权并不是一个失败状态。
     */
    @Test
    fun storagePermissionKeepsAuthorizeAndAnywayAndAbort() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.RendererNoStoragePermission)
        assertEquals(
            listOf(
                OxidePreflightAction.AuthorizeStorage,
                OxidePreflightAction.LaunchAnyway,
                OxidePreflightAction.Abort,
            ),
            ask.actions,
        )
        assertEquals(OxidePreflightMessage.StoragePermission, ask.message)
    }

    /** 版本名非法没有任何选择，所以只需要一个"知道了" */
    @Test
    fun invalidVersionNameOnlyNeedsDismiss() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.InvalidVersionName)
        assertEquals(listOf(OxidePreflightAction.Abort), ask.actions)
        assertTrue(oxidePreflightIsNoticeOnly(ask))
    }

    @Test
    fun microsoftReloginNeedsNoInput() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.AccountReloginMicrosoft)
        assertEquals(
            listOf(OxidePreflightAction.MicrosoftSignIn, OxidePreflightAction.Abort),
            ask.actions,
        )
        assertFalse(ask.requiresPasswordInput)
        assertFalse(oxidePreflightIsNoticeOnly(ask))
    }

    /** 第三方账号才需要一行输入框——这是两者唯一真正的差别 */
    @Test
    fun thirdPartyReloginNeedsAPasswordField() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.AccountReloginPassword)
        assertEquals(
            listOf(OxidePreflightAction.SubmitPassword, OxidePreflightAction.Abort),
            ask.actions,
        )
        assertTrue(ask.requiresPasswordInput)
        assertFalse(oxidePreflightIsNoticeOnly(ask))
    }

    @Test
    fun refreshFailedOffersRetrySkipAndCancel() {
        val ask = oxidePreflightAsk(OxidePreflightBranch.AccountRefreshFailed)
        assertEquals(
            listOf(
                OxidePreflightAction.RetryRefresh,
                OxidePreflightAction.SkipRefresh,
                OxidePreflightAction.Abort,
            ),
            ask.actions,
        )
        assertEquals(3, ask.actions.distinct().size)
    }

    /** 除了真正启动之外，每一条都有一条退路 */
    @Test
    fun everyAskingBranchCanBeAbandoned() {
        OxidePreflightBranch.entries.forEach { branch ->
            if (branch == OxidePreflightBranch.RealLaunch) return@forEach
            assertTrue(
                "缺少退路：$branch",
                oxidePreflightAsk(branch).actions.contains(OxidePreflightAction.Abort),
            )
        }
    }

    /**
     * 没有实例与没有账号走的是 toast
     *
     * 旧实现对这两条是弹一次 toast 并立刻把 operation 复位，
     * 因此启动页上不该闪一行出来——那一帧只会让人以为还有别的事没做完。
     */
    @Test
    fun missingVersionAndAccountStayToasts() {
        assertTrue(oxidePreflightToastOnly(OxidePreflightBranch.NoVersion))
        assertTrue(oxidePreflightToastOnly(OxidePreflightBranch.NoAccount))
        assertFalse(oxidePreflightToastOnly(OxidePreflightBranch.UnsupportedRenderer))
        assertFalse(oxidePreflightToastOnly(OxidePreflightBranch.InvalidVersionName))
    }

    // -----------------------------------------------------------------------
    // 启动页该盖住哪一段
    // -----------------------------------------------------------------------

    @Test
    fun idleLaunchesNothing() {
        assertFalse(
            oxideLaunchVisible(
                flowActive = false,
                operation = oxidePreflightOperationOf(LaunchGameOperation.None),
            ),
        )
        assertFalse(oxideLaunchVisible(false, OxidePreflightOperation.Idle))
    }

    /**
     * 阶段还没出现就已经要有启动页
     *
     * 旧实现正是在这段空窗里弹出了那串 Modal：按下 Play 到第一条阶段出现之间
     * 有一堆实打实的检查，其中 `ensureVulkanSupported` 还是挂起调用。
     */
    @Test
    fun checkingKeepsThePageUpBeforeAnyStageExists() {
        assertTrue(oxideLaunchVisible(false, oxidePreflightOperationOf(LaunchGameOperation.TryLaunch(version = null))))
        assertTrue(oxideLaunchVisible(false, OxidePreflightOperation.Checking))
        assertTrue(oxideLaunchVisible(false, OxidePreflightOperation.RealLaunch))
    }

    /** 每个要问人的分支都必须把启动页盖住 */
    @Test
    fun everyDecisionKeepsThePageUp() {
        val deciding = OxidePreflightOperation.entries.filter {
            oxidePreflightBranchOf(it) != null
        }
        assertTrue(deciding.isNotEmpty())
        deciding.forEach {
            assertTrue("不该被遮住：$it", oxideLaunchVisible(false, it))
        }
    }

    /** 流程结束之后必须收起来，否则启动页会永远压在最上面 */
    @Test
    fun idleHidesThePageEvenWhileAFlowObjectIsStillAround() {
        assertTrue(oxideLaunchVisible(true, OxidePreflightOperation.Idle))
        assertFalse(oxideLaunchVisible(false, OxidePreflightOperation.Idle))
    }

    /** 只要还有一条启动流程在跑，无论 operation 是什么都不收 */
    @Test
    fun anActiveFlowAloneKeepsThePageUp() {
        OxidePreflightOperation.entries.forEach {
            assertTrue(oxideLaunchVisible(true, it))
        }
    }

    // -----------------------------------------------------------------------
    // 投影
    // -----------------------------------------------------------------------

    @Test
    fun progressStatesAskNothing() {
        assertNull(oxidePreflightBranchOf(OxidePreflightOperation.Idle))
        assertNull(oxidePreflightBranchOf(OxidePreflightOperation.Checking))
        assertNull(oxidePreflightBranchOf(OxidePreflightOperation.RealLaunch))
    }

    @Test
    fun everyDecisionOperationMapsToItsBranch() {
        assertEquals(
            OxidePreflightBranch.NoVersion,
            oxidePreflightBranchOf(OxidePreflightOperation.NoVersion),
        )
        assertEquals(
            OxidePreflightBranch.InvalidVersionName,
            oxidePreflightBranchOf(OxidePreflightOperation.InvalidVersionName),
        )
        assertEquals(
            OxidePreflightBranch.NoAccount,
            oxidePreflightBranchOf(OxidePreflightOperation.NoAccount),
        )
        assertEquals(
            OxidePreflightBranch.RendererNoStoragePermission,
            oxidePreflightBranchOf(OxidePreflightOperation.RendererNoStoragePermission),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedRenderer,
            oxidePreflightBranchOf(OxidePreflightOperation.UnsupportedRenderer),
        )
        assertEquals(
            OxidePreflightBranch.UnsupportedPlugins,
            oxidePreflightBranchOf(OxidePreflightOperation.UnsupportedPlugins),
        )
        assertEquals(
            OxidePreflightBranch.AccountReloginMicrosoft,
            oxidePreflightBranchOf(OxidePreflightOperation.AccountReloginMicrosoft),
        )
        assertEquals(
            OxidePreflightBranch.AccountReloginPassword,
            oxidePreflightBranchOf(OxidePreflightOperation.AccountReloginPassword),
        )
        assertEquals(
            OxidePreflightBranch.AccountRefreshFailed,
            oxidePreflightBranchOf(OxidePreflightOperation.AccountRefreshFailed),
        )
    }

    @Test
    fun operationProjectionCoversTheSealedType() {
        assertEquals(
            OxidePreflightOperation.Idle,
            oxidePreflightOperationOf(LaunchGameOperation.None),
        )
        assertEquals(
            OxidePreflightOperation.NoVersion,
            oxidePreflightOperationOf(LaunchGameOperation.NoVersion),
        )
        // 实例为空时 `TryLaunch` 会自己转成 NoVersion，因此这一刻仍在检查中
        assertEquals(
            OxidePreflightOperation.Checking,
            oxidePreflightOperationOf(LaunchGameOperation.TryLaunch(version = null)),
        )
    }

    /**
     * 只有三种状态不用问，其余每一种都对应一个分支
     *
     * 这条不变量钉住两件事：没有一个分支是死代码（每条检查都真的会被人看到），
     * 也没有一个状态会漏掉该问的东西（漏掉就意味着用户被卡在一个看不懂的屏幕上）。
     */
    @Test
    fun onlyProgressStatesAskNothingAndEveryBranchIsReachable() {
        assertEquals(
            setOf(
                OxidePreflightOperation.Idle,
                OxidePreflightOperation.Checking,
                OxidePreflightOperation.RealLaunch,
            ),
            OxidePreflightOperation.entries.filter { oxidePreflightBranchOf(it) == null }.toSet(),
        )
        assertEquals(
            OxidePreflightBranch.entries.toSet(),
            (
                OxidePreflightOperation.entries.mapNotNull { oxidePreflightBranchOf(it) } +
                        // RealLaunch 不来自任何 operation，只能由凭据那一段给出来
                        OxideAccountKind.entries.map { oxideTokenBranch(OxideTokenState.Fresh, it) }
                ).toSet(),
        )
    }
}