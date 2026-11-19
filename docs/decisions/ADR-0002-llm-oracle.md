# ADR-0002: LLM 作为 Oracle 而非 Runtime

- 状态：Current
- 创建日期：2026-09-10
- 更新日期：2026-11-19

## 决策背景

- [ADR-0001](ADR-0001-secd-runtime.md) 选定确定性内核后，出现了新问题：居民仍然需要"想法"，而想法来自 LLM。这样一个不确定的系统，应该以什么身份、什么时机、多大频率接入确定性系统？
- 不做显式决定，接入方式会默认退回 vab1.0 形态（每 tick 全量决策），LLM 服务不可用时全镇停摆。
- 反方向同样不成立：完全不接入 LLM，居民退化为传统确定性 NPC，人格与语言能力失去用武之地。

## 决策方案

### 方案一：LLM 每 tick 直接决定动作
1. 世界状态整体交给 LLM，返回的动作立即执行。
2. **结论**：否决。这正是 ADR-0001 否决的现状：慢、不可解释、不可恢复。

### 方案二：LLM 以 tool-calling 直接改世界
1. 把 move/speak 等能力作为工具交给 LLM 自行调用。
2. **结论**：否决。LLM 输出不可信（坏 JSON、幻觉参数），让它直接改世界等于把金库钥匙交给会做梦的人；同时绕开了 SECD 与 Effect 两重审计。

### 方案三：LLM 只作 Oracle，在固定入口被调用
1. 计划编译期用它决定方向，行为程序内用 `ask-llm` 原语按需咨询。
2. **结论**：采纳。确定性归机器，不确定性收敛到最少的洞。

## 最终决策

- **LLM 是 Oracle，不是 Runtime。行为控制流由 SECD 保证。**

Q. 行为程序跑到一半遇到计划外状况，把控制权整个交回 LLM 重新规划？<br/>
A. 不交权，只递纸条：`ask-llm` 同步问一句，返回文本压入 S 栈当普通值用。它是机器内唯一的非确定性注入点，且限定为显式原语，而非隐式全局。

Q. Oracle 挂了会不会让世界停摆？<br/>
A. 不会。两个入口都在内部降级：`decideAction` 解析失败/调用异常 → 随机方向；`chat` 调用异常 → 哨兵文本 `[oracle-unavailable]`，行为程序可用 `eq` 检测。任何调用异常都不穿透 tick。

- 全项目只有两个方法入口，除此之外任何类不得持有 `ChatModel` 或调用 `LLMBrain`：`decideAction`（计划编译期定 dx/dy）与 `chat`（`ask-llm` 原语）。调用点、返回与失败语义见 [spec/agent-runtime.md](../spec/agent-runtime.md)「核心行为 · LLM Oracle 入口」。
- LLM **不直接修改 World**：`LLMBrain` 不持有 World 引用，其结果只能经 SECD/Effect 路径生效。
- 职责划分固定为：行为控制流 → SECD；世界状态 → World；记忆 → Memory；不确定性 → LLM。
- 咨询频率由 `virtual-ai-box.runtime.default-plan-length`（内置默认计划长度）与 DSL 行为程序自身的计划长度共同决定。

## 决策后果

- 行为可解释：给定 plan 与 Oracle 输出，SECD 执行完全确定。
- LLM 调用频率大幅下降，执行阶段延迟从秒级降到毫秒级；代价是计划粒度变粗，方向只在编译期决定一次。
- `ask-llm` 是同步阻塞调用，咨询期间该 Agent 的 tick 被挂起——行为程序应谨慎使用，LLM 慢会卡住该 Agent。
- 可回放的前置条件尚未满足：Oracle 每次咨询的输入与输出必须可持久化记录，当前 `LLMRequestEffect` 仅写日志。因此本架构只承诺"可解释"，不承诺"可回放"（[ADR-0003](ADR-0003-effect-isolation.md)）。
- 遗留兼容层：`agent.decided` 事件仍带 `llmDecision=true` 语义，与"LLM 仅作 Oracle"存在张力，属已知设计关注点，保留不改（[spec/effect.md](../spec/effect.md)「待解决问题」）。

## 相关文档

- 相关 ADR：[ADR-0001](ADR-0001-secd-runtime.md)、[ADR-0003](ADR-0003-effect-isolation.md)、[ADR-0005](ADR-0005-config-extraction.md)
- 相关 Spec：[Agent Runtime](../spec/agent-runtime.md)、[Effect](../spec/effect.md)、[Behavior DSL](../spec/behavior-dsl.md)
- 相关 Architecture：[architecture.md](../architecture.md)
