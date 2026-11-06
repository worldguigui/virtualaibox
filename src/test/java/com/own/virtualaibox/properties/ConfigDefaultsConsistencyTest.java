package com.own.virtualaibox.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MapPropertySource;

import com.own.virtualaibox.domain.world.WorldConfig;

/**
 * 机制化校验 ADR-0005 决策 1 的"双份默认值"约定：application.yml 的
 * {@code virtual-ai-box.*} 默认值必须与各配置类的 Java 字段初始化器
 * （即 {@code defaults()} 纯代码路径）保持一致。
 *
 * <p>原理：用 Spring Boot 的 {@link Binder} 在容器之外把 yml 绑定到配置类
 * （与容器的 ConfigurationProperties 绑定同一套算法：从 Java 初始值出发、
 * 逐字段覆盖 yml 中出现的键），再与 {@code defaults()} 逐字段比对。
 * yml 缺键时绑定结果保留 Java 初始值（设计内的回落），不会误报；
 * 任一侧单独改动造成漂移时本测试立即失败。REQUIRED_KEYS 清单另防
 * yml 键拼写错误被 Binder 静默忽略后"看似一致"的假阴性。</p>
 */
class ConfigDefaultsConsistencyTest {

    /** yml 中必须出现的关键键（值对不对由下面的绑定比对管，键必须在）。 */
    private static final String[] REQUIRED_KEYS = {
            "virtual-ai-box.world.name",
            "virtual-ai-box.world.world-description",
            "virtual-ai-box.world.width",
            "virtual-ai-box.world.height",
            "virtual-ai-box.world.items[0].id",
            "virtual-ai-box.world.items[1].id",
            "virtual-ai-box.world.default-residents[0].personality.role",
            "virtual-ai-box.world.default-residents[1].personality.role",
            "virtual-ai-box.runtime.perception-range",
            "virtual-ai-box.runtime.meet-distance",
            "virtual-ai-box.runtime.event-history-size",
            "virtual-ai-box.runtime.event-scan-limit",
            "virtual-ai-box.runtime.max-steps-per-tick",
            "virtual-ai-box.runtime.max-handler-steps",
            "virtual-ai-box.runtime.secd-max-steps",
            "virtual-ai-box.runtime.default-plan-length",
            "virtual-ai-box.runtime.move-clamp",
            "virtual-ai-box.runtime.convergence.stuck-ticks",
            "virtual-ai-box.runtime.convergence.converged-ticks",
            "virtual-ai-box.runtime.convergence.max-cycle-period",
            "virtual-ai-box.runtime.convergence.history-size",
            "virtual-ai-box.runtime.convergence.max-d-stack-depth",
            "virtual-ai-box.runtime.default-personality.role",
            "virtual-ai-box.prompt.default-reason",
            "virtual-ai-box.prompt.fallback-reason",
            "virtual-ai-box.dashboard.default-event-limit",
            "virtual-ai-box.dashboard.default-memory-limit",
            "virtual-ai-box.dashboard.agent-memory-limit",
            "virtual-ai-box.dashboard.state-agents-limit",
            "virtual-ai-box.dashboard.events-default-limit",
    };

    private static Map<String, Object> ymlFlat;

    @BeforeAll
    static void loadYml() {
        YamlPropertiesFactoryBean yamlFactory = new YamlPropertiesFactoryBean();
        yamlFactory.setResources(new ClassPathResource("application.yml"));
        Properties properties = yamlFactory.getObject();
        assertNotNull(properties, "application.yml 应可解析");
        Map<String, Object> flat = new HashMap<>();
        properties.forEach((key, value) -> flat.put(String.valueOf(key), value));
        ymlFlat = flat;
    }

    /** 在容器之外按 ConfigurationProperties 同款算法把 yml 绑定到配置类。 */
    private static <T> T bind(String prefix, Class<T> type) {
        Iterable<ConfigurationPropertySource> sources =
                ConfigurationPropertySources.from(new MapPropertySource("application.yml", ymlFlat));
        return new Binder(sources).bind(prefix, Bindable.of(type)).get();
    }

    @Test
    void ymlContainsAllRequiredKeys() {
        for (String key : REQUIRED_KEYS) {
            assertTrue(ymlFlat.containsKey(key), "application.yml 缺少关键键: " + key);
        }
    }

    @Test
    void runtimeYmlDefaultsMatchJavaDefaults() {
        RuntimeProperties bound = bind("virtual-ai-box.runtime", RuntimeProperties.class);
        RuntimeProperties defaults = RuntimeProperties.defaults();

        assertEquals(defaults.getPerceptionRange(), bound.getPerceptionRange(), "runtime.perception-range 与 Java 默认值漂移");
        assertEquals(defaults.getMeetDistance(), bound.getMeetDistance(), "runtime.meet-distance 与 Java 默认值漂移");
        assertEquals(defaults.getEventHistorySize(), bound.getEventHistorySize(), "runtime.event-history-size 与 Java 默认值漂移");
        assertEquals(defaults.getEventScanLimit(), bound.getEventScanLimit(), "runtime.event-scan-limit 与 Java 默认值漂移");
        assertEquals(defaults.getMaxStepsPerTick(), bound.getMaxStepsPerTick(), "runtime.max-steps-per-tick 与 Java 默认值漂移");
        assertEquals(defaults.getMaxHandlerSteps(), bound.getMaxHandlerSteps(), "runtime.max-handler-steps 与 Java 默认值漂移");
        assertEquals(defaults.getSecdMaxSteps(), bound.getSecdMaxSteps(), "runtime.secd-max-steps 与 Java 默认值漂移");
        assertEquals(defaults.getDefaultPlanLength(), bound.getDefaultPlanLength(), "runtime.default-plan-length 与 Java 默认值漂移");
        assertEquals(defaults.getMoveClamp(), bound.getMoveClamp(), "runtime.move-clamp 与 Java 默认值漂移");

        assertEquals(defaults.getConvergence().getStuckTicks(), bound.getConvergence().getStuckTicks(),
                "runtime.convergence.stuck-ticks 与 Java 默认值漂移");
        assertEquals(defaults.getConvergence().getConvergedTicks(), bound.getConvergence().getConvergedTicks(),
                "runtime.convergence.converged-ticks 与 Java 默认值漂移");
        assertEquals(defaults.getConvergence().getMaxCyclePeriod(), bound.getConvergence().getMaxCyclePeriod(),
                "runtime.convergence.max-cycle-period 与 Java 默认值漂移");
        assertEquals(defaults.getConvergence().getHistorySize(), bound.getConvergence().getHistorySize(),
                "runtime.convergence.history-size 与 Java 默认值漂移");
        assertEquals(defaults.getConvergence().getMaxDStackDepth(), bound.getConvergence().getMaxDStackDepth(),
                "runtime.convergence.max-d-stack-depth 与 Java 默认值漂移");

        assertEquals(defaults.getDefaultPersonality(), bound.getDefaultPersonality(),
                "runtime.default-personality 与 Java 默认值漂移");
    }

    @Test
    void promptYmlDefaultsMatchJavaDefaults() {
        PromptProperties bound = bind("virtual-ai-box.prompt", PromptProperties.class);
        PromptProperties defaults = PromptProperties.defaults();

        assertEquals(defaults.getDefaultReason(), bound.getDefaultReason(), "prompt.default-reason 与 Java 默认值漂移");
        assertEquals(defaults.getFallbackReason(), bound.getFallbackReason(), "prompt.fallback-reason 与 Java 默认值漂移");
    }

    @Test
    void dashboardYmlDefaultsMatchJavaDefaults() {
        DashboardProperties bound = bind("virtual-ai-box.dashboard", DashboardProperties.class);
        DashboardProperties defaults = DashboardProperties.defaults();

        assertEquals(defaults.getDefaultEventLimit(), bound.getDefaultEventLimit(), "dashboard.default-event-limit 与 Java 默认值漂移");
        assertEquals(defaults.getDefaultMemoryLimit(), bound.getDefaultMemoryLimit(), "dashboard.default-memory-limit 与 Java 默认值漂移");
        assertEquals(defaults.getAgentMemoryLimit(), bound.getAgentMemoryLimit(), "dashboard.agent-memory-limit 与 Java 默认值漂移");
        assertEquals(defaults.getStateAgentsLimit(), bound.getStateAgentsLimit(), "dashboard.state-agents-limit 与 Java 默认值漂移");
        assertEquals(defaults.getEventsDefaultLimit(), bound.getEventsDefaultLimit(), "dashboard.events-default-limit 与 Java 默认值漂移");
    }

    @Test
    void worldYmlDefaultsMatchJavaDefaults() {
        WorldConfig bound = bind("virtual-ai-box.world", WorldConfig.class);
        WorldConfig defaults = WorldConfig.defaultConfig();

        assertEquals(defaults.getName(), bound.getName(), "world.name 与 Java 默认值漂移");
        assertEquals(defaults.getWorldDescription(), bound.getWorldDescription(), "world.world-description 与 Java 默认值漂移");
        assertEquals(defaults.getWidth(), bound.getWidth(), "world.width 与 Java 默认值漂移");
        assertEquals(defaults.getHeight(), bound.getHeight(), "world.height 与 Java 默认值漂移");
        assertEquals(defaults.getRules(), bound.getRules(), "world.rules 与 Java 默认值漂移");
        assertEquals(defaults.getItems(), bound.getItems(), "world.items 与 Java 默认值漂移");
        assertEquals(defaults.getDefaultResidents(), bound.getDefaultResidents(), "world.default-residents（含人格）与 Java 默认值漂移");
    }
}
