# Spec：Behavior DSL

- 状态：Current
- 创建日期：2026-11-05
- 更新日期：2026-11-19

## 功能目标

- 让人用可读文本编写 Agent 的行为程序，而不必拼装 SECD 指令。
- 把"行为"从代码里搬到资源文件（`agents/*.lambda`），改行为不必改 Java。
- 保持表达式语言的最小性：只提供组合所需的核心构造，不追求完整编程语言。

## 功能范围

### 包含范围

1. `.lambda` 文件的顶层结构（defs + plan）。
2. v0.2 语法与保留字。
3. 原语全集（OPS）及各类的求值器归属。
4. plan 的自由变量契约。
5. 语法到指令的编译映射。
6. 启动期加载与匹配顺序。
7. 与运行时的接线（E 种子、onMeet 注入）。

不包含：原语的运行期语义（见 [effect.md](effect.md)、[perception.md](perception.md)）、指令的执行（见 [agent-runtime.md](agent-runtime.md)）。

## 输入与输出

### 输入

| 名称 | 类型 | 说明 |
|---|---|---|
| source | String | `.lambda` 文件内容 |
| sourceName | String | 文件名，仅用于错误信息 |
| dx / dy | int | 运行时由 Oracle 决定的自由变量，编译期不绑定 |

### 输出

| 名称 | 类型 | 说明 |
|---|---|---|
| plan | Instruction | 入口程序，压入 C 栈执行 |
| defs | `Map<String, Value>` | 命名行为（值或闭包），作为 E 的一部分 |
| onMeet | Closure（可空） | 相遇对话闭包；缺失则运行时回退内置 onMeet |

编译失败时抛 `IllegalArgumentException`，加载期统一转为启动失败。

## 核心行为

### 文件结构

`.lambda` 文件由顶层声明组成，`;` 或换行分隔（分隔符可省略）：

```text
topLevel := defDecl | planDecl
defDecl  := NAME '=' expr          # 命名行为（值/闭包），如 onMeet
planDecl := 'plan' '=' expr        # 入口程序（必须恰好一个）
```

- 每个 def 在编译期按依赖序求值（λ def 得到闭包）；循环引用、未知名称、缺 plan 均在加载期 fail-fast。
- `onMeet` 若存在则作为相遇对话行为（参数为对方 Agent）；否则回退 `DefaultPlanCompiler.compileOnMeet`。

### 语法（v0.2）

```text
expr      := lambda | let | ifExpr | seq | application | atom | '(' expr ')'
lambda    := ('lambda'|'λ') NAME '.' expr
let       := 'let' NAME '=' expr 'in' expr
ifExpr    := 'if' expr 'then' expr 'else' expr
application := '(' callArg callArg* ')'     # 括号应用 (f a b)，零参 (f)
             | NAME callArg+                # 并列应用 f a b
callArg   := atom | '(' expr ')'
seq       := '{' expr (';' expr)* '}'
atom      := INT | STRING | NAME | direction
direction := 'north' | 'east' | 'south' | 'west'
```

- **应用是变参左结合**：`(f a b) == ((f a) b)`；`(f)` 为零参调用（编译为"应用一次到 unit"）。
- 注释：`//`、`#` 行注释，`/* */` 块注释。
- 保留关键字：`plan lambda λ let in if then else north east south west`。
- 字符串支持 `\"` `\\` `\n` `\t` 转义。

### 原语全集（OPS）

| 类别 | 原语 | 语义 | 求值器 |
|---|---|---|---|
| 世界副作用 | `move dx dy` / `move north` | 移动 → 统一 MoveEffect(dx,dy) | WorldOpEvaluator |
| | `speak target content` | 说话 → SpeakEffect（target 可为 AgentRef） | WorldOpEvaluator |
| | `remember key content` | 记忆 → RememberEffect | WorldOpEvaluator |
| | `ask-llm prompt` | 同步咨询 Oracle，结果压 S + LLMRequestEffect | WorldOpEvaluator |
| 算术 | `add mul sub div`（二元柯里化）、`succ sqr pred zerop`（一元） | 纯算术；除零抛 `SecdEvalException` | ArithmeticOpEvaluator |
| 比较 | `eq lt gt le ge`（二元） | → BoolValue；类型不符 → `#f` | ArithmeticOpEvaluator |
| 感知 | `self closest dist-to name-of direction-of` | 只读世界查询；null/越界 → `#f` | WorldOpEvaluator（需 Perception） |
| 方向 | `north east south west` | 方向常量 → DirectionValue | 编译期原子 |

- `eq` 支持 Int/String/AgentRef（按 id）/Direction；`lt/gt/le/ge` 仅 Int。
- `zerop` 返回 BoolValue（与参考实现的 1/0 有意分歧，见 ADR-0001）。
- **词表必须与求值器同步**：`BehaviorCompiler.OPS` 是加载期白名单，求值器认识的每个名字都必须在册，否则写进 `.lambda` 会在加载期报"引用了未知名称"。新增原语需同步四处：求值器、OPS 词表、本文件、前端展示（可选）。

### plan 契约

- plan 是**内联程序**：直接引用运行时注入的自由变量 `dx`/`dy`。
- **不要**写成 `λdx. λdy.` 形式——扁平程序不产生闭包 D 帧，tick 语义才与内置 `DefaultPlanCompiler` 一致。
- plan 的自由变量必须 ⊆ {defs 名} ∪ {dx, dy}，否则加载期报错。

### 编译映射

| 语法 | 编译产物 |
|---|---|
| `if c then a else b` | `InstSeq([compile(c), InstIfThenElse(compile(a), compile(b))])` |
| `let x = e in b` | `apply(λx.b, e)`（β 归约，不占新 D 帧） |
| `(f)` | `InstApp(f, VoidValue)` |
| 裸名字 | 局部变量 → `InstVar`；原语 → `InstConst(OpValue)`；顶层名 → 自由变量 |
| 序列 `{a; b}` | `InstSeq` |
| 方向原子 | `InstConst(DirectionValue)` |

`if` 的语义：弹出 S 顶一次作为条件，按真值把选中分支压入 C；不产生额外 D 帧、不残留条件值、不改变 E（与 D 栈中断/恢复兼容）。

### 加载与匹配

- 启动时扫描 `classpath*:agents/*.lambda`，文件名去 `.lambda` 作为键（小写）。
- 匹配顺序：Agent 名（忽略大小写）→ Agent id → `default`（全局回退）。
- 无匹配或无文件 → `null` → 运行时回退内置 `DefaultPlanCompiler`。

### 运行时接线

- `compileFromProgram`：E 种子 = `defs + {dx: IntValue, dy: IntValue}`，plan 压入 C 栈。
- `interruptWithMeet`：DSL onMeet 存在时 `interrupt(apply(onMeet, AgentRefValue(other)))`；否则内置 onMeet。

## 约束与不变量

1. **恰有一个 plan**，缺失即加载失败。
2. **plan 扁平**：不产生闭包 D 帧，保证 tick 与中断语义一致。
3. **自由变量受控**：只允许 defs 名与 `dx`/`dy`。
4. **词表双向一致**：OPS 与求值器认识的名字集合相同。
5. **加载期失败优先于运行期**：能静态判定的错误不得留到运行期。

## 异常处理

| 情形 | 处理 | 影响范围 |
|---|---|---|
| 语法错误、未知名、循环引用、缺 plan | 加载期抛 `IllegalStateException`，服务启动失败 | 全局（fail-fast） |
| 坏文件存在于 `agents/` 下 | 同上一行：宁可起不来，也不带病运行 | 全局 |
| 某 Agent 无匹配文件 | 回退内置默认计划，不报错 | 该 Agent |
| 运行期触及未实现原语 | 抛富消息 `SecdEvalException`（含操作数），降级本 Agent | 单 Agent 单 tick |

## 验收标准

1. 修改 `.lambda` 里的 `plan` 后重启，Agent 行为随之改变，无需改 Java。
2. 同一目录下存在语法错误的文件时，服务启动失败并指出文件与位置。
3. 无匹配 Agent 的文件时，该 Agent 使用内置默认计划继续运行。
4. `if` 不产生额外 D 帧：含 `if` 的 plan 与不含 `if` 的 plan 在中断/恢复上行为一致。
5. 新增一个原语时，若只改求值器不改 OPS，加载含该原语的 `.lambda` 会明确报错。

## 相关文档

- 相关 ADR：[ADR-0001](../decisions/ADR-0001-secd-runtime.md)、[ADR-0004](../decisions/ADR-0004-d-stack-interrupt.md)、[ADR-0006](../decisions/ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Agent Runtime](agent-runtime.md)、[Effect](effect.md)、[Perception](perception.md)
- 相关 Architecture：[architecture.md](../architecture.md)

## 待解决问题

- `observe` 原语与 `and`/`or`/`not` 未实现，写入会得到明确的未知名错误。
