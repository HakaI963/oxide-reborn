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
 * along with this program. If not, see <https://www.gnu.org/licenses/gpl-3.0.html>.
 */

package dev.oxide.launcher.ui.screens.main.control_editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 选中层对齐规则的钉死测试
 *
 * 用户报的那条"第一次能编辑，保存之后一个控件都加不了"落在 [editorReconcileSelectedLayer]
 * 这一段算术上：编辑器被"保存并退出"finish 掉之后重新进来是一个新的 ViewModel，
 * selectedLayer 从 null 开始，而 null 会让 [editorAddBlocker] 判成
 * [EditorAddBlocker.NoSelectedLayer]、[editorAllowsAddingControls] 于是把三个新建按钮
 * 全部禁用。恢复的是选中项，不是把闸门放松——所以下面第二组测试把闸门本身也钉住。
 */
class EditorSelectedLayerReconcileTest {

    private val layers = listOf("0f1a4c7a0a75", "048cf5d399b3", "cafe")

    @Test
    fun `还选着的那一层继续被选中`() {
        assertEquals(
            "0f1a4c7a0a75",
            editorReconcileSelectedLayer(currentUuid = "0f1a4c7a0a75", layerUuids = layers),
        )
        assertEquals(
            "048cf5d399b3",
            editorReconcileSelectedLayer(currentUuid = "048cf5d399b3", layerUuids = layers),
        )
    }

    @Test
    fun `选中的是列表里的最后一项时也保持不动`() {
        // 用 last 而不是 [0]：只钉住第一项的话，"越靠后越容易被换成第一项"这种回退看不出来
        assertEquals(
            "cafe",
            editorReconcileSelectedLayer(currentUuid = layers.last(), layerUuids = layers),
        )
    }

    @Test
    fun `选中项为空但有层时退到第一个`() {
        assertEquals(
            "0f1a4c7a0a75",
            editorReconcileSelectedLayer(currentUuid = null, layerUuids = layers),
        )
    }

    @Test
    fun `选中的那一层被删掉时退到还剩下的层`() {
        // 这正是 removeLayer 走的那一步：删掉选中的那一层之后不能停在 null 上
        assertEquals(
            "cafe",
            editorReconcileSelectedLayer(currentUuid = "0f1a4c7a0a75", layerUuids = listOf("cafe")),
        )
        // 还在列表里的 uuid 一个都不能被换掉，哪怕它排在最后
        assertEquals(
            "048cf5d399b3",
            editorReconcileSelectedLayer(currentUuid = "048cf5d399b3", layerUuids = listOf("cafe", "048cf5d399b3")),
        )
    }

    @Test
    fun `一个层都没有时仍然是空`() {
        // 空列表是"真的一个层都没有"，这时必须返回 null，好让 NoLayers 那条闸门照旧响
        assertNull(editorReconcileSelectedLayer(currentUuid = null, layerUuids = emptyList()))
        assertNull(editorReconcileSelectedLayer(currentUuid = "0f1a4c7a0a75", layerUuids = emptyList()))
    }

    @Test
    fun `退到的是列表里的第一个而不是集合的迭代顺序`() {
        // 同一个集合用不同的顺序塞进去，Set 的迭代顺序不保证一致；若实现里先转成 Set
        // 再取第一个，"退到第一个"就会变成"退到随机一个"，同一份布局每次进来落在不同层上。
        // 因此这里断言的是**列表的头**，并且把 Set 自己的头写进失败消息里做对照。
        val uuids = listOf("zulu", "alpha", "mike", "delta", "kilo", "bravo", "yankee", "tango")
        val setHead = HashSet(uuids).first()
        assertEquals(
            "reconcile picked the Set iteration order ($setHead) instead of the list head",
            uuids.first(),
            editorReconcileSelectedLayer(currentUuid = null, layerUuids = uuids),
        )
        // 再多来几次：结果必须是确定的
        repeat(5) {
            assertEquals(uuids.first(), editorReconcileSelectedLayer(null, uuids))
        }
    }

    @Test
    fun `对齐回来之后新建按钮不再是死的`() {
        // 闸门本身没动：变的是 hasSelectedLayer 的输入。把这两件事放在一处断言，
        // 才不会有人以后"顺手"把 NoSelectedLayer 那一档删掉来让界面看起来正常。
        val before = editorAddBlocker(
            layerCount = layers.size,
            hasSelectedLayer = false,
            isPreviewMode = false,
        )
        assertEquals(EditorAddBlocker.NoSelectedLayer, before)
        assertFalse(editorAllowsAddingControls(before))

        val after = editorAddBlocker(
            layerCount = layers.size,
            hasSelectedLayer = editorReconcileSelectedLayer(null, layers) != null,
            isPreviewMode = false,
        )
        assertEquals(EditorAddBlocker.None, after)
        assertTrue(editorAllowsAddingControls(after))
    }
}

/**
 * 闸门本身不许被放松
 *
 * 这一组是本次修复的另一半：选中项恢复之后，"没有层""没选中层""预览模式"这三档仍然要
 * 各自响。新增控件的三个按钮仍然走同一套判定，`addWidget` 的三种警告也仍然一一对应。
 */
class EditorAddBlockerUnchangedTest {

    @Test
    fun `四档判定一个都没少`() {
        assertEquals(
            listOf(
                EditorAddBlocker.None,
                EditorAddBlocker.NoLayers,
                EditorAddBlocker.NoSelectedLayer,
                EditorAddBlocker.Preview,
            ),
            EditorAddBlocker.entries.toList(),
        )
    }

    @Test
    fun `预览模式先于其余三档判定`() {
        // 顺序有讲究：预览模式下画布只读，因此哪怕一层都没选、也一个层都没有，
        // 报出来的仍然是 Preview。这一档排在最前面，恢复选中项才不会顺手放开编辑。
        assertEquals(
            EditorAddBlocker.Preview,
            editorAddBlocker(layerCount = 0, hasSelectedLayer = false, isPreviewMode = true),
        )
        assertEquals(
            EditorAddBlocker.Preview,
            editorAddBlocker(layerCount = 2, hasSelectedLayer = true, isPreviewMode = true),
        )
        assertFalse(
            editorAllowsAddingControls(
                editorAddBlocker(layerCount = 2, hasSelectedLayer = true, isPreviewMode = true)
            )
        )
    }

    @Test
    fun `真的没有层时仍然是 NoLayers`() {
        // 对齐函数对空列表返回 null，所以这一档仍然会在真正空的布局上响
        val layerUuids = emptyList<String>()
        val uuid = editorReconcileSelectedLayer(currentUuid = "0f1a4c7a0a75", layerUuids = layerUuids)
        assertNull(uuid)
        assertEquals(
            EditorAddBlocker.NoLayers,
            editorAddBlocker(
                layerCount = layerUuids.size,
                hasSelectedLayer = uuid != null,
                isPreviewMode = false,
            ),
        )
    }

    @Test
    fun `新建控件与几何编辑都还认这一档`() {
        assertFalse(
            "预览模式下位置与尺寸仍然是锁住的",
            editorAllowsGeometryEditing(
                isPreviewMode = true,
                blocker = EditorAddBlocker.Preview,
            ),
        )
        assertFalse("没有选中控件时没有几何可改", editorShowsGeometrySection(null))
        assertTrue(editorShowsGeometrySection(EditorWidgetKind.Button))
        assertTrue("预览模式下控制层不能改名换序删除", !editorAllowsLayerEditing(true))
    }
}

/**
 * EditorViewModel 必须真的去对齐，而不是只在纯函数里写着好看
 *
 * [EditorSelectedLayerReconcileTest] 钉的是规则；这一组钉的是"有人把它接上了"。
 * 判据是读源码——`initLayout` 由 Activity 在 onCreate 里调用，一个纯 JVM 单测既构造不出
 * 那个 ViewModel（它要 Compose 快照状态与 Android 生命周期），也不该为了这一个断言
 * 把 Robolectric 引进这个模块。
 */
class EditorViewModelSelectionSourceTest {

    private val vm = codeOf(locate("java/dev/oxide/launcher/viewmodel/EditorViewModel.kt").readText())

    @Test
    fun `选中项会在层列表变化时被重新对齐`() {
        assertTrue(
            "the reconcile rule has to be imported for this to compile",
            vm.contains("import dev.oxide.launcher.ui.screens.main.control_editor.editorReconcileSelectedLayer"),
        )
        // 订阅必须挂在 initLayout 上：重新进来的是一个全新的 ViewModel，
        // 而 initLayout 是这个 ViewModel 唯一知道"有哪些层"的那一刻
        val initLayout = blockOf(vm, "fun initLayout")
        assertTrue(
            "initLayout has to start watching the layer list; got $initLayout",
            initLayout.contains("observeLayersForSelection()"),
        )
        val collector = blockOf(vm, "fun observeLayersForSelection")
        for (token in listOf(
            "viewModelScope.launch",
            "observableLayout.layers.collect",
            "reconcileSelectedLayer(layers)",
        )) {
            assertTrue("the layer collector must contain `$token`; got $collector", collector.contains(token))
        }
    }

    @Test
    fun `对齐走的是那一条纯规则而不是就地取第一个`() {
        val reconcile = blockOf(vm, "fun reconcileSelectedLayer")
        assertTrue(
            "the selection must be reconciled by uuid through the shared pure function; got $reconcile",
            reconcile.contains("editorReconcileSelectedLayer("),
        )
        // 记着上一次选中的是谁，删掉那一层之后才退得到同一层上
        assertTrue(
            "the last selected uuid has to be remembered, not the layer list order; got $reconcile",
            reconcile.contains("selectedLayer?.uuid ?: lastSelectedLayerUuid"),
        )
        // 必须落回列表里现在这一个对象：面板是按引用比对选中态的
        assertTrue(
            "the selection has to be resolved to the live layer object; got $reconcile",
            reconcile.contains("layers.firstOrNull { it.uuid == uuid }"),
        )
        assertTrue(
            "an unchanged selection must not be reassigned; got $reconcile",
            reconcile.contains("if (selectedLayer !== resolved) selectedLayer = resolved"),
        )
    }

    @Test
    fun `删除选中的那一层会立刻退到别的层`() {
        val removeLayer = blockOf(vm, "fun removeLayer")
        assertTrue(
            "removing the selected layer must reconcile against what is left; got $removeLayer",
            removeLayer.contains("observableLayout.removeLayer(layer.uuid)"),
        )
        assertTrue(
            "removing a layer must not leave the editor with no selection at all; got $removeLayer",
            removeLayer.contains("reconcileSelectedLayer(observableLayout.layers.value)"),
        )
    }

    @Test
    fun `选中的仍然只有一个真相`() {
        // selectedLayer 仍然是那个可观察的 ObservableControlLayer?，uuid 只作为一份记忆存在。
        // 另立一个平行的"选中的 uuid"字段只会带来两份可能对不上的状态。
        assertTrue(
            "selectedLayer must stay an observable ObservableControlLayer?; got $vm",
            vm.contains("var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)"),
        )
        assertTrue(
            "the uuid memory has to be written where the selection is written",
            vm.contains("if (value != null) lastSelectedLayerUuid = value.uuid"),
        )
        for (banned in listOf(
            "var selectedLayerUuid",
            "var selectedLayerName",
            "mutableStateOf<String?>",
        )) {
            assertFalse("a parallel selection state must not appear: $banned", vm.contains(banned))
        }
    }

    @Test
    fun `新增控件的两种警告仍然都在`() {
        // 对齐只恢复选中项，不许顺手把 addWidget 的警告删掉：布局真的空了、或者
        // 预览模式下，它还得自己说话。
        val addWidget = blockOf(vm, "fun addWidget")
        assertTrue(
            "an empty layout must still warn; got $addWidget",
            addWidget.contains("EditorWarningOperation.WarningNoLayers"),
        )
        assertTrue(
            "an unselected layer must still warn; got $addWidget",
            addWidget.contains("EditorWarningOperation.WarningNoSelectLayer"),
        )
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    /**
     * 以 [anchor] 开头的那一段 `{ ... }`
     *
     * 字符串字面量与注释已经被 [codeOf] 抹掉，因此这里数花括号就够：URL 或 KDoc 里的
     * 花括号不会把结果带偏。
     */
    private fun blockOf(source: String, anchor: String): String {
        val start = source.indexOf(anchor)
        assertTrue("could not find `$anchor`", start >= 0)
        val open = source.indexOf("{", start)
        assertTrue("could not find the body of `$anchor`", open >= 0)
        var depth = 0
        var index = open
        while (index < source.length) {
            when (source[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, index + 1)
                }
            }
            index++
        }
        error("unbalanced block starting at $open")
    }

    /**
     * 抹掉字符串字面量与注释，只留下真正会被编译的东西
     *
     * 顺序要紧：先字符串后注释。字符串里的 `//`（一个 URL 里的）否则会开出一个行注释，
     * 把后面整段吃掉，于是真正的失败变成一次无声的通过。
     */
    private fun codeOf(source: String): String = source
        .replace(RAW_STRING, "\"\"")
        .replace(STRING, "\"\"")
        .replace(BLOCK_COMMENT, " ")
        .replace(LINE_COMMENT, "")

    /**
     * 在模块的 src/main 下定位一个文件
     *
     * 两种前缀都试一遍：单元测试的工作目录是模块目录（`src/main/…`），从仓库根目录跑时
     * 又是 `OxideLauncher/src/main/…`。定位不到就报错，而不是跳过——跳过的守卫不是守卫。
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

    private companion object {
        val RAW_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        val STRING = Regex("\"(?:\\\\.|[^\"\\\\\\n])*\"")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
    }
}