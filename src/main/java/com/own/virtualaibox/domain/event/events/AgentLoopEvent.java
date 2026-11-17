package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 循环 / 活锁事件。
 *
 * 两种情况触发（以 reason 区分）：
 *   position-cycle：位置进入周期 A→B→A→B…（周期振荡，活锁）；
 *   d-stack：SECD D 栈过深（无限归约 / 深递归启发式）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentLoopEvent extends DomainEvent {

    // 循环的 Agent ID
    private String agentId;

    // 循环的 Agent 名称
    private String agentName;

    // 当前所在位置 X
    private int x;

    // 当前所在位置 Y
    private int y;

    // 周期长度（如 A→B→A→B 周期为 2；d-stack 时表示 D 栈深度）
    private int period;

    // 周期路径摘要，如 "(0,0)→(1,0)"
    private String pattern;

    // 触发原因：position-cycle / d-stack
    private String reason;

    @Override
    public String getEventType() {
        return "agent.loop";
    }

    @Override
    public String getDescription() {
        return String.format("Agent %s 进入循环：周期 %d，模式 %s，原因 %s（当前 (%d, %d)）",
                agentName, period, pattern, reason, x, y);
    }
}
