# Spec：Agent Runtime

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 规定一次 tick 内"谁在什么阶段做什么"，使世界推进的顺序与副作用时机可预期。
- 规定单个 Agent 的执行契约：预算、节奏、状态与失败边界。
- 规定事件打断主计划时的中断与恢复协议。
- 规定 SECD 内核与 LLM Oracle 的对外契约，作为 ADR-0001/0002/0004/0006 的实现落点。

## 功能范围

### 包含范围

1. TickSchedule 的五阶段顺序与各阶段职责。
2. AgentRuntime 的生命周期、单 tick 推进节奏与对外状态机。
3. D 栈中断/恢复协议（Interrupt → Handle → Resume）。
4. SECD 内核契约：寄存器、指令集、值类型，以及与参考实现的移植对照。
5. LLM Oracle 的两个入口及其失败语义。
6. 默认计划编译器与内置 onMeet。
7. 运行时配置键清单。

不包含：Effect 如何落地（见 [effect.md](effect.md)）、DSL 语法与编译（见 [behavior-dsl.md](behavior-dsl.md)）、感知查询语义（见 [perception.md](perception.md)）。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| worldState | WorldState | 本 tick 的世界快照，同 tick 内所有 Agent 共享同一份 |
| agent | Agent | 居民实体，提供身份、人格与记忆引用 |
| behaviorProgram | BehaviorProgram（可空） | DSL 编译产物；为空时回退内置默认计划 |
| runtime 配置 | RuntimeProperties | 预算、半径、计划长度等，主源为 `application.yml` |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| effects | `List<Effect>` | 本 tick 产生的副作用描述，**未落地** |
| mindSummary | 结构体 | 四寄存器摘要与状态（idle / executing / suspended），供看板展示 |
| trace | 文本流（可选） | 每步一行，由 `secd-trace-enabled` 开关控制 |

## 核心行为

### 一次 tick 的阶段顺序

`WorldEngine.step()` 推进一个 tick：

```text
WorldEngine.step()
  → VirtualClock.stepForward()        # tick+1
  → TickSchedule.processTick(tick, world)
      Phase1   发布 tick.started
      Phase2   decisionPhase：MindController 推进所有 Agent 心智，汇总 List<Effect>
      Phase3   executionPhase：EffectExecutor.execute(effects, world) 落地副作用
      Phase4   interactionPhase：检测相遇 → agent.met → 首次相遇触发 onMeet
      Phase4.5 convergencePhase：ConvergenceMonitor.monitor(tick, world)
      Phase5   发布 tick.ended（统计本 tick 事件数）
```

- **相遇判定**：欧氏距离 ≤ `meet-distance` 视为相遇；`agent.met` 每次相遇都发布。
- **相遇去抖**：同 pair（排序键 `id1|id2`）**首次相遇**才触发 onMeet，避免每 tick 重复打招呼。
- **onMeet 副作用**：对话产生的 SpeakEffect / RememberEffect 在本 tick 立即落地（Phase4 内调 EffectExecutor）。
- **收敛检测时机**：Phase4 之后、Phase5 之前，观察的是"副作用已落地后"的世界位置。

### 单 Agent 的 tick

```text
tick(worldState):
  1. evaluator.setPerception(WorldPerception.of(agent, worldState, perceptionRange))
  2. 若状态已终止（C/D 空）→ 编译新计划压入 C：
       - 有 BehaviorProgram → compileFromProgram：E = defs + {dx, dy}，plan 压 C
       - 否则 → DefaultPlanCompiler.compileInto：[remember "goal"; move dx dy ×N]
  3. 循环（≤ max-steps-per-tick）：
       stepWithResume() 单步推进
       若**本步**产出 MoveEffect / SpeakEffect（主动作）→ 立即停止
  4. 返回本 tick 全部副作用
```

- **一步主动作即停**：一个 tick 至多让 Agent 执行一步"世界动作"；remember / ask-llm 等次级副作用不中断本 tick。
- **计划跨 tick 驻留 C 栈**：默认计划长度为 `default-plan-length`，耗尽即重编译，因此咨询 Oracle 的频率由它决定（DSL 行为程序可自行覆盖）。
- **运行时缓存**：`MindController` 以 agentId 为键惰性创建并缓存 `AgentRuntime`；每个运行时持有独立的 `MachineState` 与 `WorldOpEvaluator`，无共享、无竞态。
- **生命周期唯一入口**：居民创建、入世、记忆初始化与 EventBus 注册由 `WorldEngine` 统一负责，看板接口不得绕过它直接拼装居民。

### Agent 状态机

| 状态 | 判定 | 含义 |
|---|---|---|
| idle | C 与 D 均为空 | 计划耗尽，等待重编译 |
| executing | C 非空、D 空 | 正在执行行为程序 |
| suspended | D 非空 | 被中断 / 有闭包续体挂起 |

### 中断与恢复（D 栈）

```text
interrupt(handler):
  D.push({S, E, C, D} 快照)     # 与闭包应用挂起同构
  S.clear(); C.clear(); C.push(handler)

runHandler(maxSteps):
  循环直到 D 空（中断帧解开、主计划恢复）或步数耗尽：
    stepWithResume()

stepWithResume():
  若 C 空且 D 非空 → 取出 S 顶返回值 → 弹 D 帧 → restore 现场 → 返回值压回 S
  否则 → secd.step()
```

- **恢复语义**：主计划从被中断的**精确位置**继续（E 引用共享、S/C/D 浅克隆快照）。恢复本身计一次状态转移，不产副作用。
- **结束条件是 D 栈清空**，不是"handler 求值完"：handler 内可能还有嵌套闭包，解帧后 C 仍可能非空，必须继续求值。
- **入口**：`MindController.onMeet` 对相遇双方各执行一次 `interruptWithMeet`（DSL onMeet 或内置 onMeet），随后 `runHandler(max-handler-steps)`。
- **保护**：`ConvergenceMonitor` 检测 D 栈深度 > `convergence.max-d-stack-depth` 时发布 `agent.loop`（reason=d-stack）。

### SECD 内核

| 寄存器 | 职责 |
|---|---|
| S | 当前计算过程中的值栈 |
| E | 当前程序的词法环境 / 闭包环境 |
| C | 当前正在执行的行为程序（指令序列） |
| D | continuation：被挂起的执行上下文 |

四者只有**计算语义**，不与人格/记忆/感知对应（ADR-0001）。状态与驱动分离：`MachineState` 每 Agent 一份、跨 tick 驻留；`SECD` 是无状态驱动器。

- 指令（7 种）：`InstConst`、`InstVar`、`InstLam`、`InstApp`、`InstApply`、`InstSeq`、`InstIfThenElse`。
- 值（11 种）：`IntValue`、`StringValue`、`OpValue`、`PartialOpValue`、`Closure`、`VarValue`、`AgentRefValue`、`BoolValue`、`DirectionValue`、`InfiniteValue`、`VoidValue`。
- 求值语义：call-by-value（rator 先于 rand）；闭包浅拷贝捕获 E；`InstVar` 未绑定压回 `VarValue`；二元原语柯里化。

与参考实现的对照：

| 参考实现 | 本项目 | 理由 |
|---|---|---|
| S/E/C/D 为机器私有字段，`run()` 开头整体重置 | `MachineState`（每 Agent 一份，跨 tick 驻留）+ `SECD`（无状态驱动） | 一次性求值器 → 可驻留、可恢复 |
| δ 归约硬编码在 `Arithmetic` 静态类 | `OpEvaluator` 接口（纯算术 / 世界原语两个实现） | 世界原语可注入而不污染纯算术 |
| 纯计算，无副作用概念 | 机器只返回 `Effect` 列表 | World 修改隔离（ADR-0003） |
| `DumpItem` 链式帧，挂起 O(1) | `DumpFrame` 入栈，挂起时浅克隆 S/C/D | 每帧独立可持有/检查/可视化 |
| 发散抛 `DivergenceException` 终止 | `Reduction(infiniteReduction=true)` 软失败，预算用尽先判发散、非发散重置预算继续 | 长驻运行时不受单次预算限制 |
| 细分异常直接冒泡 | 单一 `SecdEvalException`，只降级本 Agent | 单 Agent 失败不拖垮整个 tick（ADR-0006） |
| `setTrace(PrintStream)` 直打 | `setTrace(Consumer<String>)` 观察者，null 零开销 | 接 DEBUG 日志 / MindViewer |
| if 两阶段（`InstIf`+`InstIfBranch`），条件 Int 0/1 | 单条 `InstIfThenElse` | 与 InstSeq/C 栈同构，无额外 D 帧 |

- **原样继承**：call-by-value、闭包浅拷贝、柯里化、D 栈挂起/恢复语义、发散检测思想、纯算术原语全集。
- **领域扩展**（参考实现不存在）：`InstSeq`；Bool/String/Void/AgentRef/Direction/Infinite 值；世界原语 move/speak/remember/ask-llm → Effect；`interrupt`/`runHandler`。
- **未移植**：`NormalOrderReducer` 及 `Alpha`/`FreeVars`/`Substitution`（范式归约，Agent 行为只需应用序）；Cli/Printer 命令行前端（由 DSL 前端取代）。
- **有意分歧**：`zerop`/比较原语返回 `BoolValue`、仅 `#f` 为假的真值规则，见 ADR-0001；不要按"与参考实现不一致"修掉。

### LLM Oracle 入口

全项目只有两个方法入口，除下列调用点外任何类不得持有 `ChatModel` 或调用 `LLMBrain`：

| 入口 | 调用点 | 返回 | 失败语义 |
|---|---|---|---|
| `decideAction(agent, worldState)` | 计划编译期：`DefaultPlanCompiler.compileInto`、`AgentRuntime.compileFromProgram` | JSON(deltaX, deltaY, reason)，决定 plan 的 dx/dy | 内部降级：解析失败/调用异常 → 随机方向，世界继续运行 |
| `chat(prompt)`（ask-llm 原语） | `WorldOpEvaluator.askLlm` | 模型文本原样，压入 S 栈作为值 | 内部降级：调用异常 → 哨兵 `[oracle-unavailable]`，行为程序可用 `eq` 检测；不抛出 |

- **哨兵常量**：`LLMBrain.ORACLE_UNAVAILABLE`，是普通字符串值而非异常；`LLMRequestEffect` 的 response 字段同样携带它，审计可辨。
- **同步阻塞**：`ask-llm` 咨询期间该 Agent 的 tick 被挂起等待响应。
- **不持有 World**：`LLMBrain` 无 World 引用，结果只能经 SECD/Effect 路径生效。

### 默认计划与内置 onMeet

- **默认计划**（计划耗尽时编译）：`decideAction` 决定方向 (dx,dy)，编译为 `[remember "goal" "head-dx,dy"; move dx dy ×N]` 的 InstSeq，N = `default-plan-length`。
- **内置 onMeet**（无 DSL onMeet 时）：`apply(λother. (speak other 问候; remember "met" "遇见了 X"), other)`，以闭包应用形式占 D 栈一帧。

### 运行时配置键

键名以本表为准，**默认值以 `application.yml` 为准**：

| 键 | 作用 |
|---|---|
| `perception-range` | 感知半径（曼哈顿距离） |
| `meet-distance` | 相遇判定距离（欧氏） |
| `event-history-size` / `event-scan-limit` | 事件历史容量与扫描上限 |
| `max-steps-per-tick` | 单 tick 单台机器的步进预算 |
| `max-handler-steps` | onMeet handler 的步数预算 |
| `secd-max-steps` | SECD 单次总步数上限（防无限归约） |
| `secd-trace-enabled` | 是否输出每步 trace（DEBUG 日志） |
| `default-plan-length` | 内置默认计划的路径长度（tick 数） |
| `move-clamp` | LLM 决策/随机降级的单轴移动钳制 |
| `convergence.*` | stuck / converged / 周期检测 / 位置历史 / D 栈深度阈值 |
| `default-personality` | 居民未配置人格时的通用兜底 |

## 约束与不变量

1. **只产 Effect**：运行时与求值器只返回 Effect，绝不直接修改 World。
2. **一步主动作即停**：每 tick 至多一步 Move/Speak。
3. **同 tick 快照一致**：所有 Agent 看到同一份 WorldState，本 tick 内的移动下个 tick 才可见。
4. **每 Agent 独占机器**：一份 `MachineState` + 一台 `WorldOpEvaluator`，无共享状态。
5. **`runHandler` 不推进主计划**：D 栈清空即停，否则 meet tick 会多走一步。
6. **预算不可省略**：tick 步数、handler 步数、SECD 总步数都必须有上限，不得用 `Integer.MAX_VALUE` 绕过。
7. **恢复语义依赖浅克隆**：S/C/D 是快照、E 是共享引用；引入可变环境（如 `set!`）须重新论证。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| 求值错误（`SecdEvalException`：未知原语、类型不符、非函数应用、除零） | 清空机器（含未解开的中断帧），下一 tick 重编译；已产出的 Effect 照常落地 | 单个 Agent 单 tick |
| 装配/生命周期错误（如缺 Perception 注入） | fail-fast 冒泡 | 启动或整个 tick |
| `decideAction` 解析失败/调用异常 | 降级为随机方向 | 单次计划编译 |
| `ask-llm` 调用异常 | 返回哨兵文本压栈，不抛出 | 当前行为程序 |
| `runHandler` 预算耗尽 | 软失败：D 栈可能仍非空，主计划留待下 tick 继续（`tick` 同样走 `stepWithResume`） | 不报错、不丢计划 |

## 验收标准

1. 一个 tick 内 Phase1→5（含 4.5）按序执行，副作用只在 Phase3/Phase4 落地。
2. 计划耗尽时能自动重编译，且重编译频率由 `default-plan-length` 决定。
3. 相遇触发 onMeet 后，双方主计划从被中断的指令处继续，不丢失也不重复执行。
4. 坏行为程序只影响自身 Agent：该 tick 静默，下一 tick 恢复。
5. `ask-llm` 失败时行为程序能通过 `eq` 检测到哨兵并分支。
6. 同一 tick 内后行动的 Agent 不会看到先行动者本 tick 的新位置。

## 相关文档

- 相关 ADR：[ADR-0001](../decisions/ADR-0001-secd-runtime.md)、[ADR-0002](../decisions/ADR-0002-llm-oracle.md)、[ADR-0004](../decisions/ADR-0004-d-stack-interrupt.md)、[ADR-0006](../decisions/ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Effect](effect.md)、[Behavior DSL](behavior-dsl.md)、[Perception](perception.md)、[Personality](personality.md)
- 相关 Architecture：[architecture.md](../architecture.md)

## 待解决问题

- `runHandler` 以"D 栈空"为结束条件：若主计划在中断时 D 本已非空（相遇发生在闭包内部），会连带解掉外层帧。收口方案是记录中断前深度、以"D 回到该深度"结束，尚未实现。
- `stepWithResume` 的续体恢复不经 `SECD.step`，trace 暂不覆盖该转移。
