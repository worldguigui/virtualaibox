# ADR-0001: SECD 作为 Agent 行为执行内核

- 状态：Current
- 创建日期：2026-09-10
- 更新日期：2026-11-19

## 决策背景

- 项目前身 vab1.0/vab2.0 采用"每个 tick 全量状态提交 LLM 决策"：决策慢（必须等待大模型响应）、不可解释、不可恢复。
- 全量世界状态 → 解析 JSON → 执行，这整个过程是一个整体动作，无法组合、无法打断。
- 已有一套与项目无关的 λ 演算参考实现（[基于 antlr4 的 lambda 表达式识别与归约](https://gitee.com/worldguigui/antlr4.git)），含 SECD 机器与值模型，可作为语义参考移植。

## 决策方案

### 方案一：沿用 LLM 每 tick 全量决策
1. 每个 tick 把世界状态交给 LLM，返回 JSON 动作后直接执行。
2. **结论**：否决。决策延迟以秒计，行为不可解释、不可恢复。

### 方案二：手写状态机约束 LLM 行为
1. 自建状态机，用状态跳转规范 LLM 的输出。
2. **结论**：否决。等于从零造轮子，且没有现成的 continuation 支撑中断与恢复。

### 方案三：移植 SECD 抽象机，作为行为内核
1. 复用参考实现已验证的 λ 归约语义与 D 栈 continuation。
2. 剥离其一次性求值的外围结构，改为"可驻留的状态 + 无状态驱动器"。
3. **结论**：采纳。

## 最终决策

- **每个 Agent 拥有一个基于 SECD 机的行为运行时（Agent Runtime），负责执行可组合、可中断、可恢复的行为程序；SECD 机是行为执行内核，不是 Agent 本身。**

Q. Agent = SECD 抽象机？S 栈是人格、E 栈是感知、C 栈是记忆，一一对应？<br/>
A. 不是。S/E/C/D 只有计算语义，无法与人格/记忆/感知一一对应。人格、记忆、感知由 Runtime 外层持有，经指令原语与 Effect 与内核交换数据。

Q. 三个形容词如何落到机械层面？<br/>
A. **可组合** = `InstSeq` 把一组动作编排成驻留 C 栈的程序；**可中断** = `interrupt` 把主计划 {S,E,C,D} 快照挂入 D 栈、加载 handler，与闭包应用完全同构；**可恢复** = handler 结束后从 D 弹帧整体换回，主计划从被中断的精确指令处继续。三者都不是新机制，而是 SECD 既有语义的复用。

- 寄存器保持原机械语义，定义与移植对照见 [spec/agent-runtime.md](../spec/agent-runtime.md)「核心行为 · SECD 内核」。
- Agent 组成分离：Identity/Personality（静态）+ AgentMemory（外部认知状态）+ Perception（输入通道）+ AgentRuntime（行为执行内核，内含 SECD 机）。
- **Memory ≠ E，ShortTermMemory ≠ S**：两者只通过 Effect 与指令原语交换数据，不做概念合并。
- **移植边界**：机械语义（call-by-value、闭包浅拷贝捕获 E、柯里化、D 栈挂起/恢复、发散检测）原样继承；架构形态（状态与驱动分离、`OpEvaluator` 注入、只产 Effect）按本项目重写。
- **有意分歧**（经确认保留，勿当 bug 修）：`zerop` 与比较原语返回 `BoolValue`（参考实现返回 Int 0/1）；真值规则仅 `#f` 为假（参考实现以 0 为假）——动机是让 `if` 可直接组合"无对象 → `#f`"的感知原语。

## 决策后果

- 行为控制流完全由 SECD 机决定；LLM 仅经 [ADR-0002](ADR-0002-llm-oracle.md) 定义的两个入口接入。
- 每个 Agent 独占一台 SECD 机与求值器：无共享、无竞态，代价是内存与状态规模线性于 Agent 数。
- 内核只计算、只产 Effect，World 修改被隔离（[ADR-0003](ADR-0003-effect-isolation.md)）。
- 需要监控 D 栈增长：深度超过 `virtual-ai-box.runtime.convergence.max-d-stack-depth` 即视为疑似无限归约/深递归，触发告警（[ADR-0004](ADR-0004-d-stack-interrupt.md)）。

## 相关文档

- 相关 ADR：[ADR-0002](ADR-0002-llm-oracle.md)、[ADR-0003](ADR-0003-effect-isolation.md)、[ADR-0004](ADR-0004-d-stack-interrupt.md)、[ADR-0006](ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Agent Runtime](../spec/agent-runtime.md)、[Behavior DSL](../spec/behavior-dsl.md)
- 相关 Architecture：[architecture.md](../architecture.md)
