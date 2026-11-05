# Spec：Effect（副作用层）

> 状态：CURRENT。定义副作用描述与落地契约。
> 相关：ADR-0003（Effect 隔离）、`architecture.md` 不变量 1/11。

## 1. 定位

Effect 是 **SECD 计算与 World 副作用之间的隔离边界**：

```
SECD = 计算       → 产生 Effect（意图/副作用描述）
EffectExecutor  = 真正执行副作用 → 修改 World
```

- SECD / WorldOpEvaluator / AgentRuntime **只产出 Effect，绝不直接修改 World**。
- 唯一修改 World 的路径是 `EffectExecutor`（经 `ActionExecutor`）。
- Effect 是"意图"，不是"执行"：同一 Effect 可被记录、展示、审计，落地由其执行器负责。

## 2. Effect 类型（4 种，均为 record）

| Effect | 字段 | 语义 | 落地（EffectExecutor） |
|---|---|---|---|
| `MoveEffect` | agentId, deltaX, deltaY, reason | 向 (dx,dy) 移动一格 | ActionExecutor：边界钳制后改位置 → `agent.moved` + 兼容性 `agent.decided` |
| `SpeakEffect` | agentId, targetId(null=广播), content | 表达内容 | 发布 `agent.spoke` 事件 |
| `RememberEffect` | agentId, key, content | 写入自身记忆 | `AgentMemory.recordObservation(content, tick, "secd", key)` |
| `LLMRequestEffect` | agentId, prompt, response | 咨询预言机的事实 | **仅日志**（信息性副作用） |

接口：`Effect.describe()` 提供人类可读描述（事件日志/前端展示用）。

## 3. 单向流规则

1. **产生**：`SECD.step` → `OpEvaluator.apply`（WorldOpEvaluator / ArithmeticOpEvaluator）返回 `List<Effect>`；纯算术路径恒为空。
2. **收集**：`AgentRuntime.tick` 收集本 tick 全部副作用（含 handler 对话产生的）。
3. **汇总**：`MindController.decisionPhase` / `onMeet` 汇总。
4. **落地**：`EffectExecutor.execute(effects, world)` 按类型分发（顺序执行，无优先级概念）。

## 4. move 单形态契约（不变量 11）

- `move north`（方向一元）与 `move dx dy`（整数二元）最终都归一到**同一个** `MoveEffect(deltaX, deltaY)`。
- 方向→delta 的映射只发生在 `WorldOpEvaluator`（DSL 原语层）：north=(0,-1) east=(1,0) south=(0,1) west=(-1,0)。
- **Effect 层不新增方向重载**：MoveEffect 保持单形态 (dx,dy)，避免日后 move 重载泛滥。

## 5. LLMRequestEffect 语义

- `ask-llm` 原语**同步**调用 Oracle 并立即把结果（StringValue）压入 S 栈。
- LLMRequestEffect 本身不再需要被"执行"，仅记录"Agent 在本次计算中咨询了预言机"这一事实（日志/前端展示）。
- 因此 `EffectExecutor` 对它的处理是 `log.debug`。

## 6. 已知设计关注点（保留现状，不处理）

- **兼容性 `agent.decided`**：`EffectExecutor.executeMove` 在每次移动后发布 `AgentDecidedEvent`（`actionType=move`、`reason=move.reason`、`llmDecision=true`），代码注释自述"兼容旧决策事件：移动即本次心智的决定"。
  - 张力：当前心智系统不再直接做 LLM 决策（LLM 仅作 Oracle），但事件语义仍保留"agent.decided"。
  - 处置：见 `consistency-audit.md` §3.8（[DESIGN_CONCERN]，由你决策，当前不改）。

## 7. 不变量速查

- Effect 单向流（不变量 1）：SECD 只产不执行。
- move 单形态（不变量 11）：方向/整数统一 MoveEffect(dx,dy)。
- 感知不产 Effect（不变量 5）：见 `spec/perception.md`。
