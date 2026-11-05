# Consistency Audit：文档-代码问题台账（快照：2026-09-10）

> 本文件记录本轮项目考古（Project Archaeology）的全部发现，含 Spec/Code/Test 对照、冲突清单与处置状态。
> 处置原则：**只报告、不擅自修改代码**；需你决策的项以 [NEEDS_DECISION] 标出。
> 基线事实：已通读 79 个 Java 源文件、ANTLR grammar、6 个测试类、3 份原文档、surefire 测试记录（2026-08-22）、frontend 结构。
> 本文件是问题登记和历史审计快照，不是当前架构规范；当前文档权威关系见 [`docs/README.md`](README.md)。所有 UNKNOWN 状态必须在重新执行验证后更新。

## 使用说明

- 新发现的问题追加到对应章节，包含发现日期、影响范围、复现或验证方式和处理状态。
- 问题关闭时保留原始发现，补充关闭日期、变更位置和验证结果，不删除审计历史。
- 已经确认属于历史状态的内容保留在本文档或 `archive/`，不得复制到 `architecture.md` 作为当前事实。

## 1. Spec / Code / Test 对照

| 功能 | Spec（frozen） | Code | Test（8/22 surefire） | 状态 |
|---|---|---|---|---|
| P0 SECD 移植 | §14 P0 | `secd/*`（26 类） | SECDTest 28 例全过 | CONSISTENT |
| P1 行为运行时 | §14 P1 | `mind/AgentRuntime` `effect/` `executor/` | AgentRuntimeTest **3/4 失败** | ⚠ 需复核（见 §3.4） |
| P2 中断/恢复 | §14 P2 | interrupt/runHandler/stepWithResume | interruptResumesMainPlanViaDStack **失败** | ⚠ 需复核（见 §3.4） |
| P3 收敛检测 | §14 P3 | `ConvergenceMonitor` | ConvergenceMonitorTest 4 例全过 | CONSISTENT |
| P4 可视化 | §14 P4 | DashboardController + frontend | 无前端测试 | CONSISTENT（功能侧） |
| P5 DSL | §14 P5 | `behaviordsl/` + g4 | BehaviorCompilerTest 16 例全过 | CONSISTENT |
| P6 感知/if | §17 | Perception/InstIfThenElse/比较/方向 | SECDTest P6 14 例 + CompilerTest P6 7 例全过 | CONSISTENT |
| 应用上下文 | — | Spring 装配 | contextLoads **失败** | ⚠ 环境疑点（见 §3.4） |

## 2. 组件状态判定（考古结论）

| 组件/设计 | 判定 | 依据 |
|---|---|---|
| SECD / Effect / Mind / Perception / DSL / Monitor / EventBus / Memory / Tick 全部运行时组件 | CURRENT | 代码存在且被测试覆盖（P5/P6 测试全过） |
| ObserveEffect | DEPRECATED | frozen §7 曾列；§17.1 明确废弃；代码不存在 |
| "Agent = SECD 抽象机"定位 | DEPRECATED | frozen §2 否定；改为"Agent 拥有 SECD 运行时"（ADR-0001） |
| and/or/not、observe 原语、感知范围可配置 | PLANNED | frozen §17.4（P6.1）；代码未实现 |
| 社会关系图/反事实推演/行为进化/世界回放 | PLANNED | frozen §16；代码未实现 |
| GridTest（`grid/` 包） | EXPERIMENTAL/LEGACY | 无文档定位，测试在用 |
| WorldEngine.init 的 Agent 创建 | 被注释 | 当前代码无 Agent 初始化路径（见 §3.3） |

## 3. 冲突与问题清单

### 3.1 [SPEC_CODE_CONFLICT] 悬空引用：冻结文档声称存在不存在的文档
- **位置**：`archive/design-v0.3-frozen.md` 头部（"最新设计见 docs/architecture.md + docs/spec/*"）。
- **事实**：这两处路径从未存在。冻结文档自述"已被取代"，但取代者此前从未建立。
- **处置**：2026-09-10 已建立 `architecture.md` + `spec/`（本次重构），悬空引用由新体系补位。冻结文档归档，不再修改。

### 3.2 [SPEC_CODE_CONFLICT] 冻结文档内部状态标注矛盾
- **P4**：§14 表格 = "已完成，用户测试通过"；附2 = "代码完成，待测试"。**矛盾**。
- **P6**：§14 表格/§17 = "待实现"；附2 = "代码完成，待测试"。**矛盾**（附2 晚于 §17，实际代码已实现且测试通过）。
- **P2**：§14 表格中性；附2 = "已完成，用户测试通过"。
- **处置**：`changelog.md` 已如实并列标注 ⚠。不推断哪份"对"。

### 3.3 [RESOLVED] addAgent 接口与默认居民初始化（2026-10-01）
- 原问题：`DashboardController.addAgent()` 无返回值，`WorldEngine.init()` 的默认居民创建被注释，前后端没有可用的居民初始化入口。
- 当前处理：`WorldEngine` 创建默认居民；`createAndAddAgent()` 统一初始化记忆、加入世界并注册 EventBus；`DashboardController.addAgent()` 接受名称和坐标并返回统一世界/居民结构。
- 验证代码：`WorldEngineTest`、`DashboardControllerTest`。测试结果由本地执行后回填。

### 3.4 [RESOLVED] 测试状态悬置（2026-10-01）
- 2026-08-22 22:11 surefire：AgentRuntimeTest 3 失败（firstTickProducesRememberThenMove / planPersistsAcrossTicksAndReloads / interruptResumesMainPlanViaDStack）；contextLoads 失败。
- 22:17-22:35 多个类被重新编译（AgentRuntimeTest/DefaultPlanCompiler/MindController/LLMBrain），测试未重跑。
- 当前验证：Java 全量测试 75 个全部通过；前端 `npm run build` 通过。
- 原始 2026-08-22 失败记录保留为历史背景，不再作为当前状态依据。

### 3.5 [COMMENT_CONFLICT] 测试注释过时
- `AgentRuntimeTest` 注释声称 "PATH_LENGTH 已是 1（默认计划单步耗尽）"；实际 `DefaultPlanCompiler.PATH_LENGTH = 3`（与附2 P1 记录"move×3 路径、每 3 tick 咨询一次 Oracle"一致）。**注释与代码不符**。

### 3.6 [DOC_REF_CONFLICT] 文档引用名/旧名残留
- `BehaviorDSL.g4` 头注引用 `docs/secd-fusion-design.md`；实际文件名 `design-v0.3-frozen.md`（归档后为 `docs/archive/design-v0.3-frozen.md`）。
- frozen §12 包结构图：`dsl/` 实际为 `behaviordsl/`；`WorldOpExecutor` 实际为 `WorldOpEvaluator`；`AgentMind`/`AgentRuntime` 二选一已定为后者。
- frozen §10.1 语法列 `observe` 原语 → 已被 §17 感知原语集取代（g4 注释明确未实现，P6.1 计划）。
- **处置**：新 spec 已用真实名称（`spec/behavior-dsl.md`、`spec/perception.md`）；旧文档归档不改。

### 3.7 [DOC_STALE] README 严重过时（已修复）
- 旧 README 仅描述 vab1.0/vab2.0（"LLMBrain 每 tick 决策"、API `/test` `/state` `/step`），与当前架构（SECD/Effect/DSL、`/api/dashboard/*`）完全脱节。
- **处置**：2026-09-10 已重写为启动/使用指南。

### 3.8 [DESIGN_CONCERN] agent.decided 兼容事件（不处理，供决策）
- `EffectExecutor.executeMove` 每次移动发布 `AgentDecidedEvent`（`llmDecision=true`），注释自述"兼容旧决策事件"。
- 张力：当前心智系统不再直接做 LLM 决策（ADR-0002），事件语义与"LLM 仅作 Oracle"定位不一致。
- [NEEDS_DECISION]：保留（当前选择）/ 去除 / 改为 `agent.acted` 等中性语义。

### 3.9 [UNKNOWN] 其他待确认项
- `VirtualAiBoxApplicationTests` 失败根因未深挖（疑似 secrets.yml api-key 环境，该文件被 .gitignore 排除，未读内容）。
- 前端 `addAgent` 表单和刷新链路已补充，接口行为仍需本地联调确认。
- 本机 `JAVA_HOME` 为 JDK 17，pom 要求 Java 21 —— 命令行无法直接编译（IDE 内可能用其他 JDK）；记录环境事实，不影响本文档。

## 4. 处置对照（本次已完成）

| 处置 | 项 |
|---|---|
| 已归档（不修改） | frozen 文档 → `archive/design-v0.3-frozen.md`；运行日志 → `archive/logs.md` |
| 已并入 ADR 后移除 | 原 `docs/my_opinion.md`（内容并入 ADR-0001/0002/0003 Context/Alternatives） |
| 已重写 | `README.md` → 启动/使用指南 |
| 已建立 | `architecture.md`（SSOT）、`spec/`×4、`decisions/`×4、`changelog.md`、本文档 |
| 保留现状不处理 | §3.4（测试复核）、§3.5（注释）、§3.8（decided 事件） |
| 已处理待验证 | §3.3（居民生命周期与 addAgent）、§3.9 中的前端 addAgent 链路 |

## 5. 后续回填点

- 若你重跑测试：回填 §1 对照表与 §3.4。
- 若你决策 §3.8：更新 `spec/effect.md` §6 与本文档。
- 若新增架构决策：新建 `decisions/ADR-0005+` 并更新 `architecture.md` §7 索引。
