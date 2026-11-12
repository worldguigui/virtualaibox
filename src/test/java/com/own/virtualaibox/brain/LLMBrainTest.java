package com.own.virtualaibox.brain;

import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.properties.PromptProperties;
import com.own.virtualaibox.properties.RuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 Oracle 两入口的失败语义（ADR-0002）：
 * ChatModel 置 null 使调用必然抛异常，两个入口都应内部降级而非抛出。
 */
class LLMBrainTest {

    private LLMBrain brainWithDeadOracle() {
        // ChatModel 为 null → 调用即 NPE，模拟 Oracle 完全不可用
        return new LLMBrain(null, PromptProperties.defaults(), RuntimeProperties.defaults());
    }

    @Test
    void chatDegradesToSentinelOnOracleFailure() {
        // ask-llm 路径（方案 a，2026-11-12 采纳）：异常 → 哨兵文本压 S，不抛出
        assertEquals(LLMBrain.ORACLE_UNAVAILABLE, brainWithDeadOracle().chat("现在该去哪？"));
    }

    @Test
    void decideActionDegradesToRandomOnOracleFailure() {
        // 计划编译路径（原有语义）：异常 → 随机方向，reason 为兜底理由
        Agent agent = new Agent();
        agent.setId("a1");
        agent.setName("Alice");
        agent.setState(new AgentState(1, 1, "Alice"));

        MoveAction action = brainWithDeadOracle()
                .decideAction(agent, new WorldState(1, Map.of("a1", agent.getState())));

        assertNotNull(action);
        assertEquals(PromptProperties.defaults().getFallbackReason(), action.getReason(),
                "降级决策的 reason 应为配置的兜底理由");
    }
}
