# ADR-0004: D 栈作为中断/恢复机制

- 状态：Current
- 创建日期：2026-09-10
- 更新日期：2026-11-19

## 决策背景

- SECD 机也可以看作状态机：只要保留 S/E/C/D 四个寄存器的状态，它就具备从任意状态继续计算的能力。
- 内核能执行（[ADR-0001](ADR-0001-secd-runtime.md)）、能取想法（[ADR-0002](ADR-0002-llm-oracle.md)）、能改世界（[ADR-0003](ADR-0003-effect-isolation.md)）之后，要划"时间"这条边界：计划驻留在 C 栈跨 tick 推进，当世界中插进来一个事件，这个事件的现场由谁承接。
- 相遇对话（`onMeet`）是第一个此类需求，当时尚未实现。SECD 的 D 栈（dump stack）天然是 continuation。

## 决策方案

### 方案一：相遇时丢弃剩余计划，事后让 LLM 重新规划
1. **结论**：否决。同一情境两次运行走法不同（行为不可预测），ADR-0001 的"可恢复"就此作废，ADR-0002 的"LLM 只在编译期决策"也被绕开。

### 方案二：事件排队，等当前计划执行完再处理
1. **结论**：否决。交互性丢失：两人擦肩的瞬间没反应，等几个 tick 后才"想起来"该打招呼，位置早就不对了。

### 方案三：为事件处理单写一套状态机，与主计划并列运行
1. **结论**：否决。两套执行状态、两个推进器、两处 Effect 来源，"单一执行内核"被破坏；且两套状态如何同步没有干净答案。

### 方案四：复用 D 栈，中断 → 处理 → 恢复
1. **结论**：采纳。两次挂起在机械上完全同构，没有理由写第二套机制。

## 最终决策

- **D 栈即中断/恢复机制，协议固定三步：Interrupt → Handle → Resume。**
- **中断与闭包应用挂起同构**：相遇对话以"闭包应用"形式占 D 栈一帧，`onMeet = λother. {...}` 结束后自然解帧回到主计划，**没有特例分支**。
- 恢复语义：E 引用共享、S/C/D 浅克隆快照；恢复后从被打断的那条指令继续。
- 入口：`MindController.onMeet` 对相遇双方各执行一次 `interrupt` + `runHandler`。
- 保护：handler 步数上限 `virtual-ai-box.runtime.max-handler-steps` 与 D 栈深度阈值 `...convergence.max-d-stack-depth`，后者超限发布 `agent.loop`（reason=d-stack）。

Q. D 栈本来是给函数调用用的，拿来存"被打断的主计划"算不算滥用？<br/>
A. 不算。闭包应用挂起时压 `{S,E,C,D}`、恢复时弹帧换回；中断就是"以事件 handler 为被调用者"的一次挂起。既然同构，就不该写第二套机制。

Q. handler 跑完就算结束吗？<br/>
A. 不是。结束条件是 **D 栈清空**（中断帧已解开、主计划已换回 C 栈），而非"handler 求值完"。handler 内部可能还有嵌套闭包，解帧后 C 仍可能非空，必须继续跑；若停在"值已算出"，主计划就永远回不去。这个区分是 `runHandler` 的唯一难点。

Q. 中断会不会变成"合法的死循环"？<br/>
A. 会，所以必须配预算与告警：步数上限防单次失控，D 栈深度阈值判疑似无限嵌套。

- 协议伪码、`stepWithResume` 的恢复语义与调用链见 [spec/agent-runtime.md](../spec/agent-runtime.md)「核心行为 · 中断与恢复」。

## 决策后果

- 相遇对话与普通函数调用共享同一机制：**新增事件类型不需要新机制，只需要写一个闭包**。
- `runHandler` 预算耗尽属软失败：D 栈可能仍非空，主计划留待下 tick 继续（`tick` 同样走 `stepWithResume`，会先解帧再推进），不报错、不丢计划。
- 恢复语义依赖浅克隆：S/C/D 是快照、E 是共享引用。若未来引入可变环境（如 `set!`），恢复语义需要重新论证。
- 已知限制：结束条件取"D 栈空"，前提是常规 plan 为扁平程序（不产生 D 帧）。若主计划在中断时 D 本已非空（相遇发生在闭包内部），`runHandler` 会连带解掉那些外层帧；收口方案是记录中断前的 D 深度、以"D 回到该深度"为结束条件（尚未实现）。
- 约束（不可放宽）：不得引入第二套执行状态机、线程或队列；不得按 handler 类型在 `AgentRuntime` 里写特例分支；`runHandler` 不得省略预算或推进主计划。

## 相关文档

- 相关 ADR：[ADR-0001](ADR-0001-secd-runtime.md)、[ADR-0002](ADR-0002-llm-oracle.md)、[ADR-0003](ADR-0003-effect-isolation.md)、[ADR-0006](ADR-0006-secd-observability-and-primitives.md)
- 相关 Spec：[Agent Runtime](../spec/agent-runtime.md)、[Behavior DSL](../spec/behavior-dsl.md)
- 相关 Architecture：[architecture.md](../architecture.md)
