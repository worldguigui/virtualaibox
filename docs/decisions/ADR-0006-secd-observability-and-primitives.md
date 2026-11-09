# ADR-0006: SECD 可观测性回迁与算术原语补全

- 状态：Accepted
- 日期：2026-11-09

## Context

lambdaexpr（SECD 机器的原始实现）与本项目 `secd/` 包的逐文件对比考古发现三处迁移损耗：

1. **trace 能力丢失**：原版 `SECDMachine.setTrace(PrintStream)` 每步打印
   (S,E,C,D) 四元组与命中的规则编号，是调试与教学的利器；迁移版无对应物，
   仅剩发散比对用的 `MachineState.captureState()`。
2. **类型化异常退化为泛化异常 + 贫消息**：原版 `TypeException`/
   `NotAFunctionException` 携带"add 需要整数参数，得到: 3.5"式富消息；
   迁移版 `ArithmeticOpEvaluator` 抛 `IllegalStateException("Binary Type error: " + op)`，
   丢掉了操作数现场。而行为程序由 LLM 现场编译，求值错误的第一现场（原语名+操作数）
   对排障至关重要。且泛化异常无法被精确捕获——一个 Agent 的坏程序会经
   MindController 冒泡终止**整个 tick**（全镇所有 Agent 的本 tick 作废）。
3. **λ 原语缩水**：原版一元 `pred/zerop`、二元 `sub/div` 未随迁，DSL 侧亦无补偿；
   扩展 DSL 算术表达时将踩雷。

## Decision

1. **trace 回迁为 Consumer 钩子**（非 PrintStream）：
   - `SECD.setTrace(Consumer<String>)`，每步转移输出单行
     （步号 + 规则名 + S/E/C/D 单行摘要；S/C 顶优先、E 按键排序、D 只报深度）；
     传 null（默认）关闭、零开销。
   - 规则名 `const/var/app/lam/call/apply/seq/if/restore/halt/diverge`
     对应原版教材规则 (1)(2)(3)(4)(6)(5)(8/9)(7)。
   - 开关：`virtual-ai-box.runtime.secd-trace-enabled`（默认 false）；
     `AgentRuntime` 开启时挂 DEBUG 日志观察者（前缀 `SECD[agent名]`）。
   - 该 Consumer 钩子同时是 MindViewer 前端可视化"机器脉搏"的预留入口。
2. **轻量回迁类型化异常 + 富消息**（收敛为单一类型，不建异常层级）：
   - 新增 `secd.SecdEvalException extends RuntimeException`，消息含原语名与实际操作数
     （如 `未知二元原语 'gcd'（操作数: 1, 2）`、`div 除以零: 1 / 0`）。
   - 抛出点：`ArithmeticOpEvaluator`（未知原语/除零）、`SECD.apply`（非函数应用）。
   - **爆炸半径收窄**：`AgentRuntime.tick` 与 `runHandler` 捕获本异常并降级单个
     Agent（清空机器、下一 tick 重编译计划；错误前已产出的 Effect 照常落地），
     不再冒泡终止整个 tick。
   - **分诊边界不变**：装配/生命周期错误（如 `perceptionRequired` 的
     IllegalStateException）仍 fail-fast 冒泡——运行期数据不确定性才 fail-soft。
3. **算术原语补全**（回迁 lambdaexpr 全集）：
   - 一元：`pred`（n-1）、`zerop`；二元：`sub`、`div`（除零抛 SecdEvalException）。
   - **有意分歧**：`zerop` 返回 `BoolValue`（原版返回 Int 1/0）——本机比较原语
     一律返回 BoolValue，且 DSL 真值规则为仅 `#f` 为假，1/0 语义会引入歧义。
   - 非整数实参的**延迟闭包行为保持不变**（丘奇数等纯 λ 交互，有既有测试守护）：
     富错误只在"双整数操作数 + 未知原语名"等确实无法求值的路径触发。

## Alternatives

1. **原样回迁完整异常层级**（TypeException/NotAFunctionException/DivergenceException）
   ——被拒：调用方实际只按"求值错误 vs 其他"二分，层级无消费场景；单一类型 + 富消息
   以更低复杂度达成同一目标。
2. **trace 走 PrintStream**（原版 API 原样）——被拒：进不了 SLF4J 日志体系、
   无法被前端消费；Consumer 钩子两者通吃且测试友好。
3. **仅在 behavior-dsl spec 明示"算术仅 add/mul"边界**（不动代码）——被拒：
   补全成本（各 ~3 行）低于文档约束的长期维护成本，且消除了 LLM 编译产出
   `sub` 时"未知名→静默柯里化→难懂行为"的陷阱。

## Consequences

- **行为变化（正向）**：单个 Agent 的坏行为程序不再终止整个 tick；
  该 Agent 本 tick 静默（无主动作），下一 tick 重编译——ConvergenceMonitor 的
  stuck 检测（连续 3 tick 无移动）天然兜底"持续坏程序"的可观测性。
- 已知限制：`AgentRuntime.stepWithResume` 自行执行的续体恢复（C 空解 D 帧）
  不经 `SECD.step`，trace 暂不覆盖该转移；与 stepWithResume/SECD.run 的 DRY
   重复项（历史遗留）留待后续一并处理。
- `ConfigDefaultsConsistencyTest` 的 REQUIRED_KEYS 与绑定比对已同步
  `secd-trace-enabled`（yml 与 Java 默认值一致性继续受机制化校验）。
- 遗留问题（本 ADR 不处理，见考古报告）：DumpFrame 克隆 O(n²) 回退、
  `SECD.java` 对原版"静默跳过"的不实 javadoc、四处悬空的
  `secd-fusion-design-frozen.md` 引用。

## 验证

- `.\mvnw.cmd test`：10 个测试类 **95 例全部通过**（基线 85 → 95）。
  新增：SECDTest 9 例（sub/div/pred/zerop 算术、除零/未知原语/非函数应用富消息、
  trace 每步输出与默认关闭）、AgentRuntimeTest 1 例（坏程序降级不终止 tick）。
- 降级路径的 ERROR 日志实测输出富消息：
  `AgentRuntime: 行为程序求值错误，本 tick 降级并重置机器 [agent=Alice]: Cannot apply non-function value '5' to '6'`。
