package com.own.virtualaibox.domain.memory;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

/**
 * Agent的记忆管理器
 * 每个Agent都有一个记忆管理器，用于管理自己的短期和长期记忆
 */
@Slf4j
public class AgentMemory {
    
    private final String agentId;
    private final MemoryStore memoryStore;
    
    // 短期记忆窗口大小（tick数）
    private static final int SHORT_TERM_WINDOW = 20;
    
    // 长期记忆保留条件：重要程度 >= 7 或访问频繁
    private static final int IMPORTANCE_THRESHOLD = 7;
    private static final int ACCESS_THRESHOLD = 3;

    public AgentMemory(String agentId, MemoryStore memoryStore) {
        this.agentId = agentId;
        this.memoryStore = memoryStore;
    }
    
    /**
     * 记录观察
     */
    public void recordObservation(String content, int tick, String... tags) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setType(MemoryEntry.MemoryType.OBSERVATION);
        entry.setContent(content);
        entry.setTags(tags);
        entry.setImportance(5);  // 默认中等重要
        entry.setCreatedAt(Instant.now());
        entry.setTick(tick);
        
        memoryStore.store(entry);
    }
    
    /**
     * 记录事件
     */
    public void recordEvent(String content, int tick, int importance, String... tags) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setType(MemoryEntry.MemoryType.EVENT);
        entry.setContent(content);
        entry.setTags(tags);
        entry.setImportance(importance);
        entry.setCreatedAt(Instant.now());
        entry.setTick(tick);
        
        memoryStore.store(entry);
    }
    
    /**
     * 记录关于其他Agent的关系记忆
     */
    public void recordRelationship(String otherAgentId, String otherAgentName, String relationship, int tick) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setType(MemoryEntry.MemoryType.RELATIONSHIP);
        entry.setContent(relationship);
        entry.setTags(new String[]{"agent", otherAgentName});
        entry.setImportance(7);  // 关系记忆通常较重要
        entry.setCreatedAt(Instant.now());
        entry.setTick(tick);
        entry.setRelatedAgentId(otherAgentId);
        
        memoryStore.store(entry);
    }
    
    /**
     * 记录地点信息
     */
    public void recordLocation(int x, int y, String description, int tick) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setType(MemoryEntry.MemoryType.LOCATION);
        entry.setContent(String.format("Location (%d, %d): %s", x, y, description));
        entry.setTags(new String[]{"location", String.format("(%d,%d)", x, y)});
        entry.setImportance(6);
        entry.setCreatedAt(Instant.now());
        entry.setTick(tick);
        
        memoryStore.store(entry);
    }
    
    /**
     * 记录学习的规则或模式
     */
    public void recordLearning(String pattern, int tick) {
        MemoryEntry entry = new MemoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setAgentId(agentId);
        entry.setType(MemoryEntry.MemoryType.LEARNING);
        entry.setContent(pattern);
        entry.setTags(new String[]{"learning", "pattern"});
        entry.setImportance(8);  // 学习记忆很重要
        entry.setCreatedAt(Instant.now());
        entry.setTick(tick);
        
        memoryStore.store(entry);
    }
    
    /**
     * 获取短期记忆（最近N个tick内的记忆）
     */
    public List<MemoryEntry> getShortTermMemory(int currentTick) {
        int startTick = Math.max(0, currentTick - SHORT_TERM_WINDOW);
        List<MemoryEntry> memories = memoryStore.queryByTickRange(agentId, startTick, currentTick);
        
        log.debug("AgentMemory: Retrieved {} short-term memories for agent {} (tick {})", 
                memories.size(), agentId, currentTick);
        
        return memories;
    }
    
    /**
     * 获取长期记忆（最重要的N条记忆）
     */
    public List<MemoryEntry> getLongTermMemory(int limit) {
        List<MemoryEntry> memories = memoryStore.queryTopByImportance(agentId, limit);
        
        log.debug("AgentMemory: Retrieved {} long-term memories for agent {}", 
                memories.size(), agentId);
        
        return memories;
    }
    
    /**
     * 获取与特定Agent相关的记忆
     */
    public List<MemoryEntry> getMemoriesAboutAgent(String otherAgentId) {
        List<MemoryEntry> memories = memoryStore.queryByAgent(agentId).stream()
                .filter(e -> e.getRelatedAgentId() != null && e.getRelatedAgentId().equals(otherAgentId))
                .collect(java.util.stream.Collectors.toList());
        
        return memories;
    }
    
    /**
     * 获取某个标签的所有记忆
     */
    public List<MemoryEntry> getMemoriesByTag(String tag) {
        return memoryStore.queryByTag(agentId, tag);
    }
    
    /**
     * 获取记忆统计
     */
    public MemoryStore.MemoryStats getMemoryStats() {
        return memoryStore.getStats(agentId);
    }
    
    /**
     * 生成记忆摘要用于LLM上下文
     * 将记忆转换为易于理解的文本格式
     */
    public String summarizeMemoriesForLLM(int currentTick) {
        StringBuilder sb = new StringBuilder();
        
        // 短期记忆摘要
        List<MemoryEntry> shortTerm = getShortTermMemory(currentTick);
        if (!shortTerm.isEmpty()) {
            sb.append("【最近的观察和经历】\n");
            shortTerm.stream()
                    .sorted(Comparator.comparingInt(MemoryEntry::getTick).reversed())
                    .limit(5)
                    .forEach(m -> sb.append("- Tick ").append(m.getTick()).append(": ").append(m.getContent()).append("\n"));
            sb.append("\n");
        }
        
        // 关系记忆摘要
        List<MemoryEntry> relationships = memoryStore.queryByType(agentId, MemoryEntry.MemoryType.RELATIONSHIP);
        if (!relationships.isEmpty()) {
            sb.append("【关于其他Agent的记忆】\n");
            relationships.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
            sb.append("\n");
        }
        
        // 学习记忆摘要
        List<MemoryEntry> learnings = memoryStore.queryByType(agentId, MemoryEntry.MemoryType.LEARNING);
        if (!learnings.isEmpty()) {
            sb.append("【学到的规则】\n");
            learnings.forEach(m -> sb.append("- ").append(m.getContent()).append("\n"));
            sb.append("\n");
        }
        
        // 重要事件摘要
        List<MemoryEntry> events = memoryStore.queryByType(agentId, MemoryEntry.MemoryType.EVENT);
        if (!events.isEmpty()) {
            sb.append("【重要事件】\n");
            events.stream()
                    .sorted(Comparator.comparingInt(MemoryEntry::getImportance).reversed())
                    .limit(3)
                    .forEach(m -> sb.append("- ").append(m.getContent()).append(" (重要性: ").append(m.getImportance()).append(")\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * 清空所有记忆
     */
    public void clearAll() {
        memoryStore.clearAgent(agentId);
        log.info("AgentMemory: Cleared all memories for agent {}", agentId);
    }
}
