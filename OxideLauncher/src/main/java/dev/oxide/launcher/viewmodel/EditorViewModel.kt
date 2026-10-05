/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.oxide.layercontroller.data.HideLayerWhen
import dev.oxide.layercontroller.layout.ControlLayout
import dev.oxide.layercontroller.observable.ObservableButtonStyle
import dev.oxide.layercontroller.observable.ObservableControlLayer
import dev.oxide.layercontroller.observable.ObservableControlLayout
import dev.oxide.layercontroller.observable.ObservableJoystickData
import dev.oxide.layercontroller.observable.ObservableJoystickStyle
import dev.oxide.layercontroller.observable.ObservableNormalData
import dev.oxide.layercontroller.observable.ObservableTextData
import dev.oxide.layercontroller.observable.ObservableWidget
import dev.oxide.layercontroller.observable.cloneJoystick
import dev.oxide.layercontroller.observable.cloneNormal
import dev.oxide.layercontroller.observable.cloneText
import dev.oxide.layercontroller.utils.saveToFile
import dev.oxide.launcher.R
import dev.oxide.launcher.ui.components.MenuState
import dev.oxide.launcher.ui.screens.main.control_editor.EditorOperation
import dev.oxide.launcher.ui.screens.main.control_editor.EditorWarningOperation
import dev.oxide.launcher.ui.screens.main.control_editor.EditorWidgetOperation
import dev.oxide.launcher.ui.screens.main.control_editor.PreviewScenario
import dev.oxide.launcher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import dev.oxide.launcher.ui.theme.showThemed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 控制布局编辑器
 */
class EditorViewModel : ViewModel() {
    lateinit var observableLayout: ObservableControlLayout
        private set

    /**
     * 当前选中的控件层
     */
    var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)

    /**
     * 当前选中的组件（仅用于编辑组件对话框）
     */
    var selectedWidget by mutableStateOf<SelectedWidgetData?>(null)

    /**
     * 当前选中的控件样式（仅用于样式编辑对话框）
     */
    var selectedStyle by mutableStateOf<ObservableButtonStyle?>(null)

    /**
     * 当前选中的摇杆样式（仅用于摇杆样式编辑对话框）
     */
    var selectedJoystickStyle by mutableStateOf<ObservableJoystickStyle?>(null)

    /**
     * 编辑器菜单状态
     */
    var editorMenu by mutableStateOf(MenuState.HIDE)

    /**
     * 编辑器菜单悬浮球当前的位置，单位是像素
     *
     * null 表示**还没落位**，由 [dev.oxide.launcher.ui.screens.main.control_editor.EditorBall]
     * 摆到安全区里横向居中、贴顶的那一处。以前这里拿 [Offset.Zero] 同时表示"没摆过"
     * 和"摆在左上角"这两件事，于是球第一次出现就在左上角（压在圆角上），
     * 而且 `Offset.Zero` 与合法的落位无法区分。
     *
     * 只存在内存里：转屏、折叠屏展开与转屏之外的重开都会重新落位。
     */
    var editorBallPosition by mutableStateOf<Offset?>(null)

    /**
     * 编辑器各种操作项
     */
    var editorOperation by mutableStateOf<EditorOperation>(EditorOperation.None)

    /**
     * 编辑器对于控件的操作项
     */
    var editorWidgetOperation by mutableStateOf<EditorWidgetOperation>(EditorWidgetOperation.None)

    /**
     * 编辑器的一些警告状态项
     */
    var editorWarningOperation by mutableStateOf<EditorWarningOperation>(EditorWarningOperation.None)

    /**
     * 是否开启控件层聚焦模式
     */
    var isLayerFocus by mutableStateOf(false)

    /**
     * 是否为预览控制布局模式
     */
    var isPreviewMode by mutableStateOf(false)

    /**
     * 预览控制布局的场景
     */
    var previewScenario by mutableStateOf(PreviewScenario.InMenu)

    /**
     * 预览控制布局时根据设备隐藏控制层
     */
    var previewHideLayerWhen by mutableStateOf(HideLayerWhen.None)



    fun initLayout(layout: ControlLayout) {
        if (!::observableLayout.isInitialized) {
            this.observableLayout = ObservableControlLayout(layout)
        }
    }



    /**
     * 切换编辑器菜单
     */
    fun switchMenu() {
        editorMenu = editorMenu.next()
    }

    /**
     * 移除控件层
     */
    fun removeLayer(layer: ObservableControlLayer) {
        if (layer == selectedLayer) selectedLayer = null
        observableLayout.removeLayer(layer.uuid)
    }

    /**
     * 为控件层添加控件
     */
    fun addWidget(layers: List<ObservableControlLayer>, addToLayer: (ObservableControlLayer) -> Unit) {
        val layer = selectedLayer
        if (layers.isEmpty()) {
            editorWarningOperation = EditorWarningOperation.WarningNoLayers
        } else if (layer == null) {
            editorWarningOperation = EditorWarningOperation.WarningNoSelectLayer
        } else {
            addToLayer(layer)
        }
    }

    /**
     * 在控件层移除控件
     */
    fun removeWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
            is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
        }
    }

    /**
     * 将控件复制到控件层
     */
    fun cloneWidgetToLayers(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        when (widget) {
            is ObservableNormalData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneNormal()
                    layer.addNormalButton(newData)
                }
            }
            is ObservableTextData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneText()
                    layer.addTextBox(newData)
                }
            }
            is ObservableJoystickData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneJoystick()
                    layer.addJoystickButton(newData)
                }
            }
        }
    }

    /**
     * 创建一个新的控件外观
     */
    fun createNewStyle(name: String) {
        observableLayout.addStyle(
            dev.oxide.layercontroller.data.createNewButtonStyle(name)
        )
    }

    /**
     * 复制控件外观
     */
    fun cloneStyle(style: ObservableButtonStyle) {
        observableLayout.cloneStyle(style)
    }

    /**
     * 删除一个控件外观
     */
    fun removeStyle(style: ObservableButtonStyle) {
        observableLayout.removeStyle(style.uuid)
    }

    /**
     * 移除一个摇杆样式
     */
    fun removeJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.removeJoystickStyle(style.uuid)
    }

    /**
     * 创建一个新的摇杆样式
     */
    fun createNewJoystickStyle(name: String) {
        observableLayout.addJoystickStyle(
            dev.oxide.layercontroller.data.createNewJoystickStyle(name)
        )
    }

    /**
     * 复制摇杆样式
     */
    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.cloneJoystickStyle(style)
    }

    /**
     * 将编辑器内层级隐藏状态同步到实际隐藏状态
     * 供预览模式下使用正确的隐藏状态
     */
    fun applyEditorHide() {
        observableLayout.applyEditorHide()
    }

    /**
     * 保存控制布局
     */
    fun save(
        targetFile: File,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            editorOperation = EditorOperation.Saving
            val layout = observableLayout.pack()
            runCatching {
                layout.saveToFile(targetFile)
            }.onFailure { e ->
                editorOperation = EditorOperation.SaveFailed(e)
            }.onSuccess {
                editorOperation = EditorOperation.None
                onSaved()
            }
        }
    }

    fun onBackPressed(
        context: Context,
        onExit: () -> Unit
    ) {
        //检查并退出编辑控件对话框、编辑控件样式对话框
        if (editorOperation is EditorOperation.SelectButton || editorOperation is EditorOperation.EditButtonStyle) {
            editorOperation = EditorOperation.None
        } else {
            showExitEditorDialog(
                context = context,
                onExit = onExit
            )
        }
    }

    /**
     * 用于检查控制布局是否被修改过
     */
    private val checkModified = Mutex()

    /**
     * 弹出退出控制布局编辑器的对话框
     * @param onExit 用户点击确认，退出编辑器
     */
    fun showExitEditorDialog(
        context: Context,
        onExit: () -> Unit
    ) {
        viewModelScope.launch {
            val isModified = checkModified.withLock {
                observableLayout.isModified()
            }
            if (isModified) {
                showExitEditorDialogSuspend(
                    context = context,
                    onExit = onExit
                )
            } else {
                //未被修改，可以直接退出
                onExit()
            }
        }
    }

    private suspend fun showExitEditorDialogSuspend(
        context: Context,
        onExit: () -> Unit
    ) = withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.generic_warning)
            .setMessage(R.string.control_editor_exit_message)
            .setPositiveButton(R.string.generic_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.control_editor_exit_confirm) { dialog, _ ->
                dialog.dismiss()
                onExit()
            }
            .showThemed()
    }
}