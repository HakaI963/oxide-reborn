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

package dev.oxide.launcher.ui.screens.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.oxide.inputmap.keycodes.ControlEventKeycode
import dev.oxide.inputmap.keycodes.LwjglGlfwKeycode
import dev.oxide.inputmap.keycodes.OPEN_CHAT
import dev.oxide.inputmap.keycodes.OPEN_CHAT_VALUE
import dev.oxide.layercontroller.ControlBoxLayout
import dev.oxide.layercontroller.data.HideLayerWhen
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.event.EventHandler
import dev.oxide.layercontroller.layout.ControlLayout
import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromFile
import dev.oxide.layercontroller.observable.ObservableControlLayout
import dev.oxide.launcher.R
import dev.oxide.launcher.bridge.CURSOR_DISABLED
import dev.oxide.launcher.bridge.OxideBridgeStates
import dev.oxide.launcher.bridge.OxideNativeInvoker
import dev.oxide.launcher.game.input.LWJGLCharSender
import dev.oxide.launcher.game.keycodes.mapToKeycode
import dev.oxide.launcher.game.launch.handler.GameHandler
import dev.oxide.launcher.game.sdl.SdlBridge
import dev.oxide.launcher.game.sdl.SdlTextSender
import dev.oxide.launcher.game.support.touch_controller.touchControllerInputModifier
import dev.oxide.launcher.game.support.touch_controller.touchControllerTouchModifier
import dev.oxide.launcher.game.version.installed.Version
import dev.oxide.launcher.setting.AllSettings
import dev.oxide.launcher.setting.enums.isLauncherInDarkTheme
import dev.oxide.launcher.setting.enums.toAction
import dev.oxide.launcher.terracotta.Terracotta
import dev.oxide.launcher.ui.components.MenuState
import dev.oxide.launcher.ui.components.rememberBoxSize
import dev.oxide.launcher.ui.control.MinecraftHotbar
import dev.oxide.launcher.ui.control.event.launcherEvent
import dev.oxide.launcher.ui.control.event.lwjglEvent
import dev.oxide.launcher.ui.control.gamepad.GamepadKeyListener
import dev.oxide.launcher.ui.control.gamepad.GamepadOnActionListener
import dev.oxide.launcher.ui.control.gamepad.GamepadStickMovementListener
import dev.oxide.launcher.ui.control.gamepad.SimpleGamepadCapture
import dev.oxide.launcher.ui.control.gyroscope.GyroscopeReader
import dev.oxide.launcher.ui.control.gyroscope.isGyroscopeAvailable
import dev.oxide.launcher.ui.control.hotbarPercentage
import dev.oxide.launcher.ui.control.input.TextInputMode
import dev.oxide.launcher.ui.control.mouse.SwitchableMouseLayout
import dev.oxide.launcher.ui.screens.game.elements.DraggableGameBall
import dev.oxide.launcher.ui.screens.game.elements.ForceCloseOperation
import dev.oxide.launcher.ui.screens.game.elements.GameMenuSubscreen
import dev.oxide.launcher.ui.screens.game.elements.GamepadModePromptDialog
import dev.oxide.launcher.ui.screens.game.elements.LogBox
import dev.oxide.launcher.ui.screens.game.elements.LogState
import dev.oxide.launcher.ui.screens.game.elements.OxideGameBrand
import dev.oxide.launcher.ui.screens.game.elements.ReplacementControlOperation
import dev.oxide.launcher.ui.screens.game.elements.ReplacementControlState
import dev.oxide.launcher.ui.screens.game.elements.SendKeycodeOperation
import dev.oxide.launcher.ui.screens.game.elements.SendKeycodeState
import dev.oxide.launcher.ui.screens.game.multiplayer.TerracottaOperation
import dev.oxide.launcher.ui.screens.game.multiplayer.rememberTerracottaViewModel
import dev.oxide.launcher.ui.screens.main.control_editor.ControlEditor
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingFacts
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingOverlay
import dev.oxide.launcher.ui.screens.main.oxide.OxideGameWaitingState
import dev.oxide.launcher.ui.screens.main.oxide.rememberOxideMetrics
import dev.oxide.launcher.utils.currentGameDisplayLayout
import dev.oxide.launcher.utils.logging.Logger
import dev.oxide.launcher.viewmodel.EditorViewModel
import dev.oxide.launcher.viewmodel.EventViewModel
import dev.oxide.launcher.viewmodel.GamepadViewModel
import dev.oxide.launcher.viewmodel.sendToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "GameScreen"

/** 帧率历史记录的最大时间节点数 */
private const val FPS_HISTORY_SIZE = 15
/** 最高/最低帧所在节点被清理后，重算前等待的时间节点数 */
private const val FPS_RESYNC_NODES = 10

private class GameViewModel(
    private val version: Version,
    private val onChangeTextInputMode: (TextInputMode?) -> Unit
) : ViewModel() {
    /** 游戏菜单操作状态 */
    var gameMenuState by mutableStateOf(MenuState.NONE)
    /** 游戏菜单当前显示的分区索引 */
    var gameMenuSectionIndex by mutableIntStateOf(0)
    /** 强制关闭弹窗操作状态 */
    var forceCloseState by mutableStateOf<ForceCloseOperation>(ForceCloseOperation.None)
    /** 发送键值操作状态 */
    var sendKeycodeState by mutableStateOf<SendKeycodeState>(SendKeycodeState.None)
    /** 更换控制布局操作状态 */
    var replacementControlState by mutableStateOf<ReplacementControlState>(ReplacementControlState.None)
    /** 被控制布局层标记为仅滑动的指针列表 */
    var moveOnlyPointers = mutableSetOf<PointerId>()
    /** 鼠标触摸指针处理层占用指针列表 */
    var occupiedPointers = mutableSetOf<PointerId>()

    /** 游戏内帧率状态 */
    var gameFps by mutableIntStateOf(0)
        private set
    /** 帧率历史记录（最多保留最近15个时间节点） */
    var fpsHistory by mutableStateOf<List<Int>>(emptyList())
        private set
    /** 记录范围内的历史最高帧 */
    var fpsMax by mutableIntStateOf(0)
        private set
    /** 记录范围内的历史最低帧 */
    var fpsMin by mutableIntStateOf(0)
        private set
    /** 最高帧重算倒计时（时间节点数），-1 表示无需重算 */
    private var fpsMaxResyncCountdown = -1
    /** 最低帧重算倒计时（时间节点数），-1 表示无需重算 */
    private var fpsMinResyncCountdown = -1
    private var fpsJob: Job? = null
    /** 开始帧率捕获 */
    fun startFpsCapture() {
        if (fpsJob?.isActive == true) return
        //新一轮捕获，重置历史记录
        fpsHistory = emptyList()
        fpsMax = 0
        fpsMin = 0
        fpsMaxResyncCountdown = -1
        fpsMinResyncCountdown = -1
        //开启一个新的协程，每秒更新一次帧率数据
        fpsJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                runCatching {
                    ensureActive()
                }.onFailure {
                    break
                }
                recordFps(CallbackBridge.getCurrentFps())
                delay(1000L.milliseconds)
            }
        }
    }
    /** 停止帧率捕获 */
    fun stopFpsCapture() {
        fpsJob?.cancel()
        fpsJob = null
    }

    /**
     * 记录一次帧率采样，并维护记录范围内的最高/最低帧。
     * 被清理的最老节点若恰好是最高/最低帧，不立即重算：
     * 若之后10个时间节点内没有出现更高/更低的帧，才用当前记录的所有节点重算一次
     */
    private fun recordFps(fps: Int) {
        gameFps = fps
        //衰减重算倒计时，归零说明等待期内没有出现更高/更低的帧
        if (fpsMaxResyncCountdown > 0) fpsMaxResyncCountdown--
        if (fpsMinResyncCountdown > 0) fpsMinResyncCountdown--
        if (fpsMaxResyncCountdown == 0) {
            fpsMax = fpsHistory.max()
            fpsMaxResyncCountdown = -1
        }
        if (fpsMinResyncCountdown == 0) {
            fpsMin = fpsHistory.min()
            fpsMinResyncCountdown = -1
        }

        if (fpsHistory.isEmpty()) {
            fpsMax = fps
            fpsMin = fps
        } else {
            if (fps > fpsMax) {
                fpsMax = fps
                fpsMaxResyncCountdown = -1
            }
            if (fps < fpsMin) {
                fpsMin = fps
                fpsMinResyncCountdown = -1
            }
        }
        fpsHistory = fpsHistory + fps

        //超出最大节点数时清理最老的节点
        if (fpsHistory.size > FPS_HISTORY_SIZE) {
            val removed = fpsHistory.first()
            fpsHistory = fpsHistory.drop(1)
            if (removed == fpsMax && fpsHistory.none { it == fpsMax }) {
                fpsMaxResyncCountdown = FPS_RESYNC_NODES
            }
            if (removed == fpsMin && fpsHistory.none { it == fpsMin }) {
                fpsMinResyncCountdown = FPS_RESYNC_NODES
            }
        }
    }

    var editorRefresh by mutableIntStateOf(0)
        private set
    /** 可观察的控制布局 */
    var observableLayout by mutableStateOf<ObservableControlLayout?>(null)
        private set
    /** 当前控制布局文件 */
    var currentControlFile by mutableStateOf<File?>(null)
        private set
    /** 控制布局：控件层隐藏状态 */
    var controlLayerHideState by mutableStateOf(HideLayerWhen.None)
        private set

    /** 是否正在编辑布局 */
    var isEditingLayout by mutableStateOf(false)
        private set

    fun switchControlLayer(hideWhen: HideLayerWhen) {
        if (controlLayerHideState != hideWhen) controlLayerHideState = hideWhen
    }

    /** 虚拟鼠标滚动事件处理 */
    val mouseScrollUpEvent = MouseScrollEvent(viewModelScope, 1.0)
    val mouseScrollDownEvent = MouseScrollEvent(viewModelScope, -1.0)

    /** 游戏内消息发送器 */
    val gameTextSender = GameTextSender(viewModelScope)

    /** 控制布局控件点击事件处理器 */
    val eventHandler = EventHandler(
        handle = { event, pressed ->
            onKeyEvent(event, pressed)
        }
    )

    /** 处理控制布局类点击事件 */
    fun onKeyEvent(event: ClickEvent, pressed: Boolean) {
        val key = event.key
        when (event.type) {
            ClickEvent.Type.Key -> {
                lwjglEvent(
                    eventKey = key,
                    isMouse = key.startsWith("GLFW_MOUSE_", false),
                    isPressed = pressed
                )
            }
            ClickEvent.Type.LauncherEvent -> {
                launcherEvent(
                    eventKey = key,
                    isPressed = pressed,
                    onSwitchIME = { onChangeTextInputMode(null) },
                    onSwitchMenu = { switchMenu() },
                    onSingleScrollUp = { mouseScrollUpEvent.scrollSingle() },
                    onSingleScrollDown = { mouseScrollDownEvent.scrollSingle() },
                    onLongScrollUp = { mouseScrollUpEvent.scrollLongPress() },
                    onLongScrollUpCancel = { mouseScrollUpEvent.cancel() },
                    onLongScrollDown = { mouseScrollDownEvent.scrollLongPress() },
                    onLongScrollDownCancel = { mouseScrollDownEvent.cancel() }
                )
            }
            ClickEvent.Type.SendText -> {
                //游戏内文本发送事件
                if (pressed) {
                    val text = event.key
                    val inGame = OxideBridgeStates.cursorMode.value == CURSOR_DISABLED
                    gameTextSender.send(GameTextSender.Data(text, inGame))
                }
                return
            }
            else -> return
        }
    }

    fun replaceControlLayout(layoutFile: File) {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout(layoutFile)
        }
    }

    private val layoutMutex = Mutex()
    suspend fun loadControlLayout(layoutFile: File? = version.getControlPath()) {
        layoutMutex.withLock {
            withContext(Dispatchers.Main) {
                observableLayout = null
                val layout = withContext(Dispatchers.IO) {
                    delay(10L.milliseconds) //刻意等待一会再加载
                    currentControlFile = layoutFile
                    getLayout(layoutFile)
                }
                //将控制布局加载为可供Compose加载的形式
                observableLayout = ObservableControlLayout(layout)
            }
        }
    }

    private fun getLayout(layoutFile: File? = currentControlFile): ControlLayout {
        return layoutFile?.let {
            try {
                loadLayoutFromFile(it)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load control layout: $it", e)
                null
            }
        } ?: EmptyControlLayout
    }

    /**
     * 开始编辑控制布局模式
     */
    fun startControlEditor(editorVM: EditorViewModel) {
        if (!isEditingLayout) {
            clearState()
            editorVM.initLayout(getLayout())
            isEditingLayout = true
        }
    }

    /**
     * 退出编辑控制布局模式（如果当前确实正在编辑控制布局）
     */
    fun exitControlEditor() {
        viewModelScope.launch(Dispatchers.Main) {
            if (isEditingLayout) {
                isEditingLayout = false
                loadControlLayout(currentControlFile)
                editorRefresh++
            }
        }
    }

    /**
     * 切换游戏菜单
     */
    fun switchMenu() {
        this.gameMenuState = this.gameMenuState.next()
    }

    /**
     * 清除所有游戏状态
     */
    fun clearState() {
        mouseScrollUpEvent.cancel()
        mouseScrollDownEvent.cancel()
        gameTextSender.cancel()
        onChangeTextInputMode(TextInputMode.DISABLE)
        moveOnlyPointers.clear()
        occupiedPointers.clear()
    }

    init {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout()
        }
    }

    override fun onCleared() {
        clearState()
    }
}

/**
 * 鼠标滚轮事件管理
 * @param offset 滚轮滚动距离
 */
private class MouseScrollEvent(
    private val scope: CoroutineScope,
    private val offset: Double
) {
    private var mouseScrollJob: Job? = null

    /**
     * 取消滚动事件，并重置状态
     */
    fun cancel() {
        mouseScrollJob?.cancel()
        mouseScrollJob = null
    }

    /**
     * 单击响应一次滚轮滚动事件
     */
    fun scrollSingle() {
        CallbackBridge.sendScroll(0.0, offset)
    }

    /**
     * 长按不间断触发滚轮滚动事件
     */
    fun scrollLongPress() {
        mouseScrollJob?.cancel()
        mouseScrollJob = scope.launch {
            while (true) {
                try {
                    ensureActive()
                    CallbackBridge.sendScroll(0.0, offset)
                    delay(50L.milliseconds)
                } catch (_: Exception) {
                    break
                }
            }
            mouseScrollJob = null
        }
    }
}

/**
 * 游戏内消息发送器
 */
private class GameTextSender(private val scope: CoroutineScope) {
    /**
     * @param text 要发送的文本
     * @param inGame 当前是否处于游戏内，如果在游戏中，则会尝试打开聊天栏
     */
    data class Data(
        val text: String,
        val inGame: Boolean
    )

    private var messageChannel: Channel<Data>? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        messageChannel?.close()
        messageChannel = null
        job = null
    }

    /**
     * 尝试向游戏发送文本（排队发送）
     */
    fun send(data: Data) {
        if (job?.isActive != true || messageChannel == null) {
            job?.cancel()
            messageChannel?.close()

            messageChannel = Channel(Channel.UNLIMITED)
            job = scope.launch {
                messageChannel?.let { channel ->
                    for ((text, inGame) in channel) {
                        sendMessage(text, inGame)
                    }
                }
            }
        }

        messageChannel?.trySend(data)
    }

    private suspend fun sendMessage(text: String, inGame: Boolean) {
        withContext(Dispatchers.Main) {
            fun sendText() {
                for (ch in text) {
                    if (SdlBridge.sdlEnabled) {
                        SdlTextSender.sendChar(ch)
                    } else {
                        LWJGLCharSender.sendChar(ch)
                    }
                }
            }

            if (inGame) {
                //根据options.txt中的配置，找到打开聊天栏的键
                //如果找不到，则忽略这次事件
                mapToKeycode(OPEN_CHAT, OPEN_CHAT_VALUE)?.let { openChat ->
                    if (SdlBridge.sdlEnabled) {
                        SdlTextSender.sendKey(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        SdlTextSender.sendEnter()
                    } else {
                        CallbackBridge.sendKeyPress(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        LWJGLCharSender.sendEnter()
                    }
                }
            } else {
                //如果当前不在游戏内，则直接发送文本
                sendText()
            }
        }
    }
}

@Composable
private fun rememberGameViewModel(
    version: Version,
    onChangeTextInputMode: (TextInputMode?) -> Unit
) = viewModel(
    key = version.toString()
) {
    GameViewModel(version, onChangeTextInputMode)
}

@Composable
private fun rememberEditorViewModel(
    key: String
)= viewModel(
    key = key
) {
    EditorViewModel()
}

@Composable
fun GameScreen(
    version: Version,
    gameHandler: GameHandler,
    showGameInfo: Boolean,
    onInfoBoxClose: () -> Unit,
    logState: LogState,
    onLogStateChange: (LogState) -> Unit,
    textInputMode: TextInputMode,
    isTouchProxyEnabled: Boolean,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    getAccountName: () -> String?,
    eventViewModel: EventViewModel,
    gamepadViewModel: GamepadViewModel,
) {
    val context = LocalContext.current
    val viewModel = rememberGameViewModel(version) { mode ->
        eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(mode))
    }
    val editorViewModel = rememberEditorViewModel("ControlEditor_Times=${viewModel.editorRefresh}")
    val cursorMode by OxideBridgeStates.cursorMode.collectAsStateWithLifecycle()
    val isGrabbing = remember(cursorMode) {
        cursorMode == CURSOR_DISABLED
    }
    val terracottaViewModel = rememberTerracottaViewModel(
        keyTag = gameHandler.toString() + "_Terracotta",
        gameHandler = gameHandler,
        eventViewModel = eventViewModel,
        getUserName = getAccountName
    )

    LaunchedEffect(viewModel.isEditingLayout, viewModel.gameMenuState) {
        //向VMActivity同步状态，编辑控制布局或打开游戏菜单时，不会继续处理按键事件
        val allowKeyHandle = !viewModel.isEditingLayout && viewModel.gameMenuState != MenuState.SHOW
        eventViewModel.sendEvent(EventViewModel.Event.Game.KeyHandle(allowKeyHandle))
    }

    SendKeycodeOperation(
        operation = viewModel.sendKeycodeState,
        onChange = { viewModel.sendKeycodeState = it },
        lifecycleScope = viewModel.viewModelScope
    )

    ForceCloseOperation(
        operation = viewModel.forceCloseState,
        onChange = { viewModel.forceCloseState = it },
        onForceClose = {
            Terracotta.setWaiting(false)
            OxideNativeInvoker.jvmExit(0, false)
        },
        text = stringResource(R.string.game_menu_option_force_close_text)
    )

    ReplacementControlOperation(
        operation = viewModel.replacementControlState,
        onChange = { viewModel.replacementControlState = it },
        currentLayout = viewModel.currentControlFile,
        replacementControl = { viewModel.replaceControlLayout(it) }
    )

    TerracottaOperation(
        viewModel = terracottaViewModel,
        onShowToast = { text, duration ->
            eventViewModel.sendToast(text, duration)
        }
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenSize = rememberBoxSize()

        if (!viewModel.isEditingLayout) {
            if (AllSettings.gamepadControl.state) {
                GamepadOnActionListener(
                    gamepadViewModel = gamepadViewModel,
                    onAction = {
                        viewModel.switchControlLayer(HideLayerWhen.WhenGamepad)
                    }
                )
            }

            if (AllSettings.gamepadControl.state && gamepadViewModel.gamepadEngaged) {
                //手柄事件监听
                GamepadKeyListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { events, pressed ->
                        events.forEach { event ->
                            viewModel.onKeyEvent(event, pressed)
                        }
                    }
                )

                //手柄摇杆控制移动事件监听
                GamepadStickMovementListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { event, pressed ->
                        viewModel.onKeyEvent(event, pressed)
                    }
                )
            }

            //控制布局层
            ControlBoxLayout(
                modifier = Modifier.fillMaxSize(),
                observedLayout = viewModel.observableLayout,
                eventHandler = viewModel.eventHandler,
                checkOccupiedPointers = { viewModel.occupiedPointers.contains(it) },
                opacity = (AllSettings.controlsOpacity.state.toFloat() / 100f).coerceIn(0f, 1f),
                markPointerAsMoveOnly = { viewModel.moveOnlyPointers.add(it) },
                onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                onReleasePointer = { viewModel.occupiedPointers.remove(it) },
                isCursorGrabbing = isGrabbing,
                hideLayerWhen = viewModel.controlLayerHideState,
                isDark = isLauncherInDarkTheme()
            ) {
                //虚拟鼠标控制层
                MouseControlLayout(
                    isTouchProxyEnabled = isTouchProxyEnabled,
                    modifier = Modifier.fillMaxSize(),
                    cursorMode = cursorMode,
                    screenSize = screenSize,
                    onInputAreaRectUpdated = onInputAreaRectUpdated,
                    textInputMode = textInputMode,
                    isMoveOnlyPointer = { viewModel.moveOnlyPointers.contains(it) },
                    onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                    onReleasePointer = {
                        viewModel.occupiedPointers.remove(it)
                        viewModel.moveOnlyPointers.remove(it)
                    },
                    onMouseMoved = { viewModel.switchControlLayer(HideLayerWhen.WhenMouse) },
                    onTouch = { viewModel.switchControlLayer(HideLayerWhen.None) },
                    gamepadViewModel = gamepadViewModel.takeIf { AllSettings.gamepadControl.state }
                )
            }

            //物品栏触发层
            val gameDisplayLayout = currentGameDisplayLayout(screenSize)
            MinecraftHotbar(
                screenSize = screenSize,
                rule = AllSettings.hotbarRule.state,
                widthPercentage = AllSettings.hotbarWidth.state.hotbarPercentage(),
                heightPercentage = AllSettings.hotbarHeight.state.hotbarPercentage(),
                sendKeycode = { keycode ->
                    CallbackBridge.sendKeyPress(keycode)
                },
                isGrabbing = isGrabbing,
                displayOffset = gameDisplayLayout.offset,
                onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                onReleasePointer = { viewModel.occupiedPointers.remove(it) }
            )
        }

        // 游戏画面右下角的 Oxide 品牌标识
        //
        // 组合在控制布局层与快捷栏触发层**之后**、游戏菜单与日志框**之前**：
        // 因此它压得住游戏画面（那一层就是 VMActivity 的 surface），
        // 又被菜单、对话框与日志完整盖住——它只在"游戏自己在前面"时才露出来。
        //
        // 它不吃任何触摸，所以它出现的位置不会改变下面任何一颗控件的命中区域；
        // 开关与它出现与否都在 AllSettings.showGameBrand 一处决定。
        // 编辑控制布局时这一层被编辑器整个盖住，因此不组合，省一次绘制。
        if (!viewModel.isEditingLayout && AllSettings.showGameBrand.state) {
            OxideGameBrand(
                availableWidth = maxWidth,
                availableHeight = maxHeight,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        //陀螺仪控制
        val isGyroscopeAvailable = remember(context) {
            isGyroscopeAvailable(context = context)
        }
        if (isGrabbing && isGyroscopeAvailable && AllSettings.gyroscopeControl.state) {
            GyroscopeReader(
                xEvent = { delta ->
                    CallbackBridge.sendCursorDelta(if (AllSettings.gyroscopeInvertX.state) -delta else delta, 0f)
                },
                yEvent = { delta ->
                    CallbackBridge.sendCursorDelta(0f, if (AllSettings.gyroscopeInvertY.state) delta else -delta)
                },
                sampleRate = AllSettings.gyroscopeSampleRate.state,
                smoothing = AllSettings.gyroscopeSmoothing.state,
                smoothingWindow = AllSettings.gyroscopeSmoothingWindow.state,
                sensitivity = AllSettings.gyroscopeSensitivity.state / 100f
            )
        }

        // 旧的那一块是 GameInfoBox（BackgroundCard + Material 字体 + IconButton），
        // 写着 "The game is running. Waiting for the game screen to appear…" 与两行版本信息。
        // 它用的是 Material 的材质与字体，跟启动器其余部分不是同一种语言，
        // 因此读起来像一条与当前界面无关的旧通知。
        //
        // 现在这一块是 OxideGameWaitingOverlay：紧凑的居中卡片，写清游戏版本、加载器
        // 与启动状态，可选地带上已经过去多久，并且只摆真正被支持的动作——
        // 这一条链路上能按的只有"收起"，因此不会出现一个按了没反应的"关闭游戏"键。
        //
        // 出现与消失的时机一个字都没有改：仍然只在第一帧之前出现，
        // 仍然由 GameHandler.onGraphicOutput() 把它关掉。
        OxideGameWaitingOverlay(
            metrics = rememberOxideMetrics(),
            facts = OxideGameWaitingFacts(
                // showGameInfo 为真本身就等于"第一帧还没画出来"这一个状态：
                // GameHandler.onGraphicOutput() 在画出第一帧时把它关掉
                state = OxideGameWaitingState.Waiting,
                instanceName = version.getVersionName(),
                minecraftVersion = version.getVersionInfo()?.minecraftVersion,
                loaderLabel = version.getVersionInfo()?.primaryLoader?.let { loader ->
                    loader.loader.displayName.takeIf { it.isNotBlank() }
                        ?.plus(" ").plus(loader.version).trim()
                },
                // GameHandler 有真实的起跑时刻（launchT0），但它没有透出来，
                // 因此耗时整行不出现，而不是显示一个从 0 开始假跑的计时器
                startedAtMillis = null,
            ),
            visible = showGameInfo,
            onClose = onInfoBoxClose,
        )

        LogBox(
            enableLog = !viewModel.isEditingLayout && logState.value,
            onClose = {
                onLogStateChange(LogState.CLOSE)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 菜单一次都没开过时什么都不组合：帧率捕获每秒几十次重组，
        // 不该带着一棵用不到的浮层一起重排
        if (viewModel.gameMenuState != MenuState.NONE) {
            GameMenuSubscreen(
                state = viewModel.gameMenuState,
                sectionIndex = viewModel.gameMenuSectionIndex,
                onSectionChange = { viewModel.gameMenuSectionIndex = it },
                gamepadViewModel = gamepadViewModel,
                closeScreen = { viewModel.gameMenuState = MenuState.HIDE },
                onForceClose = { viewModel.forceCloseState = ForceCloseOperation.Show },
                onSwitchLog = { onLogStateChange(logState.next()) },
                enableTerracotta = AllSettings.enableTerracotta.state,
                onOpenTerracottaMenu = { terracottaViewModel.openMenu() },
                onRefreshWindowSize = { eventViewModel.sendEvent(EventViewModel.Event.Game.RefreshSize) },
                onInputMethod = {
                    eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(null))
                },
                onSendKeycode = { viewModel.sendKeycodeState = SendKeycodeState.ShowDialog },
                onReplacementControl = { viewModel.replacementControlState = ReplacementControlState.Show },
                onEditLayout = {
                    viewModel.startControlEditor(
                        editorVM = editorViewModel
                    )
                },
                onShowToast = { text, duration ->
                    eventViewModel.sendToast(text, duration)
                }
            )
        }

        if (AllSettings.gamepadControl.state) {
            //手柄事件捕获层
            SimpleGamepadCapture(
                gamepadViewModel = gamepadViewModel
            )
        }

        //手柄输入模式选择询问
        GamepadModePromptDialog(
            visible = gamepadViewModel.modePromptVisible,
            onConfirm = { mode ->
                gamepadViewModel.confirmModePrompt(mode)
            }
        )

        if (viewModel.isEditingLayout) {
            viewModel.currentControlFile?.let {
                ControlEditor(
                    viewModel = editorViewModel,
                    targetFile = it,
                    exit = {
                        viewModel.exitControlEditor()
                    },
                    menuExit = {
                        editorViewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                viewModel.exitControlEditor()
                            }
                        )
                    }
                )
            }
        } else {
            if (AllSettings.showMenuBall.state) {
                //在这里根据设置决定是否启用帧率捕获协程
                val showFps = AllSettings.showFPS.state
                DisposableEffect(showFps) {
                    if (showFps) viewModel.startFpsCapture()
                    onDispose {
                        viewModel.stopFpsCapture()
                    }
                }

                val gameFps: Int? = if (showFps) {
                    viewModel.gameFps
                } else {
                    null
                }

                DraggableGameBall(
                    position = AllSettings.menuBallPos.state,
                    onPositionChanged = {
                        AllSettings.menuBallPos.updateState(it)
                    },
                    onSavePos = {
                        AllSettings.menuBallPos.save()
                    },
                    gameFps = gameFps,
                    fpsDisplayMode = AllSettings.fpsDisplayMode.state,
                    fpsHistory = viewModel.fpsHistory,
                    fpsMax = viewModel.fpsMax,
                    fpsMin = viewModel.fpsMin,
                    showMemory = AllSettings.showMemory.state,
                    opened = viewModel.gameMenuState == MenuState.SHOW,
                    alpha = AllSettings.menuBallOpacity.state / 100f,
                    onClick = {
                        viewModel.switchMenu()
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        eventViewModel.events
            .filterIsInstance<EventViewModel.Event.Game>()
            .collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.OnBack -> {
                        if (viewModel.isEditingLayout) {
                            //处于控制布局编辑模式
                            editorViewModel.onBackPressed(
                                context = context,
                                onExit = {
                                    viewModel.exitControlEditor()
                                }
                            )
                        } else if (!AllSettings.showMenuBall.getValue()) {
                            viewModel.switchMenu()
                        } else {
                            //按下返回键
                            val event = ClickEvent(
                                type = ClickEvent.Type.Key,
                                key = ControlEventKeycode.GLFW_KEY_ESCAPE
                            )
                            viewModel.onKeyEvent(event, true)
                            delay(10L.milliseconds)
                            viewModel.onKeyEvent(event, false)
                        }
                    }
                    is EventViewModel.Event.Game.OnResume -> {
                        viewModel.clearState()
                    }
                    else -> { /*忽略*/ }
                }
            }
    }
}

/**
 * 鼠标控制层
 * @param isTouchProxyEnabled 是否启用控制代理（TouchController模组支持）
 * @param cursorMode 当前鼠标模式
 * @param textInputMode 输入法状态
 * @param isMoveOnlyPointer 检查指针是否被标记为仅处理滑动事件
 * @param onOccupiedPointer 标记指针已被占用
 * @param onReleasePointer 标记指针已被释放
 * @param onMouseMoved 实体鼠标操作时回调
 * @param onTouch 手指触摸操作鼠标层时回调
 */
@Composable
private fun MouseControlLayout(
    isTouchProxyEnabled: Boolean,
    modifier: Modifier = Modifier,
    cursorMode: Int,
    screenSize: IntSize,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    textInputMode: TextInputMode,
    isMoveOnlyPointer: (PointerId) -> Boolean,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    onMouseMoved: () -> Unit,
    onTouch: () -> Unit,
    gamepadViewModel: GamepadViewModel?
) {
    Box(
        modifier = modifier
            .then(
                if (isTouchProxyEnabled) {
                    Modifier
                        .touchControllerTouchModifier(
                            screenSize = screenSize
                        )
                        .touchControllerInputModifier(
                            screenSize = screenSize,
                            onInputAreaRectUpdated = onInputAreaRectUpdated,
                        )
                } else Modifier
            )
    ) {

        val capturedSpeedFactor = AllSettings.mouseCaptureSensitivity.state / 100f
        val capturedTapMouseAction = AllSettings.gestureTapMouseAction.state.toAction()
        val capturedLongPressMouseAction = AllSettings.gestureLongPressMouseAction.state.toAction()

        SwitchableMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenSize = screenSize,
            cursorMode = cursorMode,
            onTouch = onTouch,
            onMouse = onMouseMoved,
            gamepadViewModel = gamepadViewModel,
            onTap = { position ->
                val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(position)
                CallbackBridge.putMouseEventWithCoords(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), gamePosition.x, gamePosition.y)
            },
            onCapturedTap = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedTapMouseAction)
                }
            },
            onLongPress = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), true)
            },
            onLongPressEnd = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), false)
            },
            onCapturedLongPress = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, true)
                }
            },
            onCapturedLongPressEnd = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, false)
                }
            },
            onPointerMove = { pos ->
                pos.sendPosition(screenSize)
            },
            onCapturedMove = { delta ->
                CallbackBridge.sendCursorDelta(
                    delta.x * capturedSpeedFactor,
                    delta.y * capturedSpeedFactor
                )
            },
            onMouseScroll = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            },
            onMouseButton = { button, pressed ->
                val code = LWJGLCharSender.getMouseButton(button) ?: return@SwitchableMouseLayout
                CallbackBridge.sendMouseButton(code.toInt(), pressed)
            },
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            enableScrollGesture = AllSettings.gestureControl.state,
            onScrollGesture = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            }
        )
    }
}

private fun Offset.sendPosition(screenSize: IntSize) {
    val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(this)
    CallbackBridge.sendCursorPos(gamePosition.x, gamePosition.y)
}
