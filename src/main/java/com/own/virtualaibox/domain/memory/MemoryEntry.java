package com.own.virtualaibox.domain.memory;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆条目
 * 表示Agent记忆中的单条信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryEntry {
    
    // 记忆ID
    private String id;
    
    // 记忆所属Agent
    private String agentId;
    
    // 记忆类型
    private MemoryType type;
    
    // 记忆内容
    private String content;
    
    // 关键标签（便于查询和检索）
    private String[] tags;
    
    // 重要程度（0-10）
    private int importance;
    
    // 记忆创建时间
    private Instant createdAt;
    
    // 记忆最后访问时间
    private Instant lastAccessedAt;
    
    // 访问次数
    private int accessCount;
    
    // 相关的tick数据
    private int tick;
    
    // 相关的其他Agent ID
    private String relatedAgentId;
    
    /** 记忆分类，作为 MemoryStore 查询和持久化映射的稳定类型。 */
    public enum MemoryType {
        // 观察记忆：看到了什么
        OBSERVATION,
        
        // 事件记忆：发生了什么重要的事
        EVENT,
        
        // 关系记忆：关于其他Agent的信息
        RELATIONSHIP,
        
        // 地点记忆：关于特定位置的信息
        LOCATION,
        
        // 自身记忆：关于自己的目标和计划
        SELF,
        
        // 学习记忆：学到的模式和规则
        LEARNING
    }
}
