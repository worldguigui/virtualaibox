package com.own.virtualaibox.secd;

import com.own.virtualaibox.secd.value.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * SECD 机器的四寄存器状态 { S, E, C, D }。
 *
 * 语义（docs/secd-fusion-design-frozen.md §4）：
 *   S 值栈 —— 当前计算过程中的值
 *   E 词法环境 / 闭包环境 —— 变量名到值的绑定
 *   C 控制栈 —— 当前正在执行的行为程序（指令序列）
 *   D 转储栈 —— continuation，被挂起的执行上下文
 *
 * 该状态是可变持有对象（闭包应用恢复时会整体换回 DumpFrame 里的快照），
 * 供 SECD 驱动单步推进。每个 Agent 持有一份独立实例。
 */
public class MachineState {

    private Stack<Value> s;
    private Map<String, Value> e;
    private Stack<Instruction> c;
    private Stack<DumpFrame> d;

    public MachineState() {
        this(new Stack<>(), new HashMap<>(), new Stack<>(), new Stack<>());
    }

    public MachineState(Stack<Value> s, Map<String, Value> e, Stack<Instruction> c, Stack<DumpFrame> d) {
        this.s = s;
        this.e = e;
        this.c = c;
        this.d = d;
    }

    public Stack<Value> getS() {
        return s;
    }

    public Map<String, Value> getE() {
        return e;
    }

    public Stack<Instruction> getC() {
        return c;
    }

    public Stack<DumpFrame> getD() {
        return d;
    }

    public void setS(Stack<Value> s) {
        this.s = s;
    }

    public void setE(Map<String, Value> e) {
        this.e = e;
    }

    public void setC(Stack<Instruction> c) {
        this.c = c;
    }

    public void setD(Stack<DumpFrame> d) {
        this.d = d;
    }

    // 从转储帧恢复：整体换回被挂起前的 S/E/C/D（原 SECDMachine 语义）。
    public void restore(DumpFrame frame) {
        this.s = frame.s;
        this.e = frame.e;
        this.c = frame.c;
        this.d = frame.d;
    }

    // 清空所有寄存器。
    public void clear() {
        s.clear();
        e.clear();
        c.clear();
        d.clear();
    }

    // 控制栈与转储栈均为空，即求解结束。
    public boolean isTerminated() {
        return c.isEmpty() && d.isEmpty();
    }

    /**
     * 捕获当前状态的字符串快照（排除不断增长的 D 栈），
     * 用于活锁/回环检测。移植自原 captureStateSnapshot()。
     */
    public String captureState() {
        StringBuilder sb = new StringBuilder();
        sb.append("S: [");
        for (int i = 0; i < s.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(s.get(i));
        }
        sb.append("]\n");
        sb.append("E: {");
        boolean first = true;
        for (Map.Entry<String, Value> entry : e.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        sb.append("}\n");
        sb.append("C: [");
        for (int i = 0; i < c.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(c.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "MachineState{s=" + s.size() + ", e=" + e.size() + ", c=" + c.size() + ", d=" + d.size() + "}";
    }
}
