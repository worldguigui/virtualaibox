package com.own.virtualaibox.secd.value;

/**
 * 原语操作符值（add、mul、sqr、succ，后续可扩展世界原语）。
 * 移植自 lambdaexpr 的 OpValue。
 */
public class OpValue implements Value {
    public final String op;

    public OpValue(String op) {
        this.op = op;
    }

    @Override
    public String toString() {
        return op;
    }
}
