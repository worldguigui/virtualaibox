package com.own.virtualaibox.core;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.memory.InMemoryMemoryStore;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.domain.world.WorldConfig;
import com.own.virtualaibox.domain.world.WorldItemDefinition;

class WorldEngineTest {

    private WorldEngine worldEngine;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new EventBus();
        worldEngine = new WorldEngine(
            null,
                null,
                eventBus,
                new InMemoryMemoryStore());
    }

    @Test
    void initCreatesDefaultResidentsAndRegistersListeners() {
        worldEngine.init();

        World world = worldEngine.getWorld();
        assertEquals(2, world.getAgents().size());
        assertEquals(2, eventBus.getSubscriberCount());
        assertEquals(2, world.getAgents().stream().filter(agent -> agent.getMemory() != null).count());
        assertEquals("探索者", world.getAgents().get(0).getPersonality().getRole());
        assertEquals("社区居民", world.getAgents().get(1).getPersonality().getRole());
    }

    @Test
    void initIsIdempotent() {
        worldEngine.init();
        worldEngine.init();

        assertEquals(2, worldEngine.getWorld().getAgents().size());
        assertEquals(2, eventBus.getSubscriberCount());
    }

    @Test
    void createAndAddAgentUsesRequestPositionAndRegistersResident() {
        worldEngine.init();

        Agent agent = worldEngine.createAndAddAgent("Clara", 4, 9);

        assertNotNull(agent.getId());
        assertEquals("Clara", agent.getName());
        assertEquals(4, agent.getState().getX());
        assertEquals(9, agent.getState().getY());
        assertEquals(3, worldEngine.getWorld().getAgents().size());
        assertEquals(3, eventBus.getSubscriberCount());
    }

    @Test
    void createAndAddAgentRejectsDuplicateNamesAndOutsidePositions() {
        worldEngine.init();

        assertThrows(IllegalArgumentException.class,
                () -> worldEngine.createAndAddAgent("alice", 4, 4));
        assertThrows(IllegalArgumentException.class,
                () -> worldEngine.createAndAddAgent("Clara", 37, 4));
        assertThrows(IllegalArgumentException.class,
                () -> worldEngine.createAndAddAgent("Dana", 4, -1));
    }

    @Test
    void createAndAddAgentAssignsAnAvailablePositionWhenCoordinatesAreMissing() {
        worldEngine.init();

        Agent agent = worldEngine.createAndAddAgent(null, null, null);

        assertEquals("居民-3", agent.getName());
        assertEquals(10, agent.getState().getX());
        assertEquals(8, agent.getState().getY());
    }

    @Test
    void addAgentRejectsNullAndDuplicateIds() {
        worldEngine.init();

        assertThrows(NullPointerException.class, () -> worldEngine.addAgent(null));
        Agent existing = worldEngine.getWorld().getAgents().get(0);
        Agent duplicate = new Agent(existing.getId(), "Duplicate", existing.getState(), null, null, true);

        assertThrows(IllegalArgumentException.class, () -> worldEngine.addAgent(duplicate));
    }

    @Test
    void createAndAddAgentAcceptsWorldBoundaryCoordinates() {
        worldEngine.init();

        Agent agent = worldEngine.createAndAddAgent("Boundary", 0, 36);

        assertEquals(0, agent.getState().getX());
        assertEquals(36, agent.getState().getY());
    }

    @Test
    void defaultPersonalityComesFromConfiguredResidents() {
        worldEngine.init();

        Agent first = worldEngine.getWorld().getAgents().get(0);
        assertEquals(WorldConfig.defaultConfig().getDefaultResidents().get(0).getPersonality(),
                first.getPersonality());
    }

    @Test
    void addAgentRejectsMissingIdentityAndState() {
        worldEngine.init();

        Agent missingId = new Agent(null, "Missing ID", new com.own.virtualaibox.domain.agent.AgentState(1, 1, "Missing ID"), null, null, true);
        Agent missingState = new Agent("missing-state", "Missing State", null, null, null, true);

        assertThrows(IllegalArgumentException.class, () -> worldEngine.addAgent(missingId));
        assertThrows(IllegalArgumentException.class, () -> worldEngine.addAgent(missingState));
    }

    @Test
    void updateWorldConfigAppliesDimensionsRulesAndItems() {
        worldEngine.init();
        WorldConfig config = WorldConfig.defaultConfig();
        config.setName("Configured Town");
        config.setWidth(50);
        config.setHeight(45);
        config.setRules(Map.of("movement", false, "itemSpawning", true));
        config.setItems(List.of(new WorldItemDefinition("berry", "浆果", "food", 20, 4, "可食用资源")));

        WorldConfig updated = worldEngine.updateWorldConfig(config);

        assertEquals("Configured Town", worldEngine.getWorld().getName());
        assertEquals(50, worldEngine.getWorld().getWidth());
        assertEquals(false, worldEngine.getWorld().getRules().get("movement"));
        assertEquals(1, worldEngine.getWorld().getItems().size());
        assertEquals("berry", updated.getItems().get(0).getId());
    }

    @Test
    void updateWorldConfigRejectsDimensionsThatExcludeExistingResidents() {
        worldEngine.init();
        WorldConfig config = WorldConfig.defaultConfig();
        config.setWidth(5);

        assertThrows(IllegalArgumentException.class, () -> worldEngine.updateWorldConfig(config));
        assertEquals(37, worldEngine.getWorld().getWidth());
    }
}
