# ADR-0004: D 栈作为中断/恢复机制

- 状态：Accepted
- 日期：2026-09-10（回溯 P2 决策）

## Context

- Agent 需要"事件驱动"：遇到其他 Agent 时应能**停下当前计划、处理事件、精确恢复**（相遇对话就是第一个此类需求）。
- 若不引入中断语义，只能二选一：a) 每 tick 让 LLM 重新决定"我现在该干什么"（丢失计划状态，ADR-0002 已否决）；b) 把事件排队，当前计划跑完再处理（无法及时响应、丢失交互性）。
- SECD 的 D 栈（dump stack）天然是 continuation：每次调用/挂起都会把现场压入 D 栈。

## Decision

- **D 栈即中断/恢复机制**，协议固定为三步：**Interrupt → Handle → Resume**。

```
interrupt(handler):
  D.push({S, E, C, D} 快照)     # 当前执行现场整体挂起
  S.clear(); C.clear()
  C.push(handler)               # 开始处理事件（handler 为闭包应用）

runHandler(maxSteps):
  循环直到 D 栈为空（主计划恢复）或步数耗尽：
    stepWithResume()
      # C 空且 D 非空 → 弹出 D 帧 restore 现场（恢复本身计一次状态转移，不产副作用）
      # 否则 → secd.step()

resume:                         # 主计划从被中断的精确位置继续执行
```

- **中断/恢复与闭包应用挂起同构**：相遇对话以"闭包应用"形式占 D 栈一帧，`onMeet = λother. {...}` 结束后自然解帧回到主计划。
- 恢复语义：E 引用共享、S/C/D 浅克隆快照——恢复后从被打断的那条指令继续。
- 入口：`MindController.onMeet` 对相遇双方各执行一次 interrupt + runHandler(200)。
- 保护：`ConvergenceMonitor` 将 D 栈深度 > 50 判定为异常（发布 `agent.loop`，reason=d-stack），防止无限中断嵌套。

## Alternatives

1. **无中断、事件排队**——被拒：事件处理延迟到计划结束，相遇交互性丢失。
2. **每 tick 重新由 LLM 决定优先级**——被拒：与 ADR-0002 冲突，行为不可预测、不可恢复。
3. **独立事件处理状态机**（与主计划并列）——被拒：引入第二套执行状态，破坏"单一执行内核"模型。

## Rationale

- D 栈是 SECD 原生能力，零新增概念即获得"可暂停/可恢复"——这正是选择 SECD 的价值兑现（ADR-0001）。
- 行为程序因此获得**计划保持能力**：中断期间计划驻留，事件处理完精确续跑，行为可解释。

## Consequences

- 相遇对话以闭包应用表达，与普通调用共享同一机制（无特例分支）。
- D 栈增长需监控（>50 帧告警），无限归约启发式防发散。
- `runHandler` 有步数上限（200），防止事件处理失控卡死该 Agent 的 tick。
- 测试：`AgentRuntimeTest.interruptResumesMainPlanViaDStack` 守护本契约（2026-08-22 曾有失败记录；2026-10-24 实测 9 类 75 例全部通过，见 `architecture.md` §6.1）。
