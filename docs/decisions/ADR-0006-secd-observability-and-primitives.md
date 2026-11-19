# ADR-0006: SECD 可观测性回迁与算术原语补全

- 状态：Current
- 创建日期：2026-11-09
- 更新日期：2026-11-19

## 决策背景

- 目标：把 [ADR-0001](ADR-0001-secd-runtime.md) 移植过程中丢失的调试与排障能力找回来——trace、富错误消息、原语全集，使 LLM 现场编译的行为程序出错时**可定位、可降级**。
- 参考实现（lambdaexpr）迭代后，本项目 `secd/` 逐文件对比发现三处值得迁移：
  1. **trace 能力**：原版 `setTrace(PrintStream)` 每步打印 (S,E,C,D) 与命中规则编号，是排障利器；迁移版只剩发散比对用的 `captureState()`。
  2. **类型化异常**：原版 `TypeException`/`NotAFunctionException` 携带精准错误信息。
  3. **λ 原语缩水**：原版的一元 `pred/zerop`、二元 `sub/div` 未迁移。

## 决策方案

### 方案一：不迁移，维持现状
1. **结论**：否决。行为程序出错只能看到"失败了"，无法定位是哪条指令、哪个操作数。

### 方案二：照抄原版形态
1. `setTrace(PrintStream)` 直打输出，异常保留多级类型层级。
2. **结论**：否决。PrintStream 进不了 SLF4J 日志体系，也无法被前端消费；异常类型层级没有消费场景（调用方实际只按"求值错误 vs 其他"二分）。

### 方案三：改变形态迁移
1. `Consumer<String>` 观察者钩子 + 单一类型化异常 + 原语全集。
2. **结论**：采纳。以更低复杂度达成同一目标。

## 最终决策

| 迁移内容 | 迁移形态 | 契约 |
|---|---|---|
| trace | `SECD.setTrace(Consumer<String>)`，每步单行（步号 + 规则名 + S/E/C/D 摘要） | 开关 `virtual-ai-box.runtime.secd-trace-enabled`（默认关）；传 null 零开销；同时是 MindViewer"机器脉搏"的预留入口 |
| 类型化异常 | 单一 `SecdEvalException`，消息含原语名与操作数 | 抛出点：`ArithmeticOpEvaluator`、`SECD.apply`；`AgentRuntime` 捕获后只降级本 Agent；装配/生命周期错误仍 fail-fast |
| 原语补全 | 一元 `pred/zerop`、二元 `sub/div` | `zerop` 返回 `BoolValue`（与参考实现的 1/0 有意分歧，见 ADR-0001） |

Q. 什么错误降级、什么错误炸穿 tick？<br/>
A. 分诊边界：**运行期数据不确定性才 fail-soft**。行为程序由 LLM 现场编译，坏程序是常态，因此爆炸半径收窄到单 Agent（清空机器、下一 tick 重编译，已产出的 Effect 照常落地）；装配/生命周期错误（如缺 Perception 注入的 `IllegalStateException`）是真 bug，fail-fast 冒泡。

- 求值器与 DSL 编译器必须同时认识同一批原语名，词表见 [spec/behavior-dsl.md](../spec/behavior-dsl.md)「核心行为 · 原语全集（OPS）」。

## 决策后果

- 单个 Agent 的坏行为程序不再终止整个 tick；该 Agent 本 tick 静默，下一 tick 重编译。ConvergenceMonitor 的 stuck 检测天然兜底"持续坏程序"的可观测性。
- 一个新原语要同步四处：求值器、DSL 编译器词表、spec、前端展示。任一遗漏都会在加载期或运行期表现为"引用了未知名称"。
- 已知限制：`stepWithResume` 的续体恢复不经 `SECD.step`，trace 暂不覆盖该转移。
- 遗留（本决策不处理）：`DumpFrame` 克隆的 O(n²) 回退、`SECD.java` 的 javadoc 与实现不符、代码注释中四处悬空的 `secd-fusion-design-frozen.md` 引用。
- 约束（不可放宽）：分诊边界不得移动；求值异常必须携带原语名与实际操作数；`zerop` 与比较原语不得回退 Int 1/0。

## 相关文档

- 相关 ADR：[ADR-0001](ADR-0001-secd-runtime.md)、[ADR-0003](ADR-0003-effect-isolation.md)、[ADR-0005](ADR-0005-config-extraction.md)
- 相关 Spec：[Behavior DSL](../spec/behavior-dsl.md)、[Agent Runtime](../spec/agent-runtime.md)
- 相关 Architecture：[architecture.md](../architecture.md)
