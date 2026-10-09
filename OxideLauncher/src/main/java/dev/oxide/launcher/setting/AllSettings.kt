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

package dev.oxide.launcher.setting

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import dev.oxide.layercontroller.utils.snap.SnapMode
import dev.oxide.launcher.BuildKeys
import dev.oxide.launcher.game.download.assets.platform.Platform
import dev.oxide.launcher.game.path.GamePathManager
import dev.oxide.launcher.game.version.installed.GraphicsApi
import dev.oxide.launcher.setting.enums.ActionMenuSide
import dev.oxide.launcher.setting.enums.AppLanguage
import dev.oxide.launcher.setting.enums.BackgroundBlur
import dev.oxide.launcher.setting.enums.DarkMode
import dev.oxide.launcher.setting.enums.FpsDisplayMode
import dev.oxide.launcher.setting.enums.GamepadInputMode
import dev.oxide.launcher.setting.enums.GestureActionType
import dev.oxide.launcher.setting.enums.MirrorSourceType
import dev.oxide.launcher.setting.enums.MouseControlMode
import dev.oxide.launcher.setting.enums.ResolutionRule
import dev.oxide.launcher.ui.control.HotbarRule
import dev.oxide.launcher.ui.control.gamepad.JoystickMode
import dev.oxide.launcher.ui.control.mouse.CENTER_HOTSPOT
import dev.oxide.launcher.ui.control.mouse.CursorHotspot
import dev.oxide.launcher.ui.control.mouse.LEFT_TOP_HOTSPOT
import dev.oxide.launcher.ui.theme.ColorThemeType
import dev.oxide.launcher.utils.animation.TransitionAnimationType

object AllSettings : SettingsRegistry() {
    //Renderer
    /**
     * 全局渲染器
     */
    val renderer = stringSetting("renderer", "")

    /**
     * Vulkan 驱动器
     */
    val vulkanDriver = stringSetting("vulkanDriver", "default turnip")

    /**
     * 图形 API（Minecraft 26.2+）
     */
    val graphicsApi = enumSetting("graphicsApi", GraphicsApi.DEFAULT_OPENGL)

    /**
     * 分辨率
     */
    val resolutionRatio = intSetting("resolutionRatio", 100, 25..300)

    /**
     * 分辨率规则
     */
    val resolutionRule = enumSetting("resolutionRule", ResolutionRule.PERCENTAGE)

    /**
     * 自定义分辨率宽度，0 表示尚未初始化
     */
    val customResolutionWidth = intSetting("customResolutionWidth", 0)

    /**
     * 自定义分辨率高度，0 表示尚未初始化
     */
    val customResolutionHeight = intSetting("customResolutionHeight", 0)

    /**
     * 游戏页面全屏化
     */
    val gameFullScreen = boolSetting("gameFullScreen", true)

    /**
     * 使用 SurfaceView 渲染
     */
    val useSurfaceView = boolSetting("useSurfaceView", false)

    /**
     * 持续性能模式
     */
    val sustainedPerformance = boolSetting("sustainedPerformance", false)

    /**
     * 使用系统的 Vulkan 驱动
     */
    val zinkPreferSystemDriver = boolSetting("zinkPreferSystemDriver", false)

    /**
     * Zink 垂直同步
     */
    val vsyncInZink = boolSetting("vsyncInZink", false)

    /**
     * 启用着色器日志输出
     */
    val dumpShaders = boolSetting("dumpShaders", false)

    /**
     * Copper Oxide 驱动数据目录改用启动器私有目录
     *
     * 关（默认）时启动器不传 MG_DIR_PATH，行为与此前完全一致：驱动回落到
     * 编译进 .so 的默认目录（rodata 中的 "/sdcard/MG"），在分区存储设备上
     * 通常不可写，驱动打印 "Failed to load config. Use default config."
     * 后使用默认配置。开时启动器把 MG_DIR_PATH 指到应用私有目录下的
     * mobileglues 子目录，驱动在该目录读写 config.json、glsl_cache.tmp、
     * latest.log 与 stats.json。
     */
    val copperOxidePrivateDataDir = boolSetting("copperOxidePrivateDataDir", false)

    /**
     * Copper Oxide 驱动调优总开关
     *
     * 关（默认）时启动器不写 config.json，驱动行为与此前逐字节一致。
     * 开时启动时把调优快照写成 MG_DIR_PATH 所指目录下的 config.json；
     * 数据目录开关没开也会为此把 MG_DIR_PATH 指到启动器私有目录，
     * 因为调优文件必须落在 MG_DIR_PATH 里，而共享存储不可写。
     */
    val copperOxideTuningEnabled = boolSetting("copperOxideTuningEnabled", false)

    /**
     * Copper Oxide 的 FSR1 超分档位，0=关，1=超高质量，2=质量，3=均衡，4=性能
     *
     * 以低于原生分辨率渲染再放大，是光影掉帧时真正有效的杠杆。
     */
    val copperOxideFsr = intSetting("copperOxideFsr", 0, 0..4)

    /**
     * Copper Oxide 的 GLSL 缓存大小，单位 MB，0 表示关闭缓存
     */
    val copperOxideGlslCacheMb = intSetting("copperOxideGlslCacheMb", 64, 0..512)

    /**
     * Copper Oxide 的 ANGLE 后端模式：0=驱动默认，1=尽量启用，2=强制关闭，3=强制启用
     */
    val copperOxideAngle = intSetting("copperOxideAngle", 0, 0..3)

    /**
     * Copper Oxide 的 NoError 快速路径：只提供 0=自动 与 1=关闭
     *
     * L1/L2 是会破坏游戏的作弊项，不提供。
     */
    val copperOxideNoError = intSetting("copperOxideNoError", 0, 0..1)

    /**
     * Copper Oxide 是否暴露计算着色器扩展入口
     */
    val copperOxideExtCompute = boolSetting("copperOxideExtCompute", true)

    /**
     * Copper Oxide 是否暴露计时查询扩展入口
     */
    val copperOxideExtTimerQuery = boolSetting("copperOxideExtTimerQuery", true)

    /**
     * Copper Oxide 是否暴露直接状态访问扩展入口
     */
    val copperOxideExtDsa = boolSetting("copperOxideExtDsa", true)

    /**
     * Onigami performance mode: AUTO (default), BALANCED, PERFORMANCE, QUALITY.
     * Every mode maps to real backend config keys only. Restart required
     * (config is read at context init).
     */
    val onigamiPerformanceMode = stringSetting("onigamiPerformanceMode", "AUTO")

    /**
     * Onigami shader cache size, MB, 0 disables. Restart required.
     */
    val onigamiShaderCacheMb = intSetting("onigamiShaderCacheMb", 64, 0..512)

    /**
     * Onigami state coalescing: redundant binds/mode sets never reach the driver.
     * Real toggle, read by libonigami.so at context init (restart required).
     */
    val onigamiCoalescing = boolSetting("onigamiCoalescing", true)

    /**
     * Onigami diagnostic logs: verbose per-failure lines in logcat. Real toggle,
     * read by libonigami.so at context init (restart required).
     */
    val onigamiDiagnostics = boolSetting("onigamiDiagnostics", false)

    //Game
    /**
     * 版本隔离
     */
    val versionIsolation = boolSetting("versionIsolation", true)

    /**
     * 不检查游戏完整性
     */
    val skipGameIntegrityCheck = boolSetting("skipGameIntegrityCheck", false)

    /**
     * 版本自定义信息
     */
    val versionCustomInfo = stringSetting("versionCustomInfo", "${BuildKeys.LAUNCHER_IDENTIFIER}[oxide_version]")

    /**
     * 启动器的Java环境
     */
    val javaRuntime = stringSetting("javaRuntime", "")

    /**
     * 自动选择Java环境
     */
    val autoPickJavaRuntime = boolSetting("autoPickJavaRuntime", true)

    /**
     * 游戏内存分配大小
     */
    val ramAllocation = intSetting("ramAllocation", null, min = 256)

    /**
     * 自定义Jvm启动参数
     */
    val jvmArgs = stringSetting("jvmArgs", "")

    /**
     * 已禁用的原生库插件列表
     */
    val disableNativeLibPlugins = stringListSetting("nativeLibPlugins", emptyList())

    /**
     * 启动游戏时自动展示日志，直到游戏开始渲染
     */
    val showLogAutomatic = boolSetting("showLogAutomatic", false)

    /**
     * 日志字体大小
     */
    val logTextSize = intSetting("logTextSize", 15, 5..20)

    /**
     * 日志缓冲区刷新时间
     */
    val logBufferFlushInterval = intSetting("logBufferFlushInterval", 200, 100..1000)

    //Control
    /**
     * 实体鼠标控制
     */
    val physicalMouseMode = boolSetting("physicalMouseMode", true)

    /**
     * 按键键值，按下按键呼出输入法
     */
    val physicalKeyImeCode = intSetting("physicalKeyImeCode", null)

    /**
     * 隐藏虚拟鼠标
     */
    val hideMouse = boolSetting("hideMouse", false)

    /**
     * 虚拟鼠标大小（Dp）
     */
    val mouseSize = intSetting("mouseSize", 24, 5..50)

    /**
     * 虚拟鼠标箭头热点坐标
     */
    val arrowMouseHotspot = parcelableSetting("arrowMouseHotspot", LEFT_TOP_HOTSPOT)

    /**
     * 虚拟鼠标链接选择热点坐标
     */
    val linkMouseHotspot = parcelableSetting("linkMouseHotspot", CursorHotspot(xPercent = 23, yPercent = 0))

    /**
     * 虚拟鼠标输入选择热点坐标
     */
    val iBeamMouseHotspot = parcelableSetting("iBeamMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标十字热点坐标
     */
    val crossHairMouseHotspot = parcelableSetting("crossHairMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标调整大小（上下）热点坐标
     */
    val resizeNSMouseHotspot = parcelableSetting("resizeNSMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标调整大小（左右）热点坐标
     */
    val resizeEWMouseHotspot = parcelableSetting("resizeEWMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标调整大小（全部方向）热点坐标
     */
    val resizeAllMouseHotspot = parcelableSetting("resizeAllMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标禁止/无效操作热点坐标
     */
    val notAllowedMouseHotspot = parcelableSetting("notAllowedMouseHotspot", CENTER_HOTSPOT)

    /**
     * 虚拟鼠标灵敏度
     */
    val cursorSensitivity = intSetting("cursorSensitivity", 100, 25..300)

    /**
     * 被抓获指针移动灵敏度
     */
    val mouseCaptureSensitivity = intSetting("mouseCaptureSensitivity", 100, 25..300)

    /**
     * 虚拟鼠标控制模式
     */
    val mouseControlMode = enumSetting("mouseControlMode", MouseControlMode.SLIDE)

    /**
     * 鼠标控制长按延迟
     */
    val mouseLongPressDelay = intSetting("mouseLongPressDelay", 300, 100..1000)

    /**
     * 是否开启虚拟鼠标点击操作
     */
    val enableMouseClick = boolSetting("enableMouseClick", true)

    /**
     * 是否启用手柄控制
     */
    val gamepadControl = boolSetting("gamepadControl", true)

    /**
     * SDL 下是否允许自动唤起输入法
     */
    val sdlAutoShowIme = boolSetting("sdlAutoShowIme", true)

    /**
     * 手柄输入模式（映射虚拟按键 / SDL 直通）
     */
    val gamepadInputMode = enumSetting("gamepadInputMode", GamepadInputMode.Mapped)

    /**
     * 是否已完成手柄输入模式的选择询问
     */
    val gamepadInputModePrompted = boolSetting("gamepadInputModePrompted", false)

    /**
     * 摇杆死区缩放
     */
    val gamepadDeadZoneScale = intSetting("gamepadDeadZoneScale", 100, 50..200)

    /**
     * 手柄映射配置
     */
    val gamepadMappingConfig = stringSetting("gamepadMappingConfig", "default")

    /**
     * 摇杆控制模式
     */
    val joystickControlMode = enumSetting("joystickControlMode", JoystickMode.LeftMovement)

    /**
     * 手柄摇杆控制鼠标指针时的灵敏度
     */
    val gamepadCursorSensitivity = intSetting("gamepadCursorSensitivity", 100, 25..300)

    /**
     * 手柄摇杆控制游戏视角时的灵敏度
     */
    val gamepadCameraSensitivity = intSetting("gamepadCameraSensitivity", 100, 25..300)

    /**
     * 手势控制
     */
    val gestureControl = boolSetting("gestureControl", false)

    /**
     * 手势控制点击时触发的鼠标按钮
     */
    val gestureTapMouseAction = enumSetting("gestureTapMouseAction", GestureActionType.MOUSE_RIGHT)

    /**
     * 手势控制长按时触发的鼠标按钮
     */
    val gestureLongPressMouseAction = enumSetting("gestureLongPressMouseAction", GestureActionType.MOUSE_LEFT)

    /**
     * 手势控制长按延迟
     */
    val gestureLongPressDelay = intSetting("gestureLongPressDelay", 300, 100..1000)

    /**
     * 陀螺仪控制
     */
    val gyroscopeControl = boolSetting("gyroscopeControl", false)

    /**
     * 陀螺仪控制灵敏度
     */
    val gyroscopeSensitivity = intSetting("gyroscopeSensitivity", 100, 25..300)

    /**
     * 陀螺仪采样率
     */
    val gyroscopeSampleRate = intSetting("gyroscopeSampleRate", 16, 5..50)

    /**
     * 陀螺仪数值平滑
     */
    val gyroscopeSmoothing = boolSetting("gyroscopeSmoothing", true)

    /**
     * 陀螺仪平滑处理的窗口大小
     */
    val gyroscopeSmoothingWindow = intSetting("gyroscopeSmoothingWindow", 4, 2..10)

    /**
     * 反转 X 轴
     */
    val gyroscopeInvertX = boolSetting("gyroscopeInvertX", false)

    /**
     * 反转 Y 轴
     */
    val gyroscopeInvertY = boolSetting("gyroscopeInvertY", false)

    //Launcher
    /**
     * 颜色主题色
     * Android 12+ 默认动态主题色
     */
    val launcherColorTheme = enumSetting(
        "launcherColorTheme",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorThemeType.DYNAMIC
        else ColorThemeType.EMBERMIRE
    )

    /**
     * 自定义颜色主题色
     */
    val launcherCustomColor = intSetting("launcherCustomColor", Color.Blue.toArgb())

    /**
     * 自定义颜色配色风格
     */
    val launcherCustomPaletteStyle = enumSetting("launcherCustomPaletteStyle", PaletteStyle.TonalSpot)

    /**
     * 启动器UI深色主题
     */
    val launcherDarkMode = enumSetting("launcherDarkMode", DarkMode.FollowSystem)

    /**
     * 启动器语言
     */
    val launcherLanguage = enumSetting("launcherLanguage", AppLanguage.FOLLOW_SYSTEM)

    /**
     * 启动器部分屏幕全屏
     */
    val launcherFullScreen = boolSetting("launcherFullScreen", true)

    /**
     * 启动器界面缩放，百分比
     *
     * 缩的是新 Oxide 界面自己的版面与字号（见 OxideMetrics/ Oxide.Type），和游戏内的
     * 分辨率缩放、鼠标大小那些游戏侧设置互不相干。
     */
    val launcherGuiScale = intSetting("launcherGuiScale", 100, 75..150)

    /**
     * 持续型节日彩蛋效果
     */
    val launcherFestivalEffects = boolSetting("launcherFestivalEffects", true)

    /**
     * 动画倍速
     */
    val launcherAnimateSpeed = intSetting("launcherAnimateSpeed", 5, 0..10)

    /**
     * 动画幅度
     */
    val launcherAnimateExtent = intSetting("launcherAnimateExtent", 5, 0..10)

    /**
     * 启动器页面切换动画类型
     */
    val launcherSwapAnimateType = enumSetting("launcherSwapAnimateType", TransitionAnimationType.JELLY_BOUNCE)

    /**
     * 主界面操作菜单的停泊侧
     */
    val launcherActionMenuSide = enumSetting("launcherActionMenuSide", ActionMenuSide.END)

    /**
     * 启动器背景元素不透明度
     */
    val launcherBackgroundOpacity = intSetting("launcherBackgroundOpacity", 80, 20..100)

    /**
     * 启动器视频背景音量
     */
    val videoBackgroundVolume = intSetting("videoBackgroundVolume", 0, 0..100)

    /**
     * 启动器背景模糊效果
     */
    val backgroundBlur = intSetting("backgroundBlur", 0, 0..40)

    /**
     * 启动器背景模糊效果类型
     */
    val backgroundBlurType = enumSetting("backgroundBlurType", BackgroundBlur.Background)

    /**
     * 启动器上次检查更新时，用户选择忽略的版本号
     */
    val lastIgnoredVersion = intSetting("lastIgnoredVersion", null)

    /**
     * 启动器日志保留天数
     */
    val launcherLogRetentionDays = intSetting("launcherLogRetentionDays", 7, 1..14)

    /**
     * 游戏内容下载源
     */
    val gameDownloadSource = enumSetting(
        "gameDownloadSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    /**
     * 资源平台下载源
     */
    val assetPlatformSource = enumSetting(
        "assetPlatformSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    //Control
    /**
     * 全局默认控制布局文件名
     */
    val controlLayout = stringSetting("controlLayout", "")

    //Other
    /**
     * 当前选择的账号
     */
    val currentAccount = stringSetting("currentAccount", "")

    /**
     * 当前选择的游戏目录id
     */
    val currentGamePathId = stringSetting("currentGamePathId", GamePathManager.DEFAULT_ID)

    /**
     * 启动器任务菜单是否展开
     */
    val launcherTaskMenuExpanded = boolSetting("launcherTaskMenuExpanded", true)

    /**
     * 在游戏菜单悬浮窗上显示帧率
     */
    val showFPS = boolSetting("showFPS", true)

    /**
     * 游戏内帧率的展示模式
     */
    val fpsDisplayMode = enumSetting("fpsDisplayMode", FpsDisplayMode.NUMBER)

    /**
     * 在游戏菜单悬浮窗上显示内存
     */
    val showMemory = boolSetting("showMemory", false)

    /**
     * 在游戏画面上展示菜单悬浮窗
     */
    val showMenuBall = boolSetting("showMenuBall", true)

    /**
     * 游戏菜单悬浮窗位置
     */
    val menuBallPos = offsetSetting("menuBallPos", Offset.Zero)

    /**
     * 游戏菜单悬浮窗不透明度
     */
    val menuBallOpacity = intSetting("menuBallOpacity", 100, 20..100)

    /**
     * 在游戏画面右下角显示 Oxide 品牌标识
     *
     * 与 [showMenuBall] 一类的游戏内浮层开关，但默认开着：用户要的就是"看得见"。
     * 标识只画在角落里、不接收任何触摸，所以关掉它不会改变下面任何一颗控件的命中。
     */
    val showGameBrand = boolSetting("showGameBrand", true)

    /**
     * 快捷栏判定箱计算规则
     */
    val hotbarRule = enumSetting("hotbarRule", HotbarRule.Auto)

    /**
     * 快捷栏宽度百分比
     */
    val hotbarWidth = intSetting("hotbarWidth", 500, 0..1000)

    /**
     * 快捷栏高度百分比
     */
    val hotbarHeight = intSetting("hotbarHeight", 100, 0..1000)

    /**
     * 快捷栏双击与副手交换物品
     */
    val hotbarDoubleClick = boolSetting("hotbarDoubleClick", true)

    /**
     * 快捷栏长按丢弃所选物品
     */
    val hotbarLongClick = boolSetting("hotbarLongClick", true)

    /**
     * 快捷栏长按快捷栏触发延迟
     */
    val hotbarLongClickDelay = intSetting("hotbarLongClickDelay", 300, 100..1000)

    /**
     * 游戏内控制布局的整体不透明度
     */
    val controlsOpacity = intSetting("controlsOpacity", 100, 0..100)

    /**
     * 控制布局编辑器：是否开启控件吸附功能
     */
    val editorEnableWidgetSnap = boolSetting("editorEnableWidgetSnap", true)

    /**
     * 控制布局编辑器：是否在所有控件层范围内吸附
     */
    val editorSnapInAllLayers = boolSetting("editorSnapInAllLayers", false)

    /**
     * 控制布局编辑器：控件吸附模式
     */
    val editorWidgetSnapMode = enumSetting("editorWidgetSnapMode", SnapMode.FullScreen)

    /**
     * 控制布局编辑器：高级模式
     *
     * 关（默认）是给普通用户的干净界面：选层、改控件、加控件、预览、保存都在，
     * 但吸附、样式、摇杆样式、层聚焦与换序收进"高级"里。开则全部展开，与旧版
     * 面板一致。所有功能都在，只是默认不一次全倒出来。
     */
    val editorAdvancedMode = boolSetting("editorAdvancedMode", false)

    /**
     * 是否启用陶瓦联机
     */
    val enableTerracotta = boolSetting("enableTerracotta", false)

    /**
     * 是否使用自定义 EasyTier 服务器节点
     */
    val enableTerracottaNodes = boolSetting("enableTerracottaNodes", false)

    /**
     * 陶瓦联机：自定义 EasyTier 服务器节点
     */
    val terracottaNodes = stringSetting("terracottaNodes", "")

    /**
     * 陶瓦联机公告版本号
     */
    val terracottaNoticeVer = intSetting("terracottaNoticeVer", -1)

    /**
     * 上次检查更新的时间戳
     */
    val lastUpgradeCheck = longSetting("lastUpgradeCheck", 0L)

    /**
     * 玩家结束运行游戏的次数
     */
    val finishedGame = intSetting("finishedGame", 0)

    /**
     * 是否在打开启动器时，根据特定的运行游戏次数，显示赞助支持弹窗
     */
    val showSponsorship = boolSetting("showSponsorship", true)

    /**
     * 搜索模组的初始搜索平台
     */
    val searchModPlatform = enumSetting("searchModPlatform", Platform.CURSEFORGE)

    /**
     * 搜索整合包的初始搜索平台
     */
    val searchModpackPlatform = enumSetting("searchModpackPlatform", Platform.CURSEFORGE)

    /**
     * 搜索资源包的初始搜索平台
     */
    val searchResourcePackPlatform = enumSetting("searchResourcePackPlatform", Platform.CURSEFORGE)

    /**
     * 搜索光影的初始搜索平台
     */
    val searchShadersPlatform = enumSetting("searchShadersPlatform", Platform.CURSEFORGE)
}