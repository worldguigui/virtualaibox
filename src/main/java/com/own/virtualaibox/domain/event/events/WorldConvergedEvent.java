package com.own.virtualaibox.domain.event.events;

import com.own.virtualaibox.domain.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 世界收敛事件。
 * 当整个世界状态长期无变化（不动点 / 稳定态）时发布。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorldConvergedEvent extends DomainEvent {

    // 连续无变化的 tick 数
    private int stableTicks;

    // 稳定时的世界签名（所有 Agent 位置的摘要，调试用）
    private String worldSignature;

    @Override
    public String getEventType() {
        return "world.converged";
    }

    @Override
    public String getDescription() {
        return String.format("世界已收敛：连续 %d 个 tick 无变化（签名: %s）", stableTicks, worldSignature);
    }
}
