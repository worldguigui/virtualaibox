package com.own.virtualaibox.brain;

import java.util.Map;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.properties.PromptProperties;
import com.own.virtualaibox.properties.RuntimeProperties;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LLMBrain {

    // ---------------------------------------------------------------- 提示词结构脚手架
    // 与填充逻辑强耦合的段落（占位符语义、字段格式由代码定义）——属代码而非配置，
    // 常量紧邻使用处，随代码评审。可调的"政策文本"（intro/task）在 prompts/decision/*.txt。

    /** 当前状态段（段尾空行由本常量负责）。 */
    private static final String STATE_SECTION = "=== 当前状态 ===\n"
            + "- 当前时间tick：{tick}\n"
            + "- 你的名字：{name}\n"
            + "- 你的位置：({x}, {y})\n\n";

    /** 稳定人格段（内容为空时整段省略）。 */
    private static final String PERSONALITY_SECTION = "=== 你的稳定人格 ===\n{personality}\n\n";

    /** 其他居民列表段标题（段首换行由本常量负责）。 */
    private static final String OTHERS_SECTION_HEADER = "=== 世界中的其他智能体 ===\n";

    /** 其他居民列表单行模板（行尾换行由本常量负责）。 */
    private static final String OTHERS_LINE = "- {name} 位于 ({x}, {y})\n";

    /** 记忆段（段首换行由本常量负责；内容为空时整段省略）。 */
    private static final String MEMORY_SECTION = "\n=== 你的记忆 ===\n{memory}";

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final Random random;
    /** 提示词行为值（virtual-ai-box.prompt 短标量）。 */
    private final PromptProperties prompt;
    /** 移动钳制等运行参数（virtual-ai-box.runtime）。 */
    private final RuntimeProperties runtime;
    /** 决策提示词模板（classpath:prompts/decision/*.txt，加载时已裁剪首尾空白）。 */
    private final PromptRegistry prompts;

    /** 便利构造器：从 classpath 加载默认模板（测试/无 Spring 装配路径）。 */
    public LLMBrain(ChatModel chatModel, PromptProperties prompt, RuntimeProperties runtime) {
        this(chatModel, prompt, runtime, new PromptRegistry());
    }

    /** Spring 装配入口。 */
    @Autowired
    public LLMBrain(ChatModel chatModel, PromptProperties prompt, RuntimeProperties runtime,
                    PromptRegistry prompts) {
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
        this.random = new Random();
        this.prompt = prompt;
        this.runtime = runtime;
        this.prompts = prompts;
    }


    public MoveAction decideAction(Agent agent, WorldState worldState) {
        log.info("LLMBrain: Deciding action for agent: {}", agent.getName());

        try {
            String promptText = buildPrompt(agent, worldState);
            String response = callLLM(promptText);
            MoveAction action = parseResponse(response, agent.getId());

            log.info("LLMBrain: Decision made - move ({}, {}), reason: {}",
                    Integer.valueOf(action.getDeltaX()), Integer.valueOf(action.getDeltaY()), action.getReason());
            return action;

        } catch (Exception e) {
            log.error("LLMBrain: Error calling LLM, falling back to random decision", e);
            return randomDecision(agent.getId());
        }
    }


    /**
     * 拼装决策提示词：intro/task 两段"政策文本"取自 {@link PromptRegistry}
     * （文件文本已裁剪首尾空白），脚手架段取自类常量；段间排版由本方法与
     * 各常量共同负责；人格/记忆段内容为空时整段省略。
     */
    private String buildPrompt(Agent agent, WorldState worldState) {
        StringBuilder sb = new StringBuilder();

        int clamp = runtime.getMoveClamp();
        sb.append(fill(prompts.get("intro"), Map.of(
                "width", String.valueOf(worldState.getWorldWidth()),
                "height", String.valueOf(worldState.getWorldHeight())))).append("\n\n");

        sb.append(fill(STATE_SECTION, Map.of(
                "tick", String.valueOf(worldState.getTick()),
                "name", agent.getName(),
                "x", String.valueOf(agent.getState().getX()),
                "y", String.valueOf(agent.getState().getY()))));

        if (agent.getPersonality() != null) {
            sb.append(fill(PERSONALITY_SECTION, Map.of(
                    "personality", agent.getPersonality().toPromptContext())));
        }

        sb.append(OTHERS_SECTION_HEADER).append(buildOthers(agent, worldState));

        if (agent.getMemory() != null) {
            String memorySummary = agent.getMemory().summarizeMemoriesForLLM(worldState.getTick());
            if (!memorySummary.isEmpty()) {
                sb.append(fill(MEMORY_SECTION, Map.of("memory", memorySummary)));
            }
        }

        sb.append("\n").append(fill(prompts.get("task"), Map.of(
                "moveClamp", String.valueOf(clamp))));

        return sb.toString();
    }

    /** 按 OTHERS_LINE 模板生成其他居民列表文本（每行自带换行）。 */
    private String buildOthers(Agent agent, WorldState worldState) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AgentState> entry : worldState.getAgentStates().entrySet()) {
            if (entry.getKey().equals(agent.getId())) {
                continue;
            }
            AgentState other = entry.getValue();
            sb.append(fill(OTHERS_LINE, Map.of(
                    "name", other.getName(),
                    "x", String.valueOf(other.getX()),
                    "y", String.valueOf(other.getY()))));
        }
        return sb.toString();
    }

    /** 占位符替换：{key} → value。 */
    private String fill(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
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

            action.setReason(json.has("reason") && !json.get("reason").isNull()
                    ? json.get("reason").asText() : prompt.getDefaultReason());

            return action;

        } catch (Exception e) {
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
        int c = runtime.getMoveClamp();
        return Math.max(-c, Math.min(c, value));
    }

    private MoveAction randomDecision(String agentId) {
        int c = runtime.getMoveClamp();
        MoveAction action = new MoveAction();
        action.setAgentId(agentId);
        action.setDeltaX(random.nextInt(2 * c + 1) - c);
        action.setDeltaY(random.nextInt(2 * c + 1) - c);
        action.setReason(prompt.getFallbackReason());
        return action;
    }
}
