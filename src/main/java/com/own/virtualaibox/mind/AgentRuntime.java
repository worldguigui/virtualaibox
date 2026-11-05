package com.own.virtualaibox.mind;

import com.own.virtualaibox.behaviordsl.BehaviorProgram;
import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.effect.Effect;
import com.own.virtualaibox.effect.MoveEffect;
import com.own.virtualaibox.effect.SpeakEffect;
import com.own.virtualaibox.secd.DumpFrame;
import com.own.virtualaibox.secd.Instruction;
import com.own.virtualaibox.secd.MachineState;
import com.own.virtualaibox.secd.SECD;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.Value;
import com.own.virtualaibox.properties.RuntimeProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Agent 心智运行时：每个 Agent 持有一台 SECD 抽象机。
 *
 * <p>职责（docs/secd-fusion-design-frozen.md §6）：</p>
 * <ul>
 *   <li>持有 MachineState（S/E/C/D）与 SECD 驱动；</li>
 *   <li>每 tick 调用 {@link #tick} 推进机器（单步预算内）；</li>
 *   <li>计划耗尽（C/D 空）时自动编译新计划（Oracle 提供方向）；</li>
 *   <li>只产出 {@link Effect}，不直接修改 World（副作用交给 EffectExecutor）。</li>
 * </ul>
 *
 * <p>支持 D 栈中断/恢复：{@link #interrupt} 把主计划执行现场挂起到 D 栈并加载
 * handler（如 onMeet 对话程序）；handler 求值结束后由 {@link #runHandler} 解开全部
 * continuation 帧，主计划从被中断的精确位置继续。</p>
 */
public class AgentRuntime {

    private final Agent agent;
    /** LLM 预言机：DSL plan 的 dx/dy 由它决定（compileFromProgram 注入 E）。 */
    private final LLMBrain llmBrain;
    private final MachineState state;
    private final SECD secd;
    private final WorldOpEvaluator evaluator;
    private final DefaultPlanCompiler planCompiler;
    /** 行为程序（来自 .lambda 配置）；为 null 时回退到内置 DefaultPlanCompiler。 */
    private final BehaviorProgram behaviorProgram;
    /** 运行参数（感知范围、步数预算、计划长度等）。 */
    private final RuntimeProperties runtime;

    /** 上次编译的行为程序（C 栈快照，供可视化预览；机器 idle 时也保留）。 */
    private List<String> lastProgram = List.of();
    /** 上次 tick 产出的副作用摘要（供可视化预览）。 */
    private List<String> lastActions = List.of();

    public AgentRuntime(Agent agent, LLMBrain llmBrain) {
        this(agent, llmBrain, null, RuntimeProperties.defaults());
    }

    public AgentRuntime(Agent agent, LLMBrain llmBrain, BehaviorProgram behaviorProgram) {
        this(agent, llmBrain, behaviorProgram, RuntimeProperties.defaults());
    }

    public AgentRuntime(Agent agent, LLMBrain llmBrain, BehaviorProgram behaviorProgram,
                        RuntimeProperties runtime) {
        this.agent = agent;
        this.llmBrain = llmBrain;
        this.runtime = runtime;
        this.state = new MachineState();
        this.evaluator = new WorldOpEvaluator(agent, llmBrain);
        this.secd = new SECD(runtime.getSecdMaxSteps(), evaluator);
        this.planCompiler = new DefaultPlanCompiler(llmBrain, runtime.getDefaultPlanLength());
        this.behaviorProgram = behaviorProgram;
    }

    /**
     * 推进一个 tick 的心智：执行计划中的"一步动作"，返回产生的副作用。
     *
     * <p>步进规则：从当前 C 栈继续执行，直到产出一个世界动作
     * （MoveEffect / SpeakEffect）为止；若计划已耗尽则先编译新计划再执行。
     * 记忆写入、LLM 咨询等次级副作用不中断本 tick。</p>
     */
    public List<Effect> tick(WorldState worldState) {
        List<Effect> effects = new ArrayList<>();
        // 注入本 tick 的世界只读快照（供感知原语查询；同一 tick 内确定性一致）
        evaluator.setPerception(WorldPerception.of(agent, worldState, runtime.getPerceptionRange()));
        if (state.isTerminated()) {
            if (behaviorProgram == null) {
                planCompiler.compileInto(state, agent, worldState);
            } else {
                compileFromProgram(state, agent, worldState);
            }
            lastProgram = snapshotInstructions();
        }
        int steps = 0;
        while (!state.isTerminated() && steps < runtime.getMaxStepsPerTick()) {
            List<Effect> stepEffects = new ArrayList<>();
            stepWithResume(stepEffects);
            steps++;
            effects.addAll(stepEffects);
            if (isPrimaryAction(stepEffects)) {
                break;
            }
        }
        lastActions = effects.stream().map(Effect::describe).toList();
        return effects;
    }

    /**
      * 编译 DSL 行为程序并压入 C 栈。
     *
     * <p>E 以 {@code defs + {dx, dy}} 为种子：plan 是内联程序，直接引用自由变量
     * {@code dx}/{@code dy}（由 LLM Oracle 决定的移动方向）。plan 为扁平程序
     * （无顶层闭包应用），不产生 D 帧，tick 语义与内置 DefaultPlanCompiler 一致。</p>
     */
    private void compileFromProgram(MachineState state, Agent agent, WorldState worldState) {
        MoveAction direction = llmBrain.decideAction(agent, worldState);
        Map<String, Value> env = new HashMap<>(behaviorProgram.defs());
        env.put("dx", new IntValue(direction.getDeltaX()));
        env.put("dy", new IntValue(direction.getDeltaY()));
        state.setE(env);
        state.getC().push(behaviorProgram.plan());
    }

    /**
      * 中断：把当前主计划执行现场挂起到 D 栈，加载 handler。
     *
     * <p>语义与闭包应用的挂起一致（docs §8）：D 栈保存 {S,E,C,D} 快照，
     * 之后恢复时从 D 弹出该帧整体换回，主计划从被中断处精确继续。</p>
     */
    public void interrupt(Instruction handler) {
        Stack<DumpFrame> dump = state.getD();
        dump.push(new DumpFrame(
                shallowCloneValues(state.getS()),
                state.getE(),
                shallowCloneInstructions(state.getC()),
                shallowCloneFrames(dump)));
        // 开启 handler 的新求值：清空 S，C 置为 handler
        state.getS().clear();
        state.getC().clear();
        state.getC().push(handler);
    }

    /**
      * 处理中断：把 handler 及其 continuation 全部求值完，直到主计划恢复
     * （D 栈的最后一个中断帧被解开）或步数预算耗尽。
     *
      * <p>handler 内的闭包续体（如 DSL {@code onMeet = λother. { greet other; persona other }}）
     * 解帧后 C 仍可能非空，必须继续求值而不是停在解帧处；当 D 栈清空（中断帧已解开、
     * 主计划已换回 C 栈）即停止，避免在 meet tick 里推进主计划。</p>
     *
     * @param maxSteps handler 步数预算
     * @return handler 执行期间产生的副作用（对话等）
     */
    public List<Effect> runHandler(int maxSteps) {
        List<Effect> effects = new ArrayList<>();
        int steps = 0;
        while (steps < maxSteps) {
            // 中断帧已解开（D 空）＝ 主计划已恢复，交给下一个 tick；handler 结束
            if (state.getD().isEmpty()) {
                break;
            }
            List<Effect> stepEffects = new ArrayList<>();
            stepWithResume(stepEffects);   // C 空时内部先解开一帧续体
            steps++;
            effects.addAll(stepEffects);
        }
        return effects;
    }

    /**
     * 单步推进；若 C 空但 D 非空（有被挂起的续体/被中断的主计划），先执行续体恢复再返回。
     * 恢复本身算一次状态转移，不产生副作用。
     */
    private void stepWithResume(List<Effect> out) {
        if (state.getC().isEmpty() && !state.getD().isEmpty()) {
            Value retVal = state.getS().pop();
            DumpFrame frame = state.getD().pop();
            state.restore(frame);
            state.getS().push(retVal);
            return;
        }
        out.addAll(secd.step(state));
    }

    private boolean isPrimaryAction(List<Effect> effects) {
        return effects.stream()
                .anyMatch(e -> e instanceof MoveEffect || e instanceof SpeakEffect);
    }

    /** 当前计划是否已执行完（C/D 均为空）。 */
    public boolean isIdle() {
        return state.isTerminated();
    }

    /** 行为程序（可能为 null，表示回退内置行为）。 */
    public BehaviorProgram getBehaviorProgram() {
        return behaviorProgram;
    }

    public Agent getAgent() {
        return agent;
    }

    public MachineState getState() {
        return state;
    }

    /** 当前 S 栈顶值（调试/可视化用）。 */
    public String peekTop() {
        return state.getS().isEmpty() ? "<empty>" : state.getS().peek().toString();
    }

    /**
      * 捕获当前 SECD 四寄存器状态摘要。
     *
      * <p>供 dashboard 暴露每个 Agent 的行为执行状态：
     * 状态机状态（idle / executing / suspended）+ S/E/C/D 规模与栈顶预览。
     * 预览取栈顶数条，避免传输整个栈。</p>
     */
    public Map<String, Object> mindSummary() {
        Stack<Value> s = state.getS();
        Stack<Instruction> c = state.getC();
        Stack<DumpFrame> d = state.getD();
        Map<String, Value> e = state.getE();

        boolean terminated = state.isTerminated();
        String status = terminated ? "idle" : (d.isEmpty() ? "executing" : "suspended");

        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("terminated", terminated);
        m.put("sSize", s.size());
        m.put("eSize", e.size());
        m.put("cSize", c.size());
        m.put("dSize", d.size());
        m.put("topValue", s.isEmpty() ? "" : s.peek().toString());
        m.put("currentInstruction", c.isEmpty() ? "" : c.peek().toString());
        m.put("sTop", topValues(s, 4));
        m.put("eTop", topEnv(e, 4));
        m.put("cTop", topInstructions(c, 4));
        m.put("program", lastProgram);
        m.put("lastActions", lastActions);
        return m;
    }

    /** 栈顶 n 个值（自上而下）。 */
    private List<String> topValues(Stack<Value> s, int n) {
        List<String> out = new ArrayList<>();
        for (int i = s.size() - 1; i >= 0 && out.size() < n; i--) {
            out.add(s.get(i).toString());
        }
        return out;
    }

    /** 词法环境前 n 个绑定（E 是 Map，顺序不定，仅作摘要展示）。 */
    private List<Map<String, String>> topEnv(Map<String, Value> e, int n) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map.Entry<String, Value> entry : e.entrySet()) {
            if (out.size() >= n) break;
            out.add(Map.of("key", entry.getKey(), "value", entry.getValue().toString()));
        }
        return out;
    }

    /** 控制栈顶 n 条指令（自上而下，栈顶为下一步将执行）。 */
    private List<String> topInstructions(Stack<Instruction> c, int n) {
        List<String> out = new ArrayList<>();
        for (int i = c.size() - 1; i >= 0 && out.size() < n; i--) {
            out.add(c.get(i).toString());
        }
        return out;
    }

    /** 当前 C 栈全部指令（自底向上），作为最近一次编译程序的预览。 */
    private List<String> snapshotInstructions() {
        List<String> out = new ArrayList<>();
        Stack<Instruction> c = state.getC();
        for (int i = 0; i < c.size(); i++) {
            out.add(c.get(i).toString());
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private Stack<Value> shallowCloneValues(Stack<Value> src) {
        return (Stack<Value>) src.clone();
    }

    @SuppressWarnings("unchecked")
    private Stack<Instruction> shallowCloneInstructions(Stack<Instruction> src) {
        return (Stack<Instruction>) src.clone();
    }

    @SuppressWarnings("unchecked")
    private Stack<DumpFrame> shallowCloneFrames(Stack<DumpFrame> src) {
        return (Stack<DumpFrame>) src.clone();
    }
}
