package com.own.virtualaibox.behaviordsl;

import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.effect.MoveEffect;
import com.own.virtualaibox.effect.RememberEffect;
import com.own.virtualaibox.effect.SpeakEffect;
import com.own.virtualaibox.mind.AgentRuntime;
import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.secd.value.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 行为 DSL 编译与运行时融合验证，覆盖条件、感知和方向移动。
 *
 * <p>验证点包括 DSL 编译、运行时执行、条件、感知和方向移动：</p>
 * <ul>
 *   <li>编译：{@code .lambda} 文本 → {@link BehaviorProgram}（plan / defs / onMeet）；</li>
 *   <li>fail-fast：语法错误、缺 plan、未知名称、def 循环都在加载期报错；</li>
 *   <li>运行时：plan 内联引用 dx/dy（LLM Oracle 注入），tick 产出 MoveEffect；</li>
 *   <li>onMeet：DSL 的 λ 闭包经 interrupt/runHandler 跑完 greet + persona；</li>
 *   <li>条件与感知：if/then/else 编译为 InstIfThenElse（真值 Scheme 风格，仅 #f 为假）；
 *       感知原语（closest/dist-to/direction-of 等）查询注入的只读快照，无对象 → #f；
 *       move north 与 move dx dy 统一为 MoveEffect(deltaX, deltaY)。</li>
 * </ul>
 */
class BehaviorCompilerTest {

    /** 固定返回向东 (1,0) 的假预言机，使测试确定性。 */
    private static class FakeBrain extends LLMBrain {
        FakeBrain() {
            super(null, com.own.virtualaibox.properties.PromptProperties.defaults(), com.own.virtualaibox.properties.RuntimeProperties.defaults());
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

    private final BehaviorCompiler compiler = new BehaviorCompiler();
    private final FakeBrain fakeBrain = new FakeBrain();

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

    // ---------------------------------------------------------- 编译期

    @Test
    void compilesDefsPlanAndOnMeet() {
        String src = """
                greet   = λ other. (speak other "你好")
                persona = λ other. (remember "met" "遇见")
                onMeet  = λ other. { greet other; persona other }
                plan    = { remember "goal" "探索"; move dx dy }
                """;

        BehaviorProgram program = compiler.compile(src, "test.lambda");

        assertNotNull(program.plan(), "plan 应编译出入口程序");
        assertNotNull(program.onMeet(), "onMeet def 应求值为闭包");
        assertEquals(3, program.defs().size(), "greet/persona/onMeet 三个 def");
        assertTrue(program.defs().containsKey("greet"));
        assertTrue(program.defs().containsKey("onMeet"));
    }

    @Test
    void onMeetMissingIsNull() {
        String src = """
                plan = { move dx dy }
                """;
        BehaviorProgram program = compiler.compile(src, "test.lambda");
        assertNull(program.onMeet(), "没有 onMeet def 时应为 null（回退内置对话）");
    }

    @Test
    void syntaxErrorReportsLine() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> compiler.compile("plan = (move 1", "bad.lambda"));
        assertTrue(ex.getMessage().contains("bad.lambda"), ex.getMessage());
    }

    @Test
    void missingPlanFails() {
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile("greet = 1", "noplan.lambda"));
    }

    @Test
    void unknownTopLevelNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile("plan = wander", "unknown.lambda"));
    }

    @Test
    void defCycleFails() {
        String src = """
                a    = λ x. b
                b    = λ y. a
                plan = { move dx dy }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> compiler.compile(src, "cycle.lambda"));
    }

    @Test
    void ifCompilesIntoInstSeq() {
        // if/then/else 编译为 InstSeq([cond, InstIfThenElse(then, else)])
        String src = """
                plan = if (gt 3 2) then (move 1 0) else (move 0 1)
                """;
        BehaviorProgram program = compiler.compile(src, "if.lambda");
        assertNotNull(program.plan(), "if 程序应正常编译");
    }

    @Test
    void letCompilesToApplication() {
        String src = """
                plan = { let z = (add dx dy) in (move z z) }
                """;
        BehaviorProgram program = compiler.compile(src, "let.lambda");
        assertNotNull(program.plan());
    }

    // ---------------------------------------------------------- 运行时

    @Test
    void planWithRuntimeVarsProducesMoveEffect() {
        String src = """
                plan = { remember "goal" "探索"; move dx dy }
                """;
        BehaviorProgram program = compiler.compile(src, "t.lambda");
        Agent agent = newAgent("a1", "Alice", 10, 10);
        AgentRuntime runtime = new AgentRuntime(agent, fakeBrain, program);

        List<Effect> effects = runtime.tick(worldState(agent, 1));

        assertTrue(effects.stream().anyMatch(e -> e instanceof RememberEffect),
                "plan 开头应产出记忆副作用");
        MoveEffect move = (MoveEffect) effects.stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(1, move.deltaX(), "dx 应来自 LLM Oracle（向东 1）");
        assertEquals(0, move.deltaY(), "dy 应来自 LLM Oracle");
    }

    @Test
    void dslOnMeetRunsGreetThenPersonaViaInterrupt() {
        String src = """
                greet   = λ other. (speak other "你好")
                persona = λ other. (remember "met" "遇见")
                onMeet  = λ other. { greet other; persona other }
                plan    = { move dx dy }
                """;
        BehaviorProgram program = compiler.compile(src, "t.lambda");
        Agent agent = newAgent("a1", "Alice", 10, 10);
        Agent other = newAgent("b2", "Bob", 10, 11);
        AgentRuntime runtime = new AgentRuntime(agent, fakeBrain, program);

        Value onMeet = program.onMeet();
        assertNotNull(onMeet);
        runtime.interrupt(new InstApp(
                new InstConst(onMeet),
                new InstConst(new AgentRefValue(other.getId(), other.getName()))));

        List<Effect> effects = runtime.runHandler(200);

        assertTrue(effects.stream().anyMatch(e -> e instanceof SpeakEffect),
                "onMeet 的 greet 应产出说话副作用");
        assertTrue(effects.stream().anyMatch(e -> e instanceof RememberEffect),
                "onMeet 的 persona 应产出记忆副作用（嵌套闭包续体应在 runHandler 内跑完）");
        assertTrue(runtime.getState().getD().isEmpty(),
                "对话结束后 D 栈应清空（主计划已恢复）");
    }

        // ---------------------------------------------------------- 条件/感知/方向

    @Test
    void ifCompilesAndRunsThenBranch() {
        // (gt 3 2) 为真 → 走 then 分支 (move 1 0)
        String src = """
                plan = if (gt 3 2) then (move 1 0) else (move 0 1)
                """;
        Agent agent = newAgent("a1", "Alice", 10, 10);
        AgentRuntime runtime = new AgentRuntime(
                agent, fakeBrain, compiler.compile(src, "t.lambda"));

        MoveEffect move = (MoveEffect) runtime.tick(worldState(agent, 1)).stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(1, move.deltaX(), "gt 3 2 为真 → then 分支");
        assertEquals(0, move.deltaY());
    }

    @Test
    void ifPerceptionBranchesOnClosest() {
        // 范围内有其他 Agent 时 (closest) 为真 → then 分支对最近者说话
        String src = """
                plan = if (closest) then (speak (closest) "hi") else (move 1 0)
                """;
        Agent alice = newAgent("a1", "Alice", 10, 10);
        Agent bob = newAgent("b2", "Bob", 10, 11);
        AgentRuntime runtime = new AgentRuntime(
                alice, fakeBrain, compiler.compile(src, "t.lambda"));
        WorldState ws = new WorldState(1, Map.of(
                alice.getId(), alice.getState(), bob.getId(), bob.getState()));

        List<Effect> effects = runtime.tick(ws);

        assertTrue(effects.stream().anyMatch(e -> e instanceof SpeakEffect),
                "Bob 距离 1 ≤ 感知范围 5，(closest) 为真 → 说话而非移动");
    }

    @Test
    void closestReturnsFalseWhenAlone() {
        // 感知范围内无其他 Agent → (closest) 为 #f → else 分支移动
        String src = """
                plan = if (closest) then (speak (closest) "hi") else (move 1 0)
                """;
        Agent alice = newAgent("a1", "Alice", 10, 10);
        AgentRuntime runtime = new AgentRuntime(
                alice, fakeBrain, compiler.compile(src, "t.lambda"));

        MoveEffect move = (MoveEffect) runtime.tick(worldState(alice, 1)).stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(1, move.deltaX(), "无邻居 → (closest)=#f → else 分支 (move 1 0)");
        assertEquals(0, move.deltaY());
    }

    @Test
    void moveByDirection() {
        String src = """
                plan = { move north }
                """;
        Agent agent = newAgent("a1", "Alice", 10, 10);
        AgentRuntime runtime = new AgentRuntime(
                agent, fakeBrain, compiler.compile(src, "t.lambda"));

        MoveEffect move = (MoveEffect) runtime.tick(worldState(agent, 1)).stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        assertEquals(0, move.deltaX());
        assertEquals(-1, move.deltaY(), "north → dy = -1");
    }

    @Test
    void moveUnifiedEffectContract() {
        // 方向移动与整数移动统一为 MoveEffect(deltaX, deltaY)（docs §17.4，Effect 层无重载）
        Agent a1 = newAgent("a1", "Alice", 10, 10);
        Agent a2 = newAgent("a2", "Ace", 10, 10);
        AgentRuntime rDir = new AgentRuntime(
                a1, fakeBrain, compiler.compile("plan = { move north }", "d.lambda"));
        AgentRuntime rInt = new AgentRuntime(
                a2, fakeBrain, compiler.compile("plan = { move 0 -1 }", "i.lambda"));

        MoveEffect mDir = (MoveEffect) rDir.tick(worldState(a1, 1)).stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();
        MoveEffect mInt = (MoveEffect) rInt.tick(worldState(a2, 1)).stream()
                .filter(e -> e instanceof MoveEffect).findFirst().orElseThrow();

        assertEquals(0, mDir.deltaX());
        assertEquals(-1, mDir.deltaY());
        assertEquals(mInt.deltaX(), mDir.deltaX(), "方向移动与整数移动产出同一 MoveEffect");
        assertEquals(mInt.deltaY(), mDir.deltaY());
    }

    @Test
    void ifInsideHandlerAndInterrupt() {
        // if 出现在 handler 闭包内：C 栈同构，与中断/恢复兼容
        String src = """
                greet  = λ other. (speak other "你好")
                persona = λ other. (remember "met" "遇见")
                onMeet = λ other. { if (gt 1 0) then (greet other) else (move 0 0); persona other }
                plan   = { move dx dy }
                """;
        BehaviorProgram program = compiler.compile(src, "t.lambda");
        Agent agent = newAgent("a1", "Alice", 10, 10);
        Agent other = newAgent("b2", "Bob", 10, 11);
        AgentRuntime runtime = new AgentRuntime(agent, fakeBrain, program);

        Value onMeet = program.onMeet();
        assertNotNull(onMeet);
        runtime.interrupt(new InstApp(
                new InstConst(onMeet),
                new InstConst(new AgentRefValue(other.getId(), other.getName()))));

        List<Effect> effects = runtime.runHandler(200);

        assertTrue(effects.stream().anyMatch(e -> e instanceof SpeakEffect),
                "if 真 → greet 说话");
        assertTrue(effects.stream().anyMatch(e -> e instanceof RememberEffect),
                "if 之后 persona 继续执行（不产生错误的嵌套状态）");
        assertTrue(runtime.getState().getD().isEmpty(),
                "handler 结束后 D 栈清空（主计划恢复）");
    }
}
