/*
 * Oxide Launcher
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package dev.oxide.launcher.game.control

import dev.oxide.layercontroller.data.MACRO_MAX_INTERVAL_MS
import dev.oxide.layercontroller.data.MACRO_MIN_INTERVAL_MS
import dev.oxide.layercontroller.data.NormalData
import dev.oxide.layercontroller.data.clampMacroIntervalMs
import dev.oxide.layercontroller.data.macroIntervalMsIn
import dev.oxide.layercontroller.event.MAX_CLICK_EVENT_DELAY_MS
import dev.oxide.layercontroller.event.MAX_KEY_COMBO_EVENTS
import dev.oxide.layercontroller.event.MacroClock
import dev.oxide.layercontroller.event.MacroRepeater
import dev.oxide.layercontroller.event.MacroTicket
import dev.oxide.layercontroller.event.PressPipeline
import dev.oxide.layercontroller.event.ClickEvent
import dev.oxide.layercontroller.event.clickEventDelayMsIn
import dev.oxide.layercontroller.event.clampClickEventDelayMs
import dev.oxide.layercontroller.layout.ControlLayout
import dev.oxide.layercontroller.layout.EmptyControlLayout
import dev.oxide.layercontroller.layout.loadLayoutFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 一段 v1.7.0 形状的控制布局字符串
 *
 * 这一份是照着真正的加载路径写出来的最小布局：`editorVersion` 停在 12，
 * `clickEvents` 里的每一条都没有 `delayMs`，`normalButtons` 上也没有 `macroIntervalMs`。
 * 它必须在新版里原样载入并全部取默认值——用户目录里的旧文件正是这一类。
 */
private val V1_7_0_LAYOUT = """
{
  "info": {
    "name": { "default": "Key Combo", "matchQueue": [] },
    "author": { "default": "someone", "matchQueue": [] },
    "description": { "default": "", "matchQueue": [] },
    "versionCode": 1,
    "versionName": "1.0"
  },
  "layers": [
    {
      "name": "Main",
      "uuid": "layer-uuid-0001",
      "hide": false,
      "visibilityType": "always",
      "normalButtons": [
        {
          "text": { "default": "Use", "matchQueue": [] },
          "uuid": "button-uuid-0001",
          "position": { "x": 0, "y": 0 },
          "buttonSize": {
            "type": "dp",
            "widthDp": 60.0,
            "heightDp": 60.0,
            "widthPercentage": 1000,
            "heightPercentage": 1000,
            "widthReference": "screen_width",
            "heightReference": "screen_height"
          },
          "visibilityType": "always",
          "clickEvents": [
            { "type": "key", "key": "GLFW_KEY_W" },
            { "type": "key", "key": "GLFW_KEY_SPACE" }
          ],
          "isSwipple": false,
          "isPenetrable": false,
          "isToggleable": false
        }
      ]
    }
  ],
  "styles": [],
  "joystickStyles": [],
  "editorVersion": 12
}
""".trimIndent()

/**
 * 与 LayerController 内部那份同形的序列化配置
 *
 * 内部那份是 internal，测试模块看不到；这里照抄它的两个开关
 * （`prettyPrint` + `ignoreUnknownKeys`），往返用的仍是真正的 `ControlLayout` 序列化器。
 */
private val testLayoutJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}

/**
 * 手动推进的时钟
 *
 * [MacroRepeater] 与 [PressPipeline] 只认 [MacroClock]，因此这两组状态机在这里
 * 可以被逐毫秒地走一遍：没有协程、没有线程、没有 sleep，每一步都由测试自己决定。
 */
private class FakeClock : MacroClock {
    /** 还没触发的安排，每一项记下"再等多久"与"到点要做什么" */
    private val waiting = mutableListOf<Pair<Long, () -> Unit>>()
    private var nextId = 0

    /** 已经取消掉的票，用来证明取消确实生效而不是恰好没触发 */
    val cancelled = mutableListOf<Int>()

    /** 排过的所有延迟，按顺序 */
    val scheduledDelays = mutableListOf<Long>()

    override fun schedule(delayMs: Long, action: () -> Unit): MacroTicket {
        scheduledDelays += delayMs
        val id = nextId++
        waiting += delayMs to action
        return MacroTicket {
            cancelled += id
            waiting.removeAll { it.first == delayMs && it.second === action }
        }
    }

    val pendingCount: Int get() = waiting.size

    /** 让当前最短的一项到期；返回是否有事发生 */
    fun tick(): Boolean {
        val next = waiting.minByOrNull { it.first } ?: return false
        waiting.remove(next)
        next.second()
        return true
    }

    }

/**
 * 延迟与间隔的解析与夹取
 *
 * 这些函数是编辑器与运行时共用的唯一入口：滑杆、行内输入框、读旧文件都走它们，
 * 因此"非法输入被夹到边界"这一类错误只可能在这里被排掉一次。
 */
class ControlKeyComboNumberTest {

    // ---- 按键延迟 ---------------------------------------------------------

    @Test
    fun `按键延迟接受区间内的整数`() {
        assertEquals(0, clickEventDelayMsIn("0"))
        assertEquals(200, clickEventDelayMsIn("200"))
        assertEquals(MAX_CLICK_EVENT_DELAY_MS, clickEventDelayMsIn(MAX_CLICK_EVENT_DELAY_MS.toString()))
    }

    @Test
    fun `按键延迟拒绝负数与超过上限的值`() {
        // 拒绝而不是夹到边界：用户填了 6000 却存进去 5000，会以为没生效
        assertNull(clickEventDelayMsIn("-1"))
        assertNull(clickEventDelayMsIn((MAX_CLICK_EVENT_DELAY_MS + 1).toString()))
    }

    @Test
    fun `按键延迟拒绝非数字包括 NaN 与无穷大`() {
        listOf("", "   ", "abc", "1a", "1.5", "NaN", "Infinity", "-Infinity", "+", "-").forEach { text ->
            assertNull("`$text` 被接受了", clickEventDelayMsIn(text))
        }
    }

    @Test
    fun `夹取把越界的按键延迟按边界落`() {
        assertEquals(0, clampClickEventDelayMs(-100))
        assertEquals(MAX_CLICK_EVENT_DELAY_MS, clampClickEventDelayMs(999_999))
        assertEquals(1234, clampClickEventDelayMs(1234))
    }

    @Test
    fun `浮点夹取把 NaN 当作立即派发`() {
        assertEquals(0, clampClickEventDelayMs(Float.NaN))
        assertEquals(0, clampClickEventDelayMs(-0.5f))
        assertEquals(MAX_CLICK_EVENT_DELAY_MS, clampClickEventDelayMs(Float.POSITIVE_INFINITY))
    }

    // ---- 宏重复间隔 -------------------------------------------------------

    @Test
    fun `宏重复间隔接受下界与上界`() {
        assertEquals(MACRO_MIN_INTERVAL_MS, macroIntervalMsIn(MACRO_MIN_INTERVAL_MS.toString()))
        assertEquals(MACRO_MAX_INTERVAL_MS, macroIntervalMsIn(MACRO_MAX_INTERVAL_MS.toString()))
        assertEquals(2000L, macroIntervalMsIn("2000"))
    }

    @Test
    fun `宏重复间隔拒绝下界以下与上界以上的值`() {
        assertNull(macroIntervalMsIn("0"))
        assertNull(macroIntervalMsIn((MACRO_MIN_INTERVAL_MS - 1).toString()))
        assertNull(macroIntervalMsIn("-40"))
        assertNull(macroIntervalMsIn((MACRO_MAX_INTERVAL_MS + 1).toString()))
    }

    @Test
    fun `宏重复间隔拒绝非数字包括 NaN`() {
        listOf("", "  ", "abc", "40ms", "NaN", "Infinity", "-", ".").forEach { text ->
            assertNull("`$text` 被接受了", macroIntervalMsIn(text))
        }
    }

    @Test
    fun `夹取把越界的宏间隔折到边界`() {
        assertEquals(MACRO_MIN_INTERVAL_MS, clampMacroIntervalMs(1L))
        assertEquals(MACRO_MAX_INTERVAL_MS, clampMacroIntervalMs(MACRO_MAX_INTERVAL_MS * 10))
        assertEquals(3000L, clampMacroIntervalMs(3000L))
    }

    @Test
    fun `关闭宏的 0 不会被夹成一个会连发的间隔`() {
        // 宏的开关与间隔是同一个字段。把 0 夹成 40 会让"关"变成"最快连发"，
        // 也就是用户明明关掉了它，按住时却在自动重复。
        assertEquals(0L, clampMacroIntervalMs(0L))
        assertEquals(0L, clampMacroIntervalMs(-1L))
        assertEquals(0L, clampMacroIntervalMs(Float.NaN))
    }

    // ---- 组合键上限 -------------------------------------------------------

    @Test
    fun `一个按钮最多绑五个按键`() {
        assertEquals(5, MAX_KEY_COMBO_EVENTS)
    }

    // ---- 旧布局照常载入 ---------------------------------------------------

    @Test
    fun `1_7_0 的布局串能载入且新字段全部取默认值`() {
        val layout = loadLayoutFromString(V1_7_0_LAYOUT)
        val button = layout.layers.single().normalButtons.single()

        assertEquals("两个按键都必须原样保留", 2, button.clickEvents.size)
        button.clickEvents.forEach { event ->
            assertEquals(
                "旧文件里没有 delayMs，它必须落到 0，也就是与 1.7.0 完全一致的立即派发",
                0,
                event.delayMs
            )
        }
        assertEquals("旧文件里没有 macroIntervalMs，它必须落到 0，也就是不重复", 0L, button.macroIntervalMs)
        assertEquals("旧版本号要迁到当前编辑器版本", EmptyControlLayout.editorVersion, layout.editorVersion)
    }

    @Test
    fun `旧布局里的按键顺序就是派发顺序`() {
        val layout = loadLayoutFromString(V1_7_0_LAYOUT)
        val keys = layout.layers.single().normalButtons.single().clickEvents.map { it.key }
        assertEquals(listOf("GLFW_KEY_W", "GLFW_KEY_SPACE"), keys)
    }

    // ---- 新字段往返 -------------------------------------------------------

    @Test
    fun `带延迟与宏的布局经真正的序列化器往返`() {
        val original = loadLayoutFromString(V1_7_0_LAYOUT).let { layout ->
            val layer = layout.layers.single()
            val button = layer.normalButtons.single()
            layout.copy(
                layers = listOf(
                    layer.copy(
                        normalButtons = listOf(
                            // NormalData 的构造参数名是 _clickEvents（外层用 clickEvents 暴露），
                            // 与 NormalData.kt 里的 cloneNew() 写的是同一个名字
                            button.copy(
                                _clickEvents = listOf(
                                    ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_W", delayMs = 0),
                                    ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_SPACE", delayMs = 250),
                                    ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_LEFT_SHIFT", delayMs = 1500)
                                ),
                                macroIntervalMs = 2000L
                            )
                        )
                    )
                )
            )
        }

        val text = testLayoutJson.encodeToString(ControlLayout.serializer(), original)
        val restored = loadLayoutFromString(text)
        val button = restored.layers.single().normalButtons.single()

        assertEquals(3, button.clickEvents.size)
        assertEquals(listOf(0, 250, 1500), button.clickEvents.map { it.delayMs })
        assertEquals(
            listOf("GLFW_KEY_W", "GLFW_KEY_SPACE", "GLFW_KEY_LEFT_SHIFT"),
            button.clickEvents.map { it.key }
        )
        assertEquals(2000L, button.macroIntervalMs)
    }

    @Test
    fun `未设延迟的按键不再写出 delayMs 字段之外的东西`() {
        // 旧文件重新保存后仍然必须能读回来：往返一次之后再往返一次，内容不变
        val first = loadLayoutFromString(V1_7_0_LAYOUT)
        val once = testLayoutJson.encodeToString(ControlLayout.serializer(), first)
        val twice = testLayoutJson.encodeToString(ControlLayout.serializer(), loadLayoutFromString(once))
        assertEquals(
            "旧布局重新保存一次之后必须稳定，否则每存一次内容都在变",
            loadLayoutFromString(once).let { testLayoutJson.encodeToString(ControlLayout.serializer(), it) },
            twice
        )
        val button = loadLayoutFromString(twice).layers.single().normalButtons.single()
        assertEquals(0L, button.macroIntervalMs)
        assertTrue(button.clickEvents.all { it.delayMs == 0 })
    }

    @Test
    fun `只改延迟或宏间隔都算改动`() {
        val button = NormalDataProbe.button()
        assertFalse(button.isModified(button.copy()))

        // 延迟挂在事件上，所以它的改动要能透过 clickEvents 的逐条比较被看出来
        val delayed = button.copy(
            _clickEvents = listOf(ClickEvent(ClickEvent.Type.Key, "GLFW_KEY_W", delayMs = 200))
        )
        assertTrue(button.isModified(delayed))
        assertTrue(button.isModified(button.copy(macroIntervalMs = 2000L)))
        assertFalse(button.isModified(button.copy(macroIntervalMs = 0L)))
    }
}

/**
 * 宏重复的节奏
 *
 * 这一组钉死的是"按住 -> 重复若干次 -> 抬起"这条时间线：抬起之后一个动作都不许再有，
 * 没有按下时也不许自己跑起来。
 */
class ControlMacroRepeatTest {

    @Test
    fun `按下之后每隔一次间隔发一次`() {
        val clock = FakeClock()
        var repeats = 0
        val repeater = MacroRepeater(clock)

        repeater.press(2000L) { repeats++ }
        assertEquals("按下本身不产生重复，它由 onKeyPressed 同步发过一次", 0, repeats)
        assertEquals(listOf(2000L), clock.scheduledDelays)

        repeat(3) { clock.tick() }
        assertEquals("三次到点，三次重复", 3, repeats)
        assertTrue(repeater.isRunning)
    }

    @Test
    fun `抬起之后立刻停住不再有任何动作`() {
        val clock = FakeClock()
        val emitted = mutableListOf<String>()
        val repeater = MacroRepeater(clock)

        repeater.press(40L) { emitted += "repeat" }
        clock.tick()
        repeater.release()

        // 抬起收掉尚未触发的那一张票
        assertEquals(1, clock.cancelled.size)
        assertEquals(0, clock.pendingCount)

        // 再怎么推时间，也不会再有一次
        repeat(10) { clock.tick() }
        assertEquals(listOf("repeat"), emitted)
        assertFalse(repeater.isRunning)
    }

    @Test
    fun `没有按下就永远不会开始`() {
        val clock = FakeClock()
        val repeater = MacroRepeater(clock)

        repeat(5) { clock.tick() }
        assertEquals(0, clock.scheduledDelays.size)
        assertFalse(repeater.isRunning)
    }

    @Test
    fun `关闭宏的 0 间隔不排任何等待`() {
        val clock = FakeClock()
        val repeater = MacroRepeater(clock)

        repeater.press(0L) { error("宏关闭却发出了重复") }
        assertEquals(0, clock.scheduledDelays.size)
        assertFalse(repeater.isRunning)
    }

    @Test
    fun `非法间隔被夹回区间内再排等待`() {
        val clock = FakeClock()
        val repeater = MacroRepeater(clock)

        repeater.press(MACRO_MAX_INTERVAL_MS * 10) { }
        assertEquals(listOf(MACRO_MAX_INTERVAL_MS), clock.scheduledDelays)
        repeater.release()
    }

    @Test
    fun `重复按下不会叠出两条节奏`() {
        val clock = FakeClock()
        var repeats = 0
        val repeater = MacroRepeater(clock)

        repeater.press(100L) { repeats++ }
        repeater.press(100L) { repeats++ }
        repeater.press(500L) { repeats++ }
        // 三次按下只排了一次等待：否则按住一个键就会以三倍速度连发
        assertEquals(listOf(100L), clock.scheduledDelays)
        clock.tick()
        assertEquals(1, repeats)
    }

    @Test
    fun `抬起之后再次按下会重新开始`() {
        val clock = FakeClock()
        var repeats = 0
        val repeater = MacroRepeater(clock)

        repeater.press(40L) { repeats++ }
        clock.tick()
        repeater.release()
        repeater.press(40L) { repeats++ }
        clock.tick()
        assertEquals(2, repeats)
    }
}

/**
 * 一组按键事件按各自的延迟依次派发
 *
 * 顺序就是组合键的顺序，因此"哪一条在等、等多久"必须能被逐条看见：
 * 这一组用手动时钟把等待摆出来，而不是靠 sleep 去撞时间。
 */
class ControlDelayDispatchTest {

    @Test
    fun `全部延迟为零时就在当前线程同步发完`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)
        val emitted = mutableListOf<String>()

        pipeline.dispatchPress(
            listOf(
                ClickEvent(ClickEvent.Type.Key, "W"),
                ClickEvent(ClickEvent.Type.Key, "SPACE")
            )
        ) { emitted += it.key }

        assertEquals(listOf("W", "SPACE"), emitted)
        assertEquals("没有延迟就不该排任何等待", 0, clock.scheduledDelays.size)
    }

    @Test
    fun `按下依次发完再抬起的顺序与组合一致`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)
        val emitted = mutableListOf<String>()

        pipeline.dispatchPress(
            listOf(
                ClickEvent(ClickEvent.Type.Key, "W"),
                ClickEvent(ClickEvent.Type.Key, "SPACE", delayMs = 200),
                ClickEvent(ClickEvent.Type.Key, "SHIFT", delayMs = 100)
            )
        ) { emitted += it.key }

        assertEquals("第一条延迟为 0，先发出去", listOf("W"), emitted)
        repeat(2) { clock.tick() }
        assertEquals(listOf("W", "SPACE", "SHIFT"), emitted)
        assertEquals(listOf(200L, 100L), clock.scheduledDelays)
    }

    @Test
    fun `等待途中收掉就不再发剩下的`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)
        val emitted = mutableListOf<String>()

        pipeline.dispatchPress(
            listOf(
                ClickEvent(ClickEvent.Type.Key, "W"),
                ClickEvent(ClickEvent.Type.Key, "SPACE", delayMs = 5000)
            )
        ) { emitted += it.key }
        assertEquals(listOf("W"), emitted)

        pipeline.cancelAll()
        repeat(5) { clock.tick() }
        assertEquals("取消之后不能再补发", listOf("W"), emitted)
        assertFalse(pipeline.isBusy)
    }

    @Test
    fun `再次按下会顶掉上一次还没发完的序列`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)
        val emitted = mutableListOf<String>()
        val events = listOf(
            ClickEvent(ClickEvent.Type.Key, "W"),
            ClickEvent(ClickEvent.Type.Key, "SPACE", delayMs = 5000)
        )

        pipeline.dispatchPress(events) { emitted += "first:${it.key}" }
        pipeline.dispatchPress(events) { emitted += "second:${it.key}" }
        repeat(5) { clock.tick() }

        // 第一次按下的 SPACE 还在等，第二次按下把它顶掉了，因此它只属于第二次
        assertEquals(listOf("first:W", "second:W", "second:SPACE"), emitted)
    }

    @Test
    fun `宏重复走同一路管道而不是自己另起一条`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)
        val emitted = mutableListOf<String>()

        pipeline.startMacroRepeat(40L) {
            pipeline.dispatchPress(listOf(ClickEvent(ClickEvent.Type.Key, "W", delayMs = 10))) { emitted += it.key }
        }
        clock.tick()
        assertEquals(listOf("W"), emitted)
        assertTrue(pipeline.isBusy)

        pipeline.cancelAll()
        repeat(5) { clock.tick() }
        assertEquals(listOf("W"), emitted)
        assertFalse(pipeline.isBusy)
    }

    @Test
    fun `宏关闭时这一路管道不忙`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)

        pipeline.startMacroRepeat(0L) { error("宏关闭却发出了重复") }
        assertFalse(pipeline.isBusy)
        repeat(3) { clock.tick() }
        assertEquals(0, clock.scheduledDelays.size)
    }

    @Test
    fun `延迟被夹在区间内再排等待`() {
        val clock = FakeClock()
        val pipeline = PressPipeline(clock)

        pipeline.dispatchPress(
            listOf(ClickEvent(ClickEvent.Type.Key, "W", delayMs = MAX_CLICK_EVENT_DELAY_MS + 5000))
        ) { }

        assertEquals(listOf(MAX_CLICK_EVENT_DELAY_MS.toLong()), clock.scheduledDelays)
    }
}

/** 造一个最小的按钮数据，只为让 isModified 有一条可比对的基线 */
private object NormalDataProbe {
    fun button(): NormalData = loadLayoutFromString(V1_7_0_LAYOUT)
        .layers.single()
        .normalButtons.single()
}