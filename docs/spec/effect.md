# Spec：Effect

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 在"计算"与"修改世界"之间划一条明确边界：内核只描述意图，落地只有一处。
- 让每一次行为都有一个可记录、可展示、可审计的单位（一次 Effect 列表 = 一次行为）。
- 使行为可单测：验证产出 Effect 不需要启动一个 World。

## 功能范围

### 包含范围

1. 四种 Effect 类型的字段与语义。
2. Effect 的产生 → 收集 → 汇总 → 落地的单向流。
3. move 的单一形态契约（方向/整数统一）。
4. `LLMRequestEffect` 的信息性语义。
5. 执行器的分派规则与约束。

不包含：谁产生 Effect（见 [agent-runtime.md](agent-runtime.md)）、感知为何不产 Effect（见 [perception.md](perception.md)）。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| effects | `List<Effect>` | 本 tick 汇总的副作用描述，按产生顺序排列 |
| world | World | 可变世界；**只有 EffectExecutor 接收它** |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| 位置变更 | World 状态变更 | `MoveEffect` 经 `ActionExecutor` 钳制后写位置 |
| 事件 | EventBus 事件 | `agent.moved`、`agent.decided`（兼容）、`agent.spoke` |
| 记忆写入 | AgentMemory 条目 | `RememberEffect` → `recordObservation` |
| 日志 | DEBUG 日志 | `LLMRequestEffect` 仅记录，不持久化 |

## 核心行为

### 四种 Effect

| Effect | 字段 | 语义 | 落地动作 |
|---|---|---|---|
| `MoveEffect` | agentId, deltaX, deltaY, reason | 向 (dx,dy) 移动一格 | 边界钳制后改位置 → 发布 `agent.moved` + 兼容性 `agent.decided` |
| `SpeakEffect` | agentId, targetId(null=广播), content | 表达内容 | 发布 `agent.spoke` |
| `RememberEffect` | agentId, key, content | 写入自身记忆 | `recordObservation(content, tick, "secd", key)` |
| `LLMRequestEffect` | agentId, prompt, response | 咨询预言机的事实 | **仅日志**，不持久化 |

四种均为 record，字段即意图的全部内容；`Effect.describe()` 提供人类可读描述，供事件日志与前端展示。

### 单向流

1. **产生**：`SECD.step` → `OpEvaluator.apply`（WorldOpEvaluator / ArithmeticOpEvaluator）返回 `List<Effect>`；纯算术路径恒为空。
2. **收集**：`AgentRuntime.tick` 收集本 tick 全部副作用（含 handler 对话产生的）。
3. **汇总**：`MindController.decisionPhase` / `onMeet` 汇总。
4. **落地**：`EffectExecutor.execute(effects, world)` 逐条 `instanceof` 分派，**按列表顺序执行，无优先级概念**。

### move 单形态

- `move north`（方向一元）与 `move dx dy`（整数二元）最终归一到**同一个** `MoveEffect(deltaX, deltaY)`。
- 方向 → delta 的映射只发生在 `WorldOpEvaluator`（DSL 原语层）：north=(0,-1)、east=(1,0)、south=(0,1)、west=(-1,0)。
- Effect 层不新增方向重载，避免日后 move 重载泛滥。

### LLMRequestEffect 的语义

- `ask-llm` 原语**同步**调用 Oracle，并立即把结果（StringValue）压入 S 栈。
- 该 Effect 不需要被"执行"，只记录"某 Agent 在本次计算中咨询了预言机"这一事实。
- 因此执行器对它的处理是 `log.debug`；Oracle 不可用时 `response` 为哨兵 `[oracle-unavailable]`。

## 约束与不变量

1. **单向流**：SECD / WorldOpEvaluator / AgentRuntime 只产出 Effect，绝不直接修改 World。
2. **单一入口**：`EffectExecutor` 是居民行为派生副作用的唯一出口；不得新增第二处调用 `ActionExecutor` 或直接写记忆的路径。
3. **纯数据**：Effect 不得内含 `Runnable`／回调，否则内核就有了触摸世界的可能。
4. **只写不读**：感知不产 Effect，历史设计中的 `ObserveEffect` 不得复活。
5. **审计单位唯一**：收敛检测、事件日志、前端可视化统一以 Effect 列表为粒度。
6. **顺序语义**：同一次 `execute` 内按列表顺序落地，不做重排或合并。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| 未识别的 Effect 类型 | 分派链无匹配分支，该条被跳过（不报错） | 单条 Effect |
| 目标 Agent 不存在（如移动时找不到） | `ActionExecutor` 记 warn 日志后返回：不移动、不发布事件 | 单条 Effect |
| 移动越界 | `ActionExecutor` 边界钳制，不抛异常 | 单条 Effect |
| Effect 列表为空 | 直接返回，无副作用 | 无 |

## 验收标准

1. 同一段行为程序重复执行，产出的 Effect 序列一致（除 Oracle 输出）。
2. `move north` 与 `move 0 -1` 产出字段完全相同的 `MoveEffect`。
3. 纯算术表达式执行后 Effect 列表为空。
4. `ask-llm` 失败时 `LLMRequestEffect.response` 为哨兵文本。
5. 除 `EffectExecutor` 外，没有任何路径能修改 World 或写入记忆。

## 相关文档

- 相关 ADR：[ADR-0002](../decisions/ADR-0002-llm-oracle.md)、[ADR-0003](../decisions/ADR-0003-effect-isolation.md)
- 相关 Spec：[Agent Runtime](agent-runtime.md)、[Perception](perception.md)、[Behavior DSL](behavior-dsl.md)
- 相关 Architecture：[architecture.md](../architecture.md)

## 待解决问题

- 兼容性事件 `agent.decided`：执行器每次移动都会发布它（`llmDecision=true`），与"LLM 仅作 Oracle"的定位存在语义张力。当前保留不改，记录见 [consistency-audit.md](../consistency-audit.md) §3.8。
