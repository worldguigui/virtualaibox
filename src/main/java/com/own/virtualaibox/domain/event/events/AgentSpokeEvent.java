package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent说话事件
 * 当SECD的speak原语产生SpeakEffect并落地时发布
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpokeEvent extends DomainEvent {

    // 说话的Agent ID
    private String agentId;

    // 说话Agent的名称
    private String agentName;

    // 目标Agent ID（null=广播给所有人）
    private String targetId;

    // 目标Agent名称（null=广播给所有人）
    private String targetName;

    // 说话内容
    private String content;

    @Override
    public String getEventType() {
        return "agent.spoke";
    }

    @Override
    public String getDescription() {
        return String.format("Agent %s spoke to %s: %s",
                agentName,
                targetName == null ? "everyone" : targetName,
                content);
    }
}
