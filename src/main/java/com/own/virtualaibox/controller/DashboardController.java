package com.own.virtualaibox.controller;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.own.virtualaibox.core.WorldEngine;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.DomainEvent;
import com.own.virtualaibox.domain.event.events.AgentDecidedEvent;
import com.own.virtualaibox.domain.event.events.AgentMetEvent;
import com.own.virtualaibox.domain.event.events.AgentMovedEvent;
import com.own.virtualaibox.domain.event.events.TickEndedEvent;
import com.own.virtualaibox.domain.event.events.TickStartedEvent;
import com.own.virtualaibox.domain.memory.AgentMemory;
import com.own.virtualaibox.domain.memory.MemoryEntry;
import com.own.virtualaibox.domain.world.SocialRelation;
import com.own.virtualaibox.domain.world.WorldConfig;
import com.own.virtualaibox.domain.world.WorldItem;
import com.own.virtualaibox.mind.AgentRuntime;
import com.own.virtualaibox.mind.MindController;
import com.own.virtualaibox.monitor.ConvergenceMonitor;

@RestController
@RequestMapping("/api/dashboard")
/** 提供世界、Agent、事件和运行指标的看板查询接口。 */
public class DashboardController {

    private final WorldEngine worldEngine;
    private final MindController mindController;
    private final ConvergenceMonitor convergenceMonitor;

    /** 创建看板控制器并注入运行时查询依赖。 */
    /**
     * @param worldEngine 世界运行引擎
     * @param mindController Agent 心智运行时控制器
     * @param convergenceMonitor 收敛监控器
     */
    public DashboardController(WorldEngine worldEngine, MindController mindController,
                               ConvergenceMonitor convergenceMonitor) {
        this.worldEngine = worldEngine;
        this.mindController = mindController;
        this.convergenceMonitor = convergenceMonitor;
    }

    /** 汇总看板首页所需的当前运行数据。 */
    /**
     * @param eventLimit 返回的事件数量上限
     * @param memoryLimit 每个 Agent 返回的记忆数量上限
     * @return 看板汇总数据
     */
    @GetMapping
    public Map<String, Object> dashboard(@RequestParam(defaultValue = "30") int eventLimit,
                                         @RequestParam(defaultValue = "8") int memoryLimit) {
        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("world", buildWorldInfo());
        result.put("worldConfig", worldEngine.getWorldConfig());
        result.put("agents", buildAgents(memoryLimit));
        result.put("events", buildEvents(eventLimit));
        result.put("metrics", buildMetrics());
        result.put("convergence", convergenceMonitor.summary());
        result.put("capabilities", List.of(
                "事件驱动架构",
                "Agent记忆",
                "多Agent交互",
                "LLM决策",
                "未来可扩展到经营/社交/战斗系统"
        ));
        return result;
    }

    /** 处理新增居民请求，并返回新增居民与最新世界摘要。 */
    /**
     * @return 新增 Agent 的处理结果
     */
    @PostMapping("/addAgent")
    public Map<String, Object> addAgent(@RequestBody(required = false) AgentCreateRequest request) {
        AgentCreateRequest input = request == null ? new AgentCreateRequest(null, null, null) : request;
        Agent agent = worldEngine.createAndAddAgent(input.name(), input.x(), input.y(), input.personality());

        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("agent", agentToMap(agent, 8));
        result.put("world", buildWorldInfo());
        result.put("agents", buildAgents(8));
        return result;
    }

    /** 修改现有居民信息，并返回更新后的居民和世界状态。 */
    @PutMapping("/agents/{agentId}")
    public Map<String, Object> updateAgent(@PathVariable String agentId,
                                           @RequestBody(required = false) AgentUpdateRequest request) {
        AgentUpdateRequest input = request == null ? new AgentUpdateRequest(null, null, null, null) : request;
        Agent agent = worldEngine.updateAgent(agentId, input.name(), input.x(), input.y(), input.personality());

        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("agent", agentToMap(agent, 8));
        result.put("world", buildWorldInfo());
        result.put("agents", buildAgents(8));
        return result;
    }

    @GetMapping("/world")
    /** 读取当前世界设计配置。 */
    public WorldConfig worldConfig() {
        return worldEngine.getWorldConfig();
    }

    @PutMapping("/world")
    /** 原子替换世界设计配置，非法请求由统一异常处理器转为 400。 */
    public Map<String, Object> updateWorld(@RequestBody WorldConfig request) {
        WorldConfig config = worldEngine.updateWorldConfig(request);
        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("world", buildWorldInfo());
        result.put("worldConfig", config);
        result.put("agents", buildAgents(8));
        return result;
    }

    @GetMapping("/town/runtime")
    /** 返回资源实例、关系、消息、任务和居民的运行态快照。 */
    public Map<String, Object> townRuntime() {
        return runtimeSnapshot();
    }

    @PostMapping("/town/items/spawn")
    public Map<String, Object> spawnItem(@RequestBody ItemRequest request) {
        WorldItem item = worldEngine.spawnItem(request.definitionId(), request.x(), request.y(), request.quantity());
        return Map.of("item", item, "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/items/{itemId}/pickup")
    public Map<String, Object> pickupItem(@PathVariable String itemId, @RequestBody AgentCommand request) {
        return Map.of("item", worldEngine.pickupItem(request.agentId(), itemId), "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/messages")
    public Map<String, Object> sendMessage(@RequestBody MessageRequest request) {
        return Map.of("message", worldEngine.sendMessage(request.fromAgentId(), request.toAgentId(), request.content()),
                "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/relations")
    public Map<String, Object> updateRelation(@RequestBody RelationRequest request) {
        SocialRelation relation = worldEngine.updateRelation(request.agentId(), request.otherAgentId(), request.trustDelta(), request.affinityDelta());
        return Map.of("relation", relation, "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/quests")
    public Map<String, Object> createQuest(@RequestBody QuestRequest request) {
        return Map.of("quest", worldEngine.createQuest(request.title(), request.description(), request.reward()), "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/quests/{questId}/assign")
    public Map<String, Object> assignQuest(@PathVariable String questId, @RequestBody AgentCommand request) {
        return Map.of("quest", worldEngine.assignQuest(questId, request.agentId()), "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/quests/{questId}/complete")
    public Map<String, Object> completeQuest(@PathVariable String questId) {
        return Map.of("quest", worldEngine.completeQuest(questId), "runtime", runtimeSnapshot());
    }

    @PostMapping("/town/trades")
    public Map<String, Object> trade(@RequestBody TradeRequest request) {
        worldEngine.trade(request.buyerId(), request.sellerId(), request.price());
        return runtimeSnapshot();
    }

    @PostMapping("/town/combat")
    public Map<String, Object> combat(@RequestBody CombatRequest request) {
        worldEngine.combat(request.attackerId(), request.defenderId(), request.damage());
        return runtimeSnapshot();
    }

    /** 推进世界一步并返回最新 Agent 状态。 */
    /**
     * @return 推进一步后的世界状态
     */
    @GetMapping("/step")
    public Map<String, Object> step() {
        worldEngine.step();

        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("world", buildWorldInfo());
        result.put("agents", buildAgents(8));

        return result;
    }

    /** 返回当前世界和 Agent 的简要状态。 */
    /**
     * @return 当前世界状态
     */
    @GetMapping("/state")
    public Map<String, Object> state() {
        Map<String, Object> result = new HashMap<>();
        result.put("tick", worldEngine.getCurrentTick());
        result.put("world", buildWorldInfo());
        result.put("agents", buildAgents(3));
        return result;
    }

    /** 查询最近发生的领域事件。 */
    /**
     * @param limit 返回的事件数量上限
     * @return 事件数据列表
     */
    @GetMapping("/events")
    public List<Map<String, Object>> events(@RequestParam(defaultValue = "50") int limit) {
        return buildEvents(limit);
    }

    /** 查询 Agent 当前状态及记忆信息。 */
    /**
     * @param memoryLimit 每个 Agent 返回的记忆数量上限
     * @return Agent 数据列表
     */
    @GetMapping("/agents")
    public List<Map<String, Object>> agents(@RequestParam(defaultValue = "8") int memoryLimit) {
        return buildAgents(memoryLimit);
    }

    /** 查询当前运行指标。 */
    /**
     * @return 运行指标数据
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return buildMetrics();
    }

    /** 将居民创建或参数校验失败转换为客户端可识别的错误响应。 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleInvalidRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }

    /** 构建世界尺寸和 Agent 数量信息。 */
    /**
     * @return 世界信息
     */
    private Map<String, Object> buildWorldInfo() {
        Map<String, Object> world = new HashMap<>();
        world.put("width", worldEngine.getWorld().getWidth());
        world.put("height", worldEngine.getWorld().getHeight());
        world.put("name", worldEngine.getWorld().getName());
        world.put("worldDescription", worldEngine.getWorld().getWorldDescription());
        world.put("rules", worldEngine.getWorld().getRules());
        world.put("items", worldEngine.getWorld().getItems());
        world.put("itemInstances", worldEngine.getWorld().getItemInstances());
        world.put("relationships", worldEngine.getWorld().getRelationships());
        world.put("messages", worldEngine.getWorld().getMessages());
        world.put("quests", worldEngine.getWorld().getQuests());
        world.put("agentCount", worldEngine.getWorld().getAgents().size());
        return world;
    }

    private Map<String, Object> runtimeSnapshot() {
        return Map.of(
                "items", worldEngine.getWorld().getItemInstances(),
                "relationships", worldEngine.getWorld().getRelationships(),
                "messages", worldEngine.getWorld().getMessages(),
                "quests", worldEngine.getWorld().getQuests(),
                "agents", buildAgents(3));
    }

    public record ItemRequest(String definitionId, int x, int y, int quantity) {}
    public record AgentCommand(String agentId) {}
    public record MessageRequest(String fromAgentId, String toAgentId, String content) {}
    public record RelationRequest(String agentId, String otherAgentId, int trustDelta, int affinityDelta) {}
    public record QuestRequest(String title, String description, int reward) {}
    public record TradeRequest(String buyerId, String sellerId, int price) {}
    public record CombatRequest(String attackerId, String defenderId, int damage) {}

    /** 构建所有 Agent 的详细信息。 */
    /**
     * @param memoryLimit 每个 Agent 返回的记忆数量上限
     * @return Agent 数据列表
     */
    private List<Map<String, Object>> buildAgents(int memoryLimit) {
        return worldEngine.getWorld().getAgents().stream()
                .map(agent -> agentToMap(agent, memoryLimit))
                .toList();
    }

    /** 将 Agent 转换为包含心智和记忆信息的详细数据。 */
    /**
     * @param agent 待转换的 Agent
     * @param memoryLimit 返回的记忆数量上限
     * @return Agent 详细数据
     */
    private Map<String, Object> agentToMap(Agent agent, int memoryLimit) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", agent.getId());
        map.put("name", agent.getName());
        map.put("personality", agent.getPersonality());
        map.put("x", agent.getState().getX());
        map.put("y", agent.getState().getY());
        map.put("active", agent.isActive());
        map.put("coins", agent.getCoins());
        map.put("inventory", agent.getInventory());
        map.put("eventHistorySize", agent.getEventHistory() == null ? 0 : agent.getEventHistory().size());

        // 返回该 Agent 的 SECD 运行时状态（无运行时则为 null）
        AgentRuntime runtime = mindController.getRuntime(agent.getId());
        map.put("mind", runtime == null ? null : runtime.mindSummary());

        if (agent.getMemory() != null) {
            AgentMemory memory = agent.getMemory();
            map.put("memoryStats", memory.getMemoryStats());
            map.put("memorySummary", memory.summarizeMemoriesForLLM(worldEngine.getCurrentTick()));
            map.put("recentMemories", memory.getShortTermMemory(worldEngine.getCurrentTick()).stream()
                    .sorted(Comparator.comparingInt(MemoryEntry::getTick).reversed())
                    .limit(memoryLimit)
                    .map(this::memoryToMap)
                    .toList());
        } else {
            map.put("memoryStats", null);
            map.put("memorySummary", "");
            map.put("recentMemories", List.of());
        }

        return map;
    }

    /** 将领域事件历史转换为接口数据。 */
    /**
     * @param limit 返回的事件数量上限
     * @return 事件数据列表
     */
    private List<Map<String, Object>> buildEvents(int limit) {
        List<DomainEvent> history = worldEngine.getEventBus().getEventHistory(limit);
        List<Map<String, Object>> events = new ArrayList<>();
        for (DomainEvent event : history) {
            events.add(eventToMap(event));
        }
        return events;
    }

    /** 将单个领域事件转换为接口数据并展开事件详情。 */
    /**
     * @param event 待转换的领域事件
     * @return 事件数据
     */
    private Map<String, Object> eventToMap(DomainEvent event) {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", event.getEventId());
        map.put("eventType", event.getEventType());
        map.put("description", event.getDescription());
        map.put("tick", event.getTick());
        map.put("timestamp", event.getTimestamp());
        map.put("priority", event.getPriority());
        map.put("sourceSystem", event.getSourceSystem());
        map.put("processed", event.isProcessed());

        if (event instanceof AgentMovedEvent movedEvent) {
            map.put("detail", Map.of(
                    "agentId", movedEvent.getAgentId(),
                    "fromX", movedEvent.getFromX(),
                    "fromY", movedEvent.getFromY(),
                    "toX", movedEvent.getToX(),
                    "toY", movedEvent.getToY(),
                    "distance", movedEvent.getDistance(),
                    "reason", movedEvent.getReason()
            ));
        } else if (event instanceof AgentMetEvent metEvent) {
            map.put("detail", Map.of(
                    "agentId1", metEvent.getAgentId1(),
                    "agentName1", metEvent.getAgentName1(),
                    "agentId2", metEvent.getAgentId2(),
                    "agentName2", metEvent.getAgentName2(),
                    "meetX", metEvent.getMeetX(),
                    "meetY", metEvent.getMeetY(),
                    "distance", metEvent.getDistance()
            ));
        } else if (event instanceof AgentDecidedEvent decidedEvent) {
            map.put("detail", Map.of(
                    "agentId", decidedEvent.getAgentId(),
                    "agentName", decidedEvent.getAgentName(),
                    "actionType", decidedEvent.getActionType(),
                    "actionDetails", decidedEvent.getActionDetails(),
                    "reasoning", decidedEvent.getReasoning(),
                    "llmDecision", decidedEvent.isLlmDecision()
            ));
        } else if (event instanceof TickStartedEvent) {
            map.put("detail", Map.of("stage", "started"));
        } else if (event instanceof TickEndedEvent endedEvent) {
            map.put("detail", Map.of(
                    "eventCount", endedEvent.getEventCount(),
                    "executionTime", endedEvent.getExecutionTime()
            ));
        }

        return map;
    }

    /** 将记忆条目转换为接口数据。 */
    /**
     * @param entry 待转换的记忆条目
     * @return 记忆数据
     */
    private Map<String, Object> memoryToMap(MemoryEntry entry) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entry.getId());
        map.put("type", entry.getType());
        map.put("content", entry.getContent());
        map.put("tags", entry.getTags());
        map.put("importance", entry.getImportance());
        map.put("createdAt", entry.getCreatedAt());
        map.put("lastAccessedAt", entry.getLastAccessedAt());
        map.put("accessCount", entry.getAccessCount());
        map.put("tick", entry.getTick());
        map.put("relatedAgentId", entry.getRelatedAgentId());
        return map;
    }

    /** 构建事件、Agent 和服务端时间等运行指标。 */
    /**
     * @return 运行指标数据
     */
    private Map<String, Object> buildMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("eventSubscriberCount", worldEngine.getEventBus().getSubscriberCount());
        metrics.put("eventHistorySize", worldEngine.getEventBus().getEventHistory(10000).size());
        metrics.put("agentCount", worldEngine.getWorld().getAgents().size());
        metrics.put("currentTick", worldEngine.getCurrentTick());
        metrics.put("serverTime", Instant.now());
        return metrics;
    }
}
