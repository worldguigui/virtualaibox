package com.own.virtualaibox.brain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 提示词模板注册表：启动时加载 classpath:prompts/decision/*.txt。
 *
 * 只有使用者需要反复调优的内容才外置为文件：intro（世界观与任务目标）、task（决策契约与输出格式）。
 * 状态注入、他人列表、记忆段、人格格式这些文本是 LLMBrain 的常量，与代码绑定，不能当普通文案独立修改：
 * 每个占位符（{tick}、{name} 等）都由代码取值填充，想要在状态段加一行“你的朝向：{direction}”，代码必须新增朝向的取值和替换逻辑，否则占位符永远填不上。
 *
 * 每个模板加载后会去掉首尾空白；段落之间的空行由 LLMBrain 拼接时统一添加。
 * 这样，编辑器保存文件时自动增删的末尾换行，不会改变最终发给大模型的提示词。
 */
@Component
public class PromptRegistry {

    // 模板目录（classpath 相对路径）。
    public static final String TEMPLATE_DIR = "prompts/decision";

    // 必需模板名，缺一不可。
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

    // 取模板文本（首尾空白已裁剪；缺名即抛错）。
    public String get(String name) {
        String template = templates.get(name);
        if (template == null) {
            throw new IllegalArgumentException("未知提示词模板: " + name);
        }
        return template;
    }
}
