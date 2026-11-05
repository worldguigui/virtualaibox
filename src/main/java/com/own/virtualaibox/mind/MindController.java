package com.own.virtualaibox.mind;

import com.own.virtualaibox.behaviordsl.BehaviorProgram;
import com.own.virtualaibox.behaviordsl.BehaviorRegistry;
import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.agent.AgentState;
import com.own.virtualaibox.domain.world.World;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.properties.RuntimeProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 心智控制器：一个 tick 内驱动所有 Agent 的 SECD 运行时，汇总副作用。
 *
 * <p>由 SECD 行为运行时推进 Agent，并汇总其产生的副作用：
 * 每个 Agent 的心智（AgentRuntime）在 C 栈上持有跨 tick 的行为程序，
 * 每 tick 推进一步并产出 Effect；LLM 仅在计划耗尽时作为 Oracle 被询问方向。</p>
 */
@Component
public class MindController {

    private final LLMBrain llmBrain;
    private final BehaviorRegistry behaviorRegistry;
    /** 运行参数（onMeet 步数预算、AgentRuntime 步数预算等）。 */
    private final RuntimeProperties runtime;
    private final Map<String, AgentRuntime> runtimes = new ConcurrentHashMap<>();

    public MindController(LLMBrain llmBrain) {
        this(llmBrain, new BehaviorRegistry(), RuntimeProperties.defaults());
    }

    @Autowired
    public MindController(LLMBrain llmBrain, BehaviorRegistry behaviorRegistry, RuntimeProperties runtime) {
        this.llmBrain = llmBrain;
        this.behaviorRegistry = behaviorRegistry;
        this.runtime = runtime;
    }

    /** 推进所有 Agent 心智一个 tick，返回全部副作用。 */
    public List<Effect> decisionPhase(int tick, World world) {
        WorldState worldState = buildWorldState(tick, world);
        List<Effect> all = new ArrayList<>();
        for (Agent agent : world.getAgents()) {
            AgentRuntime agentRuntime = runtimes.computeIfAbsent(agent.getId(),
                    id -> new AgentRuntime(agent, llmBrain, behaviorRegistry.resolve(agent), runtime));
            all.addAll(agentRuntime.tick(worldState));
        }
        return all;
    }

    /**
      * 相遇处理：双方各中断当前主计划，执行 onMeet 对话程序，
     * 结束后经 D 栈恢复主计划。
     *
     * @return 对话产生的副作用（SpeakEffect / RememberEffect）
     */
    public List<Effect> onMeet(int tick, Agent a1, Agent a2, World world) {
        List<Effect> effects = new ArrayList<>();
        effects.addAll(interruptWithMeet(a1, a2));
        effects.addAll(interruptWithMeet(a2, a1));
        return effects;
    }

    /** 对 self 中断主计划并执行指向 other 的 onMeet 对话（优先使用 DSL 定义，否则使用内置行为）。 */
    private List<Effect> interruptWithMeet(Agent self, Agent other) {
        AgentRuntime agentRuntime = runtimes.computeIfAbsent(self.getId(),
                id -> new AgentRuntime(self, llmBrain, behaviorRegistry.resolve(self), runtime));
        BehaviorProgram program = agentRuntime.getBehaviorProgram();
        if (program != null && program.onMeet() != null) {
            // apply(onMeet, other)：onMeet 是 DSL 编译的 λ 闭包
            agentRuntime.interrupt(new InstApp(
                    new InstConst(program.onMeet()),
                    new InstConst(new AgentRefValue(other.getId(), other.getName()))));
        } else {
            agentRuntime.interrupt(DefaultPlanCompiler.compileOnMeet(self, other));
        }
        return agentRuntime.runHandler(runtime.getMaxHandlerSteps());
    }

    /** 按 Agent ID 取运行时（S/E/C/D 快照）。 */
    public AgentRuntime getRuntime(String agentId) {
        return runtimes.get(agentId);
    }

    public Collection<AgentRuntime> getRuntimes() {
        return runtimes.values();
    }

    private WorldState buildWorldState(int tick, World world) {
        Map<String, AgentState> agentStates = new HashMap<>();
        for (Agent agent : world.getAgents()) {
            agentStates.put(agent.getId(), agent.getState());
        }
        return new WorldState(tick, agentStates, world.getWidth(), world.getHeight());
    }
}
