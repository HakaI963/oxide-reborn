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

package dev.oxide.launcher.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.oxide.layercontroller.layout.ControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromFile
import dev.oxide.launcher.R
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.ui.base.BaseAppCompatActivity
import dev.oxide.launcher.ui.screens.content.elements.Background
import dev.oxide.launcher.ui.screens.main.control_editor.ControlEditor
import dev.oxide.launcher.ui.theme.OxideTheme
import dev.oxide.launcher.ui.theme.ProvideOxideChrome
import dev.oxide.launcher.ui.theme.backgroundColor
import dev.oxide.launcher.ui.theme.onBackgroundColor
import dev.oxide.launcher.viewmodel.BackgroundViewModel
import dev.oxide.launcher.viewmodel.EditorViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

private const val BUNDLE_CONTROL = "BUNDLE_CONTROL"

@AndroidEntryPoint
class ControlEditorActivity : BaseAppCompatActivity() {
    override fun isIgnoreNotch(): Boolean = AllSettings.gameFullScreen.getValue()

    override fun getTaskDescriptionTitle(): String = getString(R.string.control_manage_info_edit)

    /** 编辑器 */
    private val editorViewModel: EditorViewModel by viewModels()

    /**
     * 启动器背景内容管理 ViewModel
     */
    private val backgroundViewModel: BackgroundViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /** 控制布局绝对路径 */
        val controlPath: String = intent.extras?.getString(BUNDLE_CONTROL) ?: return runFinish()
        /** 控制布局文件 */
        val controlFile: File = File(controlPath).takeIf { it.isFile && it.exists() } ?: return runFinish()
        /** 控制布局 */
        val layout: ControlLayout = runCatching {
            loadLayoutFromFile(controlFile)
        }.getOrNull() ?: return runFinish()

        //初始化控制布局
        editorViewModel.initLayout(layout)

        //绑定返回键按下事件，防止直接退出导致控制布局丢失所有变更
        //提醒用户保存并退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                editorViewModel.onBackPressed(context = this@ControlEditorActivity) {
                    this@ControlEditorActivity.finish()
                }
            }
        })

        setContent {
            OxideTheme(
                backgroundViewModel = backgroundViewModel
            ) {
                // 界面根上还需要再包一层，见 dev.oxide.launcher.ui.theme.ProvideOxideChrome。
                // 没有它编辑器读到的是调色板的默认值而不是用户当前选的那一套
                ProvideOxideChrome {
                    // 这里**不**再挂 GuideHost。
                    //
                    // 过去第一次打开编辑器会自动播一遍 Zalith 时代的那条引导流
                    // （`guides.startOnce(GuideKeys.Editor)`）：先一句欢迎，再逐个高亮悬浮球、
                    // 控件层列表、新建层、新建控件、外观列表、预览、保存。其中悬浮球那一步就是
                    // 用户报的那张图（`controltutorial.jpg`），它讲的是旧编辑器里那个悬浮球的用法，
                    // 与 Oxide 停靠面板的实际操作对不上，也把新面板盖住了。
                    //
                    // 引导进度存在 MMKV 的 `started_Editor` 上，因此**已经看过**的那一批安装
                    // 不会再看到它；而这一版起没有任何调用点会去播它，新装与升级装同样不会看到。
                    // 主界面那条引导仍由 MainActivity 自己的 GuideHost 承载，没有受影响。
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = backgroundColor(),
                        contentColor = onBackgroundColor()
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Background(
                                modifier = Modifier.fillMaxSize(),
                                viewModel = backgroundViewModel,
                                allowVideo = false
                            )

                            ControlEditor(
                                viewModel = editorViewModel,
                                targetFile = controlFile,
                                exit = {
                                    //已保存控制布局后进行的退出
                                    finish()
                                },
                                menuExit = {
                                    //菜单要求的直接退出，使用对话框让用户确认
                                    editorViewModel.showExitEditorDialog(
                                        context = this@ControlEditorActivity,
                                        onExit = {
                                            this@ControlEditorActivity.finish()
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 开启控制布局编辑器
 */
fun startEditorActivity(context: Context, file: File) {
    val intent = Intent(context, ControlEditorActivity::class.java).apply {
        putExtra(BUNDLE_CONTROL, file.absolutePath)
    }
    context.startActivity(intent)
}