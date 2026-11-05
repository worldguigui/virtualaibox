# Vision：VirtualAIBox 项目定位

> 为什么做、是什么、边界限制，不含实现细节；文档入口见 `README.md`，实现见 `architecture.md` 与 `spec/`

## 一句话定位

> 这是一个以 **SECD 为行为执行内核、以 World/Event 为运行环境、以 Memory 为认知状态、以 Effect 为副作用边界、以 LLM 为非确定性 Oracle** 的多智能体沙盒。

## 为什么做这个项目

做这个项目的初衷是为了找工作，将自己的Java和AI Agent技术栈串联起来，基于web应用，探索世界中的多智能体编排。

我要做的的不仅仅是chatbot，这样一个简单的大模型对话应用，而是回答一个问题：

**LLM Agent 的行为执行系统，如何做到可解释、可控制、可扩展？**

围绕这个核心问题，项目把以下要素组合进一个可运行的实验系统，观察它们如何协作：

- LLM Agent
- Agent 行为执行（Behavior Execution）
- SECD 抽象机 / Agent Runtime
- Tick 驱动的虚拟世界
- Perception（感知）
- Effect / 副作用
- EventBus / Event
- Memory
- LLM Oracle
- Behavior DSL

核心主张：

- **确定性运行时 + 非确定性 Oracle**：行为控制流由 SECD 机器保证，LLM 只作为 Oracle 填补"不确定的洞"，而不是控制整个 Agent。

- **Interrupt → Handle → Resume**：Agent 拥有基于 D 栈的可中断、可恢复行为执行，而不是每个 tick 都让 LLM 猜测"我现在该干什么"。

## 项目是什么

- 一个多智能体、Tick 驱动的网格世界（37×37）。
- 每个 Agent 拥有一个基于 SECD 的行为运行时，执行可组合、可中断、可恢复的行为程序。
- 行为程序由 `.lambda`（Behavior DSL）定义，或由内置默认编译器生成。
- 世界副作用通过 Effect 单向流出，由 EffectExecutor 统一落地。
- 通过 dashboard（REST + React 前端）观察世界、Agent 心智（S/E/C/D）、记忆与收敛状态。

## 项目边界

- **不是聊天机器人产品**：LLM 只回答结构化的方向决策或 `ask-llm` 原语，不承担行为编排。
- **不是完整编程语言**：Behavior DSL 是"最小原语集 + 组合"的草稿级语言（见 `spec/behavior-dsl.md`），语法可调整。
- **不是严格 λ 演算理论**：SECD 语义保留原始机械含义（S/E/C/D = 值栈/环境/控制/续体），"交互 = 闭包应用"、"世界 = 归约"只是工程隐喻，不当作严格理论（见 ADR-0001、ADR-0004 与 `archive/design-v0.3-frozen.md` §8-9）。
- **不做**：社会关系图、反事实推演、行为进化、世界回放等扩展（历史设计中列为后续方向，见 `archive/design-v0.3-frozen.md` §16，当前为 PLANNED，未实现）。

## 本项目的交付物边界

- 文档体系：`README.md`（维护入口）→ `vision.md`（项目定位）→ `architecture.md`（当前架构 SSOT）→ `logic-walkthrough.md`（执行链路）→ `spec/`（系统契约）→ `decisions/`（架构决策）→ `development/`（协作规范与知识地图）→ `tasks/`（任务记录与设计方案）→ `changelog.md`（历史演进）→ `archive/`（历史文档，只读）。
- 当前代码问题（未完成接口、待处理项）不在本文档处理范围，记录于 `consistency-audit.md`（问题台账）。
