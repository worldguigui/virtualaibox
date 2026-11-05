package com.own.virtualaibox.mind;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.secd.value.AgentRefValue;

import java.util.Map;

/**
 * 基于 {@link WorldState} 快照的 {@link Perception} 实现（docs §17.3）。
 *
 * <p>只读：全部查询都不修改任何状态；持有的 worldState 是一次 tick 的世界快照。
 * 感知范围（曼哈顿距离）由构造注入，来自 {@code virtual-ai-box.runtime.perception-range}。</p>
 */
public class WorldPerception implements Perception {

    /** 感知范围（曼哈顿距离）：超过该距离视为"感知不到"。 */
    private final int perceptionRange;

    private final Agent selfAgent;
    private final WorldState worldState;

    private WorldPerception(Agent selfAgent, WorldState worldState, int perceptionRange) {
        this.selfAgent = selfAgent;
        this.worldState = worldState;
        this.perceptionRange = perceptionRange;
    }

    public static WorldPerception of(Agent self, WorldState worldState, int perceptionRange) {
        return new WorldPerception(self, worldState, perceptionRange);
    }

    @Override
    public AgentRefValue self() {
        return new AgentRefValue(selfAgent.getId(), selfAgent.getName());
    }

    @Override
    public int distTo(AgentRefValue other) {
        AgentState me = selfAgent.getState();
        AgentState o = worldState.getAgentStates().get(other.agentId);
        if (o == null) {
            return Integer.MAX_VALUE;
        }
        int d = Math.abs(me.getX() - o.getX()) + Math.abs(me.getY() - o.getY());
        return d <= perceptionRange ? d : Integer.MAX_VALUE;
    }

    @Override
    public String nameOf(AgentRefValue other) {
        AgentState o = worldState.getAgentStates().get(other.agentId);
        return o == null ? "?" : o.getName();
    }

    @Override
    public AgentRefValue closestOther() {
        AgentState me = selfAgent.getState();
        AgentRefValue best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Map.Entry<String, AgentState> e : worldState.getAgentStates().entrySet()) {
            if (e.getKey().equals(selfAgent.getId())) {
                continue;
            }
            AgentState o = e.getValue();
            int d = Math.abs(me.getX() - o.getX()) + Math.abs(me.getY() - o.getY());
            if (d <= perceptionRange && d < bestDist) {
                bestDist = d;
                best = new AgentRefValue(e.getKey(), o.getName());
            }
        }
        return best;
    }

    @Override
    public String directionOf(AgentRefValue other) {
        AgentState me = selfAgent.getState();
        AgentState o = worldState.getAgentStates().get(other.agentId);
        if (o == null) {
            return null;
        }
        int dx = o.getX() - me.getX();
        int dy = o.getY() - me.getY();
        if (dx == 0 && dy == 0) {
            return null;
        }
        if (Math.abs(dx) + Math.abs(dy) > perceptionRange) {
            return null;
        }
        // 按主轴判断方向
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx > 0 ? "east" : "west";
        }
        return dy > 0 ? "south" : "north";
    }
}
