package com.own.virtualaibox.secd;

import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.secd.value.BoolValue;
import com.own.virtualaibox.secd.value.ClosureValue;
import com.own.virtualaibox.secd.value.DirectionValue;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.PartialOpValue;
import com.own.virtualaibox.secd.value.StringValue;
import com.own.virtualaibox.secd.value.Value;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * 纯算术操作符求值器，移植自 lambdaexpr {@code Arithmetic}（教材 δ 归约）。
 * 默认实现，无任何世界副作用。
 *
 * <p>支持的纯原语（ADR-0006 补齐 lambdaexpr 全集）：
 * 一元 {@code sqr}、{@code succ}、{@code pred}、{@code zerop}；
 * 二元 {@code add}、{@code mul}、{@code sub}、{@code div}（柯里化；除零抛
 * {@link SecdEvalException}）。当实参不是整数时构造延迟闭包以便与丘奇数等纯 λ 表达式交互。
 * {@code zerop} 返回 {@link BoolValue}——与 lambdaexpr 的 1/0 是<b>有意分歧</b>：
 * 本机比较原语一律返回 BoolValue，且 DSL 真值规则为仅 {@code #f} 为假。</p>
 *
 * <p>支持比较原语：二元 {@code eq/lt/gt/le/ge} → {@link BoolValue}；
 * {@code eq} 支持 Int/String/AgentRef（按 id）/Direction，类型不符的其余比较 → {@code #f}
 * （docs §17.4）。比较仍走柯里化二元通道，可被 WorldOpEvaluator 委托（纯、无世界依赖）。</p>
 *
 * <p>错误消息为 lambdaexpr {@code TypeException} 的富消息风格回迁（ADR-0006）：
 * 包含原语名与实际操作数（如 {@code 未知二元原语 'gcd'（操作数: 1, 2）}），
 * 是调试 LLM 编译程序的第一现场。</p>
 */
public class ArithmeticOpEvaluator implements OpEvaluator {

    private static final String[] UNARY_OPS = {"sqr", "succ", "pred", "zerop"};

    private static final Set<String> COMPARE_OPS = Set.of("eq", "lt", "gt", "le", "ge");

    @Override
    public List<Effect> apply(MachineState state, Value func, Value arg) {
        if (func instanceof OpValue op) {
            if (isUnary(op.op)) {
                state.getS().push(computeUnary(state, op.op, arg));
            } else {
                state.getS().push(new PartialOpValue(op.op, arg));
            }
        } else if (func instanceof PartialOpValue partial) {
            state.getS().push(computeBinary(state, partial.op, partial.firstArg, arg));
        } else {
            throw new SecdEvalException("非原语操作符值: " + func);
        }
        return List.of();
    }

    private boolean isUnary(String op) {
        for (String name : UNARY_OPS) {
            if (name.equals(op)) {
                return true;
            }
        }
        return false;
    }

    private Value computeBinary(MachineState state, String op, Value v1, Value v2) {
        // 比较原语优先处理，避免落入"非整数 → 延迟闭包"分支
        if (COMPARE_OPS.contains(op)) {
            return compare(op, v1, v2);
        }
        if (v1 instanceof IntValue i1 && v2 instanceof IntValue i2) {
            return switch (op) {
                case "add" -> new IntValue(i1.val + i2.val);
                case "mul" -> new IntValue(i1.val * i2.val);
                case "sub" -> new IntValue(i1.val - i2.val);
                case "div" -> {
                    if (i2.val == 0) {
                        throw new SecdEvalException("div 除以零: " + i1.val + " / 0");
                    }
                    yield new IntValue(i1.val / i2.val);
                }
                default -> throw unknownBinary(op, v1, v2);
            };
        }
        // 非整数实参：延迟闭包（与丘奇数等纯 λ 表达式交互；不做严格类型检查）
        Instruction body = new InstApp(
                new InstApp(new InstConst(new OpValue(op)), new InstVar("x")),
                new InstVar("y"));
        return new ClosureValue("y", body, new HashMap<>(state.getE()));
    }

    /** 比较原语：eq 支持 Int/String/AgentRef/Direction；lt/gt/le/ge 仅 Int；类型不符 → #f。 */
    private Value compare(String op, Value v1, Value v2) {
        if (v1 instanceof IntValue i1 && v2 instanceof IntValue i2) {
            boolean r = switch (op) {
                case "eq" -> i1.val == i2.val;
                case "lt" -> i1.val < i2.val;
                case "gt" -> i1.val > i2.val;
                case "le" -> i1.val <= i2.val;
                case "ge" -> i1.val >= i2.val;
                default -> false;
            };
            return new BoolValue(r);
        }
        if ("eq".equals(op)) {
            if (v1 instanceof StringValue s1 && v2 instanceof StringValue s2) {
                return new BoolValue(s1.text.equals(s2.text));
            }
            if (v1 instanceof AgentRefValue a1 && v2 instanceof AgentRefValue a2) {
                return new BoolValue(a1.agentId.equals(a2.agentId));
            }
            if (v1 instanceof DirectionValue d1 && v2 instanceof DirectionValue d2) {
                return new BoolValue(d1.dir.equals(d2.dir));
            }
        }
        // 类型不符（含跨类型 eq、非 Int 的 lt/gt/le/ge）→ #f
        return new BoolValue(false);
    }

    private Value computeUnary(MachineState state, String op, Value v) {
        if (v instanceof IntValue i) {
            return switch (op) {
                case "sqr" -> new IntValue(i.val * i.val);
                case "succ" -> new IntValue(i.val + 1);
                case "pred" -> new IntValue(i.val - 1);
                // 与 lambdaexpr（1/0）有意分歧：谓词一律 BoolValue，配合仅 #f 为假的真值规则
                case "zerop" -> new BoolValue(i.val == 0);
                default -> throw unknownUnary(op, v);
            };
        }
        // 非整数实参：延迟闭包
        Instruction body = new InstApp(
                new InstConst(new OpValue(op)),
                new InstVar("x"));
        return new ClosureValue("x", body, new HashMap<>(state.getE()));
    }

    /** 富错误消息：原语名 + 实际操作数 + 可用原语清单（lambdaexpr TypeException 风格，ADR-0006）。 */
    private static SecdEvalException unknownBinary(String op, Value v1, Value v2) {
        return new SecdEvalException(
                "未知二元原语 '" + op + "'（操作数: " + v1 + ", " + v2 + "）；可用: add mul sub div eq lt gt le ge");
    }

    private static SecdEvalException unknownUnary(String op, Value v) {
        return new SecdEvalException(
                "未知一元原语 '" + op + "'（操作数: " + v + "）；可用: sqr succ pred zerop");
    }
}
