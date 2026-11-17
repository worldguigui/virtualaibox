package com.own.virtualaibox.executor;

import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.event.events.AgentMovedEvent;
import com.own.virtualaibox.domain.world.World;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
// 执行领域动作并发布相应的领域事件。
public class ActionExecutor {
    
    private final EventBus eventBus;

    // 使用事件总线创建动作执行器。
    /**
     * @param eventBus 用于发布动作结果事件的事件总线
     */
    public ActionExecutor(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // 在世界中执行 Agent 的移动动作并发布移动事件。
    /**
     * @param action 待执行的移动动作
     * @param world 动作作用的世界
     */
    public void executeMoveAction(MoveAction action, World world) {
        log.info("ActionExecutor: Executing move action for agent: {}", action.getAgentId());
        
        Agent agent = world.getAgents().stream()
                .filter(a -> a.getId().equals(action.getAgentId()))
                .findFirst()
                .orElse(null);
        
        if (agent == null) {
            log.warn("ActionExecutor: Agent not found: {}", action.getAgentId());
            return;
        }
        
        int oldX = agent.getState().getX();
        int oldY = agent.getState().getY();
        
        int newX = oldX + action.getDeltaX();
        int newY = oldY + action.getDeltaY();
        
        newX = Math.max(0, Math.min(world.getWidth() - 1, newX));
        newY = Math.max(0, Math.min(world.getHeight() - 1, newY));
        
        agent.getState().setX(newX);
        agent.getState().setY(newY);
        
        // 计算移动距离
        double distance = Math.sqrt(Math.pow(newX - oldX, 2) + Math.pow(newY - oldY, 2));
        
        log.info("ActionExecutor: Agent {} moved to ({}, {})", agent.getName(), newX, newY);
        
        // 发布Agent移动事件
        AgentMovedEvent event = new AgentMovedEvent();
        event.setEventId(action.getAgentId() + "_move_" + System.nanoTime());
        event.setAgentId(action.getAgentId());
        event.setFromX(oldX);
        event.setFromY(oldY);
        event.setToX(newX);
        event.setToY(newY);
        event.setDistance(distance);
        event.setReason(action.getReason());
        event.setSourceSystem("action-executor");
        event.setPriority(7);  // 高优先级，确保移动事件优先处理
        event.setTimestamp(Instant.now());
        
        eventBus.publish(event);
    }
}
