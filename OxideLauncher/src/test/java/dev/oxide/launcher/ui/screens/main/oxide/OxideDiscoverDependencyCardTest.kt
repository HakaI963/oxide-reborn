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
import dev.oxide.launcher.game.download.assets.platform.PlatformDependencyType
import dev.oxide.launcher.game.download.assets.platform.PlatformDisplayLabel
import dev.oxide.launcher.game.download.assets.platform.PlatformFilterCode
import dev.oxide.launcher.game.download.assets.platform.PlatformProject
import dev.oxide.launcher.game.download.assets.platform.PlatformReleaseType
import dev.oxide.launcher.game.download.assets.platform.PlatformVersion
import dev.oxide.launcher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import dev.oxide.launcher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import dev.oxide.launcher.ui.AndroidStringText
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 依赖卡片字段选择的单测
 *
 * 真机上的症状是"依赖面板只剩一串数字编号"，所以这里把几件事钉死：
 *  - 名字只能来自平台上的项目标题，**绝不**来自项目 id 或版本 id；
 *  - 两个平台给的字段并不一样（作者只有 CurseForge 有），缺的那一个就不画；
 *  - 项目查询按 `平台 + 项目 id` 去重，并且有上限；
 *  - 查不出来的依赖变成一条带原因、可重试的失败，而不是编号当名字。
 */
class OxideDiscoverDependencyCardTest {

    // ---- 字段选择：解析成功的依赖 --------------------------------------------

    @Test
    fun aResolvedDependencyCarriesTheProjectNameAuthorAndIcon() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "482378"),
            outcome = resolved(Platform.CURSEFORGE, "482378", "Sodium", "CaffeineMC", logo),
            version = fakeVersion(
                versionLabel = "0.5.8",
                gameVersions = listOf("1.20.1", "1.20.4"),
                loaders = listOf(CurseForgeModLoader.FABRIC),
            ),
            state = DiscoverDependencyState.Resolved("0.5.8", "1.20.1, 1.20.4"),
            targetGameVersion = "1.20.4",
        )
        assertEquals("Sodium", card.name)
        assertEquals("CaffeineMC", card.author)
        assertEquals(logo, card.iconUrl)
        assertEquals(PlatformDependencyType.REQUIRED, card.type)
        assertEquals(Platform.CURSEFORGE, card.provider)
        assertEquals("0.5.8", card.versionLabel)
        assertEquals("1.20.4", card.gameVersion)
        assertEquals("Fabric", card.loader)
        assertTrue(card.named)
        assertTrue(card.installable)
        assertFalse(card.retryable)
    }

    /** 有名字就不必再挂一行编号：那只是噪音 */
    @Test
    fun aNamedDependencyDoesNotShowItsId() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.MODRINTH, "AANobbMI"),
            outcome = resolved(Platform.MODRINTH, "AANobbMI", "Sodium", null, iconUrl = "https://cdn.modrinth.com/i.png"),
            version = fakeVersion(versionLabel = "mc1.20.4-0.5.8", gameVersions = listOf("1.20.4")),
            state = DiscoverDependencyState.Resolved("mc1.20.4-0.5.8", "1.20.4"),
            targetGameVersion = "1.20.4",
        )
        assertEquals("AANobbMI", card.idDetail)
        assertFalse(card.showIdDetail)
    }

    /**
     * 平台没给的字段就是 null，界面据此不画那一行——
     * 而不是画一个空的、或者拿别的字段顶上去
     */
    @Test
    fun aPartiallyResolvedProjectKeepsTheFieldsItHasAndDropsTheOnesItDoesNot() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.MODRINTH, "AANobbMI"),
            outcome = resolved(
                Platform.MODRINTH,
                "AANobbMI",
                "Fabric API",
                author = null,
                iconUrl = null,
            ),
            version = fakeVersion(versionLabel = "0.92.2", gameVersions = listOf("1.20.4")),
            state = DiscoverDependencyState.Resolved("0.92.2", "1.20.4"),
            targetGameVersion = "1.20.4",
        )
        assertEquals("Fabric API", card.name)
        assertNull(card.author)
        assertNull(card.iconUrl)
        // 版本信息仍然是那个文件自己的，不会因为项目少了图标就连版本也没了
        assertEquals("0.92.2", card.versionLabel)
        assertEquals("1.20.4", card.gameVersion)
    }

    /** 平台标题是空白时不能当成名字：否则标题位就是一段看不见的空白 */
    @Test
    fun aBlankProjectTitleIsNotAName() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "482378"),
            outcome = resolved(Platform.CURSEFORGE, "482378", "   ", "CaffeineMC", logo),
            version = fakeVersion(versionLabel = "0.5.8", gameVersions = listOf("1.20.4")),
            state = DiscoverDependencyState.Resolved("0.5.8", "1.20.4"),
            targetGameVersion = "1.20.4",
        )
        assertNull(card.name)
        assertFalse(card.named)
        // 名字没了，编号就该作为次要信息露出来，而不是被藏起来
        assertTrue(card.showIdDetail)
        assertEquals("482378", card.idDetail)
    }

    // ---- 每个平台给的字段并不一样 --------------------------------------------

    /**
     * CurseForge 的项目接口带 `authors[].name`，作者这一行拿得到。
     *
     * 这里刻意让 `platformAuthor()` 直接抛异常（CurseForge 的实现就是
     * `authors[0].name`，作者列表为空时会抛 ArrayIndexOutOfBoundsException），
     * 卡片仍然要拿得到名字、图标与作者。
     */
    @Test
    fun curseForgeGivesNameLogoAndAuthorEvenWhenTheSingleAuthorAccessorThrows() {
        val project = fakeProject(
            platform = Platform.CURSEFORGE,
            id = "482378",
            title = "Sodium",
            iconUrl = logo,
            authors = listOf("CaffeineMC", "jellysquid3"),
            // 真实实现取 authors[0].name，列表为空即抛
            authorAccessor = { error("authors[0] of an empty list") },
        )
        val resolved = project.toDiscoverDependencyProject()
        assertEquals("Sodium", resolved.name)
        assertEquals(logo, resolved.iconUrl)
        assertEquals("CaffeineMC", resolved.author)
        assertEquals(Platform.CURSEFORGE, resolved.platform)
        assertEquals("482378", resolved.projectId)
        assertTrue(resolved.named)
    }

    /**
     * Modrinth 的 `GET /project/{id}` 只带 `team` / `organization` 的 **id**，
     * 没有成员名字，因此卡片不画作者行——而不是把团队 id 冒充成作者。
     */
    @Test
    fun modrinthGivesNameAndIconButNoAuthorName() {
        val project = fakeProject(
            platform = Platform.MODRINTH,
            id = "AANobbMI",
            title = "Sodium",
            iconUrl = "https://cdn.modrinth.com/data/AANobbMI/icon.png",
            // 与真实实现一致：project/{id} 响应里没有作者字段
            authors = emptyList(),
            authorAccessor = { null },
        ).toDiscoverDependencyProject()

        assertEquals("Sodium", project.name)
        assertEquals("https://cdn.modrinth.com/data/AANobbMI/icon.png", project.iconUrl)
        assertNull(project.author)
        assertTrue(project.named)
    }

    /** 两个平台都没有给作者时，作者字段是 null 而不是空串 */
    @Test
    fun aMissingAuthorIsNullRatherThanBlank() {
        val project = fakeProject(
            platform = Platform.MODRINTH,
            id = "AANobbMI",
            title = "Something",
            iconUrl = null,
            authors = listOf("   "),
            authorAccessor = { "   " },
        ).toDiscoverDependencyProject()
        assertNull(project.author)
        assertNull(project.iconUrl)
    }

    // ---- 版本字段 -----------------------------------------------------------

    /** 平台没给版本号时才退回文件自己的显示名 */
    @Test
    fun theVersionLabelFallsBackToTheFileDisplayNameOnlyWhenThereIsNoVersionNumber() {
        assertEquals("0.5.8", discoverVersionLabel(fakeVersion(versionLabel = "0.5.8", displayName = "Sodium 0.5.8")))
        assertEquals(
            "Sodium 0.5.8",
            discoverVersionLabel(fakeVersion(versionLabel = "  ", displayName = "Sodium 0.5.8"))
        )
        assertNull(discoverVersionLabel(fakeVersion(versionLabel = "", displayName = "  ")))
    }

    /**
     * 目标 Minecraft 版本优先：这个文件被挑出来正是因为它支持那一档
     */
    @Test
    fun theTargetMinecraftVersionIsReportedWhenTheFileSupportsIt() {
        val version = fakeVersion(gameVersions = listOf("1.19.2", "1.20.1", "1.20.4"))
        assertEquals("1.20.1", discoverGameVersion(version, "1.20.1"))
    }

    /** 目标与文件自相矛盾时不报版本：报哪一个都是假的 */
    @Test
    fun aTargetTheFileDoesNotSupportReportsNoMinecraftVersion() {
        val version = fakeVersion(gameVersions = listOf("1.19.2", "1.20.1"))
        assertNull(discoverGameVersion(version, "1.21.1"))
    }

    /** 没有目标时如实报文件自己声明的第一个 */
    @Test
    fun withoutATargetTheFilesOwnFirstMinecraftVersionIsReported() {
        val version = fakeVersion(gameVersions = listOf("1.19.2", "1.20.1"))
        assertEquals("1.19.2", discoverGameVersion(version, null))
        assertEquals("1.19.2", discoverGameVersion(version, "  "))
        assertNull(discoverGameVersion(fakeVersion(gameVersions = emptyList()), null))
    }

    /** 加载器取文件标注的第一个；没标注就是 null，界面据此不画加载器 */
    @Test
    fun theLoaderIsTheFirstOneTheFileDeclares() {
        assertEquals(
            "Fabric",
            discoverLoaderLabel(fakeVersion(loaders = listOf(CurseForgeModLoader.FABRIC, CurseForgeModLoader.FORGE)))
        )
        assertEquals(
            "Fabric",
            discoverLoaderLabel(fakeVersion(loaders = listOf(ModrinthModLoaderCategory.FABRIC)))
        )
        assertNull(discoverLoaderLabel(fakeVersion(loaders = emptyList())))
    }

    /** CurseForge 的 `ANY` 加载器显示名是空串：它不算一个加载器，要跳过而不是画一个空的 */
    @Test
    fun aBlankLoaderLabelIsSkippedRatherThanShown() {
        assertEquals(
            "Forge",
            discoverLoaderLabel(fakeVersion(loaders = listOf(CurseForgeModLoader.ANY, CurseForgeModLoader.FORGE)))
        )
        assertNull(discoverLoaderLabel(fakeVersion(loaders = listOf(CurseForgeModLoader.ANY))))
    }

    // ---- 去重 ---------------------------------------------------------------

    /**
     * 同一个项目在一条关系里出现两次时只查一次：
     * 一次带精确版本 id、一次只给项目 id，两条都要画，但项目只有一份
     */
    @Test
    fun theSameProjectIsQueriedOnceEvenWhenItAppearsInSeveralRelations() {
        val targets = listOf(
            DiscoverDependencyTarget(Platform.MODRINTH, "AANobbMI"),
            DiscoverDependencyTarget(Platform.MODRINTH, "AANobbMI"),
            DiscoverDependencyTarget(Platform.CURSEFORGE, "AANobbMI"),
            DiscoverDependencyTarget(Platform.CURSEFORGE, "306612"),
        )
        val deduped = discoverProjectTargets(targets)
        assertEquals(2, deduped.size)
        // 平台不同就不是同一个项目：两个平台的编号空间各自独立
        assertEquals(
            setOf("MODRINTH/AANobbMI", "CURSEFORGE/AANobbMI"),
            discoverProjectKeys(deduped)
        )
        // 保留首次出现的顺序
        assertEquals(Platform.MODRINTH, deduped[0].platform)
        assertEquals(Platform.CURSEFORGE, deduped[1].platform)
    }

    // ---- 上限 ---------------------------------------------------------------

    /**
     * 一份写了上百条依赖的元数据不该把确认界面拖死：
     * 依赖行数与项目查询数各有各的上限
     */
    @Test
    fun theNumberOfQueriedProjectsIsCapped() {
        val targets = (1..500).map { DiscoverDependencyTarget(Platform.CURSEFORGE, it.toString()) }
        assertEquals(MAX_DISCOVER_DEPENDENCY_PROJECTS, discoverProjectTargets(targets).size)
        assertEquals(MAX_DISCOVER_DEPENDENCY_PROJECTS, discoverProjectKeys(discoverProjectTargets(targets)).size)
    }

    /** 截断按首次出现的顺序，因此留下的永远是最先被用到的那批 */
    @Test
    fun theCapKeepsTheFirstProjectsSeen() {
        val targets = (1..500).map { DiscoverDependencyTarget(Platform.CURSEFORGE, it.toString()) }
        assertEquals("1", discoverProjectTargets(targets).first().projectId)
        assertEquals(
            MAX_DISCOVER_DEPENDENCY_PROJECTS.toString(),
            discoverProjectTargets(targets).last().projectId
        )
    }

    /** 依赖行本身也有上限，与项目查询的上限是两件事 */
    @Test
    fun theDependencyListItselfIsCapped() {
        val relations = (1..300).map { id ->
            PlatformVersion.PlatformDependency(
                Platform.CURSEFORGE,
                id.toString(),
                null,
                PlatformDependencyType.REQUIRED,
            )
        }
        val dependencies = discoverDependenciesCapped(fakeVersion(dependencies = relations))
        assertEquals(MAX_DISCOVER_DEPENDENCIES, dependencies.size)
        // 去重仍然在截断之前生效，所以 300 条不同的关系才是 300 条
        assertEquals(300, discoverDependenciesOf(fakeVersion(dependencies = relations)).size)
    }

    /**
     * 两个上限一起看：一个报了 300 条依赖的文件，画出 [MAX_DISCOVER_DEPENDENCIES] 行，
     * 但只查前 [MAX_DISCOVER_DEPENDENCY_PROJECTS] 个项目的元数据。
     * 多出来的行仍然在那里，只是没有名字——而不是被藏起来，也不是顶着编号。
     */
    @Test
    fun onlyTheFirstProjectsGetANameWhenAModReportsMoreDependenciesThanTheCap() {
        val relations = (1..300).map { id ->
            PlatformVersion.PlatformDependency(
                Platform.CURSEFORGE,
                id.toString(),
                null,
                PlatformDependencyType.REQUIRED,
            )
        }
        val dependencies = discoverDependenciesCapped(fakeVersion(dependencies = relations))
        val named = discoverProjectKeys(
            discoverProjectTargets(
                dependencies.map { DiscoverDependencyTarget(it.platform, it.projectId!!) }
            )
        )

        assertEquals(MAX_DISCOVER_DEPENDENCIES, dependencies.size)
        assertEquals(MAX_DISCOVER_DEPENDENCY_PROJECTS, named.size)
        assertTrue(dependencies.first().key.isNotBlank())

        val beyondCap = discoverDependencyCard(
            dependency = dependencies.last(),
            outcome = DiscoverProjectOutcome.Skipped,
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        assertNull(beyondCap.name)
        assertTrue(beyondCap.state is DiscoverDependencyState.Failed)
        // 编号仍然在，只是当次要信息
        assertEquals(dependencies.last().projectId, beyondCap.idDetail)
        assertTrue(beyondCap.showIdDetail)
    }

    // ---- 查不出来的时候 ------------------------------------------------------

    /** 查失败：变成带原因、可重试的失败，而不是把编号顶上去当名字 */
    @Test
    fun aFailedProjectLookupBecomesARetryableFailureAndNeverAnIdAsAName() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "958083"),
            outcome = DiscoverProjectOutcome.Failed(AndroidStringText.Text("Connection timed out")),
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        assertNull(card.name)
        assertFalse(card.named)
        assertTrue(card.state is DiscoverDependencyState.Failed)
        assertEquals(
            AndroidStringText.Text("Connection timed out"),
            (card.state as DiscoverDependencyState.Failed).message
        )
        assertTrue(card.retryable)
        // 平台仍然在场，用户至少知道该去哪里核对
        assertEquals(Platform.CURSEFORGE, card.provider)
        // 编号留着，但只是次要信息
        assertTrue(card.showIdDetail)
        assertEquals("958083", card.idDetail)
    }

    /** 因为上限没查：如实说没查，不装作查过 */
    @Test
    fun aProjectBeyondTheCapSaysSo() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "309927"),
            outcome = DiscoverProjectOutcome.Skipped,
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        assertNull(card.name)
        assertTrue(
            "reason should mention the cap, was: ${failureArgsOf(card)}",
            failureArgsOf(card).contains(MAX_DISCOVER_DEPENDENCY_PROJECTS.toString())
        )
        assertTrue(card.retryable)
    }

    /** 依赖压根没给项目 id：与"查失败"是两件事，各有自己的说明 */
    @Test
    fun aDependencyWithoutAProjectIdHasNothingToLookUp() {
        val dependency = DiscoverDependency(Platform.CURSEFORGE, null, null, PlatformDependencyType.TOOL)
        val card = discoverDependencyCard(
            dependency = dependency,
            outcome = DiscoverProjectOutcome.Unknown,
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        assertNull(card.name)
        assertTrue(card.state is DiscoverDependencyState.Failed)
        // 两个 id 都没有，就连编号这一行也没有得露
        assertNull(card.idDetail)
        assertFalse(card.showIdDetail)
    }

    /** 只有版本 id 的依赖，编号这一行露的是那个版本 id */
    @Test
    fun aVersionOnlyDependencyShowsItsVersionIdWhenItCannotBeResolved() {
        val card = discoverDependencyCard(
            dependency = DiscoverDependency(
                Platform.MODRINTH,
                null,
                "8d6bzmvJ",
                PlatformDependencyType.OPTIONAL,
            ),
            outcome = DiscoverProjectOutcome.Unknown,
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        assertEquals("8d6bzmvJ", card.idDetail)
        assertTrue(card.showIdDetail)
    }

    /** 读到了项目就没有额外的失败原因 */
    @Test
    fun aResolvedProjectHasNoFailureReason() {
        assertNull(
            discoverProjectFailure(
                requiredDep(Platform.MODRINTH, "x"),
                resolved(Platform.MODRINTH, "x", "y", null, null),
            )
        )
    }

    /**
     * 平台报了"这个项目已经不在公开状态"时，依赖本身仍要保持它的类型与勾选能力，
     * 只是这一条变成带原因的失败——其余依赖不受牵连
     */
    @Test
    fun oneUnresolvableDependencyDoesNotTakeTheOthersWithIt() {
        val bad = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "958083"),
            outcome = DiscoverProjectOutcome.Failed(AndroidStringText.Text("not in a publicly available state")),
            version = null,
            state = DiscoverDependencyState.Resolving,
        )
        val good = discoverDependencyCard(
            dependency = DiscoverDependency(
                Platform.MODRINTH,
                "P7dR8mSH",
                null,
                PlatformDependencyType.REQUIRED,
            ),
            outcome = resolved(Platform.MODRINTH, "P7dR8mSH", "Fabric API", null, null),
            version = fakeVersion(versionLabel = "0.92.2", gameVersions = listOf("1.20.4")),
            state = DiscoverDependencyState.Resolved("0.92.2", "1.20.4"),
            targetGameVersion = "1.20.4",
        )
        assertTrue(bad.state is DiscoverDependencyState.Failed)
        assertFalse(good.state is DiscoverDependencyState.Failed)
        assertEquals("Fabric API", good.name)
        assertTrue(good.installable)
        assertNotEquals(bad.key, good.key)
    }

    /**
     * 版本解析本身失败时，保留它更具体的原因，不再叠一层"读不到项目"
     */
    @Test
    fun aVersionFailureKeepsItsOwnReason() {
        val card = discoverDependencyCard(
            dependency = requiredDep(Platform.CURSEFORGE, "388172"),
            outcome = DiscoverProjectOutcome.Skipped,
            version = null,
            state = DiscoverDependencyState.Failed(AndroidStringText.Text("The project {388172} does not exist")),
        )
        assertEquals(
            AndroidStringText.Text("The project {388172} does not exist"),
            (card.state as DiscoverDependencyState.Failed).message
        )
    }

    // ---- 会话内缓存 ----------------------------------------------------------

    /**
     * 会话内缓存：同一个项目读第二次直接复用，失败的读取不写进去，
     * 所以"重试"是真的重新查，而不是把上一次的错再拿出来一次
     */
    @Test
    fun theSessionCacheReusesAProjectAndNeverStoresAFailure() {
        val cache = DiscoverProjectCache()
        val project = fakeProject(Platform.CURSEFORGE, "482378", "Sodium", logo, listOf("CaffeineMC"))
        assertNull(cache["CURSEFORGE/482378"])
        cache["CURSEFORGE/482378"] = project
        assertEquals(project, cache["CURSEFORGE/482378"])
        // 平台不同就不是同一个键
        assertNull(cache["MODRINTH/482378"])
        assertEquals(1, cache.size)
        cache.clear()
        assertEquals(0, cache.size)
        assertNull(cache["CURSEFORGE/482378"])
    }

    /** 缓存有上限，满了挤掉最久没用过的那个 */
    @Test
    fun theSessionCacheIsBounded() {
        val cache = DiscoverProjectCache(capacity = 2)
        val first = fakeProject(Platform.CURSEFORGE, "1", "One", null, listOf("a"))
        val second = fakeProject(Platform.CURSEFORGE, "2", "Two", null, listOf("b"))
        val third = fakeProject(Platform.CURSEFORGE, "3", "Three", null, listOf("c"))
        cache["1"] = first
        cache["2"] = second
        // 命中一次，把 1 变成最近用过
        assertEquals(first, cache["1"])
        cache["3"] = third
        assertEquals(2, cache.size)
        assertEquals(third, cache["3"])
        assertNull(cache["2"])
        assertEquals(first, cache["1"])
    }

    // ---- 工具 ---------------------------------------------------------------

    /** 必装依赖既是可装的，也是默认勾上的 */
    private fun requiredDep(platform: Platform, projectId: String) = DiscoverDependency(
        platform = platform,
        projectId = projectId,
        versionId = null,
        type = PlatformDependencyType.REQUIRED,
    )

    private fun resolved(
        platform: Platform,
        projectId: String,
        name: String?,
        author: String?,
        iconUrl: String?,
    ) = DiscoverProjectOutcome.Resolved(
        DiscoverDependencyProject(
            platform = platform,
            projectId = projectId,
            name = name,
            iconUrl = iconUrl,
            author = author,
        )
    )

    /** 失败状态里的原因：是带格式化参数的字符串资源，参数就是那句说明的全部内容 */
    private fun failureArgsOf(card: DiscoverDependencyCard): String {
        val text = (card.state as DiscoverDependencyState.Failed).message
        assertTrue("reason should be a string resource, was: $text", text is AndroidStringText.StringRes)
        return (text as AndroidStringText.StringRes).args!!.joinToString(" ")
    }

    private val logo = "https://mediafilez.forgecdn.net/projects/482378/logos/63563c04015c8d8524d7ae7153b8029.png"

    // ---- 假数据 -------------------------------------------------------------

    /**
     * 平台项目假实现
     *
     * [authorAccessor] 单独开放，因为 CurseForge 的真实实现是 `authors[0].name`，
     * 作者列表为空时会抛——卡片必须绕开它。
     */
    private fun fakeProject(
        platform: Platform,
        id: String,
        title: String,
        iconUrl: String?,
        authors: List<String>,
        authorAccessor: () -> String? = { authors.firstOrNull() },
    ): PlatformProject = object : PlatformProject {
        override fun platform(): Platform = platform
        override fun platformId(): String = id
        override fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses = PlatformClasses.MOD
        override fun platformSlug(): String = id.lowercase()
        override fun platformIconUrl(): String? = iconUrl
        override fun platformTitle(): String = title
        override fun platformSummary(): String? = null
        override fun platformAuthor(): String? = authorAccessor()
        override fun platformAuthors(): List<String> = authors
        override fun platformDownloadCount(): Long = 0
        override fun platformFollows(): Long? = null
        override fun platformModLoaders(): List<PlatformDisplayLabel>? = null
        override fun checkClasses() = Unit
        override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? = null
        override fun platformUrls(defaultClasses: PlatformClasses): PlatformProject.Urls =
            PlatformProject.Urls()

        override fun platformScreenshots(): List<PlatformProject.Screenshot> = emptyList()
    }

    /** 只提供这组测试关心的字段的文件版本 */
    private fun fakeVersion(
        versionLabel: String = "",
        displayName: String = "",
        gameVersions: List<String> = emptyList(),
        loaders: List<PlatformDisplayLabel> = emptyList(),
        dependencies: List<PlatformVersion.PlatformDependency> = emptyList(),
    ): PlatformVersion = object : PlatformVersion {
        override suspend fun initFile(currentProjectId: String): Boolean = true
        override fun platform(): Platform = Platform.MODRINTH
        override fun platformId(): String = "file-1"
        override fun platformProjectId(): String = "project-1"
        override fun platformDisplayName(): String = displayName
        override fun platformFileName(): String = "file.jar"
        override fun platformGameVersion(): Array<String> = gameVersions.toTypedArray()
        override fun platformLoaders(): List<PlatformDisplayLabel> = loaders
        override fun platformReleaseType(): PlatformReleaseType = PlatformReleaseType.RELEASE
        override fun platformDependencies(): List<PlatformVersion.PlatformDependency> = dependencies
        override fun platformDownloadCount(): Long = 0
        override fun platformDownloadUrl(): String = ""
        override fun platformDatePublished(): Instant = Instant.EPOCH
        override fun platformSha1(): String? = null
        override fun platformFileSize(): Long = 0
        override fun platformVersion(): String = versionLabel
    }
}
