package com.own.virtualaibox.domain.world;

import java.util.List;
import java.util.Map;

import com.own.virtualaibox.domain.agent.AgentState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/** 保存世界刻度及各 Agent 的状态快照。 */
public class WorldState {
    private int tick;
    private Map<String, AgentState> agentStates;
    private String worldDescription;
    private Map<String, Boolean> rules;
    private List<WorldItemDefinition> itemDefinitions;

    public WorldState(int tick, Map<String, AgentState> agentStates) {
        this(tick, agentStates, "", Map.of(), List.of());
    }
}
