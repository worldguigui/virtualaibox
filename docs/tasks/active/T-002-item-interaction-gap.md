# T-002：居民与地图物品交互——需求诊断与问题定义

- 状态：进行中（诊断阶段，实现未开始）
- 日期：2026-11-11 开始
- 涉及模块（诊断覆盖，未改动任何代码）：`domain/world`、`domain/agent`、`core/WorldEngine`、`mind/`（Perception / WorldOpEvaluator）、`effect/`、`executor/`、`brain/LLMBrain`、`controller/`、`frontend/`

## 1. 背景

外部反馈与代码调查共同指向同一问题：**居民实际上无法与地图中的物品交互**。物品子系统在"世界侧"（配置、生成、拾取、库存）有完整管道，但在"心智侧"（快照、感知、原语、意图、落地、记忆、提示词）全部断链。本任务是项目以「总纲图分层排查」方法完成的首次系统性需求诊断：按架构主图自配置层到前端层逐层取证，定位断点并形成问题定义，作为后续设计与实现的输入。

## 2. 目标

**终态目标（可验收表述）**：居民作为认知主体能完成物品交互的完整闭环——

1. **看见**：行为程序可通过感知原语查询感知范围内的物品（含位置）；同一 tick 内所有居民看到同一份物品视图；
2. **决定**：LLM Oracle 的提示词包含可行动的物品信息（不只是图纸目录），行为 DSL 具备表达拾取意图的词汇；
3. **执行**：拾取以 Effect 表达、经 EffectExecutor 落地（遵守 Effect 单向流，不变量 #1）；
4. **记忆**：拾取经历进入 AgentMemory，能出现在后续 `summarizeMemoriesForLLM` 的决策上下文中（记忆闭环在物品这条腿上接通）；
5. **可观测**：交互事件（spawn/picked）可在前端事件流与地图上观察。

**本任务当前阶段目标**：完成问题定义与关键设计决策（§5 开放问题裁决），形成实现方案。实现另行推进。

## 3. 非目标

- 本文档阶段不写任何实现代码；
- 不修复同类化石 `trade`（不转货）/`combat`（不结算血量）与 `spawnWeight` 未使用问题——仅登记（见 §11），可另立任务；
- 不引入"LLM 直接修改世界"的捷径（ADR-0002 边界不动）；
- 不在本任务内做前端物品渲染的美化改造（仅保证数据可见性）。

## 4. 当前实现调查

### 4.1 物品管道全景（✅ = 通，❌ = 断，⚠️ = 半通）

```
配置层   yml items: [berry, wood]（含 maxCount/spawnWeight）      ✅
实例层   World.itemInstances + spawnItem()（发 town.action 审计）  ✅  World.java:20、WorldEngine.java:170-184
再生层   simulateTownTick：每 3 tick 生成 1 个，位置=伪随机哈希     ✅  WorldEngine.java:144-156
拾取层   pickupItem()：同格才触发 → inventory + item.picked 事件    ✅  WorldEngine.java:187-198
──────────────────── ⚡ 心智侧断层线 ⚡ ────────────────────
快照层   WorldState 只带 itemDefinitions（图纸目录），不带实例位置   ❌  WorldState.java:25
感知层   Perception 5 原语全是查人                                  ❌  Perception.java:18-37
原语层   指令集 = move/speak/remember/ask-llm + 比较/算术           ❌  WorldOpEvaluator.java:59-121
意图层   Effect 仅 Move/Speak/Remember/LLMRequest                   ❌  effect/Effect.java
落地层   EffectExecutor 四路分发                                    ❌  EffectExecutor.java:46-58
记忆层   Agent.onEvent 的 switch 只认 met/moved/tick started/ended  ❌  Agent.java:80-126（town.action 落 default）
提示词层 WORLD_SECTION 只注入图纸目录，无位置、无行动词汇            ❌  LLMBrain.java:30-33、113-120
前端层   types/API/背包数据齐全；WorldMap 未见物品渲染               ⚠️  types.ts、dashboard.ts:52-64
```

### 4.2 消费者证据（grep 全量）

`getInventory` / `getItemInstances` 的调用者只有两类：`WorldEngine`（生成/拾取逻辑自身）与 `DashboardController`（`DashboardController.java:284,294,335`，均为**展示**用途）。心智侧（Perception / WorldOpEvaluator / Effect / AgentMemory）**零消费**。

### 4.3 三个关键证据

**证据 1：唯一的"交互"绕过了整个心智闭环。**
`pickupItem` 的调用者有且只有一个：`simulateTownTick` 的自动拾取（`WorldEngine.java:159-166`）——居民恰好站在物品格上（x、y 精确相等）时直接写入 `inventory`。该路径属 town-runtime 命令通道（`architecture.md` §3.8 认可的旁路），**不违反不变量 #1 的字面**，但违背系统设计精神：居民没有看见、没有决定、没有表达意图，背包就多了东西。小镇的物品经济完全由巧合驱动（伪随机位置 + 路人重合）。

**证据 2：军师看得到菜单，居民没有手。**
`LLMBrain.buildPrompt` 的 WORLD_SECTION 注入 `itemDefinitions` 目录（`LLMBrain.java:113-120`）——LLM 知道世上有浆果，但不知道物品在哪（快照层不带实例），也没有任何词汇表达拾取意图（指令集无此原语）。决策者与执行器之间的词汇表缺了一页。

**证据 3：居民捡了东西，却不记得。**
自动拾取发 `town.action`（item.picked）事件，但 `Agent.onEvent` 的记忆分派 switch 只处理 `agent.met / agent.moved / tick.started / tick.ended`（`Agent.java:80-126`）：事件流水账 `eventHistory` 记一笔字符串，**结构化记忆（AgentMemory）不写**——而 `summarizeMemoriesForLLM` 只查 MemoryStore。结果：背包里躺着浆果，决策上下文里永远没有"我捡过浆果"。

### 4.4 伴生化石（登记，不处理）

- `spawnWeight` 配置字段未被生成逻辑使用（`WorldEngine.java:147-150` 用 `findFirst()`，永远先刷满 berry）；
- `trade`：只转货币，"当前尚未绑定具体物品转移"（`WorldEngine.java:262`）；
- `combat`：只发事件，"当前尚未结算生命值"（`WorldEngine.java:273`）；
- DSL 规格的 PLANNED 清单（observe / and-or-not）中亦无物品原语，即该断链此前未被任何文档记录。

### 4.5 人工验证实验（实现前可复跑，眼见为实）

1. 跑小镇观察事件流出现 `town.action / item.picked`，点开对应居民的记忆面板——有拾取、无记忆；
2. 在 `resources/agents/*.lambda` 中写 `(pickup ...)` → 运行时抛 `未知二元原语 'pickup'`（ADR-0006 富消息）——证明指令集无此动词；
3. 开 LLM 请求日志或断点 `buildPrompt`，确认 WORLD_SECTION 只有目录无坐标；
4. `grep -rn "pickupItem" src/main/java` ——唯一调用者是 `simulateTownTick` 自动拾取。

## 5. 设计方案（草案 · 待确认）

以下为待裁决的开放问题清单，按架构主图分层排列。**尚未形成最终方案**；裁决完成后本节补全设计，预计需另立 ADR（跨模块、影响 Effect 目录与 DSL 契约）。

| # | 层 | 待裁决问题 | 牵动的不变量/原则 |
|---|---|---|---|
| Q1 | 快照层 | `WorldState` 是否携带物品**实例**？带全量还是仅感知范围内？与 #8（同 tick 快照）及防御性拷贝（现存浅拷贝隐患：`AgentState`/`WorldItem` 对象仍是共享引用）如何权衡 | 不变量 #8 |
| Q2 | 感知层 | 新增哪些感知原语（`nearest-item`？`items-in-range`？）？查不到时的哨兵约定是否沿用 null→`#f` | 不变量 #7 |
| Q3 | 意图层 | 拾取是否新增 Effect 类型（如 `PickUpEffect`）？Effect 目录扩充与 ADR-0003 的关系 | Effect 单向流 |
| Q4 | 原语层 | `pickup` 的签名（无参拾脚下 / 带参指定物品）？失败返回 `#f` 还是抛 `SecdEvalException`（分诊：捡不到属数据不确定性还是错误？） | fail-fast/fail-soft 分诊 |
| Q5 | 落地层 ⚠️ | 拾取落地逻辑现居 `WorldEngine.pickupItem`；`EffectExecutor` 若直接调用则出现 **executor→core 反向依赖**。落地逻辑应驻留哪一层？ | 分层依赖方向 |
| Q6 | 事件/记忆层 | 主动拾取的记忆由谁写——程序显式 `remember`（主动记忆）或落地自动写（被动记忆）？`town.action` 是否纳入 `onEvent` 分派 | 两条记忆通道 |
| Q7 | 哲学层（决策根） | 自动拾取保留还是移除？若移除，纯 SECD 居民在无物品感知时会否永远错过物品（鸡生蛋问题）？**本问题决定其余各层形态，应最先裁决** | 四条铁律的精神 |
| Q8 | 提示词层 | 新原语/物品信息是否外置进 `task.txt`/`intro.txt`？用 ADR-0005 三问检验裁决 | ADR-0005 |
| Q9 | DSL 层 | `.lambda` 语法是否扩充？静态校验（`自由变量 ⊆ {dx,dy} ∪ defs`）是否需要同步 | 编译期 fail-fast |
| Q10 | 再生层 | `spawnWeight` 启用、生成位置策略（是否靠近居民活动区）是否随本任务一并设计 | — |

**候选方向（供裁决参考，非结论）**：
- 方向 A（最小补丁）：保留自动拾取，仅补感知与提示词——居民能"看见"，拾取仍是巧合；
- 方向 B（全链心智化）：移除自动拾取，按 Q1→Q9 新建"感知→意图→落地→记忆"物品全链，拾取成为居民意志的表达。

## 6. 修改范围

待设计确认后填写。预计触及（标注*为预计）：`domain/world/WorldState*`、`mind/Perception` / `WorldPerception` / `WorldOpEvaluator`*、`effect/*`*、`executor/EffectExecutor`*、`core/WorldEngine`、`brain/LLMBrain`、`domain/agent/Agent`、`resources/prompts/decision/*`、`docs/spec/*`、`frontend`（渲染）、新 ADR。

## 7. 验收标准

- [ ] 功能验收：对应 §2 五条闭环逐条可演示（含一次"看见→走近→拾取→记忆出现于下一 tick 提示词"的端到端场景）
- [ ] 测试验收：既有 95 例全绿不改动断言；新增用例覆盖新原语/Effect/降级路径（延续 ADR-0006 的测试风格）
- [ ] 代码检查：`git diff` 无残留（无注释掉的旧代码、无未使用 import）
- [ ] 文档更新：ADR（新）、`architecture.md` 组件表与不变量、`spec/behavior-dsl.md` 原语表、`spec/perception.md`、changelog 一行；本任务文档归档至 `completed/`

## 8. 实施记录

- 2026-11-11：完成诊断取证。通读 `WorldEngine`（全文）/`World`/`WorldItem`/`WorldItemDefinition`/`WorldState`/`Perception`/`WorldOpEvaluator`/`Effect` 四实现/`EffectExecutor`/`Agent.onEvent`/`LLMBrain.buildPrompt`/前端 `types.ts` 与 `api/dashboard.ts`；grep 全量确认 `inventory`/`itemInstances` 消费者分布；形成 §4 断点清单与 §5 开放问题。**未改动任何代码。**

## 9. 测试结果

未执行（本阶段无代码改动）。实现阶段的基线为 2026-11-09 的 10 类 95 例全绿（见 `../architecture.md` §6.1 与 ADR-0006）。

## 10. 知识沉淀（建议，对应 KNOWLEDGE_MAP 待更新）

- Effect 单向流与唯一落地关卡（不变量 #1）：新增交互类 Effect 时的守界方法
- 感知只读与 null→`#f` 哨兵（不变量 #5/#7）：新查询原语的返回值约定
- 同 tick 快照一致性（不变量 #8）：WorldState 扩展字段的拷贝策略（现存浅拷贝隐患在此复用）
- fail-fast / fail-soft 分诊（ADR-0006）：`pickup` 失败语义的裁决工具
- 记忆双通道（主动 RememberEffect / 被动 onEvent）：拾取记忆归属
- ADR-0005 三问检验：提示词外置边界

## 11. 遗留问题

- 兄弟化石未处理：`trade` 不转货、`combat` 不结算、`spawnWeight` 未使用（可另立任务）；
- 前端 `WorldMap` 物品渲染缺失（数据已在 API 中，仅展示层缺口）；
- `EffectExecutor` → `core` 的依赖方向张力（Q5）若裁决为"落地逻辑下沉/上移"，可能牵动 `architecture.md` 组件依赖图；
- 快照浅拷贝隐患（`AgentState`/`WorldItem` 共享引用）在本任务 Q1 中必须一并裁决，否则物品快照会继承同一隐患。
