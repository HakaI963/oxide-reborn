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

package dev.oxide.launcher.game.control

import dev.oxide.layercontroller.layout.loadLayoutFromFile
import dev.oxide.layercontroller.layout.loadLayoutFromString
import dev.oxide.layercontroller.utils.saveToFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 从测试工作目录往上定位仓库里 src/main 下的某个文件。
 *
 * 单元测试的工作目录是模块目录，仓库文件在它上面；两种相对路径都试一遍：从模块目录出发时
 * 前者是 src/main，从仓库根目录出发时是 OxideLauncher/src/main。做法与 DefaultControlLayoutTest 相同。
 */
private fun locate(relativePath: String): File {
    var dir: File? = File("").absoluteFile
    repeat(8) {
        val base = dir ?: return@repeat
        for (prefix in listOf("src/main/", "OxideLauncher/src/main/")) {
            val candidate = base.resolve(prefix + relativePath)
            if (candidate.isFile) return candidate
        }
        dir = base.parentFile
    }
    error("could not locate src/main/$relativePath from ${File("").absolutePath}")
}

/**
 * 去掉字符串与注释，只留下真正会被编译的代码
 *
 * 顺序要紧：先把字符串换掉，再去注释。否则一个字符串里的行注释符号会把后面整行代码连同它的
 * 花括号一起吃掉，按花括号配对切出来的函数块就会是错的。
 */
private fun code(source: String): String = source
    .replace(RAW_STRING, "\"\"")
    .replace(STRING, "\"\"")
    .replace(BLOCK_COMMENT, " ")
    .replace(LINE_COMMENT, "")

/**
 * 从 [signature] 开始、切出那个声明的函数体（含签名）
 *
 * 没有它就只能对整个文件做子串判断，那样"导出用了对的序列化器"和"别的函数用了"分不开。
 *
 * 找函数体的开括号必须先看圆括号深度：默认参数里的 `= {}`（onFinished: () -> Unit = {}）
 * 同样长得像函数体，按第一个 `{` 切会切出半个签名，后面的断言于是全部落空。
 */
private fun blockOf(source: String, signature: String): String {
    val start = source.indexOf(signature)
    assertTrue("could not find \"$signature\" in the source", start >= 0)
    var parens = 1
    var bodyStart = -1
    var i = start + signature.length
    while (i < source.length && bodyStart < 0) {
        when (source[i]) {
            '(' -> parens++
            ')' -> if (--parens == 0) bodyStart = source.indexOf('{', i)
            '{' -> if (parens == 0) bodyStart = i
        }
        i++
    }
    if (bodyStart < 0) error("\"$signature\" has no body")
    var depth = 0
    for (j in bodyStart until source.length) {
        when (source[j]) {
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return source.substring(start, j + 1)
            }
        }
    }
    error("unbalanced braces after \"$signature\"")
}

/**
 * 导出控制布局
 *
 * 用户要的是 import/export 成对，而 v1.7.0 只有 import。导出这件事有几类错编译期看不见：
 *
 *  1. **写出去的字节必须就是保存写出去的字节**。导出走的是 `ControlLayout.saveToFile`——与保存
 *     同一个序列化器；哪天有人为了导出另起一份 `Json`，导出的文件与保存出来的就会开始分叉，
 *     而导入自己可能再也认不出来。下面的用例把"加载 → 保存 → 再加载 → 再保存"钉成逐字节相同。
 *  2. **目标位置只能来自创建文档契约**。导出必须走系统文件选择器给出的 uri；一旦有人改成自己
 *     拼路径或用存储 API，就要新的权限，而 Android 上那条路早就不是这么用的了。
 *  3. **用户自己的布局永远不被导出这件事动到**。导出的临时文件不许落在布局目录里（否则
 *     refresh() 会把它当成一份布局扫到），失败时只许删自己那一份临时文件。
 *
 * 前一组是纯 JVM 的（走 `saveToFile` / `loadLayoutFromString` 这条真正的加载路径），
 * 后一组只能读源码断言，办法与 OxideContentSurfaceGuardTest 相同。测试里不碰任何 Android API，
 * 也不去真的调起系统文件选择器。
 */
class ControlLayoutExportTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** 随包分发的默认布局：字段最全，最能说明序列化是稳定的 */
    private val assetText = locate("assets/default_layout.json").readText()

    /** 内置兜底布局，同样要能被导出与再导入 */
    private val fallbackText = EMBEDDED_FALLBACK_CONTROL_LAYOUT

    private val managerRaw = locate(
        "java/dev/oxide/launcher/game/control/ControlManager.kt"
    ).readText()
    private val manager = code(managerRaw)

    private val screenRaw = locate(
        "java/dev/oxide/launcher/ui/screens/content/settings/ControlManageScreen.kt"
    ).readText()
    private val screen = code(screenRaw)

    // ---- 导出写出的字节与保存写出的字节 ------------------------------------

    @Test
    fun savingTheSameLayoutTwiceProducesIdenticalBytes() {
        // 导出把序列化结果一次性写进目标流，所以序列化必须是确定的：同一份布局存两次得到同样的字节，
        // 否则"导出 == 保存"这件事无从谈起
        for (text in fixtures()) {
            val layout = loadLayoutFromString(text)
            val first = scratch("first.json")
            val second = scratch("second.json")
            runBlocking { layout.saveToFile(first) }
            runBlocking { layout.saveToFile(second) }
            assertArrayEquals(
                "the layout serialiser is not deterministic",
                first.readBytes(),
                second.readBytes()
            )
        }
    }

    @Test
    fun anExportedLayoutIsByteIdenticalToLoadingItAndSavingItAgain() {
        for (text in fixtures()) {
            // 这一步就是 exportControl 做的事：pack 出来的布局走 saveToFile
            val exported = scratch("exported.json")
            runBlocking { loadLayoutFromString(text).saveToFile(exported) }

            // 而这一步就是"加载一份导出文件再保存一次"，用户拿到的文件因此必须一模一样
            val resaved = scratch("resaved.json")
            runBlocking { loadLayoutFromFile(exported).saveToFile(resaved) }

            assertArrayEquals(
                "exporting must produce the very bytes that saving produces",
                exported.readBytes(),
                resaved.readBytes()
            )
        }
    }

    @Test
    fun anExportedLayoutIsSemanticallyUnchangedSoItStaysEditable() {
        // 逐字节相同还不够：导出与再保存之间夹了一次真正的加载，如果那一趟悄悄改了内容
        // （迁移、丢字段），两次保存的字节就会一样而内容已经不对了
        for (text in fixtures()) {
            val exported = scratch("semantic.json")
            runBlocking { loadLayoutFromString(text).saveToFile(exported) }
            val reloaded = loadLayoutFromString(exported.readText())
            val again = scratch("semantic2.json")
            runBlocking { reloaded.saveToFile(again) }

            assertEquals(
                "the layout changed on its way through save and load",
                Json.parseToJsonElement(exported.readText()),
                Json.parseToJsonElement(again.readText())
            )
        }
    }

    @Test
    fun anExportedLayoutComesBackThroughTheImportPath() {
        // importControl 收的是一串字符再交给 loadLayoutFromString：导出产物必须能被这条
        // 真正的导入路径认回来，否则导出就是单程票
        for (text in fixtures()) {
            val exported = scratch("importable.json")
            runBlocking { loadLayoutFromString(text).saveToFile(exported) }
            val imported = loadLayoutFromString(
                String(exported.readBytes(), Charsets.UTF_8)
            )
            assertTrue(
                "an exported layout must not come back empty",
                imported.layers.isNotEmpty() && imported.info.name.default.isNotBlank()
            )
        }
    }

    // ---- 导出入口用的是文档契约，而不是自己拼的路径 --------------------------

    @Test
    fun theExportOpensItsDestinationWithTheCreateDocumentContract() {
        assertTrue(
            "the export entry point must launch the create-document contract",
            screen.contains("ActivityResultContracts.CreateDocument(")
        )
        assertTrue(
            "the export must write through the content resolver into the chosen uri",
            screen.contains("contentResolver.openOutputStream(")
        )
        assertTrue(
            "the export must go through ControlManager.exportControl",
            screen.contains("ControlManager.exportControl(")
        )
    }

    @Test
    fun theMimeTypeHasOneSourceAndIsNotHardCodedInTheScreen() {
        // 账号备份那几处创建文档用的就是这个类型；这里因此只有一个常量，而不是又抄一份字面量
        assertTrue(
            "ControlManager must declare the layout mime type once",
            managerRaw.contains("CONTROL_LAYOUT_MIME_TYPE = \"application/json\"")
        )
        assertTrue(
            "the screen must use the shared constant",
            screen.contains("ActivityResultContracts.CreateDocument(CONTROL_LAYOUT_MIME_TYPE)")
        )
        assertFalse(
            "the screen must not hard code a second mime type",
            screenRaw.contains("\"application/json\"")
        )
    }

    @Test
    fun theExportNeverWritesThroughAPathItInventedOrAStorageApi() {
        // 自己拼路径的那条路要么需要权限，要么在今天的 Android 上根本写不进去。
        // 这里逐条钉住：导出的落点只能是系统选择器给出的那个 uri。
        val banned = listOf(
            "Environment.getExternalStorageDirectory",
            "MediaStore",
            "openFileOutput",
            "FileOutputStream(",
            "contentResolver.insert(",
            "requestPermissions",
        )
        for (needle in banned) {
            assertFalse("ControlManager must not use $needle", manager.contains(needle))
            assertFalse(
                "the control management screen must not use $needle",
                screen.contains(needle)
            )
        }
    }

    @Test
    fun theExportAsksForNoStoragePermission() {
        for (needle in listOf(
            "Manifest.permission",
            "checkSelfPermission",
            "WRITE_EXTERNAL_STORAGE",
            "READ_EXTERNAL_STORAGE",
            "MANAGE_EXTERNAL_STORAGE",
        )) {
            assertFalse("ControlManager must not touch $needle", manager.contains(needle))
            assertFalse("the screen must not touch $needle", screen.contains(needle))
        }
    }

    // ---- 序列化只有一条路 ---------------------------------------------------

    @Test
    fun theExportSerialisesThroughTheSameFunctionSavingUses() {
        val save = blockOf(manager, "fun saveControl(")
        val export = blockOf(manager, "suspend fun exportControl(")
        assertTrue("saving must go through saveToFile", save.contains("saveToFile("))
        assertTrue(
            "exporting must go through the same saveToFile, not a second serialiser",
            export.contains("saveToFile(")
        )
        for (needle in listOf("encodeToString", "writeText(", "writeBytes(", "Json(", "Gson(")) {
            assertFalse(
                "exportControl must not serialise through $needle",
                export.contains(needle)
            )
        }
    }

    @Test
    fun theExportSerialisesTheLayoutInMemoryRatherThanTheUsersFile() {
        // 与保存一致：从观察包装里 pack 出当前状态，而不是把用户目录里那份文件原样倒出去。
        // 少了 pack，用户在编辑器里改完还没保存的那一版就丢在导出之外了。
        val export = blockOf(manager, "suspend fun exportControl(")
        assertTrue(export.contains("data.controlLayout.pack()"))
    }

    // ---- 用户自己的布局不能被导出这件事动到 ---------------------------------

    @Test
    fun theExportNeverWritesIntoTheLayoutDirectory() {
        // 临时文件一旦落在布局目录里，refresh() 就会把它当成一份布局扫到，用户列表里会凭空
        // 多出一份，随后导出失败又被留在那里
        val export = blockOf(manager, "suspend fun exportControl(")
        assertFalse(
            "the export scratch file must not live in the layout directory",
            export.contains("DIR_CONTROL_LAYOUTS")
        )
        assertTrue(
            "the export scratch file must live in the launcher cache",
            export.contains("DIR_CACHE")
        )
    }

    @Test
    fun theExportOnlyEverDeletesItsOwnScratchFile() {
        val export = blockOf(manager, "suspend fun exportControl(")
        assertTrue(
            "the scratch file must be cleaned up on both the happy and the failing path",
            export.contains("FileUtils.deleteQuietly(tempFile)")
        )
        for (needle in listOf(
            "renameTo(",
            "deleteQuietly(data.file",
            "deleteQuietly(file)",
            "data.file.delete",
            "data.file.writeText",
            "copyTo(data.file",
        )) {
            assertFalse(
                "the export must never rewrite or rename a user layout, found $needle",
                export.contains(needle)
            )
        }
    }

    @Test
    fun theExportCannotWriteAPartialFile() {
        val export = blockOf(manager, "suspend fun exportControl(")
        // 先把完整内容落到自己控制的临时文件里，再一次性写进目标流：
        // 目标位置因此要么是完整的布局，要么根本没被打开过
        assertTrue(
            "the whole payload must be read before the destination is touched",
            export.contains("val bytes = tempFile.readBytes()")
        )
        assertTrue(
            "the destination must be written in one write call",
            export.contains("stream.write(bytes)")
        )
        // 分多次写就意味着写到一半可能断：目标位置因此会留下一份截断的 json，
        // 而它看上去又是一个合法的文件，导入时才会被发现
        assertEquals(
            "the destination must be written in exactly one call",
            1,
            Regex("stream\\.write\\(").findAll(export).count()
        )
    }

    @Test
    fun theExportRefusesALayoutItCannotParse() {
        // 不受支持的布局从未被完整解析过，导出去只会得到一份加载器自己也读不回来的东西
        val export = blockOf(manager, "suspend fun exportControl(")
        assertTrue(export.contains("if (!data.isSupport)"))
    }

    // ---- 导入必须原样保留 ---------------------------------------------------

    @Test
    fun theImportPathIsUntouched() {
        // 导出是加法，不是搬家：导入仍然写随机名的新文件、仍然失败即删、仍然不碰已有布局
        val import = blockOf(manager, "suspend fun importControl(")
        assertTrue("import must still write a fresh random file", import.contains("getNewRandomFile()"))
        assertTrue(
            "import must still parse through the real loader",
            import.contains("loadLayoutFromString(")
        )
        assertTrue("import must still save through saveToFile", import.contains("saveToFile("))
        assertEquals(
            "import must still delete its own file on both failure paths",
            2,
            Regex("FileUtils\\.deleteQuietly\\(file\\)").findAll(import).count()
        )
        assertTrue(
            "import must still accept a plain input stream",
            blockOf(manager, "suspend fun importControl(").contains("inputStream")
        )
    }

    @Test
    fun theBundledDefaultAndItsFallbackStayInPlace() {
        // 内置兜底布局是另一条改动线加的，导出改动不许把它挤掉：那份 json 一旦没了，
        // 默认布局解不开时用户目录里会一份布局都没有
        assertTrue(
            "the embedded fallback layout must stay declared",
            managerRaw.contains("internal const val EMBEDDED_FALLBACK_CONTROL_LAYOUT")
        )
        assertTrue(
            "the bundled asset path must stay declared",
            managerRaw.contains("DEFAULT_LAYOUT_ASSET = \"default_layout.json\"")
        )
        assertTrue(
            "the fallback layout must still load through the real schema",
            runCatching { loadLayoutFromString(fallbackText).layers.isNotEmpty() }
                .getOrDefault(false)
        )
    }

    // ---- helpers -----------------------------------------------------------

    /** 两份真实布局：随包分发的那份字段最全，内置兜底那份是最小可用形态 */
    private fun fixtures(): List<String> = listOf(assetText, fallbackText)

    /** saveToFile 会先写 .tmp 再 rename，因此占位文件先建好也无所谓 */
    private fun scratch(name: String): File = folder.newFile(name)
}

private val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
private val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
private val LINE_COMMENT = Regex("""//[^\n]*""")