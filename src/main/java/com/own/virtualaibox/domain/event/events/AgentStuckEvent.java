package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 卡死事件。
 * 当单个 Agent 连续多个 tick 原地不动（如被世界边界钳制）时发布。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentStuckEvent extends DomainEvent {

    // 卡住的 Agent ID
    private String agentId;

    // 卡住的 Agent 名称
    private String agentName;

    // 卡住位置 X
    private int x;

    // 卡住位置 Y
    private int y;

    // 连续原地不动的 tick 数
    private int stuckTicks;

    @Override
    public String getEventType() {
        return "agent.stuck";
    }

    @Override
    public String getDescription() {
        return String.format("Agent %s 原地卡住：在 (%d, %d) 连续 %d 个 tick 未移动",
                agentName, x, y, stuckTicks);
    }
}
