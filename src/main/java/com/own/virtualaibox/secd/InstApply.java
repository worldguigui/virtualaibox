package com.own.virtualaibox.secd;

/**
 * 应用符号 @：执行时从 S 栈弹出 rand、rator 并执行应用。
 * 移植自 lambdaexpr 的 InstApply。
 */
public class InstApply implements Instruction {
    @Override
    public String toString() {
        return "@";
    }
}
