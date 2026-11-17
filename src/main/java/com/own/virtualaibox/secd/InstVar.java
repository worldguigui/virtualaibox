package com.own.virtualaibox.secd;

/**
 * 变量指令：执行时若 E 中有绑定则压入绑定值，否则压入 VarValue(name)。
 * 移植自 lambdaexpr 的 InstVar。
 */
public class InstVar implements Instruction {
    public final String name;

    public InstVar(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
