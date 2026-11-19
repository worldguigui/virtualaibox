# ADR-0005: 配置参数提取

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 决策背景

- 目标：行为调整（提示词、阈值、世界尺寸、居民人格）应当**改配置即可生效**，不修改代码、不重新编译。
- 现状与之相悖：可调参数以硬编码形式散落在前后端 20+ 处，且同一事实存在多份副本。
- 典型症状：
  - 改世界尺寸后，提示词里仍硬编码宣称"37×37"；
  - 人格有两份互不相干的实现：Java `defaultFor`（中文）与前端 `buildDefaultProfile`（英文，两个组件各一份）；
  - `WorldPerception.PERCEPTION_RANGE` 注释自注"后续可配置"，长期未配。
- 后果：调整行为要改代码重编译；改一处漏一处，维护难度随参数增长。

## 决策方案

### 方案一：维持硬编码
1. **结论**：否决。改一处漏一处，且调整行为必须重编译。

### 方案二：全部收敛到 yml，代码不留默认值
1. **结论**：否决。纯代码路径与单测拿不到 Spring 装配，测试会被迫依赖容器。

### 方案三：yml 为唯一事实源，代码保留一份对齐兜底
1. 代码默认值仅服务 yml 缺失与无 Spring 的测试路径。
2. 两份默认值的一致性用机制化测试保证，而非人工纪律。
3. **结论**：采纳。

## 最终决策

- **`application.yml` 的 `virtual-ai-box.*` 为唯一事实源（SSOT）；行为调整改配置、不改代码、不重编译。**
- **配置边界准则（三问检验）**：① 谁改它（部署者还是开发者）；② 改它需要懂代码吗；③ 改它是否伴随结构性代码改动。**配置 = 不改逻辑结构就能变的值。** 世界尺寸、各类阈值/预算/默认值三问全指向配置；居民及人格属世界内容（需求方原始点名项），同样进配置。
- 五个配置段承接所有可调参数：

| 配置段 | 承接内容 | 主要消费方 |
|---|---|---|
| `virtual-ai-box.world` | 世界名/尺寸/规则/物品 + 默认居民含人格 | `WorldEngine` |
| `spring.virtual-clock` | 虚拟时钟刻度与起始时间 | `VirtualClock` |
| `virtual-ai-box.runtime` | 感知范围、相遇距离、事件历史上限、步数预算、默认计划长度、移动钳制、收敛阈值、通用兜底人格 | `MindController`、`TickSchedule`、`EventBus`、`ConvergenceMonitor`、`WorldEngine`、`LLMBrain` |
| `virtual-ai-box.prompt` | 仅短标量（默认/降级理由） | `LLMBrain` |
| `virtual-ai-box.dashboard` | REST 默认值（事件/记忆条数、轻量快照居民数） | `DashboardController` |

- **提示词外置边界**：规则文本外置为 `prompts/decision/intro.txt`、`task.txt`，脚手架段留代码常量；占位符由代码填充，人格段与记忆段内容为空时整段省略（拼接顺序固定）；世界尺寸取自本 tick 的 `WorldState`（`worldWidth`/`worldHeight`），不再硬编码。
- **人格三级解析**：显式传入的人格（空字段回落）→ 世界配置中同名默认居民 → `runtime.default-personality`。
- **前端一致性**：运行数据以后端 API 为准；`config.ts` 只集中 UI 兜底值；人格只裁剪不预填。

Q. 为什么代码里还留一份默认值，而不是只认 yml？<br/>
A. 纯代码路径（单测、无 Spring 装配）需要可用默认值。两份一致的代价用 `ConfigDefaultsConsistencyTest` 机制化承担：容器外用 Binder 绑定 yml 与 `defaults()` 逐字段比对，并断言关键键存在——防止拼写错误被 Binder 静默忽略后"看似一致"。

Q. 为什么提示词不整段外置，还要留脚手架在代码里？<br/>
A. 占位符语义与格式契约（`PROMPT_FORMAT` 的 6 个 `%s` 顺序）必须与字段定义同文件可见，随代码评审；外置的只有可独立修改的规则文本。

## 决策后果

- 行为调整（提示词、阈值、尺寸、居民人格）改配置即可，无需改代码重编译；前后端一致性由 API 保证，而非人工同步。
- 代价一：Java 与 yml 两份默认值需保持一致，靠机制测试而非纪律。
- 代价二：提示词段间排版由代码负责，新增模板段需同步 `buildPrompt` 的拼接顺序与间距，文件不表达段间关系。
- 代价三：外置文件与 yml 一样在启动期加载，**需重启才生效**，没有热更新收益。
- `PROMPT_FORMAT` 恰好需要 6 个 `%s`（对应人格六字段），配错会在运行时抛 `MissingFormatArgumentException` → `decideAction` 降级随机方向（fail-soft）。
- 前端 `config.ts` 的世界尺寸兜底与后端默认值仍是**约定一致**（跨栈无机制校验，靠注释）。
- 约束（不可放宽）：不得新增硬编码可调值；不得把提示词脚手架外置；不得让前端成为第二事实源（不预填人格、组件内不散落 `?? 37` 类兜底）。

## 相关文档

- 相关 ADR：[ADR-0002](ADR-0002-llm-oracle.md)、[ADR-0006](ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Perception](../spec/perception.md)、[Personality](../spec/personality.md)、[Dashboard API](../spec/api.md)、[Agent Runtime](../spec/agent-runtime.md)
- 相关 Architecture：[architecture.md](../architecture.md)
