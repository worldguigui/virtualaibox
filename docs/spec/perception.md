# Spec：Perception（感知输入通道）

> 状态：CURRENT（P6 实现）。本文定义感知的当前契约。
> 相关：`architecture.md` 不变量 5/6/7/8/12；历史设计见 `archive/design-v0.3-frozen.md` §17.3。
> 注：Perception 当前作为 spec 契约维护；若日后被判定为重大架构取舍，再升级为 ADR（当前不建）。

## 1. 定位

Perception 是 Agent 行为程序可查询的**只读**世界视图（对应 Agent 组成中的"感知输入通道"）。

- 感知是**读**：把值压入 SECD S 栈，**不产生 Effect、不触碰世界**（ObserveEffect 已于 P6 废弃，见 ADR-0003）。
- 确定性感知分支 + LLM Oracle 兜底：能"看到"的用机器决策，看不到的才问 LLM。

## 2. 接口

```java
public interface Perception {
    AgentRefValue self();                          // 自身引用（恒存在）
    int          distTo(AgentRefValue other);      // 曼哈顿距离 |dx|+|dy|
    String       nameOf(AgentRefValue other);      // 对方名称（未知 → "?"）
    AgentRefValue closestOther();                  // 感知范围内最近的其他 Agent；无 → null
    String       directionOf(AgentRefValue other); // north/east/south/west；不在范围或同格 → null
}
```

实现：`WorldPerception.of(agent, worldState)`——包装一次 WorldState 快照，全部查询只读。

## 3. 感知范围

- `PERCEPTION_RANGE = 5`（曼哈顿距离）：超过该距离视为"感知不到"。
- `distTo` 超范围/未知 → `Integer.MAX_VALUE` 哨兵（由求值器转 `#f`）。
- `directionOf` 按主轴判定（|dx|≥|dy| → east/west，否则 south/north）；同格或超范围 → null。

## 4. 快照语义（确定性）

- 每个 tick 开头由 `AgentRuntime.tick(worldState)` 注入一次 `evaluator.setPerception(WorldPerception.of(agent, worldState))`。
- **同一 tick 内所有 Agent 看到同一个 WorldState 快照**：无顺序假象；本 tick 内 A 的移动不影响 B 本 tick 的感知（下个 tick 才生效）。
- 每个 Agent 独占一台 WorldOpEvaluator，无共享、无竞态。

## 5. null 不出机器边界

- Java 层（Perception 实现）允许用 `null` 表达"无"，但 **null 永不进入 SECD Value**。
- `WorldOpEvaluator` 在感知原语返回边界统一转换：

| 原语 | Java 返回 | S 栈上的值 |
|---|---|---|
| `(self)` | AgentRefValue | AgentRefValue |
| `(closest)` | null（无人） | `#f`（BoolValue(false)） |
| `(dist-to t)` | MAX_VALUE / 未知 | `#f` |
| `(name-of t)` | "?"（未知） | StringValue("?") |
| `(direction-of t)` | null（同格/超范围） | `#f` |
| 非 AgentRef 实参 | — | 构造"查无此人"引用 → 查询返回 null/哨兵 → `#f` |

DSL 层只看到 `#f`，不存在"Java null 出现在 S 栈上"的路径。

## 6. 感知原语与真值

感知原语（`self/closest/dist-to/name-of/direction-of`）只在一元上下文有效；无 Perception 注入时调用 → fail-fast 明确报错。

真值规则（Scheme 风格）：仅 `#f` 为假，`0`/`""`/AgentRef 等一切值皆真。这使 `(closest)` 在"无对象"时返回 `#f` 可直接用于 `if`：

```text
plan = if (closest) then (speak (closest) "我看到你了") else { remember "goal" "向前探索"; move dx dy }
```

## 7. 不变量速查

- 感知不产 Effect、不进 EffectExecutor（不变量 5）。
- null 不出机器边界（不变量 7）。
- 同 tick 快照一致（不变量 8）。
- 感知范围 = 5 曼哈顿（不变量 12）。

## 8. 未实现（PLANNED，历史设计 P6.1）

- 感知范围可配置（当前硬编码 5）。
- `observe` 原语（历史 DSL 草案 §10.1 中的名字，已被本感知原语集取代，未实现）。
