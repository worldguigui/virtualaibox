# ADR-0001: SECD 作为 Agent 行为执行内核

- 状态：Accepted
- 日期：2026-09-10（回溯 P0 决策）

## Context

- 项目最初的方向描述是"**把每个 Agent 变成一台 SECD 抽象机**"。审查意见（原 `docs/my_opinion.md`，已并入本 ADR 后移除）指出该表述不准确：SECD 是行为执行内核，而不是 Agent 本身。
- 项目前身 vab1.0/vab2.0 采用"每个 tick 全量状态提交 LLM 决策"，存在：决策慢（单次 1.5-2 秒级）、不可解释、不可恢复的问题。
- 同时存在一套与项目无关的 λ 演算参考实现（lambdaexpr），含 SECD 机器与值模型，可作为语义参考移植。

## Decision

- **每个 Agent 拥有一个基于 SECD 的行为运行时（Agent Runtime），负责执行可组合、可中断、可恢复的行为程序。**
- SECD 是 Agent 的**行为执行内核**，不是 Agent 本身。
- S/E/C/D 保持 SECD 原始机械语义，不与记忆/人格强行对应：

| 寄存器 | 职责 |
|---|---|
| S | 当前计算过程中的值栈 |
| E | 当前程序的词法环境 / 闭包环境 |
| C | 当前正在执行的行为程序（指令序列） |
| D | continuation：被挂起的执行上下文 |

- Agent 组成分离：Identity/Personality（静态） + AgentMemory（外部认知状态） + Perception（输入通道） + AgentRuntime（行为执行内核，内含 SECD）。
- **Memory ≠ E，ShortTermMemory ≠ S**：两者之间只通过 Effect 与指令原语交换数据，不做概念合并。
- 实现：P0 从 lambdaexpr 移植 SECD 核心到 `com.own.virtualaibox.secd`，状态与驱动分离（MachineState / SECD）。

## Alternatives

1. **"Agent = SECD 抽象机"**（原表述）——被拒：混淆执行内核与智能体实体，Agent 的人格/记忆/感知无处安放。
2. **每 tick LLM 全量决策**（vab1.0/vab2.0 现状）——被拒：非确定、慢、无法保证控制流。
3. **手写状态机**——被拒：SECD 已提供经过验证的 λ 归约语义与 D 栈 continuation，直接移植更符合实验目标。

## Rationale

- 需要"确定性运行时 + 非确定性 Oracle"的清晰分工：控制流由机器保证。
- SECD 的 D 栈天然表达"挂起/恢复"，是 Agent 事件中断能力的机械基础（见 ADR-0004）。
- 移植参考实现比新造轮子更可控、可回灌同步。

## Consequences

- 行为控制流完全由 SECD 决定；LLM 只在 `ask-llm` 处注入不确定性（ADR-0002）。
- 每个 Agent 独占一台 SECD 与求值器 → 无共享、无竞态，但内存与状态规模线性于 Agent 数。
- SECD 只计算、只产 Effect（ADR-0003），World 修改被隔离。
- 需要监控 D 栈增长（无限归约启发式，>50 帧告警）。
