# ADR-0005：配置参数提取

| | |
|---|---|
| **状态** | 已采纳（Accepted），2026-11-05 |
| **日期** | 2026-11-05 |

## 背景

重构前，可调参数以硬编码形式散落在前后端 20+ 处，且同一事实存在多份副本：

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

## 决策

1. **`application.yml` 的 `virtual-ai-box.*` 是唯一事实源（SSOT）**。Java 侧字段初始化器保留一份与 yml 一致的代码兜底默认值（仅用于 yml 缺失或无 Spring 装配的纯代码/测试路径）。

2. **四个配置类承接所有可调参数**（`@ConfigurationProperties`，Java 默认值与 yml 保持一致）：

   | 配置段 | 配置类 | 内容 | 主要消费方 |
      |---|---|---|---|
   | `virtual-ai-box.world` | `WorldConfig`（扩展） | 世界名/尺寸/规则/物品 + **默认居民含人格**（`default-residents[].personality`） | `WorldEngine` |
   | `virtual-ai-box.runtime` | `RuntimeProperties`（新） | 感知范围、相遇距离、事件历史上限/扫描上限、SECD/tick/handler 步数预算、默认计划长度、移动钳制、收敛阈值、通用兜底人格 | `MindController`、`TickSchedule`、`EventBus`、`ConvergenceMonitor`、`WorldEngine`、`LLMBrain` |
   | `virtual-ai-box.prompt` | `PromptProperties`（新） | 仅短标量：默认/降级理由（**长文本模板见决策 3，不在 yml**） | `LLMBrain` |
   | `virtual-ai-box.dashboard` | `DashboardProperties`（新） | REST 默认值（事件/记忆条数、轻量快照居民数） | `DashboardController` |

3. **提示词只外置"政策文本"，脚手架段留在代码**。
   - **外置为文件**（`classpath:prompts/decision/`，`PromptRegistry` 启动加载、缺文件/空白文件即 fail-fast、首尾空白自动裁剪）：仅 `intro.txt`（世界观与任务目标）和 `task.txt`（决策契约与输出格式）——使用者不看代码也会反复调优的两段；
   - **留在代码**（`LLMBrain` 常量，`personality-format` 为 `PersonalityProfile.PROMPT_FORMAT` 常量）：状态注入、他人列表、记忆段等人格/记忆注入脚手架——占位符语义与字段格式由代码定义，改动必然伴随代码改动，外置只是把一处改动劈成两处；
   - 占位符 `{width}` `{height}` `{tick}` `{name}` `{x}` `{y}` `{personality}` `{memory}` `{moveClamp}` 由代码填充；人格段、记忆段**内容为空时整段省略**（拼接顺序固定）；世界尺寸来自本 tick 的 `WorldState`（新增 `worldWidth`/`worldHeight` 字段，由 `MindController.buildWorldState` 填充），不再硬编码。

4. **人格三级解析**：显式传入的人格（空字段回落）→ 世界配置中同名默认居民的人格 → `runtime.default-personality`。`PersonalityProfile` 删除 Alice/Bob 名字特判，只保留 `generic()` 兜底与 `normalizeFor(profile, fallback)`。

5. **前端一致性机制**：
   - 世界网格尺寸等运行数据**以后端 API 返回为准**（`/api/dashboard` 的 `world.width/height`）；
   - `frontend/src/config.ts` 只集中显示兜底值（`worldSize: 37` 等），注释约束须与后端默认值一致；
   - 坐标上限**从 `worldConfig` 尺寸动态解析**（`frontend/src/lib/personality.ts` 的 `maxCoordinate`）；人格**前端只裁剪、不预填兜底**：表单提交的空字段保持为空，回落完全交给后端三级解析（决策 4）；
   - 轮询间隔、autoSpeed、eventLimit、memoryLimit 等纯 UI 默认值集中在 `config.ts`。

6. **兼容性策略**：所有改动类保留旧构造器重载并委托 `RuntimeProperties.defaults()` 等纯代码实例，测试与无 Spring 装配路径零行为变化。

## 理由

**配置的边界准则（三问检验）**：一个值该不该进配置，看三点——① 谁改它（部署者还是开发者）；② 改它需要懂代码吗；③ 改它时是否伴随结构性代码改动。**配置 = 不改逻辑结构就能变的值。**

- **width/height、各类阈值/预算/默认值 → 配置**：三问全指向配置，毫无争议；
- **居民及人格 → 配置**：世界内容，设计者可改，不伴随代码改动（本条为需求方原始点名项）；
- **intro/task → 文件**：提示词的"政策文本"，提示词工程师改它不需要懂 Java，也不伴随代码改动；是 LLM 应用中真正被反复调优的段落。外置为文件还有一层收益：**加载器裁剪首尾空白、代码统一排版**，使"编辑器自动增删末尾换行"这类最常见的意外变得无害；
- **状态注入/他人列表/记忆段/人格格式 → 代码常量**：纯脚手架，占位符语义由代码定义（`{tick}` 由 `WorldState` 填充、`%s` 个数必须匹配 `PersonalityProfile` 字段顺序），改它们必然伴随改代码——外置没有换来解耦，只把一处改动劈成两处，且把本应随代码评审的契约（6 个 `%s`）推到运行时 fail-soft。`PROMPT_FORMAT` 常量紧邻六个字段，契约重新可见；
- **yml 保留短标量**（default-reason / fallback-reason）：决策元数据（配置值）而非长文本文档；
- **分段模板而非整段单模板**：人格段/记忆段需按内容有无条件省略，单模板无法表达；占位符替换而非模板引擎：只有简单替换，引入引擎过重；
- **前端不预填人格兜底**：后端 `normalizeFor` 已实现等价的空字段归一化，前端预填只会把英文兜底副本送进 payload、遮蔽 `runtime.default-personality`，属于第二份事实源越权；坐标上限从 dashboard 已返回的 `worldConfig` 解析，零新端点；
- **保留 Java 兜底默认值**：单测与纯代码路径不依赖 Spring 装配即可运行；代价是 Java/yml 两份需靠注释约束一致（已在两处文件头声明"yml 是主源"）。

## 影响

**正面**
- 行为调整（提示词、阈值、尺寸、居民人格）改配置即可，无需改代码重编译；
- 前后端一致性由 API 保证而非人工同步；
- `WorldConfig.validate()` 新增默认居民非空名校验，坏配置启动即失败。

**代价与注意**
- `PersonalityProfile.PROMPT_FORMAT` 恰好需要 6 个 `%s`（对应人格六字段），配错会在运行时抛 `MissingFormatArgumentException` → `decideAction` 降级为随机决策（fail-soft）；契约常量与字段同文件可见，随代码评审；
- 提示词段间排版由代码负责：新增模板段需在 `LLMBrain.buildPrompt` 同步拼接顺序与间距，文件不表达段间关系；
- 外置文件与 yml 一样**需重启才生效**（启动期加载），没有热更新收益——外置的全部意义是"免重编译 + 文档化编辑/diff"；
- 构造器签名变化（均保留旧重载）：`LLMBrain(ChatModel, PromptProperties, RuntimeProperties[, PromptRegistry])`、`TickSchedule(..., RuntimeProperties)`、`AgentRuntime(..., RuntimeProperties)`、`ConvergenceMonitor(..., RuntimeProperties)`、`EventBus(RuntimeProperties)`、`WorldEngine(..., WorldConfig, RuntimeProperties)`、`DashboardController(..., DashboardProperties, RuntimeProperties)`、`WorldState` 新增 4 参构造；
- API 变化（向后兼容）：`worldConfig.defaultResidents[].personality` 为新增字段；
- 后端 Java 默认值与 yml 默认值的一致性已机制化：`ConfigDefaultsConsistencyTest` 用 Binder 在容器外绑定 yml 后与 `defaults()` 逐字段比对，并断言关键键存在（防拼写错误被 Binder 静默忽略后"看似一致"）；
- 前端 `config.ts` 的 `worldSize` 兜底与后端默认值仍是约定一致性（跨栈无机制校验，靠注释）。

## 验证

- `mvnw test`：85 个测试全部通过（含 `contextLoads`、`PromptRegistryTest` 的加载/裁剪/fail-fast/REQUIRED 清单契约——追加"JSON 示例不得硬编码钳制幅度"断言，及 `ConfigDefaultsConsistencyTest` 的 yml/Java 默认值逐字段比对）；
- jshell 字节级比对：从文件+常量按 `LLMBrain.buildPrompt` 同算法装配的提示词，与重构前硬编码版本在"含人格+记忆"与"均省略"两种变体下 `equals` 均为 `true`；
- 前端 `tsc -b` 零错误；oxlint 无新增警告。