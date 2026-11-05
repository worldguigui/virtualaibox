package com.own.virtualaibox.domain.agent;

import java.util.ArrayList;
import java.util.List;

import com.own.virtualaibox.domain.event.EventListener;
import com.own.virtualaibox.domain.memory.AgentMemory;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Agent implements EventListener {
    private String id;
    private String name;
    private AgentState state;

    /** 居民稳定的人格、动机、表达方式和知识边界。 */
    private PersonalityProfile personality;
    
    /** Agent的记忆管理器 */
    private AgentMemory memory;
    
    /** Agent的事件历史 */
    private List<String> eventHistory = new ArrayList<>();
    
    /** Agent是否活跃 */
    private boolean active = true;

    /** 保留旧的六参数构造方式，并为居民创建通用兜底人格。 */
    public Agent(String id, String name, AgentState state, AgentMemory memory,
                 List<String> eventHistory, boolean active) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.personality = PersonalityProfile.generic();
        this.memory = memory;
        this.eventHistory = eventHistory;
        this.active = active;
    }

    /** 创建带有显式人格配置的居民（人格为 null 时回落到通用兜底人格）。 */
    public Agent(String id, String name, AgentState state, PersonalityProfile personality,
                 AgentMemory memory, List<String> eventHistory, boolean active) {
        this.id = id;
        this.name = name;
        this.state = state;
        this.personality = personality == null ? PersonalityProfile.generic() : personality;
        this.memory = memory;
        this.eventHistory = eventHistory;
        this.active = active;
    }
    
    @Override
    public void onEvent(com.own.virtualaibox.domain.event.DomainEvent event) {
        if (!active) return;

        // 防御：eventHistory 可能被 @AllArgsConstructor 覆盖为 null
        if (eventHistory == null) {
            eventHistory = new ArrayList<>();
        }

        // 将事件记录到历史
        eventHistory.add(event.getEventType() + ": " + event.getDescription());
        
        // 如果有记忆管理器，记录关键事件
        if (memory != null) {
            // 记录重要事件到记忆
            handleEventForMemory(event);
        }
    }
    
    private void handleEventForMemory(com.own.virtualaibox.domain.event.DomainEvent event) {
        switch (event.getEventType()) {
            case "agent.met" -> {
                com.own.virtualaibox.domain.event.events.AgentMetEvent metEvent = 
                        (com.own.virtualaibox.domain.event.events.AgentMetEvent) event;
                // 如果这个事件涉及当前Agent
                if (metEvent.getAgentId1().equals(id) || metEvent.getAgentId2().equals(id)) {
                    String otherAgentId = metEvent.getAgentId1().equals(id) ? 
                            metEvent.getAgentId2() : metEvent.getAgentId1();
                    String otherAgentName = metEvent.getAgentId1().equals(id) ? 
                            metEvent.getAgentName2() : metEvent.getAgentName1();
                    
                    memory.recordRelationship(otherAgentId, otherAgentName, 
                            "遇见了Agent: " + otherAgentName, event.getTick());
                }
            }
            case "agent.moved" -> {
                com.own.virtualaibox.domain.event.events.AgentMovedEvent movedEvent = 
                        (com.own.virtualaibox.domain.event.events.AgentMovedEvent) event;
                // 如果这是当前Agent的移动
                if (movedEvent.getAgentId().equals(id)) {
                    memory.recordObservation(
                            String.format("我从(%d, %d)移动到(%d, %d): %s", 
                                    movedEvent.getFromX(), movedEvent.getFromY(),
                                    movedEvent.getToX(), movedEvent.getToY(),
                                    movedEvent.getReason()),
                            event.getTick(),
                            "movement", "self"
                    );
                } else {
                    // 观察到其他Agent的移动
                    memory.recordObservation(
                            String.format("我看到了Agent在(%d, %d)处", 
                                    movedEvent.getToX(), movedEvent.getToY()),
                            event.getTick(),
                            "observation"
                    );
                }
            }
            case "tick.started" -> {
                // 可选：在每个tick开始时进行的操作
            }
            case "tick.ended" -> {
                // 可选：在每个tick结束时进行的操作
            }
        }
    }
}

