# Spec：Perception

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 给行为程序一个**只读**的世界视图，让"看得到的事"由确定性分支处理，看不到的才问 LLM。
- 把各种"感知不到"统一成同一个可判定的值（`#f`），使 DSL 的 `if` 可以直接组合。
- 保证同一 tick 内所有 Agent 基于同一份世界快照决策。

## 功能范围

### 包含范围

1. 五个感知查询的语义与返回约定。
2. 感知半径的来源与越界判定。
3. 快照注入时机与确定性保证。
4. Java `null` 到 `#f` 的边界转换规则。
5. 感知原语的真值约定。

不包含：感知原语在 DSL 中的语法（见 [behavior-dsl.md](behavior-dsl.md)）、世界状态本身的构造（见 [architecture.md](../architecture.md)）。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| self | Agent | 感知主体，决定距离与方向的基准 |
| worldState | WorldState | 本 tick 的世界快照（只读） |
| perceptionRange | int | 感知半径（曼哈顿距离），来自 `perception-range` |
| other | AgentRefValue | 查询目标，由行为程序传入 |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| self | AgentRefValue | 自身引用，恒存在 |
| distTo | int | 曼哈顿距离；越界/未知为 `Integer.MAX_VALUE` 哨兵 |
| nameOf | String | 对方名称；未知为 `"?"` |
| closestOther | AgentRefValue \| null | 半径内最近的其他 Agent；无则 null |
| directionOf | String \| null | north/east/south/west；同格或越界为 null |

Java 层的 `null` 与哨兵在求值器边界统一转成 S 栈上的值（见「核心行为 → 边界转换」）。

## 核心行为

### 接口

```java
public interface Perception {
    AgentRefValue self();
    int           distTo(AgentRefValue other);
    String        nameOf(AgentRefValue other);
    AgentRefValue closestOther();
    String        directionOf(AgentRefValue other);
}
```

实现：`WorldPerception.of(agent, worldState, perceptionRange)`——包装一次快照，全部查询只读。

### 感知半径

- 半径来自 `virtual-ai-box.runtime.perception-range`，由构造注入，**不是代码常量**。
- 超出半径视为"感知不到"：`distTo` 返回 `Integer.MAX_VALUE`，`closestOther` 跳过该对象。
- `directionOf` 按主轴判定（|dx| ≥ |dy| → east/west，否则 south/north）；同格或越界 → null。

### 快照与注入时机

- 每个 tick 开头由 `AgentRuntime.tick(worldState)` 注入一次 `WorldPerception.of(...)`。
- **同一 tick 内所有 Agent 看到同一个 WorldState**：无顺序假象；本 tick 内 A 的移动不影响 B 本 tick 的感知，下个 tick 才生效。
- 每个 Agent 独占一台 `WorldOpEvaluator`，无共享、无竞态。

### 边界转换（null 不出机器）

| 原语 | Java 返回 | S 栈上的值 |
|---|---|---|
| `(self)` | AgentRefValue | AgentRefValue |
| `(closest)` | null（无人） | `#f`（BoolValue(false)） |
| `(dist-to t)` | MAX_VALUE / 未知 | `#f` |
| `(name-of t)` | `"?"`（未知） | StringValue("?") |
| `(direction-of t)` | null（同格/越界） | `#f` |
| 非 AgentRef 实参 | — | 构造"查无此人"引用 → 查询返回 null/哨兵 → `#f` |

DSL 层只看到 `#f`，不存在"Java null 出现在 S 栈上"的路径。

### 真值约定

仅 `#f` 为假，`0` / `""` / AgentRef 等一切值皆真（Scheme 风格）。这使 `(closest)` 在"无对象"时返回的 `#f` 可直接用于 `if`：

```text
plan = if (closest) then (speak (closest) "我看到你了") else { remember "goal" "向前探索"; move dx dy }
```

## 约束与不变量

1. **只读**：感知只把值压入 S 栈，不产生 Effect、不修改世界。
2. **null 不出机器**：Java 层允许 null，SECD Value 层不允许。
3. **同 tick 快照一致**：所有 Agent 共用同一份 WorldState。
4. **半径唯一来源**：半径只能来自配置，不得在实现里另设常量。
5. **无副作用可测**：感知查询的结果只依赖 (self, worldState, range) 三者。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| 未注入 Perception 就调用感知原语 | fail-fast 抛 `IllegalStateException`（`perceptionRequired`），属装配错误，不降级 | 当前 Agent 的 tick，冒泡 |
| 传入非 AgentRef 实参 | 构造"查无此人"引用，查询返回 null/哨兵 → `#f` | 单次调用 |
| 目标已离开世界 | 同"未知"处理：`name-of` → `"?"`，`dist-to`/`direction-of` → `#f` | 单次调用 |

## 验收标准

1. `(closest)` 在无人时返回 `#f`，`if` 走 else 分支。
2. 距离恰好等于半径时可见，超过一格即不可见。
3. 同一 tick 内，A 移动后 B 的 `dist-to a` 仍返回移动前的值。
4. 任何感知原语都不会产生 Effect，也不会改变 World。
5. 修改 `perception-range` 后无需改代码，可见范围随之变化。

## 相关文档

- 相关 ADR：[ADR-0003](../decisions/ADR-0003-effect-isolation.md)、[ADR-0005](../decisions/ADR-0005-config-extraction.md)
- 相关 Spec：[Agent Runtime](agent-runtime.md)、[Behavior DSL](behavior-dsl.md)、[Effect](effect.md)
- 相关 Architecture：[architecture.md](../architecture.md)

## 待解决问题

- `observe` 原语（历史 DSL 草案中的名字）未实现，已由本 spec 的五个查询取代；写入 `.lambda` 会在加载期报"未知名称"。
