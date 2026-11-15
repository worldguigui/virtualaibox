# ADR-0003: Effect 隔离副作用

- 状态：Accepted
- 日期：2026-09-10

## 决策背景

- 大模型有了想法和行动内核后，此时，引入了新的问题，想法和行动该如何落实到世界里呢？要知道，光有想法、行动，但世界实体没有变化，那这些想法和行动就没有意义了。于是，想法和行动必须落实到世界里，产生实际的影响。
- 早期草案让求值器直接改 World（Agent 位置、世界事件），副作用与计算耦合行在一起，出错时说不清是算错了还是改错了。行为不可审计、测试困难。

## 决策选择

Q. 我们到底在决策什么？<br/>
A. 行为已经交给确定性内核（[ADR-0001](ADR-0001-secd-runtime.md)），大模型思考的接入时机也已经确定（[ADR-0002](ADR-0002-llm-integration.md)）。现在要决定：内核“想改世界”这件事用什么形式表达，又由谁真正落地。

Q. 让 SECD 直接改 World 不行吗？求值器里拿到 World 引用，算完就 set 进去，最省事。<br/>
A. 不行。这是把“计算”和“事实”耦合在一起：同一个方法既算值又改世界，行为无法单测（必须起一个 World）、无法审计（没有中间记录）、无法回放（改动已发生，无法重放）。内核只能产出“意图描述”，落地是别人的事。

Q. 那 Effect 由谁执行？允许各模块自己落地吗？<br/>
A. 只允许 `EffectExecutor`，它是居民行为派生副作用的唯一出入口。新增世界能力必须新增 Effect 类型并在此分派，不允许在别处私改 World。审计单位因此唯一：一次 Effect 列表 = 一次行为。

Q. 感知也是“对世界做点什么”，为什么不走 Effect？<br/>
A. 因为感知是读，不是写。早期 `ObserveEffect` 的语义不明确，已废弃。

Q. 那 ask-llm 呢？它既不读也不写世界，为什么有 `LLMRequestEffect`？<br/>
A. 它是信息性副作用：不改变 World，但要留下“这个 Agent 在此时咨询过 Oracle、问了什么、答了什么”的审计痕迹（[spec/effect.md](../spec/effect.md) §5）。执行器对它只记日志、不落库。换句话说，它存在是为了可解释，不是为了可改变。

## 最终决策

- **三层分工固定为：**
```
SECD = 计算            产出 Effect（副作用描述，不执行）
Effect = 意图          MoveEffect / SpeakEffect / RememberEffect / LLMRequestEffect
EffectExecutor = 执行  唯一允许修改 World 的路径（Move 经 ActionExecutor）
```

- **单向流**：SECD / WorldOpEvaluator / AgentRuntime 只产出 Effect，绝不直接修改 World；World 只被 EffectExecutor 修改。

## 改造边界

### 明令禁止的改动

1.**禁止读操作走 Effect**：不得复活 `ObserveEffect`，也不得为感知新增 Effect。
2.**禁止绕过 `EffectExecutor` 落地居民行为**：不得新增第二处调用 `ActionExecutor.executeMoveAction` 或直接 `memory.recordObservation` 的路径。
3**禁止 Effect 携带可执行逻辑**：Effect 是 record（纯数据），不得内含 `Runnable` / 回调，否则无法保证 SECD机 无法触摸到世界。

### 涉及旧有模块与核心文件

| 模块 / 文件 | 改动 |
|---|---|
| `secd/SECD` | `step` 返回 `List<Effect>`；不再接收可写 World |
| `mind/WorldOpEvaluator` | 世界原语 move / speak / remember / ask-llm 产出 Effect |
| `mind/AgentRuntime` | `tick` 累积 Effect 返回；不持有执行器 |
| `mind/DefaultPlanCompiler` | 编译期只产计划，不产副作用 |
| `executor/EffectExecutor`（新增） | 唯一落地入口，按四类分派 |
| `effect/*`（新增） | `Effect` 标记接口 + 四个 record |
| `executor/ActionExecutor` | 负责移动的真实落地，仅被 EffectExecutor 调用 |

## 最终实现

- `effect/Effect`：空标记接口，用来标记副作用类。
- `effect/MoveEffect` / `SpeakEffect` / `RememberEffect` / `LLMRequestEffect`：四个 record，字段即意图的全部内容。
- `executor/EffectExecutor`：`execute(List<Effect>, World)` 逐条 `instanceof`，在派发对应的事件。
- `mind/AgentRuntime`：`tick` / `runHandler` 返回 `List<Effect>`，只累积副作用但不落地。

## 决策后果

- World 的修改只有一条路径（居民行为派生），因此收敛检测、事件日志、前端可视化统一以 Effect 为审计单位。
- 新增世界能力的成本固定：必须新增 Effect 类型 + 在 `EffectExecutor` 分派，不能走捷径。这条约束已经影响到物品交互设计（T-002）。
- 单向流把写世界收窄为一处，代价是 `EffectExecutor` 成为改动热点：每次扩能力都要动它，但它同时也是唯一需要 review 的地方
- `LLMRequestEffect` 是“看似多余”的一类：它不改变世界，只为留下咨询痕迹；`EffectExecutor` 未持久化它，因此当前只保证可解释，不保证可回放。