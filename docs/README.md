# VirtualAIBox 文档体系

本目录按**信息类型**管理项目文档，文档必须服从代码与测试的当前事实，不以历史设计或计划内容冒充已实现能力。

## 文档分层

| 信息类型 | 文档 | 唯一职责 | 更新时机 |
|---|---|---|---|
| 项目定位 | [vision.md](vision.md) | 说明项目为什么存在、解决什么问题、明确边界 | 产品方向或项目边界变化时 |
| 当前架构 | [architecture.md](architecture.md) | 描述当前真实存在的组件、数据流、不变量和代码位置 | 组件职责、数据流或核心不变量变化时 |
| 执行链路 | [logic-walkthrough.md](logic-walkthrough.md) | 按一次真实运行追踪调用链，并覆盖类/方法职责、时序边界和已知缺口 | 主流程、tick 顺序或公共方法职责变化时 |
| 系统契约 | [spec/](spec/) | 描述某个子系统的输入、输出、语义、限制和验收条件 | 契约或外部可观察行为变化时 |
| 架构决策 | [decisions/](decisions/) | 记录当初为什么做出重要选择、替代方案和后果 | 做出不可逆或影响多个模块的决策时，只增不改历史决策 |
| 开发规范 | [development/](development/) | 规定 Agent 与人如何协作（[AI_WORKFLOW.md](development/AI_WORKFLOW.md)）、个人掌握程度如何记录（[KNOWLEDGE_MAP.md](development/KNOWLEDGE_MAP.md)） | 协作流程、权限边界或知识状态变化时 |

历史文档，不被引用，仅供参考。

| 位置 | 职责 | 更新时机 |
|---|---|---|
| [archive/](archive/) | 历史文档（只读）：已冻结的设计与运行日志，不能证明当前行为 | 不再更新 |

## 目录结构

```text
docs/
├── README.md                  本文：文档地图、权威关系、维护规则
├── vision.md                  项目定位与边界
├── architecture.md            当前架构 SSOT
├── logic-walkthrough.md       执行链路与类职责审计
├── spec/                      子系统详情
├── decisions/                 决策记录
├── development/
│   ├── AI_WORKFLOW.md         Agent 七阶段流程 + 权限边界 + 文档更新映射
│   └── KNOWLEDGE_MAP.md       个人知识地图（勾选权归你）
└── archive/                   历史文档（只读）
```

## 当前权威关系

发生冲突时按以下顺序判断：

1. 当前代码和当前测试结果；
2. [architecture.md](architecture.md) 的当前架构描述；
3. 对应的 [spec/](spec/) 契约；
4. 已接受的 [decisions/](decisions/)；
5. [vision.md](vision.md) 的项目边界；

## 一次代码变更需要更新什么

- 修改行为语义、输入输出或不变量：同步更新对应 `spec/`，并补充或调整测试。
- 引入新的跨模块架构选择：新增 ADR 到 `decisions/`，并在 `architecture.md` §7 决策索引中登记。
- 修改主流程或 tick 顺序：同步更新 `logic-walkthrough.md` §1 与 `spec/agent-runtime.md`。
- 修复历史审计中记录的问题：先重新验证，再将当前事实更新到 `architecture.md` 或对应 `spec/`；台账中保留原始发现并标记关闭日期。
- 修改公开 REST 接口：同时更新 `spec/api.md`、后端响应、前端类型和测试（`spec/api.md` 目前仅覆盖 7 个端点，补全前以代码为准）。
- 修改协作流程或权限边界：更新 `development/AI_WORKFLOW.md`。

## 维护规则

- `vision` 只回答是什么、给谁用和边界；`architecture` 只描述当前系统组成和约束；`spec` 只描述当前契约。
- `decisions` 只记录为什么做出重要选择，已接受决策只增不改；若后来推翻，新增 ADR 并注明替代关系。
- 文档完成或被替代后移动到 `archive/`，不要直接删除仍有追溯价值的内容。
