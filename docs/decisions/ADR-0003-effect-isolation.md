# ADR-0003: Effect 隔离副作用

- 状态：Accepted
- 日期：2026-09-10（回溯 P1 决策；P6 修订感知部分）

## Context

- 早期设计中 SECD/求值器直接修改 World（Agent 位置、世界事件），副作用与计算耦合，行为不可审计、测试困难。
- 审查意见（原 `docs/my_opinion.md`，已并入本 ADR 后移除）："增加 Effect 层"——把"意图"与"执行"分离。
- 历史 DSL 草案曾把"观察"列为副作用原语（`observe` → ObserveEffect），感知过程因此也走 Effect 通道。

## Decision

- **三层分工固定为：**

```
SECD = 计算          → 产出 Effect（副作用描述，不执行）
Effect = 意图        → MoveEffect / SpeakEffect / RememberEffect / LLMRequestEffect
EffectExecutor = 执行 → 唯一允许修改 World 的路径（Move 经 ActionExecutor）
```

- **单向流**：SECD / WorldOpEvaluator / AgentRuntime 只产出 Effect，绝不直接修改 World；World 只被 EffectExecutor 修改。
- **move 单形态**：方向移动（`move north`）与整数移动（`move dx dy`）在 DSL 原语层归一为同一个 `MoveEffect(deltaX, deltaY)`，Effect 层不新增方向重载。
- **感知只读化（P6 修订）**：ObserveEffect 被废弃。感知原语（self/closest/dist-to/name-of/direction-of）是**读**——值直接压入 S 栈，**不产生 Effect**；"感知不到"在返回边界统一转 `#f`（null 不出机器）。
- 纯算术求值恒产出空 Effect 列表。

## Alternatives

1. **SECD 直接改 World**——被拒：副作用与计算耦合，无法审计、无法测试（`AgentRuntimeTest.worldOpEvaluatorProducesEffectsNotWorldMutation` 正是守护此边界的测试）。
2. **感知作为 Effect（ObserveEffect）**——曾被采纳后废弃（P6）：感知是输入不是副作用，走 Effect 通道会污染"Effect=对世界的改变"语义，且破坏只读承诺。

## Rationale

- 单向数据流（计算 → 意图 → 执行）使行为可审计、可测试、可回放。
- "Effect = 意图"允许同一意图被记录、展示（dashboard）、延迟执行，而不改变机器语义。
- 感知只读化使 Perception 成为纯函数输入，与 LLM Oracle 形成"确定性感知 + 非确定性决策"的对称结构。

## Consequences

- World 的修改只有一条路径 → 收敛检测、事件日志、前端可视化都能以 Effect 为审计单位。
- EffectExecutor 是副作用唯一出入口，新增世界能力必须新增 Effect 类型或复用现有四种。
- `LLMRequestEffect` 为信息性副作用（ask-llm 同步求值后仅记录，见 `spec/effect.md` §5）。
- 兼容层：每次移动仍发布 `agent.decided` 事件（[DESIGN_CONCERN]，见 `consistency-audit.md` §3.8）。
