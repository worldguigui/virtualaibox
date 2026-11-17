package com.own.virtualaibox.secd;

/**
 * λ 抽象指令：执行时用 (arg, body, 当前 E 的浅拷贝) 构造闭包压入 S 栈。
 * 移植自 lambdaexpr 的 InstLam。
 */
public class InstLam implements Instruction {
    public final String arg;
    public final Instruction body;

    public InstLam(String arg, Instruction body) {
        this.arg = arg;
        this.body = body;
    }

    @Override
    public String toString() {
        return "(lambda " + arg + "." + body + ")";
    }
}
