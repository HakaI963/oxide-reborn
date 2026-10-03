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

/**
 * 实例页里与 Compose、磁盘和 Android 都无关的纯逻辑
 *
 * 之所以把它们单独抽出来，是因为这四件事恰恰是最容易改坏、又最不该靠肉眼看的地方：
 * 选中态会不会改变卡片的几何、当前实例落在哪一个、齿轮菜单一次交互后的状态、
 * 以及网格行高会不会把卡片内容裁掉。它们都可以在没有 Android 的单元测试里钉死。
 *
 * 这个文件刻意不 import 任何 Compose 的东西，测试因此只需要 JUnit。
 */

// ---- 选中态 ----------------------------------------------------------------

/** 卡片怎么表示"这就是当前实例" */
enum class InstanceSelectedIndicator {
    /** 不画任何东西 */
    None,

    /** 在一个固定槽位里画一个小圆点 */
    Dot,
}

/**
 * 一次选中在卡片上产生的**全部**差异
 *
 * 关键约束：除了 [indicator] 与 [announceAsCurrent]，其余字段在选中与未选中时
 * 必须逐字相同。参考稿是靠 `position:absolute` 的浮层加 `box-shadow` 来标记
 * 当前实例的，两者都不参与布局；一旦改用"给选中的卡片加一层描边、再塞一个
 * SELECTED 标签"，卡片就会长高一截、描边也变粗，整张网格因此被顶歪。
 * 这里把这三个几何量显式记下来，[geometryStable] 就是给它们设的护栏。
 */
data class InstanceSelectionPresentation(
    /** 唯一允许随选中变化的视觉量 */
    val indicator: InstanceSelectedIndicator,
    /** 是否用语义把"当前实例"念给读屏软件 */
    val announceAsCurrent: Boolean,
    /** 指示槽位的宽度，选中与否都一样 */
    val indicatorSlotWidthDp: Float,
    /** 顶部额外留白，必须恒为 0 */
    val extraTopInsetDp: Float,
    /** 额外描边宽度，必须恒为 0 */
    val extraBorderDp: Float,
    /** 徽章行最多占几个槽位，选中与否都一样 */
    val badgeSlots: Int,
) {
    /** 选中只改表面与描边的浓淡，不改任何几何 */
    val geometryStable: Boolean
        get() = extraTopInsetDp == 0f && extraBorderDp == 0f
}

/** 指示槽位的宽度，和参考稿的间距一致；所有卡片都留这么宽 */
private const val INDICATOR_SLOT_DP = 5f

/** 参考稿的徽章行最多两个，因此徽章行的高度对所有卡片都一样 */
private const val BADGE_SLOTS = 2

/** 选中态的全部呈现差异 */
fun instanceSelectionPresentation(selected: Boolean): InstanceSelectionPresentation =
    InstanceSelectionPresentation(
        indicator = if (selected) InstanceSelectedIndicator.Dot else InstanceSelectedIndicator.None,
        announceAsCurrent = selected,
        indicatorSlotWidthDp = INDICATOR_SLOT_DP,
        extraTopInsetDp = 0f,
        extraBorderDp = 0f,
        badgeSlots = BADGE_SLOTS,
    )

/**
 * 决定高亮落在哪一个实例上
 *
 * 顺序是：用户刚点过的那个 → 后端记录的当前版本（`VersionsManager.currentVersion`，
 * 它来自 current.json，重启之后仍然是同一个） → 列表里第一个真实存在的版本。
 *
 * 这样安排是为了两个真实场景：装完一个新版本之后列表会变，用户点的那个如果已经不在了
 * 就自动落回后端记录；而第一次打开页面时后端记录才是唯一可信的落点。
 */
fun resolveActiveInstance(
    pickedKey: String?,
    installedNames: List<String>,
    backendCurrentName: String?,
): String? {
    if (installedNames.isEmpty()) return null
    return pickedKey?.takeIf { it in installedNames }
        ?: backendCurrentName?.takeIf { it in installedNames }
        ?: installedNames.first()
}

// ---- 卡片上能显示什么 -------------------------------------------------------

/** 卡片统计块能显示的两种真实量 */
enum class InstanceStatKind {
    LastPlayed,
    Memory,
}

/**
 * 统计块只放真实读到的量
 *
 * 上次运行时间必须大于 0——还没有日志时这一行整个不出现，而不是显示"从未"，
 * 更不能显示"0 分钟前"；内存必须是正数——版本配置里没写过时读出来是 0，
 * 那代表"跟随全局设置"，不是"这个实例分到了 0 MB"。
 */
fun instanceCardStats(lastRunAtMillis: Long, ramMb: Int): List<InstanceStatKind> = buildList {
    if (lastRunAtMillis > 0L) add(InstanceStatKind.LastPlayed)
    if (ramMb > 0) add(InstanceStatKind.Memory)
}

/** 卡片徽章 */
enum class InstanceBadgeKind {
    Invalid,
    Mods,
    Pinned,
    Isolated,
    Shared,
}

/** 徽章背后的真实状态 */
data class InstanceBadgeFacts(
    val valid: Boolean,
    val isolated: Boolean,
    val pinned: Boolean,
    val modsCount: Int,
)

/**
 * 徽章的取值与顺序
 *
 * 无效排最前，因为它决定这张卡片能不能播放；接着是模组数与置顶这两个最常看的；
 * 隔离方式排在最后，并且在配置抽屉里还有一行完整的说明可以看。
 */
fun instanceCardBadges(facts: InstanceBadgeFacts): List<InstanceBadgeKind> = buildList {
    if (!facts.valid) add(InstanceBadgeKind.Invalid)
    if (facts.modsCount > 0) add(InstanceBadgeKind.Mods)
    if (facts.pinned) add(InstanceBadgeKind.Pinned)
    add(if (facts.isolated) InstanceBadgeKind.Isolated else InstanceBadgeKind.Shared)
}

// ---- 齿轮动作菜单 -----------------------------------------------------------

/** 齿轮菜单里能触发的真实动作 */
enum class InstanceAction {
    Configure,
    Rename,
    Copy,
    ExportModPack,
    SetPinned,
    ClearPinned,
    OpenFolder,
    Delete,
}

/** 一次齿轮交互想做的事 */
enum class InstanceMenuAction {
    /** 点同一个齿轮就收起，点别的齿轮就换成那一张 */
    Toggle,

    /** 直接展开某一张 */
    Open,

    /** 全部收起 */
    Close,
}

/**
 * 齿轮菜单状态机
 *
 * 同一时刻最多只有一张卡片展开菜单：在别处展开会把上一张收起来，
 * 于是屏幕上不会出现两片浮层互相压住、也分不清当前属于哪张卡片。
 */
fun reduceInstanceMenu(openKey: String?, key: String, action: InstanceMenuAction): String? =
    when (action) {
        InstanceMenuAction.Open -> key
        InstanceMenuAction.Toggle -> if (openKey == key) null else key
        InstanceMenuAction.Close -> null
    }

/**
 * 这张卡片的齿轮菜单里有哪些项
 *
 * 重命名、复制与导出都依赖版本本身还完整，因此跟着 [usable] 走；
 * 删除永远在——它是清理一个已经失效的版本目录的唯一途径；
 * 置顶读的是实例真实的置顶态，标题也随之在 Pin 与 Unpin 之间切换。
 */
fun instanceMenuActions(usable: Boolean, pinned: Boolean): List<InstanceAction> = buildList {
    add(InstanceAction.Configure)
    if (usable) {
        add(InstanceAction.Rename)
        add(InstanceAction.Copy)
        add(InstanceAction.ExportModPack)
    }
    add(if (pinned) InstanceAction.ClearPinned else InstanceAction.SetPinned)
    add(InstanceAction.OpenFolder)
    add(InstanceAction.Delete)
}

// ---- 网格 ------------------------------------------------------------------

/**
 * 网格的行高
 *
 * 参考稿是两行网格，因此先把实测剩余高度按行平分；窗口太矮时退到一个由卡片
 * 真实内容算出来的下限——宁可让网格开始滚动，也不把卡片内容裁掉半行。
 */
fun instanceRowHeight(
    availableHeightDp: Float,
    cardGapDp: Float,
    rows: Int,
    minRowHeightDp: Float,
): Float {
    if (rows <= 0) return minRowHeightDp.coerceAtLeast(0f)
    val gap = cardGapDp.coerceAtLeast(0f)
    val share = (availableHeightDp - gap * (rows - 1)) / rows
    val floor = minRowHeightDp.coerceAtLeast(0f)
    return if (share > floor) share else floor
}
