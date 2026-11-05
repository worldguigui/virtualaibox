package com.own.virtualaibox.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.event.EventBus;
import com.own.virtualaibox.domain.event.events.AgentMetEvent;
import com.own.virtualaibox.domain.event.events.TickEndedEvent;
import com.own.virtualaibox.domain.event.events.TickStartedEvent;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.executor.EffectExecutor;
import com.own.virtualaibox.mind.MindController;
import com.own.virtualaibox.monitor.ConvergenceMonitor;
import com.own.virtualaibox.properties.RuntimeProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 按固定顺序推进世界 tick，并协调决策、效果执行、交互检测和收敛检测。
 * Agent 的心智运行时只产生 {@link Effect}，世界状态由 {@link EffectExecutor} 统一更新。
 */
@Component
@Slf4j
public class TickSchedule {

    private final MindController mindController;
    private final EffectExecutor effectExecutor;
    private final EventBus eventBus;
    private final ConvergenceMonitor convergenceMonitor;
    /** 运行参数（相遇距离、事件扫描上限）。 */
    private final RuntimeProperties runtime;

    /** 当前已处于相遇状态的 pair（id1|id2 排序键），用于"首次相遇才对话"去抖。 */
    private final Set<String> activeMeetings = ConcurrentHashMap.newKeySet();

    /**
     * 创建 tick 调度器。
     *
     * @param mindController 驱动 Agent 心智运行时的控制器
     * @param effectExecutor 将副作用应用到世界的执行器
     * @param eventBus 发布 tick 和交互事件的事件总线
     * @param convergenceMonitor 检测世界及 Agent 收敛状态的监视器
     * @param runtime 运行参数配置
     */
    public TickSchedule(MindController mindController, EffectExecutor effectExecutor,
                        EventBus eventBus, ConvergenceMonitor convergenceMonitor,
                        RuntimeProperties runtime) {
        this.mindController = mindController;
        this.effectExecutor = effectExecutor;
        this.eventBus = eventBus;
        this.convergenceMonitor = convergenceMonitor;
        this.runtime = runtime;
    }

    /**
     * 执行一个完整的世界 tick，并在异常发生时记录错误。
     *
     * @param tick 当前 tick 编号
     * @param world 待推进的世界
     */
    public void processTick(int tick, World world) {
        long startTime = System.currentTimeMillis();
        log.info("TickSchedule: Processing tick {}", tick);

        try {
            // 发布 tick 开始事件
            publishTickStarted(tick);

            // 推进所有 Agent 的心智运行时并收集副作用
            List<Effect> effects = decisionPhase(tick, world);

            // 将收集到的副作用应用到世界
            executionPhase(tick, effects, world);

            // 检测 Agent 相遇并处理相遇交互
            interactionPhase(tick, world);

            // 检测世界不动点、Agent 卡死、位置周期和 D 栈增长
            convergencePhase(tick, world);

            // 发布 tick 结束事件
            long executionTime = System.currentTimeMillis() - startTime;
            publishTickEnded(tick, executionTime);

            log.info("TickSchedule: Tick {} completed in {}ms", tick, executionTime);

        } catch (Exception e) {
            log.error("TickSchedule: Error processing tick {}", tick, e);
        }
    }

    /**
     * 发布当前 tick 的开始事件。
     *
     * @param tick 当前 tick 编号
     */
    private void publishTickStarted(int tick) {
        TickStartedEvent event = new TickStartedEvent();
        event.setEventId("tick_start_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("tick-schedule");
        event.setPriority(10);  // 最高优先级

        eventBus.publish(event);
    }

    /**
     * 推进所有 Agent 的心智运行时并返回产生的副作用。
     *
     * @param tick 当前 tick 编号
     * @param world 当前世界
     * @return 本 tick 待执行的副作用列表
     */
    private List<Effect> decisionPhase(int tick, World world) {
        log.info("TickSchedule: Entering decision phase, current tick: {}", tick);
        return mindController.decisionPhase(tick, world);
    }

    /**
     * 将副作用应用到当前世界。
     *
     * @param tick 当前 tick 编号
     * @param effects 待执行的副作用列表
     * @param world 接收副作用的世界
     */
    private void executionPhase(int tick, List<Effect> effects, World world) {
        log.info("TickSchedule: Entering execution phase, current tick: {}", tick);
        effectExecutor.execute(effects, world);
    }

    /**
     * 检测 Agent 相遇，发布相遇事件，并为首次相遇生成对话副作用。
     *
     * <p>检测相遇 → 发布 AgentMetEvent → <b>首次相遇</b>触发 onMeet 对话
      * （双方各中断当前 SECD 主计划、执行对话程序、经 D 栈恢复），
      * 对话副作用在当前 tick 落地。</p>
    *
      * @param tick 当前 tick 编号
      * @param world 用于查找 Agent 和应用对话副作用的世界
     */
    private void interactionPhase(int tick, World world) {
        log.info("TickSchedule: Entering interaction detection phase, current tick: {}", tick);

        List<Agent> agents = world.getAgents();
        List<Effect> interactionEffects = new ArrayList<>();
        Set<String> currentMeets = new HashSet<>();

        // 检测所有Agent对的相遇
        for (int i = 0; i < agents.size(); i++) {
            for (int j = i + 1; j < agents.size(); j++) {
                Agent agent1 = agents.get(i);
                Agent agent2 = agents.get(j);

                double distance = calculateDistance(agent1, agent2);

                // 距离 <= meet-distance 格视为相遇（virtual-ai-box.runtime.meet-distance）
                if (distance <= runtime.getMeetDistance()) {
                    String pairKey = pairKey(agent1, agent2);
                    currentMeets.add(pairKey);
                    publishAgentMet(tick, agent1, agent2, distance);

                    // 首次相遇才触发对话，避免同 pair 每个 tick 都打招呼
                    if (!activeMeetings.contains(pairKey)) {
                        interactionEffects.addAll(mindController.onMeet(tick, agent1, agent2, world));
                    }
                }
            }
        }

        // 落地对话副作用（SECD 计算 → 意图 → 本 tick 执行）
        if (!interactionEffects.isEmpty()) {
            effectExecutor.execute(interactionEffects, world);
        }

        activeMeetings.clear();
        activeMeetings.addAll(currentMeets);
    }

    /**
     * 检查执行交互后的世界状态及 Agent 运行时状态，并发布收敛相关事件。
     *
     * <p>观察执行后的世界状态，检测不动点（WorldConverged）、Agent 卡死（AgentStuck）、
      * 位置周期（AgentLoop）与 SECD D 栈膨胀（无限归约启发式），发布对应事件。
      * 检测在交互处理之后、发布 tick 结束事件之前进行，
      * 使收敛事件计入本 tick 的事件统计。
    *
     * @param tick 当前 tick 编号
     * @param world 当前世界
    */
    private void convergencePhase(int tick, World world) {
        log.info("TickSchedule: Entering convergence detection phase, current tick: {}", tick);
        convergenceMonitor.monitor(tick, world);
    }

    /**
     * 生成与 Agent 参数顺序无关的相遇键。
     *
     * @param a1 第一个 Agent
     * @param a2 第二个 Agent
     * @return 由两个 Agent ID 按字典序组成的相遇键
     */
    private String pairKey(Agent a1, Agent a2) {
        return a1.getId().compareTo(a2.getId()) <= 0
                ? a1.getId() + "|" + a2.getId()
                : a2.getId() + "|" + a1.getId();
    }

    /**
     * 发布当前 tick 的结束事件，并记录该 tick 的事件数量和执行耗时。
     *
     * @param tick 当前 tick 编号
     * @param executionTime 当前 tick 的执行耗时，单位为毫秒
     */
    private void publishTickEnded(int tick, long executionTime) {
        TickEndedEvent event = new TickEndedEvent();
        event.setEventId("tick_end_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("tick-schedule");
        event.setPriority(1);  // 最低优先级

        // 获取事件历史中本Tick的事件数
        int eventCount = (int) eventBus.getEventHistory(runtime.getEventScanLimit()).stream()
                .filter(e -> e.getTick() == tick)
                .count();

        event.setEventCount(eventCount);
        event.setExecutionTime(executionTime);

        eventBus.publish(event);
    }

    /**
     * 发布两个 Agent 相遇的领域事件。
     *
     * @param tick 当前 tick 编号
     * @param agent1 第一个相遇的 Agent
     * @param agent2 第二个相遇的 Agent
     * @param distance 两个 Agent 之间的距离
     */
    private void publishAgentMet(int tick, Agent agent1, Agent agent2, double distance) {
        AgentMetEvent event = new AgentMetEvent();
        event.setEventId(agent1.getId() + "_met_" + agent2.getId() + "_" + tick);
        event.setTick(tick);
        event.setTimestamp(Instant.now());
        event.setSourceSystem("interaction-detector");
        event.setPriority(8);  // 高优先级
        event.setAgentId1(agent1.getId());
        event.setAgentName1(agent1.getName());
        event.setAgentId2(agent2.getId());
        event.setAgentName2(agent2.getName());
        event.setMeetX((agent1.getState().getX() + agent2.getState().getX()) / 2);
        event.setMeetY((agent1.getState().getY() + agent2.getState().getY()) / 2);
        event.setDistance(distance);

        eventBus.publish(event);
        log.info("TickSchedule: Detected agents meeting - {} and {}", agent1.getName(), agent2.getName());
    }

    /**
     * 计算两个 Agent 当前坐标之间的欧氏距离。
     *
     * @param agent1 第一个 Agent
     * @param agent2 第二个 Agent
     * @return 两个 Agent 之间的欧氏距离
     */
    private double calculateDistance(Agent agent1, Agent agent2) {
        int dx = agent1.getState().getX() - agent2.getState().getX();
        int dy = agent1.getState().getY() - agent2.getState().getY();
        return Math.sqrt(dx * dx + dy * dy);
    }
}
