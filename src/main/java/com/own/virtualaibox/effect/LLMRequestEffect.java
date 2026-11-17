package com.own.virtualaibox.effect;

/**
 * LLM 预言机调用（信息性副作用）。
 *
 * ask-llm 原语在当前实现中是同步调用预言机并立即得到结果，
 * 该 Effect 本身不再需要被"执行"，仅用于日志/前端展示
 * "Agent 在本次计算中咨询了预言机"这一事实。
 */
public record LLMRequestEffect(String agentId, String prompt, String response) implements Effect {
    @Override
    public String describe() {
        return "ask-llm -> " + (response == null ? "…" : response);
    }
}
