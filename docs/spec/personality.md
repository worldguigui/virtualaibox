# Spec：PersonalityProfile（居民稳定人格）

> 状态：CURRENT。定义居民人格作为领域状态的最小契约。
> 代码实现：`src/main/java/com/own/virtualaibox/domain/agent/PersonalityProfile.java`。

## 1. 人格边界

人格属于居民的长期身份状态，不属于 SECD 的 S/E/C/D 寄存器，也不由单次 LLM 回复覆盖。

人格包含：

- `role`：居民在小镇中的角色；
- `temperament`：稳定性情；
- `speakingStyle`：表达方式；
- `motivations`：当前默认动机；
- `values`：决策时优先考虑的价值；
- `knowledgeBoundary`：居民允许使用的知识范围。

## 2. 默认规则

- Alice 使用探索者人格；
- Bob 使用社区居民人格；
- 其他居民使用稳定的通用居民人格；
- 相同名称的默认模板必须产生相同人格内容；
- 居民创建后人格可被展示和读取，但当前阶段不允许被 LLM 自动改写。

## 3. LLM 使用方式

`LLMBrain` 只能把人格作为决策上下文注入 Oracle prompt：

```text
PersonalityProfile + Memory + Perception
                 ↓
              LLM Oracle
                 ↓
            MoveAction 意图
```

LLM 不得直接修改人格、记忆或 World；人格影响决策只能通过可审计的 prompt 和 Effect 流体现。

## 4. 验收条件

- 默认居民人格不同；
- 同一居民重复创建其默认模板时结果稳定；
- Dashboard 居民数据包含 `personality`；
- 前端居民卡片可以展示人格摘要；
- 人格上下文测试覆盖角色、动机和知识边界；
- 既有 SECD、DSL、世界和居民生命周期测试不受影响。

本阶段验证结果：2026-10-24 `.\mvnw.cmd test` 9 类 75 例全部通过（含 `PersonalityProfileTest` 3 例），前端构建通过。
