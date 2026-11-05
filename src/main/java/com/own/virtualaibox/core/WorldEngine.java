package com.own.virtualaibox.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.agent.PersonalityProfile;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.memory.AgentMemory;
import com.own.virtualaibox.domain.memory.MemoryStore;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.domain.world.WorldConfig;
import com.own.virtualaibox.properties.RuntimeProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WorldEngine {

    private final VirtualClock virtualClock;
    private final TickSchedule tickSchedule;
    private final EventBus eventBus;
    private final MemoryStore memoryStore;
    /** 运行参数（通用兜底人格等）。 */
    private final RuntimeProperties runtime;
    private WorldConfig worldConfig;
    private final World world;

    public WorldEngine(VirtualClock virtualClock, TickSchedule tickSchedule,
                       EventBus eventBus, MemoryStore memoryStore) {
        this(virtualClock, tickSchedule, eventBus, memoryStore, WorldConfig.defaultConfig(),
                RuntimeProperties.defaults());
    }

    @Autowired
    public WorldEngine(VirtualClock virtualClock, TickSchedule tickSchedule,
                       EventBus eventBus, MemoryStore memoryStore, WorldConfig worldConfig,
                       RuntimeProperties runtime) {
        this.virtualClock = virtualClock;
        this.tickSchedule = tickSchedule;
        this.eventBus = eventBus;
        this.memoryStore = memoryStore;
        this.runtime = runtime;
        this.worldConfig = worldConfig == null ? WorldConfig.defaultConfig() : worldConfig;
        this.worldConfig.validate();
        this.world = new World(this.worldConfig);
    }

    @PostConstruct
    public void init() {
        log.info("WorldEngine: Initializing...");
        if (world.getAgents().isEmpty()) {
            for (WorldConfig.DefaultResident resident : worldConfig.getDefaultResidents()) {
                createAndAddAgent(resident.getName(), resident.getX(), resident.getY(),
                        resident.getPersonality());
            }
        }

        log.info("WorldEngine: Initialized with {} agents", world.getAgents().size());
        log.info("WorldEngine: Event listeners registered: {}", eventBus.getSubscriberCount());
    }

    public void step() {
        log.info("WorldEngine: Starting new tick...");
        
        virtualClock.stepForward();
        int currentTick = getCurrentTick();
        
        tickSchedule.processTick(currentTick, world);
        
        log.info("WorldEngine: Tick {} completed", currentTick);
    }

    public void addAgent(Agent agent) {
        Objects.requireNonNull(agent, "agent must not be null");
        if (agent.getId() == null || agent.getId().isBlank()) {
            throw new IllegalArgumentException("agent id must not be blank");
        }
        if (agent.getState() == null) {
            throw new IllegalArgumentException("agent state must not be null");
        }
        validatePosition(agent.getState().getX(), agent.getState().getY());
        if (world.getAgents().stream().anyMatch(existing -> existing.getId().equals(agent.getId()))) {
            throw new IllegalArgumentException("agent id already exists: " + agent.getId());
        }
        if (agent.getMemory() == null) {
            agent.setMemory(new AgentMemory(agent.getId(), memoryStore));
        }
        world.addAgent(agent);
        eventBus.subscribeGlobal(agent);
        log.info("WorldEngine: Added agent {} with id {}", agent.getName(), agent.getId());
    }

    /** 创建并加入一个拥有独立记忆和事件监听能力的居民。 */
    public Agent createAndAddAgent(String name, Integer x, Integer y) {
        return createAndAddAgent(name, x, y, null);
    }

    /** 更新现有 Agent 的基本信息与人格。 */
    public Agent updateAgent(String agentId, String name, Integer x, Integer y, PersonalityProfile personality) {
        Agent agent = world.getAgents().stream()
                .filter(existing -> existing.getId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("agent not found: " + agentId));

        String nextName = name == null || name.isBlank() ? agent.getName() : name.trim();
        if (!nextName.equalsIgnoreCase(agent.getName())
                && world.getAgents().stream().anyMatch(existing -> !existing.getId().equals(agentId)
                        && nextName.equalsIgnoreCase(existing.getName()))) {
            throw new IllegalArgumentException("agent name already exists: " + nextName);
        }

        int nextX = x == null ? agent.getState().getX() : x;
        int nextY = y == null ? agent.getState().getY() : y;
        validatePosition(nextX, nextY);

        agent.setName(nextName);
        agent.getState().setX(nextX);
        agent.getState().setY(nextY);
        agent.setPersonality(resolvePersonality(nextName, personality));
        return agent;
    }

    /** 创建并加入一个拥有独立记忆和事件监听能力的居民，并允许显式覆盖人格。 */
    public Agent createAndAddAgent(String name, Integer x, Integer y, PersonalityProfile personality) {
        String normalizedName = name == null || name.isBlank()
                ? "居民-" + (world.getAgents().size() + 1)
                : name.trim();
        if (world.getAgents().stream().anyMatch(agent -> normalizedName.equalsIgnoreCase(agent.getName()))) {
            throw new IllegalArgumentException("agent name already exists: " + normalizedName);
        }

        int spawnIndex = world.getAgents().size();
        int spawnX = x == null ? 2 + (spawnIndex * 4) % world.getWidth() : x;
        int spawnY = y == null ? 2 + (spawnIndex * 3) % world.getHeight() : y;
        validatePosition(spawnX, spawnY);

        Agent agent = createAgent(normalizedName, spawnX, spawnY, personality);
        addAgent(agent);
        return agent;
    }

    /**
     * 解析居民人格：显式传入的人格（空字段回落）优先，其次世界配置中同名
     * 默认居民的人格，最后回落到运行时配置的通用兜底人格。
     */
    private PersonalityProfile resolvePersonality(String name, PersonalityProfile personality) {
        PersonalityProfile configured = worldConfig.getDefaultResidents().stream()
                .filter(resident -> resident.getName().equalsIgnoreCase(name))
                .findFirst()
                .map(WorldConfig.DefaultResident::getPersonality)
                .orElse(null);
        PersonalityProfile fallback = configured == null ? runtime.getDefaultPersonality() : configured;
        return PersonalityProfile.normalizeFor(personality, fallback);
    }

    private Agent createAgent(String name, int x, int y, PersonalityProfile personality) {
        String agentId = UUID.randomUUID().toString();
        AgentState state = new AgentState(x, y, name);

        AgentMemory memory = new AgentMemory(agentId, memoryStore);
        PersonalityProfile resolvedPersonality = resolvePersonality(name, personality);

        Agent agent = new Agent(agentId, name, state, resolvedPersonality,
            memory, new ArrayList<>(), true);
        agent.setMemory(memory);

        log.info("WorldEngine: Created agent {} with id {}", name, agentId);

        return agent;
    }

    private void validatePosition(int x, int y) {
        if (x < 0 || x >= world.getWidth() || y < 0 || y >= world.getHeight()) {
            throw new IllegalArgumentException(
                    "agent position must be inside world: (" + x + ", " + y + ")");
        }
    }


    public int getCurrentTick() {
        return virtualClock.getTick();
    }

    public World getWorld() {
        return world;
    }

    public synchronized WorldConfig getWorldConfig() {
        return copyConfig(worldConfig);
    }

    public synchronized WorldConfig updateWorldConfig(WorldConfig nextConfig) {
        if (nextConfig == null) {
            throw new IllegalArgumentException("world config must not be null");
        }
        nextConfig.validate();
        world.applyConfig(nextConfig);
        worldConfig = copyConfig(nextConfig);
        return getWorldConfig();
    }

    private WorldConfig copyConfig(WorldConfig source) {
        return new WorldConfig(source.getName(), source.getWidth(), source.getHeight(),
                source.getRules() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source.getRules()),
                source.getItems() == null ? List.of() : new ArrayList<>(source.getItems()),
                source.getDefaultResidents() == null ? List.of() : new ArrayList<>(source.getDefaultResidents()));
    }
    
    /**
     * 获取事件总线
     */
    public EventBus getEventBus() {
        return eventBus;
    }
}

