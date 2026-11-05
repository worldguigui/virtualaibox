package com.own.virtualaibox.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Dashboard API 默认值配置（prefix {@code virtual-ai-box.dashboard}）。
 *
 * <p>原先散落在 {@code DashboardController} 的 @RequestParam 默认值、
 * 魔法数（响应里的记忆条数、指标扫描上限）统一收口。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "virtual-ai-box.dashboard")
public class DashboardProperties {

    /** GET /api/dashboard 的事件条数默认值。 */
    private int defaultEventLimit = 30;

    /** GET /api/dashboard 的每居民记忆条数默认值。 */
    private int defaultMemoryLimit = 8;

    /** step/addAgent/update 等响应中每居民携带的记忆条数。 */
    private int agentMemoryLimit = 8;

    /** GET /api/dashboard/state 携带的居民数上限（轻量快照）。 */
    private int stateAgentsLimit = 3;

    /** GET /api/dashboard/events 的条数默认值。 */
    private int eventsDefaultLimit = 50;

    /** 纯代码路径（单测/无 Spring 装配）使用的默认实例。 */
    public static DashboardProperties defaults() {
        return new DashboardProperties();
    }
}
