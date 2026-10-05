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

package dev.oxide.launcher.game.account.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * 离线账号的内置默认皮肤与默认披风
 *
 * 离线账号从不下载贴图：`Account.downloadYggdrasil()` 对它算出的 baseUrl 是 null，于是
 * DIR_ACCOUNT_SKIN / DIR_ACCOUNT_CAPE 下永远不会有文件，`addCharacter` 也就永远拿不到贴图，
 * 游戏里只能看到原版 Steve/Alex。因此随包分发 `assets/default_skin.png` 与
 * `assets/default_cape.png`，在离线账号缺少本地贴图时由离线 Yggdrasil 服务器补齐。
 *
 * 这两件事都不是编译期能发现的，所以这里钉住：
 *
 * 1. 资源确实被打进了 assets/，且尺寸满足衣橱自己的校验规则。
 *    尺寸一旦不对，游戏会直接拒绝加载贴图，或者在 skinview 里显示错位。
 * 2. 回落只对离线账号生效。微软账号的皮肤由 Mojang 下发、Ely.by 的皮肤由其认证服务器
 *    下发、账号自带的披风存在 DIR_ACCOUNT_CAPE 下，这些都必须继续以本地文件为准，
 *    否则用户挑好的皮肤会被内置资源悄悄顶掉。
 *
 * 断言跑在**去掉注释之后**的源码上：注释里提到这些账号类型是为了解释回落规则，那是意图，
 * 不是行为。
 */
class DefaultOfflineTexturesTest {

    // ---- 资源被打包且尺寸合法 -------------------------------------------------

    @Test
    fun `the bundled default textures ship with the launcher`() {
        assertTrue(
            "assets/default_skin.png is missing; an offline account would fall back to vanilla",
            locate(DEFAULT_SKIN_ASSET).isFile
        )
        assertTrue(
            "assets/default_cape.png is missing; an offline account would show no cape",
            locate(DEFAULT_CAPE_ASSET).isFile
        )
    }

    @Test
    fun `the bundled default skin is a layout validateSkinFile accepts`() {
        val (width, height) = pngSizeOf(locate(DEFAULT_SKIN_ASSET))

        // 镜像 wardrobe/LocalSkinUtils.kt 的两条规则：
        // isDualLayerSkin() 要求 64x64，isClassicSkin() 要求 64x32，validateSkinFile 接受其一。
        assertTrue(
            "the default skin must be 64x64 (isDualLayerSkin) or 64x32 (isClassicSkin) " +
                "to pass validateSkinFile, but it is ${width}x$height",
            (width == 64 && height == 64) || (width == 64 && height == 32)
        )
    }

    @Test
    fun `the bundled default cape is exactly 64x32 so validateCapeFile accepts it`() {
        val (width, height) = pngSizeOf(locate(DEFAULT_CAPE_ASSET))

        assertTrue(
            "validateCapeFile requires exactly 64x32, but the default cape is ${width}x$height",
            width == 64 && height == 32
        )
    }

    // ---- 回落只对离线账号生效 -------------------------------------------------

    @Test
    fun `the bundled default is only used for a local account`() {
        val source = code(readMainSource("game/account/offline/OfflineYggdrasilServer.kt"))

        assertTrue(
            "the fallback must be gated on the account being local, so a Microsoft or Ely.by " +
                "skin is never replaced by the bundled default",
            source.contains(FALLBACK_SIGNATURE) &&
                source.contains("if (!isLocalAccount()) return null")
        )

        val fallback = source
            .substringAfter(FALLBACK_SIGNATURE)
            .substringBefore("class OfflineYggdrasilServer")
        assertTrue(
            "the default texture helper must exist and read the bundled asset",
            fallback.contains("GlobalContext.assets.open(assetName)")
        )
        for (foreign in listOf("isMicrosoftAccount", "isElyByAccount", "isAuthServerAccount")) {
            assertFalse(
                "the bundled default must never be derived from $foreign",
                fallback.contains(foreign)
            )
        }
    }

    @Test
    fun `a missing local texture is what triggers the fallback`() {
        val source = code(readMainSource("game/account/offline/OfflineYggdrasilServer.kt"))

        // 本地文件优先，缺失时才回落到内置资源：两次都是 elvis 接在 exists() 之后。
        assertTrue(
            "the skin must be read from the account file first and only then defaulted",
            source.contains("skinFile.takeIf { it.exists() }?.readBytes()") &&
                source.contains("?: account.defaultTextureOrNull(DEFAULT_SKIN_ASSET)")
        )
        assertTrue(
            "the cape must be read from the account file first and only then defaulted",
            source.contains("capeFile.takeIf { it.exists() }?.readBytes()") &&
                source.contains("?: account.defaultTextureOrNull(DEFAULT_CAPE_ASSET)")
        )
    }

    @Test
    fun `an Elyby account is still gated on having both a skin and a cape`() {
        val source = code(readMainSource("game/launch/LaunchArgs.kt"))

        // 放宽的只有离线分支。若这里也被放宽，内置默认皮肤会顶掉 Ely.by 下发的皮肤。
        assertTrue(
            "the Ely.by branch must keep requiring a local skin and cape, otherwise the " +
                "bundled default would override the Ely.by skin",
            source.contains(
                "account.isElyByAccount() && account.hasSkinFile && account.getCapeFile().exists()"
            )
        )
        assertTrue(
            "an offline account must start the server even with no local texture, otherwise " +
                "the bundled defaults are never served",
            !source.contains("if (account.hasSkinFile || account.getCapeFile().exists())")
        )
    }

    // ---- 工具 -----------------------------------------------------------------

    /**
     * 用 ImageIO 读取 PNG 的尺寸
     *
     * 衣橱里的 `validateSkinFile` / `validateCapeFile` 走的是 BitmapFactory，这里用纯 Java
     * 的 ImageIO 得到同一份 IHDR 信息：单元测试里没有任何 Android 类可用，但 PNG 的宽高在
     * 两边读的是同一组字节。只取宽高不解码像素，因此调色板（indexed colour）的皮肤也没问题。
     */
    private fun pngSizeOf(file: File): Pair<Int, Int> {
        assertTrue("missing asset: ${file.path}", file.isFile)
        val bytes = file.readBytes()

        return ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { stream ->
            assertTrue("${file.name} is not a readable image", stream != null)
            val readers = ImageIO.getImageReaders(stream)
            assertTrue("${file.name} has no ImageIO reader", readers.hasNext())

            val reader = readers.next()
            try {
                reader.setInput(stream, true, true)
                reader.getWidth(0) to reader.getHeight(0)
            } finally {
                reader.dispose()
            }
        }
    }

    /**
     * 去掉字符串与注释，只留下真正会被编译的代码
     */
    private fun code(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    private fun readMainSource(relativePath: String): String =
        locate("src/main/java/dev/oxide/launcher/$relativePath").readText()

    /**
     * 从当前工作目录往上找文件
     *
     * 单元测试的工作目录不一定是模块根目录，所以逐级上溯，
     * 找不到就直接报错——绝不能悄悄跳过，那样的测试等于没有。
     */
    private fun locate(relativePath: String): File {
        var dir: File? = File("").absoluteFile
        repeat(8) {
            val candidate = dir?.resolve(relativePath)
            if (candidate != null && candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        error("could not locate $relativePath from ${File("").absolutePath}")
    }

    private companion object {
        const val DEFAULT_SKIN_ASSET = "src/main/assets/default_skin.png"
        const val DEFAULT_CAPE_ASSET = "src/main/assets/default_cape.png"

        /** 内置贴图的回落函数签名，用作截取其函数体的锚点 */
        const val FALLBACK_SIGNATURE =
            "private fun Account.defaultTextureOrNull(assetName: String): ByteArray? {"

        /** 原始字符串：里面可以出现引号、换行与注释符号，必须先换掉 */
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}