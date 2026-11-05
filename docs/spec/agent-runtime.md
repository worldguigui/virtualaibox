# Spec：Agent Runtime（AgentRuntime / MindController / TickSchedule）

> 状态：CURRENT。定义 Agent 行为执行运行时的当前契约。
> 相关：ADR-0001（SECD 内核）、ADR-0004（D 栈中断）、`architecture.md` §3。
> 测试状态见 `architecture.md` §6.1（2026-10-24 / `.\mvnw.cmd test` / 9 类 75 例全部通过）。

## 1. Tick 调度契约（TickSchedule）

`WorldEngine.step()` 推进一个 tick：

```
WorldEngine.step()
  → VirtualClock.stepForward()        # tick+1
  → TickSchedule.processTick(tick, world)
      Phase1   发布 tick.started
      Phase2   decisionPhase：MindController 推进所有 Agent 心智，汇总 List<Effect>
      Phase3   executionPhase：EffectExecutor.execute(effects, world) 落地副作用
      Phase4   interactionPhase：检测相遇 → agent.met → 首次相遇触发 onMeet
      Phase4.5 convergencePhase：ConvergenceMonitor.monitor(tick, world)
      Phase5   发布 tick.ended（统计本 tick 事件数）
```

- **相遇判定**：欧氏距离 ≤ 1.5 视为相遇；`agent.met` 每次相遇都发布。
- **相遇去抖**：同 pair（排序键 `id1|id2`）**首次相遇**才触发 onMeet 对话，避免每 tick 重复打招呼。
- **onMeet 副作用**：对话产生的 SpeakEffect/RememberEffect 在本 tick 立即落地（Phase4 内调 EffectExecutor）。
- **收敛检测时机**：在 Phase4 之后、Phase5 之前，观察的是"副作用已落地后"的世界位置。

## 2. AgentRuntime 执行契约

### 2.1 每 Agent 一台运行时

- `MindController` 维护 `runtimes` 缓存（ConcurrentHashMap，key=agentId），惰性创建 `AgentRuntime(agent, llmBrain, behaviorRegistry.resolve(agent))`。
- `WorldEngine` 负责居民创建、加入世界、记忆初始化和 EventBus 注册；看板接口不得绕过该生命周期入口直接拼装居民。
- 每个 AgentRuntime 持有一份独立 `MachineState`（S/E/C/D）与独立 `WorldOpEvaluator`（无共享、无竞态）。
- 行为程序来源：DSL（`BehaviorProgram`，非 null 时用）或内置 `DefaultPlanCompiler`（null 时回退）。

### 2.2 tick(worldState) 语义

```
tick(worldState):
  1. evaluator.setPerception(WorldPerception.of(agent, worldState))   # P6 只读快照注入
  2. 若状态已终止（C/D 空）→ 编译新计划压入 C：
       - 有 BehaviorProgram → compileFromProgram：E = defs + {dx, dy}(LLM Oracle 决定)，plan 压 C
       - 否则 → DefaultPlanCompiler.compileInto：[remember "goal"; move dx dy ×3]
  3. 循环（≤ MAX_STEPS_PER_TICK = 500）：
       stepWithResume() 单步推进
       若**本步**产出 MoveEffect/SpeakEffect（"主动作"）→ 立即停止
  4. 返回本 tick 全部副作用
```

要点：

- **一步主动作即停**：一个 tick 至多让 Agent 执行一步"世界动作"；remember/ask-llm 等次级副作用不中断本 tick。
- **计划跨 tick 驻留 C 栈**：`PATH_LENGTH = 3`，即默认计划每 3 个 tick 耗尽并重编译一次（约每 3 tick 咨询一次 LLM Oracle）。
- **感知注入时机**：同一 tick 内所有 Agent 看到同一 WorldState 快照（确定性）。

### 2.3 D 栈中断/恢复协议（P2）

```
interrupt(handler):
  D.push({S, E, C, D} 快照)     # 与闭包应用挂起同构
  S.clear(); C.clear(); C.push(handler)

runHandler(maxSteps):
  循环直到 D 空（中断帧解开、主计划恢复）或步数耗尽：
    stepWithResume()  # C 空但 D 非空时，先弹出 D 帧整体换回现场再继续

stepWithResume():
  若 C 空且 D 非空 → 弹 D 帧 restore 现场（恢复本身算一次状态转移，不产副作用）
  否则 → secd.step()
```

- **恢复语义**：主计划从被中断的**精确位置**继续（E 引用共享、S/C/D 浅克隆快照）。
- **handler 内嵌套闭包**：如 DSL `onMeet = λother. { greet other; persona other }`，解帧后 C 仍可能非空，`runHandler` 会继续求值直到 D 栈清空，而不是停在解帧处。
- **入口调用方**：`MindController.onMeet` 对相遇双方各执行一次 `interruptWithMeet`（DSL onMeet 或内置 onMeet），随后 `runHandler(200)`。
- **D 栈保护**：`ConvergenceMonitor` 检测 D 栈深度 > 50 时发布 `agent.loop`（reason=d-stack）。

### 2.4 状态机（供 dashboard）

`AgentRuntime.mindSummary()` 暴露四寄存器摘要与状态：

| 状态 | 判定 | 含义 |
|---|---|---|
| idle | C 与 D 均为空 | 计划耗尽，等待重编译 |
| executing | C 非空、D 空 | 正在执行行为程序 |
| suspended | D 非空 | 被中断/有闭包续体挂起 |

## 3. 默认计划编译器（DefaultPlanCompiler）

- **默认计划**（计划耗尽时编译）：`llmBrain.decideAction(agent, worldState)` 决定方向 (dx,dy)，编译为 `[remember "goal" "head-dx,dy"; move dx dy ×3]` 的 InstSeq。
- **内置 onMeet**（无 DSL onMeet 时）：`apply(λother. (speak other 问候; remember "met" "遇见了 X"), other)`，以闭包应用形式占 D 栈一帧。
- LLM 决策失败时由 `LLMBrain` 自行降级为随机方向（`decideAction` 内部 catch）。

## 4. MindController

- 职责：决策阶段驱动全部 Agent 运行时、汇总 Effects；相遇时对双方执行 onMeet 中断。
- 构造：`MindController(LLMBrain)` 与 `MindController(LLMBrain, BehaviorRegistry)` 两种（Spring 注入后者）。
- `getRuntime(agentId)` / `getRuntimes()`：供 dashboard 与 ConvergenceMonitor 读取 SECD 状态。

## 5. 相关不变量速查

- tick 步数预算 500 / run 总步数预算 10_000（SECD 层）。
- 发散检测：步数超限时检查 D 栈规模、状态快照回环、D 栈增长计数（`SECD.run`）。
- Effect 单向流：运行时与求值器只产出 Effect，World 修改仅发生在 EffectExecutor（见 `spec/effect.md`）。
