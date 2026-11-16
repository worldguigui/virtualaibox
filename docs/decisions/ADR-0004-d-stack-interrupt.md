# ADR-0004: D 栈作为中断/恢复机制

- 状态：Accepted
- 日期：2026-09-10

## 决策背景

- SECD机 也可以看作是一个状态机，所以中间状态对于它来说也就是特定的“SECD状态”而已。只要保留 SECD 四个栈的状态，它就具备从任意状态开始计算的能力。
- 行为内核有了，想法接入有了，模型也可以通过副作用影响世界了。现在的问题是，Agent 需要遇到其他 Agent 时应能**停下当前计划、处理事件、精确恢复**。相遇对话（`onMeet`）是第一个此类需求。这个能力，现在还没有实现。
- SECD 的 D 栈（dump stack）天然是 continuation：每次调用/挂起都会把现场压入 D 栈，我们可以在这方面做文章。

## 决策选择

Q. 我们到底在决策什么？<br/>
A. 内核能执行（0001）、能取想法（0002）、能改世界（0003）了；现在要划“时间”这条边界了。计划驻留在 C 栈上跨 tick 推进，当世界中插进来一个事件，这个事件的现场由谁承接。

Q. 相遇时干脆丢弃剩余计划，处理完再让 LLM 重新规划？<br/>
A. 不行。这等于每遇一次事件就丢一次计划：同一情境两次运行走法不同（行为不可预测），0001 的“可恢复”约定作废，0002 的“LLM 只在编译期决策”也被绕开。

Q. 把事件排队，等当前计划执行完再处理？<br/>
A. 不行。交互性丢失：两人擦肩的瞬间没反应，等几个 tick 后才“想起来”该打招呼，位置早就不对了。

Q. 单独写一个事件处理状态机，与主计划并列运行？<br/>
A. 不行。那会有两套执行状态、两个推进器、两处 Effect 来源——“单一执行内核”约定被破坏，而且两套状态如何同步没有干净答案。更何况，SECD机 本质上也是一个状态机，既然如此，为何要舍近求远呢？

Q. D 栈本来是给函数调用用的，拿来存“被打断的主计划”算不算滥用？<br/>
A. 不算，两件事机械上完全同构：闭包应用挂起时压 `{S,E,C,D}`，恢复时弹帧换回；中断就是“以事件 handler 为被调用者”的一次挂起。既然同构，就不该写第二套机制。`onMeet` 就是一个 `λother.{...}` 闭包，用 `InstApp` 应用，走普通调用路径，**没有特例分支**。

Q. handler 跑完就算结束吗？<br/>
A. 不是。结束条件是 **D 栈清空**（中断帧已解开、主计划已换回 C 栈），不是“handler 求值完”。handler 内部的闭包续体解帧后 C 仍可能非空，必须继续跑；若停在"值已算出"，主计划就永远回不去。这个区分是 `runHandler` 的唯一难点。

Q. 中断会不会变成“合法的死循环”？<br/>
A. 会，所以必须配预算和告警：handler 步数上限（默认 200，可配），D 栈深度超阈值（默认 50 帧）判定疑似无限中断嵌套，发布 `agent.loop`（reason=d-stack）。


## 最终决策

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

## 改造边界

### 明令禁止的改动

1. **禁止引入第二套执行状态机**：事件处理必须复用 D 栈，不得新增独立的事件运行时、线程或队列。
2. **禁止在 runHandler 中推进主计划**：D 栈清空即停（中断帧解开），不得顺带执行主计划的下一条指令——否则 meet tick 会多走一步，与正常 tick 语义不一致。
3. **禁止为 onMeet 写特例分支**：handler 必须是普通闭包经 `InstApp` 应用，不得在 `AgentRuntime` 里按 handler 类型分支。
4. **禁止无预算调用 runHandler**：步数上限不得省略，不得用 `Integer.MAX_VALUE` 绕过。
5. **禁止 handler 异常穿透 tick**：handler 求值错误与主计划同款降级（清机器、下 tick 重编译），不得抛到 `TickSchedule`。

### 涉及旧有模块与核心文件

| 模块 / 文件 | 改动 |
|---|---|
| `mind/AgentRuntime`（改造） | 新增 `interrupt` / `runHandler` / `stepWithResume`；`tick` 复用 `stepWithResume` 以支持续跑 |
| `mind/MindController`（改造） | 新增 `onMeet` / `interruptWithMeet`：取 DSL `onMeet` 闭包，缺失则退回内置对话行为 |
| `secd/MachineState` | 提供 D 栈访问与 `restore(DumpFrame)`，恢复语义在此落地 |
| `secd/DumpFrame` | 一帧承载 {S,E,C,D}，与闭包应用的挂起帧同结构 |
| `monitor/ConvergenceMonitor`（改造） | 消费 D 栈深度，超 `max-d-stack-depth` 发 `agent.loop` |
| `properties/RuntimeProperties` | `max-handler-steps`（默认 200） |
| `core/TickSchedule`（改造） | 相遇判定后调用 `mindController.onMeet`，Effect 汇入本 tick |

## 最终实现

- `AgentRuntime.interrupt(Instruction handler)`：`{S,E,C,D}` 浅克隆压入 D 栈，清空 S/C，C 置 handler。E 不复制（闭包环境共享），S/C/D 复制（现场快照）。
- `AgentRuntime.runHandler(int maxSteps)`：循环至 D 栈空或预算耗尽；每轮 `stepWithResume` 并累积 Effect；捕获 `SecdEvalException` → `state.clear()`（含未解开的中断帧），下 tick 重编译，已产出的 Effect 照常落地。
- `AgentRuntime.stepWithResume(List<Effect>)`：C 空且 D 非空时，取出 S 顶返回值 → 弹帧 → `state.restore(frame)` → 返回值压回 S（闭包应用的传值点）；否则 `secd.step(state)`。这是"恢复"的唯一实现处。
- `MindController.onMeet(tick, a1, a2, world)`：双向各一次 `interruptWithMeet`；优先用 DSL 编译出的 `onMeet` 闭包（`new InstApp(onMeet, otherRef)`），缺失则退回内置行为；返回双方 Effect 合并。
- 调用链：`TickSchedule` 相遇判定 → `onMeet` → 双方 `interrupt` + `runHandler` → Effect 汇入本 tick → `EffectExecutor` 落地（[ADR-0003](ADR-0003-effect-isolation.md)）。
- 预算与告警：`virtual-ai-box.runtime.max-handler-steps`（默认 200）、`virtual-ai-box.runtime.convergence.max-d-stack-depth`（默认 50）。
- 守护测试：`AgentRuntimeTest.interruptResumesMainPlanViaDStack`。

## 决策后果

- 相遇对话与普通函数调用共享同一机制，无特例分支。也就是说**新增事件类型不需要新机制，只需要写一个闭包**。
- `runHandler` 预算耗尽时属软失败：D 栈可能仍非空，主计划留待下 tick 继续（`tick` 同样走 `stepWithResume`，会先解帧再推进），不报错、不丢计划。
- 已知限制：结束条件取"D 栈空"，前提是常规 plan 为扁平程序（不产生 D 帧），D 栈只含中断帧。若主计划在中断时 D 本已非空（例如相遇发生在闭包内部），`runHandler` 会连带解掉那些外层帧。收口方案是记录中断前的 D 深度、以"D 回到该深度"为结束条件（尚未实现）。
- 恢复语义依赖浅克隆：S/C/D 是快照、E 是共享引用。若未来引入可变环境（如 `set!`），恢复语义需要重新论证。
- D 栈增长需监控：>50 帧判定疑似无限中断嵌套；步数上限防"合法的死循环"。