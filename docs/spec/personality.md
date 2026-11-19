# Spec：PersonalityProfile

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 把居民的人格固定为**领域状态**，不随单次 LLM 回复漂移。
- 让人格可配置（世界默认居民 / 运行时兜底），而不是写死在代码里。
- 人格对决策的影响只能通过可审计的 prompt 体现，不能直接改记忆或世界。

## 功能范围

### 包含范围

1. 人格的六个字段及其含义。
2. 人格的三级来源解析。
3. 人格在 Oracle prompt 中的注入方式。
4. 相关配置键与校验规则。

不包含：人格如何影响具体动作选择（那是 Oracle 的输出，见 [agent-runtime.md](agent-runtime.md)）、前端展示样式。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| name | String | 居民名，用于匹配世界默认居民（忽略大小写） |
| personality | PersonalityProfile（可空） | 显式传入的人格，字段可部分为空 |
| 默认居民配置 | `world.default-residents[].personality` | 第二级来源 |
| 兜底人格 | `runtime.default-personality` | 第三级来源 |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| resolved | PersonalityProfile | 六字段均已填充的稳定人格 |
| promptContext | String | 注入 Oracle 的人格段（`toPromptContext()`） |

## 核心行为

### 六个字段

| 字段 | 含义 |
|---|---|
| `role` | 居民在小镇中的角色 |
| `temperament` | 稳定性情 |
| `speakingStyle` | 表达方式 |
| `motivations` | 当前默认动机 |
| `values` | 决策时优先考虑的价值 |
| `knowledgeBoundary` | 允许使用的知识范围 |

### 三级解析

按顺序解析，前一级为空或字段为空时回落到下一级：

1. **显式传入的人格**：创建/更新居民时请求体里的 `personality`（空字段逐项回落）；
2. **世界配置中同名默认居民的人格**：名称忽略大小写匹配；
3. **运行时通用兜底人格**：`runtime.default-personality`。

- 解析在 `WorldEngine.resolvePersonality` 中完成，调用 `PersonalityProfile.normalizeFor(profile, fallback)`。
- **没有按名字特判的内置模板**：Alice 与 Bob 的差异完全来自 `application.yml`，代码中不存在名字分支。
- 解析是纯函数：相同输入必得相同人格，无随机、无时间依赖。
- 居民创建后人格可被展示与读取，但当前阶段不允许被 LLM 自动改写。

### prompt 注入

```text
PersonalityProfile + Memory + Perception
                 ↓
              LLM Oracle
                 ↓
             MoveAction 意图
```

人格在提示词中占一段；段内容为空时整段省略。占位符与拼接顺序由 `buildPrompt` 负责（`PROMPT_FORMAT` 恰好需要 6 个 `%s`，对应六个字段）。

## 约束与不变量

1. **无名字特判**：不得在代码中为特定居民名写人格分支。
2. **纯函数**：同一 (name, 显式人格, 配置) 必得同一结果。
3. **六字段齐全**：解析后的 profile 不得留空字段。
4. **只读消费**：LLM 只能读取人格作为上下文，不能回写。
5. **三处同步**：改人格字段时必须同时改字段定义、`PROMPT_FORMAT`、`toPromptContext()`。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| `PROMPT_FORMAT` 占位符数量与字段数不符 | 运行时抛 `MissingFormatArgumentException`，被 `decideAction` 捕获后降级为随机方向（fail-soft） | 单次决策 |
| 世界默认居民名为空 | `WorldConfig.validate()` 在启动期失败 | 服务启动 |
| 显式人格只给了部分字段 | 缺的字段逐项从下一级补，不视为错误 | 无 |
| 三级都没有可用人格 | 兜底人格本身有默认值，不会出现空人格 | 无 |

## 验收标准

1. 显式人格的每个非空字段优先生效，空字段由下一级补齐。
2. 同名默认居民的人格能被正确匹配（大小写不敏感）。
3. 未匹配任何默认居民时，落到 `runtime.default-personality`。
4. 同一居民重复创建，人格结果稳定不变。
5. 看板返回的居民数据包含 `personality`，前端可展示摘要。
6. 修改 `application.yml` 中默认居民的人格，无需改代码即可生效。

## 相关文档

- 相关 ADR：[ADR-0005](../decisions/ADR-0005-config-extraction.md)
- 相关 Spec：[Agent Runtime](agent-runtime.md)、[Dashboard API](api.md)
- 相关 Architecture：[architecture.md](../architecture.md)
