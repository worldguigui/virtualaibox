package com.own.virtualaibox.secd;

import com.own.virtualaibox.secd.value.Value;

import java.util.Map;
import java.util.Stack;

/**
 * 转储帧（continuation）：闭包应用时挂起的调用方机器状态快照。
 * 移植自 lambdaexpr 的 DumpItem，语义保持一致：
 * 恢复时把当前 S/E/C/D 整体换回该帧保存的快照。
 */
public class DumpFrame {
    public final Stack<Value> s;
    public final Map<String, Value> e;
    public final Stack<Instruction> c;
    public final Stack<DumpFrame> d;

    public DumpFrame(Stack<Value> s, Map<String, Value> e, Stack<Instruction> c, Stack<DumpFrame> d) {
        this.s = s;
        this.e = e;
        this.c = c;
        this.d = d;
    }

    @Override
    public String toString() {
        return "Dump(s=" + s.size() + ",c=" + c.size() + ",d=" + d.size() + ")";
    }
}
