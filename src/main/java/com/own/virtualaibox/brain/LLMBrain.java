package com.own.virtualaibox.brain;

import java.util.Map;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LLMBrain {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Random random;

    public LLMBrain(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
    }


    public MoveAction decideAction(Agent agent, WorldState worldState) {
        log.info("LLMBrain: Deciding action for agent: {}", agent.getName());
        
        try {
            String prompt = buildPrompt(agent, worldState);
            String response = callLLM(prompt);
            MoveAction action = parseResponse(response, agent.getId());
            
            log.info("LLMBrain: Decision made - move (" + action.getDeltaX() + ", "
                    + action.getDeltaY() + "), reason: " + action.getReason());
            return action;
            
        } catch (Exception e) {
            log.error("LLMBrain: Error calling LLM, falling back to random decision", e);
            return randomDecision(agent.getId());
        }
    }


    private String buildPrompt(Agent agent, WorldState worldState) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("你正在一个由世界设定约束的小镇中行动。你现在的首要任务是探索、生活并完成有意义的行动。\n");
        sb.append("你有记忆能力，可以基于过去的经历做出更好的决策。\n\n");
        sb.append("=== 世界描述与规则 ===\n");
        sb.append(worldState.getWorldDescription()).append("\n");
        sb.append("规则：").append(worldState.getRules()).append("\n");
        sb.append("可用资源定义：").append(worldState.getItemDefinitions()).append("\n\n");
        
        sb.append("=== 当前状态 ===\n");
        sb.append("- 当前时间tick：").append(worldState.getTick()).append("\n");
        sb.append("- 你的名字：").append(agent.getName()).append("\n");
        sb.append("- 你的位置：(").append(agent.getState().getX()).append(", ")
                .append(agent.getState().getY()).append(")\n\n");

        if (agent.getPersonality() != null) {
            sb.append("=== 你的稳定人格 ===\n");
            sb.append(agent.getPersonality().toPromptContext()).append("\n\n");
        }
        
        sb.append("=== 世界中的其他智能体 ===\n");
        for (Map.Entry<String, AgentState> entry : worldState.getAgentStates().entrySet()) {
            if (!entry.getKey().equals(agent.getId())) {
                sb.append("- ").append(entry.getValue().getName())
                        .append(" 位于 (").append(entry.getValue().getX())
                        .append(", ").append(entry.getValue().getY()).append(")\n");
            }
        }
        
        // 添加记忆上下文
        if (agent.getMemory() != null) {
            String memorySummary = agent.getMemory().summarizeMemoriesForLLM(worldState.getTick());
            if (!memorySummary.isEmpty()) {
                sb.append("\n=== 你的记忆 ===\n");
                sb.append(memorySummary);
            }
        }
        
        sb.append("\n=== 任务 ===\n");
        sb.append("请根据当前情况和你的记忆，决定你的下一步行动。\n");
        sb.append("你可以向x和y方向各移动至多1格。\n");
        sb.append("请考虑：\n");
        sb.append("1. 你之前访问过的位置\n");
        sb.append("2. 你遇见过的其他Agent\n");
        sb.append("3. 学到的规律和模式\n\n");
        sb.append("请以JSON格式返回你的决策，格式如下：\n");
        sb.append("{\n");
        sb.append("  \"deltaX\": -1到1之间的整数,\n");
        sb.append("  \"deltaY\": -1到1之间的整数,\n");
        sb.append("  \"reason\": \"你的决策理由，考虑了哪些因素\"\n");
        sb.append("}\n");
        sb.append("只返回JSON，不要其他文字。");
        
        return sb.toString();
    }

    private String callLLM(String userPrompt) {
        return chatModel.chat(userPrompt);
    }

    /**
      * 直接咨询预言机，供 SECD 的 ask-llm 原语使用。
     * 与 {@link #decideAction} 的区别：不做 JSON 决策解析，原样返回模型文本。
     */
    public String chat(String userPrompt) {
        return callLLM(userPrompt);
    }

    private MoveAction parseResponse(String response, String agentId) {
        try {
            String jsonStr = extractJson(response);
            JsonNode json = objectMapper.readTree(jsonStr);
            
            MoveAction action = new MoveAction();
            action.setAgentId(agentId);
            action.setDeltaX(clamp(json.get("deltaX").asInt()));
            action.setDeltaY(clamp(json.get("deltaY").asInt()));
            //action.setDeltaX(json.get("deltaX").asInt());
            //action.setDeltaY(json.get("deltaY").asInt());

            action.setReason(json.has("reason") ? json.get("reason").asText() : "LLM决策");
            
            return action;
            
        } catch (JsonProcessingException | NullPointerException | IllegalArgumentException e) {
            log.warn("LLMBrain: Failed to parse LLM response, using random fallback", e);
            return randomDecision(agentId);
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private int clamp(int value) {
        return Math.max(-1, Math.min(1, value));
    }

    private MoveAction randomDecision(String agentId) {
        MoveAction action = new MoveAction();
        action.setAgentId(agentId);
        action.setDeltaX(random.nextInt(3) - 1);
        action.setDeltaY(random.nextInt(3) - 1);
        action.setReason("Fallback: Random movement");
        return action;
    }
}
