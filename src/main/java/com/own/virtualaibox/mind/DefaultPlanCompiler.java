package com.own.virtualaibox.mind;

import com.own.virtualaibox.brain.LLMBrain;
import com.own.virtualaibox.domain.action.MoveAction;
import com.own.virtualaibox.domain.agent.Agent;
import com.own.virtualaibox.domain.world.WorldState;
import com.own.virtualaibox.secd.InstApp;
import com.own.virtualaibox.secd.InstConst;
import com.own.virtualaibox.secd.InstLam;
import com.own.virtualaibox.secd.InstSeq;
import com.own.virtualaibox.secd.InstVar;
import com.own.virtualaibox.secd.Instruction;
import com.own.virtualaibox.secd.MachineState;
import com.own.virtualaibox.secd.value.AgentRefValue;
import com.own.virtualaibox.secd.value.IntValue;
import com.own.virtualaibox.secd.value.OpValue;
import com.own.virtualaibox.secd.value.StringValue;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为程序编译器，负责生成默认计划和 onMeet 对话行为。
 *
 * <p><b>默认计划</b>（计划耗尽时编译）：LLM 作 Oracle 决定方向，编译成一条
 * {@value #PATH_LENGTH} 步的路径，路径起点附记忆写入。程序驻留 C 栈跨 tick 执行，
 * 每 tick 只推进"一步动作"，计划自然分片。</p>
 *
 * <p><b>onMeet 对话</b>（相遇时编译）：把对方当作参数 B，应用 λ 闭包
 * {@code λother. (speak other 问候; remember 相遇)}。闭包应用会挂起现场到 D 栈，
 * 与 {@link AgentRuntime#interrupt} 的中断帧叠加，构成"主计划被中断→对话→恢复"。</p>
 */
public class DefaultPlanCompiler {

    /** 默认路径长度（tick 数），每 tick 走一格；可由 virtual-ai-box.runtime.default-plan-length 覆盖。 */
    private static final int DEFAULT_PATH_LENGTH = 3;

    private final LLMBrain llmBrain;
    /** 本次编译的路径长度（tick 数）。 */
    private final int pathLength;

    public DefaultPlanCompiler(LLMBrain llmBrain) {
        this(llmBrain, DEFAULT_PATH_LENGTH);
    }

    public DefaultPlanCompiler(LLMBrain llmBrain, int pathLength) {
        this.llmBrain = llmBrain;
        this.pathLength = pathLength;
    }

    /** 编译新计划并压入状态 C 栈。 */
    public void compileInto(MachineState state, Agent agent, WorldState worldState) {
        // Oracle：决定本次路径的方向（LLM 失败时由 LLMBrain 自行降级为随机）
        MoveAction direction = llmBrain.decideAction(agent, worldState);
        int dx = direction.getDeltaX();
        int dy = direction.getDeltaY();

        List<Instruction> program = new ArrayList<>();
        // 记忆副作用：记录本次目标方向
        program.add(app(appOp("remember", constStr("goal")),
                constStr("head-" + dx + "," + dy)));
        // 沿方向走 pathLength 步
        for (int i = 0; i < pathLength; i++) {
            program.add(app(appOp("move", constInt(dx)), constInt(dy)));
        }

        state.getC().push(new InstSeq(program));
    }

    /**
      * 编译 onMeet 对话程序：{@code apply(λother. (speak other …; remember …), B)}。
     *
     * <p>以闭包应用形式表达"相遇 → 对话"，闭包应用会占用 D 栈一帧；
     * 与中断帧（{@link AgentRuntime#interrupt}）叠加，验证 D 栈的中断/恢复语义。</p>
     *
     * @param self  被中断的 Agent
     * @param other 相遇对象（作为闭包实参 B）
     */
    public static InstSeq compileOnMeet(Agent self, Agent other) {
        String greeting = "你好" + other.getName() + "，我是" + self.getName() + "，很高兴遇见你";
        Instruction body = new InstSeq(List.of(
                app(appOp("speak", new InstVar("other")), constStr(greeting)),
                app(appOp("remember", constStr("met")), constStr("遇见了 " + other.getName()))));
        // apply(onMeet, B)：onMeet = λother. body
        Instruction handler = app(new InstLam("other", body),
                new InstConst(new AgentRefValue(other.getId(), other.getName())));
        return new InstSeq(List.of(handler));
    }

    private static InstApp app(Instruction rator, Instruction rand) {
        return new InstApp(rator, rand);
    }

    private static InstApp appOp(String op, Instruction rand) {
        return app(new InstConst(new OpValue(op)), rand);
    }

    private static InstConst constInt(int v) {
        return new InstConst(new IntValue(v));
    }

    private static InstConst constStr(String s) {
        return new InstConst(new StringValue(s));
    }
}
