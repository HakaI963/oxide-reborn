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

import androidx.compose.runtime.Immutable
import dev.oxide.launcher.R
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformProject
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchData
import dev.oxide.launcher.game.download.assets.platform.PlatformSortField
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.cacheKey
import dev.oxide.launcher.game.download.assets.platform.curseforge.CurseForgePaging
import dev.oxide.launcher.game.download.assets.utils.ModTranslations
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.ui.androidText
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsPage
import dev.oxide.launcher.ui.screens.content.download.assets.elements.AssetsPaging

// ---------------------------------------------------------------------------
// 结果条目
// ---------------------------------------------------------------------------

/**
 * 一条搜索结果
 *
 * 字段在结果落地时取好，绘制时不再访问平台模型，也不再碰磁盘与网络。
 *
 * [classes] 是发起本次搜索时使用的类别：平台接口对一次搜索只返回这一类结果，
 * 所以它就是这条结果真实所属的类别，安装目录与详情标签都直接用它。
 */
internal data class DiscoverItem(
    val data: PlatformSearchData,
    val title: String,
    val classes: PlatformClasses,
    val loaderLabel: String?,
) {
    /**
     * 跨页去重用的稳定身份
     *
     * 必须是"平台 + 项目 ID"而不是标题或列表下标：标题会被改写、
     * 下标每页都从头开始，两者都会让同一个项目在两页里被当成两条。
     */
    val key: String get() = discoverProjectKey(data.platform(), data.platformId())
}

/** 项目在结果集里的稳定身份，跨页去重与选中态都用它 */
internal fun discoverProjectKey(platform: Platform, projectId: String): String =
    "${platform.name}/$projectId"

/**
 * 一页结果的最小表示
 *
 * 从平台那一层的 [AssetsPage] 里剥出来：只留下翻页要用的元数据与原始条目，
 * 因此分页的每一条规则都能在单元测试里直接构造 [DiscoverPage] 来断言，
 * 不必把平台的响应模型一起拉进来。
 *
 * @param index 服务端回显的这一页起始索引（语义是"跳过多少条"）
 * @param entries 这一页返回的原始条目（尚未去重）
 * @param serverLastPage 平台自己给出的"已经是最后一页"判定
 */
internal data class DiscoverPage(
    val index: Int,
    val entries: List<DiscoverEntry>,
    val serverLastPage: Boolean,
)

/**
 * 一条原始条目，配上按当前语言解析好的标题
 *
 * 标题在条目进入列表时就算好：绘制与去重都不再碰翻译表，也不再读 Context。
 */
internal data class DiscoverEntry(
    val data: PlatformSearchData,
    val title: String,
)

/**
 * 平台页 → 发现页的一页
 *
 * 平台的 [AssetsPage] 已经把分页算好了，这里只是换个形状：
 * `pageIndex` 是起始索引，`isLastPage` 来自 CurseForge 的 `resultCount/totalCount`
 * 或 Modrinth 的 `offset/total_hits`，两者都是服务端给出的真实分页信息。
 *
 * [titleOf] 由组合期递进来：本地化名称要走当前语言与 mcmod 译名，
 * 而翻译表只存在于界面这一侧。
 */
internal fun AssetsPage.toDiscoverPage(
    titleOf: (PlatformSearchData, ModTranslations.McMod?) -> String,
): DiscoverPage = DiscoverPage(
    index = pageIndex,
    entries = data.map { (data, mcmod) -> DiscoverEntry(data, titleOf(data, mcmod)) },
    serverLastPage = isLastPage,
)

/** 把一页的原始条目转成可绘制的条目 */
internal fun discoverItemsOf(
    page: DiscoverPage,
    classes: PlatformClasses,
): List<DiscoverItem> = page.entries.map { entry ->
    DiscoverItem(
        data = entry.data,
        title = entry.title,
        classes = classes,
        loaderLabel = entry.data.platformModLoaders()
            ?.firstOrNull()
            ?.getDisplayName()
            ?.takeIf { it.isNotBlank() },
    )
}

// ---------------------------------------------------------------------------
// 分页
// ---------------------------------------------------------------------------

/**
 * 发现页的分页规则
 *
 * 页大小与索引上限全部收敛到 CurseForge 服务端自己声明的契约（[CurseForgePaging]）：
 *
 * ```
 * PageSize must be between 1 and 50.
 * Index must be between 0 and 10000. / Index + PageSize cannot exceed 10000
 * ```
 *
 * 越界一律返回 HTTP 400，而搜索接口的 400 响应体是 RFC7807 错误对象，
 * 线上只能表现为"搜索失败"。所以这里根本不发越界请求：页大小先夹紧，
 * 到达上限之后诚实地把列表收尾并标出"没有更多了"。
 */
internal object DiscoverPaging {

    /** 一页取多少条，与资源搜索页保持一致 */
    const val PAGE_SIZE: Int = 20

    /** 服务端允许的最大 `index + pageSize` */
    const val RESULT_CAP: Int = CurseForgePaging.MAX_INDEX

    /** 离列表末尾多少条开始预取下一页；小半页足够覆盖一次快速滑动 */
    const val PREFETCH_THRESHOLD: Int = 8

    /** 单页条数，收敛到 1..50 */
    fun pageSize(requested: Int): Int = CurseForgePaging.pageSize(requested)

    /**
     * 当前页大小下最后一个合法的起始索引
     *
     * 不是 `RESULT_CAP`：服务端同时校验 `index + pageSize <= 10000`，
     * 所以合法索引的最大值是 `10000 - pageSize`。
     */
    fun maxIndex(size: Int): Int = CurseForgePaging.index(RESULT_CAP, pageSize(size))

    /**
     * 下一页的起始索引
     *
     * `index` 的语义是"跳过多少条"，所以下一页是当前索引加上一页大小（[AssetsPaging.nextIndex]），
     * 再按服务端上限收敛一次，越界值永远不会发出去。
     */
    fun nextIndex(currentIndex: Int, size: Int): Int {
        val clamped = pageSize(size)
        return CurseForgePaging.index(AssetsPaging.nextIndex(currentIndex, clamped), clamped)
    }

    /** 是否已经到达服务端上限，再翻一页就会拿到 400 */
    fun capReached(index: Int, size: Int): Boolean = index >= maxIndex(size)

    /**
     * 是否到达末尾
     *
     * 四条独立的依据，任何一条成立都算到底，**没有一条是"这一页失败了"**——
     * 失败是 [DiscoverFeedPhase.Failed]，绝不能落进这里变成空列表：
     *
     * 1. 平台自己说这是最后一页（服务端分页信息，最可靠）；
     * 2. 已经到达服务端索引上限，再请求就是 400；
     * 3. 这一页一条都没返回，继续翻只会拿到同样的空页；
     * 4. 这一页一条**新的**都没带来——结果集在按下载量/更新时间排序时会边翻边变，
     *    同一个项目可能连续两页都出现；这时继续翻拿到的仍然是已经看过的内容。
     */
    fun isEndOfResults(
        page: DiscoverPage,
        pageSize: Int,
        addedNewItems: Int,
    ): Boolean {
        if (page.serverLastPage) return true
        if (capReached(page.index, pageSize)) return true
        if (page.entries.isEmpty()) return true
        return addedNewItems <= 0
    }
}

/**
 * 什么时候该去取下一页
 *
 * 靠近末尾就取，而不是等用户滚到底：翻页是一次网络往返，等到底再发会让
 * 列表先停住再跳。已经在取、已经到末尾、还没出结果时都不取——
 * 否则同一个请求会被并发发好几遍。
 */
internal fun discoverShouldPrefetch(
    lastVisibleIndex: Int,
    visibleCount: Int,
    phase: DiscoverFeedPhase,
    endOfResults: Boolean,
): Boolean {
    if (visibleCount <= 0) return false
    if (endOfResults) return false
    if (phase == DiscoverFeedPhase.PagingNext) return false
    if (phase != DiscoverFeedPhase.Ready && phase != DiscoverFeedPhase.Failed) return false
    return lastVisibleIndex >= visibleCount - DiscoverPaging.PREFETCH_THRESHOLD
}

// ---------------------------------------------------------------------------
// 结果列表的状态
// ---------------------------------------------------------------------------

/**
 * 结果列表的阶段
 *
 * 这五种状态是**并列**的，不是同一个"没有结果"的不同写法：
 * 首次加载、正在翻下一页、确实没有结果、失败、没有更多。
 * 把失败并进空列表，就是让 CurseForge 看起来"搜不出东西"的那类 bug。
 */
internal enum class DiscoverFeedPhase {
    /** 还没发出任何请求 */
    Idle,

    /** 第一页正在加载，列表为空 */
    Loading,

    /** 结果可用，可能正在翻下一页，也可能已经到底 */
    Ready,

    /** 正在取下一页，已有的结果仍然可用 */
    PagingNext,

    /** 平台确实返回了零条结果 */
    Empty,

    /** 这一轮请求失败；是否还有旧结果取决于 [DiscoverFeed.items] */
    Failed,
}

/**
 * 结果区该怎么呈现自己
 *
 * 这是"失败不能变成空列表"这条要求的可测形式：
 * 只有 [DiscoverFeedPhase.Empty] 才允许显示"没有结果"，
 * 失败一律渲染成错误 + 重试（已经翻过几页时是"列表 + 重试"）。
 */
internal enum class DiscoverFeedView {
    Idle,
    Loading,
    List,
    LoadingNext,
    NoResults,
    Error,
    ListWithError,
}

internal fun DiscoverFeed.view(): DiscoverFeedView = when (phase) {
    DiscoverFeedPhase.Idle -> DiscoverFeedView.Idle
    DiscoverFeedPhase.Loading -> DiscoverFeedView.Loading
    DiscoverFeedPhase.Ready -> DiscoverFeedView.List
    DiscoverFeedPhase.PagingNext -> DiscoverFeedView.LoadingNext
    DiscoverFeedPhase.Empty -> DiscoverFeedView.NoResults
    DiscoverFeedPhase.Failed ->
        if (items.isEmpty()) DiscoverFeedView.Error else DiscoverFeedView.ListWithError
}

/**
 * 结果列表的全部状态
 *
 * 翻页的结果**累积**在这里而不是替换：无限滚动要的是一条越来越长的列表。
 * [index] 是"下一页要从哪里开始取"，[endOfResults] 是服务端侧的尽头，
 * 两者都来自真实响应，不来自本地猜测。
 */
internal data class DiscoverFeed(
    val phase: DiscoverFeedPhase = DiscoverFeedPhase.Idle,
    val items: List<DiscoverItem> = emptyList(),
    val index: Int = 0,
    val pageSize: Int = DiscoverPaging.PAGE_SIZE,
    val endOfResults: Boolean = false,
    /** 失败时的真实错误，来自 mapExceptionToMessage */
    val error: AndroidStringText? = null,
    /** 已经成功合并了几页 */
    val pages: Int = 0,
) {
    /** 是否还能再取一页 */
    fun canLoadMore(): Boolean =
        items.isNotEmpty() && !endOfResults && !DiscoverPaging.capReached(index, pageSize)

    /** 第一页：清空旧结果，进入加载中 */
    fun start(): DiscoverFeed = DiscoverFeed(phase = DiscoverFeedPhase.Loading, pageSize = pageSize)

    /** 下一页；已经在取或已经到底时返回 null，调用方据此跳过这次请求 */
    fun beginNextPage(): DiscoverFeed? =
        if (phase == DiscoverFeedPhase.PagingNext || !canLoadMore()) {
            null
        } else {
            copy(phase = DiscoverFeedPhase.PagingNext, error = null)
        }

    /**
     * 合并一页结果
     *
     * 跨页按 [DiscoverItem.key] 去重：结果集在翻页过程中会变化，
     * 同一个项目被算成两条会让用户看到一个装了两遍的列表。
     */
    fun append(
        page: DiscoverPage,
        classes: PlatformClasses,
    ): DiscoverFeed {
        val incoming = discoverItemsOf(page, classes)
        val merged = discoverAppendDistinct(items, incoming) { it.key }
        val added = merged.size - items.size
        return copy(
            phase = if (merged.isEmpty()) DiscoverFeedPhase.Empty else DiscoverFeedPhase.Ready,
            items = merged,
            index = page.index,
            endOfResults = DiscoverPaging.isEndOfResults(page, pageSize, added),
            error = null,
            pages = pages + 1,
        )
    }

    /** 第一页失败：没有旧结果可留，界面只能显示错误 + 重试 */
    fun failFirstPage(message: AndroidStringText): DiscoverFeed =
        DiscoverFeed(phase = DiscoverFeedPhase.Failed, pageSize = pageSize, error = message)

    /** 下一页失败：已经取到的结果仍然可用，只是不能再翻了 */
    fun failNextPage(message: AndroidStringText): DiscoverFeed =
        copy(phase = DiscoverFeedPhase.Failed, error = message)
}

/**
 * 把新一页接在已有结果后面，并按稳定身份去重
 *
 * 保留**首次**出现的位置：排序是平台给的，去掉后来的重复项正好让顺序稳定下来。
 */
internal fun <T> discoverAppendDistinct(
    existing: List<T>,
    incoming: List<T>,
    keyOf: (T) -> String,
): List<T> {
    if (incoming.isEmpty()) return existing
    val seen = HashSet<String>(existing.size + incoming.size)
    existing.forEach { seen.add(keyOf(it)) }
    return existing + incoming.filter { seen.add(keyOf(it)) }
}

// ---------------------------------------------------------------------------
// 重置分页的条件
// ---------------------------------------------------------------------------

/**
 * 决定"这是不是另一批结果"的条件全集
 *
 * 少一个字段就意味着换了这个条件之后旧页还留在列表里，
 * 于是列表里混着两批关键词不同的项目——所以这里列全，并由
 * [discoverResetReason] 逐条判定。
 */
internal data class DiscoverQuery(
    val platform: Platform,
    val classes: PlatformClasses,
    val searchName: String,
    val gameVersion: String,
    val modloader: PlatformDisplayLabel?,
    val sortField: PlatformSortField,
)

/** 分页为什么必须回到第一页 */
internal enum class DiscoverResetReason {
    /** 第一次搜索，还没有分页可重置 */
    FirstSearch,
    Provider,
    Category,
    SearchName,
    GameVersion,
    ModLoader,
    Sort,
}

/**
 * 这次搜索要不要重置分页
 *
 * 返回 null 表示条件没变，累计结果继续往下接；
 * 返回具体原因表示必须取消在途请求、清空结果、从 index 0 重新开始。
 * 逐条判定而不是一句 `previous != next`，是为了让每一条都能单独断言。
 */
internal fun discoverResetReason(
    previous: DiscoverQuery?,
    next: DiscoverQuery,
): DiscoverResetReason? = when {
    previous == null -> DiscoverResetReason.FirstSearch
    previous.platform != next.platform -> DiscoverResetReason.Provider
    previous.classes != next.classes -> DiscoverResetReason.Category
    previous.searchName != next.searchName -> DiscoverResetReason.SearchName
    previous.gameVersion != next.gameVersion -> DiscoverResetReason.GameVersion
    previous.modloader != next.modloader -> DiscoverResetReason.ModLoader
    previous.sortField != next.sortField -> DiscoverResetReason.Sort
    else -> null
}

// ---------------------------------------------------------------------------
// 依赖
// ---------------------------------------------------------------------------

/**
 * 这类依赖能不能被单独装上
 *
 * `EMBEDDED` 已经打包在这个文件里，再装一份就是同一个 mod 出现两次；
 * `INCOMPATIBLE` 是平台明确标注互斥，装上去必然冲突。
 * 这两种无论用户怎么点都不会进这一次安装。
 *
 * `INCLUDE` 与 `OPTIONAL` 一样处理：CurseForge 只标注了关系，没说它一定被包含，
 * 所以交给用户决定，但不默认勾选。
 */
internal fun PlatformDependencyType.discoverInstallable(): Boolean =
    this != PlatformDependencyType.EMBEDDED && this != PlatformDependencyType.INCOMPATIBLE

/**
 * 新发现的依赖默认勾选哪些
 *
 * 必装依赖缺了游戏就起不来，工具（配置器之类）也是玩法的必要部分，
 * 所以默认勾上；可选与包含类默认不勾，让用户自己决定要不要多下东西。
 */
internal fun PlatformDependencyType.discoverSelectedByDefault(): Boolean =
    this == PlatformDependencyType.REQUIRED || this == PlatformDependencyType.TOOL

/** 一个项目版本标注的依赖，取自平台返回的真实关系 */
internal data class DiscoverDependency(
    val platform: Platform,
    /** 只给了精确版本而没给项目时为 null，解析项目要靠平台接口 */
    val projectId: String?,
    /** 只指定了项目而没指定版本时为 null */
    val versionId: String?,
    val type: PlatformDependencyType,
) {
    /**
     * 去重与选择用的键
     *
     * 复用依赖下载链路自己的 [cacheKey]，两边对"同一个依赖"的判断必须一致，
     * 否则界面里勾了两条，安装时又被判成一条。
     */
    val key: String
        get() = PlatformVersion.PlatformDependency(platform, projectId, versionId, type).cacheKey()

    /** 是否可以被勾进这一次安装 */
    val installable: Boolean get() = type.discoverInstallable()

    /** 刚被发现时默认是否勾上 */
    val selectedByDefault: Boolean get() = type.discoverSelectedByDefault()
}

/**
 * 读出某个项目版本的全部依赖
 *
 * 平台把同一条关系重复给出时按 [DiscoverDependency.key] 去重：列表里的
 * 勾选状态与键一一对应，重复项会让用户改了一个另一个却没跟着变。
 */
internal fun discoverDependenciesOf(version: PlatformVersion): List<DiscoverDependency> =
    version.platformDependencies()
        .map { DiscoverDependency(it.platform, it.projectId, it.versionId, it.type) }
        .distinctBy { it.key }

/**
 * 一次最多处理多少个依赖
 *
 * 平台的依赖关系来自上传者的元数据，一个文件报出上百条关系是可能的。
 * 界面把这些关系逐条画出来并逐条解析，所以必须在这里先截断，
 * 否则确认界面与详情抽屉会被一份异常元数据撑爆。
 */
internal const val MAX_DISCOVER_DEPENDENCIES: Int = 64

/**
 * 读出一个文件版本的全部依赖，并按 [MAX_DISCOVER_DEPENDENCIES] 截断
 *
 * 与 [discoverDependenciesOf] 分开：去重是"平台重复给出同一条关系"这件事，
 * 截断是"这份元数据太大了"这件事，两条规则各自的断言不该绑在一起。
 */
internal fun discoverDependenciesCapped(version: PlatformVersion): List<DiscoverDependency> =
    discoverDependenciesOf(version).take(MAX_DISCOVER_DEPENDENCIES)

/** 刚打开确认界面时的默认勾选：必装与工具 */
internal fun discoverDefaultSelection(dependencies: List<DiscoverDependency>): Set<String> =
    dependencies
        .filter { it.installable && it.selectedByDefault }
        .mapTo(LinkedHashSet()) { it.key }

/** 勾选或取消一个依赖；不可安装的依赖不接受勾选 */
internal fun discoverToggleSelection(
    selected: Set<String>,
    dependency: DiscoverDependency,
    checked: Boolean,
): Set<String> {
    if (!dependency.installable) return selected
    return if (checked) selected + dependency.key else selected - dependency.key
}

/** 一个依赖当前的真实状态 */
internal sealed interface DiscoverDependencyState {
    /** 正在读这个依赖的版本列表 */
    data object Resolving : DiscoverDependencyState

    /** 找到了与所选 Minecraft 版本匹配的文件版本 */
    data class Resolved(val versionLabel: String, val gameVersions: String) :
        DiscoverDependencyState

    /** 这个依赖在所选 Minecraft 版本下没有可用文件，本次装不上 */
    data object NoCompatibleVersion : DiscoverDependencyState

    /** 读取失败，可以单独重试，不影响其他依赖 */
    data class Failed(val message: AndroidStringText) : DiscoverDependencyState
}

/** 是否可以只重试这一个依赖（有失败状态就说明还有值可读） */
internal fun DiscoverDependencyState.retryable(): Boolean = this is DiscoverDependencyState.Failed

// ---------------------------------------------------------------------------
// 依赖卡片：把依赖关系解析成平台上的真实项目
// ---------------------------------------------------------------------------

/**
 * 一次最多为多少个依赖项目读元数据
 *
 * 每读一个项目就是一次网络往返，而依赖关系是上传者写的，可以任意长。
 * 上限和 [MAX_DISCOVER_DEPENDENCIES] 一样来自确认界面的容量，而不是来自平台契约。
 * 超出上限的依赖仍然会被画出来，只是拿不到项目名——也因此它们**不会有**任何编造的名字。
 */
internal const val MAX_DISCOVER_DEPENDENCY_PROJECTS: Int = 24

/**
 * 一个依赖要读项目元数据时的身份
 *
 * 平台 + 项目 id，与 [discoverProjectKey] 同一个键，因此同一项目在一次解析里
 * 无论以哪种形式出现（只给项目 id、或附带一个精确版本 id）都落到同一个身份上。
 */
@Immutable
internal data class DiscoverDependencyTarget(
    val platform: Platform,
    val projectId: String,
) {
    val key: String get() = discoverProjectKey(platform, projectId)
}

/**
 * 这一批依赖要去读哪些项目的元数据
 *
 * 同一个项目会在一个文件的关系里出现多次——一条带精确版本 id 的 `REQUIRED`
 * 和一条只给项目 id 的 `OPTIONAL` 指向同一个项目，两条都要画，但**项目只有一份**。
 * 所以这里按 [DiscoverDependencyTarget.key] 去重并保留首次出现的顺序，
 * 再按 [MAX_DISCOVER_DEPENDENCY_PROJECTS] 截断。
 */
internal fun discoverProjectTargets(
    targets: List<DiscoverDependencyTarget>,
): List<DiscoverDependencyTarget> =
    targets.distinctBy { it.key }.take(MAX_DISCOVER_DEPENDENCY_PROJECTS)

/** 去重截断之后真正会去查的那些项目键 */
internal fun discoverProjectKeys(targets: List<DiscoverDependencyTarget>): Set<String> =
    targets.mapTo(LinkedHashSet()) { it.key }

/**
 * 一个依赖项目在平台上真实存在的那些字段
 *
 * 只收平台真的给出的字段：没给出就是 null，界面据此决定不画哪一行。
 * 名字尤其不能拿 id 顶替——`482378` 是编号，不是项目名，把它放在标题位
 * 就是"依赖面板只剩一串数字"这个缺陷本身。
 */
@Immutable
internal data class DiscoverDependencyProject(
    val platform: Platform,
    val projectId: String,
    /** 平台上的显示名；平台没给就是 null */
    val name: String?,
    /** 项目图标链接；平台没给就是 null */
    val iconUrl: String?,
    /** 作者；平台没给就是 null */
    val author: String?,
) {
    val key: String get() = discoverProjectKey(platform, projectId)

    /** 界面能不能拿这个项目名当标题 */
    val named: Boolean get() = !name.isNullOrBlank()
}

/**
 * 从平台项目读出卡片需要的字段
 *
 * 作者取 [PlatformProject.platformAuthors] 而不是 [PlatformProject.platformAuthor]：
 * 后者在 CurseForge 上就是 `authors[0].name`，作者列表为空时抛
 * ArrayIndexOutOfBoundsException——一个可有可无的字段不该把整张卡搞崩。
 *
 * 两个平台给的作者并不等价，这里如实照收，不做统一：
 *  - CurseForge `GET /v1/mods/{id}` 带 `authors[].name`，拿得到作者名；
 *  - Modrinth `GET /v2/project/{id}` 只有 `team` / `organization` 的 **id**，
 *    成员名字要另发一次 `GET /team/{id}/members`，启动器现在没有这条链路。
 *    所以 Modrinth 的卡片不画作者行，而不是把团队 id 冒充成作者。
 */
internal fun PlatformProject.toDiscoverDependencyProject(): DiscoverDependencyProject =
    DiscoverDependencyProject(
        platform = platform(),
        projectId = platformId(),
        name = platformTitle().trim().takeIf { it.isNotEmpty() },
        iconUrl = platformIconUrl()?.trim()?.takeIf { it.isNotEmpty() },
        author = platformAuthors().firstOrNull { it.isNotBlank() }?.trim()?.takeIf { it.isNotEmpty() },
    )

/**
 * 一个依赖项目元数据的读取结果
 *
 * 三种"没有"必须分开，因为它们对用户是完全不同的三件事：
 * 查了但平台报错、因为上限没查、以及依赖压根没给出项目 id。
 * 含糊成同一句，只会让人把"这份模组报了 200 条依赖"当成网络故障去重试。
 */
internal sealed interface DiscoverProjectOutcome {

    /** 读到了项目，卡片字段全在 [project] 里 */
    data class Resolved(val project: DiscoverDependencyProject) : DiscoverProjectOutcome

    /** 查了，但失败；[message] 是平台给的真实原因，可重试 */
    data class Failed(val message: AndroidStringText) : DiscoverProjectOutcome

    /** 超出 [MAX_DISCOVER_DEPENDENCY_PROJECTS] 上限，这一批没有去查 */
    data object Skipped : DiscoverProjectOutcome

    /** 依赖没有给出项目 id，平台上无从查起 */
    data object Unknown : DiscoverProjectOutcome
}

/**
 * 项目元数据没读到时的原因；读到了就是 null
 *
 * 平台给了原因就用平台给的，不另编一句；
 * 没查或无从查起也要有一句能说清状况的话——什么都不说看起来就像"这条依赖没有名字"。
 */
internal fun discoverProjectFailure(
    dependency: DiscoverDependency,
    outcome: DiscoverProjectOutcome,
): AndroidStringText? = when (outcome) {
    is DiscoverProjectOutcome.Resolved -> null
    is DiscoverProjectOutcome.Failed -> outcome.message
    DiscoverProjectOutcome.Skipped ->
        androidText(R.string.oxide_dis_dep_project_capped, MAX_DISCOVER_DEPENDENCY_PROJECTS)

    DiscoverProjectOutcome.Unknown ->
        androidText(R.string.oxide_dis_dep_project_unknown, dependency.platform.displayName)
}

/**
 * 一张依赖卡片上要画的东西
 *
 * 全部字段在解析完成时就算好，绘制与测量都不再访问平台模型，也不碰磁盘与网络。
 */
@Immutable
internal data class DiscoverDependencyCard(
    /** 勾选与重试用它，与依赖下载链路对"同一个依赖"的判断一致 */
    val key: String,
    /** 平台上的项目名；解析不出来时为 null */
    val name: String?,
    val iconUrl: String?,
    /** 平台没给作者时为 null，界面据此不画作者行 */
    val author: String?,
    val type: PlatformDependencyType,
    val provider: Platform,
    /** 与目标 Minecraft 版本匹配的那个文件版本号 */
    val versionLabel: String?,
    /** 这个文件支持的 Minecraft 版本 */
    val gameVersion: String?,
    /** 这个文件支持的加载器 */
    val loader: String?,
    /** 平台给的项目 id 或版本 id，永远只是次要信息 */
    val idDetail: String?,
    val state: DiscoverDependencyState,
    /** 是否可以被勾进这一次安装 */
    val installable: Boolean,
) {
    /** 界面上能不能拿这个项目名当标题 */
    val named: Boolean get() = !name.isNullOrBlank()

    /** 失败才给这一条单独的重试 */
    val retryable: Boolean get() = state.retryable()

    /**
     * 要不要把平台 id 作为次要信息露出来
     *
     * 只在解析不出名字、或者这一条读失败时才露：这两种情况下它是用户唯一能拿去
     * 搜索和反馈的线索。有名字的时候再挂一行编号纯属噪音。
     * 它永远只是"Project 482378"这样的一行 faint 次要信息，不会被当成标题。
     */
    val showIdDetail: Boolean
        get() = idDetail != null && (!named || state is DiscoverDependencyState.Failed)
}

/**
 * 把"关系本身 + 读到的项目 + 读到的文件 + 读取状态"收成一张卡
 *
 * 字段选择规则逐条钉死，每一条都有单测：
 *  - 名字只来自项目的平台标题，**绝不**退回项目 id 或版本 id；
 *  - 作者只来自平台真的给出的那一个，没有就不画作者行；
 *  - 版本号取 [PlatformVersion.platformVersion]，平台没给才退回文件自己的显示名；
 *  - Minecraft 版本优先报目标版本——这个文件被选中正是因为它支持那一档；
 *    没有目标才报文件自己声明的第一个；目标非空而文件并不支持时什么都不报，
 *    因为那时候报哪一个都是假的；
 *  - 加载器取文件标注的第一个非空显示名，没有标注就是 null 而不是空串。
 *
 * 项目没读到时整条落成 [DiscoverDependencyState.Failed]：
 * 版本解析本身先失败的，保留它更具体的原因。
 */
internal fun discoverDependencyCard(
    dependency: DiscoverDependency,
    outcome: DiscoverProjectOutcome,
    version: PlatformVersion?,
    state: DiscoverDependencyState,
    targetGameVersion: String? = null,
): DiscoverDependencyCard {
    val failure = discoverProjectFailure(dependency, outcome)
    val finalState = when {
        failure == null -> state
        state is DiscoverDependencyState.Failed -> state
        else -> DiscoverDependencyState.Failed(failure)
    }
    val project = (outcome as? DiscoverProjectOutcome.Resolved)?.project
    return DiscoverDependencyCard(
        key = dependency.key,
        name = project?.name?.takeIf { it.isNotBlank() },
        iconUrl = project?.iconUrl?.takeIf { it.isNotBlank() },
        author = project?.author?.takeIf { it.isNotBlank() },
        type = dependency.type,
        provider = dependency.platform,
        versionLabel = version?.let { discoverVersionLabel(it) },
        gameVersion = version?.let { discoverGameVersion(it, targetGameVersion) },
        loader = version?.let { discoverLoaderLabel(it) },
        idDetail = dependency.projectId?.takeIf { it.isNotBlank() }
            ?: dependency.versionId?.takeIf { it.isNotBlank() },
        state = finalState,
        installable = dependency.installable,
    )
}

/** 文件版本号；平台没给版本号时才退回这个文件自己的显示名 */
internal fun discoverVersionLabel(version: PlatformVersion): String? =
    version.platformVersion().trim().takeIf { it.isNotEmpty() }
        ?: version.platformDisplayName().trim().takeIf { it.isNotEmpty() }

/**
 * 这个文件对应的 Minecraft 版本
 *
 * [target] 是用户选中的那一档，也就是这次安装的目标。文件被挑出来正是因为它支持那一档，
 * 所以目标在文件声明的支持列表里时直接报目标。
 */
internal fun discoverGameVersion(version: PlatformVersion, target: String?): String? {
    val supported = version.platformGameVersion().filter { it.isNotBlank() }
    val wanted = target?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        wanted != null && wanted in supported -> wanted
        // 目标与文件自相矛盾时不报任何版本：报哪个都是假的
        wanted != null -> null
        else -> supported.firstOrNull()
    }
}

/** 文件标注的第一个加载器；没有标注就是 null，界面据此不画加载器 */
internal fun discoverLoaderLabel(version: PlatformVersion): String? =
    version.platformLoaders().firstNotNullOfOrNull { loader ->
        loader.getDisplayName().trim().takeIf { it.isNotEmpty() }
    }

/**
 * 依赖项目元数据的会话内缓存
 *
 * 用户反复打开确认层与详情抽屉、或者在两个目标 Minecraft 版本之间来回切换时，
 * 同一批项目会被一遍遍查。这里按 `平台 + 项目 id` 记住已经读到的 [PlatformProject]，
 * 第二次直接复用，重复解析因此不会变成重复网络往返。
 *
 * **只缓存成功的读取**：失败不写进去，否则用户点"重试"拿到的还是上一次那个错，
 * 看起来就像重试根本没用。
 *
 * 启动器现成的两处缓存都不适用于这里，也没有第三处可复用：
 * [dev.oxide.launcher.game.download.assets.favorites.FavoriteProjectsRepository] 是收藏的
 * 持久化存储，把临时查到的依赖写进去等于凭空造出收藏项；
 * [dev.oxide.launcher.game.version.mod.ModFingerprints] 按文件绝对路径做键，与项目 id 无关。
 * 所以这里是发现页自己的一份有上限的小表，容量用满就按 LRU 挤掉最久没用的那个。
 */
internal class DiscoverProjectCache(
    private val capacity: Int = DISCOVER_PROJECT_CACHE_CAPACITY,
) {
    // accessOrder = true：get 也算一次访问，于是反复用到的项目不会被挤出去
    private val entries = LinkedHashMap<String, PlatformProject>(16, 0.75f, true)

    /** 已经读到的项目；没读过或读过失败都返回 null */
    operator fun get(key: String): PlatformProject? = synchronized(entries) { entries[key] }

    /** 记住一次成功的读取 */
    operator fun set(key: String, project: PlatformProject) {
        val limit = capacity.coerceAtLeast(1)
        synchronized(entries) {
            entries[key] = project
            while (entries.size > limit) {
                val oldest = entries.entries.firstOrNull()?.key ?: break
                entries.remove(oldest)
            }
        }
    }

    val size: Int get() = synchronized(entries) { entries.size }

    fun clear() = synchronized(entries) { entries.clear() }
}

/** 会话内缓存最多记住多少个项目 */
internal const val DISCOVER_PROJECT_CACHE_CAPACITY: Int = 64

// ---------------------------------------------------------------------------
// 安装前确认
// ---------------------------------------------------------------------------

/**
 * 哪一类内容会先弹"安装前确认"
 *
 * 只有模组与整合包需要：模组要决定装到哪个 Minecraft 版本、要带哪些依赖，
 * 那个版本还没装的话还要连游戏版本一起装（并让用户确认）；整合包要决定
 * 新实例叫什么，并由安装器自己带上游戏版本与加载器。
 *
 * 光影、资源包、存档、地图是整包放进某个实例的固定目录，
 * 没有 Minecraft 版本与依赖的概念，所以仍然直接安装，不弹这一层。
 */
internal fun discoverUsesInstallSheet(classes: PlatformClasses): Boolean =
    classes == PlatformClasses.MOD || classes == PlatformClasses.MOD_PACK

/**
 * 要装到哪个 Minecraft 版本
 *
 * @param detected 是否仍然是自动检测出来的那个（用户改过就是 false）
 */
internal data class DiscoverVersionChoice(
    val name: String?,
    val detected: Boolean,
)

/**
 * 解析目标 Minecraft 版本
 *
 * 默认自动检测当前选中实例的版本，用户在确认界面改过之后就用他改的那个；
 * 两者都空才是真的没有目标（界面上会引导去选实例）。
 */
internal fun discoverResolveVersion(chosen: String?, detected: String?): DiscoverVersionChoice {
    val picked = chosen?.takeIf { it.isNotBlank() } ?: detected?.takeIf { it.isNotBlank() }
    return DiscoverVersionChoice(
        name = picked,
        detected = picked != null && picked == detected,
    )
}

/** 这个 Minecraft 版本本地还没装，需要连游戏版本一起装 */
internal fun discoverNeedsGameVersion(name: String?, installed: Set<String>): Boolean =
    name != null && name !in installed

/**
 * 一次安装的完整计划
 *
 * 界面上确认过的所有选择收成这一个值，之后的下载只按它执行，
 * 不会再临时改主意。
 */
internal data class DiscoverInstallPlan(
    /** 目标 Minecraft 版本；整合包自带游戏版本，因此为 null */
    val targetVersionName: String?,
    /** 目标版本尚未安装，需要连同游戏版本一起装 */
    val installGameVersion: Boolean,
    /** 一并安装的依赖键，顺序与界面上一致 */
    val dependencyKeys: List<String>,
    /** 整合包要创建的实例名 */
    val instanceName: String?,
) {
    val hasTarget: Boolean get() = targetVersionName != null
    val hasDependencies: Boolean get() = dependencyKeys.isNotEmpty()
}

/**
 * 把确认界面上的选择汇总成一步执行的安装计划
 *
 * 整合包单独处理：它由安装器装成一个新实例（自带游戏版本与加载器），
 * 所以既没有目标 Minecraft 版本，也不能把依赖塞进目标实例——
 * [PlatformClasses] 里整合包对应的目录是 `NONE`，依赖下载链路会直接拒收。
 *
 * 模组则相反：目标是某个已存在的实例，依赖按所选游戏版本一起装进去；
 * 目标版本本地不存在时把 [DiscoverInstallPlan.installGameVersion] 置为 true，
 * 界面上据此问用户要不要连游戏版本一起装。
 */
internal fun discoverBuildPlan(
    classes: PlatformClasses,
    detectedVersion: String?,
    chosenVersion: String?,
    installedVersions: Set<String>,
    dependencies: List<DiscoverDependency>,
    selectedDependencyKeys: Set<String>,
    instanceName: String? = null,
): DiscoverInstallPlan {
    if (classes == PlatformClasses.MOD_PACK) {
        return DiscoverInstallPlan(
            targetVersionName = null,
            installGameVersion = false,
            dependencyKeys = emptyList(),
            instanceName = instanceName?.takeIf { it.isNotBlank() },
        )
    }

    val choice = discoverResolveVersion(chosenVersion, detectedVersion)
    return DiscoverInstallPlan(
        targetVersionName = choice.name,
        installGameVersion = discoverNeedsGameVersion(choice.name, installedVersions),
        dependencyKeys = dependencies
            .filter { it.installable && it.key in selectedDependencyKeys }
            .map { it.key },
        instanceName = null,
    )
}

// ---------------------------------------------------------------------------
// 安装目标
// ---------------------------------------------------------------------------

/**
 * 一个已存在的实例作为安装目标时提供的真实信息
 *
 * [minecraftVersion] **只**取版本 JSON 里解析出来的 Minecraft 版本，绝不是实例名。
 * 实例名是版本文件夹的名字：自定义名、整合包名、加载器 id（`fabric-loader-0.16.9-1.20.4`）
 * 都可能是它。把这一串拿去 Minecraft 版本的位置上，平台上必然一个文件都匹配不上，
 * 用户看到的就是"这个项目不支持 MyModPack 1.2"这种自己造出来的理由。
 *
 * 解析不出 Minecraft 版本时它是 null，意思是"还不知道"——不是"拿实例名顶上"。
 */
@Immutable
internal data class DiscoverInstanceTarget(
    /** 实例名，也是 `VersionsManager.versions` 里的键 */
    val name: String,
    /** 实例真实的 Minecraft 版本；版本 JSON 解析不出来时为 null */
    val minecraftVersion: String?,
    /** 实例自己的模组加载器显示名；没有加载器就是空集 */
    val loaders: Set<String> = emptySet(),
)

/** 这次安装最终落到哪里 */
@Immutable
internal data class DiscoverTarget(
    /** 目标实例名；目标是版本表里还没装的条目时为 null */
    val instanceName: String?,
    /** 目标 Minecraft 版本，平台上就是拿它筛兼容文件 */
    val minecraftVersion: String?,
    /** 目标自己的加载器；目标是纯版本（没有实例）时是空集 */
    val loaders: Set<String>,
    /** 目标本地是不是已经有；false 时确认层要问用户要不要连游戏版本一起装 */
    val installed: Boolean,
    /** 目标仍然就是当前选中的实例（用户没有改过） */
    val detected: Boolean,
) {
    /** 既没有实例也没有版本，才是真正的"没有目标" */
    val hasTarget: Boolean get() = instanceName != null || minecraftVersion != null
}

/** 什么都没选时用的空目标 */
internal val EMPTY_DISCOVER_TARGET: DiscoverTarget =
    DiscoverTarget(instanceName = null, minecraftVersion = null, loaders = emptySet(), installed = false, detected = false)

/**
 * 决定这一次安装的目标
 *
 * 优先级只有一条，且逐级降级：
 *
 *  1. **用户明确选中的目标实例**（[selectedInstance]）——用户点名要装进哪个实例；
 *  2. **用户在确认层里选的版本**（[pickedVersionName]）——选中的是版本表里的一条，
 *     本地还没有对应实例，因此目标就是一个纯 Minecraft 版本，确认层会连游戏版本一起装；
 *  3. **当前选中的可玩实例**（[playableInstance]）——一个字节都没选时的默认值。
 *
 * 模组/整合包自己的文件版本（[pinnedProjectVersion]）**永远不参与**：
 * 它描述的是"这个文件属于哪一档 Minecraft 版本"，不是"装到哪一档"。
 * 参数保留在这里，是为了让这条规则被单元测试钉死，而不是靠约定——
 * 曾经的缺陷正是把模组自己的文件版本当成了目标游戏版本。
 *
 * [detected] 只在目标仍然是当前选中的那个实例时为 true：用户重新选中同一个实例，
 * 界面仍然应该说"跟着选中实例"，而不是"改成了别的"。
 */
@Suppress("UNUSED_PARAMETER")
internal fun discoverResolveTarget(
    selectedInstance: DiscoverInstanceTarget?,
    pickedVersionName: String?,
    playableInstance: DiscoverInstanceTarget?,
    installedInstances: List<DiscoverInstanceTarget> = emptyList(),
    pinnedProjectVersion: String? = null,
): DiscoverTarget {
    val picked = pickedVersionName?.takeIf { it.isNotBlank() }

    // 用户选中的那条本身就是一个已存在的实例：它连同自己的 Minecraft 版本与加载器一起生效
    val chosen = selectedInstance
        ?: picked?.let { name -> installedInstances.firstOrNull { it.name == name } }

    if (chosen != null) {
        return DiscoverTarget(
            instanceName = chosen.name,
            minecraftVersion = chosen.minecraftVersion,
            loaders = chosen.loaders,
            installed = true,
            detected = chosen.name == playableInstance?.name,
        )
    }

    // 选中的是版本表里的一条：本地还没有这个实例，目标就是这一档 Minecraft 版本本身
    if (picked != null) {
        return DiscoverTarget(
            instanceName = null,
            minecraftVersion = picked,
            loaders = emptySet(),
            installed = false,
            detected = false,
        )
    }

    val playable = playableInstance ?: return EMPTY_DISCOVER_TARGET
    return DiscoverTarget(
        instanceName = playable.name,
        minecraftVersion = playable.minecraftVersion,
        loaders = playable.loaders,
        installed = true,
        detected = true,
    )
}

/** 一个文件版本在平台上标注的 Minecraft 版本与加载器 */
internal data class DiscoverFileFacts(
    /** 这个文件支持的所有 Minecraft 版本 */
    val gameVersions: List<String>,
    /** 这个文件标注的加载器显示名；没标注就是空列表（原版文件） */
    val loaders: List<String>,
)

/**
 * 从已经按发布时间倒序排好的文件版本里，挑出目标真正装得上的那一个
 *
 * 规则与依赖下载链路自己的 `isCompatibleWith` 一致，所以界面上显示的文件
 * 就是会被装上的那个文件：
 *
 *  - [minecraftVersion] 为 null 表示"还不知道目标版本"，此时不按 Minecraft 版本过滤；
 *    但**绝不会**用别的东西（实例名、模组自己的版本）来填这个位置；
 *  - 目标实例带加载器时，文件必须标注其中之一（任一匹配即可）；
 *  - 目标实例没有加载器（纯原版）时，平台的加载器标注一律放行——
 *    与下载链路一致，否则这里挑中的文件到了下载那一步会被判成不兼容。
 *
 * 一个都匹配不上时返回 null，交给调用方决定是换一个目标版本还是如实报"这个版本没有文件"。
 */
internal fun <T> discoverCompatibleFile(
    versions: List<T>,
    minecraftVersion: String?,
    targetLoaders: Set<String>,
    factsOf: (T) -> DiscoverFileFacts,
): T? {
    val wanted = minecraftVersion?.trim()?.takeIf { it.isNotEmpty() }
    val loaders = targetLoaders.filter { it.isNotBlank() }.toSet()
    return versions.firstOrNull { version ->
        val facts = factsOf(version)
        val mcMatches = wanted == null || facts.gameVersions.any { it.equals(wanted, ignoreCase = true) }
        val loaderMatches = loaders.isEmpty() ||
                facts.loaders.any { fileLoader -> loaders.any { it.equals(fileLoader, ignoreCase = true) } }
        mcMatches && loaderMatches
    }
}