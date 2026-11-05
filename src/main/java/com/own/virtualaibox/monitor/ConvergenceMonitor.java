package com.own.virtualaibox.monitor;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.event.events.AgentLoopEvent;
import com.own.virtualaibox.domain.event.events.AgentStuckEvent;
import com.own.virtualaibox.domain.event.events.WorldConvergedEvent;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.mind.AgentRuntime;
import com.own.virtualaibox.mind.MindController;
import com.own.virtualaibox.properties.RuntimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 收敛 / 活锁检测器。
 *
 * <p>对应设计（docs/secd-fusion-design-frozen.md §9）：把"归一化"落地为工程检测：</p>
 * <ul>
 *   <li><b>Fixed Point / Stability</b> —— 整个世界长期无变化 → {@link WorldConvergedEvent}；</li>
 *   <li><b>AgentStuck</b> —— 单个 Agent 原地不动（含被世界边界钳制）→ {@link AgentStuckEvent}；</li>
 *   <li><b>Loop Detection</b> —— 位置进入周期 A→B→A→B… → {@link AgentLoopEvent}；</li>
 *   <li><b>D 栈增长（无限归约启发式）</b> —— SECD 深递归 / D 栈膨胀 → {@link AgentLoopEvent}（reason=d-stack）。</li>
 * </ul>
 *
 * <p>全部事件为<b>边沿触发</b>：条件首次成立时发布一次，状态被打破后清除，
 * 重新成立才会再发，避免每个 tick 刷屏。检测基于执行后（副作用已落地）的世界状态，
 * 由 {@code TickSchedule} 每 tick 末尾调用。</p>
 */
@Component
@Slf4j
public class ConvergenceMonitor {

    /** 收敛检测阈值（virtual-ai-box.runtime.convergence）。 */
    private final RuntimeProperties.Convergence thresholds;

    private final EventBus eventBus;
    private final MindController mindController;

    /** 测试/纯代码路径：使用默认阈值。 */
    public ConvergenceMonitor(EventBus eventBus, MindController mindController) {
        this(eventBus, mindController, RuntimeProperties.defaults());
    }

    /** Spring 装配：阈值来自运行时配置。 */
    @Autowired
    public ConvergenceMonitor(EventBus eventBus, MindController mindController, RuntimeProperties runtime) {
        this.eventBus = eventBus;
        this.mindController = mindController;
        this.thresholds = runtime.getConvergence();
    }

    /** agentId → 位置历史（按 tick 有序，头部最旧）。 */
    private final Map<String, Deque<Position>> positionHistory = new ConcurrentHashMap<>();
    /** 已发布 stuck 的 agentId → 名称（边沿触发去重，名称供收敛指示器展示）。 */
    private final Map<String, String> stuckFired = new ConcurrentHashMap<>();
    /** 已发布 loop 的 agentId → 名称。 */
    private final Map<String, String> loopFired = new ConcurrentHashMap<>();
    /** 已发布 D 栈超限的 agentId → 名称。 */
    private final Map<String, String> dStackFired = new ConcurrentHashMap<>();

    /** 世界连续稳定（签名不变）tick 计数。 */
    private int stableTicks = 0;
    private String lastWorldSignature = null;
    private boolean convergedFired = false;

    /**
     * 每 tick 末尾调用：基于执行后的世界状态做三类收敛检测并发布事件。
     */
    public void monitor(int tick, World world) {
        for (Agent agent : world.getAgents()) {
            checkAgentPosition(tick, agent);
        }
        checkWorldConverged(tick, world);
        checkDStackGrowth(tick);
    }

    // ------------------------------------------------------------------ 单 Agent

    /** 更新单 Agent 位置历史，检测卡死与位置周期。 */
    private void checkAgentPosition(int tick, Agent agent) {
        String agentId = agent.getId();
        Deque<Position> history = positionHistory.computeIfAbsent(agentId, k -> new ArrayDeque<>());
        history.addLast(new Position(tick, agent.getState().getX(), agent.getState().getY()));
        while (history.size() > thresholds.getHistorySize()) {
            history.removeFirst();
        }

        boolean stuck = isStuck(history);
        if (stuck) {
            if (stuckFired.put(agentId, agent.getName()) == null) {
                publishStuck(tick, agent);
            }
        } else {
            stuckFired.remove(agentId);
        }

        boolean loop = isLoop(history);
        if (loop) {
            if (loopFired.put(agentId, agent.getName()) == null) {
                publishLoop(tick, agent, history);
            }
        } else {
            loopFired.remove(agentId);
        }
    }

    /** 最近 stuck-ticks 个位置是否完全相同（固定点，非周期）。 */
    private boolean isStuck(Deque<Position> history) {
        int stuckTicks = thresholds.getStuckTicks();
        if (history.size() < stuckTicks) {
            return false;
        }
        Position last = history.getLast();
        Position[] arr = history.toArray(new Position[0]);
        for (int i = arr.length - stuckTicks; i < arr.length; i++) {
            if (!arr[i].samePosition(last)) {
                return false;
            }
        }
        return true;
    }

    /** 尾部位置序列是否构成"真实"周期（排除全相同的固定点）。 */
    private boolean isLoop(Deque<Position> history) {
        if (history.size() < 2 * 2) {
            return false;
        }
        Position[] arr = history.toArray(new Position[0]);
        for (int L = 2; L <= thresholds.getMaxCyclePeriod(); L++) {
            if (arr.length < 2 * L) {
                break;
            }
            if (isCycle(arr, L)) {
                return true;
            }
        }
        return false;
    }

    /** 尾部长 2L 序列是否构成周期 L：两段逐点一致，且段内有运动（排除固定点）。 */
    private boolean isCycle(Position[] arr, int L) {
        int n = arr.length;
        boolean moving = false;
        for (int i = 0; i < L; i++) {
            if (!arr[n - 2 * L + i].samePosition(arr[n - L + i])) {
                return false;
            }
            if (i > 0 && !arr[n - L + i].samePosition(arr[n - L])) {
                moving = true;
            }
        }
        return moving;
    }

    private void publishStuck(int tick, Agent agent) {
        AgentStuckEvent event = new AgentStuckEvent();
        event.setEventId("stuck_" + agent.getId() + "_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("convergence-monitor");
        event.setPriority(6);
        event.setAgentId(agent.getId());
        event.setAgentName(agent.getName());
        event.setX(agent.getState().getX());
        event.setY(agent.getState().getY());
        event.setStuckTicks(thresholds.getStuckTicks());
        eventBus.publish(event);
        log.info("ConvergenceMonitor: {} 原地卡住（连续 {} tick 未移动）", agent.getName(), thresholds.getStuckTicks());
    }

    private void publishLoop(int tick, Agent agent, Deque<Position> history) {
        Position[] arr = history.toArray(new Position[0]);
        int L = 2;
        for (; L <= thresholds.getMaxCyclePeriod() && arr.length >= 2 * L; L++) {
            if (isCycle(arr, L)) {
                break;
            }
        }
        String pattern = patternSummary(arr, L);
        AgentLoopEvent event = new AgentLoopEvent();
        event.setEventId("loop_" + agent.getId() + "_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("convergence-monitor");
        event.setPriority(6);
        event.setAgentId(agent.getId());
        event.setAgentName(agent.getName());
        event.setX(agent.getState().getX());
        event.setY(agent.getState().getY());
        event.setPeriod(L);
        event.setPattern(pattern);
        event.setReason("position-cycle");
        eventBus.publish(event);
        log.info("ConvergenceMonitor: {} 进入位置周期，周期={}, 模式={}", agent.getName(), L, pattern);
    }

    /** 周期路径摘要，如 "(0,0)→(1,0)→(0,0)"。 */
    private String patternSummary(Position[] arr, int L) {
        StringBuilder sb = new StringBuilder();
        for (int i = arr.length - L; i < arr.length; i++) {
            if (i > arr.length - L) {
                sb.append("→");
            }
            sb.append("(").append(arr[i].x()).append(",").append(arr[i].y()).append(")");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 世界

    /** 检测世界是否长期无变化（不动点 / 稳定态）。 */
    private void checkWorldConverged(int tick, World world) {
        String signature = worldSignature(world);
        if (signature.equals(lastWorldSignature)) {
            stableTicks++;
            if (stableTicks >= thresholds.getConvergedTicks() && !convergedFired) {
                publishConverged(tick, signature);
                convergedFired = true;
            }
        } else {
            lastWorldSignature = signature;
            stableTicks = 1;
            convergedFired = false;
        }
    }

    /** 所有 Agent 位置的有序摘要（顺序无关的稳定签名）。 */
    private String worldSignature(World world) {
        return world.getAgents().stream()
                .sorted(Comparator.comparing(Agent::getId))
                .map(a -> a.getId() + "@" + a.getState().getX() + "," + a.getState().getY())
                .collect(Collectors.joining("|"));
    }

    private void publishConverged(int tick, String signature) {
        WorldConvergedEvent event = new WorldConvergedEvent();
        event.setEventId("converged_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("convergence-monitor");
        event.setPriority(6);
        event.setStableTicks(stableTicks);
        event.setWorldSignature(signature);
        eventBus.publish(event);
        log.info("ConvergenceMonitor: 世界收敛，连续 {} 个 tick 无变化", stableTicks);
    }

    // ------------------------------------------------------------------ SECD

    /** 无限归约启发式：D 栈深度超过阈值即告警（深递归 / 活锁）。 */
    private void checkDStackGrowth(int tick) {
        for (AgentRuntime runtime : mindController.getRuntimes()) {
            Agent agent = runtime.getAgent();
            int depth = runtime.getState().getD().size();
            if (depth > thresholds.getMaxDStackDepth()) {
                if (dStackFired.put(agent.getId(), agent.getName()) == null) {
                    AgentLoopEvent event = new AgentLoopEvent();
                    event.setEventId("dstack_" + agent.getId() + "_" + tick);
                    event.setTick(tick);
                    event.setTimestamp(Instant.now());
                    event.setSourceSystem("convergence-monitor");
                    event.setPriority(7);
                    event.setAgentId(agent.getId());
                    event.setAgentName(agent.getName());
                    event.setX(agent.getState().getX());
                    event.setY(agent.getState().getY());
                    event.setPeriod(depth);
                    event.setPattern("D-stack=" + depth);
                    event.setReason("d-stack");
                    eventBus.publish(event);
                    log.warn("ConvergenceMonitor: {} D 栈过深（{} 帧），疑似无限归约", agent.getName(), depth);
                }
            } else {
                dStackFired.remove(agent.getId());
            }
        }
    }

    /** 一个 Agent 在某个 tick 的位置快照。 */
    private record Position(int tick, int x, int y) {
        boolean samePosition(Position o) {
            return o != null && this.x == o.x && this.y == o.y;
        }
    }

    /**
      * 当前收敛状态快照（供 dashboard 收敛指示器使用）。
     *
     * <p>反映当前仍处于"已检出"状态（边沿触发尚未被打破）的检测结果。</p>
     */
    public Map<String, Object> summary() {
        Map<String, Object> m = new HashMap<>();
        m.put("worldConverged", convergedFired);
        m.put("stableTicks", stableTicks);
        m.put("stuckAgents", List.copyOf(stuckFired.values()));
        m.put("loopAgents", List.copyOf(loopFired.values()));
        m.put("dStackOverflowAgents", List.copyOf(dStackFired.values()));
        return m;
    }
}
