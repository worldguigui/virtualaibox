# ADR-0006: SECD 可观测性回迁与算术原语补全

- 状态：Accepted
- 日期：2026-11-09

## 决策背景

- 本决策的目标：把 [ADR-0001](ADR-0001-secd-runtime.md) 移植过程中丢失的调试与排障能力找回来。trace、富错误消息、原语全集，使 LLM 现场编译的行为程序出错时**可定位、可降级**。
- lambdaexpr（SECD 机器的参考实现）进行了迭代，完善了部分代码。本项目 `secd/` 包的逐文件进行对比，发现三处值得迁移的新特性。

1. **trace 能力**：原版 `SECDMachine.setTrace(PrintStream)` 每步打印 (S,E,C,D) 四元组与命中的规则编号，是调试与教学的利器；迁移版无对应物，仅剩发散比对用的 `MachineState.captureState()`。
2. **完善的类型化异常**：原版 `TypeException`/`NotAFunctionException` 可以处理异常，并记录精准的错误信息。
3. **λ 原语缩水**：原版扩展的一元 `pred/zerop`、二元 `sub/div` 很有用，值得迁移。

## 决策选择

Q. 我们到底在决策什么？<br/>
A. 对三处新特性逐一决定：迁移什么、迁移成什么形态、边界在哪。

Q. trace 和异常为什么改变形态迁移，而不是照抄原版？<br/>
A. trace：PrintStream 进不了 SLF4J 日志体系、无法被前端消费，`Consumer<String>` 两者通吃、传 null（默认）零开销。异常：调用方实际只按"求值错误 vs 其他"二分，类型层级无消费场景；收敛为单一 `SecdEvalException` + 富消息（原语名 + 操作数），以更低复杂度达成同一目标。

Q. 什么错误降级、什么错误炸穿 tick？<br/>
A. 分诊边界：**运行期数据不确定性才 fail-soft**。行为程序由 LLM 现场编译，坏程序是常态，爆炸半径收窄到单 Agent（清空机器、下一 tick 重编译，已产出的 Effect 照常落地）；装配/生命周期错误（如 `perceptionRequired` 的 IllegalStateException）是真 bug，fail-fast 冒泡。

## 最终决策

- **三处新特性按"Consumer 钩子 + 单一类型化异常 + 原语全集"形态迁移：**

| 迁移内容 | 迁移形态 | 契约 |
|---|---|---|
| trace 能力 | `SECD.setTrace(Consumer<String>)`，每步单行输出（步号 + 规则名 + S/E/C/D 摘要） | 规则名对应原版教材规则；开关 `virtual-ai-box.runtime.secd-trace-enabled`（默认 false）；也是 MindViewer"机器脉搏"预留入口 |
| 类型化异常 | 单一 `SecdEvalException`，消息含原语名与操作数 | 抛出点：`ArithmeticOpEvaluator`、`SECD.apply`；`AgentRuntime` 捕获后降级单 Agent；装配/生命周期错误仍 fail-fast |
| λ 原语缩水 | 补全一元 `pred/zerop`、二元 `sub/div` | **有意分歧**：`zerop` 返回 `BoolValue`（原版 Int 1/0）；非整数实参延迟闭包行为不变 |

## 改造边界

### 明令禁止的改动

1. **分诊边界不得移动**：求值错误降级单 Agent，装配/生命周期错误 fail-fast，两方向都不得改。
2. **异常信息要携带详细的信息**：求值异常必须携带原语名与实际操作数。
3. **禁止 zerop/比较原语回退 Int 1/0**：一律 BoolValue（[ADR-0001](ADR-0001-secd-runtime.md) 有意分歧）。

### 涉及旧有模块与核心文件

| 模块 / 文件 | 改动 |
|---|---|
| `secd/SECD`（改造） | `setTrace(Consumer<String>)`；非函数应用抛 `SecdEvalException` |
| `secd/SecdEvalException`（新增） | 单一类型化求值异常 |
| `secd/ArithmeticOpEvaluator`（改造） | 补全 pred/zerop/sub/div；富消息；zerop → BoolValue |
| `behaviordsl/BehaviorCompiler`（改造，2026-11-16 补） | OPS 词表登记 pred/zerop/sub/div（此前遗漏：求值器认识这四个名字，DSL 编译器不认识） |
| `mind/AgentRuntime`（改造） | tick/runHandler 捕获降级；trace 开启时挂 DEBUG 观察者 |
| `properties/RuntimeProperties` + `application.yml`（扩展） | 新增 `secd-trace-enabled` |
| `secd/SECDTest`（新增）、`mind/AgentRuntimeTest`、`properties/ConfigDefaultsConsistencyTest`（扩展） | 守护测试 |

## 最终实现

- `SECD.setTrace(Consumer<String>)` 每步单行输出，null 零开销；`AgentRuntime` 按开关挂 DEBUG 日志观察者。
- `AgentRuntime.tick` / `runHandler` 捕获 `SecdEvalException` 降级：清空机器、下 tick 重编译，已产出的 Effect 照常落地。
- `ArithmeticOpEvaluator` 补全 `pred`/`zerop`/`sub`/`div`。
- 2026-11-16 收尾：`BehaviorCompiler.OPS` 词表登记这四个原语（此前求值器已支持，但 DSL 编译器不认识，写进 .lambda 会在加载期报"引用了未知名称"）。至此求值器、编译器、spec 三处一致，守护用例 `BehaviorCompilerTest.arithmeticPrimitivesCompileAndRun`。

## 决策后果

- 单个 Agent 的坏行为程序不再终止整个 tick；该 Agent 本 tick 静默，下一 tick 重编译。ConvergenceMonitor 的 stuck 检测天然兜底“持续坏程序”的可观测性。
- 已知限制：`stepWithResume` 的续体恢复不经 `SECD.step`，trace 暂不覆盖该转移。
- 一个新原语要同步四处：求值器（WorldOpEvaluator/ArithmeticOpEvaluator）、编译器词表（OPS）、spec 文档、（可选）前端展示。
- 遗留问题（本 ADR 不处理）：DumpFrame 克隆 O(n²) 回退、`SECD.java` 不实 javadoc、四处悬空的 `secd-fusion-design-frozen.md` 引用。
