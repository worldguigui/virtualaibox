# ADR-0002: LLM 作为 Oracle 而非 Runtime

- 状态：Accepted
- 日期：2026-09-10（回溯 P1 决策）

## Context

- 项目前身（vab1.0/vab2.0）中 LLM 是**决策主体**：每个 tick 把全量世界状态提交 LLM，解析 JSON 决定动作。运行日志显示单次决策耗时 1.5-2 秒级，且行为不可解释、不可控制、不可恢复。
- 审查意见（原 `docs/my_opinion.md`，已并入本 ADR 后移除）："LLM 是 Oracle，而不是 Agent Runtime"——行为控制流应归属 SECD，LLM 只填"不确定的洞"。

## Decision

- **LLM 是 Oracle，不是 Runtime。** 行为控制流由 SECD 保证，LLM 只在两个明确的入口被调用：

| 入口 | 调用点 | 返回 |
|---|---|---|
| `decideAction(agent, worldState)` | 计划编译期（计划耗尽时） | JSON(deltaX, deltaY, reason)，决定 plan 的 dx/dy |
| `chat(prompt)`（ask-llm 原语） | 行为程序中显式调用 | 模型文本原样，压入 S 栈作为值 |

- LLM **不直接修改 World**：`LLMBrain` 不持有 World 引用，其结果只经 SECD/Effect 路径生效。
- 职责划分固定为：行为控制流 → SECD；世界状态 → World；记忆 → Memory；不确定性 → LLM。
- 降级策略：`decideAction` 解析失败/调用异常 → 随机方向（`randomDecision`），保证世界继续运行。

## Alternatives

1. **每 tick 全量 LLM 决策**（vab1.0/vab2.0）——被拒：慢、非确定、每 tick 都让 LLM 猜测"我现在该干什么"。
2. **LLM 直接执行动作/修改 World**——被拒：破坏机械可靠承诺，无法保证控制流与可恢复性。
3. **LLM 作为纯函数内嵌每个原语**——部分被采纳：`ask-llm` 即此形态，但限制为显式原语而非隐式全局。

## Rationale

- "Deterministic Runtime + Non-deterministic Oracle"：非确定性被收敛到最少的洞（每 3 tick 一次方向决策 + 显式 ask-llm），其余全部可解释、可回放。
- 默认计划 `PATH_LENGTH = 3`：每 3 tick 才咨询一次 Oracle，取代原先每 tick 直调。

## Consequences

- 行为可解释：给定 plan 与 Oracle 输出，SECD 执行完全确定。
- LLM 调用频率大幅下降（每 3 tick 一次），tick 延迟从秒级降到毫秒级（执行阶段）。
- `ask-llm` 是同步阻塞调用，行为程序应谨慎使用（LLM 慢会卡住该 Agent 的 tick）。
- 遗留兼容层：`agent.decided` 事件仍带 `llmDecision=true` 语义（见 `spec/effect.md` §6，[DESIGN_CONCERN]）。
