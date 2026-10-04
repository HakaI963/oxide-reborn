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

import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.download.assets.platform.PlatformClasses
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformFilterCode
import dev.oxide.launcher.game.download.assets.platform.PlatformSearchData
import dev.oxide.launcher.game.download.assets.platform.PlatformSortField
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import dev.oxide.launcher.ui.AndroidStringText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发现页翻页的单测
 *
 * 真机上的症状是"翻到大约 20 条就不动了"，所以这里把三件事钉死：
 * 下一页的起始索引怎么算、服务端上限在哪里、以及**失败绝不能变成空列表**。
 */
class OxideDiscoverPagingTest {

    // ---- 起始索引 ---------------------------------------------------------

    @Test
    fun theFirstPageStartsAtZero() {
        // index 的语义是"跳过多少条"，第 1 页必须是 0
        assertEquals(0, DiscoverFeed().index)
        assertEquals(0, DiscoverFeed().start().index)
        assertEquals(DiscoverPaging.PAGE_SIZE, DiscoverPaging.nextIndex(0, DiscoverPaging.PAGE_SIZE))
    }

    @Test
    fun theIndexAdvancesByOnePageEachTime() {
        var index = 0
        listOf(20, 40, 60, 80, 100).forEach { expected ->
            index = DiscoverPaging.nextIndex(index, DiscoverPaging.PAGE_SIZE)
            assertEquals(expected, index)
        }
    }

    /** index 的语义是"跳过多少条"，所以它必须一直落在整页的倍数上 */
    @Test
    fun theIndexAlwaysLandsOnAPageBoundary() {
        var index = 0
        repeat(50) {
            index = DiscoverPaging.nextIndex(index, DiscoverPaging.PAGE_SIZE)
            assertEquals(0, index % DiscoverPaging.PAGE_SIZE)
        }
    }

    // ---- 服务端上限 --------------------------------------------------------

    /**
     * pageSize 越界一律 400，因此发出去的一定在 1..50
     */
    @Test
    fun thePageSizeIsClampedIntoTheServerRange() {
        assertEquals(1, DiscoverPaging.pageSize(0))
        assertEquals(1, DiscoverPaging.pageSize(-7))
        assertEquals(20, DiscoverPaging.pageSize(20))
        assertEquals(50, DiscoverPaging.pageSize(50))
        assertEquals(50, DiscoverPaging.pageSize(100))
    }

    /**
     * 服务端同时校验 `index + pageSize <= 10000`，所以最后合法的索引是 `10000 - pageSize`，
     * 而不是 10000 本身
     */
    @Test
    fun theLastLegalIndexAccountsForThePageSize() {
        assertEquals(9980, DiscoverPaging.maxIndex(20))
        assertEquals(9950, DiscoverPaging.maxIndex(50))
        assertEquals(9999, DiscoverPaging.maxIndex(1))
    }

    @Test
    fun theCapIsReachedExactlyAtTheLastLegalIndex() {
        assertFalse(DiscoverPaging.capReached(9960, 20))
        assertTrue(DiscoverPaging.capReached(9980, 20))
    }

    /** 越界索引绝不会被发出去：每一步都要满足 `index + pageSize <= 10000` */
    @Test
    fun noRequestedPageCanEverExceedTheServerCap() {
        listOf(1, 20, 50).forEach { size ->
            var index = 0
            var steps = 0
            while (!DiscoverPaging.capReached(index, size) && steps < 20_000) {
                index = DiscoverPaging.nextIndex(index, size)
                assertTrue("index=$index size=$size", index >= 0)
                assertTrue(
                    "index=$index size=$size",
                    index.toLong() + DiscoverPaging.pageSize(size) <= DiscoverPaging.RESULT_CAP
                )
                steps++
            }
            // 走到这一步一定已经停在上限上，不会无限增长
            assertTrue("size=$size stopped at $index", DiscoverPaging.capReached(index, size))
            assertTrue("size=$size never converged", steps < 20_000)
        }
    }

    // ---- 到达末尾 ---------------------------------------------------------

    @Test
    fun aPageTheServerCallsTheLastOneEndsTheList() {
        assertTrue(
            DiscoverPaging.isEndOfResults(
                page = page(index = 40, ids = (0 until 20).map { "m$it" }, lastPage = true),
                pageSize = 20,
                addedNewItems = 20,
            )
        )
    }

    @Test
    fun anEmptyPageEndsTheList() {
        assertTrue(
            DiscoverPaging.isEndOfResults(
                page = page(index = 40, ids = emptyList(), lastPage = false),
                pageSize = 20,
                addedNewItems = 0,
            )
        )
    }

    /**
     * 结果集会边翻边变：同一项目可能连续两页都出现。
     * 这一页一条新的都没有，就说明再翻也是已经看过的内容，必须收尾。
     */
    @Test
    fun aPageThatAddsNothingNewEndsTheList() {
        val already = (0 until 20).map { "m$it" }
        assertTrue(
            DiscoverPaging.isEndOfResults(
                page = page(index = 20, ids = already, lastPage = false),
                pageSize = 20,
                addedNewItems = 0,
            )
        )
    }

    @Test
    fun reachingTheServerCapEndsTheListEvenWhenThePageIsFull() {
        assertTrue(
            DiscoverPaging.isEndOfResults(
                page = page(index = 9980, ids = (0 until 20).map { "m$it" }, lastPage = false),
                pageSize = 20,
                addedNewItems = 20,
            )
        )
    }

    @Test
    fun aFullPageWithSomethingNewKeepsGoing() {
        assertFalse(
            DiscoverPaging.isEndOfResults(
                page = page(index = 20, ids = (0 until 20).map { "m$it" }, lastPage = false),
                pageSize = 20,
                addedNewItems = 20,
            )
        )
    }

    // ---- 累积与去重 --------------------------------------------------------

    @Test
    fun pagesAccumulateInsteadOfReplacing() {
        var feed = DiscoverFeed().start()
        assertEquals(DiscoverFeedPhase.Loading, feed.phase)

        feed = feed.append(page(0, (0 until 20).map { "m$it" }), PlatformClasses.MOD)
        assertEquals(20, feed.items.size)
        assertEquals(DiscoverFeedPhase.Ready, feed.phase)
        assertEquals(1, feed.pages)
        assertFalse(feed.endOfResults)

        feed = feed.append(page(20, (20 until 40).map { "m$it" }), PlatformClasses.MOD)
        assertEquals(40, feed.items.size)
        assertEquals(2, feed.pages)
        assertEquals(20, feed.index)
        assertTrue(feed.canLoadMore())
    }

    /** 同一个项目不能出现两次，哪怕它被平台在两页里各发了一次 */
    @Test
    fun theSameProjectIsNeverListedTwice() {
        var feed = DiscoverFeed().start()
        feed = feed.append(page(0, listOf("a", "b", "c")), PlatformClasses.MOD)
        feed = feed.append(page(20, listOf("c", "d", "a")), PlatformClasses.MOD)

        assertEquals(listOf("a", "b", "c", "d"), feed.items.map { it.data.platformId() })
        assertEquals(feed.items.size, feed.items.map { it.key }.toSet().size)
        // 这一页带来了一条新的（d），服务端也没说这是最后一页，所以还能继续翻
        assertFalse(feed.endOfResults)
        assertTrue(feed.canLoadMore())
    }

    @Test
    fun appendDistinctKeepsTheFirstOccurrenceAndItsOrder() {
        val merged = discoverAppendDistinct(
            existing = listOf("x", "y"),
            incoming = listOf("y", "z", "z"),
            keyOf = { it },
        )
        assertEquals(listOf("x", "y", "z"), merged)
    }

    @Test
    fun appendDistinctWithAnEmptyPageChangesNothing() {
        val existing = listOf("x")
        assertEquals(existing, discoverAppendDistinct(existing, emptyList<String>()) { it })
    }

    /** 跨平台同 ID 不是同一个项目 */
    @Test
    fun theProjectKeyIncludesThePlatform() {
        val forge = DiscoverItem(fakeSearchData(Platform.CURSEFORGE, "123"), "t", PlatformClasses.MOD, null)
        val modrinth = DiscoverItem(fakeSearchData(Platform.MODRINTH, "123"), "t", PlatformClasses.MOD, null)
        assertEquals("CURSEFORGE/123", forge.key)
        assertEquals("MODRINTH/123", modrinth.key)
    }

    // ---- 状态区分 ---------------------------------------------------------

    @Test
    fun aZeroResultSearchIsEmptyNotFailed() {
        val feed = DiscoverFeed().start().append(
            page(0, emptyList(), lastPage = true),
            PlatformClasses.MOD,
        )
        assertEquals(DiscoverFeedPhase.Empty, feed.phase)
        assertEquals(DiscoverFeedView.NoResults, feed.view())
        assertNull(feed.error)
    }

    /** 第一页失败：只有错误，绝不是一个"没有结果"的列表 */
    @Test
    fun aFailedFirstPageIsAnErrorNotAnEmptyList() {
        val feed = DiscoverFeed().start().failFirstPage(AndroidStringText.Text("boom"))
        assertEquals(DiscoverFeedPhase.Failed, feed.phase)
        assertEquals(DiscoverFeedView.Error, feed.view())
        assertEquals("boom", (feed.error as AndroidStringText.Text).value)
        assertTrue(feed.items.isEmpty())
        assertFalse(feed.canLoadMore())
    }

    /** 下一页失败：已经取到的结果仍然可用，但不能把失败说成"到底了" */
    @Test
    fun aFailedNextPageKeepsTheResultsAlreadyLoaded() {
        val feed = DiscoverFeed()
            .start()
            .append(page(0, (0 until 20).map { "m$it" }), PlatformClasses.MOD)
            .failNextPage(AndroidStringText.Text("next failed"))

        assertEquals(DiscoverFeedPhase.Failed, feed.phase)
        assertEquals(DiscoverFeedView.ListWithError, feed.view())
        assertEquals(20, feed.items.size)
        assertFalse(feed.endOfResults)
        // 重试要能继续翻同一页
        assertTrue(feed.canLoadMore())
        assertNotNull(feed.beginNextPage())
    }

    @Test
    fun aFailedListNeverReportsEndOfResults() {
        val feed = DiscoverFeed()
            .start()
            .append(page(0, (0 until 20).map { "m$it" }), PlatformClasses.MOD)
            .failNextPage(AndroidStringText.Text("x"))
        assertFalse(feed.endOfResults)
        assertFalse(feed.view() == DiscoverFeedView.NoResults)
    }

    @Test
    fun aListAtTheEndCannotStartAnotherPage() {
        val feed = DiscoverFeed()
            .start()
            .append(page(0, (0 until 20).map { "m$it" }, lastPage = true), PlatformClasses.MOD)
        assertTrue(feed.endOfResults)
        assertFalse(feed.canLoadMore())
        assertNull(feed.beginNextPage())
    }

    @Test
    fun beginNextPageKeepsTheItemsAndOnlyMarksLoading() {
        val feed = DiscoverFeed()
            .start()
            .append(page(0, (0 until 20).map { "m$it" }), PlatformClasses.MOD)
        val paging = feed.beginNextPage()!!
        assertEquals(DiscoverFeedPhase.PagingNext, paging.phase)
        assertEquals(feed.items, paging.items)
        assertEquals(DiscoverFeedView.LoadingNext, paging.view())
        // 已经在取了就不该再发一次
        assertNull(paging.beginNextPage())
    }

    // ---- 预取 -------------------------------------------------------------

    @Test
    fun theNextPageIsRequestedBeforeTheUserHitsTheBottom() {
        assertTrue(prefetch(lastVisible = 12, count = 20))
        assertTrue(prefetch(lastVisible = 19, count = 20))
    }

    @Test
    fun theMiddleOfTheListDoesNotRequestTheNextPage() {
        assertFalse(prefetch(lastVisible = 5, count = 20))
        // 阈值之内：正好差 8 条才触发
        assertFalse(prefetch(lastVisible = 11, count = 20))
    }

    @Test
    fun nothingIsPrefetchedWhileLoadingOrAtTheEnd() {
        assertFalse(prefetch(19, 20, DiscoverFeedPhase.Loading))
        assertFalse(prefetch(19, 20, DiscoverFeedPhase.PagingNext))
        assertFalse(prefetch(19, 20, DiscoverFeedPhase.Idle))
        assertFalse(prefetch(19, 20, DiscoverFeedPhase.Empty))
        assertFalse(prefetch(19, 20, DiscoverFeedPhase.Ready, endOfResults = true))
    }

    /**
     * 可见条目为零时这里不预取（没有位置信息）
     *
     * 「已安装」筛选后可能一条都不剩，那种情况由调用方按
     * "还没到底就继续翻"处理，而不是靠这个函数猜。
     */
    @Test
    fun anEmptyVisibleListNeverPrefetchesOnItsOwn() {
        assertFalse(prefetch(lastVisible = -1, count = 0))
        assertFalse(prefetch(lastVisible = 0, count = 0))
    }

    // ---- 重置条件 ---------------------------------------------------------

    private val baseQuery = DiscoverQuery(
        platform = Platform.CURSEFORGE,
        classes = PlatformClasses.MOD,
        searchName = "sodium",
        gameVersion = "1.20.1",
        modloader = CurseForgeModLoader.FORGE,
        sortField = PlatformSortField.RELEVANCE,
    )

    @Test
    fun theFirstSearchAlwaysStartsAFreshList() {
        assertEquals(
            DiscoverResetReason.FirstSearch,
            discoverResetReason(previous = null, next = baseQuery),
        )
    }

    @Test
    fun anUnchangedQueryKeepsAccumulating() {
        assertNull(discoverResetReason(baseQuery, baseQuery.copy()))
    }

    @Test
    fun everySingleConditionResetsPagingOnItsOwn() {
        assertEquals(
            DiscoverResetReason.Provider,
            discoverResetReason(baseQuery, baseQuery.copy(platform = Platform.MODRINTH)),
        )
        assertEquals(
            DiscoverResetReason.Category,
            discoverResetReason(baseQuery, baseQuery.copy(classes = PlatformClasses.MOD_PACK)),
        )
        assertEquals(
            DiscoverResetReason.SearchName,
            discoverResetReason(baseQuery, baseQuery.copy(searchName = "iris")),
        )
        assertEquals(
            DiscoverResetReason.GameVersion,
            discoverResetReason(baseQuery, baseQuery.copy(gameVersion = "1.21")),
        )
        assertEquals(
            DiscoverResetReason.ModLoader,
            discoverResetReason(baseQuery, baseQuery.copy(modloader = CurseForgeModLoader.FABRIC)),
        )
        assertEquals(
            DiscoverResetReason.Sort,
            discoverResetReason(baseQuery, baseQuery.copy(sortField = PlatformSortField.DOWNLOADS)),
        )
    }

    /** 清空加载器也是一次真实变化（存档不支持加载器过滤） */
    @Test
    fun clearingTheLoaderIsAlsoAReset() {
        assertEquals(
            DiscoverResetReason.ModLoader,
            discoverResetReason(baseQuery, baseQuery.copy(modloader = null)),
        )
        assertEquals(
            DiscoverResetReason.ModLoader,
            discoverResetReason(baseQuery.copy(modloader = null), baseQuery),
        )
    }

    @Test
    fun startingAFreshListClearsEverythingButThePageSize() {
        val feed = DiscoverFeed()
            .start()
            .append(page(0, (0 until 20).map { "m$it" }), PlatformClasses.MOD)
            .failNextPage(AndroidStringText.Text("x"))
        val restarted = feed.start()
        assertEquals(DiscoverFeedPhase.Loading, restarted.phase)
        assertTrue(restarted.items.isEmpty())
        assertEquals(0, restarted.index)
        assertEquals(0, restarted.pages)
        assertFalse(restarted.endOfResults)
        assertNull(restarted.error)
        assertEquals(feed.pageSize, restarted.pageSize)
    }

    // ---- 工具 -------------------------------------------------------------

    private fun prefetch(
        lastVisible: Int,
        count: Int,
        phase: DiscoverFeedPhase = DiscoverFeedPhase.Ready,
        endOfResults: Boolean = false,
    ) = discoverShouldPrefetch(lastVisible, count, phase, endOfResults)

    private fun page(index: Int, ids: List<String>, lastPage: Boolean = false) = DiscoverPage(
        index = index,
        entries = ids.map {
            DiscoverEntry(data = fakeSearchData(Platform.CURSEFORGE, it), title = it)
        },
        serverLastPage = lastPage,
    )
}

/** 测试用的搜索结果：只提供身份与标题，其余字段没有意义 */
internal fun fakeSearchData(
    platform: Platform,
    projectId: String,
    loaders: List<PlatformDisplayLabel> = emptyList(),
): PlatformSearchData = object : PlatformSearchData {
    override fun platform(): Platform = platform
    override fun platformId(): String = projectId
    override fun platformTitle(): String = projectId
    override fun platformDescription(): String = ""
    override fun platformAuthor(): String = "someone"
    override fun platformIconUrl(): String? = null
    override fun platformDownloadCount(): Long = 0
    override fun platformFollows(): Long? = null
    override fun platformModLoaders(): List<PlatformDisplayLabel>? = loaders.ifEmpty { null }
    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? = null
}