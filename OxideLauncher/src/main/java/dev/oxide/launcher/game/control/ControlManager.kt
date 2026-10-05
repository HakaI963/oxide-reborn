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

package dev.oxide.launcher.game.control

import android.content.Context
import dev.oxide.layercontroller.layout.ControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromFile
import dev.oxide.layercontroller.layout.loadLayoutFromFileUncheck
import dev.oxide.layercontroller.layout.loadLayoutFromString
import dev.oxide.layercontroller.observable.ObservableControlLayout
import dev.oxide.layercontroller.utils.newRandomFileName
import dev.oxide.layercontroller.utils.saveToFile
import dev.oxide.launcher.context.copyAssetFile
import dev.oxide.launcher.path.PathManager
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.utils.file.readString
import dev.oxide.launcher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val TAG = "ControlManager"

/**
 * assets 里的默认控制布局文件名
 */
private const val DEFAULT_LAYOUT_ASSET = "default_layout.json"

/**
 * 控制布局文件的后缀，创建文档契约与默认导出文件名共用它
 */
internal const val CONTROL_LAYOUT_EXTENSION = "json"

/**
 * 控制布局在系统文件选择器里的 MimeType
 *
 * 与账号备份那几处创建文档的用法完全一致，因此导出不需要新常量、新权限或新依赖。
 */
internal const val CONTROL_LAYOUT_MIME_TYPE = "application/json"

/**
 * 内置的兜底控制布局。
 *
 * 存在的唯一理由是：assets 里的 [DEFAULT_LAYOUT_ASSET] 读不出来、或者读出来但解析不过
 * （缺字段、越界、版本号不受支持）时，[unpackDefaultControl] 仍然能落一份**能加载**的布局到
 * 用户的布局目录。否则用户会落到"目录里一份布局都没有"的状态：checkDefaultAndRefresh 每次
 * 启动都判定目录为空并重试解压，AllSettings.controlLayout 选不出任何布局，而控件编辑器里
 * 一份可编辑的布局都没有，编辑器就此不可用。
 *
 * 它刻意保持最小：一个始终可见的控件层、一个按键按钮、一份样式，形状与 assets 里那份完全一致
 * （同样是 info / layers / styles / editorVersion），因此走过同一条 loadLayoutFromString 校验。
 * editorVersion 必须不超过 LayerController 的 EDITOR_VERSION（当前 12）；写死 12 之后即使
 * 编辑器版本再往上加也依然合法，而写更小的值反而会平白触发一次 11 -> 12 迁移。
 * 这**不是** assets 里默认布局的副本，两者互不覆盖、互为补充。
 */
internal const val EMBEDDED_FALLBACK_CONTROL_LAYOUT = """
{
    "info": {
        "name": {
            "default": "oxide-fallback",
            "matchQueue": []
        },
        "author": {
            "default": "oxide-mc",
            "matchQueue": []
        },
        "description": {
            "default": "built-in fallback, written when the bundled default layout cannot be read",
            "matchQueue": []
        },
        "versionCode": 0,
        "versionName": "1.0"
    },
    "layers": [
        {
            "name": "oxide-fallback",
            "uuid": "cafecafecafe",
            "hide": false,
            "hideWhenMouse": false,
            "hideWhenGamepad": false,
            "visibilityType": "always",
            "normalButtons": [
                {
                    "text": {
                        "default": "esc",
                        "matchQueue": []
                    },
                    "uuid": "deadbeefdeadbeef01",
                    "position": {
                        "x": 1245,
                        "y": 383
                    },
                    "buttonSize": {
                        "type": "percentage",
                        "widthDp": 50.0,
                        "heightDp": 50.0,
                        "widthPercentage": 1015,
                        "heightPercentage": 1015,
                        "widthReference": "screen_height",
                        "heightReference": "screen_height"
                    },
                    "buttonStyle": "cafebabecaf1",
                    "visibilityType": "always",
                    "clickEvents": [
                        {
                            "type": "key",
                            "key": "GLFW_KEY_ESCAPE"
                        }
                    ],
                    "isSwipple": false,
                    "isPenetrable": false,
                    "isToggleable": false
                }
            ]
        }
    ],
    "styles": [
        {
            "name": "oxide-fallback",
            "uuid": "cafebabecaf1",
            "animateSwap": false,
            "commonStyle": true,
            "lightStyle": {
                "alpha": 1.0,
                "pressedAlpha": 1.0,
                "backgroundColor": 788529152,
                "pressedBackgroundColor": 3012069512,
                "contentColor": 4294967295,
                "pressedContentColor": 4294967295,
                "borderWidth": 0,
                "pressedBorderWidth": 0,
                "borderColor": 4294967295,
                "pressedBorderColor": 4294967295,
                "borderRadius": {
                    "topStart": 20.0,
                    "topEnd": 20.0,
                    "bottomEnd": 20.0,
                    "bottomStart": 20.0
                },
                "pressedBorderRadius": {
                    "topStart": 0.0,
                    "topEnd": 0.0,
                    "bottomEnd": 0.0,
                    "bottomStart": 0.0
                }
            },
            "darkStyle": {
                "alpha": 1.0,
                "pressedAlpha": 1.0,
                "backgroundColor": 788529152,
                "pressedBackgroundColor": 3012069512,
                "contentColor": 4294967295,
                "pressedContentColor": 4294967295,
                "borderWidth": 0,
                "pressedBorderWidth": 0,
                "borderColor": 4294967295,
                "pressedBorderColor": 4294967295,
                "borderRadius": {
                    "topStart": 20.0,
                    "topEnd": 20.0,
                    "bottomEnd": 20.0,
                    "bottomStart": 20.0
                },
                "pressedBorderRadius": {
                    "topStart": 0.0,
                    "topEnd": 0.0,
                    "bottomEnd": 0.0,
                    "bottomStart": 0.0
                }
            }
        }
    ],
    "editorVersion": 12
}
""".trimIndent()

/**
 * 控制布局管理者
 */
object ControlManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _dataList = MutableStateFlow<List<ControlData>>(emptyList())
    val dataList = _dataList.asStateFlow()

    private var currentJob: Job? = null

    private val _selectedLayout = MutableStateFlow<ControlData?>(null)
    /** 当前选择的控制布局 */
    val selectedLayout = _selectedLayout.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** 是否正在刷新控制布局 */
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * 获取一个新的布局文件文件，名称随机
     */
    private fun getNewRandomFile() = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")

    /**
     * 检查当前是否不存在控制布局，不存在则解压一份默认控制布局
     * @param context 访问assets的上下文
     */
    fun checkDefaultAndRefresh(context: Context) {
        scope.launch(Dispatchers.IO) {
            val files = (PathManager.DIR_CONTROL_LAYOUTS.listFiles() ?: emptyArray())
                .filter { file ->
                    file.isFile && file.exists() && file.extension.equals("json", true)
                }
            if (files.isEmpty()) {
                unpackDefaultControl(context)
            }
            refresh()
        }
    }

    fun refresh() {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.update { true }

            _dataList.update { emptyList() }
            PathManager.DIR_CONTROL_LAYOUTS.listFiles()?.mapNotNull { file ->
                if (!(file.isFile && file.exists() && file.extension.equals("json", true))) return@mapNotNull null

                var isSupport = true
                val layout: ControlLayout = try {
                    loadLayoutFromFile(file)
                } catch (_: IllegalArgumentException) {
                    isSupport = false
                    runCatching {
                        loadLayoutFromFileUncheck(file)
                    }.onFailure { e ->
                        Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    }.getOrNull() ?: return@mapNotNull null
                } catch (e: Exception) {
                    Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    return@mapNotNull null
                }

                ControlData(
                    file = file,
                    controlLayout = ObservableControlLayout(layout),
                    isSupport = isSupport
                )
            }?.let { list ->
                _dataList.update {
                    list.sortedBy {
                        if (it.isSupport) it.controlLayout.info.name.default
                        else it.file.name
                    }
                }
            }
            checkSettings()

            _isRefreshing.update { false }
        }
    }

    /**
     * 检查并更新设置
     */
    private fun checkSettings() {
        val setting = AllSettings.controlLayout.getValue()

        val layout = _dataList.value.find { it.file.name == setting && it.isSupport }
            ?: dataList.value.firstOrNull { it.isSupport }
                ?.also { AllSettings.controlLayout.save(it.file.name) }

        if (layout == null) {
            AllSettings.controlLayout.reset()
        }

        _selectedLayout.update { layout }
    }

    /**
     * 解压默认控制布局
     *
     * assets 里那份读不出来、或者落盘之后解析不过时，退到 [EMBEDDED_FALLBACK_CONTROL_LAYOUT]：
     * 一份最小但一定能加载的布局。没有这一步的话，assets 一旦出问题，用户目录里就一份布局都
     * 没有，checkDefaultAndRefresh 只会每次启动重试同一个失败的解压，控件编辑器拿到空列表。
     *
     * 两件事刻意保持原样：拷贝仍然是 overwrite = false，因此已存在的用户布局永远不会被覆盖；
     * 并且这里不删除任何文件——落盘后解析失败的那份残缺文件就留在目录里，refresh() 本来就会
     * 跳过并告警它，而误删用户布局的后果远比多一个被跳过的文件严重。
     */
    private suspend fun unpackDefaultControl(
        context: Context
    ) = withContext(Dispatchers.IO) {
        val file = getNewRandomFile()
        val existedBefore = file.exists()
        val unpacked = try {
            context.copyAssetFile(fileName = DEFAULT_LAYOUT_ASSET, output = file, overwrite = false)
            isLoadableLayout(file)
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to unpack default control layout", e)
            false
        }
        if (unpacked) return@withContext

        // 只覆盖本次调用自己刚创建出来的那个文件；万一随机名撞上了已有文件（用户自己的布局），
        // 就另起一个新名字，绝不写进去。
        val target = if (existedBefore) getNewRandomFile() else file
        try {
            target.parentFile?.mkdirs()
            target.writeText(EMBEDDED_FALLBACK_CONTROL_LAYOUT)
            Logger.warning(TAG, "Wrote the embedded fallback control layout, file = $target")
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to write the embedded fallback control layout", e)
        }
    }

    /**
     * 布局文件是否真的能被加载，而不只是存在
     */
    private fun isLoadableLayout(
        file: File
    ): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return try {
            loadLayoutFromFile(file)
            true
        } catch (e: Exception) {
            Logger.warning(TAG, "Control layout cannot be loaded! file = $file", e)
            false
        }
    }

    /**
     * 选择控制布局
     */
    fun selectControl(data: ControlData) {
        if (!data.file.exists() || !data.isSupport) return
        AllSettings.controlLayout.save(data.file.name)
        _selectedLayout.update { data }
    }

    /**
     * 在协程内删除控制布局
     */
    fun deleteControl(data: ControlData) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) return@launch
            FileUtils.deleteQuietly(data.file)
            refresh()
        }
    }

    /**
     * 在协程内保存控制布局的数据
     */
    fun saveControl(
        data: ControlData,
        submitError: (Exception) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) {
                refresh()
                return@launch
            }
            val layout = data.controlLayout.pack()
            try {
                layout.saveToFile(data.file)
            } catch (e: Exception) {
                submitError(e)
//                FileUtils.deleteQuietly(data.file)
            }
            refresh()
        }
    }

    /**
     * 尝试导入控制布局
     */
    suspend fun importControl(
        inputStream: InputStream,
        onSerializationError: (Exception) -> Unit,
        catchedError: (Exception) -> Unit,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = getNewRandomFile()
        try {
            inputStream.use { stream ->
                val jsonString = stream.readString()
                val layout = loadLayoutFromString(jsonString)
                layout.saveToFile(file)
            }
            onFinished()
        } catch (e: SerializationException) {
            FileUtils.deleteQuietly(file)
            onSerializationError(e)
        } catch (e: Exception) {
            FileUtils.deleteQuietly(file)
            catchedError(e)
        }
    }

    /**
     * 尝试把控制布局导出到用户自己挑的那个目标位置
     *
     * 与 [importControl] 严格对称：同样是 suspend + IO 调度器 + 回调式的汇报，只是**方向**反过来。
     * 序列化走的是 [saveControl] 那条路——`ControlLayout.saveToFile` 是导出与保存共同的唯一入口，
     * 所以"导出得到的字节"与"把这份布局保存一次得到的字节"逐字节相同，导回来的文件一定能被
     * [importControl] 自己认出来。目标位置由系统的创建文档契约给出，这里既不申请新权限，也不碰
     * 任何存储 API，用户挑到哪儿就写到哪儿。
     *
     * 为了不给目标位置留下半截文件，序列化先落到启动器自己的缓存目录（`saveToFile` 内部仍然是
     * 先写 .tmp 再 rename），拿到完整字节之后才一次性写进目标流。任何一步失败都只删掉这一次自己
     * 造出来的临时文件：用户目录里的布局一份都不会被改写、覆盖、改名或删除，导入那条路也一个字
     * 都没动。
     *
     * 与 [importControl] 的差别只有一处：导出的失败只有一条通道。导入要分开"这个文件解析不了"
     * 与"IO 出错"，是因为输入来自不可信的外部文件；导出的输入是一份已经解析过的内存模型，
     * 序列化不出来和写不出去对用户来说是同一件事——导出没成功，附上原因即可。
     *
     * @param data 要导出的布局
     * @param outputStream 目标输出流，由调用方打开，本方法负责关掉它（否则很多提供方不会落盘）
     * @param onError 序列化失败，或目标位置写不进去
     * @param onFinished 完整内容已经写进目标位置
     */
    suspend fun exportControl(
        data: ControlData,
        outputStream: OutputStream,
        onError: (Exception) -> Unit,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        // 不受支持的布局从未被完整解析过，导出去只会得到一份加载器自己也读不回来的东西
        if (!data.isSupport) {
            onError(
                IllegalArgumentException("Unsupported control layout, file = ${data.file.name}")
            )
            return@withContext
        }

        // 落在缓存目录而不是用户的布局目录：临时文件因此永远不会被 refresh() 当成一份布局扫到，
        // 也不会出现在列表里
        val tempFile = File(
            PathManager.DIR_CACHE,
            "export_control_${newRandomFileName()}.$CONTROL_LAYOUT_EXTENSION"
        )
        try {
            data.controlLayout.pack().saveToFile(tempFile)

            val bytes = tempFile.readBytes()
            if (bytes.isEmpty()) {
                throw IllegalStateException("The exported control layout is empty, file = $tempFile")
            }
            // 一次 write 写完全部字节：目标位置因此要么是完整的布局，要么一次都不会被写坏
            outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
            onFinished()
        } catch (e: Exception) {
            Logger.warning(TAG, "Failed to export the control layout, file = ${data.file.name}", e)
            onError(e)
        } finally {
            // 只删这一次自己造出来的临时文件
            FileUtils.deleteQuietly(tempFile)
        }
    }
}