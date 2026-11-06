package com.own.virtualaibox.domain.world;

import com.own.virtualaibox.domain.agent.AgentState;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
/** 保存世界刻度、尺寸、设定信息及各 Agent 的状态快照。 */
public class WorldState {
    private int tick;
    private Map<String, AgentState> agentStates;
    /** 本 tick 的世界宽度（供 LLM 提示词等消费；未提供时为 0）。 */
    private int worldWidth;
    /** 本 tick 的世界高度。 */
    private int worldHeight;
    /** 本 tick 的世界设定描述（未提供时为空串）。 */
    private String worldDescription = "";
    /** 本 tick 生效的世界规则开关（未提供时为空映射）。 */
    private Map<String, Boolean> rules = Map.of();
    /** 本 tick 可用的资源定义（未提供时为空列表）。 */
    private List<WorldItemDefinition> itemDefinitions = List.of();

    public WorldState(int tick, Map<String, AgentState> agentStates) {
        this(tick, agentStates, 0, 0);
    }

    public WorldState(int tick, Map<String, AgentState> agentStates, int worldWidth, int worldHeight) {
        this(tick, agentStates, worldWidth, worldHeight, "", Map.of(), List.of());
    }

    public WorldState(int tick, Map<String, AgentState> agentStates, int worldWidth, int worldHeight,
                      String worldDescription, Map<String, Boolean> rules,
                      List<WorldItemDefinition> itemDefinitions) {
        this.tick = tick;
        this.agentStates = agentStates;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.worldDescription = worldDescription == null ? "" : worldDescription;
        this.rules = rules == null ? Map.of() : rules;
        this.itemDefinitions = itemDefinitions == null ? List.of() : itemDefinitions;
    }
}
