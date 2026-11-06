# Architecture：VirtualAIBox 当前架构（SSOT）

> 状态：CURRENT。本文档是**当前架构的唯一权威总览（Single Source of Truth）**。
> 文档目录、维护规则和权威关系见 [`docs/README.md`](README.md)。
> 组件级契约见 `spec/`，设计决策见 `decisions/`，单次改动方案见 `tasks/`，历史演进见 `changelog.md`，历史设计见 `archive/`（只读）。
> 本文档只描述**当前真实存在**的系统；已废弃/计划中的内容不在此列。

## 1. 当前系统组成

```text
                          Virtual AI Box
                                │
                     WorldEngine / VirtualClock
                                │ step()
                     TickSchedule（Phase1-5 + Phase4.5）
    ┌──────────────┬──────────────┬───────────────┬────────────┐
 Phase1 start  Phase2 decision  Phase3 execute  Phase4 inter  Phase5 end
                                │ Phase4.5 convergence
                     MindController（决策阶段）
       ┌─────────────────────────┼─────────────────────────┐
       │        AgentRuntime（每 Agent 一台）      │
       │   ┌──────────────────────┐   │
       │   │ SECD  S E C D        │   │
       │   │ WorldOpEvaluator     │   │
       │   └──────────┬───────────┘   │
       └──────────────┼───────────────┘
                      │ 产出 List<Effect>（单向流）
              EffectExecutor / ActionExecutor
        ┌─────────────┼──────────────┬─────────────┐
        ↓             ↓              ↓
                     World       EventBus        AgentMemory
   (位置/世界/小镇运行态) (事件发布) (认知记忆)
```

辅助通道：

```text
LLM Oracle（LLMBrain）   ← 两个入口：decideAction 定 dx/dy（计划编译期）；ask-llm 原语
Perception（WorldPerception）← 每 tick 只读注入，值压入 S 栈，不产 Effect
ConvergenceMonitor       ← 每 tick 末尾观察世界与 D 栈，发布收敛/卡死/循环事件
```

## 2. 组件职责

| 组件 | 当前职责 | 代码位置 | 状态 |
|---|---|---|---|
| SECD | 确定性行为执行内核：单步推进 S/E/C/D，产出 Effect | `src/main/java/com/own/virtualaibox/secd/` | CURRENT |
| MachineState | 四寄存器可变状态 {S,E,C,D}，restore 整体换帧 | `secd/MachineState.java` | CURRENT |
| Instruction（7 种） | C 栈指令：Const/Var/Lam/App/Apply/Seq/IfThenElse | `secd/Inst*.java` | CURRENT |
| Value（11 种） | Int/String/Op/PartialOp/Closure/Var/AgentRef/Bool/Direction/Infinite/Void | `secd/value/` | CURRENT |
| ArithmeticOpEvaluator | 纯算术（add/mul/succ/sqr）+ 比较（eq/lt/gt/le/ge） | `secd/ArithmeticOpEvaluator.java` | CURRENT |
| AgentRuntime | 每 Agent 一台 SECD；tick 推进（≤500 步）；D 栈中断/恢复；DSL 加载 | `mind/AgentRuntime.java` | CURRENT（测试状态见 §6） |
| MindController | 决策阶段驱动全部运行时；相遇 onMeet 中断双方；运行时缓存 | `mind/MindController.java` | CURRENT |
| DefaultPlanCompiler | 内置默认计划（LLM 定方向 + 3 步路径）；内置 onMeet | `mind/DefaultPlanCompiler.java` | CURRENT |
| WorldOpEvaluator | 世界原语（move/speak/remember/ask-llm/感知/方向移动）；只产 Effect | `mind/WorldOpEvaluator.java` | CURRENT |
| Perception / WorldPerception | 只读世界视图；PERCEPTION_RANGE=5（曼哈顿）；同 tick 快照 | `mind/Perception.java`、`mind/WorldPerception.java` | CURRENT |
| Effect（4 种） | MoveEffect/SpeakEffect/RememberEffect/LLMRequestEffect | `effect/` | CURRENT |
| EffectExecutor | 副作用落地（唯一修改 World 的路径） | `executor/EffectExecutor.java` | CURRENT |
| ActionExecutor | 移动落地（边界钳制）+ agent.moved 事件 | `executor/ActionExecutor.java` | CURRENT |
| LLMBrain | LLM Oracle：decideAction（JSON 决策，失败降级随机）+ chat（ask-llm） | `brain/LLMBrain.java` | CURRENT |
| ConvergenceMonitor | stuck(3 tick)/loop(周期 2-8)/converged(5 tick)/d-stack(>50)；边沿触发 | `monitor/ConvergenceMonitor.java` | CURRENT |
| TickSchedule | 5 阶段 + 4.5 收敛检测；相遇去抖 | `core/TickSchedule.java` | CURRENT |
| VirtualClock / WorldEngine | 虚拟时钟；世界配置、居民生命周期和小镇运行态编排 | `core/` | CURRENT |
| EventBus | 事件订阅/发布/历史（上限 210）/优先级；当前为进程内历史 | `domain/event/EventBus.java` | CURRENT |
| Agent / AgentState / PersonalityProfile | 居民实体、稳定人格、EventListener 和记忆写入 | `domain/agent/` | CURRENT |
| AgentMemory / MemoryStore | 认知记忆（6 类条目，短期窗口 20 tick） | `domain/memory/` | CURRENT |
| BehaviorCompiler/Program/Registry | `.lambda` → Instruction；启动加载 agents/*.lambda | `behaviordsl/` | CURRENT（文档自标"草案"，已实现并有测试） |
| DashboardController | REST：/api/dashboard 聚合（含每 Agent mind/memory 字段与 convergence 快照）+ state/events/agents/metrics/step/addAgent | `controller/DashboardController.java` | CURRENT（接口契约见 `spec/api.md`） |

## 3. 核心协作（数据流摘要）

详细契约见各 `spec/` 文档，这里给权威摘要：

1. **Tick 流**：`WorldEngine.step()` → `VirtualClock.stepForward()` → `TickSchedule.processTick()` → Phase1 `tick.started` → Phase2 `decisionPhase` → Phase3 `executionPhase` → Phase4 相遇检测/onMeet → Phase4.5 收敛检测 → Phase5 `tick.ended` → `WorldEngine.simulateTownTick()` 资源再生与自动拾取。（见 `spec/agent-runtime.md` §1）
2. **Agent 行为执行流**：decisionPhase 逐个 Agent 调 `AgentRuntime.tick(worldState)`；计划耗尽则重编译（DSL 或默认计划）；每 tick 至多推进一步主动作（Move/Speak）即停；行为程序驻留 C 栈跨 tick 执行。（见 `spec/agent-runtime.md` §2）
3. **Perception 流**：tick 开头注入只读快照 → 感知原语压值进 S 栈（null→`#f`），不产 Effect。（见 `spec/perception.md`）
4. **Effect 执行流**：SECD 计算产出 Effect → MindController 汇总 → EffectExecutor 落地（Move→World+事件、Speak→事件、Remember→记忆、LLMRequest→仅日志）。（见 `spec/effect.md`）
5. **LLM Oracle 流**：仅两处——计划编译期 `decideAction`（定 dx/dy）；`ask-llm` 原语（返回值压 S）。LLM 不直接修改 World。（见 ADR-0002）
6. **Event 流**：EventBus.publish → 历史记录 + 全局/类型监听者 → Agent 写 eventHistory 与记忆。
7. **DSL → Plan → Runtime 流**：BehaviorRegistry 启动加载 `.lambda` → BehaviorCompiler 编译为 BehaviorProgram(plan/defs/onMeet) → AgentRuntime 以 E=defs+{dx,dy} 执行 plan。（见 `spec/behavior-dsl.md`）
8. **Town Runtime 流**：DashboardController 或 tick 收尾调用 WorldEngine 的领域命令 → World 更新资源/消息/关系/任务/货币 → EventBus 发布 `town.action` → Agent 事件监听和 dashboard snapshot 读取结果。

## 4. 核心不变量（均为 CURRENT，有代码/测试/历史 Spec 支撑）

| # | 不变量 | 证据 |
|---|---|---|
| 1 | **Effect 单向流**：SECD/求值器只产 Effect，绝不直接修改 World | `AgentRuntimeTest.worldOpEvaluatorProducesEffectsNotWorldMutation`；ADR-0003 |
| 2 | **LLM 不直接修改 World**：仅在计划编译期与 ask-llm 两处被调用 | `LLMBrain` 无 World 引用；ADR-0002 |
| 3 | **步数预算**：SECD.run ≤10_000 步；tick ≤500 步；含发散检测 | `SECD.DEFAULT_MAX_STEPS`、`AgentRuntime.MAX_STEPS_PER_TICK` |
| 4 | **D 栈中断/恢复**：interrupt 整体挂 {S,E,C,D}，runHandler 解帧恢复主计划 | `AgentRuntimeTest.interruptResumesMainPlanViaDStack`；ADR-0004 |
| 5 | **感知只读**：不产 Effect、不进 EffectExecutor | Perception 接口无副作用；`spec/perception.md` |
| 6 | **真值规则**：仅 `#f` 为假，`0`/`""`/AgentRef 皆真（Scheme 风格） | `SECDTest`（ifZeroIsTruthy 等）+ `BoolValue.isTruthy` |
| 7 | **null 不出机器边界**：感知层 Java null 在返回边界统一转 `#f` | `WorldOpEvaluator` closest/dist-to/direction-of 分支 |
| 8 | **同 tick 快照一致**：所有 Agent 在同一 tick 看到同一 WorldState | `AgentRuntime.tick` 注入时机 |
| 9 | **相遇去抖**：同 pair 首次相遇才触发 onMeet | `TickSchedule.activeMeetings` |
| 10 | **ITE 与 C 栈同构**：if 不产生新 D 帧、不残留条件值 | `SECDTest.ifConsumesCondNoStrayValue` |
| 11 | **move 单形态 Effect**：方向/整数移动统一 MoveEffect(dx,dy)，Effect 层无方向重载 | `BehaviorCompilerTest.moveUnifiedEffectContract` |
| 12 | **感知范围 = 5（曼哈顿）** | `WorldPerception.PERCEPTION_RANGE` |
| 13 | **收敛边沿触发**：条件首次成立发布一次，状态打破后清除才再发 | `ConvergenceMonitorTest.stuckAgentAtBoundaryFiresAgentStuck` |

## 5. 状态标注与已知不确定性

- **CURRENT**：上表所列全部组件（当前真实实现）。
- **DEPRECATED**：`ObserveEffect`（设计草案中的感知副作用，P6 起感知只读化后废弃，代码中不存在）；定位"每个 Agent 变成一台 SECD 抽象机"（改为"Agent 拥有 SECD 行为运行时"，见 ADR-0001）。
- **PLANNED**：`and/or/not` 原语、`observe` 原语、感知范围可配置（历史设计 P6.1）；社会关系图/反事实推演/行为进化/世界回放（历史设计 §16）。均未实现。
- **UNKNOWN**：见 §6。

## 6. 已知不确定性（不擅自推断）

### 6.1 测试状态
- **2026-10-24 / `.\mvnw.cmd test` / 9 个测试类 75 例全部通过**（SECDTest 28、BehaviorCompilerTest 16、WorldEngineTest 11、DashboardControllerTest 7、AgentRuntimeTest 4、ConvergenceMonitorTest 4、PersonalityProfileTest 3、JunitTest 1、VirtualAiBoxApplicationTests 1）。依据 `target/surefire-reports/` 9 份报告，Failures/Errors/Skipped 均为 0。
- 前端 `npm run build` 通过；Vite 仅报告 bundle 体积提示，不影响构建结果。
- 2026-08-22 的失败记录保留在 `consistency-audit.md`，作为历史审计背景，不再代表当前测试状态。

### 6.2 Agent 初始化
- `WorldEngine.init()` 创建默认居民；`WorldEngine.createAndAddAgent()` 统一负责身份、位置、记忆和 EventBus 注册；`DashboardController.addAgent()` 通过该入口创建居民。接口字段见 `spec/api.md`。

### 6.3 兼容层
- `EffectExecutor.executeMove` 会为每次移动发布 `agent.decided`（`llmDecision=true`），代码注释自述"兼容旧决策事件"。与"LLM 仅作 Oracle"定位存在语义张力，属已知设计关注点（见 `consistency-audit.md` §3.8），**保留现状，不处理**。

## 7. 文档索引

| 主题 | 权威文档 |
|---|---|
| 项目定位/边界 | `vision.md` |
| 当前架构总览（本文） | `architecture.md` |
| AgentRuntime / Tick / D 栈中断 | `spec/agent-runtime.md` |
| Behavior DSL | `spec/behavior-dsl.md` |
| Dashboard API | `spec/api.md` |
| Personality | `spec/personality.md` |
| Perception | `spec/perception.md` |
| Effect 层 | `spec/effect.md` |
| 设计决策 | `decisions/ADR-0001~0004` |
| 协作规范 / 知识地图 | `development/AI_WORKFLOW.md`、`development/KNOWLEDGE_MAP.md` |
| 任务记录与设计方案 | `tasks/` |
| 历史演进 | `changelog.md` |
| 审计记录 | `consistency-audit.md` |
| 历史设计（只读） | `archive/design-v0.3-frozen.md` |
