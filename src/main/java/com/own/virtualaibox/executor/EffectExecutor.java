package com.own.virtualaibox.executor;

import com.own.virtualaibox.core.VirtualClock;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.event.events.AgentDecidedEvent;
import com.own.virtualaibox.domain.event.events.AgentSpokeEvent;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.effect.LLMRequestEffect;
import com.own.virtualaibox.effect.MoveEffect;
import com.own.virtualaibox.effect.RememberEffect;
import com.own.virtualaibox.effect.SpeakEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 副作用执行器：把 SECD 计算产出的 Effect 落地到 World。
 *
 * SECD 只计算、只产生意图；本类负责真正的世界副作用：
 *   MoveEffect    → ActionExecutor 落地移动 + 发布移动/决策事件
 *   SpeakEffect   → 发布说话事件
 *   RememberEffect → 写入 Agent 记忆（AgentMemory）
 *   LLMRequestEffect → 信息性副作用，仅日志记录
 */
@Component
@Slf4j
public class EffectExecutor {

    private final ActionExecutor actionExecutor;
    private final EventBus eventBus;
    private final VirtualClock virtualClock;

    public EffectExecutor(ActionExecutor actionExecutor, EventBus eventBus, VirtualClock virtualClock) {
        this.actionExecutor = actionExecutor;
        this.eventBus = eventBus;
        this.virtualClock = virtualClock;
    }

    public void execute(List<Effect> effects, World world) {
        for (Effect effect : effects) {
            if (effect instanceof MoveEffect move) {
                executeMove(move, world);
            } else if (effect instanceof SpeakEffect speak) {
                executeSpeak(speak, world);
            } else if (effect instanceof RememberEffect remember) {
                executeRemember(remember, world);
            } else if (effect instanceof LLMRequestEffect llm) {
                log.debug("EffectExecutor: LLM oracle consulted: {}", llm.describe());
            }
        }
    }

    private void executeMove(MoveEffect move, World world) {
        actionExecutor.executeMoveAction(
                new MoveAction(move.agentId(), move.deltaX(), move.deltaY(), move.reason()),
                world);

        // 发布移动对应的本次心智决定事件
        Agent agent = findAgent(world, move.agentId());
        if (agent != null) {
            AgentDecidedEvent decided = new AgentDecidedEvent();
            decided.setEventId(agent.getId() + "_decided_" + virtualClock.getTick());
            decided.setTick(virtualClock.getTick());
            decided.setTimestamp(Instant.now());
            decided.setSourceSystem("mind-runtime");
            decided.setPriority(6);
            decided.setAgentId(agent.getId());
            decided.setAgentName(agent.getName());
            decided.setActionType("move");
            decided.setActionDetails(String.format("{\"deltaX\":%d,\"deltaY\":%d}",
                    move.deltaX(), move.deltaY()));
            decided.setReasoning(move.reason());
            decided.setLlmDecision(true);
            eventBus.publish(decided);
        }
    }

    private void executeSpeak(SpeakEffect speak, World world) {
        Agent speaker = findAgent(world, speak.agentId());
        Agent target = speak.targetId() == null ? null : findAgent(world, speak.targetId());

        AgentSpokeEvent event = new AgentSpokeEvent();
        event.setEventId((speak.agentId() == null ? "agent" : speak.agentId())
                + "_spoke_" + virtualClock.getTick() + "_" + System.nanoTime());
        event.setTick(virtualClock.getTick());
        event.setTimestamp(Instant.now());
        event.setSourceSystem("effect-executor");
        event.setPriority(5);
        event.setAgentId(speak.agentId());
        event.setAgentName(speaker == null ? speak.agentId() : speaker.getName());
        event.setTargetId(speak.targetId());
        event.setTargetName(target == null ? null : target.getName());
        event.setContent(speak.content());
        eventBus.publish(event);
    }

    private void executeRemember(RememberEffect remember, World world) {
        Agent agent = findAgent(world, remember.agentId());
        if (agent != null && agent.getMemory() != null) {
            agent.getMemory().recordObservation(
                    remember.content(), virtualClock.getTick(), "secd", remember.key());
            log.debug("EffectExecutor: wrote memory for {} [{}]: {}",
                    remember.agentId(), remember.key(), remember.content());
        }
    }

    private Agent findAgent(World world, String agentId) {
        return world.getAgents().stream()
                .filter(a -> a.getId().equals(agentId))
                .findFirst()
                .orElse(null);
    }
}
