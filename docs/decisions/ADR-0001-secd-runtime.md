# ADR-0001: SECD 作为 Agent 行为执行内核

- 状态：Accepted
- 日期：2026-09-10

## 决策背景

- 项目前身 vab1.0/vab2.0 采用“每个 tick 全量状态提交 LLM 决策”，存在：决策慢（必须等待大模型响应）、不可解释、不可恢复的问题。
- 全量世界状态塞给大模型，解析JSON，得到工作并执行，这整个过程是动作是一个整体，无法组合、打断。
- 同时存在一套与项目无关的 λ 演算参考实现：[基于antlr4的lambda表达式识别与归约](https://gitee.com/worldguigui/antlr4.git)，含 SECD 机器与值模型，可作为语义参考移植。
- 貌似可以将 SECD抽象机 的核心功能迁移到本项目中？可以！

## 决策选择

Q. 我们到底在决策什么？<br/>
A. 原先的设计中，大模型思考并行动，我们现在要将思考和行为拆开，将行为交由行动内核SECD机来执行。

Q. 手写状态机，以及状态机的状态跳转来规范大模型的行为？<br/>
A. 手动从零造轮子并不可取，而且SECD 已提供经过验证的 λ 归约语义与 D 栈 continuation，直接移植更符合实验目标。

Q. Agent = SECD 抽象机？S栈 就是 Agent 的人格，E栈 就是感知，C栈 就是记忆，一一对应，岂不妙哉？<br/>
A. S/E/C/D 四个寄存器只有**计算语义**，无法与Agent 的人格/记忆/感知一一对应。SECD机 应该对应于 Agent 的行为内核，人格/记忆/感知由 Runtime 外层持有，经指令原语与 Effect 与内核交换数据。

Q. 完全迁移？原 SECD 机的执行逻辑，项目结构等完美照搬？<br/>
A. 参考实现的 SECD机 用于教材效果演示，SECDMachine类内部持有S/E/C/D四个栈，每次运行直接解析并归约 lambda表达式。这对于演示来讲很好，因为我们输入一个表达式，直接运行，就可以得到最终结果。但在当前项目中，我们只需要 SECD机 作为行动内核，而不需要配套的外围设施，所以最好将其抽离成无状态的机器，只保留其核心的运算逻辑。

## 最终决策

- **每个 Agent 拥有一个基于 SECD机 的行为运行时（Agent Runtime），负责执行可组合、可中断、可恢复的行为程序。**
- SECD机 是 Agent 的**行为执行内核**，不是 Agent 本身。
- S/E/C/D 保持 SECD机 原始机械语义，不与记忆/人格强行对应：

| 寄存器 | 职责 |
|---|---|
| S | 当前计算过程中的值栈 |
| E | 当前程序的词法环境 / 闭包环境 |
| C | 当前正在执行的行为程序（指令序列） |
| D | continuation：被挂起的执行上下文 |

- Agent 组成分离：Identity/Personality（静态） + AgentMemory（外部认知状态） + Perception（输入通道） + AgentRuntime（行为执行内核，内含 SECD机）。
- **Memory ≠ E，ShortTermMemory ≠ S**：两者之间只通过 Effect 与指令原语交换数据，不做概念合并。

## 移植边界

**原样移植**：call-by-value（rator 先于 rand）；闭包浅拷贝捕获 E；`InstVar` 未绑定压回 `VarValue`；二元原语柯里化（`PartialOpValue`）；D 栈挂起/恢复语义；发散检测思想（状态指纹回环 + 步数上限）；纯算术原语全集（sqr/succ/pred/zerop + add/mul/sub/div）。

**结构性改造**（机械语义不变，架构形态改变）：

| 参考实现 | 本项目 | 理由 |
|---|---|---|
| S/E/C/D 为机器私有字段，`run()` 开头整体重置 | `MachineState`（每 Agent 一份，跨 tick 驻留）+ `SECD`（无状态驱动） | 一次性求值器 → 可驻留、可恢复 |
| δ 归约硬编码在 `Arithmetic` 静态类 | `OpEvaluator` 接口（纯算术 / 世界原语两个实现） | 世界原语可注入而不污染纯算术 |
| 纯计算，无副作用概念 | 机器只返回 `Effect` 列表 | World 修改隔离（[ADR-0003](ADR-0003-effect-isolation.md)） |
| `DumpItem` 链式帧，挂起 O(1) | `DumpFrame` 入栈，挂起时浅克隆 S/C/D | 每帧独立可持有/检查/可视化 |
| 发散抛 `DivergenceException` 终止 | `Reduction(infiniteReduction=true)` 软失败，预算用尽先判发散、非发散重置预算继续 | 长驻运行时不受单次预算限制 |
| 细分异常直接冒泡 | 单一 `SecdEvalException`，AgentRuntime 只降级本 Agent | 单 Agent 失败不拖垮整个 tick |
| `setTrace(PrintStream)` 直打 | `setTrace(Consumer<String>)` 观察者，null 零开销 | 接 DEBUG 日志 / MindViewer |
| if 两阶段（`InstIf`+`InstIfBranch`），条件 Int 0/1 | 单条 `InstIfThenElse` | 与 InstSeq/C 栈同构，无额外 D 帧 |

**领域扩展**（参考实现不存在）：指令 `InstSeq`（行为编排，程序跨 tick 驻留 C 栈）；值 BoolValue/StringValue/VoidValue/AgentRefValue/DirectionValue/InfiniteValue；世界原语 move/speak/remember/ask-llm → Effect，其中 ask-llm 是机器内唯一非确定性注入点（ADR-0002）；`AgentRuntime.interrupt/runHandler` 主计划挂 D 栈的中断恢复（ADR-0004）。

**有意分歧**（经确认保留，勿当 bug 修）：`zerop` 与比较原语返回 `BoolValue`（lambdaexpr 返回 Int 0/1）；真值规则仅 `#f` 为假（lambdaexpr 为 0 为假）——动机：让 `if` 可直接组合“无对象→`#f`”的感知原语。

**未移植**：`NormalOrderReducer` 及 `Alpha`/`FreeVars`/`Substitution`（范式归约）——Agent 行为只需应用序；Cli/Printer 命令行前端——由 `BehaviorDSL.g4` + `BehaviorCompiler` 取代（ANTLR4 前端路线本身继承）。

## 最终实现

- 从 lambdaexpr 移植 SECD 核心到 `com.own.virtualaibox.secd`，状态与驱动分离：`MachineState`（每 Agent 一份，跨 tick 驻留）+ `SECD`（无状态驱动器）。参考实现的 `SECDMachine` 把 S/E/C/D 作为私有字段、`run()` 开头整体重置，只能一次性求值；分离后机器才能“从当前状态继续”，这是“可恢复”的前提。
- 决策声明中三个形容词的机械对应物：
    - **可组合** —— 新增 `InstSeq` 指令：行为程序 = 一组动作的编排，整体驻留 C 栈（参考实现无此指令）；
    - **可中断** —— 复用 D 栈 continuation：`AgentRuntime.interrupt` 把主计划 {S,E,C,D} 快照挂入 D 栈、加载 handler，与闭包应用（教材规则 6）完全同构；
    - **可恢复** —— handler 求值完毕后从 D 弹帧整体换回，主计划从被中断的精确指令处继续（`runHandler`，以 D 栈清空为结束条件）。
- 原语经 `OpEvaluator` 接口注入：`ArithmeticOpEvaluator`（默认纯算术）/ `WorldOpEvaluator`（move/speak/remember/ask-llm → Effect）。

## 决策后果

- 行为控制流完全由 SECD机 决定；LLM 仅经 [ADR-0002](ADR-0002-llm-oracle.md) 定义的入口接入：计划编译期 `decideAction`、机器内 `ask-llm`。
- 每个 Agent 独占一台 SECD机 与求值器 → 无共享、无竞态，但内存与状态规模线性于 Agent 数。
- SECD机 只计算、只产 Effect（详见[ADR-0003](ADR-0003-effect-isolation.md)），World 修改被隔离。
- 需要监控 D 栈增长：深度超过 [virtual-ai-box.runtime.convergence.max-d-stack-depth](../../src/main/resources/application.yml)（默认 50 帧）即视为疑似无限归约/深递归，触发告警（ConvergenceMonitor 消费该阈值）。

