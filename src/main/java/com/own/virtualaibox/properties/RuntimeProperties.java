package com.own.virtualaibox.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.PersonalityProfile;

import lombok.Data;

/**
 * 运行时参数配置（prefix {@code virtual-ai-box.runtime}）。
 *
 * <p>集中原先散落在各类中的硬编码常量：感知范围、相遇距离、事件历史上限、
 * SECD/AgentRuntime 步数预算、默认计划长度、移动钳制、收敛检测阈值与
 * 默认兜底人格。Java 字段默认值与 application.yml 保持一致，yml 是主源。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "virtual-ai-box.runtime")
public class RuntimeProperties {

    /** 感知范围（曼哈顿距离）：超过该距离视为"感知不到"。 */
    private int perceptionRange = 5;

    /** 两个 Agent 距离小于等于该值（欧氏距离，格）视为相遇。 */
    private double meetDistance = 1.5;

    /** 事件历史环形缓冲上限。 */
    private int eventHistorySize = 210;

    /** 扫描事件历史的保守上限（须 >= eventHistorySize，用于统计/指标）。 */
    private int eventScanLimit = 10000;

    /** 单 tick 内单台 SECD 机器的步进预算。 */
    private int maxStepsPerTick = 500;

    /** onMeet 对话处理器的步数预算。 */
    private int maxHandlerSteps = 200;

    /** SECD 机器总步数上限（防无限归约）。 */
    private int secdMaxSteps = 10000;

    /** 内置默认计划的路径长度（tick 数），每 tick 走一格。 */
    private int defaultPlanLength = 3;

    /** LLM 决策/随机降级的单轴移动钳制幅度（格）。 */
    private int moveClamp = 1;

    /** 收敛 / 活锁检测阈值。 */
    private Convergence convergence = new Convergence();

    /** 通用兜底人格：居民在配置中未指定人格时使用。 */
    private PersonalityProfile defaultPersonality = PersonalityProfile.generic();

    /** 收敛检测的可调阈值（对应 ConvergenceMonitor 原常量）。 */
    @Data
    public static class Convergence {
        /** 连续原地不动超过该 tick 数 → AgentStuck。 */
        private int stuckTicks = 3;
        /** 世界整体连续无变化超过该 tick 数 → WorldConverged。 */
        private int convergedTicks = 5;
        /** 位置周期检测的周期上限（A→B→A→B 周期为 2）。 */
        private int maxCyclePeriod = 8;
        /** 每个 Agent 保留的位置历史长度。 */
        private int historySize = 32;
        /** SECD D 栈深度阈值：超过视为无限归约 / 深递归。 */
        private int maxDStackDepth = 50;
    }

    /** 纯代码路径（单测/无 Spring 装配）使用的默认实例。 */
    public static RuntimeProperties defaults() {
        return new RuntimeProperties();
    }
}
