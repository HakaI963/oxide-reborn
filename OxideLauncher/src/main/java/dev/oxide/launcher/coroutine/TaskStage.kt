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

package dev.oxide.launcher.coroutine

enum class TaskStage {
    /** 预备 */
    PREPARING,
    /** 运行中 */
    RUNNING,
    /** 已完成 */
    COMPLETED
}

/**
 * 一个任务最终怎么了
 *
 * [TaskStage] 只有三个值，而它答不了"这件事是成功了还是炸了"：[TaskSystem] 捕获到非取消异常
 * 时**不会**把阶段改成某个失败值——阶段停在 RUNNING/PREPARING，任务随后从在跑列表里消失。
 * 因此"任务失败过"这件事在阶段上没有任何痕迹，而任务面板需要如实报出来。
 *
 * 所以另立这一个枚举，而不是往 [TaskStage] 里加值：[TaskStage] 在
 * `CommonElements` / `OxideInstallPanel` / `OxideLaunchPage` / `OxideLaunchSurface` /
 * `OxideInstallVersionPage` 里都以 `when (stage) { ... }` 的**表达式**形式穷举，
 * 多一个常量就是五处编译错误，而它们都不该为了这个面板改。两者也是不同的轴：
 * 阶段服务于进度条，结局服务于"这一条现在算不算结束"。
 */
enum class TaskOutcome {
    /** 交出去了，还没开始跑 */
    Queued,

    /** 正在跑 */
    Running,

    /** 跑完了，没有抛异常 */
    Succeeded,

    /** 抛了异常（取消与网络中断不算，那两种走 [Cancelled] / 不记） */
    Failed,

    /** 用户主动取消 */
    Cancelled,
    ;

    /** 收尾的三种：这一条已经不在"在跑"里了 */
    val finished: Boolean
        get() = this == Succeeded || this == Failed || this == Cancelled
}

/**
 * 阶段默认对应哪一种结局
 *
 * 只有三个阶段，所以只有三种映射：**失败与取消没有对应阶段**，它们只经由
 * `Task.markOutcome` 写进来（见 [TaskOutcome] 的说明）。
 */
val TaskStage.outcome: TaskOutcome
    get() = when (this) {
        TaskStage.PREPARING -> TaskOutcome.Queued
        TaskStage.RUNNING -> TaskOutcome.Running
        TaskStage.COMPLETED -> TaskOutcome.Succeeded
    }