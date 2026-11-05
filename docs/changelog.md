# Changelog：VirtualAIBox 演进记录

> 状态：CURRENT（只记录历史演进，不描述当前架构；当前架构见 `architecture.md`）。
> 权威来源：`archive/design-v0.3-frozen.md`（附1 文档历史 + 附2 实现记录）、README 版本记录、2026-08-22 surefire 测试记录。
> 阶段状态词保留原始标注（如"代码完成，待测试"）；方括号内为本轮考古补充的实际测试记录，与原始标注不一致处以 ⚠ 标出。

## 前 SECD 时代（vab1.0 / vab2.0）

- **vab1.0**：基础多智能体世界。Spring Boot 3.5 + React + TS；LLMBrain **每 tick 决策**（全量世界状态提交 LLM 解析 JSON）；/test、/state、/step API；37×37 网格。
- **vab2.0**：引入 AgentMemory（认知记忆）与简单计划语义。LLM 仍是决策主体。无 SECD、无 Effect、无 DSL。
- ⚠ README 仍停留在该时代（2026-09-10 已重写为启动指南）。

## v0.1（P0 完成，用户测试通过）

- **P0 SECD 移植**：语义纯移植 `com.own.virtualaibox.secd`（MachineState/SECD/DumpFrame/值/指令），行为与语义参考实现 lambdaexpr 一致。
- 结构性改造（非行为变化）：state 与 driver 分离；`step()/run()` 返回 `List<Effect>`（纯算术恒空）；`OpEvaluator` 接口抽出算术分发。
- 裁剪：去掉 ANTLR / Swing / `LamValue` / `main()` 再解析循环。
- 单测：SECDTest（16 例）。
- 文档：v0.1 定稿。

## v0.2（P1-P4）

- **P1 行为运行时**（附2 标注"代码完成，待测试"）：AgentRuntime（每 Agent 一台 SECD，tick 单步预算内推进、产出一个世界动作即停、计划耗尽重编译）；WorldOpEvaluator（世界原语柯里化，只产 Effect）；Effect 层（Move/Speak/Remember/LLMRequest）；EffectExecutor；MindController；DefaultPlanCompiler（LLM 仅计划耗尽时作 Oracle 定方向，`[remember goal; move×3]`，每 3 tick 咨询一次）；TickSchedule 集成 5 阶段。单测 AgentRuntimeTest（3 例，FakeBrain）。
- **P2 中断/恢复**（附2 标注"已完成，用户测试通过"）：D 栈中断/恢复（interrupt/runHandler/stepWithResume）；onMeet 对话以闭包应用表达；interactionPhase 相遇检测 + activeMeetings 去抖；首次相遇触发对话。单测 interruptResumesMainPlanViaDStack。
- **P3 收敛检测**（附2 标注"代码完成，待测试"）：ConvergenceMonitor（边沿触发四类检测：AgentStuck/AgentLoop(position-cycle)/WorldConverged/AgentLoop(d-stack)）；Phase 4.5 接入。单测 ConvergenceMonitorTest（4 例）。
- **P4 可视化**（附2 标注"代码完成，待测试"；§14 表格标注"已完成，用户测试通过" —— 文档内部标注不一致 ⚠）：AgentRuntime.mindSummary（S/E/C/D 摘要）；DashboardController 暴露 mind + convergence；前端 MindViewer（2×2 寄存器卡 + 状态徽标 + D 栈深度条）、收敛指示器、三栏布局。

## v0.3（P5-P6 + 评审修订）

- 依据审查意见（原 docs/my_opinion.md）修订：**"Agent = SECD 抽象机"定位改为"Agent 拥有 SECD 行为运行时"**；增加 Effect 层；LLM 定位为 Oracle。对应 ADR-0001/0002/0003/0004。
- **P5 配置化 DSL**（附2 标注"代码完成，待测试"）：解析器改为 ANTLR 4.13.2（BehaviorDSL.g4）；应用变参左结合 `(f a b)`；plan 契约（内联、引用运行时 dx/dy，禁止 λdx.λdy.）；BehaviorCompiler/BehaviorProgram/BehaviorRegistry；`resources/agents/default.lambda`；runHandler 修正（handler 闭包续体需继续求值至 D 栈清空）。单测 BehaviorCompilerTest。
- **P6 感知与条件**（附2 标注"代码完成，待测试"；§14 表格/§17 标注"待实现" —— 文档内部标注不一致 ⚠）：BoolValue（#t/#f，Scheme 真值）；InstIfThenElse（C 栈同构）；比较原语 eq/lt/gt/le/ge；Perception/WorldPerception（只读、PERCEPTION_RANGE=5、null 不出机器边界）；方向移动（move north 与 move dx dy 统一 MoveEffect）；g4 v0.2（callArg*/if/方向 token/分隔符可省略）。单测：SECDTest 增 14 例、BehaviorCompilerTest 增 7 例。
- 文档 v0.3 定稿后**冻结**：`docs/secd-fusion-design-frozen.md`（自述"已冻结，只做历史回顾，不进行任何更新"），2026-09-10 归档至 `docs/archive/design-v0.3-frozen.md`。

## 2026-08-22 测试记录（考古补充）

- surefire 记录显示当时运行结果：
  - SECDTest：28 例全过（P0 16 例 + P6 14 例 → 已包含 P6）。
  - BehaviorCompilerTest：16 例全过（P5 9 例 + P6 7 例）。
  - ConvergenceMonitorTest：4 例全过。
  - **AgentRuntimeTest：4 例中 3 例失败**（firstTickProducesRememberThenMove / planPersistsAcrossTicksAndReloads / interruptResumesMainPlanViaDStack）⚠（P1/P2 相关，附2 标注与运行记录不符）。
  - **VirtualAiBoxApplicationTests.contextLoads：失败**（ApplicationContext 加载失败，疑似 secrets.yml api-key 环境问题）。
- 记录显示 22:17-22:35 间多个类（AgentRuntimeTest/DefaultPlanCompiler/MindController/LLMBrain 等）被重新编译，**测试未重跑** → 当前测试是否通过：[UNKNOWN]（不擅自推断，见 `architecture.md` §6.1）。

## 2026-09-10 文档重构（本次）

- 建立 SSOT 文档体系：`vision.md` / `architecture.md` / `spec/`（agent-runtime、behavior-dsl、perception、effect）/ `decisions/`（ADR-0001~0004）/ `changelog.md`（本文）/ `consistency-audit.md` / `archive/`。
- 冻结文档整体归档（不修改内容）；原审查意见并入 ADR 后移除；运行日志归档。
- 代码零改动（按约定：代码问题暂不处理）。

## 2026-10-01 居民生命周期与看板接口

- `WorldEngine` 初始化两个默认居民，并统一负责新居民的记忆初始化、加入世界和 EventBus 注册。
- 完成 `POST /api/dashboard/addAgent`，支持名称和坐标校验、默认名称、默认位置及非法请求响应。
- 统一 `/dashboard`、`/state`、`/step`、`/addAgent` 的世界摘要和居民展示结构。
- 新增 `spec/api.md`，记录世界状态、居民字段、创建请求和前后端一致性要求。
- 新增 `WorldEngineTest`、`DashboardControllerTest` 覆盖居民生命周期和看板接口场景；2026-10-01 Java 全量测试 75 个全部通过，前端构建通过。

## 2026-10-01 稳定人格模型

- 新增 `PersonalityProfile`，将居民角色、性情、表达方式、动机、价值和知识边界作为独立领域状态。
- 默认居民拥有不同且稳定的人格；新居民使用可重复的通用人格模板。
- Dashboard 和居民页面展示人格摘要，LLM Oracle 将人格作为只读决策上下文。
- 新增人格模型与提示词上下文测试；2026-10-01 Java 全量测试 80 个通过，前端构建通过。（⚠ 2026-11-01 校正：此处"80 个"查无出处，实际可查的 surefire 记录为 9 类 75 例，见 `architecture.md` §6.1。原始记录保留不改。）

## 2026-10-01 文档体系整理

- 新增 `docs/README.md`，统一说明文档职责、权威关系和持续维护规则。
- 保留当前定位、架构、系统契约、ADR、变更记录、问题台账和历史归档七类文档。
- 删除空的临时文件 `docs/temp/append-prd.md`；历史设计和运行日志继续保留在 `docs/archive/`，不参与当前规范。
- 将 `consistency-audit.md` 明确为问题台账快照，避免与 `architecture.md` 和 `spec/` 重复承担当前规范职责。

## 2026-10-01 实习路线与基线修正

- 明确项目整体阶段顺序：P0（编译与基线）→ P1（人格/记忆/WorldState/世界规则）→ P2（消息/关系/LLM 可靠性）→ P2（持久化/事件日志/回放）→ P3（前端居民管理与调试控制台）→ P4（经济/任务/战斗/进化玩法）。
- 先决条件顺序必须在代码开发前落地：
  1. 修正更新逻辑，统一使用 `agentId` 作为更新键，不再依赖名称；
  2. 补充世界配置层，抽离世界尺寸与默认配置；
  3. 将默认居民生成逻辑从硬编码改为配置驱动。
- 后续开发以“世界可运行、可调试、可复现”为主线，不在未稳定的基础上扩展高层玩法。

## 2026-11-01 文档体系第一阶段整改（T-001）

- 新增 `docs/development/AI_WORKFLOW.md`：Agent 七阶段工作流、7 条权限边界、事实纪律、文档更新映射表。
- 新增 `docs/development/KNOWLEDGE_MAP.md`：个人知识地图（未学习 / 正在理解 / 已理解 / 需重新确认），每条挂文档、代码、测试三处入口。
- 新增 `docs/tasks/`（README + active/ + completed/）：任务准入、命名、流转与模板；单次改动的设计方案统一写在任务文档第 5 节。
- 移除 `docs/design/`（与任务文档设计方案职责重叠，且项目期间从未产生实际设计文档）与空壳 `docs/temp/append-prd.md`（修正 2026-10-01 记录中"已删除"但实际仍存在的情况）。
- 修正既有文档缺陷：7 处 `adr/` 引用改为实际目录 `decisions/`；`architecture.md` 的 Value 数量 10 → 11；测试状态在 6 处统一为 **2026-10-24 / 9 类 75 例通过**（原为 80 / 75 / 未复核 / [UNKNOWN] 四种说法并存）；`docs/README.md` 不再声称 `consistency-audit.md` 已归档。
- 文档分类法从"六类 / 七类"的计数表述改为按信息类型命名。
- 代码零改动，未执行测试（无代码变更）；引用的测试结果为 2026-10-24 的 surefire 历史记录。
- 详细过程见 `docs/tasks/completed/T-001-docs-remediation.md`。
