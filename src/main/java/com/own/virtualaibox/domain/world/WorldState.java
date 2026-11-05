package com.own.virtualaibox.domain.world;

import com.own.virtualaibox.domain.agent.AgentState;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
/** 保存世界刻度、尺寸及各 Agent 的状态快照。 */
public class WorldState {
    private int tick;
    private Map<String, AgentState> agentStates;
    /** 本 tick 的世界宽度（供 LLM 提示词等消费；未提供时为 0）。 */
    private int worldWidth;
    /** 本 tick 的世界高度。 */
    private int worldHeight;

    public WorldState(int tick, Map<String, AgentState> agentStates) {
        this(tick, agentStates, 0, 0);
    }

    public WorldState(int tick, Map<String, AgentState> agentStates, int worldWidth, int worldHeight) {
        this.tick = tick;
        this.agentStates = agentStates;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
    }
}
