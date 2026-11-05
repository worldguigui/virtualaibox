package com.own.virtualaibox.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.own.virtualaibox.properties.PromptProperties;
import com.own.virtualaibox.properties.RuntimeProperties;

class PromptRegistryTest {

    @Test
    void loadsAllRequiredTemplates() {
        PromptRegistry registry = new PromptRegistry();
        assertEquals(List.of("intro", "task"), PromptRegistry.REQUIRED);
        for (String name : PromptRegistry.REQUIRED) {
            assertFalse(registry.get(name).isBlank(), "模板不应为空白: " + name);
        }
    }

    @Test
    void templatesAreTrimmedSoTrailingNewlinesAreHarmless() {
        PromptRegistry registry = new PromptRegistry();

        String intro = registry.get("intro");
        assertTrue(intro.contains("{width}") && intro.contains("{height}"));
        assertFalse(intro.endsWith("\n"));

        String task = registry.get("task");
        assertTrue(task.startsWith("=== 任务 ==="));
        assertFalse(task.endsWith("\n"));
        assertTrue(task.contains("{moveClamp}"));
        // JSON 示例行不得硬编码钳制幅度（ADR-0005：改 move-clamp 不得让提示词自相矛盾）
        assertFalse(task.contains("-1到1"), "JSON 示例不应硬编码幅度，应使用 {moveClamp} 占位符");
        int moveClampSlots = task.split("\\{moveClamp\\}", -1).length - 1;
        assertTrue(moveClampSlots >= 3, "moveClamp 占位符须同时覆盖正文与 JSON 示例的两条坐标轴");
    }

    @Test
    void unknownTemplateNameFails() {
        assertThrows(IllegalArgumentException.class,
                () -> new PromptRegistry().get("no-such-template"));
    }

    @Test
    void llmBrainConvenienceConstructorLoadsClasspathTemplates() {
        LLMBrain brain = new LLMBrain(null, PromptProperties.defaults(), RuntimeProperties.defaults());
        assertTrue(brain != null);
    }
}
