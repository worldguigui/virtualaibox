# VirtualAIBox 逻辑演习与代码职责审计

> 状态：CURRENT。本文基于当前源码整理，目标是让一次小镇运行可以从启动一路追踪到仪表盘。
> 这里的“持久化”暂未实现：World、EventBus 和 InMemoryMemoryStore 都是进程内状态。

## 1. 一次完整运行的文字模拟

### 1.1 启动阶段

1. Spring 启动 `VirtualAiBoxApplication`，注册 `VirtualClockConfig`、`VirtualClock`、`EventBus`、`MemoryStore`、`MindController`、`TickSchedule` 和 `WorldEngine`。
2. `WorldConfig` 从 `application.yml` 绑定世界名称、世界描述、尺寸、规则、资源定义和默认居民。
3. `WorldEngine` 校验配置并创建 `World`。此时 World 拥有世界配置和空的运行态集合：居民、资源实例、关系、消息、任务。
4. `@PostConstruct WorldEngine.init()` 按默认居民配置调用 `createAndAddAgent()`。
5. 每个居民获得稳定 id、位置、人格、`AgentMemory` 和 EventBus 全局订阅。默认居民初始化完成后，事件总线会把后续领域事件回送给所有活跃居民。

### 1.2 手动或自动推进一个 tick

```text
Dashboard /step 或自动运行
  -> useDashboard.stepTick()
  -> GET /api/dashboard/step
  -> WorldEngine.step()
  -> VirtualClock.stepForward()
  -> TickSchedule.processTick(tick, world)
```

1. `TickSchedule` 发布 `tick.started`。
2. `MindController.decisionPhase()` 为当前世界创建同一份 `WorldState` 快照。
3. 每个 `AgentRuntime` 注入 `WorldPerception`，从 C 栈继续执行旧计划；计划耗尽时调用 `LLMBrain.decideAction()`，再由 DSL 或默认计划编译成 SECD 指令。
4. SECD 最多执行当前 tick 预算，产生 `MoveEffect`、`SpeakEffect`、`RememberEffect` 或 `LLMRequestEffect`，不会直接修改 World。
5. `EffectExecutor` 执行副作用：移动交给 `ActionExecutor`，说话发布 `AgentSpokeEvent`，记忆写入 `AgentMemory`，LLM 请求只记录日志。
6. `TickSchedule` 检测居民相遇。首次相遇才运行双方 onMeet 中断程序；生成的对话/记忆副作用在当前 tick 再次经过 `EffectExecutor`。
7. `ConvergenceMonitor` 检查停滞、循环、收敛和 D 栈增长。
8. `TickSchedule` 发布 `tick.ended`。
9. `WorldEngine.simulateTownTick()` 在调度阶段之后执行资源再生和自动拾取，并发布 `TownActionEvent`。因此资源事件属于 tick 结束事件之后的运行时收尾阶段，前端下一次刷新才能看到。
10. Controller 返回世界、居民、事件、记忆、心智摘要和收敛状态；React Query 失效并重新拉取 dashboard。

### 1.3 一次仪表盘小镇操作

以“创建任务”为例：

```text
RuntimePage
  -> TownActionsPanel
  -> createTownQuest()
  -> POST /api/dashboard/town/quests
  -> DashboardController.createQuest()
  -> WorldEngine.createQuest()
  -> World.quests.add()
  -> EventBus.publish(TownActionEvent)
  -> 所有活跃 Agent 记录事件历史/必要记忆
  -> 返回 runtimeSnapshot()
  -> 前端 refresh()
```

资源生成、消息、关系、拾取、交易和战斗遵循同一条“Controller -> WorldEngine -> World + EventBus -> snapshot”路径。

## 2. 类与方法职责覆盖

### 2.1 应用、配置和核心循环

| 类 | 方法/职责 |
|---|---|
| `VirtualAiBoxApplication` | Spring Boot 入口；不承载模拟逻辑。 |
| `VirtualClockConfig` | 创建虚拟时钟相关 Bean。 |
| `VirtualClockProperties` | 绑定 tick、间隔和元时间配置。 |
| `VirtualClock` | `stepForward()` 推进 tick；`getTick()` 读取当前 tick。 |
| `WorldConfig` | 绑定并校验世界描述、尺寸、规则、资源定义和默认居民；`defaultConfig()` 提供测试/无配置时默认值。 |
| `WorldEngine` | `init()` 初始化居民；`step()` 编排 tick；`createAndAddAgent()`/`updateAgent()` 管理居民生命周期；`updateWorldConfig()` 原子应用世界配置；`simulateTownTick()` 执行资源再生/自动拾取；其余 town 方法是小镇命令的领域入口。 |
| `TickSchedule` | 按 start、decision、execution、interaction、convergence、end 顺序调度一个 tick；维护相遇去抖。 |
| `ConvergenceMonitor` | 观察居民位置、运行时和世界状态，发布停滞、循环、收敛及 D 栈异常事件。 |

### 2.2 居民、人格和记忆

| 类 | 方法/职责 |
|---|---|
| `Agent` | 居民实体；`onEvent()` 记录事件历史并将移动/相遇事件转成记忆；保存人格、钱币和背包。 |
| `AgentState` | 居民位置和基础状态。 |
| `PersonalityProfile` | `defaultFor()` 生成稳定默认人格；`normalizeFor()` 合并输入并保持人格字段完整；`toPromptContext()` 生成提示词上下文。 |
| `AgentMemory` | `recordObservation()`、`recordEvent()`、`recordRelationship()`、`recordLocation()`、`recordLearning()` 写入记忆；`getShortTermMemory()`/`getLongTermMemory()` 检索；`summarizeMemoriesForLLM()` 生成提示词摘要。 |
| `MemoryEntry` | 单条记忆的身份、类型、内容、标签、重要度、tick 和访问信息。 |
| `MemoryStore` | 记忆存储抽象契约。 |
| `InMemoryMemoryStore` | 当前实现；线程安全但重启丢失数据，不是正式持久化。 |

### 2.3 心智、LLM 和 SECD

| 类 | 方法/职责 |
|---|---|
| `MindController` | `decisionPhase()` 创建同 tick 快照并驱动全部 `AgentRuntime`；`onMeet()` 对双方运行中断处理；`getRuntime()` 提供调试查询。 |
| `AgentRuntime` | `tick()` 推进一个居民的 S/E/C/D；`interrupt()` 挂起主计划；`runHandler()` 执行 onMeet 并恢复主计划；`mindSummary()` 输出调试摘要。 |
| `DefaultPlanCompiler` | 无 DSL 行为时生成默认移动/记忆计划和 onMeet 计划。 |
| `WorldPerception` | 读取同一 tick 的 `WorldState`；只提供感知，不修改 World。 |
| `WorldOpEvaluator` | 将 move、speak、remember、ask-llm 和感知原语转换为 Value 或 Effect。 |
| `LLMBrain` | `decideAction()` 生成移动方向 JSON 并失败降级；`chat()` 为 ask-llm 提供文本 Oracle；提示词包含世界描述、规则、资源、人格和记忆。 |
| `SECD` | `step()` 执行一条指令；`run()` 处理 S/E/C/D、闭包恢复、Effect 汇总和发散保护。 |
| `MachineState` | 保存四寄存器状态及快照/恢复。 |
| `Instruction` 及 `Inst*` | SECD 指令值。 |
| `Value` 及 `*Value` | SECD 运行时值，包括整数、字符串、闭包、Agent 引用、方向和发散值。 |
| `ArithmeticOpEvaluator` | 纯算术、比较和部分应用。 |
| `BehaviorCompiler` | 将 `.lambda` 行为 DSL 编译为 `BehaviorProgram`。 |
| `BehaviorRegistry` | 启动加载和按居民解析行为程序。 |
| `BehaviorProgram` | 保存 plan、defs 和 onMeet 编译结果。 |

### 2.4 Effect、事件和世界运行态

| 类 | 方法/职责 |
|---|---|
| `Effect` | SECD 到世界的单向意图边界。 |
| `MoveEffect`/`SpeakEffect`/`RememberEffect`/`LLMRequestEffect` | 四种现有副作用载体。 |
| `EffectExecutor` | 唯一的 Effect 落地入口；调用 `ActionExecutor`、发布说话事件或写记忆。 |
| `ActionExecutor` | 校验边界并落地移动，发布 `AgentMovedEvent`。 |
| `DomainEvent` | 事件公共元数据。 |
| `EventBus` | 同步/异步发布、订阅、历史记录和全局监听。历史上限为 210，当前为内存态。 |
| `AgentMovedEvent`/`AgentMetEvent`/`AgentSpokeEvent`/`AgentDecidedEvent` | 居民移动、相遇、发言和决定事件。 |
| `TickStartedEvent`/`TickEndedEvent` | tick 生命周期事件。 |
| `AgentStuckEvent`/`AgentLoopEvent`/`WorldConvergedEvent` | 收敛监控事件。 |
| `TownActionEvent` | 资源、消息、关系、任务、交易和战斗命令的统一审计事件。 |
| `World` | 保存配置映射后的世界边界、规则、资源定义及运行态居民/资源/关系/消息/任务集合。 |
| `WorldState` | 给心智层的只读快照，不应直接暴露可变 World 集合。 |
| `WorldItemDefinition` | 可生成资源的静态定义。 |
| `WorldItem` | 地图上的资源实例；拥有位置、数量和 owner。 |
| `SocialRelation` | 居民之间的信任、好感和互动计数。 |
| `TownMessage` | 居民消息记录。 |
| `TownQuest` | 任务生命周期：open -> assigned -> completed。 |

### 2.5 REST 与前端

| 类/模块 | 方法/职责 |
|---|---|
| `DashboardController` | 聚合 dashboard/state/events/agents/metrics；提供居民 CRUD、世界配置和 town runtime 命令。 |
| `AgentCreateRequest`/`AgentUpdateRequest` | 居民输入契约，更新路径使用 `agentId`。 |
| `frontend/src/api/dashboard.ts` | REST 请求封装。 |
| `useDashboard` | Query 缓存、刷新、手动 tick、自动运行和配置/居民更新。 |
| `App` | 持有跨页面模拟控制和筛选状态。 |
| `OverviewPage` | 总览地图、居民、心智和事件。 |
| `WorldPage` | 地图与世界配置编辑。 |
| `AgentsPage` | 居民创建、编辑、人格和记忆。 |
| `EventsPage` | 事件时间线和详情。 |
| `RuntimePage` | SECD 诊断和 TownActionsPanel。 |
| `WorldMap` | 绘制网格、居民和未拾取资源点。 |
| `WorldConfigPanel` | 编辑世界描述、尺寸、规则和资源定义。 |
| `TownActionsPanel` | 发送消息、创建任务、生成资源。 |
| `AgentList`/`MindViewer`/`EventList`/`StatusPanel` | 分别展示居民、心智、事件和运行指标。 |

## 3. 当前发现的逻辑边界

1. **持久化未实现**：`MemoryStore` 当前注入 `InMemoryMemoryStore`；World、EventBus、心智运行时和 town 运行态重启都会丢失。
2. **资源收尾时序**：资源再生和自动拾取发生在 `TickSchedule` 的 `tick.ended` 之后，不属于调度器 Phase1-5 内部；后续应将其正式拆成 `WorldRulePhase` 并纳入 tick 事件统计。
3. **任务完成条件较弱**：当前可手动 complete，尚未根据资源、位置或行为自动验收。
4. **战斗只是审计动作**：当前没有生命值、伤害结算、死亡或恢复状态。
5. **交易只结算货币**：尚未绑定具体物品转移。
6. **世界快照是浅结构**：`WorldState` 当前主要给心智层居民位置、规则和资源定义，资源实例/任务/消息未进入感知原语。
7. **事件历史是环形内存队列**：无法支持进程重启后的审计、回放和恢复。
8. **记忆阈值尚未生效**：`AgentMemory` 声明了重要度和访问次数阈值，但当前没有自动遗忘、长期记忆晋升或压缩任务；这些字段不能在文档中描述为已完成的记忆闭环。

这些不是注释可以掩盖的功能缺口，应分别进入 P2 持久化、P3 调试和 P4 玩法增强任务。

## 4. 维护规则

- 修改 tick 顺序时必须同步更新本文第 1 节、`docs/architecture.md` 和 `docs/spec/agent-runtime.md`。
- 新增公共类/方法必须说明：输入、状态变化、事件副作用、失败条件和线程/持久化边界。
- 只读感知不能直接拿到可变 `World`；所有世界修改必须经过 EffectExecutor 或 WorldEngine 的领域命令入口。
- 文档描述“CURRENT”前必须以源码和静态检查为依据，计划内容放入“PLANNED”。
