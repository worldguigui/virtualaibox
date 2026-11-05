# Spec：Behavior DSL（.lambda → SECD Instruction）

> 状态：CURRENT（已实现并有测试；历史设计自标"草案"，语法后续可调整）。
> 语法权威来源：`src/main/antlr4/com/own/virtualaibox/behaviordsl/BehaviorDSL.g4`（v0.2）。
> 编译实现：`src/main/java/com/own/virtualaibox/behaviordsl/`。

## 1. 文件结构

`.lambda` 文件由顶层声明组成，`;` 或换行分隔（分隔符可省略）：

```
topLevel := defDecl | planDecl
defDecl  := NAME '=' expr          # 命名行为（值/闭包），如 onMeet
planDecl := 'plan' '=' expr        # 入口程序（必须恰好一个）
```

- 每个 def 编译期按依赖序求值为值（λ def 得到闭包）；循环引用/未知名/缺 plan 在加载期 fail-fast。
- `onMeet` def 若存在则作为相遇对话行为（参数为对方 Agent）；否则回退内置对话（`DefaultPlanCompiler.compileOnMeet`）。

## 2. 语法（v0.2）

```
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

- **应用是变参左结合**：`(f a b) == ((f a) b)`；`(f)` 为零参调用（感知原语用，编译为"应用一次到 unit"）。
- 注释：`//`、`#` 行注释，`/* */` 块注释。
- 保留关键字：`plan lambda λ let in if then else north east south west`。

## 3. 原语全集（OPS）

| 类别 | 原语 | 语义 | 求值器 |
|---|---|---|---|
| 世界副作用 | `move dx dy` / `move north` | 移动 → 统一 MoveEffect(dx,dy) | WorldOpEvaluator |
| | `speak target content` | 说话 → SpeakEffect（target 可为 AgentRef） | WorldOpEvaluator |
| | `remember key content` | 记忆 → RememberEffect | WorldOpEvaluator |
| | `ask-llm prompt` | 同步咨询 Oracle，结果压 S + LLMRequestEffect（信息性） | WorldOpEvaluator |
| 算术 | `add mul`（二元柯里化）`succ sqr`（一元） | 纯算术 | ArithmeticOpEvaluator |
| 比较 | `eq lt gt le ge`（二元） | → BoolValue；类型不符 → `#f` | ArithmeticOpEvaluator |
| 感知 | `self closest dist-to name-of direction-of` | 只读世界查询，值压 S；null/超范围 → `#f` | WorldOpEvaluator（需 Perception） |
| 方向 | `north east south west` | 方向常量 → DirectionValue | 编译期原子 |

- `eq` 支持 Int/String/AgentRef（按 id）/Direction；`lt/gt/le/ge` 仅 Int。
- **未实现**（PLANNED，写入会得到明确错误或未知名）：`observe`（P6.1 计划）、`and/or/not`（P6.1 计划）。

## 4. plan 契约

- plan 是**内联程序**：直接引用运行时注入的自由变量 `dx`/`dy`（由 LLM Oracle 决定方向并注入 E）。
- **不要**写成 `λdx. λdy.` 形式——扁平程序不产生闭包 D 帧，tick 语义与内置 DefaultPlanCompiler 一致。
- plan 的自由变量必须 ⊆ {defs 名} ∪ {dx, dy}，否则加载期报错。

## 5. 编译语义要点

| 语法 | 编译产物 |
|---|---|
| `if c then a else b` | `InstSeq([compile(c), InstIfThenElse(compile(a), compile(b))])` |
| `let x = e in b` | `apply(λx.b, e)`（β 归约，不占新 D 帧） |
| `(f)` | `InstApp(f, VoidValue)`（unit 哨兵，零参原语被实际调用） |
| 裸名字 | 局部变量 → InstVar；原语 → InstConst(OpValue)；顶层名 → 自由变量 |
| 序列 `{a; b}` | InstSeq |
| 方向原子 | InstConst(DirectionValue) |

- **if 语义**：弹出 S 顶一次作为条件，按真值把选中分支压入 C；不产生额外 D 帧、不残留条件值、不改变 E（与 D 栈中断/恢复兼容）。
- **真值规则**：Scheme 风格——仅 `#f` 为假，`0`/`""`/AgentRef 等一切值皆真。
- **字符串**：支持 `\"` `\\` `\n` `\t` 转义。

## 6. 加载机制（BehaviorRegistry）

- 启动时扫描 `classpath*:agents/*.lambda`，文件名去 `.lambda` 作为键（小写）。
- 匹配顺序：Agent 名（忽略大小写）→ Agent id → `default`（全局回退）。
- 无匹配/无文件 → null → 运行时回退内置 `DefaultPlanCompiler`。
- 坏文件加载期 fail-fast（抛 IllegalStateException，服务启动失败）。

## 7. 运行时接线

- `AgentRuntime.compileFromProgram`：E 种子 = `defs + {dx: IntValue, dy: IntValue}`（dx/dy 来自 `llmBrain.decideAction`），plan 压入 C 栈。
- `MindController.interruptWithMeet`：DSL onMeet 存在时 `interrupt(apply(onMeet, AgentRefValue(other)))`；否则内置 onMeet。
- 感知原语只能在注入 Perception 的 tick 内调用，否则 fail-fast 报错（`WorldOpEvaluator.perceptionRequired`）。

## 8. 示例（当前 `resources/agents/default.lambda`）

```text
greet   = λ other. (speak other "你好，很高兴遇见你")
persona = λ other. (remember "met" "遇见了伙伴")
onMeet  = λ other. { greet other; persona other }
plan = { remember "goal" "向前探索"; move dx dy }
```

（文件头含被注释的 P6 感知条件示例：`plan = if (closest) then (speak (closest) "我看到你了") else { remember "goal" "向前探索"; move dx dy }`）
