package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent移动事件
 * 当Agent执行移动动作时发布此事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentMovedEvent extends DomainEvent {
    
    // 移动的Agent ID
    private String agentId;
    
    // 原始位置X
    private int fromX;
    
    // 原始位置Y
    private int fromY;
    
    // 新位置X
    private int toX;
    
    // 新位置Y
    private int toY;
    
    // 移动距离
    private double distance;
    
    // 移动原因
    private String reason;
    
    @Override
    public String getEventType() {
        return "agent.moved";
    }
    
    @Override
    public String getDescription() {
        return String.format("Agent %s moved from (%d, %d) to (%d, %d), reason: %s", 
                agentId, fromX, fromY, toX, toY, reason);
    }
}
