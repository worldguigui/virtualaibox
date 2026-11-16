# ADR-0005: 配置参数提取

- 状态：Accepted
- 日期：2026-11-05

## 决策背景

- 本决策的目标：行为调整（提示词、阈值、世界尺寸、居民人格）应当**改配置即可生效**，不修改代码、不重新编译。
- 现状与此相悖：可调参数以硬编码形式散落在前后端 20+ 处，且同一事实存在多份副本。

**硬编码散落（部分）**

| 位置 | 硬编码内容 |
|---|---|
| `LLMBrain.buildPrompt` | 决策提示词整段（含"37x37"，而世界尺寸已可配置） |
| `PersonalityProfile.defaultFor` | Alice/Bob 人格按名字 if 特判 + 通用兜底 |
| `WorldPerception.PERCEPTION_RANGE` | 感知范围 5（注释自注"后续可配置"） |
| `TickSchedule.interactionPhase` | 相遇距离 1.5 |
| `EventBus.MAX_HISTORY_SIZE` | 事件历史上限 210 |
| `AgentRuntime` / `MindController` / `DefaultPlanCompiler` / `SECD` | 各类步数预算 500/200/3/10000 |
| `ConvergenceMonitor` | 收敛检测阈值 3/5/8/32/50 |
| `DashboardController` | REST 默认值 30/8/50 与魔法数 `buildAgents(8)`、`getEventHistory(10000)` |
| `World()` 无参构造 | `this(37, 37)` |
| 前端 `WorldMap` / `WorldPage` | `?? 37` 兜底 ×5、副标题"37 × 37 网格世界" |
| 前端 `AgentCreatePanel` / `AgentEditorModal` | 两份重复的英文人格预设、坐标上限 `max="36"` |
| 前端 `useDashboard` / `App` | 轮询 5000ms、autoSpeed 650、eventLimit 40、memoryLimit 8 |

**同一事实多副本**

- "37×37"：`WorldConfig` 字段默认值 + `defaultConfig()` + `World()` + yml + 前端兜底/文案，共 5+ 处；
- 人格：Java `defaultFor`（中文）+ 前端 `buildDefaultProfile`（英文，两个组件各一份）。

**后果**：调整行为需要改代码重编译；改一处漏一处（如改世界尺寸后提示词仍宣称 37x37），维护难度较高。

## 决策选择

Q. 我们到底在决策什么？<br/>
A. 为"哪些值可以不改代码就改"划一条边界，并把越界散落的值收敛回边界内——收敛到哪、按什么原则收纳。

Q. 收敛到哪里？唯一事实源还是多处并存？<br/>
A. `application.yml` 的 `virtual-ai-box.*` 是唯一事实源（SSOT）。Java 侧字段初始化器保留一份与 yml 一致的代码兜底默认值，仅用于 yml 缺失或无 Spring 装配的纯代码/测试路径——单测不依赖 Spring 即可运行；代价是两份需保持一致（靠机制化测试，见改造边界禁止第 4 条）。

Q. 是不是所有值都该进配置？<br/>
A. 不是。**三问检验**：1.谁改它（部署者还是开发者）；2.改它需要懂代码吗；3.改它是否伴随结构性代码改动。**配置 = 不改逻辑结构就能变的值。** width/height、各类阈值/预算/默认值三问全指向配置；居民及人格属世界内容（需求方原始点名项），同样进配置。

## 最终决策

- **`application.yml` 的 `virtual-ai-box.*` 为唯一事实源（SSOT）；行为调整改配置、不改代码、不重编译。**
- **配置边界准则（三问检验）**：① 谁改它；② 改它需要懂代码吗；③ 改它是否伴随结构性代码改动——**配置 = 不改逻辑结构就能变的值**。
- **五个配置段承接所有可调参数**（`@ConfigurationProperties`，Java 默认值与 yml 保持一致）：

| 配置段 | 配置类 | 内容 | 主要消费方 |
|---|---|---|---|
| `virtual-ai-box.world` | `WorldConfig`（扩展） | 世界名/尺寸/规则/物品 + **默认居民含人格**（`default-residents[].personality`） | `WorldEngine` |
| `spring.virtual-clock` | `VirtualClockProperties`（原有） |虚拟时钟的相关配置 | `VirtualClock` |
| `virtual-ai-box.runtime` | `RuntimeProperties`（新） | 感知范围、相遇距离、事件历史上限/扫描上限、SECD/tick/handler 步数预算、默认计划长度、移动钳制、收敛阈值、通用兜底人格 | `MindController`、`TickSchedule`、`EventBus`、`ConvergenceMonitor`、`WorldEngine`、`LLMBrain` |
| `virtual-ai-box.prompt` | `PromptProperties`（新） | 仅短标量：默认/降级理由（长文本模板见下条，不在 yml） | `LLMBrain` |
| `virtual-ai-box.dashboard` | `DashboardProperties`（新） | REST 默认值（事件/记忆条数、轻量快照居民数） | `DashboardController` |

- **提示词外置边界**：政策文本外置文件（`intro.txt` / `task.txt`），脚手架段留代码常量；占位符 `{width}` `{height}` `{tick}` `{name}` `{x}` `{y}` `{personality}` `{memory}` `{moveClamp}` 由代码填充，人格段、记忆段**内容为空时整段省略**（拼接顺序固定）；世界尺寸来自本 tick 的 `WorldState`（新增 `worldWidth`/`worldHeight` 字段，由 `MindController.buildWorldState` 填充），不再硬编码。
- **人格三级解析**：显式传入的人格（空字段回落）→ 世界配置同名默认居民 → `runtime.default-personality`。
- **前端一致性**：运行数据以后端 API 为准；`config.ts` 只集中 UI 兜底值；人格只裁剪不预填。

## 改造边界

### 明令禁止的改动

1. **禁止新增硬编码可调值**：新参数必须按三问检验归入对应配置段，不得在业务代码散落魔法数。
2. **禁止提示词脚手架外置**：占位符语义与格式契约（`PROMPT_FORMAT` 的 6 个 `%s` 顺序）必须留在代码常量，随代码评审。
3. **禁止前端第二事实源**：不得预填人格兜底、不得在组件里散落 `?? 37` 类兜底——运行数据取 API，UI 兜底只集中 `config.ts`。
4. **禁止 Java/yml 默认值漂移**：`ConfigDefaultsConsistencyTest` 机制化校验的字段必须逐字段一致；其余以文件头注释声明"yml 是主源"。

### 涉及旧有模块与核心文件

| 模块 / 文件 | 改动 |
|---|---|
| `properties/RuntimeProperties`、`PromptProperties`、`DashboardProperties`（新增） | runtime / prompt / dashboard 三个配置段 |
| `domain/world/WorldConfig`（扩展） | 默认居民含人格；`validate()` 新增非空名校验，坏配置启动即失败 |
| `brain/PromptRegistry`（新增） | intro/task 启动加载、裁剪、fail-fast |
| `brain/LLMBrain`（改造） | `buildPrompt` 改为"文件政策文本 + 代码脚手架"装配；删硬编码提示词与 37x37 |
| `domain/agent/PersonalityProfile`（改造） | 删 Alice/Bob 名字特判；`generic()` / `normalizeFor()` / `PROMPT_FORMAT` 常量 |
| `mind/WorldPerception`（改造） | 删 `PERCEPTION_RANGE` 常量，改配置注入 |
| `mind/MindController`、`mind/AgentRuntime`、`mind/DefaultPlanCompiler`、`core/TickSchedule`、`monitor/ConvergenceMonitor`、`domain/event/EventBus`、`core/WorldEngine`、`controller/DashboardController`（改造） | 注入配置类，删除各自魔法数 |
| `domain/world/WorldState`（改造） | 新增 `worldWidth`/`worldHeight` 与 4 参构造 |
| `resources/prompts/decision/intro.txt`、`task.txt`（新增） | 提示词政策文本外置 |
| `resources/application.yml` | `virtual-ai-box.*` 各段（主源） |
| 前端 `src/config.ts`、`src/lib/personality.ts`（改造） | 集中 UI 兜底值；`maxCoordinate` 动态解析 |

## 最终实现

- 四个配置类 `@ConfigurationProperties` 生效，Java 字段默认值与 yml 一致（yml 是主源）。
- `PromptRegistry`：启动期加载 `intro.txt` / `task.txt`，裁剪首尾空白，缺文件/空白文件 fail-fast。
- 守护测试：`properties/ConfigDefaultsConsistencyTest`（容器外用 Binder 绑定 yml 后与 `defaults()` 逐字段比对，并断言关键键存在——防拼写错误被 Binder 静默忽略后"看似一致"）；`brain/PromptRegistryTest`（加载/裁剪/fail-fast/REQUIRED 清单契约，含"JSON 示例不得硬编码钳制幅度"断言）。
- 验证记录：`mvnw test` 85 例全部通过（含 `contextLoads`）；jshell 字节级比对——按 `buildPrompt` 同算法装配的提示词与重构前硬编码版本，在"含人格+记忆"与"均省略"两变体下 `equals` 均为 `true`；前端 `tsc -b` 零错误、oxlint 无新增警告。

## 决策后果

- 行为调整（提示词、阈值、尺寸、居民人格）改配置即可，无需改代码重编译；前后端一致性由 API 保证而非人工同步。
- `PROMPT_FORMAT` 恰好需要 6 个 `%s`（对应人格六字段），配错会在运行时抛 `MissingFormatArgumentException` → `decideAction` 降级为随机决策（fail-soft）；契约常量与字段同文件可见，随代码评审。
- 提示词段间排版由代码负责：新增模板段需在 `buildPrompt` 同步拼接顺序与间距，文件不表达段间关系。
- 外置文件与 yml 一样**需重启才生效**（启动期加载），没有热更新收益。
- 构造器签名变化（均保留旧重载）：`LLMBrain(ChatModel, PromptProperties, RuntimeProperties[, PromptRegistry])`、`TickSchedule` / `AgentRuntime` / `ConvergenceMonitor` / `EventBus` / `WorldEngine` / `DashboardController` 相应注入配置类；API 变化（向后兼容）：`worldConfig.defaultResidents[].personality` 为新增字段。
- 前端 `config.ts` 的 `worldSize` 兜底与后端默认值仍是**约定一致性**（跨栈无机制校验，靠注释）。
