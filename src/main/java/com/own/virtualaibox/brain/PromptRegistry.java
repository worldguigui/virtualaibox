package com.own.virtualaibox.brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 提示词模板注册表：启动时加载 {@code classpath:prompts/decision/*.txt}。
 *
 * <p>只外置"政策文本"——使用者不看代码也会反复调优的内容（intro：世界观与
 * 任务目标；task：决策契约与输出格式）。与代码结构强耦合的脚手架段
 * （状态注入、他人列表、记忆段、人格格式）是 {@link LLMBrain} 的常量，
 * 不进配置：占位符语义与字段格式由代码定义，改动必然伴随代码改动。</p>
 *
 * <p>加载期 fail-fast（缺文件/空白文件立刻暴露）；对每个文件做<b>首尾空白
 * 裁剪</b>，段间排版间距由 {@link LLMBrain} 统一负责——编辑器对文件末尾
 * 换行的增删不会影响最终提示词。</p>
 */
@Component
public class PromptRegistry {

    /** 模板目录（classpath 相对路径）。 */
    public static final String TEMPLATE_DIR = "prompts/decision";

    /** 必需模板名，缺一不可。 */
    public static final List<String> REQUIRED = List.of("intro", "task");

    private final Map<String, String> templates = new HashMap<>();

    public PromptRegistry() {
        load();
    }

    private void load() {
        for (String name : REQUIRED) {
            String path = TEMPLATE_DIR + "/" + name + ".txt";
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                throw new IllegalStateException("提示词模板缺失: " + path);
            }
            try {
                String text = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                        .strip();
                if (text.isEmpty()) {
                    throw new IllegalStateException("提示词模板为空白: " + path);
                }
                templates.put(name, text);
            } catch (IOException e) {
                throw new IllegalStateException("提示词模板读取失败: " + path, e);
            }
        }
    }

    /** 取模板文本（首尾空白已裁剪；缺名即抛错）。 */
    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("未知提示词模板: " + name);
        }
        return template;
    }
}
