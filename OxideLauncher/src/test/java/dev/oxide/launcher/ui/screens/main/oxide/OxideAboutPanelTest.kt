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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.ui.screens.main.oxide

import dev.oxide.launcher.BuildConfig
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.path.URL_COMMUNITY
import dev.oxide.launcher.path.URL_GITHUB_DRIVER_PLUGINS
import dev.oxide.launcher.path.URL_GITHUB_NATIVE_LIB_PLUGINS
import dev.oxide.launcher.path.URL_GITHUB_RENDERER_PLUGINS
import dev.oxide.launcher.path.URL_MCMOD
import dev.oxide.launcher.path.URL_PLUS
import dev.oxide.launcher.path.URL_PROJECT
import dev.oxide.launcher.path.URL_WEBLATE
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关于面板的内容与署名
 *
 * 关于面板最容易出的两类错，都不是编译期能发现的：
 * 一是把构建身份（发行号、BuildConfig.VERSION_NAME）当成产品版本报给用户，
 * 二是某天"清理品牌"时顺手把 GPL 要求的署名一起删了。
 * 因此这里把版本规则与名单逐条钉死。
 */
class OxideAboutPanelTest {

    private val entries = oxideAboutEntries()
    private val byKey = entries.associateBy { it.key }

    // ---- 版本 -------------------------------------------------------------

    @Test
    fun theProductVersionIsTheDisplayVersionAndNeverTheBuildIdentity() {
        // 同一份源码上发行号与 BuildConfig.VERSION_NAME 都会和 1.0.0 不同；
        // 把它们当"启动器版本"报出去等于报错版本
        assertEquals("1.0.0", oxideAboutProductVersion("1.0.0", "1.6.0"))
        assertEquals("1.0.0", oxideAboutProductVersion("1.0.0", "2.0.0-beta-7"))
        // 换个构建身份，显示的仍然是产品版本
        assertEquals(
            oxideAboutProductVersion("3.1.4", "1"),
            oxideAboutProductVersion("3.1.4", "999"),
        )
    }

    @Test
    fun theVersionRuleCannotSilentlyFallBackToTheBuildVersion() {
        // 产品版本为空是配置错误，不等于"那就显示构建身份"：空着也是空着，
        // 悄悄换成构建号会把同一个错误报给用户两次
        assertEquals("", oxideAboutProductVersion("", "1.6.0"))
    }

    @Test
    fun thePanelShowsTheProductVersionAndNotTheBuildIdentity() {
        // 直接对真实常量断言：面板读的必须是 LAUNCHER_DISPLAY_VERSION，
        // 而 BuildConfig.VERSION_NAME 是构建身份（同一份源码上各不相同）
        assertEquals(BuildKeys.LAUNCHER_DISPLAY_VERSION, oxideAboutProductVersion(
            displayVersion = BuildKeys.LAUNCHER_DISPLAY_VERSION,
            buildVersionName = BuildConfig.VERSION_NAME,
        ))
        assertTrue(
            "the product version must not be blank",
            BuildKeys.LAUNCHER_DISPLAY_VERSION.isNotBlank(),
        )
    }

    // ---- 必须保留的署名 ---------------------------------------------------

    @Test
    fun theAdaptedAccountCodeCreditStaysAndIsFirst() {
        val credit = byKey[OXIDE_ABOUT_ACCOUNT_CODE_KEY]
        assertNotNull(
            "GPLv3 requires crediting the project the account code was adapted from",
            credit,
        )
        assertEquals("The account code credit must not be renamed away", credit!!.key, OXIDE_ABOUT_ACCOUNT_CODE_KEY)
        // 指向被改编的那个项目，而不是本项目自己
        assertEquals(URL_PLUS, credit.url)
        assertTrue("the credit must name its source project", URL_PLUS.isNotBlank())
    }

    @Test
    fun theAccountCodeCreditIsNotTreatedAsAPlaceholderForTheLauncherName() {
        // 那条说明里没有 %s：填启动器名会把参数丢掉，是格式化误用
        val credit = byKey.getValue(OXIDE_ABOUT_ACCOUNT_CODE_KEY)
        assertFalse(credit.takesLauncherName)
    }

    // ---- 必须保留的致谢 ---------------------------------------------------

    @Test
    fun everyThirdPartyProjectTheLauncherBorrowsFromIsStillCredited() {
        // 少了任何一条，就少了别人允许复用代码的凭据
        val required = listOf(
            "pojav", "pcl2", "fcl", "hmcl", "mcmod", "bmcl", "mcim", "weblate", "github-community",
        )
        val keys = entries.map { it.key }.toSet()
        for (key in required) {
            assertTrue("$key must stay in the acknowledgements, got $keys", key in keys)
        }
    }

    @Test
    fun theLauncherNameFlowsIntoTheAcknowledgementsThatNeedIt() {
        // 这几条说明里有 "%s uses …"，必须填启动器名
        for (key in listOf("pojav", "pcl2", "fcl", "hmcl", "mcmod", "bmcl", "mcim")) {
            assertTrue("$key should name the launcher in its text", byKey.getValue(key).takesLauncherName)
        }
        // 这几条描述的是贡献者群体，没有 %s
        for (key in listOf("weblate", "github-community")) {
            assertFalse("$key must not be given a format argument", byKey.getValue(key).takesLauncherName)
        }
    }

    @Test
    fun theCreditForTheOldMirrorSourcesIsKeptEvenThoughTheCreatorCardIsGone() {
        // BMCL 与 MCIM 是镜像源：删掉那张作者头像卡不等于可以删掉它们的署名
        assertNotNull(byKey["bmcl"])
        assertNotNull(byKey["mcim"])
    }

    @Test
    fun everyAcknowledgementWithAProjectLinkPointsAtThatProject() {
        val expected = mapOf(
            "pojav" to "https://github.com/PojavLauncherTeam/PojavLauncher",
            "pcl2" to "https://github.com/Meloong-Git/PCL",
            "fcl" to "https://github.com/FCL-Team/FoldCraftLauncher",
            "hmcl" to "https://github.com/HMCL-dev/HMCL",
            "mcmod" to URL_MCMOD,
            "mcim" to "https://www.mcimirror.top/sponsor",
            "weblate" to URL_WEBLATE,
            "github-community" to URL_COMMUNITY,
            OXIDE_ABOUT_ACCOUNT_CODE_KEY to URL_PLUS,
        )
        for ((key, url) in expected) {
            assertEquals("$key points at the wrong project", url, byKey.getValue(key).url)
        }
    }

    @Test
    fun anAcknowledgementWithoutAKnownProjectOffersNoLink() {
        // BMCL 那一行原来只有捐赠按钮，没有项目链接：没有链接就不给按钮，
        // 也不在这里编一个地址出来
        assertEquals(null, byKey.getValue("bmcl").url)
    }

    @Test
    fun thePluginProjectsAreCreditedToo() {
        // 渲染器、驱动与本地库都是别人的代码
        val plugins = oxideAboutPluginProjectEntries()
        assertEquals(
            listOf(
                "renderer-plugins" to URL_GITHUB_RENDERER_PLUGINS,
                "driver-plugins" to URL_GITHUB_DRIVER_PLUGINS,
                "native-lib-plugins" to URL_GITHUB_NATIVE_LIB_PLUGINS,
            ),
            plugins.map { it.key to it.url },
        )
        for (entry in plugins) {
            assertTrue(entry.takesLauncherName)
        }
    }

    // ---- 已移除的东西 -----------------------------------------------------

    @Test
    fun theAcknowledgementsCarryNoDonationOrAvatarEntry() {
        // 旧界面把作者自己的头像与捐赠页摆在致谢第一条：那是那个产品的创作者展示
        val source = readPanelSource()
        assertFalse(
            "the old creator's ifdian page must be gone",
            source.contains("ifdian.net"),
        )
        assertFalse(
            "the old creator avatar must be gone",
            source.contains("img_avatar_bangbang93"),
        )
        assertFalse(
            "no donation button belongs in the acknowledgements",
            source.contains("about_sponsor"),
        )
    }

    @Test
    fun thePanelDoesNotClaimToBeTheOldProduct() {
        // "Zalith Launcher" 只允许出现在账号代码那条**署名**里（以字符串资源的形式引用），
        // 绝不能作为这个产品自己的身份出现
        val source = readPanelSource()
        assertFalse(
            "the panel must not present the old launcher as this product",
            source.contains("Zalith Launcher"),
        )
    }

    @Test
    fun theControlLayoutPanelSwitchesToASingleColumnOnASmallLandscape() {
        // 640x360 的横屏上并排会把列表挤到读不出名字，因此窄屏一律纵向堆叠
        assertFalse(oxideControlLayoutsSideBySide(availableWidth = 640.dp, cardMinWidth = 285.dp))
        assertTrue(oxideControlLayoutsSideBySide(availableWidth = 1280.dp, cardMinWidth = 285.dp))
        // 阈值本身也要跟着档位走：放大之后同样的宽度自然落到并排那一侧的反面
        assertTrue(
            "the threshold must follow cardMinWidth rather than being a fixed width",
            oxideControlLayoutsSideBySide(availableWidth = 560.dp, cardMinWidth = 285.dp) ==
                oxideControlLayoutsSideBySide(availableWidth = 560.dp, cardMinWidth = 500.dp),
        )
    }

    @Test
    fun thePanelReadsTheLauncherNameFromBuildKeysRatherThanHardCodingIt() {
        // 产品名只有 BuildKeys.LAUNCHER_NAME 一个来源：写死一份就等于开了一个
        // 可以和真实产品名分叉的分支
        assertTrue(
            "the panel must read BuildKeys.LAUNCHER_NAME",
            readPanelSource().contains("BuildKeys.LAUNCHER_NAME"),
        )
    }

    @Test
    fun theProjectLinkIsTheOxideRepository() {
        assertEquals("https://github.com/oxide-mc/oxide-launcher.git", URL_PROJECT)
    }

    // ---- 协议全文 ---------------------------------------------------------

    @Test
    fun onlyAcknowledgementsWithALicenceRawCanBeOpenedAsALicence() {
        val withLicence = entries.filter { it.licenseRaw != 0 }.map { it.key }.toSet()
        assertEquals(setOf("pojav", "fcl", "hmcl"), withLicence)
        // 反过来：这三条的 raw 都不是 0
        for (key in withLicence) {
            assertTrue("$key must point at a real R.raw licence", byKey.getValue(key).licenseRaw > 0)
        }
    }

    @Test
    fun theLicenceListIsDerivedFromTheAcknowledgementsRatherThanHandWritten() {
        // 名单少一条，协议列表就跟着少一条；两者不会各说各话
        assertEquals(
            entries.filter { it.licenseRaw != 0 },
            oxideAboutLicenseEntries(entries),
        )
        assertEquals(emptyList<OxideAboutEntry>(), oxideAboutLicenseEntries(emptyList()))
    }

    @Test
    fun theLicenceAssetsThePanelRendersAgainstArePresent() {
        // 协议全文由 res/raw 里的文件供文：文件被删掉而代码还指着它，
        // 编译能过、运行才崩。aapt 的名字到 id 的映射在单测里拿不到，
        // 所以这里钉的是"面板依赖的那几份协议文件确实在 res/raw 里"。
        val assets = locate("res/raw").listFiles().orEmpty().map { it.name }.toSet()
        assertFalse("res/raw should not be empty", assets.isEmpty())
        for (asset in listOf("fcl_license.txt", "hmcl_license.txt", "lgpl_3_license.txt")) {
            assertTrue("$asset is referenced by the about panel but missing", asset in assets)
        }
    }

    private fun readPanelSource(): String = locate(
        "ui/screens/main/oxide/OxideAboutPanels.kt"
    ).readText()

    private fun locate(relativePath: String): java.io.File {
        var dir: java.io.File? = java.io.File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve("src/main/java/dev/oxide/launcher/$relativePath")
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error(
            "could not locate src/main/java/dev/oxide/launcher/$relativePath from " +
                java.io.File("").absolutePath
        )
    }
}
