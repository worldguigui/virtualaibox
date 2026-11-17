package com.own.virtualaibox.mind;

import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.effect.LLMRequestEffect;
import com.own.virtualaibox.effect.MoveEffect;
import com.own.virtualaibox.effect.RememberEffect;
import com.own.virtualaibox.effect.SpeakEffect;
import com.own.virtualaibox.secd.ArithmeticOpEvaluator;
import com.own.virtualaibox.secd.MachineState;
import com.own.virtualaibox.secd.OpEvaluator;
import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.secd.value.BoolValue;
import com.own.virtualaibox.secd.value.DirectionValue;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.PartialOpValue;
import com.own.virtualaibox.secd.value.StringValue;
import com.own.virtualaibox.secd.value.Value;
import com.own.virtualaibox.secd.value.VoidValue;

import java.util.List;

/**
 * 世界原语求值器：在纯算术之上注入世界副作用原语。
 *
 * 原语语义以 λ 操作符实现：
 *   move dx dy —— 二元，产生 MoveEffect
 *   speak target content —— 二元，产生 SpeakEffect
 *   remember key content —— 二元，产生 RememberEffect
 *   ask-llm prompt —— 一元，同步调用预言机，把结果文本压入 S
 *
 * 每个 Agent 持有一份独立实例（绑定自身上下文）。纯算术原语委托给
 * ArithmeticOpEvaluator；move/speak/remember 的第一次应用由算术求值器
 * 压成 PartialOpValue，第二次应用在这里完成并产出副作用。
 */
public class WorldOpEvaluator implements OpEvaluator {

    private final Agent agent;
    private final LLMBrain llmBrain;
    private final ArithmeticOpEvaluator arithmetic = new ArithmeticOpEvaluator();
    // 感知（每 tick 由 AgentRuntime 注入的世界只读快照）；null 表示纯算术上下文。
    private Perception perception;

    public WorldOpEvaluator(Agent agent, LLMBrain llmBrain) {
        this.agent = agent;
        this.llmBrain = llmBrain;
    }

    // 注入本 tick 的感知快照（AgentRuntime 每 tick 调用一次）。
    public void setPerception(Perception perception) {
        this.perception = perception;
    }

    @Override
    public List<Effect> apply(MachineState state, Value func, Value arg) {
        if (func instanceof OpValue op) {
            switch (op.op) {
                // ask-llm 是一元原语，直接拦截执行
                case "ask-llm" -> {
                    return askLlm(state, arg);
                }
                // 感知原语（一元，世界只读，null → #f）
                case "self" -> {
                    pushValue(state, perceptionRequired().self());
                    return List.of();
                }
                case "closest" -> {
                    AgentRefValue a = perceptionRequired().closestOther();
                    pushValue(state, a == null ? new BoolValue(false) : a);
                    return List.of();
                }
                case "dist-to" -> {
                    int d = perceptionRequired().distTo(toAgentRef(arg));
                    pushValue(state, d == Integer.MAX_VALUE ? new BoolValue(false) : new IntValue(d));
                    return List.of();
                }
                case "name-of" -> {
                    pushValue(state, new StringValue(perceptionRequired().nameOf(toAgentRef(arg))));
                    return List.of();
                }
                case "direction-of" -> {
                    String d = perceptionRequired().directionOf(toAgentRef(arg));
                    pushValue(state, d == null ? new BoolValue(false) : new DirectionValue(d));
                    return List.of();
                }
                // 方向移动：一元立即完成，统一生成 MoveEffect(deltaX, deltaY)
                case "move" -> {
                    if (arg instanceof DirectionValue d) {
                        return moveByDirection(state, d);
                    }
                }
                default -> {
                    // 其余 OpValue（含 move 整数参数）→ 柯里化二元
                }
            }
            return arithmetic.apply(state, func, arg);
        }
        // 柯里化二元原语的完成态：move/speak/remember 的第二次应用
        if (func instanceof PartialOpValue partial) {
            switch (partial.op) {
                case "move" -> {
                    return move(state, partial.firstArg, arg);
                }
                case "speak" -> {
                    return speak(state, partial.firstArg, arg);
                }
                case "remember" -> {
                    return remember(state, partial.firstArg, arg);
                }
                default -> {
                    return arithmetic.apply(state, func, arg);
                }
            }
        }
        // 其余委托给纯算术（压成 PartialOpValue 或比较原语）
        return arithmetic.apply(state, func, arg);
    }

    // ------------------------------------------------------------------ 感知辅助

    // 感知原语必须运行在有 Perception 注入的 tick 内（fail-fast，docs §17.4）。
    private Perception perceptionRequired() {
        if (perception == null) {
            throw new IllegalStateException(
                    "感知原语（self/closest/dist-to/name-of/direction-of）只能在注入 Perception 的 tick 内调用");
        }
        return perception;
    }

    // 把实参转成 AgentRefValue；非 AgentRef → 返回 #f 的哨兵处理交给各查询（按 id 查无 → null/MAX）。
    private AgentRefValue toAgentRef(Value v) {
        if (v instanceof AgentRefValue ref) {
            return ref;
        }
        // 非 AgentRef 实参：构造一个"查无此人"的引用，感知查询会返回 null/哨兵 → #f
        return new AgentRefValue(v.toString(), v.toString());
    }

    private void pushValue(MachineState state, Value v) {
        state.getS().push(v);
    }

    private List<Effect> moveByDirection(MachineState state, DirectionValue d) {
        state.getS().push(new VoidValue());
        int[] delta = deltaOf(d.dir);
        if (delta == null) {
            return List.of();
        }
        return List.of(new MoveEffect(agent.getId(), delta[0], delta[1], "SECD move " + d.dir));
    }

    // 方向 → delta 映射（docs §17.4：转换只发生在 DSL 原语层，Effect 层无方向重载）。
    private int[] deltaOf(String dir) {
        return switch (dir) {
            case "north" -> new int[]{0, -1};
            case "east" -> new int[]{1, 0};
            case "south" -> new int[]{0, 1};
            case "west" -> new int[]{-1, 0};
            default -> null;
        };
    }

    private List<Effect> move(MachineState state, Value dx, Value dy) {
        state.getS().push(new VoidValue());
        if (dx instanceof IntValue ix && dy instanceof IntValue iy) {
            return List.of(new MoveEffect(agent.getId(), ix.val, iy.val, "SECD move"));
        }
        return List.of();
    }

    private List<Effect> speak(MachineState state, Value target, Value content) {
        state.getS().push(new VoidValue());
        String targetId = target instanceof AgentRefValue ref ? ref.agentId : null;
        String text = content instanceof StringValue s ? s.text : content.toString();
        return List.of(new SpeakEffect(agent.getId(), targetId, text));
    }

    private List<Effect> remember(MachineState state, Value key, Value content) {
        state.getS().push(new VoidValue());
        String k = key instanceof StringValue s ? s.text : key.toString();
        String c = content instanceof StringValue s ? s.text : content.toString();
        return List.of(new RememberEffect(agent.getId(), k, c));
    }

    private List<Effect> askLlm(MachineState state, Value prompt) {
        String p = prompt instanceof StringValue s ? s.text : prompt.toString();
        String result = llmBrain.chat(p);
        state.getS().push(new StringValue(result));
        return List.of(new LLMRequestEffect(agent.getId(), p, result));
    }
}
