package com.own.virtualaibox.domain.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的记忆存储实现
 * 使用ConcurrentHashMap确保线程安全
 */
@Component
@Slf4j
public class InMemoryMemoryStore implements MemoryStore {
    
    // Agent ID -> 记忆列表
    private final Map<String, List<MemoryEntry>> memoryIndex = new ConcurrentHashMap<>();
    
    // 记忆ID -> 记忆对象，快速查询
    private final Map<String, MemoryEntry> memoryCache = new ConcurrentHashMap<>();
    
    @Override
    public void store(MemoryEntry entry) {
        if (entry == null) {
            log.warn("InMemoryMemoryStore: Attempting to store null entry");
            return;
        }
        
        memoryCache.put(entry.getId(), entry);
        memoryIndex.computeIfAbsent(entry.getAgentId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);
        
        log.debug("InMemoryMemoryStore: Stored memory {} for agent {}", entry.getId(), entry.getAgentId());
    }
    
    @Override
    public void storeBatch(List<MemoryEntry> entries) {
        for (MemoryEntry entry : entries) {
            store(entry);
        }
        log.info("InMemoryMemoryStore: Stored batch of {} memories", entries.size());
    }
    
    @Override
    public MemoryEntry retrieve(String memoryId) {
        MemoryEntry entry = memoryCache.get(memoryId);
        if (entry != null) {
            // 更新访问时间和次数
            entry.setLastAccessedAt(java.time.Instant.now());
            entry.setAccessCount(entry.getAccessCount() + 1);
        }
        return entry;
    }
    
    @Override
    public List<MemoryEntry> queryByAgent(String agentId) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList());
    }
    
    @Override
    public List<MemoryEntry> queryByTag(String agentId, String tag) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList()).stream()
                .filter(e -> e.getTags() != null && Arrays.asList(e.getTags()).contains(tag))
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> queryByType(String agentId, MemoryEntry.MemoryType type) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList()).stream()
                .filter(e -> e.getType() == type)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> queryTopByImportance(String agentId, int limit) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList()).stream()
                .sorted(Comparator.comparingInt(MemoryEntry::getImportance).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> queryByTickRange(String agentId, int startTick, int endTick) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList()).stream()
                .filter(e -> e.getTick() >= startTick && e.getTick() <= endTick)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<MemoryEntry> queryRecent(String agentId, int limit) {
        return memoryIndex.getOrDefault(agentId, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(MemoryEntry::getCreatedAt).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    @Override
    public void delete(String memoryId) {
        MemoryEntry entry = memoryCache.remove(memoryId);
        if (entry != null) {
            List<MemoryEntry> agentMemories = memoryIndex.get(entry.getAgentId());
            if (agentMemories != null) {
                agentMemories.remove(entry);
            }
            log.debug("InMemoryMemoryStore: Deleted memory {}", memoryId);
        }
    }
    
    @Override
    public void clearAgent(String agentId) {
        List<MemoryEntry> memories = memoryIndex.remove(agentId);
        if (memories != null) {
            for (MemoryEntry entry : memories) {
                memoryCache.remove(entry.getId());
            }
            log.info("InMemoryMemoryStore: Cleared {} memories for agent {}", memories.size(), agentId);
        }
    }
    
    @Override
    public MemoryStats getStats(String agentId) {
        List<MemoryEntry> memories = memoryIndex.getOrDefault(agentId, Collections.emptyList());
        
        return new MemoryStats(
            agentId,
            memories.size(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.OBSERVATION).count(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.EVENT).count(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.RELATIONSHIP).count(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.LOCATION).count(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.SELF).count(),
            (int) memories.stream().filter(e -> e.getType() == MemoryEntry.MemoryType.LEARNING).count()
        );
    }
}
