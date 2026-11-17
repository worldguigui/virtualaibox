package com.own.virtualaibox.behaviordsl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.Agent;

import lombok.extern.slf4j.Slf4j;

/**
 * 行为程序注册表：启动时加载 classpath:agents/*.lambda，按文件名解析。
 *
 * 匹配规则：文件名去掉 .lambda 后与 Agent 名（忽略大小写）或
 * id 匹配；default.lambda 作为全局回退；没有匹配（或没有任何文件）时返回
 * null，运行时回退到内置 com.own.virtualaibox.mind.DefaultPlanCompiler。
 * 坏文件在启动期 fail-fast：改错 DSL 立刻暴露，而不是在某个 tick 里静默失效。
 */
@Component
@Slf4j
public class BehaviorRegistry {

    private final Map<String, BehaviorProgram> programs = new ConcurrentHashMap<>();
    private final BehaviorCompiler compiler = new BehaviorCompiler();

    public BehaviorRegistry() {
        load();
    }

    private void load() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources("classpath*:agents/*.lambda");
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                String base = filename.endsWith(".lambda")
                        ? filename.substring(0, filename.length() - ".lambda".length())
                        : filename;
                String source = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                BehaviorProgram program = compiler.compile(source, filename);
                programs.put(base.toLowerCase(), program);
                log.info("BehaviorRegistry: 加载 {}（defs={}，onMeet={}）", filename,
                        program.defs().size(), program.onMeet() != null ? "有" : "无");
            }
            if (programs.isEmpty()) {
                log.warn("BehaviorRegistry: 未找到 agents/*.lambda，Agent 使用内置默认行为");
            }
        } catch (IOException e) {
            throw new IllegalStateException("行为 DSL 加载失败", e);
        }
    }

    /**
     * 解析某 Agent 的行为程序。
     *
     * @return 命中的程序；无任何匹配（含无 default.lambda）时返回 null
     */
    public BehaviorProgram resolve(Agent agent) {
        BehaviorProgram p = programs.get(agent.getName().toLowerCase());
        if (p == null) {
            p = programs.get(agent.getId().toLowerCase());
        }
        if (p == null) {
            p = programs.get("default");
        }
        return p;
    }
}
