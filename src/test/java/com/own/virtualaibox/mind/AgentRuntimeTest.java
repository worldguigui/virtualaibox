package com.own.virtualaibox.mind;

import com.own.virtualaibox.behaviordsl.BehaviorProgram;
import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.effect.LLMRequestEffect;
import com.own.virtualaibox.effect.MoveEffect;
import com.own.virtualaibox.effect.RememberEffect;
import com.own.virtualaibox.effect.SpeakEffect;
import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.InstSeq;
import com.own.virtualaibox.secd.Instruction;
import com.own.virtualaibox.secd.MachineState;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.StringValue;
import com.own.virtualaibox.secd.value.Value;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 SECD 心智运行时产出 Effect，且世界副作用与计算隔离。
 *
 * <p>验证点包括默认计划、跨 tick 执行和副作用隔离：</p>
 * <ul>
 *   <li>心智驱动：AgentRuntime 每 tick 产出移动副作用，不再由 LLM 直接决定每个动作；</li>
 *   <li>多 tick 计划：行为程序驻留 C 栈，每 tick 只推进一步动作，耗尽后自动重编译；</li>
 *   <li>副作用隔离：WorldOpEvaluator 只返回 Effect，不修改任何世界状态。</li>
 * </ul>
 *
 * <p>用 FakeBrain（固定方向）替代真实 LLM，使测试确定性可重复。</p>
 */
class AgentRuntimeTest {

    /** 固定返回向东 (1,0) 的假预言机。 */
    private static class FakeBrain extends LLMBrain {
        FakeBrain() {
            super(null, com.own.virtualaibox.properties.PromptProperties.defaults(), com.own.virtualaibox.properties.RuntimeProperties.defaults()); // 不接真实 ChatModel；所有方法被重写，不会触发网络调用
        }

        @Override
        public MoveAction decideAction(Agent agent, WorldState worldState) {
            MoveAction a = new MoveAction();
            a.setAgentId(agent.getId());
            a.setDeltaX(1);
            a.setDeltaY(0);
            a.setReason("fake: head east");
            return a;
        }

        @Override
        public String chat(String prompt) {
            return "fake-llm-reply";
        }
    }

    private final FakeBrain fakeBrain = new FakeBrain();

    private AgentRuntime newRuntime(Agent agent, BehaviorProgram program) {
        return new AgentRuntime(agent, fakeBrain, program);
    }

    /**
     * 显式构造"1 记忆写入 + steps 步移动"的多步行为程序。
     *
     * <p>与 {@code DefaultPlanCompiler.PATH_LENGTH} 常量解耦：PATH_LENGTH 已是 1（默认计划单步耗尽），
     * 而"计划跨 tick 驻留 / 耗尽重编译 / D 栈中断恢复"这些行为需要多步计划才能验证。</p>
     */
    private static BehaviorProgram multiStepPlan(int steps) {
        List<Instruction> instrs = new ArrayList<>();
        instrs.add(app(appOp("remember", constStr("goal")), constStr("head-1,0")));
        for (int i = 0; i < steps; i++) {
            instrs.add(app(appOp("move", constInt(1)), constInt(0)));
        }
        return new BehaviorProgram(new InstSeq(instrs), Map.of(), null);
    }

    private static InstApp app(Instruction rator, Instruction rand) {
        return new InstApp(rator, rand);
    }

    private static InstApp appOp(String op, Instruction rand) {
        return app(new InstConst(new OpValue(op)), rand);
    }

    private static InstConst constInt(int v) {
        return new InstConst(new IntValue(v));
    }

    private static InstConst constStr(String s) {
        return new InstConst(new StringValue(s));
    }

    private Agent newAgent(String id, String name, int x, int y) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setState(new AgentState(x, y, name));
        return agent;
    }

    private WorldState worldState(Agent agent, int tick) {
        return new WorldState(tick, Map.of(agent.getId(), agent.getState()));
    }

    @Test
    void firstTickProducesRememberThenMove() {
        Agent agent = newAgent("a1", "Alice", 10, 10);
        // 显式注入 3 步计划（不依赖 PATH_LENGTH；PATH_LENGTH=1 时默认计划仅 1 步、首 tick 即耗尽）
        AgentRuntime runtime = newRuntime(agent, multiStepPlan(3));

        List<Effect> effects = runtime.tick(worldState(agent, 1));

        // 计划开头是记忆写入，随后是一步移动
        assertTrue(effects.stream().anyMatch(e -> e instanceof RememberEffect),
                "计划开头应产出记忆副作用");
        MoveEffect move = (MoveEffect) effects.stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(1, move.deltaX());
        assertEquals(0, move.deltaY());

        // 一步动作后计划未耗尽：C 栈仍驻留剩余路径（3 步计划走 1 步剩 2 步）
        assertFalse(runtime.isIdle(), "首 tick 后应还有剩余计划步");
    }

    @Test
    void planPersistsAcrossTicksAndReloads() {
        Agent agent = newAgent("a1", "Alice", 10, 10);
        // 显式 3 步计划：验证计划跨 tick 驻留 + 耗尽后自动重编译（与 PATH_LENGTH 常量解耦）
        AgentRuntime runtime = newRuntime(agent, multiStepPlan(3));

        // tick1~3：每 tick 恰好一个移动动作，3 步走完计划耗尽
        for (int t = 1; t <= 3; t++) {
            List<Effect> effects = runtime.tick(worldState(agent, t));
            long moves = effects.stream().filter(e -> e instanceof MoveEffect).count();
            assertEquals(1, moves, "tick " + t + " 应恰好一步移动");
        }
        // 计划耗尽
        assertTrue(runtime.isIdle(), "3 步路径走完后应处于空闲（计划耗尽）");

        // tick4：空闲 → 自动重编译新计划，继续有动作
        List<Effect> effects = runtime.tick(worldState(agent, 4));
        assertTrue(effects.stream().anyMatch(e -> e instanceof MoveEffect),
                "空闲后应自动重编译新计划并产出移动副作用");
        assertFalse(runtime.isIdle(), "重编译后不应再空闲");
    }

    @Test
    void interruptResumesMainPlanViaDStack() {
        Agent agent = newAgent("a1", "Alice", 10, 10);
        Agent other = newAgent("b2", "Bob", 10, 11);
        // 显式 3 步计划：走 1 步后 C 栈仍驻留 2 步，供中断挂起（与 PATH_LENGTH 常量解耦）
        AgentRuntime runtime = newRuntime(agent, multiStepPlan(3));

        // 主计划走一步（remember + move1），C 栈仍驻留剩余路径
        runtime.tick(worldState(agent, 1));
        assertTrue(runtime.getState().getC().size() > 0, "主计划应驻留于 C 栈");

        // 相遇 → 中断主计划，现场压入 D 栈
        runtime.interrupt(DefaultPlanCompiler.compileOnMeet(agent, other));
        assertEquals(1, runtime.getState().getD().size(), "中断后 D 栈应有中断帧");

        // 处理中断：跑完 onMeet 对话（λ 闭包应用会再占一帧），随后解开全部续体恢复主计划
        List<Effect> handlerEffects = runtime.runHandler(200);
        assertTrue(handlerEffects.stream().anyMatch(e -> e instanceof SpeakEffect),
                "onMeet 对话应产出说话副作用");
        assertTrue(handlerEffects.stream().anyMatch(e -> e instanceof RememberEffect),
                "onMeet 对话应产出记忆副作用");
        assertTrue(runtime.getState().getD().isEmpty(), "对话结束后 D 栈应清空（中断帧已解开）");

        // 主计划已恢复：下一 tick 继续剩余路径（方向不变，不再重新决策）
        List<Effect> resumed = runtime.tick(worldState(agent, 2));
        MoveEffect move = (MoveEffect) resumed.stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(1, move.deltaX());
        assertEquals(0, move.deltaY());
    }

    @Test
    void worldOpEvaluatorProducesEffectsNotWorldMutation() {
        Agent agent = newAgent("a1", "Alice", 10, 10);
        WorldOpEvaluator evaluator = new WorldOpEvaluator(agent, fakeBrain);
        MachineState state = new MachineState();

        // move 原语：((move 1) 0) -> MoveEffect
        List<Effect> moveEffects = evaluator.apply(state,
                new com.own.virtualaibox.secd.value.PartialOpValue("move",
                        new com.own.virtualaibox.secd.value.IntValue(1)),
                new com.own.virtualaibox.secd.value.IntValue(0));
        assertInstanceOf(MoveEffect.class, moveEffects.get(0));

        // speak 原语：(speak target) content -> SpeakEffect
        List<Effect> speakEffects = evaluator.apply(state,
                new com.own.virtualaibox.secd.value.PartialOpValue("speak",
                        new com.own.virtualaibox.secd.value.AgentRefValue("b2", "Bob")),
                new StringValue("hello"));
        assertInstanceOf(SpeakEffect.class, speakEffects.get(0));

        // remember 原语：(remember key) content -> RememberEffect
        List<Effect> rememberEffects = evaluator.apply(state,
                new com.own.virtualaibox.secd.value.PartialOpValue("remember",
                        new StringValue("goal")),
                new StringValue("head-east"));
        assertInstanceOf(RememberEffect.class, rememberEffects.get(0));

        // ask-llm 原语：同步咨询预言机，把结果压入 S，返回信息性 Effect
        List<Effect> llmEffects = evaluator.apply(state,
                new com.own.virtualaibox.secd.value.OpValue("ask-llm"),
                new StringValue("what now?"));
        assertInstanceOf(LLMRequestEffect.class, llmEffects.get(0));
        Value top = state.getS().peek();
        assertTrue(top instanceof StringValue);
        assertEquals("fake-llm-reply", ((StringValue) top).text);

        // 全程未触碰 Agent 位置（副作用隔离）
        assertEquals(10, agent.getState().getX());
        assertEquals(10, agent.getState().getY());
    }
}
