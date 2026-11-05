package com.own.virtualaibox.monitor;

import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.event.DomainEvent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.event.EventListener;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.mind.AgentRuntime;
import com.own.virtualaibox.mind.DefaultPlanCompiler;
import com.own.virtualaibox.mind.MindController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证收敛 / 活锁检测。
 *
 * <p>验收点"构造'原地打转 / 周期振荡'Agent，能检出"逐一覆盖：</p>
 * <ul>
 *   <li>原地不动（含被边界钳制）→ {@code agent.stuck}；</li>
 *   <li>周期振荡 A→B→A→B… → {@code agent.loop}；</li>
 *   <li>整个世界长期无变化 → {@code world.converged}；</li>
 *   <li>SECD D 栈膨胀（无限归约启发式）→ {@code agent.loop}（reason=d-stack）。</li>
 * </ul>
 *
 * <p>检测器只消费 tick + 世界状态（Agent 位置），故测试直接驱动
 * {@link ConvergenceMonitor#monitor}，手动改写 AgentState 模拟各类运动模式。</p>
 */
class ConvergenceMonitorTest {

    /** 假预言机：与 AgentRuntimeTest 同构，避免测试触发真实网络。 */
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

    /** 全局监听：收集所有发布的事件，按事件类型计数。 */
    private static class CapturingListener implements EventListener {
        final List<DomainEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void onEvent(DomainEvent event) {
            events.add(event);
        }

        long count(String eventType) {
            return events.stream().filter(e -> e.getEventType().equals(eventType)).count();
        }
    }

    private EventBus eventBus;
    private CapturingListener listener;
    private ConvergenceMonitor monitor;
    private final FakeBrain fakeBrain = new FakeBrain();

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        listener = new CapturingListener();
        eventBus.subscribeGlobal(listener);
        monitor = new ConvergenceMonitor(eventBus, new MindController(fakeBrain));
    }

    private Agent newAgent(String id, String name, int x, int y) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName(name);
        agent.setState(new AgentState(x, y, name));
        agent.setEventHistory(new ArrayList<>());
        return agent;
    }

    @Test
    void stuckAgentAtBoundaryFiresAgentStuck() {
        World world = new World();
        Agent alice = newAgent("a1", "Alice", 0, 0);
        world.addAgent(alice);

        // 连续 4 tick 原地不动（如被世界边界钳制）
        for (int t = 1; t <= 4; t++) {
            monitor.monitor(t, world);
        }
        assertEquals(1, listener.count("agent.stuck"), "原地不动 3 tick 应触发一次卡死");
        assertTrue(listener.count("agent.loop") == 0, "固定点不是位置周期，不应触发循环");

        // 边沿触发：位置恢复移动后不再重复触发
        alice.getState().setX(1);
        monitor.monitor(5, world);
        alice.getState().setX(2);
        monitor.monitor(6, world);
        alice.getState().setX(3);
        monitor.monitor(7, world);
        assertEquals(1, listener.count("agent.stuck"), "恢复移动后不应重复触发卡死");
    }

    @Test
    void oscillatingAgentFiresAgentLoop() {
        World world = new World();
        Agent alice = newAgent("a1", "Alice", 0, 0);
        world.addAgent(alice);

        // A→B→A→B… 振荡（周期 2），位置每 tick 在 (0,0) 与 (1,0) 间往返
        int[] xs = {0, 1, 0, 1, 0, 1};
        for (int t = 1; t <= xs.length; t++) {
            alice.getState().setX(xs[t - 1]);
            monitor.monitor(t, world);
        }
        assertTrue(listener.count("agent.loop") >= 1, "周期振荡应触发循环检测");
        assertEquals(0, listener.count("agent.stuck"), "每 tick 都移动，不是卡死");
        assertEquals(0, listener.count("world.converged"), "位置持续变化，世界未收敛");
    }

    @Test
    void staticWorldFiresWorldConverged() {
        World world = new World();
        world.addAgent(newAgent("a1", "Alice", 3, 3));
        world.addAgent(newAgent("b2", "Bob", 20, 20));

        for (int t = 1; t <= 6; t++) {
            monitor.monitor(t, world);
        }
        assertEquals(1, listener.count("world.converged"), "世界长期无变化应触发一次收敛");
    }

    @Test
    void dStackGrowthFiresLoopEvent() {
        World world = new World();
        Agent alice = newAgent("a1", "Alice", 0, 0);
        Agent bob = newAgent("b2", "Bob", 1, 1);
        world.addAgent(alice);

        MindController mindController = new MindController(fakeBrain);
        mindController.decisionPhase(1, world);   // 创建 Alice 的运行时
        AgentRuntime runtime = mindController.getRuntime("a1");

        // 反复中断（不 runHandler）撑大 D 栈，模拟无限归约 / 深递归
        for (int i = 0; i < 60; i++) {
            runtime.interrupt(DefaultPlanCompiler.compileOnMeet(alice, bob));
        }
        assertEquals(60, runtime.getState().getD().size());

        monitor = new ConvergenceMonitor(eventBus, mindController);
        monitor.monitor(1, world);
        assertTrue(listener.count("agent.loop") >= 1, "D 栈过深应触发无限归约事件");
    }
}
