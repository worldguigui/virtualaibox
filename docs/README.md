# VirtualAIBox 文档体系

本目录按**信息类型**管理项目文档，不用固定数量的"类别"来计数——每份文档只承担一个职责，一个事实只保留一个权威位置。文档必须服从代码与测试的当前事实，不以历史设计或计划内容冒充已实现能力。

## 文档分层

| 信息类型 | 文档 | 唯一职责 | 更新时机 |
|---|---|---|---|
| 项目定位 | [vision.md](vision.md) | 说明项目为什么存在、解决什么问题、明确边界 | 产品方向或项目边界变化时 |
| 当前架构 | [architecture.md](architecture.md) | 描述当前真实存在的组件、数据流、不变量和代码位置 | 组件职责、数据流或核心不变量变化时 |
| 执行链路 | [logic-walkthrough.md](logic-walkthrough.md) | 按一次真实运行追踪调用链，并覆盖类/方法职责、时序边界和已知缺口 | 主流程、tick 顺序或公共方法职责变化时 |
| 系统契约 | [spec/](spec/) | 描述某个子系统的输入、输出、语义、限制和验收条件 | 契约或外部可观察行为变化时 |
| 架构决策 | [decisions/](decisions/) | 记录当初为什么做出重要选择、替代方案和后果 | 做出不可逆或影响多个模块的决策时，只增不改历史决策 |
| 开发规范 | [development/](development/) | 规定 Agent 与人如何协作（[AI_WORKFLOW.md](development/AI_WORKFLOW.md)）、个人掌握程度如何记录（[KNOWLEDGE_MAP.md](development/KNOWLEDGE_MAP.md)） | 协作流程、权限边界或知识状态变化时 |
| 任务记录 | [tasks/](tasks/) | 记录一次改动怎么做、改了哪些文件、验证了什么（含设计方案）；完成后归档 | 符合 [tasks/README.md](tasks/README.md) 准入条件时 |
| 变更历史 | [changelog.md](changelog.md) | 记录已经发生的用户可感知变化和重要技术变更 | 完成一个可识别的变更或版本时 |

两个辅助位置，不参与"当前事实"的判定：

| 位置 | 职责 | 更新时机 |
|---|---|---|
| [consistency-audit.md](consistency-audit.md) | 问题台账：记录文档-代码不一致项及其处置状态。初始内容为 2026-09-10 审计快照，后续增量追加 | 问题状态变化且**已实际核验**时 |
| [archive/](archive/) | 历史文档（只读）：已冻结的设计与运行日志，不能证明当前行为 | 不再更新 |

## 目录结构

```text
docs/
├── README.md                  本文：文档地图、权威关系、维护规则
├── vision.md                  项目定位与边界
├── architecture.md            当前架构 SSOT
├── logic-walkthrough.md       执行链路与类职责审计
├── consistency-audit.md       问题台账（含 2026-09-10 审计快照）
├── changelog.md               演进历史（只追加）
├── spec/                      子系统契约（6 份）
├── decisions/                 ADR-0001 ~ 0004（只增不改）
├── development/
│   ├── AI_WORKFLOW.md         Agent 七阶段流程 + 权限边界 + 文档更新映射
│   └── KNOWLEDGE_MAP.md       个人知识地图（勾选权归你）
├── tasks/
│   ├── README.md              任务文档准入、命名、流转、模板
│   ├── active/                进行中的任务
│   └── completed/             已完成的任务（只归档，不删除）
└── archive/                   历史文档（只读）
```

## 当前权威关系

发生冲突时按以下顺序判断：

1. 当前代码和当前测试结果；
2. [architecture.md](architecture.md) 的当前架构描述；
3. 对应的 [spec/](spec/) 契约；
4. 已接受的 [decisions/](decisions/)；
5. [vision.md](vision.md) 的项目边界；
6. [changelog.md](changelog.md) 的历史记录；
7. [tasks/](tasks/) 中的设计方案——只表明当时打算怎么做，不能证明已实现；
8. [archive/](archive/) 与 [consistency-audit.md](consistency-audit.md) 中的材料不能证明当前行为：归档是历史，台账是待办。

`development/` 不参与事实判定：它规定"怎么做"，不描述"是什么"。

## 一次代码变更需要更新什么

- 修改实现但不改变外部契约：更新 `changelog.md`，必要时更新 `architecture.md` 的代码位置或组件职责。
- 修改行为语义、输入输出或不变量：同步更新对应 `spec/`，并补充或调整测试。
- 引入新的跨模块架构选择：新增 ADR 到 `decisions/`，并在 `architecture.md` §7 决策索引中登记。
- 修改主流程或 tick 顺序：同步更新 `logic-walkthrough.md` §1 与 `spec/agent-runtime.md`。
- 修复历史审计中记录的问题：先重新验证，再将当前事实更新到 `architecture.md` 或对应 `spec/`；台账中保留原始发现并标记关闭日期。
- 修改公开 REST 接口：同时更新 `spec/api.md`、后端响应、前端类型和测试（`spec/api.md` 目前仅覆盖 7 个端点，补全前以代码为准）。
- 修改协作流程或权限边界：更新 `development/AI_WORKFLOW.md`。
- 符合任务准入条件（见 `tasks/README.md` §1）：在 `tasks/active/` 建任务文档。
- 修改配置、部署、密钥或运行参数：补充运行手册（当前尚缺 `operations.md`）。

## 推荐的持续维护文档

现有文档已覆盖定位、架构、链路、契约、决策、规范、任务和历史。随着开发推进，建议按实际需要补充，**不要提前创建空白文件**：

- `spec/api.md`：已存在，但仅覆盖 7/20 个端点，需补全 `town/*`、世界配置与居民更新接口。
- `spec/data-model.md`：居民、人格、记忆、世界状态和持久化模型。
- `development/CODING_GUIDELINES.md`：命名、包结构、异常、日志、依赖约束。
- `development/TESTING_GUIDELINES.md`：测试分层、数据准备、Mock 原则、必测行为。
- `operations.md`：本地启动、环境变量、部署、备份和故障处理。
- `roadmap.md`：按优先级维护未完成能力，作为任务文档的上游计划。

这些文档只有在对应能力开始实现或需要稳定协作时再建立。

## 维护规则

- `vision` 只回答是什么、给谁用和边界；`architecture` 只描述当前系统组成和约束；`spec` 只描述当前契约。
- `decisions` 只记录为什么做出重要选择，已接受决策只增不改；若后来推翻，新增 ADR 并注明替代关系。
- `tasks` 只记录过程与验证，完成后归档；当前架构事实一律不回写进任务文档。
- `changelog` 只追加何时改了什么，不描述当前架构细节。
- 一个事实只保留一个权威位置，其他文档使用链接引用。
- 每个计划必须写明状态、依赖和验收条件，不能写成已完成能力。
- 每次变更至少执行相关测试；测试状态必须注明日期、命令和结果，没有出处的数字不写。
- 文档完成或被替代后移动到 `archive/`，不要直接删除仍有追溯价值的内容。
