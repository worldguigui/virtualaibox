# KNOWLEDGE_MAP：个人知识地图

> 状态：CURRENT。本文记录**本人**对 VirtualAIBox 核心机制的理解程度，不是项目说明文档。
> **勾选权归你。** Agent 只能提出更新建议，不得自行把任何条目改为"已理解"。
> 判断标准不是"读过代码"，而是能否回答四个问题：它为什么存在？如何工作？和谁交互？出问题从哪里查？

## 状态说明

| 状态 | 含义 |
|---|---|
| 未学习 | 还没看过，或只知其名 |
| 正在理解 | 读过代码/文档，但还不能独立解释 |
| 已理解 | 能脱稿讲清四个问题，并能定位到代码 |
| 需重新确认 | 曾经理解，但代码已变化或记忆模糊（由你标记） |

除特别注明外，所有条目初始状态均为**未学习**。依据列给出三处入口：文档 / 代码 / 测试，用于补齐。

## 1. World Engine

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| World 的职责与状态模型（配置映射 + 运行态集合） | 未学习 | `logic-walkthrough.md` §2.1 / `domain/world/World.java` / `WorldEngineTest` |
| WorldConfig 的绑定与校验（含 `defaultConfig()`） | 未学习 | `logic-walkthrough.md` §2.1 / `domain/world/WorldConfig.java` / `WorldEngineTest` |
| VirtualClock 与 tick 推进 | 未学习 | `spec/agent-runtime.md` §1 / `core/VirtualClock.java` / `WorldEngineTest` |
| TickSchedule 五阶段顺序（Phase1-5 + 4.5） | 未学习 | `spec/agent-runtime.md` §1 / `core/TickSchedule.java` / `WorldEngineTest` |
| **时序边界**：`simulateTownTick()` 在 `tick.ended` **之后**执行，不在五阶段内 | 未学习 | `logic-walkthrough.md` §3.2 / `core/WorldEngine.java` / — |
| 多 Agent 同 tick 调度与"同 tick 快照一致" | 未学习 | `architecture.md` 不变量 8 / `mind/MindController.java` / `AgentRuntimeTest` |
| **Tick 异常处理**：整体 try/catch，仅 `log.error`，已落地 Effect 不回滚 | 未学习 | — / `core/TickSchedule.java:67-91` / — |
| WorldState 只读快照与可变 World 的隔离 | 未学习 | `logic-walkthrough.md` §2.4 / `domain/world/WorldState.java` / — |

## 2. Agent Runtime

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| AgentRuntime 职责：每 Agent 一台 SECD，独立 MachineState | 未学习 | `spec/agent-runtime.md` §2.1 / `mind/AgentRuntime.java` / `AgentRuntimeTest` |
| `tick(worldState)` 四步语义与 500 步预算 | 未学习 | `spec/agent-runtime.md` §2.2 / `AgentRuntime.java` / `AgentRuntimeTest` |
| "一步主动作即停"：一个 tick 至多一次 Move/Speak | 未学习 | `spec/agent-runtime.md` §2.2 / `AgentRuntime.tick` / `AgentRuntimeTest.firstTickProducesRememberThenMove` |
| 计划跨 tick 驻留 C 栈；`PATH_LENGTH=3` 决定 Oracle 咨询频率 | 未学习 | `spec/agent-runtime.md` §2.2 / `mind/DefaultPlanCompiler.java` / `AgentRuntimeTest.planPersistsAcrossTicksAndReloads` |
| Perception 注入时机与只读性 | 未学习 | `spec/perception.md` §4 / `mind/WorldPerception.java` / `AgentRuntimeTest` |
| **Reasoning 的真实形态**：无独立 Reasoning 组件，等于「计划编译 + Oracle 定向」 | 未学习 | `spec/agent-runtime.md` §3 / `DefaultPlanCompiler` + `brain/LLMBrain.decideAction` / `AgentRuntimeTest` |
| 中断/恢复协议：interrupt / runHandler / stepWithResume | 未学习 | `spec/agent-runtime.md` §2.3、ADR-0004 / `AgentRuntime.java` / `AgentRuntimeTest.interruptResumesMainPlanViaDStack` |
| idle / executing / suspended 三态与 `mindSummary()` | 未学习 | `spec/agent-runtime.md` §2.4 / `AgentRuntime.mindSummary` / — |
| MindController 驱动决策阶段 + 相遇时对双方中断 | 未学习 | `spec/agent-runtime.md` §4 / `mind/MindController.java` / `AgentRuntimeTest` |

## 3. Event System

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| **DomainEvent 元数据模型**（注意：代码中**没有** `EventDomain` 抽象） | 未学习 | `logic-walkthrough.md` §2.4 / `domain/event/DomainEvent.java` / — |
| EventBus 订阅 / 发布 / 历史（上限 210）/ 优先级 | 未学习 | `architecture.md` §2 / `domain/event/EventBus.java` / — |
| 同步与异步发布两条路径（`async` 开关） | 未学习 | — / `EventBus.publish` / — |
| **监听器异常隔离**：catch + `log.error`，不传播、不重试、不影响其他监听器 | 未学习 | — / `EventBus.java:142-160` / — |
| 事件类型全集 10 个（tick 2 / agent 5 / town 1 / 收敛 3，含复用 `agent.loop`） | 未学习 | `logic-walkthrough.md` §2.4 / `domain/event/events/` / — |
| Agent 作为 EventListener：eventHistory 与记忆写入 | 未学习 | `logic-walkthrough.md` §2.2 / `domain/agent/Agent.onEvent` / — |

## 4. Execution Engine（SECD）

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| SECD 基本原理与 S/E/C/D 四寄存器语义 | 未学习 | `architecture.md` §1、ADR-0001 / `secd/SECD.java`、`MachineState.java` / `SECDTest` |
| 指令集 7 种：Const/Var/Lam/App/Apply/Seq/IfThenElse | 未学习 | `architecture.md` §2 / `secd/Inst*.java` / `SECDTest` |
| Value 类型 11 种（含 `VarValue`、`InfiniteValue`） | 未学习 | `architecture.md` §2 / `secd/value/` / `SECDTest` |
| 求值器分工：ArithmeticOpEvaluator（纯计算）vs WorldOpEvaluator（世界原语） | 未学习 | `spec/effect.md` §3 / 两个 Evaluator / `SECDTest` |
| if 与 C 栈同构：不产生 D 帧、不残留条件值 | 未学习 | `spec/behavior-dsl.md` §5 / `InstIfThenElse.java` / `SECDTest.ifConsumesCondNoStrayValue` |
| 真值规则：仅 `#f` 为假（Scheme 风格） | 未学习 | `spec/perception.md` §6 / `BoolValue.isTruthy` / `SECDTest` |
| **DSL → 引擎完整调用链**：Registry → Compiler → `compileFromProgram` | 未学习 | `spec/behavior-dsl.md` §6-7 / `behaviordsl/` / `BehaviorCompilerTest` |
| 步数预算与发散检测（run 10,000 / tick 500） | 未学习 | `spec/agent-runtime.md` §5 / `SECD.run` / `SECDTest` |
| move 单形态契约：方向与整数统一为 `MoveEffect(dx,dy)` | 未学习 | `spec/effect.md` §4 / `WorldOpEvaluator` / `BehaviorCompilerTest.moveUnifiedEffectContract` |

## 5. Memory System

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| MemoryEntry 数据结构（身份/类型/内容/标签/重要度/tick/访问信息） | 未学习 | `logic-walkthrough.md` §2.2 / `domain/memory/MemoryEntry.java` / — |
| 写入路径：5 个 `record*` 方法及其重要度约定 | 未学习 | `logic-walkthrough.md` §2.2 / `AgentMemory.java:33-114` / — |
| **短期 vs 长期是两种查询口径**：最近 20 tick vs importance TopN | 未学习 | — / `AgentMemory.getShortTermMemory/getLongTermMemory` / — |
| `summarizeMemoriesForLLM` 如何生成提示词上下文 | 未学习 | `spec/personality.md` §3 / `AgentMemory.java:170` / — |
| InMemoryMemoryStore：线程安全但重启丢失 | 未学习 | `logic-walkthrough.md` §3.1 / `InMemoryMemoryStore.java` / — |

## 6. Town Runtime 与看板

| 知识点 | 状态 | 依据（文档 / 代码 / 测试） |
|---|---|---|
| Town 领域命令统一路径：Controller → WorldEngine → World + EventBus → snapshot | 未学习 | `logic-walkthrough.md` §1.3 / `core/WorldEngine.java` / `DashboardControllerTest` |
| 资源再生与自动拾取的时序位置 | 未学习 | `logic-walkthrough.md` §3.2 / `WorldEngine.simulateTownTick` / — |
| SocialRelation / TownMessage / TownQuest 的数据模型与生命周期 | 未学习 | `logic-walkthrough.md` §2.4 / `domain/world/` / — |
| **交易与战斗的当前边界**：交易只结算货币、战斗只是审计动作 | 未学习 | `logic-walkthrough.md` §3.4-3.5 / `WorldEngine` town 方法 / — |
| DashboardController 的 20 个端点分 4 组（看板查询 / 居民 / 世界配置 / town 命令） | 未学习 | `spec/api.md`（仅覆盖 7 个）、`README.md` API 表（最全）/ `controller/DashboardController.java` / `DashboardControllerTest` |
| 前后端字段一致性要求 | 未学习 | `spec/api.md` §6 / `frontend/src/api/dashboard.ts` / — |

## 7. 已确认未实现（Planned，不计入掌握要求）

以下为历史设计或代码中的空缺，**当前不要求掌握**，实现后再转入上表：

- 持久化与回放：World / EventBus / 心智运行时 / town 运行态重启全部丢失（`logic-walkthrough.md` §3.1、§3.7）
- 记忆闭环：无自动遗忘、无长期记忆晋升、无压缩任务；重要度与访问阈值字段未生效（`logic-walkthrough.md` §3.8）
- DSL 原语 `and/or/not`、`observe`、感知范围可配置（`spec/behavior-dsl.md` §3、`spec/perception.md` §8）
- 社会关系图、反事实推演、行为进化、世界回放（`architecture.md` §5、`vision.md` 边界）
- 战斗结算（生命值/伤害/死亡）、交易物品转移（`logic-walkthrough.md` §3.4-3.5）
- `WorldRulePhase`：把资源收尾逻辑正式纳入 tick 阶段（`logic-walkthrough.md` §3.2）

---

## 维护约定

- 学完一个知识点后，你把状态改为「正在理解」或「已理解」；同一行可附一句自己的话说明"卡在哪"。
- Agent 在任务交付时（`AI_WORKFLOW.md` 阶段七第 6 项）可以建议"本次改动涉及哪些知识点、建议你关注哪几行"，但**不勾选**。
- 代码发生结构性变化时，Agent 应提示"某条目依据已失效，建议改回需重新确认"，由你决定。
