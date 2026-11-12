# ADR-0002: LLM 作为 Oracle 而非 Runtime

- 状态：Accepted
- 日期：2026-09-10

## 决策背景

- [ADR-0001](ADR-0001-secd-runtime.md) 选定确定性内核后，出现了新的问题： **居民仍然需要“想法”，而想法来自 LLM。这样一个不确定性的系统应该以什么身份、什么时机、多大频率接入当前这个确定性系统？**
- 不做显式决定，接入方式会默认退回 vab1.0 形态（每 tick 全量决策），LLM 服务不可用时全镇停摆。
- 反方向同样不成立：完全不接入 LLM，居民退化为传统确定性 NPC，人格与语言能力失去用武之地，LLM 的价值也无法体现。

## 决策选择

Q. 我们到底在决策什么？
A. 原先的设计中，大模型思考并行动，我们现在要将思考和行为拆开，行为已经完成交付，现在要决定如何处理思考。

Q. 既然内核是确定性的，居民“想干什么”由谁决定？让 LLM 每 tick 直接决定动作，像 vab1.0 那样？<br/>
A. 否——那正是 ADR-0001 否决的现状。LLM 只在计划编译期决定方向（`decideAction`），计划本身交给 SECD 一步步执行：确定性归机器，不确定性收敛到最少的洞。

Q. 主流 Agent 都是 tool-calling，让 LLM 直接调工具改世界，岂不更简单？<br/>
A. 否。LLM 的输出不可信任（坏 JSON、幻觉参数），让它直接改世界等于把金库钥匙交给会做梦的人。`LLMBrain` 不持有 World 引用——物理上摸不到世界，其产出只能经 SECD/Effect 路径生效。

Q. 行为程序跑到一半遇到计划外状况，把控制权整个交回 LLM 重新规划？<br/>
A. 不交权，只递纸条：`ask-llm` 原语同步问一句，返回文本压入 S 栈当普通值用。它是机器内唯一的非确定性注入点，且限制为显式原语而非隐式全局。

Q. Oracle 挂了怎么办，世界跟着停摆？<br/>
A. 两个入口都内部降级，世界永不因 Oracle 失败而停摆：`decideAction` 解析失败/调用异常 → 随机方向；`ask-llm` 调用异常 → 返回哨兵文本 `[oracle-unavailable]` 压入 S 栈，行为程序可用 `eq` 检测（2026-11-12 采纳方案 (a)——此前 `chat` 无降级，异常会穿透 TickSchedule 终止整个 tick）。

## 最终决策

- **LLM 是 Oracle，不是 Runtime。** 行为控制流由 SECD 保证。LLM 在全项目只有两个方法入口，各有枚举调用点；除此之外任何类不得持有 `ChatModel` 或调用 `LLMBrain`：

| 入口 | 调用点 | 返回 | 失败语义 |
|---|---|---|---|
| `decideAction(agent, worldState)` | 计划编译期：`DefaultPlanCompiler.compileInto`、`AgentRuntime.compileFromProgram` | JSON(deltaX, deltaY, reason)，决定 plan 的 dx/dy | 方法内部降级：解析失败/调用异常 → 随机方向（`randomDecision`），世界继续运行 |
| `chat(prompt)`（ask-llm 原语） | `WorldOpEvaluator.askLlm`（行为程序显式调用） | 模型文本原样，压入 S 栈作为值 | 方法内部降级：调用异常 → 哨兵文本 `[oracle-unavailable]`（`LLMBrain.ORACLE_UNAVAILABLE`）压入 S 栈，行为程序可用 `eq` 检测；不抛出 |

- LLM **不直接修改 World**：`LLMBrain` 不持有 World 引用，其结果只经 SECD/Effect 路径生效。
- 职责划分固定为：行为控制流 → SECD；世界状态 → World；记忆 → Memory；不确定性 → LLM。
- 咨询频率由 [virtual-ai-box.runtime.default-plan-length](../../src/main/resources/application.yml)（内置默认计划）与 DSL 行为程序自身的计划长度共同决定，二者都可以让频率高于默认值。

## 最终实现

- `brain/LLMBrain`：唯一 Oracle 封装。`decideAction` = buildPrompt 组装提示词（脚手架段为类常量，政策文本取自 `PromptRegistry`，ADR-0005）→ ChatModel 调用 → JSON 解析 + `moveClamp` 钳制 → 失败随机降级；`chat` = 原样透传 + 异常降级哨兵。
- 调用点全集（可 grep 验证）：`decideAction` 仅 `DefaultPlanCompiler.compileInto` 与 `AgentRuntime.compileFromProgram` 两处；`chat` 仅 `WorldOpEvaluator.askLlm` 一处。
- ask-llm 失败哨兵：`LLMBrain.ORACLE_UNAVAILABLE = "[oracle-unavailable]"`——压入 S 栈的普通字符串值，行为程序可用 `eq` 比较检测 Oracle 不可用并分支处理；`LLMRequestEffect` 的 result 字段同样携带哨兵（审计可辨）。
- `LLMRequestEffect`：ask-llm 咨询产信息性 Effect，`EffectExecutor` 仅日志、未持久化。
- `ask-llm` 为同步阻塞调用：咨询期间该 Agent 的 tick 被挂起等待响应。

## 决策后果

- 行为可解释：给定 plan 与 Oracle 输出，SECD 执行完全确定。
- LLM 调用频率大幅下降（默认每 3 tick 一次），tick 延迟从秒级降到毫秒级（执行阶段）。
- `ask-llm` 是同步阻塞调用，行为程序应谨慎使用（LLM 慢会卡住该 Agent 的 tick）。
- Oracle 失败永不终止 tick：两入口均内部降级——`decideAction` 随机方向、`chat` 使用哨兵文本。
- 可回放的前置条件：Oracle 每次咨询的输入与输出必须可持久化记录；当前 `LLMRequestEffect` 仅写日志，补齐记录机制前，本架构只承诺“可解释”，不承诺“可回放”。
- 遗留兼容层：`agent.decided` 事件仍带 `llmDecision=true` 语义（见 [spec/effect.md](../spec/effect.md)` §6，[DESIGN_CONCERN]）。
