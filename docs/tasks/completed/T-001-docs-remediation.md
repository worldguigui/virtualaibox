# T-001：文档体系第一阶段整改

- 状态：已完成
- 日期：2026-11-01 开始，2026-11-01 完成
- 涉及模块：仅 `docs/` 与根 `README.md`，**未改动任何 Java / DSL / 前端代码**

## 1. 背景

现有文档体系（20 份、2,069 行、134 KB）已具备项目定位、架构事实、执行链路、子系统契约、ADR、演进历史、问题台账和历史归档，但缺少三块支撑人机协作开发的能力：

1. 没有 Agent 协作规范 → Agent 接手任务时无流程可依、无权限边界；
2. 没有任务记录机制 → 已完成工作的"过程"全部丢失，只剩 `changelog.md` 的结果摘要；
3. 没有个人知识地图 → 无法区分"项目实现了"与"我已掌握"。

同时调查发现 6 处会直接误导的文档缺陷（断链、事实失真、声称已归档但未归档等）。

## 2. 目标

- 补齐 `development/`（协作规范 + 知识地图）与 `tasks/`（任务机制）两个目录；
- 修正现有文档之间的断链与事实矛盾，使其可被 Agent 直接信任；
- 不重写 `architecture.md` / `spec/` / `decisions/` / `archive/` 的任何技术内容。

## 3. 非目标

- 不修改业务代码、不改核心架构；
- 不拆分 `architecture.md`，不新增 `overview.md` 等重复架构文件；
- 不重写六份 Spec、四份 ADR；
- 不删除 `archive/design-v0.3-frozen.md`；
- 不补写 `spec/api.md` 缺失的 13 个端点（另立任务，见 §11）；
- 不要求用户立即学习 KNOWLEDGE_MAP 的任何条目。

## 4. 当前实现调查

调查覆盖 `docs/` 全部 20 份文档、`src/main/java` 83 个类、`DashboardController` 20 个端点、`target/surefire-reports` 9 份报告。关键发现：

| # | 问题 | 证据 |
|---|---|---|
| 1 | 7 处引用 `adr/`，实际目录名为 `decisions/` | `README.md:14,25,37,61`、`architecture.md:5`、`vision.md:53`、`design/README.md:5` |
| 2 | 测试状态 6 处 4 种说法 | `80`×3（architecture:112 / changelog:64 / personality:50）、`75`×1（changelog:57）、"未复核"（README:102）、`[UNKNOWN]`（agent-runtime:5） |
| 3 | 声称已归档、实际未归档 | `docs/README.md:31` 称 consistency-audit 已移入 archive，文件仍在根目录 |
| 4 | 声称已删除、实际仍存在 | `changelog.md:70` 称删除 `temp/append-prd.md`，文件仍在（24 字节） |
| 5 | 分类基数自相矛盾 | `docs/README.md:3` "六类"、同文件表 8 行、`changelog.md:69` "七类" |
| 6 | `Value（10 种）` 但同行列出 11 个 | `architecture.md:50`；`secd/value/` 实际 11 个实现类 |
| 7 | `spec/api.md` 仅覆盖 7/20 端点 | 控制器 20 个 `@*Mapping`，spec 覆盖 dashboard/step/state/agents/addAgent |

## 5. 设计方案

### 5.1 目录调整

- 新增 `docs/development/`（AI_WORKFLOW.md、KNOWLEDGE_MAP.md）；
- 新增 `docs/tasks/`（README.md、active/、completed/）；
- **删除 `docs/design/`**：与任务文档第 5 节「设计方案」职责重叠。经确认，项目至今未产生过一份真实设计文档（`design/README.md` 仅为 7 行职责说明），保留只会形成两套并存的设计入口；
- **删除 `docs/temp/`**：仅一个 24 字节空壳，且 `changelog.md` 已宣称删除。

### 5.2 分类法收敛

原体系用"六类"计数，加入两类后必然再打架。改为**按信息类型命名、不再计数的表述**：项目定位 / 当前架构 / 执行链路 / 系统契约 / 架构决策 / 开发规范 / 任务记录 / 变更历史，另有问题台账与历史归档两个辅助位置。

### 5.3 测试状态口径

统一为实测值：`2026-10-24 / .\mvnw.cmd test / 9 类 75 例全部通过`。依据是 `target/surefire-reports/` 9 份报告的汇总（SECDTest 28、BehaviorCompilerTest 16、WorldEngineTest 11、DashboardControllerTest 7、AgentRuntimeTest 4、ConvergenceMonitorTest 4、PersonalityProfileTest 3、JunitTest 1、VirtualAiBoxApplicationTests 1）。

### 5.4 KNOWLEDGE_MAP 知识点的代码核验

按"删除不存在或不适用"的原则，对初始草案做了 5 处修正：

| 草案表述 | 代码实际 | 处理 |
|---|---|---|
| `EventDomain` 抽象 | 不存在；只有 `DomainEvent` | 改为「DomainEvent 元数据模型」 |
| `Reasoning` 流程 | 无独立组件；为 `DefaultPlanCompiler` + `LLMBrain.decideAction` | 改为「计划编译 + Oracle 定向」 |
| Tick 异常处理机制 | 存在：`processTick` 整体 try/catch，仅 log，不回滚 | 保留，注明边界 |
| 事件异常处理机制 | 存在：监听器异常 catch + log，不传播不重试 | 保留，注明语义 |
| 短期与长期记忆关系 | 同一 MemoryStore 的两种查询口径；无晋升/遗忘 | 保留，晋升机制列入 Planned |

## 6. 修改范围

**新增**：`docs/development/AI_WORKFLOW.md`、`docs/development/KNOWLEDGE_MAP.md`、`docs/tasks/README.md`、`docs/tasks/active/.gitkeep`、`docs/tasks/completed/T-001-docs-remediation.md`（本文件）

**修改**：`docs/README.md`（分类表、权威顺序、断链、假声明、新增两类的职责与更新时机）、`docs/architecture.md`（断链 ×1、Value 数量、测试状态）、`docs/vision.md`（断链 ×1、文档链条）、`docs/spec/agent-runtime.md`（测试状态）、`docs/spec/personality.md`（测试状态）、根 `README.md`（文档地图、新增目录、测试状态）

**删除**：`docs/design/README.md` 及目录、`docs/temp/append-prd.md` 及目录

## 7. 验收标准

- [x] 现有核心文档和历史资料全部保留（`archive/` 两份未动）
- [x] `AI_WORKFLOW.md` 可独立指导一次标准开发任务（七阶段 + 权限边界 + 文档映射表）
- [x] `KNOWLEDGE_MAP.md` 可用于记录实际掌握情况（4 态 + 文档/代码/测试三处依据）
- [x] `tasks/` 有创建、执行、归档规则
- [x] `README.md`（根 + docs）能导航全部文档，无断链
- [x] 空壳文档得到处置（design/、temp/ 删除）
- [x] 未修改业务代码
- [x] 未凭空补充技术事实（新写入的每条代码事实均经 grep/read 核验）

## 8. 实施记录

2026-11-01：

1. 通读 `docs/` 全部 20 份文档；核对 83 个 Java 类、20 个端点、9 份 surefire 报告。
2. 就三处需决策项征询并确认：design/ 合并删除、测试状态改实测 75、根 `HELP.md` 本次不动。
3. 新建 `development/`、`tasks/` 及三个文件。
4. 改写 `docs/README.md`；定点修正 `architecture.md`、`vision.md`、两份 spec、根 `README.md`。
5. 删除 `design/`、`temp/` 并同步清理 9 处引用。
6. 用脚本核验全库 Markdown 内部链接。

## 9. 测试结果

**未执行测试。** 本次整改未改动任何代码，按 `AI_WORKFLOW.md` 阶段五不适用。

引用的基线为历史记录：`2026-10-24` 的 surefire 报告，9 类 75 例，`Failures: 0, Errors: 0, Skipped: 0`。该结果采集于本次整改之前，不代表本次改动后的状态（本次无代码改动，状态应保持）。

## 10. 知识沉淀

本次整改涉及的机制（供 `KNOWLEDGE_MAP.md` 参考，未勾选任何条目）：

- 第 1 节：Tick 五阶段与 `simulateTownTick` 的时序边界（`logic-walkthrough.md` §3.2 明确指出它在 `tick.ended` 之后）
- 第 3 节：EventBus 的监听器异常隔离（`EventBus.java:142-160`）
- 第 5 节：短期/长期记忆是查询口径差异，而非两套存储
- 第 6 节：DashboardController 实际 20 端点，`spec/api.md` 只覆盖 7 个

## 11. 遗留问题

1. **`spec/api.md` 缺 13 个端点**（全部 `town/*` 9 个、`PUT /agents/{agentId}`、`GET/PUT /world`、`GET /events`、`GET /metrics`）。建议立 `T-002`，并把根 `README.md` 的 API 表迁入 spec。
2. **根 `HELP.md`** 为 Spring 初始化模板残留，链接指向 Spring Boot 4.0.6（pom 实际 3.5.14）。本次按约定不动。
3. **`archive/logs.md` 首行被截断**（缺日期前缀）。已加注记说明，不补造内容。
4. **`docs/README.md` 原称 consistency-audit 已归档**：本次改为"保留在原地作为活台账"，但该文档的"持续维护 vs 历史快照"定位仍需你最终确认（当前 `consistency-audit.md` 头部自述为 2026-09-10 快照）。
