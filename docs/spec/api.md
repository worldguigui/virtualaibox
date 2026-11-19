# Spec：Dashboard API

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 定义后端对外的 HTTP 契约，使前端与后端可以各自独立开发。
- 让"世界摘要"在所有接口里保持同一形状，前端只需要一种解析方式。
- 把可调默认值交给配置，而不是散落在 Controller 里。

## 功能范围

### 包含范围

1. 全部 20 个端点的路径与用途。
2. 统一世界摘要结构与居民字段。
3. 看板聚合、推进、状态、居民写入、世界配置的行为差异。
4. 错误响应约定。
5. REST 默认值对应的配置键。

不包含：小镇命令的领域语义（由 `WorldEngine` 承担）、前端渲染逻辑。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| eventLimit / memoryLimit | int（可空） | 条数上限，省略时取配置默认值 |
| AgentCreateRequest | JSON | `name`、`x`、`y`、`personality`，均可为空 |
| AgentUpdateRequest | JSON | 同上，字段为 null 表示不修改 |
| WorldConfig | JSON | 世界名、尺寸、规则、物品、默认居民 |
| 各 town 命令请求体 | JSON | 物品/消息/关系/任务/交易/战斗的领域参数 |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| 世界摘要 | JSON | `tick` + `world` + `agents` |
| 看板聚合 | JSON | 世界摘要 + `events`/`metrics`/`convergence`/`capabilities` |
| 运行态快照 | JSON | 资源实例、关系、消息、任务、居民 |
| 错误 | JSON | `{"error": "具体错误原因"}` |

## 核心行为

### 端点索引

所有端点挂在 `/api/dashboard` 下，共 20 个：

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/dashboard` | 看板聚合 |
| GET | `/api/dashboard/agents` | 居民列表 |
| POST | `/api/dashboard/addAgent` | 创建居民 |
| PUT | `/api/dashboard/agents/{agentId}` | 修改居民 |
| GET | `/api/dashboard/world` | 读取世界配置 |
| PUT | `/api/dashboard/world` | 原子替换世界配置 |
| GET | `/api/dashboard/events` | 最近事件 |
| GET | `/api/dashboard/metrics` | 运行指标 |
| GET | `/api/dashboard/step` | 推进一个 tick |
| GET | `/api/dashboard/state` | 当前状态轻量快照 |
| GET | `/api/dashboard/town/runtime` | 小镇运行态快照 |
| POST | `/api/dashboard/town/items/spawn` | 生成物品实例 |
| POST | `/api/dashboard/town/items/{itemId}/pickup` | 拾取物品 |
| POST | `/api/dashboard/town/messages` | 发送消息 |
| POST | `/api/dashboard/town/relations` | 调整社会关系 |
| POST | `/api/dashboard/town/quests` | 创建任务 |
| POST | `/api/dashboard/town/quests/{questId}/assign` | 分配任务 |
| POST | `/api/dashboard/town/quests/{questId}/complete` | 完成任务 |
| POST | `/api/dashboard/town/trades` | 交易 |
| POST | `/api/dashboard/town/combat` | 战斗 |

### 统一世界摘要

（数值为示例，实际取自世界配置）

```json
{
  "tick": 0,
  "world": { "width": 37, "height": 37, "agentCount": 2 },
  "agents": []
}
```

`world.agentCount` 必须与 `agents.length` 一致。居民项字段：`id`、`name`、`x`、`y`、`personality`、`active`、`eventHistorySize`、`memoryStats`、`memorySummary`、`recentMemories`、`mind`（无运行时则为 `null`）。

### 看板聚合

```text
GET /api/dashboard?eventLimit=30&memoryLimit=8
```

在世界摘要之外返回：

- `events`：条数由 `eventLimit` 控制；
- `metrics`：`eventSubscriberCount` / `eventHistorySize` / `agentCount` / `currentTick` / `serverTime`；
- `convergence`：收敛检测摘要；
- `capabilities`：能力声明列表。

`memoryLimit` 控制每个居民返回的最近记忆条数。

### 推进世界

```text
GET /api/dashboard/step
```

推进一个 tick 后返回世界摘要，`agents` 使用完整居民字段（记忆条数取 `agent-memory-limit`），不能只返回位置摘要。

### 当前状态（轻量快照）

```text
GET /api/dashboard/state
```

返回 `tick`、`world` 与**轻量居民快照**：居民数量受 `state-agents-limit` 限制，不是完整列表；需要完整列表用 `/agents` 或看板接口。该接口不推进世界。

### 居民写入

```text
POST /api/dashboard/addAgent
PUT  /api/dashboard/agents/{agentId}
```

请求体（创建时可为空）：`{ "name": "Clara", "x": 4, "y": 9, "personality": {...} }`

- `name` 为空时生成唯一默认名称；`x`/`y` 为空时由世界引擎分配；
- 坐标必须满足 `0 <= x < width`、`0 <= y < height`；
- 名称不能与已有居民重复（忽略大小写）；
- 新居民必须获得独立记忆管理器并注册到 EventBus；
- `personality` 可省略，按三级解析补齐（见 [personality.md](personality.md)）。

响应：`{ "tick": 0, "agent": {}, "world": {}, "agents": [] }`。

### 世界配置

- `GET /api/dashboard/world`：返回当前 `WorldConfig`。
- `PUT /api/dashboard/world`：原子替换，返回 `tick`、`world`、`worldConfig`、`agents`。

### 小镇运行态命令

所有 `town/*` 写操作返回 `runtime` 快照；`/town/runtime` 只读返回同一快照。命令的领域语义由 `WorldEngine` 承担，本 spec 只约定 HTTP 形态。

### 配置键

键名以本表为准，**默认值以 `application.yml` 为准**：

| 键 | 作用 |
|---|---|
| `virtual-ai-box.dashboard.default-event-limit` | 看板事件条数默认值 |
| `virtual-ai-box.dashboard.default-memory-limit` | 看板/居民列表的每居民记忆条数默认值 |
| `virtual-ai-box.dashboard.agent-memory-limit` | `step`/`addAgent`/`update` 携带的记忆条数 |
| `virtual-ai-box.dashboard.state-agents-limit` | `/state` 携带的居民数上限 |
| `virtual-ai-box.dashboard.events-default-limit` | `/events` 条数默认值 |

## 约束与不变量

1. **摘要一致**：任何返回居民集合的接口都遵循同一 `world` + `agents` 结构，`agentCount` 与 `agents.length` 一致。
2. **读写分离**：只有 `addAgent` / `updateAgent` / `PUT world` / `town/*` 会改变状态；`GET` 一律只读（`/step` 例外，它显式推进世界）。
3. **默认值来自配置**：`limit` 类参数的默认值只能取自 `virtual-ai-box.dashboard.*`，不得在 Controller 写死。
4. **生命周期唯一入口**：居民创建必须走 `WorldEngine`，不得在 Controller 拼装。
5. **字段同步**：新增世界或居民字段时，本文件、后端响应、前端类型、测试四处同时更新。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| 名称非法、坐标越界、名称重复 | HTTP 400 + `{"error": ...}` | 单次请求 |
| 请求体缺失（创建/修改允许） | 按全空字段处理，使用默认值与三级解析 | 单次请求 |
| 世界配置非法（`WorldConfig.validate()` 失败） | HTTP 400，配置不被替换（原子） | 单次请求 |
| Agent id 不存在（修改/命令引用） | 由领域层抛 `IllegalArgumentException` → 400 | 单次请求 |

## 验收标准

1. 任一返回居民集合的接口，`world.agentCount` 等于 `agents.length`。
2. `/state` 返回的居民数不超过 `state-agents-limit`，且不推进 tick。
3. `/step` 返回的居民包含完整字段（含 `mind` 与记忆摘要）。
4. 创建重名居民（不同大小写）返回 400，世界不变。
5. 修改 `dashboard.*` 配置后，省略 `limit` 的请求按新默认值返回。
6. `PUT /world` 失败时，旧配置仍然有效。

## 相关文档

- 相关 ADR：[ADR-0005](../decisions/ADR-0005-config-extraction.md)
- 相关 Spec：[Personality](personality.md)、[Agent Runtime](agent-runtime.md)、[Effect](effect.md)
- 相关 Architecture：[architecture.md](../architecture.md)
