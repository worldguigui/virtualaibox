package com.own.virtualaibox.domain.agent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 描述居民稳定的人格、动机、表达方式和知识边界。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PersonalityProfile {
    private String role;
    private String temperament;
    private String speakingStyle;
    private String motivations;
    private String values;
    private String knowledgeBoundary;

    /**
     * 人格 → 提示词上下文的序列化格式。恰好 6 个 {@code %s}，顺序与上方六个字段
     * 一一对应——字段增删必须同步本格式（同文件可见，随代码评审）。
     */
    private static final String PROMPT_FORMAT =
            "身份：%s\n性情：%s\n表达方式：%s\n当前动机：%s\n重视的价值：%s\n知识边界：%s";

    /** 将人格转换为稳定的决策上下文，供 Oracle 提示词注入。 */
    public String toPromptContext() {
        return String.format(PROMPT_FORMAT,
                role, temperament, speakingStyle, motivations, values, knowledgeBoundary);
    }

    /** 通用兜底人格：配置未指定（或指定不全）的居民回落到这里。 */
    public static PersonalityProfile generic() {
        return new PersonalityProfile(
                "居民",
                "平和、谨慎",
                "简洁、礼貌",
                "在小镇中生活并理解周围环境",
                "重视安全和真实经历",
                "只知道已经感知到或记忆中的世界信息");
    }

    /**
     * 返回一个稳定的有效人格配置：空字段回落到 fallback 的对应字段；
     * fallback 为 null 时回落到通用兜底人格 {@link #generic()}。
     */
    public static PersonalityProfile normalizeFor(PersonalityProfile profile, PersonalityProfile fallback) {
        PersonalityProfile base = fallback == null ? generic() : fallback;
        if (profile == null) {
            return base;
        }
        return new PersonalityProfile(
                profile.getRole() == null || profile.getRole().isBlank() ? base.getRole() : profile.getRole().trim(),
                profile.getTemperament() == null || profile.getTemperament().isBlank() ? base.getTemperament() : profile.getTemperament().trim(),
                profile.getSpeakingStyle() == null || profile.getSpeakingStyle().isBlank() ? base.getSpeakingStyle() : profile.getSpeakingStyle().trim(),
                profile.getMotivations() == null || profile.getMotivations().isBlank() ? base.getMotivations() : profile.getMotivations().trim(),
                profile.getValues() == null || profile.getValues().isBlank() ? base.getValues() : profile.getValues().trim(),
                profile.getKnowledgeBoundary() == null || profile.getKnowledgeBoundary().isBlank() ? base.getKnowledgeBoundary() : profile.getKnowledgeBoundary().trim());
    }
}
