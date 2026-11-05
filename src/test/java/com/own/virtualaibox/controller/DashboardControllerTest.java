package com.own.virtualaibox.controller;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.core.TickSchedule;
import com.own.virtualaibox.core.VirtualClock;
import com.own.virtualaibox.core.WorldEngine;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.memory.InMemoryMemoryStore;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.mind.MindController;
import com.own.virtualaibox.monitor.ConvergenceMonitor;

class DashboardControllerTest {

    private DashboardController controller;
    private WorldEngine worldEngine;

    @BeforeEach
    void setUp() {
        EventBus eventBus = new EventBus();
        VirtualClock virtualClock = mock(VirtualClock.class);
        when(virtualClock.getTick()).thenReturn(0);
        worldEngine = new WorldEngine(virtualClock, null, eventBus, new InMemoryMemoryStore());
        worldEngine.init();
        MindController mindController = new MindController(new FakeBrain());
        controller = new DashboardController(
                worldEngine,
                mindController,
                new ConvergenceMonitor(eventBus, mindController),
                com.own.virtualaibox.properties.DashboardProperties.defaults(),
                com.own.virtualaibox.properties.RuntimeProperties.defaults());
    }

    @Test
    void dashboardReturnsConsistentWorldAndResidentShapes() {
        Map<String, Object> dashboard = controller.dashboard(30, 8);

        Map<?, ?> world = (Map<?, ?>) dashboard.get("world");
        List<?> agents = (List<?>) dashboard.get("agents");
        assertEquals(2, world.get("agentCount"));
        assertEquals(2, agents.size());
        assertEquals(2, ((Map<?, ?>) dashboard.get("metrics")).get("agentCount"));

        Map<?, ?> firstAgent = (Map<?, ?>) agents.get(0);
        assertNotNull(firstAgent.get("id"));
        assertNotNull(firstAgent.get("name"));
        assertNotNull(firstAgent.get("personality"));
        assertNotNull(firstAgent.get("memoryStats"));
        assertNotNull(firstAgent.get("recentMemories"));
    }

    @Test
    void addAgentReturnsCreatedResidentAndUpdatedWorld() {
        Map<String, Object> response = controller.addAgent(new AgentCreateRequest("Clara", 4, 9));

        Map<?, ?> created = (Map<?, ?>) response.get("agent");
        Map<?, ?> world = (Map<?, ?>) response.get("world");
        List<?> agents = (List<?>) response.get("agents");
        assertEquals("Clara", created.get("name"));
        assertEquals(4, created.get("x"));
        assertEquals(9, created.get("y"));
        assertEquals(3, world.get("agentCount"));
        assertEquals(3, agents.size());
    }

    @Test
    void addAgentPreservesCustomPersonalityProfile() {
        var profile = new com.own.virtualaibox.domain.agent.PersonalityProfile(
                "守望者",
                "沉稳、耐心",
                "低声细语，措辞慎重",
                "保护村民并记录关键信息",
                "重视信任与秩序",
                "只相信已亲历的事实和可核验的证据");

        Map<String, Object> response = controller.addAgent(new AgentCreateRequest("Nina", 7, 11, profile));

        Map<?, ?> created = (Map<?, ?>) response.get("agent");
        com.own.virtualaibox.domain.agent.PersonalityProfile personality =
                (com.own.virtualaibox.domain.agent.PersonalityProfile) created.get("personality");
        assertEquals("守望者", personality.getRole());
        assertEquals("沉稳、耐心", personality.getTemperament());
        assertEquals("Nina", created.get("name"));
    }

    @Test
    void stepReturnsWorldAndFullResidentShape() {
        TickSchedule tickSchedule = mock(TickSchedule.class);
        VirtualClock virtualClock = mock(VirtualClock.class);
        when(virtualClock.getTick()).thenReturn(1);
        EventBus eventBus = new EventBus();
        WorldEngine worldEngine = new WorldEngine(virtualClock, tickSchedule, eventBus, new InMemoryMemoryStore());
        worldEngine.init();
        MindController mindController = new MindController(new FakeBrain());
        DashboardController stepController = new DashboardController(
                worldEngine,
                mindController,
                new ConvergenceMonitor(eventBus, mindController),
                com.own.virtualaibox.properties.DashboardProperties.defaults(),
                com.own.virtualaibox.properties.RuntimeProperties.defaults());

        Map<String, Object> response = stepController.step();

        assertEquals(1, response.get("tick"));
        Map<?, ?> world = (Map<?, ?>) response.get("world");
        List<?> agents = (List<?>) response.get("agents");
        assertEquals(2, world.get("agentCount"));
        assertEquals(2, agents.size());
        assertNotNull(((Map<?, ?>) agents.get(0)).get("memoryStats"));
    }

    @Test
    void updateAgentUpdatesExistingResidentProfile() {
        String aliceId = worldEngine.getWorld().getAgents().stream()
                .filter(agent -> "Alice".equalsIgnoreCase(agent.getName()))
                .findFirst()
                .orElseThrow()
                .getId();

        AgentUpdateRequest request = new AgentUpdateRequest(
                "Alice",
                12,
                8,
                new com.own.virtualaibox.domain.agent.PersonalityProfile(
                        "观察员",
                        "沉静而专注",
                        "慢条理、低调",
                        "记录事实并观察变化",
                        "重视秩序与证据",
                        "只相信已核验与已记录的细节"));

        Map<String, Object> response = controller.updateAgent(aliceId, request);

        Map<?, ?> updated = (Map<?, ?>) response.get("agent");
        assertEquals("Alice", updated.get("name"));
        assertEquals(12, updated.get("x"));
        assertEquals(8, updated.get("y"));
        assertNotNull(updated.get("personality"));
    }

    @Test
    void invalidAgentRequestIsMappedToBadRequestPayload() {
        Map<String, Object> response = controller.handleInvalidRequest(
                new IllegalArgumentException("agent name already exists"));

        assertEquals("agent name already exists", response.get("error"));
    }

    @Test
    void nullCreateRequestUsesWorldDefaults() {
        Map<String, Object> response = controller.addAgent(null);

        Map<?, ?> created = (Map<?, ?>) response.get("agent");
        Map<?, ?> world = (Map<?, ?>) response.get("world");
        assertNotNull(created.get("name"));
        assertEquals(3, world.get("agentCount"));
    }

    private static class FakeBrain extends LLMBrain {
        FakeBrain() {
            super(null, com.own.virtualaibox.properties.PromptProperties.defaults(), com.own.virtualaibox.properties.RuntimeProperties.defaults());
        }

        @Override
        public MoveAction decideAction(Agent agent, WorldState worldState) {
            MoveAction action = new MoveAction();
            action.setAgentId(agent.getId());
            action.setDeltaX(1);
            action.setDeltaY(0);
            action.setReason("test");
            return action;
        }
    }
}
