package com.own.virtualaibox.secd;

/**
 * 应用指令：执行时把 C 栈顶改写为 [rator, rand, @]（rator 在顶）。
 * 移植自 lambdaexpr 的 InstApp。
 */
public class InstApp implements Instruction {
    public final Instruction rator;
    public final Instruction rand;

    public InstApp(Instruction rator, Instruction rand) {
        this.rator = rator;
        this.rand = rand;
    }

    @Override
    public String toString() {
        return "(" + rator + " " + rand + ")";
    }
}
