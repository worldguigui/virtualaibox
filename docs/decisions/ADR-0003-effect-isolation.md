# ADR-0003: Effect 隔离副作用

- 状态：Current
- 创建日期：2026-09-10
- 更新日期：2026-11-19

## 决策背景

- 有了想法（[ADR-0002](ADR-0002-llm-oracle.md)）和行动内核（[ADR-0001](ADR-0001-secd-runtime.md)）之后，新问题浮出：计划该如何落到世界里？光有计划和行动、世界实体没有变化，这些计划就没有意义。
- 早期草案让求值器直接改 World（Agent 位置、世界事件）。副作用与计算耦合在一起，出错时说不清是算错了还是改错了，行为不可审计、测试困难。

## 决策方案

### 方案一：求值器持有 World 引用，算完直接写
1. 最省事，但计算与事实耦合。
2. **结论**：否决。行为无法单测（必须起一个 World）、无法审计（无中间记录）、无法回放（改动已发生）。

### 方案二：各模块自行落地副作用
1. 谁需要改世界，谁自己写 World。
2. **结论**：否决。落地路径分散，审计单位不唯一，无法回答"这一步是谁改的"。

### 方案三：Effect 描述意图，执行器唯一落地
1. 内核只产"意图描述"，落地集中到一处。
2. **结论**：采纳。

## 最终决策

- **三层分工固定：**

| 层 | 职责 | 产出 |
|---|---|---|
| SECD / 求值器 | 计算 | `List<Effect>`（只描述，不执行） |
| Effect | 意图 | MoveEffect / SpeakEffect / RememberEffect / LLMRequestEffect |
| EffectExecutor | 执行 | 唯一允许修改 World 的路径（Move 经 ActionExecutor） |

- **单向流**：SECD / WorldOpEvaluator / AgentRuntime 只产出 Effect，绝不直接修改 World；World 只被 EffectExecutor 修改。

Q. 感知也是"对世界做点什么"，为什么不走 Effect？<br/>
A. 感知是读，不是写。早期 `ObserveEffect` 的语义不明确，已废弃；感知值直接压入 S 栈，不产生 Effect（[spec/perception.md](../spec/perception.md)）。

Q. `ask-llm` 既不读也不写世界，为什么要有 `LLMRequestEffect`？<br/>
A. 它是**信息性**副作用：不改变 World，但要留下"某 Agent 在此时咨询过 Oracle、问了什么、答了什么"的审计痕迹。它存在是为了可解释，不是为了可改变。

- 四种 Effect 的字段、落地动作与 move 单形态契约见 [spec/effect.md](../spec/effect.md)「核心行为」。

## 决策后果

- World 的修改只有一条路径，因此收敛检测、事件日志、前端可视化可以统一以 Effect 为审计单位：一次 Effect 列表 = 一次行为。
- 新增世界能力的成本固定：必须新增 Effect 类型并在 `EffectExecutor` 分派，不能走捷径。这条约束已影响到物品交互设计。
- 代价：`EffectExecutor` 成为改动热点，每次扩能力都要动它；但它同时是唯一需要 review 的地方。
- 约束（不可放宽）：
  - 不得新增第二处落地入口——不得绕过 `EffectExecutor` 调 `ActionExecutor` 或直接写记忆。
  - Effect 必须是纯数据 record，不得携带 `Runnable`／回调，否则内核就有了触摸世界的可能。
  - 不得复活 `ObserveEffect`，也不得为感知新增 Effect。

## 相关文档

- 相关 ADR：[ADR-0001](ADR-0001-secd-runtime.md)、[ADR-0002](ADR-0002-llm-oracle.md)、[ADR-0006](ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Effect](../spec/effect.md)、[Perception](../spec/perception.md)、[Agent Runtime](../spec/agent-runtime.md)
- 相关 Architecture：[architecture.md](../architecture.md)
