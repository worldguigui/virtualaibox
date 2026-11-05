# Spec：Dashboard API

> 状态：CURRENT。定义世界状态和居民展示相关的 REST 接口契约。
> 代码实现：`src/main/java/com/own/virtualaibox/controller/DashboardController.java`。
> 前端调用：`frontend/src/api/dashboard.ts`。

## 1. 统一世界摘要

所有返回世界与居民集合的接口使用以下结构：

```json
{
  "tick": 0,
  "world": {
    "width": 37,
    "height": 37,
    "agentCount": 2
  },
  "agents": []
}
```

`world.agentCount` 必须与 `agents.length` 一致。居民列表中的每项至少包含：

- `id`、`name`、`x`、`y`
- `personality`（角色、性情、表达方式、动机、价值和知识边界）
- `active`、`eventHistorySize`
- `memoryStats`、`memorySummary`、`recentMemories`
- `mind`（没有运行时则为 `null`）

## 2. 看板查询

```text
GET /api/dashboard?eventLimit=30&memoryLimit=8
```

除统一世界摘要外，还返回：

- `events`
- `metrics`
- `convergence`
- `capabilities`

`eventLimit` 控制返回事件数量，`memoryLimit` 控制每个居民返回的最近记忆数量。

## 3. 推进世界

```text
GET /api/dashboard/step
```

推进一个 tick 后返回统一世界摘要。返回的 `agents` 使用与 `/api/dashboard` 相同的完整居民字段，不能只返回位置摘要。

## 4. 查询当前状态

```text
GET /api/dashboard/state
```

返回当前 `tick`、`world` 和完整居民列表。状态查询不推进世界。

## 5. 创建居民

```text
POST /api/dashboard/addAgent
Content-Type: application/json

{
  "name": "Clara",
  "x": 4,
  "y": 9
}
```

请求体可以为空，字段规则如下：

- `name` 为空时生成唯一默认名称；
- `x`、`y` 为空时由世界引擎分配位置；
- 坐标必须满足 `0 <= x < width`、`0 <= y < height`；
- 居民名称不能与已有名称重复，忽略大小写；
- 新居民必须获得独立记忆管理器并注册到 EventBus。

成功响应包含：

```json
{
  "tick": 0,
  "agent": {},
  "world": {},
  "agents": []
}
```

非法名称、坐标或重复 ID 返回 HTTP 400，并包含：

```json
{
  "error": "具体错误原因"
}
```

## 6. 前后端一致性要求

- 前端 `WorldInfo` 与后端 `world` 字段保持一致。
- 前端 `Agent` 类型与 `/dashboard`、`/state`、`/step`、`/addAgent` 中的居民结构保持一致。
- 创建居民成功后，前端必须重新查询 dashboard，使地图、居民列表、记忆和心智状态同步更新。
- 任何新增世界或居民字段，必须同时更新本文件、后端响应、前端类型和对应测试。
