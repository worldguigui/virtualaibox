package com.own.virtualaibox.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 提示词行为值配置（prefix {@code virtual-ai-box.prompt}）。
 *
 * <p>长文本提示词模板<b>不在 yml</b>：位于 {@code classpath:prompts/decision/*.txt}，
 * 由 {@link com.own.virtualaibox.brain.PromptRegistry} 启动加载并 fail-fast
 * （决策见 ADR-0005 及其修订）。这里只保留记录决策元数据用的短标量。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "virtual-ai-box.prompt")
public class PromptProperties {

    /** 解析成功但缺省 reason 字段时的默认理由。 */
    private String defaultReason = "LLM决策";

    /** LLM 调用/解析失败、随机降级时的理由。 */
    private String fallbackReason = "Fallback: Random movement";

    /** 纯代码路径（单测/无 Spring 装配）使用的默认实例。 */
    public static PromptProperties defaults() {
        return new PromptProperties();
    }
}
