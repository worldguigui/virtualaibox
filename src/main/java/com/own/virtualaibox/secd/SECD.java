package com.own.virtualaibox.secd;

import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.secd.value.BoolValue;
import com.own.virtualaibox.secd.value.ClosureValue;
import com.own.virtualaibox.secd.value.InfiniteValue;
import com.own.virtualaibox.secd.value.Value;
import com.own.virtualaibox.secd.value.VarValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * SECD 抽象机驱动。移植自 lambdaexpr 的 {@code SECDMachine}，并做了两处改造：
 *
 * <ul>
 *   <li>状态与驱动分离：S/E/C/D 收敛进 {@link MachineState}，由调用方持有；</li>
 *   <li>副作用隔离：单步/运行返回 {@link Effect} 列表，机器本身不修改 World。</li>
 * </ul>
 *
 * <p>默认使用 {@link ArithmeticOpEvaluator}（纯算术原语）。
 * 可注入世界原语求值器（move/speak/remember/ask-llm），
 * 此时对应原语以 Effect 形式表达世界副作用。</p>
 *
 * <p><b>可观测性</b>（回迁自 lambdaexpr 的 {@code setTrace}，ADR-0006）：
 * {@link #setTrace(Consumer)} 挂载每步转移观察者，每步收到单行文本
 * （步号 + 规则名 + S/E/C/D 单行摘要）；传 null（默认）即关闭、零开销。
 * 规则名 {@code const/var/app/lam/apply/seq/if/call/restore/halt/diverge} 大致对应
 * 原版教材规则 (1)(2)(3)(4)(5)(8/9)(6)(7)。宿主 AgentRuntime 经
 * {@code virtual-ai-box.runtime.secd-trace-enabled} 接通 DEBUG 日志；该 Consumer 钩子
 * 同时是 MindViewer 前端可视化"机器脉搏"的预留入口。</p>
 */
public class SECD {

    /** 默认最大执行步数，用于防止无限归约。 */
    public static final int DEFAULT_MAX_STEPS = 10_000;

    private final int maxSteps;
    private final OpEvaluator opEvaluator;

    /** 每步转移观察者（单行文本）；null = 关闭（默认，零开销）。 */
    private Consumer<String> trace;
    /** trace 步计数（仅诊断用；每 Agent 独占一台 SECD 实例，无共享）。 */
    private long traceStep = 0;

    public SECD() {
        this(DEFAULT_MAX_STEPS, new ArithmeticOpEvaluator());
    }

    public SECD(OpEvaluator opEvaluator) {
        this(DEFAULT_MAX_STEPS, opEvaluator);
    }

    public SECD(int maxSteps, OpEvaluator opEvaluator) {
        this.maxSteps = maxSteps;
        this.opEvaluator = opEvaluator;
    }

    /** 挂载/卸载每步转移观察者（调试与可视化）；传 null 关闭。 */
    public void setTrace(Consumer<String> trace) {
        this.trace = trace;
    }

    /**
     * 单步执行：弹出 C 栈顶指令并按其语义推进状态。
     *
     * @return 本步产生的副作用（纯算术路径为空）
     */
    public List<Effect> step(MachineState state) {
        Instruction inst = state.getC().pop();
        List<Effect> effects = new ArrayList<>();

        // 常量：压入 S
        if (inst instanceof InstConst c) {
            state.getS().push(c.value);
            trace(state, "const");
        }
        // 变量：E 有绑定则压绑定，否则压变量本身
        else if (inst instanceof InstVar v) {
            if (state.getE().containsKey(v.name)) {
                state.getS().push(state.getE().get(v.name));
            } else {
                state.getS().push(new VarValue(v.name));
            }
            trace(state, "var");
        }
        // 应用：改写 C 顶为 [rator, rand, @]，rator 在顶
        else if (inst instanceof InstApp app) {
            state.getC().push(new InstApply());
            state.getC().push(app.rand);
            state.getC().push(app.rator);
            trace(state, "app");
        }
        // λ 抽象：用 (arg, body, 当前 E 的浅拷贝) 构造闭包压入 S
        else if (inst instanceof InstLam lam) {
            state.getS().push(new ClosureValue(lam.arg, lam.body, new HashMap<>(state.getE())));
            trace(state, "lam");
        }
        // 应用符号 @：弹出 rand、rator 并执行应用
        else if (inst instanceof InstApply) {
            Value rand = state.getS().pop();
            Value rator = state.getS().pop();
            effects.addAll(apply(state, rator, rand));
            trace(state, "apply");
        }
        // 指令序列：逆序展开压入 C，保持原有顺序执行
        else if (inst instanceof InstSeq seq) {
            List<Instruction> instructions = seq.instructions;
            for (int i = instructions.size() - 1; i >= 0; i--) {
                state.getC().push(instructions.get(i));
            }
            trace(state, "seq");
        }
        // 条件分支：弹出 S 顶作为条件，真则压入 then，假则压入 otherwise（C 栈同构，无新 D 帧）
        else if (inst instanceof InstIfThenElse ite) {
            Value cond = state.getS().pop();
            state.getC().push(BoolValue.isTruthy(cond) ? ite.then : ite.otherwise);
            trace(state, "if");
        }
        // 未知指令
        else {
            throw new IllegalStateException("Unknown instruction: " + inst);
        }

        return effects;
    }

    /**
     * 执行应用 {@code func arg}。
     * 操作符值委托给 {@link OpEvaluator}；闭包则挂起当前状态到 D 栈并开启新求值。
     */
    private List<Effect> apply(MachineState state, Value func, Value arg) {
        // 原语操作符（一元/二元/柯里化中间态）—— 委托给求值器
        if (func instanceof com.own.virtualaibox.secd.value.OpValue
                || func instanceof com.own.virtualaibox.secd.value.PartialOpValue) {
            return opEvaluator.apply(state, func, arg);
        }
        // 闭包：D 栈挂起当前配置，开启闭包 (V, B, E1) 的新求值
        else if (func instanceof ClosureValue cl) {
            Stack<DumpFrame> dump = state.getD();
            // 保存当前配置到 D 栈（S/C/D 浅克隆；E 引用共享，与原实现一致）
            dump.push(new DumpFrame(
                    shallowCloneValues(state.getS()),
                    state.getE(),
                    shallowCloneInstructions(state.getC()),
                    shallowCloneFrames(dump)));
            // 开启新求值：以闭包捕获环境为基，绑定形参
            state.getS().clear();
            state.setE(new HashMap<>(cl.getEnv()));
            state.getE().put(cl.getArg(), arg);
            state.getC().clear();
            state.getC().push(cl.getBody());
            trace(state, "call");
            return List.of();
        }
        // 其余类型被当作函数应用：非函数值应用属求值期错误（lambdaexpr 的
        // NotAFunctionException 富消息风格回迁，ADR-0006），由 AgentRuntime 降级
        else {
            throw new SecdEvalException("Cannot apply non-function value '" + func + "' to '" + arg + "'");
        }
    }

    /**
     * 把程序压入 C 栈并运行到求解结束。
     *
     * @return 归约结果（最终值 + 副作用 + 是否发散）
     */
    public Reduction run(MachineState state, Instruction code) {
        state.getC().push(code);
        return run(state);
    }

    /**
     * 从当前状态继续运行，直到 C/D 清空或触发发散检测。
     * 支持恢复被挂起的计划。
     */
    public Reduction run(MachineState state) {
        List<Effect> allEffects = new ArrayList<>();
        int stepCount = 0;
        int lastDumpSize = 0;
        int dumpGrowthCount = 0;
        String initialStateSnapshot = state.captureState();

        while (true) {
            // 步数上限 + 发散检测（移植自原 SECDMachine）
            if (stepCount >= maxSteps) {
                boolean infinite = state.getD().size() > maxSteps / 10
                        || (initialStateSnapshot != null && initialStateSnapshot.equals(state.captureState()))
                        || dumpGrowthCount > maxSteps / 20;
                if (infinite) {
                    trace(state, "diverge");
                    return new Reduction(
                            new InfiniteValue("infinite reduction detected after " + stepCount
                                    + " steps (D stack size: " + state.getD().size() + ")"),
                            allEffects, true);
                }
                // 非发散：计算复杂但仍在推进，重置计数继续
                stepCount = 0;
                dumpGrowthCount = 0;
            }

            // 监控 D 栈增长
            if (state.getD().size() > lastDumpSize) {
                dumpGrowthCount++;
            }
            lastDumpSize = state.getD().size();

            // 求解结束或从转储帧恢复
            if (state.getC().isEmpty()) {
                if (state.getD().isEmpty()) {
                    break;
                }
                Value retVal = state.getS().pop();
                DumpFrame dump = state.getD().pop();
                state.restore(dump);
                state.getS().push(retVal);
                trace(state, "restore");
                continue;
            }

            allEffects.addAll(step(state));
            stepCount++;
        }

        trace(state, "halt");
        return new Reduction(state.getS().isEmpty() ? null : state.getS().peek(), allEffects, false);
    }

    // ---------------------------------------------------------- trace（ADR-0006）

    /** 每步转移输出一行：步号 + 规则名 + S/E/C/D 单行摘要（观察者未挂载时零开销）。 */
    private void trace(MachineState state, String rule) {
        if (trace == null) {
            return;
        }
        trace.accept(String.format("%6d %-8s %s", ++traceStep, rule, compactState(state)));
    }

    /** 单行四寄存器摘要：S/C 顶优先、E 按键排序（输出确定性）、D 只报深度。 */
    private String compactState(MachineState st) {
        StringBuilder sb = new StringBuilder();
        sb.append("S=[");
        for (int i = st.getS().size() - 1; i >= 0; i--) {
            if (i < st.getS().size() - 1) sb.append(", ");
            sb.append(st.getS().get(i));
        }
        sb.append("] E={");
        boolean first = true;
        for (Map.Entry<String, Value> e : new TreeMap<>(st.getE()).entrySet()) {
            if (!first) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
            first = false;
        }
        sb.append("} C=[");
        for (int i = st.getC().size() - 1; i >= 0; i--) {
            if (i < st.getC().size() - 1) sb.append(", ");
            sb.append(st.getC().get(i));
        }
        sb.append("] D=").append(st.getD().size());
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Stack<Value> shallowCloneValues(Stack<Value> src) {
        return (Stack<Value>) src.clone();
    }

    @SuppressWarnings("unchecked")
    private Stack<Instruction> shallowCloneInstructions(Stack<Instruction> src) {
        return (Stack<Instruction>) src.clone();
    }

    @SuppressWarnings("unchecked")
    private Stack<DumpFrame> shallowCloneFrames(Stack<DumpFrame> src) {
        return (Stack<DumpFrame>) src.clone();
    }
}
