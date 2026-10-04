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

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.oxide.launcher.game.path.GamePathManager
import dev.oxide.launcher.game.version.installed.cleanup.CleanFailedException
import dev.oxide.launcher.game.version.installed.cleanup.GameAssetCleaner
import dev.oxide.launcher.ui.AndroidStringText
import dev.oxide.launcher.utils.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "OxideStorage"

/**
 * SAF 的 document id 里，"主存储"那一档的前缀
 *
 * 系统对外部存储给出的 document id 是 `primary:<相对于主存储根的路径>`；
 * 其他提供方（SD 卡、云盘、MTP）各有各的 id 空间，**没有**对应的文件系统路径。
 */
internal const val OXIDE_PRIMARY_DOCUMENT_PREFIX = "primary:"

/**
 * "新增游戏目录"走到哪一步了
 *
 * 挑文件夹与起名字分成两步，而不是一步：SAF 的系统选择器给不了一个好名字，
 * 而 [dev.oxide.launcher.game.path.GamePathManager.GamePath] 需要一个用来在列表里
 * 区分这一项的标题。
 */
internal enum class OxideStorageAddStage {
    /** 已经挑好一个真实文件夹，正在等一个名字 */
    Name,
}

/**
 * 这个 document id 是不是“主存储”那一档
 *
 * 纯函数，可直接单测。主存储的 document id 是 `primary:<相对路径>`；
 * 其余提供方（SD 卡、云盘、MTP）各有各的 id 空间，**没有**对应的文件系统路径。
 */
internal fun isPrimaryStorageDocument(docId: String): Boolean =
    docId.startsWith(OXIDE_PRIMARY_DOCUMENT_PREFIX)

/**
 * 主存储上的相对路径 → 真实文件系统路径
 *
 * [GamePathManager] 存的是真实路径（后面全部按 `File(path)` 处理），所以只能换算主存储；
 * 其余一律返回 null，由界面明确告知用户“游戏目录只能是这台设备上的一个真实文件夹”。
 *
 * [relative] 必须已经解码：系统把空格写成 `%20`，解码放在调用那一边。
 * `..` 一律拒绝：document id 是系统给的，但游戏目录随后会被当成真实路径使用，
 * 路径穿越不是这里该冒的风险。
 *
 * 纯函数，可直接单测。
 */
internal fun oxideGamePathFromRelative(
    relative: String,
    storageRoot: String,
): String? {
    val root = storageRoot.trimEnd('/')
    if (root.isEmpty()) return null
    val clean = relative.trim().trim('/')
    if (clean.isEmpty()) return root
    if (clean.split('/').any { it == ".." }) return null
    return "$root/$clean"
}

/**
 * 这台设备的主存储根目录
 *
 * 拿不到时给空串：[oxideGamePathFromRelative] 会把空根当作“换算不了”，
 * 而不是猜一个 `/storage/emulated/0`。
 */
internal fun oxideExternalStorageRoot(): String =
    Environment.getExternalStorageDirectory()?.absolutePath.orEmpty()

/**
 * "新增游戏目录"这一枚是否出现
 *
 * 正在挑目录或正在起名时不再给第二枚：连按两下会打开两个 SAF 选择器。
 */
internal fun oxideStorageAddVisible(adding: Boolean): Boolean = !adding

/**
 * "清理冗余游戏资源"这一枚是否可点
 *
 * **一个已安装的实例都没有时不可点**：这一档会比对所有版本需要的资源文件，
 * 版本列表为空意味着"什么都不需要"，于是它会把整个 assets 目录删光。
 * 后端没有这道保护，因此必须由界面挡住。
 */
internal fun oxideStorageCleanupEnabled(installedCount: Int): Boolean = installedCount > 0

/** 清理的结果 */
data class OxideCleanupResult(
    val files: Int,
    val size: String,
    val failed: List<String> = emptyList(),
)

/**
 * 清理冗余游戏资源的状态持有者
 *
 * [GameAssetCleaner] 自己要一个 [kotlinx.coroutines.CoroutineScope] 并且会在里面
 * 跑一整条任务流水线（扫盘、比对、删除）。它必须活在 ViewModel 上：
 * 抽屉收起或重组时任务不能跟着取消，进度也要在抽屉重新打开时还在。
 */
internal class OxideStorageViewModel : ViewModel() {

    private var cleaner: GameAssetCleaner? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _failed = MutableStateFlow<List<String>>(emptyList())
    val failed: StateFlow<List<String>> = _failed.asStateFlow()

    /** 清理结果：文件数与体积都是后端真实算出来的，不是界面估的 */
    private val _result = MutableStateFlow<OxideCleanupResult?>(null)
    val result: StateFlow<OxideCleanupResult?> = _result.asStateFlow()

    /**
     * 正在跑的那一次的任务标题
     *
     * 这里存的是**还没有解析**的 [AndroidStringText]：资源 id 只有在组合里才解得开，
     * 而收集发生在 [viewModelScope] 的协程里。因此标题由界面读 [tasks] 时解析。
     */
    val tasks: StateFlow<List<AndroidStringText>>
        get() = _tasks

    private val _tasks = MutableStateFlow<List<AndroidStringText>>(emptyList())

    /**
     * 开始清理
     *
     * @param onFinished 清理成功结束：文件数与体积都已经算好
     * @param onFailure 失败：把不能删的文件名列出来，而不是只说一句"出错了"
     */
    fun start(onFinished: (OxideCleanupResult) -> Unit, onFailure: (Throwable) -> Unit) {
        if (_running.value) return
        val instance = cleaner ?: GameAssetCleaner(viewModelScope).also { cleaner = it }
        _running.value = true
        _failed.value = emptyList()

        viewModelScope.launch {
            // 标题原样递出去：resolveAndroidString 是 @Composable，
            // 在协程里调它编译不过，解析留给读 [tasks] 的那一层
            instance.tasksFlow.collect { tasks ->
                _tasks.value = tasks.map { task -> task.title }
            }
        }
        instance.start(
            // 已经有一次在跑时后端会拒掉这一次；上面的 `_running` 已经先拦下一次，
            // 这里只需要不影响已经在跑的那一次
            isRunning = { Logger.info(TAG, "A cleanup is already running") },
            onEnd = { count, size ->
                _running.value = false
                _tasks.value = emptyList()
                val outcome = OxideCleanupResult(files = count, size = size)
                _result.value = outcome
                onFinished(outcome)
            },
            onThrowable = { error ->
                _running.value = false
                _tasks.value = emptyList()
                _failed.value = (error as? CleanFailedException)
                    ?.files
                    ?.map { file -> file.name }
                    .orEmpty()
                onFailure(error)
            },
        )
    }

    fun cancel() {
        cleaner?.cancel()
        _running.value = false
        _tasks.value = emptyList()
    }

    fun consumeResult() {
        _result.value = null
    }

    /** [GamePathManager.addNewPath] 抛出来的重复路径，界面据此给一句人话 */
    fun isDuplicatePathConflict(error: Throwable): Boolean =
        error is IllegalArgumentException && error.message?.contains("conflicts") == true
}
