package com.own.virtualaibox.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.LinkedHashMap;

import com.own.virtualaibox.domain.event.events.TownActionEvent;
import com.own.virtualaibox.domain.world.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.agent.PersonalityProfile;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.memory.AgentMemory;
import com.own.virtualaibox.domain.memory.MemoryStore;
import com.own.virtualaibox.properties.RuntimeProperties;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
/**
 * 小镇领域编排器：负责配置映射、居民生命周期、tick 收尾和仪表盘领域命令。
 *
 * <p>心智决策仍由 {@link TickSchedule} 和 EffectExecutor 负责；本类只在命令或
 * tick 收尾阶段修改资源、消息、关系、任务和配置等小镇状态。</p>
 */
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

    /**
     * 推进一个完整 tick。资源再生和自动拾取发生在 TickSchedule 发布 tick.ended 之后。
     */
    public void step() {
        log.info("WorldEngine: Starting new tick...");
        
        virtualClock.stepForward();
        int currentTick = getCurrentTick();
        
        tickSchedule.processTick(currentTick, world);
        simulateTownTick(currentTick);
        
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

    private void simulateTownTick(int tick) {
        if (world.getRules().getOrDefault("itemSpawning", false) && tick % 3 == 0) {
            worldConfig.getItems().stream()
                    .filter(definition -> definition.getMaxCount() == 0
                            || world.getItemInstances().stream().filter(item -> definition.getId().equals(item.getDefinitionId())).count() < definition.getMaxCount())
                    .findFirst()
                    .ifPresent(definition -> {
                        int x = Math.floorMod(tick * 7 + definition.getId().hashCode(), world.getWidth());
                        int y = Math.floorMod(tick * 11 + definition.getId().hashCode(), world.getHeight());
                        spawnItem(definition.getId(), x, y, 1);
                    });
        }
        for (Agent agent : world.getAgents()) {
            world.getItemInstances().stream()
                    .filter(item -> item.getOwnerId() == null && item.getX() == agent.getState().getX() && item.getY() == agent.getState().getY())
                    .findFirst()
                    .ifPresent(item -> pickupItem(agent.getId(), item.getId()));
        }
    }

    /** 在合法位置生成资源实例，并发布 town.action 审计事件。 */
    public synchronized WorldItem spawnItem(String definitionId, int x, int y, int quantity) {
        requireRule("itemSpawning");
        if (worldConfig.getItems().stream().noneMatch(item -> item.getId().equals(definitionId))) {
            throw new IllegalArgumentException("unknown item definition: " + definitionId);
        }
        validatePosition(x, y);
        if (quantity < 1) {
            throw new IllegalArgumentException("item quantity must be positive");
        }
        WorldItem item = WorldItem.loose(definitionId, x, y);
        item.setQuantity(quantity);
        world.getItemInstances().add(item);
        publishTownAction("item.spawned", null, item.getId(), definitionId + " x" + quantity);
        return item;
    }

    /** 只有位于资源点且资源未被占有的居民才能拾取资源。 */
    public synchronized WorldItem pickupItem(String agentId, String itemId) {
        Agent agent = requireAgent(agentId);
        WorldItem item = world.getItemInstances().stream().filter(candidate -> candidate.getId().equals(itemId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("item not found: " + itemId));
        if (item.getOwnerId() != null || item.getX() != agent.getState().getX() || item.getY() != agent.getState().getY()) {
            throw new IllegalArgumentException("item is not available at the agent position");
        }
        item.setOwnerId(agentId);
        agent.getInventory().add(itemId);
        publishTownAction("item.picked", agentId, itemId, item.getDefinitionId());
        return item;
    }

    /** 校验双方身份并记录一条居民消息。 */
    public synchronized TownMessage sendMessage(String fromAgentId, String toAgentId, String content) {
        requireAgent(fromAgentId);
        requireAgent(toAgentId);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("message content must not be blank");
        TownMessage message = TownMessage.create(fromAgentId, toAgentId, content.trim(), getCurrentTick());
        world.getMessages().add(message);
        publishTownAction("message.sent", fromAgentId, toAgentId, message.getContent());
        return message;
    }

    /** 创建或调整有向居民关系，并将信任/好感限制在 -100 到 100。 */
    public synchronized SocialRelation updateRelation(String agentId, String otherAgentId, int trustDelta, int affinityDelta) {
        requireAgent(agentId);
        requireAgent(otherAgentId);
        SocialRelation relation = world.getRelationships().stream()
                .filter(candidate -> candidate.getAgentId().equals(agentId) && candidate.getOtherAgentId().equals(otherAgentId))
                .findFirst().orElseGet(() -> {
                    SocialRelation created = new SocialRelation(agentId, otherAgentId, 0, 0, 0, "known");
                    world.getRelationships().add(created);
                    return created;
                });
        relation.setTrust(clamp(relation.getTrust() + trustDelta));
        relation.setAffinity(clamp(relation.getAffinity() + affinityDelta));
        relation.setInteractions(relation.getInteractions() + 1);
        publishTownAction("relation.updated", agentId, otherAgentId, "trust=" + relation.getTrust());
        return relation;
    }

    /** 创建一个 open 状态的任务。 */
    public synchronized TownQuest createQuest(String title, String description, int reward) {
        if (title == null || title.isBlank() || reward < 0) throw new IllegalArgumentException("invalid quest");
        TownQuest quest = TownQuest.open(title.trim(), description == null ? "" : description.trim(), reward);
        world.getQuests().add(quest);
        publishTownAction("quest.created", null, quest.getId(), quest.getTitle());
        return quest;
    }

    /** 将 open 任务分配给指定居民。 */
    public synchronized TownQuest assignQuest(String questId, String agentId) {
        Agent agent = requireAgent(agentId);
        TownQuest quest = world.getQuests().stream().filter(candidate -> candidate.getId().equals(questId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("quest not found: " + questId));
        if (!"open".equals(quest.getStatus())) throw new IllegalArgumentException("quest is not open");
        quest.setAssigneeId(agent.getId());
        quest.setStatus("assigned");
        publishTownAction("quest.assigned", agentId, questId, quest.getTitle());
        return quest;
    }

    /** 完成 assigned 任务并向执行居民发放货币奖励。 */
    public synchronized TownQuest completeQuest(String questId) {
        TownQuest quest = world.getQuests().stream().filter(candidate -> candidate.getId().equals(questId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("quest not found: " + questId));
        if (!"assigned".equals(quest.getStatus())) throw new IllegalArgumentException("quest is not assigned");
        Agent agent = requireAgent(quest.getAssigneeId());
        quest.setStatus("completed");
        agent.setCoins(agent.getCoins() + quest.getReward());
        publishTownAction("quest.completed", agent.getId(), questId, "reward=" + quest.getReward());
        return quest;
    }

    /** 在 trading 规则开启时转移货币；当前尚未绑定具体物品转移。 */
    public synchronized void trade(String buyerId, String sellerId, int price) {
        Agent buyer = requireAgent(buyerId);
        Agent seller = requireAgent(sellerId);
        if (!world.getRules().getOrDefault("trading", false)) throw new IllegalArgumentException("trading is disabled");
        if (price < 0 || buyer.getCoins() < price) throw new IllegalArgumentException("insufficient coins");
        buyer.setCoins(buyer.getCoins() - price);
        seller.setCoins(seller.getCoins() + price);
        publishTownAction("trade.completed", buyerId, sellerId, "price=" + price);
    }

    /** 在 combat 规则开启时记录战斗动作；当前尚未结算生命值。 */
    public synchronized void combat(String attackerId, String defenderId, int damage) {
        Agent attacker = requireAgent(attackerId);
        requireAgent(defenderId);
        if (!world.getRules().getOrDefault("combat", false)) throw new IllegalArgumentException("combat is disabled");
        if (damage < 1 || damage > 100) throw new IllegalArgumentException("damage must be between 1 and 100");
        publishTownAction("combat.resolved", attacker.getId(), defenderId, "damage=" + damage);
    }

    private Agent requireAgent(String agentId) {
        return world.getAgents().stream().filter(agent -> agent.getId().equals(agentId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("agent not found: " + agentId));
    }

    private void requireRule(String rule) {
        if (!world.getRules().getOrDefault(rule, false)) throw new IllegalArgumentException(rule + " is disabled");
    }

    private int clamp(int value) { return Math.max(-100, Math.min(100, value)); }

    private void publishTownAction(String action, String actorId, String targetId, String details) {
        TownActionEvent event = new TownActionEvent();
        event.setEventId("town_" + action + "_" + System.nanoTime());
        event.setTick(getCurrentTick());
        event.setTimestamp(Instant.now());
        event.setPriority(5);
        event.setSourceSystem("town-runtime");
        event.setAction(action);
        event.setActorId(actorId);
        event.setTargetId(targetId);
        event.setDetails(details);
        eventBus.publish(event);
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

